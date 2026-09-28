// SPDX-License-Identifier: GPL-3.0-or-later
package dev.snooped.bedrockmenu.mixin;

import dev.snooped.bedrockmenu.ResourcePackConsent;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ResourcePackConsentMixin {
    @Shadow @Final protected Connection connection;
    @Shadow @Final protected ServerData serverData;

    @Inject(method = "handleResourcePackPush", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER), cancellable = true)
    private void togetherSkipEmptyConsentReload(ClientboundResourcePackPushPacket packet, CallbackInfo ci) {
        if (ResourcePackConsent.handle(connection, serverData, packet)) ci.cancel();
    }
}
