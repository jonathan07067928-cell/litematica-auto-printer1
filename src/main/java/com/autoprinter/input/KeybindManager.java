package com.autoprinter.input;

import com.autoprinter.LitematicaAutoPrinter;
import com.autoprinter.printer.PrinterController;
import com.autoprinter.util.ChatUtil;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Registers the four rebindable keys. They appear in
 * Options > Controls > Key Binds under "Litematica Auto Printer".
 */
public final class KeybindManager {
    private final PrinterController printer;
    private final Runnable openSettings;

    private final KeyMapping toggleKey;
    private final KeyMapping pauseKey;
    private final KeyMapping settingsKey;
    private final KeyMapping stopKey;

    public KeybindManager(PrinterController printer, Runnable openSettings) {
        this.printer = printer;
        this.openSettings = openSettings;

        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(LitematicaAutoPrinter.MOD_ID, "main"));

        this.toggleKey = register("toggle_printer", GLFW.GLFW_KEY_P, category);
        this.pauseKey = register("pause_resume", GLFW.GLFW_KEY_O, category);
        this.settingsKey = register("open_settings", GLFW.GLFW_KEY_K, category);
        this.stopKey = register("stop_printing", GLFW.GLFW_KEY_L, category);

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private static KeyMapping register(String id, int defaultKey, KeyMapping.Category category) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key." + LitematicaAutoPrinter.MOD_ID + "." + id,
                InputConstants.Type.KEYSYM,
                defaultKey,
                category));
    }

    private void onTick(Minecraft client) {
        while (toggleKey.consumeClick()) {
            printer.toggle();
            ChatUtil.info(client, printer.isEnabled() ? "Printer enabled." : "Printer disabled.");
        }
        while (pauseKey.consumeClick()) {
            if (printer.isEnabled()) {
                printer.togglePause();
                ChatUtil.info(client, printer.isPaused() ? "Printing paused." : "Printing resumed.");
            } else {
                ChatUtil.info(client, "Printer is disabled, nothing to pause.");
            }
        }
        while (stopKey.consumeClick()) {
            printer.stop();
            ChatUtil.info(client, "Printing stopped.");
        }
        while (settingsKey.consumeClick()) {
            openSettings.run();
        }
    }
}
