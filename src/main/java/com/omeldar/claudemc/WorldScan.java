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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dumps the blocks around the player for Claude to read.
 *
 * <p>Air is never written. Each y-layer is emitted as run-length spans of non-air blocks, so a
 * mostly-empty scan costs almost nothing and a solid floor collapses into a handful of runs.
 * Anything not listed is air - that is the whole contract, and it is what makes a 256-block
 * radius practical: the dense form would have been a quarter of a million cells per layer.
 */
public final class WorldScan {
    private static final String KEYS =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    public static final int MAX_RADIUS = 256;
    /** Stop writing rather than produce a file nothing can read. */
    private static final int MAX_CHARS = 400_000;

    private WorldScan() {}

    /** Must be called on the client thread. Returns the file written, or null on failure. */
    public static Path dump(MinecraftClient mc, int radius) {
        if (mc.player == null || mc.world == null) return null;
        int r = Math.max(1, Math.min(MAX_RADIUS, radius));
        ClientWorld w = mc.world;
        BlockPos feet = mc.player.getBlockPos();
        int yFrom = feet.getY() - 1;
        int yTo = feet.getY() + 1;

        Map<String, Integer> counts = new HashMap<>();
        Map<String, String> keyOf = new LinkedHashMap<>();
        StringBuilder layers = new StringBuilder();
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        long solid = 0;
        long scanned = 0;
        boolean truncated = false;

        for (int y = yFrom; y <= yTo && !truncated; y++) {
            StringBuilder body = new StringBuilder();
            for (int dz = -r; dz <= r; dz++) {
                StringBuilder row = new StringBuilder();
                String runId = null;
                int runStart = 0;
                int lastX = 0;

                for (int dx = -r; dx <= r; dx++) {
                    boolean inside = dx * dx + dz * dz <= r * r;
                    String id = null;
                    if (inside) {
                        cursor.set(feet.getX() + dx, y, feet.getZ() + dz);
                        scanned++;
                        try {
                            var state = w.getBlockState(cursor);
                            if (!state.isAir()) {
                                id = Registries.BLOCK.getId(state.getBlock()).toString();
                            }
                        } catch (Exception ignored) {
                            // unloaded or out of bounds reads as air
                        }
                    }
                    int x = feet.getX() + dx;
                    if (id != null && id.equals(runId)) {
                        lastX = x;
                        continue;
                    }
                    if (runId != null) {
                        appendRun(row, runStart, lastX, keyOf.get(runId));
                        solid += lastX - runStart + 1;
                    }
                    runId = id;
                    if (id != null) {
                        counts.merge(id, 1, Integer::sum);
                        keyOf.computeIfAbsent(id, k -> keyFor(keyOf.size()));
                        runStart = x;
                        lastX = x;
                    }
                }
                if (runId != null) {
                    appendRun(row, runStart, lastX, keyOf.get(runId));
                    solid += lastX - runStart + 1;
                }
                if (row.length() > 0) {
                    body.append("  z=").append(feet.getZ() + dz).append("  ").append(row).append('\n');
                }
                if (layers.length() + body.length() > MAX_CHARS) {
                    truncated = true;
                    break;
                }
            }

            layers.append("\ny=").append(y);
            if (y == feet.getY()) layers.append("   (the layer the player stands in)");
            if (y == feet.getY() - 1) layers.append("   (the floor under the player)");
            layers.append('\n');
            layers.append(body.length() == 0 ? "  (all air)\n" : body);
        }

        // Counts were per-run above; recount properly from the spans we already summed.
        StringBuilder out = new StringBuilder();
        out.append("ClaudeMC world scan\n");
        out.append("centre: x=").append(feet.getX())
           .append(" y=").append(feet.getY())
           .append(" z=").append(feet.getZ()).append('\n');
        out.append("radius: ").append(r)
           .append("   y layers: ").append(yFrom).append("..").append(yTo).append('\n');
        out.append("scanned ").append(scanned).append(" positions, ")
           .append(solid).append(" of them not air\n\n");

        out.append("FORMAT\n");
        out.append("  Air is never listed. Anything not mentioned below is air.\n");
        out.append("  Each line is one z row: 'z=<z>  <x1>..<x2> <key>  <x1>..<x2> <key> ...'\n");
        out.append("  where x1..x2 is an inclusive run of the same block. A single block is\n");
        out.append("  written as one coordinate. Keys are in the legend.\n");
        out.append("  Blocks outside loaded chunks read as air, so a radius beyond the render\n");
        out.append("  distance will look emptier than it is.\n\n");

        List<Map.Entry<String, Integer>> byName = new ArrayList<>(counts.entrySet());
        byName.sort(Comparator.comparing(Map.Entry::getKey));
        out.append("LEGEND (").append(keyOf.size()).append(" block types)\n");
        for (Map.Entry<String, String> e : keyOf.entrySet()) {
            out.append("  ").append(e.getValue()).append(" = ").append(e.getKey()).append('\n');
        }

        out.append("\nRUNS PER BLOCK TYPE (not block counts - how fragmented each is)\n");
        byName.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        for (int i = 0; i < Math.min(30, byName.size()); i++) {
            out.append("  ").append(byName.get(i).getValue())
               .append("  ").append(byName.get(i).getKey()).append('\n');
        }

        out.append(layers);
        if (truncated) {
            out.append("\n(truncated - the scan exceeded ").append(MAX_CHARS)
               .append(" characters; ask for a smaller radius for the rest)\n");
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

    private static void appendRun(StringBuilder row, int from, int to, String key) {
        if (row.length() > 0) row.append("  ");
        if (from == to) row.append(from);
        else row.append(from).append("..").append(to);
        row.append(' ').append(key);
    }

    /** a, b, ... z, A, ... 9, then aa, ab, ... for very mixed areas. */
    private static String keyFor(int index) {
        if (index < KEYS.length()) return String.valueOf(KEYS.charAt(index));
        int first = index / KEYS.length() - 1;
        int second = index % KEYS.length();
        return "" + KEYS.charAt(first) + KEYS.charAt(second);
    }
}
