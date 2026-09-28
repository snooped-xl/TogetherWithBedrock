// SPDX-License-Identifier: GPL-3.0-or-later
package dev.snooped.bedrockhostskin.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The co-located Geyser already delivers signed skins through Floodgate's plugin channel. */
@Pseudo
@Mixin(targets = "org.geysermc.geyser.session.GeyserSessionAdapter", remap = false)
public abstract class HostedSkinSubscriptionMixin {
    @Redirect(method = "packetSending", at = @At(value = "INVOKE",
            target = "Lorg/geysermc/geyser/skin/FloodgateSkinUploader;isAllowSubscribers()Z"), require = 0)
    private boolean togetherWithBedrock$useExistingSkinPublisher(@Coerce Object uploader) {
        // Avoid a second websocket authenticating with a stale uploader code. This only skips
        // skin subscription hints in the encrypted handshake: Floodgate login/key checks remain.
        // Geyser's sole-subscriber path forwards signed floodgate:skin after the player spawns.
        return false;
    }
}
