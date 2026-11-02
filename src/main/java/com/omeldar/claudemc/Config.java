package com.omeldar.claudemc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** User-editable settings, stored at config/claudemc.json. */
public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config instance;

    /** Absolute path to the claude executable. Empty means auto-detect. */
    public String claudePath = "";
    /** Model alias or exact id passed to --model. Anything the CLI accepts works. */
    public String model = "opus";
    /**
     * The list the in-game model button cycles through. Add exact ids here to switch between
     * specific versions rather than aliases - an alias always means the newest of its family.
     */
    public String[] models = {
        "opus", "sonnet", "haiku", "fable",
        "claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-5-5", "claude-fable-5-1"
    };
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
    /** Read the reply as it streams, so progress shows instead of a frozen wait. */
    public boolean streaming = true;
    /** Echo each progress step into chat as well as the action bar. Noisy; off by default. */
    public boolean showProgressInChat = false;
    /** How many characters the chat box accepts while the line starts with /claude or /ai. */
    public int chatPromptLimit = 2000;
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
        return FabricLoader.getInstance().getConfigDir().resolve("claudemc.json");
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
            ClaudeMCClient.LOG.warn("could not read config, using defaults", e);
        }
        if (c.bridgeToken == null || c.bridgeToken.isBlank()) {
            c.bridgeToken = UUID.randomUUID().toString();
        }
        if (c.models == null || c.models.length == 0) {
            c.models = new Config().models;
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
            ClaudeMCClient.LOG.warn("could not write config", e);
        }
    }

    /** Adds a model id to the cycle list if it is not already there. */
    public void rememberModel(String id) {
        if (id == null || id.isBlank()) return;
        for (String m : models) {
            if (m.equalsIgnoreCase(id)) return;
        }
        String[] grown = new String[models.length + 1];
        System.arraycopy(models, 0, grown, 0, models.length);
        grown[models.length] = id;
        models = grown;
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
