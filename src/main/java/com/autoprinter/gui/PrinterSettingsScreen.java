package com.autoprinter.gui;

import com.autoprinter.LitematicaAutoPrinterClient;
import com.autoprinter.config.ConfigManager;
import com.autoprinter.config.PrinterConfig;
import com.autoprinter.hud.HudRenderer;
import com.autoprinter.printer.PrinterController;
import com.autoprinter.printer.ProgressSnapshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

/**
 * The settings screen: four pages (Status, Printing, Inventory, HUD) with live
 * numbers, Start/Stop and Pause/Resume buttons. Settings are saved when the screen closes.
 */
public final class PrinterSettingsScreen extends Screen {
    private static final int CONTENT_WIDTH = 222;
    private static final int HALF_WIDTH = 108;
    private static final int ROW = 24;
    private static final int TOP = 50;

    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_DIM = 0xFFAAAAAA;
    private static final int COLOR_BAD = 0xFFFF5555;

    private enum Page {
        STATUS("Status"),
        PRINTING("Printing"),
        INVENTORY("Inventory"),
        HUD("HUD");

        private final String label;

        Page(String label) {
            this.label = label;
        }
    }

    private final Screen parent;
    private final ConfigManager configManager;
    private final PrinterController printer;
    private Page page = Page.STATUS;

    private Button startStopButton;
    private Button pauseButton;

    public PrinterSettingsScreen(Screen parent) {
        super(Component.literal("Litematica Auto Printer"));
        this.parent = parent;
        this.configManager = LitematicaAutoPrinterClient.config();
        this.printer = LitematicaAutoPrinterClient.printer();
    }

    @Override
    protected void init() {
        PrinterConfig config = configManager.get();
        int left = this.width / 2 - CONTENT_WIDTH / 2;

        // Tabs
        Page[] pages = Page.values();
        int tabWidth = (CONTENT_WIDTH - (pages.length - 1) * 2) / pages.length;
        for (int i = 0; i < pages.length; i++) {
            Page target = pages[i];
            Button tab = Button.builder(Component.literal(target.label), button -> {
                page = target;
                rebuildWidgets();
            }).bounds(left + i * (tabWidth + 2), 18, tabWidth, 20).build();
            tab.active = target != page;
            addRenderableWidget(tab);
        }

        switch (page) {
            case STATUS -> buildStatusPage(left);
            case PRINTING -> buildPrintingPage(left, config);
            case INVENTORY -> buildInventoryPage(left, config);
            case HUD -> buildHudPage(left, config);
        }

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(left, this.height - 28, CONTENT_WIDTH, 20).build());

        HudRenderer.setPreview(page == Page.HUD);
    }

    // ------------------------------------------------------------------ pages

    private void buildStatusPage(int left) {
        startStopButton = Button.builder(Component.literal("Start Printer"), button -> {
            if (printer.isEnabled()) {
                printer.stop();
            } else {
                printer.setEnabled(true);
            }
        }).bounds(left, TOP + 100, HALF_WIDTH, 20).build();
        pauseButton = Button.builder(Component.literal("Pause"), button -> printer.togglePause())
                .bounds(left + CONTENT_WIDTH - HALF_WIDTH, TOP + 100, HALF_WIDTH, 20).build();
        addRenderableWidget(startStopButton);
        addRenderableWidget(pauseButton);
    }

    private void buildPrintingPage(int left, PrinterConfig config) {
        int y = TOP;
        addSlider(left, y, "Printing Speed", 1, 20, true, config.printingSpeed, " /s",
                value -> config.printingSpeed = (int) value);
        y += ROW;
        addSlider(left, y, "Delay Between Blocks", 0, 40, true, config.delayBetweenBlocksTicks, " ticks",
                value -> config.delayBetweenBlocksTicks = (int) value);
        y += ROW;
        addSlider(left, y, "Max Blocks Per Tick", 1, 4, true, config.maxBlocksPerTick, "",
                value -> config.maxBlocksPerTick = (int) value);
        y += ROW;
        addSlider(left, y, "Placement Range", 1.0, 6.0, false, config.placementRange, " blocks",
                value -> config.placementRange = value);
        y += ROW;
        addToggle(left, y, HALF_WIDTH, "Auto Rotation",
                () -> config.automaticRotation, value -> config.automaticRotation = value);
        addToggle(left + CONTENT_WIDTH - HALF_WIDTH, y, HALF_WIDTH, "Range Check",
                () -> config.placementRangeChecking, value -> config.placementRangeChecking = value);
    }

    private void buildInventoryPage(int left, PrinterConfig config) {
        int right = left + CONTENT_WIDTH - HALF_WIDTH;
        int y = TOP;
        addToggle(left, y, HALF_WIDTH, "Auto Hotbar",
                () -> config.autoHotbarSelection, value -> config.autoHotbarSelection = value);
        addToggle(right, y, HALF_WIDTH, "Inv. Checking",
                () -> config.inventoryChecking, value -> config.inventoryChecking = value);
        y += ROW;
        addToggle(left, y, HALF_WIDTH, "Skip Unavailable",
                () -> config.skipUnavailableBlocks, value -> config.skipUnavailableBlocks = value);
        addToggle(right, y, HALF_WIDTH, "Stop If Empty",
                () -> config.stopWhenInventoryEmpty, value -> config.stopWhenInventoryEmpty = value);
        y += ROW;
        addToggle(left, y, CONTENT_WIDTH, "Stop When Schematic Complete",
                () -> config.stopWhenSchematicComplete, value -> config.stopWhenSchematicComplete = value);
    }

    private void buildHudPage(int left, PrinterConfig config) {
        int right = left + CONTENT_WIDTH - HALF_WIDTH;
        int y = TOP;
        addToggle(left, y, CONTENT_WIDTH, "Show HUD",
                () -> config.hudEnabled, value -> config.hudEnabled = value);
        y += ROW;
        addSlider(left, y, "HUD X", 0, Math.max(1, this.width - 150), true, config.hudX, "",
                value -> config.hudX = (int) value);
        y += ROW;
        addSlider(left, y, "HUD Y", 0, Math.max(1, this.height - 80), true, config.hudY, "",
                value -> config.hudY = (int) value);
        y += ROW;
        addPreset(left, y, "Top Left", config, 6, 6);
        addPreset(right, y, "Top Right", config, this.width - 156, 6);
        y += ROW;
        addPreset(left, y, "Bottom Left", config, 6, this.height - 84);
        addPreset(right, y, "Bottom Right", config, this.width - 156, this.height - 84);
    }

    // ---------------------------------------------------------------- widgets

    private void addSlider(int x, int y, String label, double min, double max, boolean integer,
                           double current, String suffix, DoubleConsumer onChange) {
        addRenderableWidget(new ConfigSlider(x, y, CONTENT_WIDTH, 20, label, min, max, integer, current, suffix, onChange));
    }

    private void addToggle(int x, int y, int width, String label, BooleanSupplier getter, Consumer<Boolean> setter) {
        addRenderableWidget(Button.builder(toggleText(label, getter.getAsBoolean()), button -> {
            boolean value = !getter.getAsBoolean();
            setter.accept(value);
            button.setMessage(toggleText(label, value));
        }).bounds(x, y, width, 20).build());
    }

    private void addPreset(int x, int y, String label, PrinterConfig config, int hudX, int hudY) {
        addRenderableWidget(Button.builder(Component.literal(label), button -> {
            config.hudX = Math.max(0, hudX);
            config.hudY = Math.max(0, hudY);
            rebuildWidgets(); // refresh the sliders
        }).bounds(x, y, HALF_WIDTH, 20).build());
    }

    private static Component toggleText(String label, boolean value) {
        return Component.literal(label + ": " + (value ? "ON" : "OFF"));
    }

    // -------------------------------------------------------------- rendering

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        String title = this.title.getString();
        graphics.text(this.font, title, (this.width - this.font.width(title)) / 2, 6, COLOR_TEXT, true);

        if (page == Page.STATUS) {
            drawStatus(graphics);
        } else if (page == Page.HUD) {
            graphics.text(this.font, "A preview of the HUD is shown while this page is open.",
                    this.width / 2 - CONTENT_WIDTH / 2, TOP + ROW * 5 + 4, COLOR_DIM, true);
        }
    }

    private void drawStatus(GuiGraphicsExtractor graphics) {
        if (startStopButton != null) {
            startStopButton.setMessage(Component.literal(printer.isEnabled() ? "Stop Printer" : "Start Printer"));
        }
        if (pauseButton != null) {
            pauseButton.setMessage(Component.literal(printer.isPaused() ? "Resume" : "Pause"));
            pauseButton.active = printer.isEnabled();
        }

        ProgressSnapshot progress = printer.getProgress();
        boolean measured = progress.total() > 0;
        List<String> missing = printer.getStats().getMissingItems();

        String[] lines = {
                "Status: " + printer.getStatus().label(),
                "Schematic: " + printer.getSchematicName(),
                "Total blocks: " + (measured ? String.format(Locale.ROOT, "%,d", progress.total()) : "measuring..."),
                "Placed (verified): " + String.format(Locale.ROOT, "%,d", progress.correct()),
                "Remaining: " + (measured ? String.format(Locale.ROOT, "%,d", progress.remaining()) : "-"),
                "Progress: " + String.format(Locale.ROOT, "%.1f%%", progress.percent()),
                String.format(Locale.ROOT, "In reach: %d    Speed: %.1f blocks/s",
                        printer.getRemainingInReach(), printer.getPlacementsPerSecond()),
        };

        int x = this.width / 2 - CONTENT_WIDTH / 2;
        int y = TOP;
        for (String line : lines) {
            graphics.text(this.font, line, x, y, COLOR_TEXT, true);
            y += 11;
        }
        String missingText = missing.isEmpty() ? "Missing in hotbar: none" : "Missing in hotbar: " + String.join(", ", missing);
        graphics.text(this.font, missingText, x, y, missing.isEmpty() ? COLOR_DIM : COLOR_BAD, true);
    }

    // -------------------------------------------------------------- lifecycle

    @Override
    public boolean isPauseScreen() {
        return false; // keep the world running so the live numbers keep moving
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        HudRenderer.setPreview(false);
        configManager.save();
    }
}
