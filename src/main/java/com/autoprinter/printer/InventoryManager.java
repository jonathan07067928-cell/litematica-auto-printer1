package com.autoprinter.printer;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Read-only helpers for the hotbar plus the hotbar slot switch.
 * Only the 9 hotbar slots are used; the printer never moves items around.
 */
public final class InventoryManager {
    private static final int HOTBAR_SIZE = 9;

    /** Returns a hotbar slot (0-8) holding the item, preferring the selected slot, or -1. */
    public int findHotbarSlot(Player player, Item item) {
        Inventory inventory = player.getInventory();
        int selected = inventory.getSelectedSlot();
        if (matches(inventory.getItem(selected), item)) {
            return selected;
        }
        for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
            if (matches(inventory.getItem(slot), item)) {
                return slot;
            }
        }
        return -1;
    }

    public int selectedSlot(Player player) {
        return player.getInventory().getSelectedSlot();
    }

    /** Switches the selected hotbar slot. Vanilla tells the server before the next block interaction. */
    public void select(Player player, int slot) {
        player.getInventory().setSelectedSlot(slot);
    }

    public ItemStack hotbarStack(Player player, int slot) {
        return player.getInventory().getItem(slot);
    }

    private static boolean matches(ItemStack stack, Item item) {
        return !stack.isEmpty() && stack.getItem() == item;
    }
}
