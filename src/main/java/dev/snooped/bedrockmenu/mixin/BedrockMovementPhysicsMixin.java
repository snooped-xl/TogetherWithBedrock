package dev.snooped.bedrockmenu.mixin;

import dev.snooped.bedrockmenu.BedrockMovement;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Constant;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.effect.MobEffects;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class BedrockMovementPhysicsMixin {
    @ModifyConstant(method="aiStep",constant=@Constant(doubleValue=9.0E-6D))
    private double togetherWithBedrock$horizontalMomentum(double threshold) {
        return BedrockMovement.physics((LivingEntity)(Object)this) ? 0D : threshold;
    }
    @ModifyConstant(method="aiStep",constant=@Constant(doubleValue=0.003D))
    private double togetherWithBedrock$momentum(double threshold) {
        return BedrockMovement.physics((LivingEntity)(Object)this) ? 0D : threshold;
    }
    @Inject(method="jumpFromGround",at=@At(value="FIELD",target="Lnet/minecraft/world/entity/LivingEntity;needsSync:Z",opcode=181,shift=At.Shift.AFTER))
    private void togetherWithBedrock$actualJump(CallbackInfo ci) {
        BedrockMovement.jumped((LivingEntity)(Object)this);
    }
    @WrapOperation(method="aiStep",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;jumpFromGround()V"))
    private void togetherWithBedrock$waterAscent(LivingEntity entity,Operation<Void> original) {
        if(BedrockMovement.physics(entity) && entity.isInWater()) BedrockMovement.liquidJump(entity);
        else original.call(entity);
    }
    @Inject(method="jumpInLiquid",at=@At("HEAD"),cancellable=true)
    private void togetherWithBedrock$liquidJump(net.minecraft.tags.TagKey<net.minecraft.world.level.material.Fluid> fluid,CallbackInfo ci) {
        LivingEntity entity=(LivingEntity)(Object)this;
        if(fluid==net.minecraft.tags.FluidTags.WATER && BedrockMovement.physics(entity)) {
            BedrockMovement.liquidJump(entity); ci.cancel();
        }
    }
    @WrapOperation(method="travelInWater",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;getFluidFallingAdjustedMovement(DZLnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 togetherWithBedrock$swimGravity(LivingEntity entity,double gravity,boolean falling,Vec3 velocity,Operation<Vec3> original) {
        if(!BedrockMovement.physics(entity)) return original.call(entity,gravity,falling,velocity);
        var levitation=entity.getEffect(MobEffects.LEVITATION);
        if(levitation!=null) return velocity.add(0,(0.05*(levitation.getAmplifier()+1)-velocity.y)*0.2,0);
        return BedrockMovement.waterGravity(velocity,entity.isSwimming(),gravity);
    }
    @Inject(method="travel",at=@At("HEAD"))
    private void togetherWithBedrock$captureEffectiveInput(Vec3 input, CallbackInfo ci) {
        BedrockMovement.captureInput((LivingEntity)(Object)this,input);
    }
    @ModifyArg(method="handleOnClimbable",at=@At(value="INVOKE",target="Ljava/lang/Math;max(DD)D"),index=1)
    private double togetherWithBedrock$ladderDescent(double minimum) {
        // The method overwrites its Vec3 argument; at RETURN the unclamped value is lost.
        // Change only its vertical clamp. Vanilla still handles crouch hold afterwards.
        return BedrockMovement.ladderPhysics((LivingEntity)(Object)this) ? -0.2D : minimum;
    }
    @Inject(method="travelInAir",at=@At("TAIL"))
    private void togetherWithBedrock$ladderAscent(Vec3 input, CallbackInfo ci) {
        LivingEntity entity=(LivingEntity)(Object)this;
        if(!BedrockMovement.ladderPhysics(entity)) return;
        Vec3 motion=entity.getDeltaMovement();
        double y=BedrockMovement.ascendingVelocity(motion.y,entity.horizontalCollision);
        if(y!=motion.y) entity.setDeltaMovement(motion.x,y,motion.z);
    }
}
