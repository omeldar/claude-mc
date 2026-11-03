package com.omeldar.lumi;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.client.MinecraftClient;

/**
 * Decides whether this client is actually allowed to run commands.
 *
 * <p>Lumi never runs anything the player could not have typed themselves: every command
 * goes out as a normal chat command under the player's own name and the server applies its own
 * rules. This class exists so we fail loudly and early instead of spamming a survival server
 * with commands it will silently reject - and so Claude is told up front not to propose any.
 *
 * <p>Allowed: singleplayer with cheats, a world opened to LAN with cheats on, an opped player on
 * a server, or any server whose command tree offers the player build commands.
 * Not allowed: plain survival singleplayer, and survival servers where the player is not op.
 */
public final class CommandPermissions {

    /** Command names that only appear in a player's command tree if they may actually build. */
    private static final String[] PROBES = {
        "setblock", "fill", "clone", "gamemode", "worldedit",
        "/set", "/cyl", "/pos1", "/brush", "/schem", "/we"
    };

    private CommandPermissions() {}

    public enum Mode {
        /** Commands can be run. */
        ALLOWED,
        /** Connected, but this player has no command authority here. */
        READ_ONLY,
        /** Not in a world at all. */
        OFFLINE
    }

    public static Mode mode(MinecraftClient mc) {
        if (mc == null || mc.player == null || mc.getNetworkHandler() == null) return Mode.OFFLINE;
        // The server only sends a player the commands they are allowed to use, so the command
        // tree is the authoritative answer in both singleplayer and multiplayer. A world without
        // cheats gets a minimal tree (help, me, msg, ...) and none of the probes below.
        if (hasBuildCommands(mc)) return Mode.ALLOWED;
        return Mode.READ_ONLY;
    }

    public static boolean allowed(MinecraftClient mc) {
        return mode(mc) == Mode.ALLOWED;
    }

    private static boolean hasBuildCommands(MinecraftClient mc) {
        try {
            CommandDispatcher<?> dispatcher = mc.getNetworkHandler().getCommandDispatcher();
            if (dispatcher == null) return false;
            CommandNode<?> root = dispatcher.getRoot();
            for (String probe : PROBES) {
                if (root.getChild(probe) != null) return true;
            }
        } catch (Exception e) {
            LumiClient.LOG.debug("could not inspect command tree", e);
        }
        return false;
    }

    /** Short machine-readable label, written into context.json for Claude. */
    public static String authority(MinecraftClient mc) {
        return switch (mode(mc)) {
            case OFFLINE -> "offline";
            case ALLOWED -> mc.isInSingleplayer() ? "singleplayer-with-cheats" : "server-with-permission";
            case READ_ONLY -> mc.isInSingleplayer() ? "survival-singleplayer-no-cheats" : "server-no-permission";
        };
    }

    /** What to tell the player when they ask for something we cannot run. */
    public static String explain(MinecraftClient mc) {
        if (mc == null || mc.player == null) {
            return "You are not in a world, so there is nothing to run commands against.";
        }
        if (mc.isInSingleplayer()) {
            return "This world has cheats off, so no commands can be run - not by me and not by you. "
                 + "To enable them: pause, Open to LAN, set Allow Cheats to ON, then Start LAN World. "
                 + "Until then I will answer with advice only.";
        }
        return "You do not have command permission on this server, so nothing can be run here. "
             + "I will answer with advice only - ask an admin for permissions, or hop into a "
             + "singleplayer copy to prototype.";
    }

    /** The line handed to Claude so it does not propose commands it knows cannot run. */
    public static String promptNotice(MinecraftClient mc) {
        if (allowed(mc)) return "";
        return """

               IMPORTANT - READ-ONLY MODE: this client currently has no command permission \
               (%s). Do NOT propose any commands and do NOT emit a json block this turn. \
               Answer with advice, block palettes and technique only, and if the answer really \
               needs commands, say what the player would have to enable first.
               """.formatted(authority(mc));
    }
}
