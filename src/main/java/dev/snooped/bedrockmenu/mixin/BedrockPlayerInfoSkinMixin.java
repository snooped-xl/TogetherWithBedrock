package dev.snooped.bedrockmenu.mixin;

import dev.snooped.bedrockmenu.BedrockSkins;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerInfo.class)
public abstract class BedrockPlayerInfoSkinMixin {
    @Inject(method="getSkin",at=@At("RETURN"),cancellable=true)
    private void bedrockMenu$skin(CallbackInfoReturnable<PlayerSkin> cir) {
        PlayerSkin skin=BedrockSkins.lookup(((PlayerInfo)(Object)this).getProfile().id());
        if(skin!=null) cir.setReturnValue(skin);
    }
}
