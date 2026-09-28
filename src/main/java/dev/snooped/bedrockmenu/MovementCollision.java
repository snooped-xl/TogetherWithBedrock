package dev.snooped.bedrockmenu;

import dev.snooped.bedrockmenu.mixin.EntityCollisionAccess;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/** Minecraft 26.2 collision/step solving at a supplied box, without moving the player. */
public final class MovementCollision {
    private MovementCollision() { }

    public static Vec3 collide(LocalPlayer player, Vec3 delta, AABB box, boolean onGround) {
        Level level = player.level();
        List<VoxelShape> entityCollisions = level.getEntityCollisions(player, box.expandTowards(delta));
        Vec3 clipped = delta.lengthSqr() == 0.0D ? delta
                : Entity.collideBoundingBox(player, delta, box, level, entityCollisions);
        boolean blockedX = delta.x != clipped.x;
        boolean blockedY = delta.y != clipped.y;
        boolean blockedZ = delta.z != clipped.z;
        boolean landed = blockedY && delta.y < 0.0D;
        float maxStep = player.maxUpStep();

        if (maxStep > 0.0F && (landed || onGround) && (blockedX || blockedZ)) {
            AABB stepBox = landed ? box.move(0.0D, clipped.y, 0.0D) : box;
            AABB searchBox = stepBox.expandTowards(delta.x, maxStep, delta.z);
            if (!landed) {
                // Match the native float constant before widening it to a double.
                searchBox = searchBox.expandTowards(0.0D, (double) -1.0E-5F, 0.0D);
            }
            List<VoxelShape> colliders = EntityCollisionAccess.togetherWithBedrock$collectCollidersIgnoringWorldBorder(
                    player, level, entityCollisions, searchBox);
            float[] candidates = EntityCollisionAccess.togetherWithBedrock$collectCandidateStepUpHeights(
                    stepBox, colliders, maxStep, (float) clipped.y);
            for (float height : candidates) {
                Vec3 stepped = EntityCollisionAccess.togetherWithBedrock$collideWithShapes(
                        new Vec3(delta.x, height, delta.z), stepBox, colliders);
                if (stepped.horizontalDistanceSqr() > clipped.horizontalDistanceSqr()) {
                    return stepped.subtract(0.0D, box.minY - stepBox.minY, 0.0D);
                }
            }
        }
        return clipped;
    }
}
