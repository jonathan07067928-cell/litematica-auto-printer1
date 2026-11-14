package com.autoprinter.config;

import com.autoprinter.LitematicaAutoPrinter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Loads and saves {@link PrinterConfig} to config/litematica_auto_printer.json. */
public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private PrinterConfig config = new PrinterConfig();

    public ConfigManager() {
        this.file = FabricLoader.getInstance().getConfigDir().resolve(LitematicaAutoPrinter.MOD_ID + ".json");
    }

    public PrinterConfig get() {
        return config;
    }

    /** Reads the config file. A missing file creates defaults; a broken file is backed up and replaced. */
    public void load() {
        if (!Files.exists(file)) {
            LitematicaAutoPrinter.LOGGER.info("No config found, creating defaults at {}", file);
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            PrinterConfig loaded = GSON.fromJson(reader, PrinterConfig.class);
            if (loaded == null) {
                throw new JsonParseException("Config file is empty");
            }
            loaded.sanitize();
            config = loaded;
            LitematicaAutoPrinter.LOGGER.info("Config loaded from {}", file);
        } catch (IOException | JsonParseException e) {
            LitematicaAutoPrinter.LOGGER.error("Could not read config, using defaults. A backup was kept.", e);
            backupBrokenFile();
            config = new PrinterConfig();
            save();
        }
    }

    /** Writes the config atomically so a crash can never leave a half-written file. */
    public void save() {
        config.sanitize();
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LitematicaAutoPrinter.LOGGER.error("Could not save config to {}", file, e);
        }
    }

    private void backupBrokenFile() {
        Path backup = file.resolveSibling(file.getFileName() + ".broken");
        try {
            Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LitematicaAutoPrinter.LOGGER.error("Could not back up broken config", e);
        }
    }
}
