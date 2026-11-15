package com.autoprinter;

import com.autoprinter.config.ConfigManager;
import com.autoprinter.gui.PrinterSettingsScreen;
import com.autoprinter.hud.HudRenderer;
import com.autoprinter.input.KeybindManager;
import com.autoprinter.integration.SchematicProvider;
import com.autoprinter.printer.PrinterController;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

/** Client entrypoint: wires config, Litematica access, printer state, keybinds, HUD and settings together. */
public class LitematicaAutoPrinterClient implements ClientModInitializer {
    private static ConfigManager configManager;
    private static SchematicProvider schematicProvider;
    private static PrinterController printerController;
    private static KeybindManager keybindManager;

    public static ConfigManager config() {
        return configManager;
    }

    public static PrinterController printer() {
        return printerController;
    }

    @Override
    public void onInitializeClient() {
        configManager = new ConfigManager();
        configManager.load();

        schematicProvider = new SchematicProvider();
        printerController = new PrinterController(configManager, schematicProvider);

        keybindManager = new KeybindManager(printerController,
                () -> Minecraft.getInstance().setScreen(new PrinterSettingsScreen(null)));

        HudRenderer.register(configManager, printerController);

        ClientTickEvents.END_CLIENT_TICK.register(printerController::tick);

        // Make sure the latest settings are on disk when the game closes.
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> configManager.save());

        LitematicaAutoPrinter.LOGGER.info("Litematica Auto Printer loaded (client).");
    }
}
