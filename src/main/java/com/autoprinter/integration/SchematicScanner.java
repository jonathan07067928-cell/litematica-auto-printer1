package com.autoprinter.integration;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Cheap, bounded scans around the player. Never scans the whole schematic. */
public final class SchematicScanner {

    private SchematicScanner() {
    }

    /**
     * Counts positions inside a cube around {@code center} where the schematic wants a
     * block that the real world does not have. Schematic air is ignored (we never break blocks).
     */
    public static int countMissingNear(SchematicAccess schematic, Level world, BlockPos center, int radius) {
        int missing = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    BlockState required = schematic.getRequiredState(pos);
                    if (required == null || required.isAir()) {
                        continue;
                    }
                    if (!required.equals(world.getBlockState(pos))) {
                        missing++;
                    }
                }
            }
        }
        return missing;
    }
}
