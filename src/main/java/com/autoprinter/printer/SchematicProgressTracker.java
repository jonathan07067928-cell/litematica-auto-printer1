package com.autoprinter.printer;

import com.autoprinter.integration.Bounds;
import com.autoprinter.integration.SchematicAccess;
import com.autoprinter.integration.SchematicProvider;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Measures how much of the schematic is built, a little each tick, so large
 * schematics never freeze the game. Each measurement walks the whole box once,
 * then waits briefly and starts again, which also picks up outside changes.
 */
public final class SchematicProgressTracker {
    private static final int FALLBACK_RADIUS = 24;
    private static final int IDLE_TICKS_BETWEEN_PASSES = 20;

    private final SchematicProvider schematics;
    private final BlockPos.MutableBlockPos cursorPos = new BlockPos.MutableBlockPos();

    private boolean passActive;
    private boolean boundsKnown;
    private int minX;
    private int minY;
    private int minZ;
    private int sizeY;
    private int sizeZ;
    private long volume;
    private long cursor;
    private long total;
    private long correct;
    private long unknown;
    private int idleTicks;

    private int cachedChunkX = Integer.MIN_VALUE;
    private int cachedChunkZ = Integer.MIN_VALUE;
    private boolean cachedLoaded;

    private ProgressSnapshot published = ProgressSnapshot.EMPTY;

    public SchematicProgressTracker(SchematicProvider schematics) {
        this.schematics = schematics;
    }

    public void reset() {
        passActive = false;
        volume = 0;
        cursor = 0;
        total = 0;
        correct = 0;
        unknown = 0;
        idleTicks = 0;
        cachedChunkX = Integer.MIN_VALUE;
        published = ProgressSnapshot.EMPTY;
    }

    /** Latest finished measurement, or the partial one while the very first pass is still running. */
    public ProgressSnapshot getSnapshot() {
        if (published != ProgressSnapshot.EMPTY) {
            return published;
        }
        if (passActive && volume > 0) {
            return new ProgressSnapshot(volume, total, correct, unknown, false, boundsKnown, (double) cursor / volume);
        }
        return ProgressSnapshot.EMPTY;
    }

    public void tick(ClientLevel level, LocalPlayer player, SchematicAccess schematic, int budget) {
        if (!passActive) {
            if (idleTicks > 0) {
                idleTicks--;
                return;
            }
            startPass(player);
        }
        if (volume <= 0) {
            passActive = false;
            return;
        }

        int steps = 0;
        while (steps < budget && cursor < volume) {
            long index = cursor++;
            steps++;

            int y = (int) (index % sizeY);
            long rest = index / sizeY;
            int z = (int) (rest % sizeZ);
            int x = (int) (rest / sizeZ);
            cursorPos.set(minX + x, minY + y, minZ + z);

            if (!isChunkLoaded(level, cursorPos)) {
                unknown++;
                continue;
            }
            BlockState required = schematic.getRequiredState(cursorPos);
            if (required == null || required.isAir() || required.getBlock().asItem() == Items.AIR) {
                continue;
            }
            total++;
            if (PlacementValidator.statesMatch(level.getBlockState(cursorPos), required)) {
                correct++;
            }
        }

        if (cursor >= volume) {
            published = new ProgressSnapshot(volume, total, correct, unknown, true, boundsKnown, 1.0);
            passActive = false;
            idleTicks = IDLE_TICKS_BETWEEN_PASSES;
        }
    }

    private void startPass(LocalPlayer player) {
        Bounds bounds = schematics.boundsOrNull();
        boundsKnown = bounds != null;
        if (bounds == null) {
            BlockPos center = player.blockPosition();
            bounds = new Bounds(center.getX() - FALLBACK_RADIUS, center.getY() - FALLBACK_RADIUS,
                    center.getZ() - FALLBACK_RADIUS, center.getX() + FALLBACK_RADIUS,
                    center.getY() + FALLBACK_RADIUS, center.getZ() + FALLBACK_RADIUS);
        }
        minX = bounds.minX();
        minY = bounds.minY();
        minZ = bounds.minZ();
        sizeY = bounds.sizeY();
        sizeZ = bounds.sizeZ();
        volume = bounds.volume();
        cursor = 0;
        total = 0;
        correct = 0;
        unknown = 0;
        passActive = true;
    }

    /** Remembers the last chunk so the loaded-check costs almost nothing inside a chunk. */
    private boolean isChunkLoaded(ClientLevel level, BlockPos pos) {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        if (chunkX != cachedChunkX || chunkZ != cachedChunkZ) {
            cachedChunkX = chunkX;
            cachedChunkZ = chunkZ;
            cachedLoaded = level.hasChunk(chunkX, chunkZ);
        }
        return cachedLoaded;
    }
}
