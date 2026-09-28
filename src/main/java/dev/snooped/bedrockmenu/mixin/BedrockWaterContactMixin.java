package dev.snooped.bedrockmenu.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.snooped.bedrockmenu.BedrockMovement;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityFluidInteraction;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityFluidInteraction.class)
public abstract class BedrockWaterContactMixin {
    @WrapOperation(method="update",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/material/FluidState;isEmpty()Z"))
    private boolean togetherWithBedrock$waterProbe(FluidState fluid,Operation<Boolean> original,
            @Local(argsOnly=true) Entity entity,@Local BlockPos.MutableBlockPos position) {
        // Bedrock tests water against a vertically inset body probe. Java starts
        // dragging the player as soon as their feet touch the surface. Keep the
        // existing scan/currents, and leave lava and other entities untouched.
        return original.call(fluid) || !BedrockMovement.waterContact(entity,fluid,position);
    }
}
