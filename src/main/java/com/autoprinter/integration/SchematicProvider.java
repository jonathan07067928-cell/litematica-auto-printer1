package com.autoprinter.integration;

import com.autoprinter.LitematicaAutoPrinter;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Safe front door to Litematica. Checks that the dependencies exist, creates
 * the bridge lazily, and turns any version-mismatch error into a clear
 * "unavailable" state instead of crashing the game.
 */
public final class SchematicProvider {

    public enum State {
        LITEMATICA_MISSING,
        NO_SCHEMATIC,
        READY
    }

    private SchematicAccess access;
    private boolean unavailable;

    public State state() {
        SchematicAccess current = access();
        if (current == null) {
            return State.LITEMATICA_MISSING;
        }
        try {
            return current.hasSchematic() ? State.READY : State.NO_SCHEMATIC;
        } catch (LinkageError | RuntimeException e) {
            markUnavailable("Litematica API call failed (version mismatch?)", e);
            return State.LITEMATICA_MISSING;
        }
    }

    /** Name of the active schematic, or "(none)". */
    public String schematicName() {
        SchematicAccess current = access();
        if (current == null) {
            return "(none)";
        }
        try {
            return current.getSchematicName();
        } catch (LinkageError | RuntimeException e) {
            markUnavailable("Litematica API call failed while reading the schematic name", e);
            return "(none)";
        }
    }

    /** Box around all placed schematics, or null if unknown. */
    public Bounds boundsOrNull() {
        SchematicAccess current = access();
        if (current == null) {
            return null;
        }
        try {
            return current.getBounds();
        } catch (LinkageError | RuntimeException e) {
            markUnavailable("Litematica API call failed while reading the schematic bounds", e);
            return null;
        }
    }

    /** Returns the access object for scanning, or null while Litematica is unavailable. */
    public SchematicAccess accessOrNull() {
        return access();
    }

    private SchematicAccess access() {
        if (access != null) {
            return access;
        }
        if (unavailable) {
            return null;
        }
        FabricLoader loader = FabricLoader.getInstance();
        if (!loader.isModLoaded("litematica") || !loader.isModLoaded("malilib")) {
            unavailable = true;
            LitematicaAutoPrinter.LOGGER.warn(
                    "Litematica and/or MaLiLib are not installed. Schematic printing is unavailable.");
            return null;
        }
        try {
            access = new LitematicaBridge();
        } catch (LinkageError | RuntimeException e) {
            markUnavailable("Could not connect to Litematica", e);
        }
        return access;
    }

    private void markUnavailable(String message, Throwable error) {
        unavailable = true;
        access = null;
        LitematicaAutoPrinter.LOGGER.error("{}. Schematic printing is disabled until Minecraft is restarted.", message, error);
    }
}
