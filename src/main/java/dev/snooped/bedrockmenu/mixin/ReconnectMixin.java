// SPDX-License-Identifier: GPL-3.0-or-later
package dev.snooped.bedrockmenu.mixin;
import dev.snooped.bedrockmenu.BedrockMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Route a reconnect mod's saved logical address back through proxy startup. */
@Mixin(ConnectScreen.class)
public abstract class ReconnectMixin {
    @Inject(method="startConnecting",at=@At("HEAD"),cancellable=true)
    private static void bedrockReconnect(Screen parent,Minecraft client,ServerAddress address,ServerData data,boolean quickPlay,TransferState transfer,CallbackInfo ci) {
        if(BedrockMenu.INSTANCE!=null && BedrockMenu.INSTANCE.routeReconnect(parent,address,data,transfer))ci.cancel();
    }
}
