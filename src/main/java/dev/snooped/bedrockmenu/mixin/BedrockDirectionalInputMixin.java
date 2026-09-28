package dev.snooped.bedrockmenu.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.snooped.bedrockmenu.BedrockMovement;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LocalPlayer.class)
public abstract class BedrockDirectionalInputMixin {
    @WrapOperation(method="modifyInput",at=@At(value="INVOKE",target="Lnet/minecraft/client/player/LocalPlayer;modifyInputSpeedForSquareMovement(Lnet/minecraft/world/phys/Vec2;)Lnet/minecraft/world/phys/Vec2;"))
    private Vec2 togetherWithBedrock$circularInput(Vec2 input, Operation<Vec2> original) {
        // Java's square-input expansion turns diagonal crouch 0.208 into 0.294 per axis.
        // Preserve the already normalized, slowed input used by Bedrock and camera mods.
        return BedrockMovement.usesBedrockInput((LocalPlayer)(Object)this) ? input : original.call(input);
    }
}
