package com.autoprinter.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Small helper for showing local (client-only) messages to the player. */
public final class ChatUtil {
    private static final String PREFIX = "[Auto Printer] ";

    private ChatUtil() {
    }

    public static void info(Minecraft client, String message) {
        if (client != null && client.player != null) {
            client.player.sendSystemMessage(Component.literal(PREFIX + message));
        }
    }
}
