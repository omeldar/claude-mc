package com.omeldar.lumi;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** User-editable settings, stored at config/lumi.json. */
public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config instance;

    /** Absolute path to the claude executable. Empty means auto-detect. */
    public String claudePath = "";
    /** Model alias or exact id passed to --model. Anything the CLI accepts works. */
    public String model = "opus";
    /**
     * The list the in-game model button cycles through.
     *
     * <p>Exact ids first, because an alias always means the newest of its family - "opus" is
     * claude-opus-5-5 today and something else later. Verified against the CLI:
     * every id below resolves to itself. claude-opus-4-1 quietly resolves to 5-5 instead,
     * claude-haiku-5 does not exist, and short forms like "opus-4.8" are rejected outright.
     * Add any id you like; the mod remembers what you type.
     */
    public String[] models = {
        "claude-opus-4-8",
        "claude-opus-5",
        "claude-opus-5-5",
        "claude-opus-4-5",
        "claude-sonnet-5-5",
        "claude-sonnet-5",
        "claude-sonnet-4-5",
        "claude-haiku-5-5",
        "claude-haiku-4-5",
        "claude-fable-5-1",
        "claude-fable-5",
        "opus",
        "sonnet",
        "haiku",
        "fable"
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
    /** How many characters the chat box accepts while the line starts with /lumi or /ai. */
    public int chatPromptLimit = 2000;
    /**
     * Allow prompts to run on an API key, which is <em>billed per token</em>.
     *
     * <p>Off by default, and off means enforced, not merely discouraged: the key variables are
     * removed from the environment handed to the CLI, so it falls back to the subscription
     * login. The CLI prefers ANTHROPIC_API_KEY over an OAuth profile, so without this the mod
     * could silently start charging a card because of a variable set for something else
     * entirely. Turn it on only if billed API usage is what you actually want.
     */
    public boolean allowApiKey = false;
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
        return FabricLoader.getInstance().getConfigDir().resolve("lumi.json");
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
            LumiClient.LOG.warn("could not read config, using defaults", e);
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
            LumiClient.LOG.warn("could not write config", e);
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
