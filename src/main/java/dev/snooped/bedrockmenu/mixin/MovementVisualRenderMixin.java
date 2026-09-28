package dev.snooped.bedrockmenu.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.snooped.bedrockmenu.MovementVisualCorrection;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(GameRenderer.class)
public abstract class MovementVisualRenderMixin {
    @WrapMethod(method="renderLevel")
    private void twb$drawCorrection(DeltaTracker delta,Operation<Void> original) {
        try(var scope=MovementVisualCorrection.draw(((GameRenderer)(Object)this).mainCamera())) {
            original.call(delta);
        }
    }
    @WrapMethod(method="extract")
    private void twb$renderCorrection(DeltaTracker delta,boolean renderLevel,Operation<Void> original) {
        // This is after picking and before render-state extraction. Restore even if another mod throws.
        if(!renderLevel) {original.call(delta,false);return;}
        GameRenderer renderer=(GameRenderer)(Object)this;
        try(var scope=MovementVisualCorrection.render(renderer.mainCamera())) {
            try {original.call(delta,true);}
            finally {scope.freeze(renderer.gameRenderState().levelRenderState.cameraRenderState);}
        }
    }
}
