package dev.snooped.bedrockmenu.mixin;
import dev.snooped.bedrockmenu.MovementReplay;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientPacketListener.class)
public abstract class MovementReplayTeleportMixin {
    @Inject(method={"handleMovePlayer","handleRespawn"},at=@At("TAIL"))
    private void twb$teleport(CallbackInfo ci) {MovementReplay.invalidate();}
}
