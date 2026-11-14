package com.autoprinter;

import com.autoprinter.config.ConfigManager;
import com.autoprinter.input.KeybindManager;
import com.autoprinter.integration.SchematicProvider;
import com.autoprinter.printer.PrinterController;
import com.autoprinter.util.ChatUtil;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

/** Client entrypoint: wires config, Litematica access, printer state and keybinds together. */
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

        keybindManager = new KeybindManager(printerController, () ->
                ChatUtil.info(Minecraft.getInstance(),
                        "The settings screen is coming in a later update. For now, edit config/"
                                + LitematicaAutoPrinter.MOD_ID + ".json"));

        ClientTickEvents.END_CLIENT_TICK.register(printerController::tick);

        // Make sure the latest settings are on disk when the game closes.
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> configManager.save());

        LitematicaAutoPrinter.LOGGER.info("Litematica Auto Printer loaded (client).");
    }
}
