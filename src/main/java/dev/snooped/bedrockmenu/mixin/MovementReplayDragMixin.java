package dev.snooped.bedrockmenu.mixin;
import dev.snooped.bedrockmenu.MovementReplay;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
@Mixin(LivingEntity.class)
public abstract class MovementReplayDragMixin {
    @WrapOperation(method="travelInAir",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;setDeltaMovement(DDD)V",ordinal=1))
    private void twb$air(LivingEntity entity,double x,double y,double z,Operation<Void> original,
                         @Local(index=10) float horizontal,@Local(index=11) float vertical) {
        MovementReplay.drag(entity,horizontal,vertical,horizontal);original.call(entity,x,y,z);
    }
    @WrapOperation(method="travelInWater",at=@At(value="INVOKE",target="Lnet/minecraft/world/phys/Vec3;multiply(DDD)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 twb$water(Vec3 velocity,double x,double y,double z,Operation<Vec3> original) {
        MovementReplay.drag((LivingEntity)(Object)this,x,y,z);return original.call(velocity,x,y,z);
    }
}
