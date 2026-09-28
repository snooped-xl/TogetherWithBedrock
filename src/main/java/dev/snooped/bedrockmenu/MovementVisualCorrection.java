package dev.snooped.bedrockmenu;

import dev.snooped.bedrockmenu.mixin.CameraPositionAccess;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Applies a temporary presentation transform only while Minecraft extracts a render frame. */
public final class MovementVisualCorrection {
    private static final CorrectionSmoothing smoothing=new CorrectionSmoothing();
    private static LocalPlayer owner;
    private static Object level;
    private static Vec3 renderedOffset=Vec3.ZERO;
    private static Camera renderedCamera;
    private static Camera frameCamera;
    private static Vec3 frameOffset=Vec3.ZERO;
    private static long lastSample;

    private MovementVisualCorrection() {}
    static void corrected(LocalPlayer p,Vec3 before,Vec3 after) {
        if(!eligible(p)) {reset();return;}
        if(owner!=p || level!=p.level()) reset();
        owner=p;level=p.level();
        smoothing.correct(before,after,System.nanoTime());
    }
    public static void reset() {
        owner=null;level=null;lastSample=0;smoothing.reset();frameCamera=null;frameOffset=Vec3.ZERO;
        // Do not disturb an active try/finally render scope's restoration state.
    }
    private static boolean eligible(LocalPlayer p) {
        return p!=null && BedrockMovement.reconciliation(p) && !p.isPassenger() && !p.isSleeping() && !p.isRemoved();
    }
    public static RenderScope render(Camera camera) {
        if(renderedCamera!=null) return RenderScope.EMPTY;
        frameCamera=null;frameOffset=Vec3.ZERO;
        LocalPlayer p=Minecraft.getInstance().player;
        if(!eligible(p) || owner!=p || level!=p.level() || camera.entity()!=p
                || !camera.isInitialized() || camera.getCapturedFrustum()!=null) {
            if(renderedCamera==null) reset();
            return RenderScope.EMPTY;
        }
        long now=System.nanoTime();
        // Resume after menus/stalls without showing an old visual correction.
        if(lastSample!=0 && now-lastSample>1_000_000_000L) smoothing.reset();
        lastSample=now;
        Vec3 offset=smoothing.offset(now);
        if(offset.lengthSqr()<1e-12) return RenderScope.EMPTY;
        Vec3 original=camera.position();
        Vec3 target=original.add(offset);
        if(!p.level().hasChunkAt(BlockPos.containing(target))) return RenderScope.EMPTY;
        // Preserve camera collision while easing near a wall; no entities or packets are moved.
        var hit=p.level().clip(new ClipContext(original,target,ClipContext.Block.VISUAL,ClipContext.Fluid.NONE,p));
        if(hit.getType()!=HitResult.Type.MISS) {
            double length=offset.length();
            double allowed=Math.max(0,original.distanceTo(hit.getLocation())-.15);
            offset=offset.scale(Math.min(1,allowed/length));
        }
        RenderScope scope=apply(camera,offset);
        frameCamera=camera;frameOffset=offset;
        return scope;
    }
    public static RenderScope draw(Camera camera) {
        LocalPlayer p=Minecraft.getInstance().player;
        if(renderedCamera!=null || frameCamera!=camera || frameOffset.lengthSqr()<1e-12 || !eligible(p)
                || owner!=p || level!=p.level() || camera.entity()!=p || camera.getCapturedFrustum()!=null) return RenderScope.EMPTY;
        // Iris and other renderers still query the live camera during drawing. Use the exact
        // extracted offset; do not decay/reclip it again or shift an already extracted model.
        return apply(camera,frameOffset);
    }
    static RenderScope apply(Camera camera,Vec3 offset) {
        Vec3 original=camera.position();
        var frustum=camera.getCullFrustum();
        Vec3 frustumOrigin=new Vec3(frustum.getCamX(),frustum.getCamY(),frustum.getCamZ());
        var scope=new RenderScope(camera,original,frustumOrigin);
        renderedOffset=offset;renderedCamera=camera;
        try {setPosition(camera,original.add(offset));return scope;}
        catch(RuntimeException|Error failure) {scope.close();throw failure;}
    }
    public static void shiftPlayer(Entity entity,EntityRenderState state) {
        if(renderedCamera==null || entity!=owner || renderedCamera.entity()!=entity) return;
        state.x+=renderedOffset.x;state.y+=renderedOffset.y;state.z+=renderedOffset.z;
        state.distanceToCameraSq=renderedCamera.position().distanceToSqr(state.x,state.y,state.z);
    }
    private static void setPosition(Camera camera,Vec3 position) {
        ((CameraPositionAccess)camera).twb$setRenderPosition(position);
        camera.getCullFrustum().prepare(position.x,position.y,position.z);
    }
    public static final class RenderScope implements AutoCloseable {
        private static final RenderScope EMPTY=new RenderScope(null,null,null);
        private Camera camera;
        private final Vec3 original;
        private final Vec3 frustumOrigin;
        private RenderScope(Camera camera,Vec3 original,Vec3 frustumOrigin) {this.camera=camera;this.original=original;this.frustumOrigin=frustumOrigin;}
        public void freeze(net.minecraft.client.renderer.state.level.CameraRenderState state) {
            // Vanilla stores Camera.blockPosition() directly, aliasing its MutableBlockPos.
            if(camera!=null && state.blockPos!=null) state.blockPos=state.blockPos.immutable();
        }
        @Override public void close() {
            if(camera==null) return;
            try {
                ((CameraPositionAccess)camera).twb$setRenderPosition(original);
                camera.getCullFrustum().prepare(frustumOrigin.x,frustumOrigin.y,frustumOrigin.z);
            }
            finally {camera=null;renderedCamera=null;renderedOffset=Vec3.ZERO;}
        }
    }
}
