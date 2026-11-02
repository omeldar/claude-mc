package com.omeldar.claudemc;

import net.minecraft.client.MinecraftClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Tells one world from another, so notes do not get mixed between them.
 *
 * <p>A singleplayer world is identified by its level name, a server by its address. Both are
 * slugged into a filename. Dimensions deliberately do not come into it - the nether and the
 * overworld of one world are the same build project.
 */
public final class WorldIdentity {
    private WorldIdentity() {}

    /** Stable, filesystem-safe id for the world the player is in. */
    public static String slug(MinecraftClient mc) {
        return slugify(rawId(mc));
    }

    /** What to call it in a sentence. */
    public static String label(MinecraftClient mc) {
        if (mc == null || mc.world == null) return "no world";
        if (mc.isInSingleplayer()) {
            String name = levelName(mc);
            return name.isEmpty() ? "a singleplayer world" : name;
        }
        if (mc.getCurrentServerEntry() != null) {
            String name = mc.getCurrentServerEntry().name;
            String address = mc.getCurrentServerEntry().address;
            return (name == null || name.isBlank()) ? address : name + " (" + address + ")";
        }
        return "an unknown server";
    }

    private static String rawId(MinecraftClient mc) {
        if (mc == null || mc.world == null) return "none";
        if (mc.isInSingleplayer()) {
            String name = levelName(mc);
            return "sp-" + (name.isEmpty() ? "world" : name);
        }
        if (mc.getCurrentServerEntry() != null) {
            return "mp-" + mc.getCurrentServerEntry().address;
        }
        return "mp-unknown";
    }

    private static String levelName(MinecraftClient mc) {
        try {
            if (mc.getServer() != null && mc.getServer().getSaveProperties() != null) {
                String n = mc.getServer().getSaveProperties().getLevelName();
                if (n != null) return n;
            }
        } catch (Exception e) {
            ClaudeMCClient.LOG.debug("could not read level name", e);
        }
        return "";
    }

    private static String slugify(String raw) {
        String s = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-");
        s = s.replaceAll("-{2,}", "-").replaceAll("^-|-$", "");
        if (s.isEmpty()) s = "world";
        return s.length() > 64 ? s.substring(0, 64) : s;
    }

    /** The notes file for the current world, relative to the workspace. */
    public static String notesRelativePath(MinecraftClient mc) {
        return "notes/" + slug(mc) + ".md";
    }

    public static Path notesFile(MinecraftClient mc) {
        return Workspace.root().resolve("notes").resolve(slug(mc) + ".md");
    }

    /** Creates this world's notes file with a header if it is not there yet. */
    public static void ensureNotes(MinecraftClient mc) {
        try {
            Path p = notesFile(mc);
            Files.createDirectories(p.getParent());
            if (!Files.exists(p)) {
                String header = "# Build notes - " + label(mc) + "\n\n"
                        + "Claude keeps this file. Anything worth remembering between sessions\n"
                        + "goes here: coordinates, block palettes, what each area is for,\n"
                        + "decisions already made. It belongs to this world only.\n\n";
                Files.writeString(p, header, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            ClaudeMCClient.LOG.warn("could not prepare notes for this world", e);
        }
    }
}
