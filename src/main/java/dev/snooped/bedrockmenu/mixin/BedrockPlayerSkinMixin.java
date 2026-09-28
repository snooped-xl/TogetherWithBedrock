package dev.snooped.bedrockmenu.mixin;

import dev.snooped.bedrockmenu.BedrockSkins;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Also covers player-shaped entities whose Bedrock server hides the tab-list entry. */
@Mixin(AbstractClientPlayer.class)
public abstract class BedrockPlayerSkinMixin {
    @Inject(method="getSkin",at=@At("RETURN"),cancellable=true)
    private void bedrockMenu$skin(CallbackInfoReturnable<PlayerSkin> cir) {
        PlayerSkin skin=BedrockSkins.lookup(((AbstractClientPlayer)(Object)this).getUUID());
        if(skin!=null) cir.setReturnValue(skin);
    }
}
