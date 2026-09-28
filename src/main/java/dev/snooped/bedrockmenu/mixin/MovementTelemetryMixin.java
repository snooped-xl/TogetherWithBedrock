// SPDX-License-Identifier: GPL-3.0-or-later
package dev.snooped.bedrockmenu.mixin;

import dev.snooped.bedrockmenu.BedrockMenu;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MovementTelemetryMixin {
    // This exact field read is immediately before the vanilla tick-end send, after
    // the world/player tick. END_CLIENT_TICK would be too late for the same packet.
    @Inject(method = "tick", at = @At(value = "FIELD",
            target = "Lnet/minecraft/network/protocol/game/ServerboundClientTickEndPacket;INSTANCE:Lnet/minecraft/network/protocol/game/ServerboundClientTickEndPacket;",
            shift = At.Shift.BEFORE))
    private void bedrockMovementBeforeTickEnd(CallbackInfo ci) {
        if (BedrockMenu.INSTANCE != null) BedrockMenu.INSTANCE.sendMovementTelemetry();
    }
}
