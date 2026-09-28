package dev.snooped.bedrockmenu.mixin;

import dev.snooped.bedrockmenu.MovementVisualCorrection;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class MovementVisualPlayerMixin {
    @Inject(method="finalizeRenderState",at=@At("HEAD"))
    private void twb$renderLocalPlayer(Entity entity,EntityRenderState state,CallbackInfo ci) {
        // Move the local third-person model and its shadow with the camera, never other players.
        MovementVisualCorrection.shiftPlayer(entity,state);
    }
}
