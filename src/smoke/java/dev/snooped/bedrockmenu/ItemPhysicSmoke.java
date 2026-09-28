package dev.snooped.bedrockmenu;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;

/** Actual optional-mod class loading and injection order, without a world or a network connection. */
final class ItemPhysicSmoke {
    static void register() {
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            BedrockMenu.INSTANCE.companion.close();
            try {
                BedrockSkinSmoke.run(client);
                ReconnectSmoke.run(client);
                var camera=client.gameRenderer.mainCamera();
                var originalCamera=camera.position();
                var originalBlock=camera.blockPosition().immutable();
                var frustum=camera.getCullFrustum();
                var originalFrustum=new net.minecraft.world.phys.Vec3(frustum.getCamX(),frustum.getCamY(),frustum.getCamZ());
                // Deliberately differ from camera position: restoration must preserve the exact frustum origin.
                frustum.prepare(12,13,14);
                var state=new net.minecraft.client.renderer.state.level.CameraRenderState();
                try {
                    try(var scope=MovementVisualCorrection.apply(camera,new net.minecraft.world.phys.Vec3(1.25,.1,0))) {
                        if(camera.position().distanceToSqr(originalCamera.add(1.25,.1,0))>1e-12) throw new AssertionError("Visual camera offset missing");
                        state.blockPos=camera.blockPosition();
                        scope.freeze(state);
                        throw new VisualScopeProbe();
                    }
                } catch(VisualScopeProbe expected) {}
                if(!camera.position().equals(originalCamera) || !camera.blockPosition().equals(originalBlock)) throw new AssertionError("Visual camera not restored after error");
                if(frustum.getCamX()!=12 || frustum.getCamY()!=13 || frustum.getCamZ()!=14) throw new AssertionError("Frustum restoration failed");
                if(state.blockPos.equals(originalBlock)) throw new AssertionError("Render block position aliased mutable camera position");
                frustum.prepare(originalFrustum.x,originalFrustum.y,originalFrustum.z);
                Class.forName("net.minecraft.client.renderer.entity.EntityRenderer");
                System.out.println("MOVEMENT_VISUAL_SCOPE_SMOKE_PASS");
                // Exercise the transformed native collision invokers used by historical replay.
                var floor=net.minecraft.world.phys.shapes.Shapes.create(new net.minecraft.world.phys.AABB(-2,0,-2,2,1,2));
                var bounds=new net.minecraft.world.phys.AABB(0,1,0,.6,2.8,.6);
                var clipped=dev.snooped.bedrockmenu.mixin.EntityCollisionAccess.togetherWithBedrock$collideWithShapes(
                        new net.minecraft.world.phys.Vec3(.1,-.0784,0),bounds,java.util.List.of(floor));
                if(clipped.y!=0 || Math.abs(clipped.x-.1)>1e-9) throw new AssertionError("Replay floor collision failed");
                var step=net.minecraft.world.phys.shapes.Shapes.create(new net.minecraft.world.phys.AABB(.6,1,0,1.6,1.5,1));
                var heights=dev.snooped.bedrockmenu.mixin.EntityCollisionAccess.togetherWithBedrock$collectCandidateStepUpHeights(
                        bounds,java.util.List.of(step),.6F,0F);
                if(heights.length!=1 || heights[0]!=.5F) throw new AssertionError("Replay step candidates failed");
                System.out.println("MOVEMENT_REPLAY_COLLISION_SMOKE_PASS");
                var resolve = ItemPhysicDropCompatibility.class.getDeclaredMethod("resolve");
                resolve.setAccessible(true);
                boolean resolved = (Boolean) resolve.invoke(null);
                if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("itemphysic")) {
                    if (resolved) throw new AssertionError("Absent optional mod should not resolve");
                    Class.forName("net.minecraft.client.player.LocalPlayer");
                    Class.forName("net.minecraft.world.entity.EntityFluidInteraction");
                    System.out.println("ITEMPHYSIC_ABSENT_SMOKE_PASS");
                    return;
                }
                if (!resolved) throw new AssertionError("Actual ItemPhysic channel did not resolve");
                var mod = Class.forName("team.creative.itemphysic.ItemPhysic");
                var config = mod.getField("CONFIG").get(null);
                var settings = config.getClass().getField("throwConfig").get(config);
                var enabled = settings.getClass().getField("enabled");
                boolean saved = enabled.getBoolean(settings);
                // No entity/world is created: the probe stops at the first vanilla inventory access.
                var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
                field.setAccessible(true);
                var player = (DropProbe) ((sun.misc.Unsafe) field.get(null)).allocateInstance(DropProbe.class);
                try {
                    enabled.setBoolean(settings, true);
                    if (!player.drop(false)) throw new AssertionError("ItemPhysic cancellation fixture not active");
                    for (boolean stack : new boolean[]{false, true}) {
                        boolean vanilla = false;
                        try (var scope = ItemPhysicDropCompatibility.Scope.disable(settings, enabled)) {
                            try { player.drop(stack); } catch (VanillaReached expected) { vanilla = true; }
                        }
                        if (!vanilla || !enabled.getBoolean(settings)) throw new AssertionError("Vanilla Q/ctrl-Q path or restore failed");
                    }
                    // Forces the optional tick mixin to apply and execute, with no player connected.
                    Class.forName("team.creative.itemphysic.client.ItemPhysicClient").getMethod("gameTick").invoke(null);
                } finally { enabled.setBoolean(settings, saved); }
                System.out.println("ITEMPHYSIC_COMPAT_SMOKE_PASS");
            } catch (Throwable error) { error.printStackTrace(); }
            finally { client.stop(); }
        });
    }
    static final class VisualScopeProbe extends RuntimeException { }
    static final class VanillaReached extends RuntimeException { }
    static final class DropProbe extends LocalPlayer {
        private DropProbe() { super(null, null, null, null, null, null, false, null); }
        @Override public Inventory getInventory() { throw new VanillaReached(); }
    }
}
