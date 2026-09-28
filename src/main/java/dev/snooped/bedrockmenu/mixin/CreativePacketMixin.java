// SPDX-License-Identifier: GPL-3.0-or-later
package dev.snooped.bedrockmenu.mixin;

import dev.snooped.bedrockmenu.CreativeBridge;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class CreativePacketMixin {
    @Inject(method = "send", at = @At("HEAD"), cancellable = true)
    private void togetherCaptureCreativePacket(Packet<?> packet, CallbackInfo ci) {
        if (CreativeBridge.capturePacket(packet)) ci.cancel();
    }
}
