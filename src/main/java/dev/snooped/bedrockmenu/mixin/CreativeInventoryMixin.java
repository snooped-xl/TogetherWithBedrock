// SPDX-License-Identifier: GPL-3.0-or-later
package dev.snooped.bedrockmenu.mixin;

import dev.snooped.bedrockmenu.CreativeBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeInventoryMixin {
    @WrapMethod(method = "slotClicked")
    private void togetherCreativeClick(Slot slot, int slotId, int button, ContainerInput input, Operation<Void> original) {
        if (CreativeBridge.closingInventory()) return;
        boolean captured = CreativeBridge.begin();
        try {
            original.call(slot, slotId, button, input);
            if (captured) CreativeBridge.end();
        } finally {
            if (captured) CreativeBridge.abort();
        }
    }
    @WrapMethod(method = "handleHotbarLoadOrSave")
    private static void togetherCreativeHotbar(Minecraft client, int index, boolean load, boolean save, Operation<Void> original) {
        if (load && CreativeBridge.closingInventory()) return;
        boolean captured = load && CreativeBridge.begin();
        try {
            original.call(client, index, load, save);
            if (captured) CreativeBridge.end();
        } finally {
            if (captured) CreativeBridge.abort();
        }
    }
}
