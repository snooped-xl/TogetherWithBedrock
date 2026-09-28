// SPDX-License-Identifier: GPL-3.0-or-later
package dev.snooped.bedrockmenu.mixin;

import dev.snooped.bedrockmenu.CreativeBridge;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class CreativeInventorySyncMixin {
    // Run after vanilla has transferred packet handling onto the game thread.
    private static final String THREAD_GATE = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V";
    @Inject(method = "handleContainerSetSlot", at = @At(value = "INVOKE", target = THREAD_GATE, shift = At.Shift.AFTER), cancellable = true)
    private void togetherDeferSlot(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        if (packet.getContainerId() == 0 && CreativeBridge.deferInventoryUpdate()) ci.cancel();
    }
    @Inject(method = "handleContainerContent", at = @At(value = "INVOKE", target = THREAD_GATE, shift = At.Shift.AFTER), cancellable = true)
    private void togetherDeferContent(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        if (packet.containerId() == 0 && CreativeBridge.deferInventoryUpdate()) ci.cancel();
    }
    @Inject(method = "handleSetPlayerInventory", at = @At(value = "INVOKE", target = THREAD_GATE, shift = At.Shift.AFTER), cancellable = true)
    private void togetherDeferInventory(ClientboundSetPlayerInventoryPacket packet, CallbackInfo ci) {
        if (CreativeBridge.deferInventoryUpdate()) ci.cancel();
    }
    @Inject(method = "handleSetCursorItem", at = @At(value = "INVOKE", target = THREAD_GATE, shift = At.Shift.AFTER), cancellable = true)
    private void togetherDeferCursor(ClientboundSetCursorItemPacket packet, CallbackInfo ci) {
        if (CreativeBridge.deferInventoryUpdate()) ci.cancel();
    }
}
