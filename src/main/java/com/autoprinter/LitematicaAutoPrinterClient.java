package com.autoprinter;

import net.fabricmc.api.ClientModInitializer;

/** Client entrypoint. Config, keybinds, HUD and the printer are added in later batches. */
public class LitematicaAutoPrinterClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        LitematicaAutoPrinter.LOGGER.info("Litematica Auto Printer loaded (client).");
    }
}
