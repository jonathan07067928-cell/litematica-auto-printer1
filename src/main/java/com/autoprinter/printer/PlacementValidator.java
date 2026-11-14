package com.autoprinter.printer;

import com.autoprinter.config.PrinterConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.Set;

/**
 * Decides whether a block can be placed right now, and how.
 *
 * How orientation works without cheating: for every candidate click we ask
 * Minecraft's own placement code ({@code getStateForPlacement}) which block
 * state would result, with the player looking exactly along the ray that
 * produces that click. We only click when the result equals the schematic's
 * state. Nothing is faked; positions that cannot be reached this way are skipped.
 */
public final class PlacementValidator {

    private static final Direction[] FACE_ORDER = {
            Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP
    };
    private static final double[] OFFSETS = {0.0, -0.3, 0.3};

    /** Properties that change by themselves after placing (power, leaf distance...). */
    private static final Set<String> IGNORED_PROPERTIES = Set.of("powered", "triggered", "power", "distance", "persistent");
    /** Connection flags that fences and walls recompute from their neighbours. */
    private static final Set<String> CONNECTION_PROPERTIES = Set.of("north", "east", "south", "west", "up");

    private PlacementValidator() {
    }

    /** True if both states are the same block with the same relevant properties. */
    public static boolean statesMatch(BlockState actual, BlockState required) {
        if (actual.getBlock() != required.getBlock()) {
            return false;
        }
        boolean relaxConnections = required.is(BlockTags.FENCES) || required.is(BlockTags.WALLS);
        for (Property<?> property : required.getProperties()) {
            String name = property.getName();
            if (IGNORED_PROPERTIES.contains(name)) {
                continue;
            }
            if (relaxConnections && CONNECTION_PROPERTIES.contains(name)) {
                continue;
            }
            if (!sameValue(actual, required, property)) {
                return false;
            }
        }
        return true;
    }

    private static <T extends Comparable<T>> boolean sameValue(BlockState a, BlockState b, Property<T> property) {
        return a.hasProperty(property) && a.getValue(property).equals(b.getValue(property));
    }

    /** The largest distance we allow for a click: our setting, capped by the player's real reach. */
    public static double effectiveReach(LocalPlayer player, PrinterConfig config) {
        return Math.min(config.placementRange, player.blockInteractionRange());
    }

    /** Blocks we can safely click against: solid, and not something that opens or toggles when clicked. */
    static boolean isClickableSupport(BlockState state) {
        if (state.isAir() || state.canBeReplaced() || state.hasBlockEntity()) {
            return false;
        }
        if (state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS) || state.is(BlockTags.BUTTONS)
                || state.is(BlockTags.FENCE_GATES) || state.is(BlockTags.BEDS) || state.is(BlockTags.ANVIL)) {
            return false;
        }
        return state.getBlock() != Blocks.CRAFTING_TABLE
                && state.getBlock() != Blocks.LEVER
                && state.getBlock() != Blocks.NOTE_BLOCK
                && state.getBlock() != Blocks.REPEATER
                && state.getBlock() != Blocks.COMPARATOR
                && state.getBlock() != Blocks.CAKE
                && state.getBlock() != Blocks.COMPOSTER
                && state.getBlock() != Blocks.DRAGON_EGG;
    }

    /**
     * Finds a valid way to place {@code required} at {@code pos}, or null if none exists from here.
     * The player's view is restored before returning; turning it is the caller's decision.
     */
    public static PlacementPlan plan(Minecraft mc, LocalPlayer player, ClientLevel level, BlockPos pos,
                                     BlockState required, ItemStack stack, PrinterConfig config) {
        Vec3 eye = player.getEyePosition();
        double reach = effectiveReach(player, config);

        if (!config.automaticRotation) {
            return planFromCrosshair(mc, player, level, pos, required, stack, config, eye, reach);
        }

        for (Direction side : FACE_ORDER) {
            BlockPos neighbor = pos.relative(side);
            if (!isClickableSupport(level.getBlockState(neighbor))) {
                continue;
            }
            Direction face = side.getOpposite();
            for (double a : OFFSETS) {
                for (double b : OFFSETS) {
                    Vec3 target = facePoint(neighbor, face, a, b);
                    if (config.placementRangeChecking && eye.distanceToSqr(target) > reach * reach) {
                        continue;
                    }
                    BlockHitResult hit = clipTo(level, player, eye, target);
                    if (hit == null || !hit.getBlockPos().equals(neighbor) || hit.getDirection() != face) {
                        continue;
                    }
                    if (config.placementRangeChecking && eye.distanceToSqr(hit.getLocation()) > reach * reach) {
                        continue;
                    }
                    float[] look = lookAt(eye, hit.getLocation());
                    if (!simulate(player, level, pos, hit, stack, required, look[0], look[1])) {
                        continue;
                    }
                    boolean needsRotation = Math.abs(Mth.wrapDegrees(player.getYRot() - look[0])) > 0.05f
                            || Math.abs(player.getXRot() - look[1]) > 0.05f;
                    return new PlacementPlan(pos, hit, required.getBlock().asItem(), look[0], look[1], needsRotation);
                }
            }
        }
        return null;
    }

    /** Rotation disabled: only place where the player's crosshair already points. */
    private static PlacementPlan planFromCrosshair(Minecraft mc, LocalPlayer player, ClientLevel level, BlockPos pos,
                                                   BlockState required, ItemStack stack, PrinterConfig config,
                                                   Vec3 eye, double reach) {
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        if (!hit.getBlockPos().relative(hit.getDirection()).equals(pos)) {
            return null;
        }
        if (!isClickableSupport(level.getBlockState(hit.getBlockPos()))) {
            return null;
        }
        if (config.placementRangeChecking && eye.distanceToSqr(hit.getLocation()) > reach * reach) {
            return null;
        }
        if (!simulate(player, level, pos, hit, stack, required, player.getYRot(), player.getXRot())) {
            return null;
        }
        return new PlacementPlan(pos, hit, required.getBlock().asItem(), player.getYRot(), player.getXRot(), false);
    }

    /** Asks vanilla what would be placed, as if the player looked along (yaw, pitch). Restores the real view. */
    private static boolean simulate(LocalPlayer player, ClientLevel level, BlockPos pos, BlockHitResult hit,
                                    ItemStack stack, BlockState required, float yaw, float pitch) {
        float oldYaw = player.getYRot();
        float oldPitch = player.getXRot();
        player.setYRot(yaw);
        player.setXRot(pitch);
        try {
            BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit);
            if (!context.getClickedPos().equals(pos) || !context.canPlace()) {
                return false;
            }
            BlockState predicted = required.getBlock().getStateForPlacement(context);
            if (predicted == null || !statesMatch(predicted, required)) {
                return false;
            }
            return predicted.canSurvive(level, pos)
                    && level.isUnobstructed(predicted, pos, CollisionContext.of(player));
        } finally {
            player.setYRot(oldYaw);
            player.setXRot(oldPitch);
        }
    }

    /** Casts a ray like the crosshair would; returns the first block it hits, or null. */
    private static BlockHitResult clipTo(ClientLevel level, LocalPlayer player, Vec3 eye, Vec3 target) {
        Vec3 direction = target.subtract(eye);
        double length = direction.length();
        if (length < 1.0e-6) {
            return null;
        }
        Vec3 end = eye.add(direction.scale((length + 0.6) / length));
        BlockHitResult result = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return result.getType() == HitResult.Type.BLOCK ? result : null;
    }

    private static Vec3 facePoint(BlockPos block, Direction face, double a, double b) {
        Vec3 center = Vec3.atCenterOf(block);
        double sx = face.getStepX() * 0.5;
        double sy = face.getStepY() * 0.5;
        double sz = face.getStepZ() * 0.5;
        return switch (face.getAxis()) {
            case X -> center.add(sx, a, b);
            case Y -> center.add(a, sy, b);
            case Z -> center.add(a, b, sz);
        };
    }

    /** Yaw and pitch (degrees) for looking from {@code eye} at {@code target}. */
    private static float[] lookAt(Vec3 eye, Vec3 target) {
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = Mth.wrapDegrees((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
        float pitch = Mth.clamp((float) -Math.toDegrees(Math.atan2(dy, horizontal)), -90.0f, 90.0f);
        return new float[]{yaw, pitch};
    }
}
