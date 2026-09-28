package dev.snooped.bedrockmenu.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(Entity.class)
public interface EntityCollisionAccess {
    @Invoker("collectCollidersIgnoringWorldBorder")
    static List<VoxelShape> togetherWithBedrock$collectCollidersIgnoringWorldBorder(
            Entity entity, Level level, List<VoxelShape> entityCollisions, AABB box) {
        throw new AssertionError("Entity collision invoker was not applied");
    }

    @Invoker("collectCandidateStepUpHeights")
    static float[] togetherWithBedrock$collectCandidateStepUpHeights(
            AABB box, List<VoxelShape> colliders, float maxStep, float previousY) {
        throw new AssertionError("Entity collision invoker was not applied");
    }

    @Invoker("collideWithShapes")
    static Vec3 togetherWithBedrock$collideWithShapes(Vec3 delta, AABB box, List<VoxelShape> colliders) {
        throw new AssertionError("Entity collision invoker was not applied");
    }
}
