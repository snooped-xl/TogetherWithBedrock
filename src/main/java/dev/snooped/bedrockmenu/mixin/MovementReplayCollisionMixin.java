package dev.snooped.bedrockmenu.mixin;
import dev.snooped.bedrockmenu.MovementReplay;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Entity.class)
public abstract class MovementReplayCollisionMixin {
    @Inject(method="move",at=@At("HEAD"))
    private void twb$beforeMove(MoverType type,Vec3 delta,CallbackInfo ci) {MovementReplay.beforeMove((Entity)(Object)this,type,delta);}
    @Inject(method="move",at=@At("RETURN"))
    private void twb$afterMove(MoverType type,Vec3 delta,CallbackInfo ci) {MovementReplay.afterMove((Entity)(Object)this);}
}
