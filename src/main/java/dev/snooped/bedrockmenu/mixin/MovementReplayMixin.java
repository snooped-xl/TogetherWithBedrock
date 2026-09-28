package dev.snooped.bedrockmenu.mixin;
import dev.snooped.bedrockmenu.MovementReplay;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LocalPlayer.class)
public abstract class MovementReplayMixin {
    @Inject(method="tick",at=@At("HEAD"))
    private void twb$begin(CallbackInfo ci) {MovementReplay.begin((LocalPlayer)(Object)this);}
}
