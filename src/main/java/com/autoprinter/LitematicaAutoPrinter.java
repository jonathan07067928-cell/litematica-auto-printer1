package com.autoprinter;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common entrypoint. Holds shared constants such as the mod id and logger. */
public class LitematicaAutoPrinter implements ModInitializer {
    public static final String MOD_ID = "litematica_auto_printer";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Litematica Auto Printer loaded (common).");
    }
}
