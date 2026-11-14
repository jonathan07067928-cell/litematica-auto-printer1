package com.autoprinter.printer;

import com.autoprinter.config.ConfigManager;
import com.autoprinter.config.PrinterConfig;
import com.autoprinter.integration.SchematicAccess;
import com.autoprinter.integration.SchematicProvider;
import com.autoprinter.integration.SchematicScanner;
import com.autoprinter.util.ChatUtil;
import net.minecraft.client.Minecraft;

/**
 * Owns the printer's state. This batch adds the connection to Litematica and a
 * once-per-second scan of the area around the player. Actual block placement
 * is added in the next batch.
 */
public final class PrinterController {

    public enum Status {
        DISABLED("Disabled"),
        READY("Ready"),
        PAUSED("Paused"),
        NO_LITEMATICA("Litematica missing"),
        NO_SCHEMATIC("No schematic loaded");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private static final int SCAN_INTERVAL_TICKS = 20;

    private final ConfigManager configManager;
    private final SchematicProvider schematics;

    private boolean paused;
    private Status environmentStatus = Status.READY;
    private Status lastNotified;
    private int tickCounter;
    private int nearbyMissing;
    private String schematicName = "(none)";

    public PrinterController(ConfigManager configManager, SchematicProvider schematics) {
        this.configManager = configManager;
        this.schematics = schematics;
    }

    private PrinterConfig config() {
        return configManager.get();
    }

    public boolean isEnabled() {
        return config().printerEnabled;
    }

    public boolean isPaused() {
        return paused;
    }

    public Status getStatus() {
        if (!isEnabled()) {
            return Status.DISABLED;
        }
        if (paused) {
            return Status.PAUSED;
        }
        return environmentStatus;
    }

    /** Missing blocks within a few blocks of the player, refreshed about once per second. */
    public int getNearbyMissing() {
        return nearbyMissing;
    }

    public String getSchematicName() {
        return schematicName;
    }

    public void setEnabled(boolean enabled) {
        config().printerEnabled = enabled;
        if (!enabled) {
            paused = false;
            lastNotified = null;
        }
        configManager.save();
    }

    public void toggle() {
        setEnabled(!isEnabled());
    }

    /** Pauses or resumes. Does nothing while the printer is disabled. */
    public void togglePause() {
        if (isEnabled()) {
            paused = !paused;
        }
    }

    /** Stops printing completely: disables the printer and clears the pause flag. */
    public void stop() {
        setEnabled(false);
    }

    /** Called when there is no world (main menu, disconnect). */
    public void onNoWorld() {
        paused = false;
        nearbyMissing = 0;
        tickCounter = 0;
        lastNotified = null;
        schematicName = "(none)";
    }

    /** Runs once per client tick. */
    public void tick(Minecraft client) {
        if (client.player == null || client.level == null) {
            onNoWorld();
            return;
        }
        if (!isEnabled()) {
            return;
        }

        SchematicProvider.State state = schematics.state();
        switch (state) {
            case LITEMATICA_MISSING -> environmentStatus = Status.NO_LITEMATICA;
            case NO_SCHEMATIC -> environmentStatus = Status.NO_SCHEMATIC;
            case READY -> environmentStatus = Status.READY;
        }

        notifyOnce(client);

        if (environmentStatus != Status.READY || paused) {
            return;
        }

        if (++tickCounter % SCAN_INTERVAL_TICKS == 0) {
            SchematicAccess access = schematics.accessOrNull();
            if (access != null) {
                schematicName = schematics.schematicName();
                int radius = (int) Math.floor(config().placementRange);
                nearbyMissing = SchematicScanner.countMissingNear(access, client.level, client.player.blockPosition(), radius);
            }
        }
    }

    private void notifyOnce(Minecraft client) {
        if (environmentStatus == lastNotified) {
            return;
        }
        lastNotified = environmentStatus;
        if (environmentStatus == Status.NO_LITEMATICA) {
            ChatUtil.info(client, "Litematica and MaLiLib are required for schematic printing, and they could not be used.");
        } else if (environmentStatus == Status.NO_SCHEMATIC) {
            ChatUtil.info(client, "No schematic is loaded. Load and place one with Litematica first.");
        }
    }
}
