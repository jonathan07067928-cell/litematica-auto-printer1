package com.autoprinter.printer;

import com.autoprinter.config.ConfigManager;
import com.autoprinter.config.PrinterConfig;
import com.autoprinter.integration.SchematicAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds missing blocks near the player and places them through the normal
 * client interaction ({@code MultiPlayerGameMode.useItemOn}), which makes
 * vanilla build and send its usual packets.
 *
 * Work per tick is bounded. Candidate lists are rebuilt every few ticks and
 * every candidate is re-checked against the live world right before use, so
 * outside changes are picked up automatically. Failed spots back off
 * exponentially, so nothing can loop forever.
 */
public final class PlacementEngine {

    public enum Outcome {
        PLACING,
        NOTHING_TO_PLACE,
        OUT_OF_MATERIALS,
        WAITING
    }

    private static final int RESCAN_INTERVAL_TICKS = 10;
    private static final int MAX_CANDIDATES = 600;
    private static final int MAX_EXAMINED_PER_TICK = 24;
    private static final int MAX_RETRY_DELAY_TICKS = 400;
    private static final int MAX_AIM_ATTEMPTS = 4;

    private record RetryState(int failures, long nextTick) {
    }

    private final ConfigManager configManager;
    private final InventoryManager inventory = new InventoryManager();
    private final PrinterStats stats = new PrinterStats();
    private final List<BlockPos> candidates = new ArrayList<>();
    private final Map<Long, RetryState> retries = new HashMap<>();

    private PlacementPlan pending;
    private int pendingAttempts;
    private long tick;
    private int cooldown;
    private int rescanCountdown;

    public PlacementEngine(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public PrinterStats getStats() {
        return stats;
    }

    /** Missing blocks currently within reach of the player. */
    public int getCandidateCount() {
        return candidates.size();
    }

    public long getTick() {
        return tick;
    }

    public void cancelPending() {
        pending = null;
        pendingAttempts = 0;
    }

    /** Clears everything. Called on disconnect, world change, or when the printer is switched off. */
    public void reset() {
        candidates.clear();
        retries.clear();
        pending = null;
        pendingAttempts = 0;
        cooldown = 0;
        rescanCountdown = 0;
        stats.reset();
    }

    public Outcome tick(Minecraft mc, SchematicAccess schematic) {
        tick++;
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || mc.gameMode == null) {
            return Outcome.WAITING;
        }
        if (mc.screen != null || !player.mayBuild() || player.isSpectator()) {
            cancelPending();
            return Outcome.WAITING;
        }

        PrinterConfig config = configManager.get();

        if (--rescanCountdown <= 0) {
            rescan(player, level, schematic, config);
            rescanCountdown = RESCAN_INTERVAL_TICKS;
        }
        if (candidates.isEmpty()) {
            cancelPending();
            return Outcome.NOTHING_TO_PLACE;
        }
        if (cooldown > 0) {
            cooldown--;
            return Outcome.PLACING;
        }
        if (pending != null) {
            return continuePending(mc, player, level, schematic, config);
        }
        return searchAndPlace(mc, player, level, schematic, config);
    }

    // ------------------------------------------------------------------ search

    private Outcome searchAndPlace(Minecraft mc, LocalPlayer player, ClientLevel level,
                                   SchematicAccess schematic, PrinterConfig config) {
        int budget = config.maxBlocksPerTick;
        int placed = 0;
        int examined = 0;
        boolean sawMissing = false;
        boolean sawAvailable = false;

        for (BlockPos pos : candidates) {
            if (examined >= MAX_EXAMINED_PER_TICK || placed >= budget) {
                break;
            }
            long key = pos.asLong();
            RetryState retry = retries.get(key);
            if (retry != null && retry.nextTick() > tick) {
                continue;
            }
            examined++;

            BlockState required = schematic.getRequiredState(pos);
            BlockState actual = level.getBlockState(pos);
            if (required == null || required.isAir() || !actual.canBeReplaced()
                    || PlacementValidator.statesMatch(actual, required)) {
                continue; // outdated candidate, the next rescan drops it
            }
            Item item = required.getBlock().asItem();
            if (item == Items.AIR) {
                recordFailure(key);
                continue;
            }

            int slot = inventory.findHotbarSlot(player, item);
            boolean usable = slot >= 0 && (config.autoHotbarSelection || slot == inventory.selectedSlot(player));
            if (!usable) {
                sawMissing = true;
                if (!config.skipUnavailableBlocks) {
                    return Outcome.OUT_OF_MATERIALS;
                }
                continue;
            }
            sawAvailable = true;

            ItemStack stack = inventory.hotbarStack(player, slot);
            PlacementPlan plan = PlacementValidator.plan(mc, player, level, pos, required, stack, config);
            if (plan == null) {
                recordFailure(key);
                continue;
            }
            if (plan.needsRotation()) {
                aim(player, plan);
                pending = plan;
                pendingAttempts = 1;
                return Outcome.PLACING; // placed on the next tick, after vanilla sent the new view
            }
            if (place(mc, player, plan, config)) {
                placed++;
            } else {
                recordFailure(key);
            }
        }

        if (placed > 0) {
            cooldown = Math.max(0, intervalTicks(config) - 1);
        }
        if (placed == 0 && sawMissing && !sawAvailable) {
            return Outcome.OUT_OF_MATERIALS;
        }
        return Outcome.PLACING;
    }

    // ----------------------------------------------------------------- pending

    private Outcome continuePending(Minecraft mc, LocalPlayer player, ClientLevel level,
                                    SchematicAccess schematic, PrinterConfig config) {
        PlacementPlan old = pending;
        BlockPos pos = old.pos();
        long key = pos.asLong();

        BlockState required = schematic.getRequiredState(pos);
        BlockState actual = level.getBlockState(pos);
        if (required == null || required.isAir() || !actual.canBeReplaced()
                || PlacementValidator.statesMatch(actual, required)) {
            cancelPending();
            return Outcome.PLACING; // world changed, nothing to do here any more
        }
        int slot = inventory.findHotbarSlot(player, old.item());
        if (slot < 0) {
            cancelPending();
            return Outcome.PLACING;
        }

        PlacementPlan fresh = PlacementValidator.plan(mc, player, level, pos, required, inventory.hotbarStack(player, slot), config);
        if (fresh == null) {
            cancelPending();
            recordFailure(key);
            return Outcome.PLACING;
        }
        if (fresh.needsRotation()) {
            if (++pendingAttempts > MAX_AIM_ATTEMPTS) {
                cancelPending();
                recordFailure(key);
                return Outcome.PLACING;
            }
            aim(player, fresh);
            pending = fresh;
            return Outcome.PLACING;
        }

        cancelPending();
        if (place(mc, player, fresh, config)) {
            cooldown = Math.max(0, intervalTicks(config) - 1);
        } else {
            recordFailure(key);
        }
        return Outcome.PLACING;
    }

    // ------------------------------------------------------------------- place

    private void aim(LocalPlayer player, PlacementPlan plan) {
        player.setYRot(plan.yaw());
        player.setXRot(plan.pitch());
    }

    /** Performs the normal right-click on the block face. Returns true if the client interaction was accepted. */
    private boolean place(Minecraft mc, LocalPlayer player, PlacementPlan plan, PrinterConfig config) {
        int slot = inventory.findHotbarSlot(player, plan.item());
        if (slot < 0) {
            return false;
        }
        if (slot != inventory.selectedSlot(player)) {
            if (!config.autoHotbarSelection) {
                return false;
            }
            inventory.select(player, slot);
        }
        InteractionResult result = mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, plan.hit());
        if (result.consumesAction()) {
            player.swing(InteractionHand.MAIN_HAND);
            stats.recordPlacement(tick);
            retries.remove(plan.pos().asLong());
            return true;
        }
        return false;
    }

    // -------------------------------------------------------------------- scan

    private void rescan(LocalPlayer player, ClientLevel level, SchematicAccess schematic, PrinterConfig config) {
        candidates.clear();
        double range = PlacementValidator.effectiveReach(player, config);
        int radius = (int) Math.ceil(range) + 1;
        double maxDistSq = (range + 1.0) * (range + 1.0);
        BlockPos center = player.blockPosition();

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        List<BlockPos> found = new ArrayList<>();
        Set<Item> needed = new LinkedHashSet<>();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (center.distSqr(cursor) > maxDistSq) {
                        continue;
                    }
                    BlockState required = schematic.getRequiredState(cursor);
                    if (required == null || required.isAir()) {
                        continue;
                    }
                    BlockState actual = level.getBlockState(cursor);
                    if (!actual.canBeReplaced() || PlacementValidator.statesMatch(actual, required)) {
                        continue;
                    }
                    Item item = required.getBlock().asItem();
                    if (item == Items.AIR) {
                        continue;
                    }
                    found.add(cursor.immutable());
                    needed.add(item);
                }
            }
        }

        // Build from the bottom up, nearest first.
        found.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).thenComparingDouble(center::distSqr));
        candidates.addAll(found.size() > MAX_CANDIDATES ? found.subList(0, MAX_CANDIDATES) : found);

        List<String> missing = new ArrayList<>();
        if (config.inventoryChecking) {
            for (Item item : needed) {
                if (inventory.findHotbarSlot(player, item) < 0) {
                    missing.add(item.getDefaultInstance().getHoverName().getString());
                }
            }
        }
        stats.setMissingItems(missing);

        long cutoff = tick - 200;
        retries.entrySet().removeIf(entry -> entry.getValue().nextTick() < cutoff);
    }

    // ----------------------------------------------------------------- helpers

    /** Ticks between placement bursts, from "speed" (per second) and "delay" (ticks); the slower wins. */
    private static int intervalTicks(PrinterConfig config) {
        int fromSpeed = (int) Math.ceil(20.0 / config.printingSpeed);
        return Math.max(1, Math.max(config.delayBetweenBlocksTicks, fromSpeed));
    }

    private void recordFailure(long key) {
        RetryState old = retries.get(key);
        int failures = (old == null ? 0 : old.failures()) + 1;
        long delay = Math.min(MAX_RETRY_DELAY_TICKS, 10L << Math.min(failures, 6));
        retries.put(key, new RetryState(failures, tick + delay));
        stats.recordFailure();
    }
}
