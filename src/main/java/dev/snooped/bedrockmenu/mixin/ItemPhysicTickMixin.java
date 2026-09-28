package dev.snooped.bedrockmenu.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.snooped.bedrockmenu.ItemPhysicDropCompatibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "team.creative.itemphysic.client.ItemPhysicClient", remap = false)
public abstract class ItemPhysicTickMixin {
    @WrapMethod(method = "gameTick")
    private static void togetherWithBedrock$skipUnsupportedChargedThrow(Operation<Void> original) {
        try (var scope = ItemPhysicDropCompatibility.vanillaDropScope()) {
            original.call();
        }
    }
}
