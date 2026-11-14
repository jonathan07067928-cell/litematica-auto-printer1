package com.autoprinter.printer;

import com.autoprinter.config.ConfigManager;
import com.autoprinter.config.PrinterConfig;
import com.autoprinter.integration.SchematicAccess;
import com.autoprinter.integration.SchematicProvider;
import com.autoprinter.util.ChatUtil;
import net.minecraft.client.Minecraft;

/**
 * Owns the printer's state and drives the {@link PlacementEngine} once per client tick.
 * Keybinds, the settings screen and the HUD all talk to this class.
 */
public final class PrinterController {

    public enum Status {
        DISABLED("Disabled"),
        READY("Ready"),
        PRINTING("Printing"),
        NOTHING_TO_PLACE("Nothing left in reach"),
        OUT_OF_MATERIALS("Missing blocks in hotbar"),
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

    private final ConfigManager configManager;
    private final SchematicProvider schematics;
    private final PlacementEngine engine;

    private boolean paused;
    private Status environmentStatus = Status.READY;
    private Status lastNotified;
    private int tickCounter;
    private String schematicName = "(none)";

    public PrinterController(ConfigManager configManager, SchematicProvider schematics) {
        this.configManager = configManager;
        this.schematics = schematics;
        this.engine = new PlacementEngine(configManager);
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

    public PrinterStats getStats() {
        return engine.getStats();
    }

    /** Missing blocks currently within reach of the player. */
    public int getRemainingInReach() {
        return engine.getCandidateCount();
    }

    public double getPlacementsPerSecond() {
        return engine.getStats().getPlacementsPerSecond(engine.getTick());
    }

    public String getSchematicName() {
        return schematicName;
    }

    public void setEnabled(boolean enabled) {
        config().printerEnabled = enabled;
        if (!enabled) {
            paused = false;
            lastNotified = null;
            environmentStatus = Status.READY;
            engine.reset();
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
            if (paused) {
                engine.cancelPending();
            }
        }
    }

    /** Stops printing completely: disables the printer and clears all progress. */
    public void stop() {
        setEnabled(false);
    }

    /** Called when there is no world (main menu, disconnect). */
    public void onNoWorld() {
        paused = false;
        tickCounter = 0;
        lastNotified = null;
        schematicName = "(none)";
        engine.reset();
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
        if (state == SchematicProvider.State.LITEMATICA_MISSING) {
            environmentStatus = Status.NO_LITEMATICA;
        } else if (state == SchematicProvider.State.NO_SCHEMATIC) {
            environmentStatus = Status.NO_SCHEMATIC;
        } else if (!paused) {
            SchematicAccess access = schematics.accessOrNull();
            if (access != null) {
                if (tickCounter++ % 20 == 0) {
                    schematicName = schematics.schematicName();
                }
                environmentStatus = toStatus(engine.tick(client, access));
            }
        } else if (environmentStatus == Status.NO_LITEMATICA || environmentStatus == Status.NO_SCHEMATIC) {
            environmentStatus = Status.READY;
        }

        notifyOnce(client);

        if (environmentStatus == Status.OUT_OF_MATERIALS && config().stopWhenInventoryEmpty) {
            ChatUtil.info(client, "Out of blocks in the hotbar. Printer stopped.");
            stop();
        }
    }

    private static Status toStatus(PlacementEngine.Outcome outcome) {
        return switch (outcome) {
            case PLACING, WAITING -> Status.PRINTING;
            case NOTHING_TO_PLACE -> Status.NOTHING_TO_PLACE;
            case OUT_OF_MATERIALS -> Status.OUT_OF_MATERIALS;
        };
    }

    private void notifyOnce(Minecraft client) {
        if (environmentStatus == lastNotified) {
            return;
        }
        lastNotified = environmentStatus;
        switch (environmentStatus) {
            case NO_LITEMATICA -> ChatUtil.info(client,
                    "Litematica and MaLiLib are required for schematic printing, and they could not be used.");
            case NO_SCHEMATIC -> ChatUtil.info(client,
                    "No schematic is loaded. Load and place one with Litematica first.");
            case OUT_OF_MATERIALS -> ChatUtil.info(client,
                    "Missing in hotbar: " + String.join(", ", engine.getStats().getMissingItems())
                            + ". Put the blocks into your hotbar.");
            default -> {
            }
        }
    }
}
