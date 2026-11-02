package com.omeldar.claudemc;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dumps the blocks around the player to a text file Claude can read: a block histogram
 * plus, for small radii, one ASCII map per y-layer with a legend.
 */
public final class WorldScan {
    private static final String CHARS =
            "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int MAX_RADIUS = 64;
    private static final int MAX_MAP_RADIUS = 32;

    private WorldScan() {}

    /** Must be called on the client thread. Returns the file written, or null on failure. */
    public static Path dump(MinecraftClient mc, int radius) {
        if (mc.player == null || mc.world == null) return null;
        int r = Math.max(1, Math.min(MAX_RADIUS, radius));
        ClientWorld w = mc.world;
        BlockPos feet = mc.player.getBlockPos();

        int yFrom = feet.getY() - 1;
        int yTo = feet.getY() + 1;

        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, Character> legend = new LinkedHashMap<>();
        StringBuilder maps = new StringBuilder();
        BlockPos.Mutable cursor = new BlockPos.Mutable();

        for (int y = yFrom; y <= yTo; y++) {
            StringBuilder layer = new StringBuilder();
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (dx * dx + dz * dz > r * r) {
                        if (r <= MAX_MAP_RADIUS) layer.append(' ');
                        continue;
                    }
                    cursor.set(feet.getX() + dx, y, feet.getZ() + dz);
                    String id;
                    try {
                        id = Registries.BLOCK.getId(w.getBlockState(cursor).getBlock()).toString();
                    } catch (Exception e) {
                        id = "minecraft:air";
                    }
                    counts.merge(id, 1, Integer::sum);
                    if (r <= MAX_MAP_RADIUS) layer.append(charFor(id, legend));
                }
                if (r <= MAX_MAP_RADIUS) layer.append('\n');
            }
            if (r <= MAX_MAP_RADIUS) {
                maps.append("\n--- y=").append(y);
                if (y == feet.getY()) maps.append("  (the layer the player stands in)");
                if (y == feet.getY() - 1) maps.append("  (the floor under the player)");
                maps.append(" ---\n").append(layer);
            }
        }

        StringBuilder out = new StringBuilder();
        out.append("ClaudeMC world scan\n");
        out.append("centre: x=").append(feet.getX())
           .append(" y=").append(feet.getY())
           .append(" z=").append(feet.getZ()).append('\n');
        out.append("radius: ").append(r).append("   y layers: ")
           .append(yFrom).append("..").append(yTo).append('\n');
        out.append("map orientation: each row is one z, west (-x) on the left, "
                 + "north (-z) at the top\n\n");

        out.append("block counts across all layers:\n");
        counts.entrySet().stream()
              .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
              .limit(40)
              .forEach(e -> out.append("  ").append(pad(e.getValue())).append("  ")
                               .append(e.getKey()).append('\n'));

        if (r <= MAX_MAP_RADIUS) {
            out.append("\nlegend:\n");
            legend.forEach((id, ch) -> out.append("  ").append(ch).append(" = ").append(id).append('\n'));
            out.append("  (space) = outside the scan circle\n");
            out.append(maps);
        } else {
            out.append("\n(ascii maps omitted: radius > ").append(MAX_MAP_RADIUS)
               .append(", counts only)\n");
        }

        try {
            Workspace.ensure();
            String name = "scan-" + LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".txt";
            Path p = Workspace.scans().resolve(name);
            Files.writeString(p, out.toString(), StandardCharsets.UTF_8);
            return p;
        } catch (Exception e) {
            ClaudeMCClient.LOG.warn("could not write scan", e);
            return null;
        }
    }

    private static char charFor(String id, Map<String, Character> legend) {
        if (id.equals("minecraft:air") || id.equals("minecraft:cave_air")
                || id.equals("minecraft:void_air")) return '.';
        Character c = legend.get(id);
        if (c != null) return c;
        int idx = legend.size();
        char assigned = idx < CHARS.length() ? CHARS.charAt(idx) : '?';
        legend.put(id, assigned);
        return assigned;
    }

    private static String pad(int n) {
        String s = Integer.toString(n);
        return " ".repeat(Math.max(0, 7 - s.length())) + s;
    }
}
