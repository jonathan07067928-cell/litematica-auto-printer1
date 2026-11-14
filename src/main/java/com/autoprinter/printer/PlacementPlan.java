package com.autoprinter.printer;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.BlockHitResult;

/**
 * One fully checked way to place a block: where it goes, exactly which face
 * point to click, the item to hold, and the view direction the player needs.
 *
 * @param pos           the empty position that will receive the block
 * @param hit           the click, taken from a real ray the player's view could produce
 * @param item          the item that must be held
 * @param yaw           horizontal view angle needed for the click
 * @param pitch         vertical view angle needed for the click
 * @param needsRotation true if the player's view must be turned first
 */
public record PlacementPlan(BlockPos pos, BlockHitResult hit, Item item, float yaw, float pitch, boolean needsRotation) {
}
