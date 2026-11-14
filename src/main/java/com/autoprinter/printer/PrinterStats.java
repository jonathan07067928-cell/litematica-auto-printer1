package com.autoprinter.printer;

import java.util.ArrayDeque;
import java.util.List;

/** Counters shown on the HUD and settings screen. */
public final class PrinterStats {
    private static final int SPEED_WINDOW_TICKS = 100; // 5 seconds

    private final ArrayDeque<Long> recentPlacements = new ArrayDeque<>();
    private int placed;
    private int failed;
    private List<String> missingItems = List.of();

    /** Blocks the server-bound interaction accepted (client side result). */
    public int getPlaced() {
        return placed;
    }

    public int getFailed() {
        return failed;
    }

    public List<String> getMissingItems() {
        return missingItems;
    }

    void recordPlacement(long tick) {
        placed++;
        recentPlacements.addLast(tick);
    }

    void recordFailure() {
        failed++;
    }

    void setMissingItems(List<String> names) {
        this.missingItems = List.copyOf(names);
    }

    /** Placements per second over the last five seconds. */
    public double getPlacementsPerSecond(long nowTick) {
        while (!recentPlacements.isEmpty() && recentPlacements.peekFirst() < nowTick - SPEED_WINDOW_TICKS) {
            recentPlacements.removeFirst();
        }
        return recentPlacements.size() / (SPEED_WINDOW_TICKS / 20.0);
    }

    void reset() {
        recentPlacements.clear();
        placed = 0;
        failed = 0;
        missingItems = List.of();
    }
}
