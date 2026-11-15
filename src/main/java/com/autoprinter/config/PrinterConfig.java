package com.autoprinter.config;

/**
 * All persistent settings. Saved as JSON by {@link ConfigManager}.
 * Field names are the JSON keys, so avoid renaming them once released.
 *
 * Note: key bindings are stored by Minecraft itself (options.txt), the same
 * place as every vanilla key. Rebind them in Options > Controls > Key Binds.
 */
public class PrinterConfig {

    // ---- Printer controls ----
    /** Whether the printer is switched on. Restored after restarting Minecraft. */
    public boolean printerEnabled = false;

    /** Upper limit of placements per second (1-20). */
    public int printingSpeed = 5;

    /** Minimum number of game ticks between two placements (0-40). 20 ticks = 1 second. */
    public int delayBetweenBlocksTicks = 2;

    /** Maximum placements in a single game tick (1-4). */
    public int maxBlocksPerTick = 1;

    // ---- Inventory behaviour ----
    public boolean autoHotbarSelection = true;
    public boolean inventoryChecking = true;
    public boolean skipUnavailableBlocks = true;
    public boolean stopWhenInventoryEmpty = false;
    public boolean stopWhenSchematicComplete = true;

    // ---- Placement behaviour ----
    public boolean placementRangeChecking = true;
    /** Maximum distance (in blocks) to a block we try to place. Vanilla survival reach is 4.5. */
    public double placementRange = 4.0;
    /** Turn the player's view only when a placement needs it. */
    public boolean automaticRotation = true;

    // ---- Progress tracking ----
    /** How many schematic positions are checked per tick when measuring progress (500-50000). */
    public int scanBudgetPerTick = 6000;

    // ---- HUD ----
    public boolean hudEnabled = true;
    public int hudX = 6;
    public int hudY = 6;
    public float hudScale = 1.0f;

    /** Forces every value into a safe range. Called after loading and before saving. */
    public void sanitize() {
        printingSpeed = clamp(printingSpeed, 1, 20);
        delayBetweenBlocksTicks = clamp(delayBetweenBlocksTicks, 0, 40);
        maxBlocksPerTick = clamp(maxBlocksPerTick, 1, 4);
        placementRange = Math.max(1.0, Math.min(placementRange, 6.0));
        scanBudgetPerTick = clamp(scanBudgetPerTick, 500, 50000);
        hudX = clamp(hudX, 0, 10000);
        hudY = clamp(hudY, 0, 10000);
        hudScale = Math.max(0.5f, Math.min(hudScale, 3.0f));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }
}
