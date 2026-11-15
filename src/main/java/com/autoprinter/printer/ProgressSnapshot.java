package com.autoprinter.printer;

/**
 * Result of measuring the schematic against the world.
 *
 * @param volume       positions inside the measured box
 * @param total        blocks the schematic wants (air and non-placeable states excluded)
 * @param correct      of those, how many already match in the world
 * @param unknown      positions whose chunk is not loaded, so they could not be checked
 * @param passComplete true when the whole box has been measured at least once
 * @param boundsKnown  false when Litematica's box could not be read and only the area near the player was measured
 * @param scanFraction how far the current measurement has progressed (0..1)
 */
public record ProgressSnapshot(long volume, long total, long correct, long unknown,
                               boolean passComplete, boolean boundsKnown, double scanFraction) {

    public static final ProgressSnapshot EMPTY = new ProgressSnapshot(0, 0, 0, 0, false, false, 0.0);

    public long remaining() {
        return Math.max(0, total - correct);
    }

    public double percent() {
        return total <= 0 ? 0.0 : 100.0 * correct / total;
    }

    /** True only when everything was measured, nothing is missing, and no part was unloaded. */
    public boolean isComplete() {
        return passComplete && boundsKnown && total > 0 && remaining() == 0 && unknown == 0;
    }
}
