package com.omeldar.claudecraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** User-editable settings, stored at config/claudecraft.json. */
public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config instance;

    /** Absolute path to the claude executable. Empty means auto-detect. */
    public String claudePath = "";
    /** Model alias passed to --model: sonnet, opus, fable, haiku, or a full model name. */
    public String model = "sonnet";
    /** Passed to --effort when non-empty: low, medium, high. */
    public String effort = "";
    /** How long to wait for a reply before giving up. */
    public int timeoutSeconds = 240;
    /** Ticks between queued commands, so server anti-spam does not kick you. */
    public int commandDelayTicks = 5;
    /** Accept proposals pushed over HTTP by an already-running Claude Code session. */
    public boolean bridgeEnabled = true;
    public int bridgePort = 25599;
    /** Shared secret for the bridge. Generated on first run. */
    public String bridgeToken = "";
    /** Default radius for /ai scan. */
    public int defaultScanRadius = 24;
    /** Hard cap on how many commands one proposal may contain. */
    public int maxCommands = 24;
    /** Commands matching these prefixes are never run, whatever Claude proposes. */
    public String[] blockedPrefixes = {
        "/stop", "/op", "/deop", "/ban", "/ban-ip", "/kick", "/whitelist", "/pardon", "/save-off"
    };

    public static Config get() {
        if (instance == null) instance = load();
        return instance;
    }

    public static void reload() {
        instance = load();
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("claudecraft.json");
    }

    private static Config load() {
        Path p = path();
        Config c = new Config();
        try {
            if (Files.exists(p)) {
                Config read = GSON.fromJson(Files.readString(p, StandardCharsets.UTF_8), Config.class);
                if (read != null) c = read;
            }
        } catch (Exception e) {
            ClaudeCraftClient.LOG.warn("could not read config, using defaults", e);
        }
        if (c.bridgeToken == null || c.bridgeToken.isBlank()) {
            c.bridgeToken = UUID.randomUUID().toString();
        }
        if (c.blockedPrefixes == null) {
            c.blockedPrefixes = new Config().blockedPrefixes;
        }
        c.save();
        return c;
    }

    public void save() {
        try {
            Path p = path();
            Files.createDirectories(p.getParent());
            Files.writeString(p, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (Exception e) {
            ClaudeCraftClient.LOG.warn("could not write config", e);
        }
    }

    /** True if this command is on the deny list. Leading slashes are normalised first. */
    public boolean isBlocked(String command) {
        String c = command.trim().toLowerCase();
        while (c.startsWith("//")) c = c.substring(1);
        if (!c.startsWith("/")) c = "/" + c;
        for (String bad : blockedPrefixes) {
            if (c.equals(bad) || c.startsWith(bad + " ")) return true;
        }
        return false;
    }
}
