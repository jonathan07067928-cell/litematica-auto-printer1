package com.autoprinter.hud;

import com.autoprinter.LitematicaAutoPrinter;
import com.autoprinter.config.ConfigManager;
import com.autoprinter.config.PrinterConfig;
import com.autoprinter.printer.PrinterController;
import com.autoprinter.printer.ProgressSnapshot;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Compact overlay with the printer's status and progress. It is only drawn
 * while the printer is switched on, and follows the position saved in the config.
 */
public final class HudRenderer {
    private static final int PADDING = 4;
    private static final int LINE_HEIGHT = 10;
    private static final int BAR_HEIGHT = 5;
    private static final int MIN_WIDTH = 150;
    private static final int MAX_NAME_LENGTH = 28;

    private static final int COLOR_PANEL = 0x90000000;
    private static final int COLOR_BAR_BACKGROUND = 0xFF333333;
    private static final int COLOR_BAR_FILL = 0xFF55FF55;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_DIM = 0xFFAAAAAA;
    private static final int COLOR_GOOD = 0xFF55FF55;
    private static final int COLOR_WARN = 0xFFFFFF55;
    private static final int COLOR_BAD = 0xFFFF5555;
    private static final int COLOR_DONE = 0xFF55FFFF;

    private record Line(String text, int color) {
    }

    private final ConfigManager configManager;
    private final PrinterController printer;

    private HudRenderer(ConfigManager configManager, PrinterController printer) {
        this.configManager = configManager;
        this.printer = printer;
    }

    public static void register(ConfigManager configManager, PrinterController printer) {
        HudRenderer renderer = new HudRenderer(configManager, printer);
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(LitematicaAutoPrinter.MOD_ID, "printer_hud"),
                renderer::render);
    }

    private void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        PrinterConfig config = configManager.get();
        if (!config.hudEnabled || !printer.isEnabled()) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        if (font == null) {
            return;
        }

        ProgressSnapshot progress = printer.getProgress();
        PrinterController.Status status = printer.getStatus();

        // Lines drawn above the progress bar.
        List<Line> top = new ArrayList<>();
        top.add(new Line("Auto Printer: " + status.label(), statusColor(status)));
        top.add(new Line(shorten(printer.getSchematicName()), COLOR_DIM));

        // Lines drawn below the progress bar.
        List<Line> bottom = new ArrayList<>();
        bottom.add(new Line(progressText(progress), COLOR_TEXT));
        bottom.add(new Line(String.format(Locale.ROOT, "In reach: %d   Speed: %.1f/s",
                printer.getRemainingInReach(), printer.getPlacementsPerSecond()), COLOR_DIM));
        List<String> missing = printer.getStats().getMissingItems();
        if (!missing.isEmpty()) {
            bottom.add(new Line("Missing: " + missingText(missing), COLOR_BAD));
        }

        int textWidth = 0;
        for (Line line : top) {
            textWidth = Math.max(textWidth, font.width(line.text()));
        }
        for (Line line : bottom) {
            textWidth = Math.max(textWidth, font.width(line.text()));
        }
        int width = Math.max(MIN_WIDTH, textWidth + PADDING * 2);
        int height = PADDING * 2 + (top.size() + bottom.size()) * LINE_HEIGHT + BAR_HEIGHT + 3;

        int x = Math.max(0, Math.min(config.hudX, graphics.guiWidth() - width));
        int y = Math.max(0, Math.min(config.hudY, graphics.guiHeight() - height));

        graphics.fill(x, y, x + width, y + height, COLOR_PANEL);

        int cursor = y + PADDING;
        for (Line line : top) {
            graphics.text(font, line.text(), x + PADDING, cursor, line.color());
            cursor += LINE_HEIGHT;
        }

        int barWidth = width - PADDING * 2;
        double fraction = progress.total() > 0 ? (double) progress.correct() / progress.total() : 0.0;
        int filled = (int) Math.round(barWidth * Math.max(0.0, Math.min(1.0, fraction)));
        graphics.fill(x + PADDING, cursor, x + PADDING + barWidth, cursor + BAR_HEIGHT, COLOR_BAR_BACKGROUND);
        if (filled > 0) {
            graphics.fill(x + PADDING, cursor, x + PADDING + filled, cursor + BAR_HEIGHT, COLOR_BAR_FILL);
        }
        cursor += BAR_HEIGHT + 3;

        for (Line line : bottom) {
            graphics.text(font, line.text(), x + PADDING, cursor, line.color());
            cursor += LINE_HEIGHT;
        }
    }

    private static String progressText(ProgressSnapshot progress) {
        if (progress.total() <= 0) {
            return "Measuring schematic...";
        }
        String text = String.format(Locale.ROOT, "%,d / %,d  (%.1f%%)",
                progress.correct(), progress.total(), progress.percent());
        return progress.boundsKnown() ? text : text + " near you";
    }

    private static String missingText(List<String> missing) {
        int shown = Math.min(3, missing.size());
        String text = String.join(", ", missing.subList(0, shown));
        return missing.size() > shown ? text + " +" + (missing.size() - shown) : text;
    }

    private static String shorten(String name) {
        return name.length() <= MAX_NAME_LENGTH ? name : name.substring(0, MAX_NAME_LENGTH - 1) + "~";
    }

    private static int statusColor(PrinterController.Status status) {
        return switch (status) {
            case PRINTING, READY -> COLOR_GOOD;
            case PAUSED, NOTHING_TO_PLACE -> COLOR_WARN;
            case COMPLETE -> COLOR_DONE;
            case OUT_OF_MATERIALS, NO_LITEMATICA, NO_SCHEMATIC -> COLOR_BAD;
            case DISABLED -> COLOR_DIM;
        };
    }
}
