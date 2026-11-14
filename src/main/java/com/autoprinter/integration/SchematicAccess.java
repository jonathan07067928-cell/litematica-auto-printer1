package com.autoprinter.integration;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What the printer needs to know about the loaded schematic.
 * This interface mentions only Minecraft types, so the rest of the mod never
 * touches Litematica classes directly. If Litematica is missing, nothing
 * outside {@link LitematicaBridge} is ever affected.
 */
public interface SchematicAccess {

    /** True when at least one schematic placement is loaded. */
    boolean hasSchematic();

    /** Display name of the active schematic placement (never null). */
    String getSchematicName();

    /** The block state the schematic wants at this position, or null if the schematic has nothing there. */
    BlockState getRequiredState(BlockPos pos);
}
