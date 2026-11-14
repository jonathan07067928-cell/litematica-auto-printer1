package com.autoprinter.printer;

import com.autoprinter.config.ConfigManager;
import com.autoprinter.config.PrinterConfig;

/**
 * Owns the printer's on/off/paused state. The actual placement work is added
 * to this class in a later batch; this batch provides the state machine that
 * keybinds, the GUI and the HUD all talk to.
 */
public final class PrinterController {

    public enum Status {
        DISABLED("Disabled"),
        IDLE("Idle"),
        PAUSED("Paused");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private final ConfigManager configManager;
    private boolean paused;

    public PrinterController(ConfigManager configManager) {
        this.configManager = configManager;
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
        return paused ? Status.PAUSED : Status.IDLE;
    }

    public void setEnabled(boolean enabled) {
        config().printerEnabled = enabled;
        if (!enabled) {
            paused = false;
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

    /** Called when there is no world (main menu, disconnect). Never leaves a stale paused flag behind. */
    public void onNoWorld() {
        paused = false;
    }
}
