package dev.snooped.bedrockmenu.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.snooped.bedrockmenu.ItemPhysicDropCompatibility;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LocalPlayer.class)
public abstract class ItemPhysicDropMixin {
    // WrapMethod surrounds other mods' HEAD injections as well as vanilla's drop implementation.
    @WrapMethod(method = "drop(Z)Z")
    private boolean togetherWithBedrock$vanillaDropOnUnsupportedServer(boolean wholeStack, Operation<Boolean> original) {
        try (var scope = ItemPhysicDropCompatibility.vanillaDropScope()) {
            return original.call(wholeStack);
        }
    }
}
