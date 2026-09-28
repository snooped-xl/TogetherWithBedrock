package dev.snooped.bedrockmenu.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.snooped.bedrockmenu.BedrockMovement;
import dev.snooped.bedrockmenu.BedrockSwimState;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class BedrockSwimStateMixin {
    @Inject(method = "aiStep", at = @At("HEAD"))
    private void togetherWithBedrock$advanceSwimTransition(CallbackInfo ci) {
        if ((Object) this instanceof LocalPlayer player && BedrockMovement.physics(player)) {
            BedrockSwimState.beginTick(player);
        }
    }

    @WrapOperation(method = "travelInWater", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isSprinting()Z"))
    private boolean togetherWithBedrock$keepStopSwimmingDrag(LivingEntity entity, Operation<Boolean> original) {
        boolean sprinting = original.call(entity);
        return sprinting || entity instanceof LocalPlayer player && BedrockMovement.physics(player)
                && BedrockSwimState.stoppedThisTick(player);
    }
}
