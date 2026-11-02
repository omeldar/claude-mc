package com.omeldar.claudemc;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Sends approved commands to the server one at a time with a tick gap,
 * so chat anti-spam does not disconnect the player.
 */
public final class CommandQueue {
    private static final Deque<String> QUEUE = new ArrayDeque<>();
    private static int cooldown = 0;

    private CommandQueue() {}

    public static void enqueue(String command) {
        if (command == null || command.isBlank()) return;
        synchronized (QUEUE) {
            QUEUE.add(command.trim());
        }
    }

    public static int pending() {
        synchronized (QUEUE) {
            return QUEUE.size();
        }
    }

    public static void clear() {
        synchronized (QUEUE) {
            QUEUE.clear();
        }
    }

    public static void tick(MinecraftClient client) {
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        String next;
        synchronized (QUEUE) {
            next = QUEUE.poll();
        }
        if (next == null) return;
        send(client, next);
        cooldown = Math.max(1, Config.get().commandDelayTicks);
    }

    private static void send(MinecraftClient client, String command) {
        if (client.getNetworkHandler() == null || client.player == null) return;
        if (!CommandPermissions.allowed(client)) {
            // Drop the whole batch rather than warn once per command.
            clear();
            Chat.warn("nothing was run. " + CommandPermissions.explain(client));
            return;
        }
        if (Config.get().isBlocked(command)) {
            Chat.warn("blocked by config: " + command);
            return;
        }
        // sendChatCommand expects the command WITHOUT its leading slash. WorldEdit's commands
        // are literally named "/cyl", so the chat line "//cyl ..." goes on the wire as "/cyl ...".
        String wire = command.startsWith("/") ? command.substring(1) : command;
        if (wire.isBlank()) return;
        try {
            client.getNetworkHandler().sendChatCommand(wire);
            Chat.info(Text.literal("> ").formatted(Formatting.DARK_GRAY)
                    .append(Text.literal(command).formatted(Formatting.GRAY)));
        } catch (Exception e) {
            Chat.warn("failed to send: " + command + " (" + e.getMessage() + ")");
        }
    }
}
