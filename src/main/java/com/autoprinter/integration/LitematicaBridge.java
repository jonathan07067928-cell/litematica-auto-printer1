package com.autoprinter.integration;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * The ONLY class in this mod that references Litematica types.
 * It is created lazily by {@link SchematicProvider}, and only after checking
 * that Litematica and MaLiLib are installed.
 */
public final class LitematicaBridge implements SchematicAccess {

    @Override
    public boolean hasSchematic() {
        if (SchematicWorldHandler.getSchematicWorld() == null) {
            return false;
        }
        List<SchematicPlacement> placements = DataManager.getSchematicPlacementManager().getAllSchematicsPlacements();
        return placements != null && !placements.isEmpty();
    }

    @Override
    public String getSchematicName() {
        SchematicPlacement selected = DataManager.getSchematicPlacementManager().getSelectedSchematicPlacement();
        if (selected != null) {
            return selected.getName();
        }
        List<SchematicPlacement> placements = DataManager.getSchematicPlacementManager().getAllSchematicsPlacements();
        if (placements != null && !placements.isEmpty()) {
            return placements.get(0).getName();
        }
        return "(none)";
    }

    @Override
    public BlockState getRequiredState(BlockPos pos) {
        WorldSchematic schematicWorld = SchematicWorldHandler.getSchematicWorld();
        return schematicWorld == null ? null : schematicWorld.getBlockState(pos);
    }
}
