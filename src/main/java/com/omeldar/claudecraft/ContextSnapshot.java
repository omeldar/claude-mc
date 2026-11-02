package com.omeldar.claudecraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.Registries;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Captures what the player can see and where they are, as json Claude can read. */
public final class ContextSnapshot {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ContextSnapshot() {}

    /** Must be called on the client thread. */
    public static JsonObject capture(MinecraftClient mc) {
        JsonObject root = new JsonObject();
        root.addProperty("capturedAt", java.time.OffsetDateTime.now().toString());
        root.addProperty("minecraftVersion",
                FabricLoader.getInstance().getModContainer("minecraft")
                        .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("unknown"));
        root.addProperty("worldEditPresent", FabricLoader.getInstance().isModLoaded("worldedit"));

        // Which build tools are actually installed, so answers can use the right one.
        JsonObject tools = new JsonObject();
        for (String id : new String[]{"worldedit", "litematica", "axiom", "masa", "malilib",
                                      "tweakeroo", "minihud", "journeymap", "voxy", "bobby",
                                      "schematica", "effortlessbuilding", "worldtools"}) {
            if (FabricLoader.getInstance().isModLoaded(id)) {
                tools.addProperty(id, FabricLoader.getInstance().getModContainer(id)
                        .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("present"));
            }
        }
        root.add("buildTools", tools);

        JsonArray allMods = new JsonArray();
        FabricLoader.getInstance().getAllMods().stream()
                .map(m -> m.getMetadata().getId())
                .filter(id -> !id.startsWith("fabric-"))
                .sorted()
                .forEach(allMods::add);
        root.add("installedMods", allMods);

        ClientPlayerEntity p = mc.player;
        ClientWorld w = mc.world;
        if (p == null || w == null) {
            root.addProperty("inWorld", false);
            return root;
        }
        root.addProperty("inWorld", true);

        JsonObject perms = new JsonObject();
        perms.addProperty("commandsAllowed", CommandPermissions.allowed(mc));
        perms.addProperty("authority", CommandPermissions.authority(mc));
        perms.addProperty("singleplayer", mc.isInSingleplayer());
        perms.addProperty("can_build", CommandPermissions.allowed(mc) && mc.interactionManager != null
                && mc.interactionManager.getCurrentGameMode().isCreative());
        perms.addProperty("note", CommandPermissions.allowed(mc)
                ? "Commands run as this player, under their own name and permissions."
                : "READ-ONLY: do not propose commands. " + CommandPermissions.explain(mc));
        root.add("permissions", perms);

        JsonObject pos = new JsonObject();
        pos.addProperty("x", round(p.getX()));
        pos.addProperty("y", round(p.getY()));
        pos.addProperty("z", round(p.getZ()));
        BlockPos bp = p.getBlockPos();
        pos.addProperty("blockX", bp.getX());
        pos.addProperty("blockY", bp.getY());
        pos.addProperty("blockZ", bp.getZ());
        root.add("position", pos);

        JsonObject look = new JsonObject();
        look.addProperty("yaw", round(p.getYaw()));
        look.addProperty("pitch", round(p.getPitch()));
        look.addProperty("facing", p.getHorizontalFacing().asString());
        root.add("look", look);

        safely(root, "dimension", () -> w.getRegistryKey().getValue().toString());
        safely(root, "biome", () -> w.getBiome(bp).getKey()
                .map(k -> k.getValue().toString()).orElse("unknown"));
        safely(root, "gameMode", () -> mc.interactionManager == null ? "unknown"
                : mc.interactionManager.getCurrentGameMode().name().toLowerCase(java.util.Locale.ROOT));
        safely(root, "heldItem", () -> Registries.ITEM.getId(p.getMainHandStack().getItem()).toString());
        safely(root, "standingOn", () ->
                Registries.BLOCK.getId(w.getBlockState(bp.down()).getBlock()).toString());
        safely(root, "serverAddress", () -> {
            if (mc.getCurrentServerEntry() != null) return mc.getCurrentServerEntry().address;
            return mc.isInSingleplayer() ? "singleplayer" : "unknown";
        });

        JsonObject light = new JsonObject();
        try {
            light.addProperty("block", w.getLightLevel(LightType.BLOCK, bp));
            light.addProperty("sky", w.getLightLevel(LightType.SKY, bp));
        } catch (Exception ignored) {}
        root.add("lightAtFeet", light);

        try {
            root.addProperty("timeOfDay", w.getTimeOfDay() % 24000L);
        } catch (Exception ignored) {}

        HitResult hit = mc.crosshairTarget;
        if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK) {
            JsonObject target = new JsonObject();
            BlockPos tp = bhr.getBlockPos();
            target.addProperty("x", tp.getX());
            target.addProperty("y", tp.getY());
            target.addProperty("z", tp.getZ());
            target.addProperty("side", bhr.getSide().asString());
            try {
                target.addProperty("block", Registries.BLOCK.getId(w.getBlockState(tp).getBlock()).toString());
                target.addProperty("state", w.getBlockState(tp).toString());
            } catch (Exception ignored) {}
            root.add("lookingAt", target);
        }

        return root;
    }

    /** Captures and writes context.json. Must be called on the client thread. */
    public static void write(MinecraftClient mc) {
        try {
            Workspace.ensure();
            Files.writeString(Workspace.contextFile(), GSON.toJson(capture(mc)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            ClaudeCraftClient.LOG.warn("could not write context.json", e);
        }
    }

    public static String json(MinecraftClient mc) {
        return GSON.toJson(capture(mc));
    }

    private interface Supplier { String get() throws Exception; }

    private static void safely(JsonObject o, String key, Supplier s) {
        try {
            o.addProperty(key, s.get());
        } catch (Exception e) {
            o.addProperty(key, "unknown");
        }
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
