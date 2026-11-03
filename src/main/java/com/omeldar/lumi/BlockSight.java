package com.omeldar.lumi;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.RaycastContext;

/**
 * Keeps block reads from working as X-ray for players without authority on a server.
 *
 * <p>A client holds far more of the world than its player can see, including every buried ore
 * in loaded chunks. For a survival or adventure player with no command permission on a server,
 * reads therefore only return what that player could see from where they stand. Singleplayer,
 * opped or permitted players, and creative or spectator players are not limited.
 */
public final class BlockSight {
    /** Largest scan radius when limited. Line-of-sight checks run on the client thread. */
    public static final int LIMITED_SCAN_RADIUS = 64;

    private BlockSight() {}

    /** True when this player's block reads must be limited to what they can see. */
    public static boolean limited(MinecraftClient mc) {
        if (mc == null || mc.isInSingleplayer() || CommandPermissions.allowed(mc)) return false;
        if (mc.interactionManager == null) return true;
        GameMode mode = mc.interactionManager.getCurrentGameMode();
        return mode != GameMode.CREATIVE && mode != GameMode.SPECTATOR;
    }

    /** True if an unbroken line runs from the player's eyes to this block. */
    public static boolean canSee(MinecraftClient mc, BlockPos pos) {
        if (mc.player == null || mc.world == null) return false;
        Vec3d eye = mc.player.getEyePos();
        Vec3d centre = Vec3d.ofCenter(pos);
        BlockState state = mc.world.getBlockState(pos);

        // Air, plants, glass and the like: look at the middle of the block itself.
        if (!state.isOpaqueFullCube() && clearLine(mc, eye, centre, pos)) return true;

        // Solid blocks: a face counts only if it points at the player and nothing covers it.
        for (Direction side : Direction.values()) {
            Vec3d normal = new Vec3d(side.getOffsetX(), side.getOffsetY(), side.getOffsetZ());
            Vec3d face = centre.add(normal.multiply(0.5));
            if (eye.subtract(face).dotProduct(normal) <= 0) continue;
            if (mc.world.getBlockState(pos.offset(side)).isOpaqueFullCube()) continue;
            // Aim just inside the face, so the ray ends on this block and not its neighbour.
            if (clearLine(mc, eye, centre.add(normal.multiply(0.49)), pos)) return true;
        }
        return false;
    }

    private static boolean clearLine(MinecraftClient mc, Vec3d from, Vec3d to, BlockPos target) {
        BlockHitResult hit = mc.world.raycast(new RaycastContext(from, to,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(target);
    }
}
