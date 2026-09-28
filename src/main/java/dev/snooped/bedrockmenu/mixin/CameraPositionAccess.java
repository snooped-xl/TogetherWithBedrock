package dev.snooped.bedrockmenu.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface CameraPositionAccess {
    @Invoker("setPosition") void twb$setRenderPosition(Vec3 position);
}
