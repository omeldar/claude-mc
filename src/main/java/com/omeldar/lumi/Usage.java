package com.omeldar.lumi;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What the CLI tells us about quota and spend, kept so it can be shown in game.
 *
 * <p>The rate-limit windows are read straight out of the CLI's {@code rate_limit_event} and
 * stored under whatever names it sends. On this account that is a shared five-hour and
 * seven-day pair - Opus and Fable draw from the same buckets, there is no per-model
 * contingent - but nothing here assumes that, so if a model-specific window ever appears it
 * will simply show up.
 */
public final class Usage {

    public record Window(String name, double utilization, long resetsAtEpoch, String status) {
        /** "5h", "7d", "7d overage" - a short label for a chat line. */
        public String shortLabel() {
            return switch (name) {
                case "five_hour" -> "5h";
                case "seven_day" -> "7d";
                case "seven_day_overage_included" -> "7d overage";
                default -> name.replace("seven_day_", "7d ").replace('_', ' ');
            };
        }

        public int percent() {
            return (int) Math.round(utilization * 100.0);
        }

        /** How long until this window rolls over, or empty when unknown. */
        public String resetsIn() {
            if (resetsAtEpoch <= 0) return "";
            Duration d = Duration.between(Instant.now(), Instant.ofEpochSecond(resetsAtEpoch));
            if (d.isNegative() || d.isZero()) return "now";
            long days = d.toDays();
            long hours = d.toHours() % 24;
            long minutes = d.toMinutes() % 60;
            if (days > 0) return days + "d " + hours + "h";
            if (hours > 0) return hours + "h " + minutes + "m";
            return minutes + "m";
        }

        public boolean isHealthy() {
            return "allowed".equalsIgnoreCase(status);
        }
    }

    private static final Map<String, Window> WINDOWS = new LinkedHashMap<>();
    private static volatile String rateLimitType = "";
    private static volatile boolean usingOverage = false;
    private static volatile boolean haveLimits = false;

    // Totals for this game session, not for the Claude session.
    private static volatile int messages = 0;
    private static volatile double costUsd = 0.0;
    private static volatile long inputTokens = 0;
    private static volatile long outputTokens = 0;
    private static volatile long cacheReadTokens = 0;
    private static volatile long cacheWriteTokens = 0;
    private static volatile long totalMillis = 0;
    private static volatile int turns = 0;

    private Usage() {}

    public static boolean hasLimits() { return haveLimits; }
    public static boolean hasSpend()  { return messages > 0; }

    public static synchronized Map<String, Window> windows() {
        return new LinkedHashMap<>(WINDOWS);
    }

    public static String rateLimitType() { return rateLimitType; }
    public static boolean usingOverage()  { return usingOverage; }
    public static int messages()          { return messages; }
    public static double costUsd()        { return costUsd; }
    public static long inputTokens()      { return inputTokens; }
    public static long outputTokens()     { return outputTokens; }
    public static long cacheReadTokens()  { return cacheReadTokens; }
    public static long cacheWriteTokens() { return cacheWriteTokens; }
    public static int turns()             { return turns; }
    public static long totalMillis()      { return totalMillis; }

    /** Feed it the body of a {@code rate_limit_event}. */
    public static synchronized void updateLimits(JsonObject info) {
        if (info == null) return;
        try {
            rateLimitType = str(info, "rateLimitType", rateLimitType);
            if (info.has("isUsingOverage") && !info.get("isUsingOverage").isJsonNull()) {
                usingOverage = info.get("isUsingOverage").getAsBoolean();
            }
            JsonElement unified = info.get("unifiedWindows");
            if (unified != null && unified.isJsonObject()) {
                WINDOWS.clear();
                for (Map.Entry<String, JsonElement> e : unified.getAsJsonObject().entrySet()) {
                    if (!e.getValue().isJsonObject()) continue;
                    JsonObject w = e.getValue().getAsJsonObject();
                    WINDOWS.put(e.getKey(), new Window(
                            e.getKey(),
                            w.has("utilization") ? w.get("utilization").getAsDouble() : 0.0,
                            w.has("resetsAt") ? w.get("resetsAt").getAsLong() : 0L,
                            str(w, "status", "unknown")));
                }
                haveLimits = !WINDOWS.isEmpty();
            }
        } catch (Exception e) {
            LumiClient.LOG.debug("could not read rate limit info", e);
        }
    }

    /** Feed it a {@code result} event (or the single json envelope when not streaming). */
    public static synchronized void recordResult(JsonObject result) {
        if (result == null) return;
        try {
            messages++;
            if (result.has("total_cost_usd") && !result.get("total_cost_usd").isJsonNull()) {
                costUsd += result.get("total_cost_usd").getAsDouble();
            }
            if (result.has("num_turns") && !result.get("num_turns").isJsonNull()) {
                turns += result.get("num_turns").getAsInt();
            }
            if (result.has("duration_ms") && !result.get("duration_ms").isJsonNull()) {
                totalMillis += result.get("duration_ms").getAsLong();
            }
            JsonElement u = result.get("usage");
            if (u != null && u.isJsonObject()) {
                JsonObject usage = u.getAsJsonObject();
                inputTokens += num(usage, "input_tokens");
                outputTokens += num(usage, "output_tokens");
                cacheReadTokens += num(usage, "cache_read_input_tokens");
                cacheWriteTokens += num(usage, "cache_creation_input_tokens");
            }
        } catch (Exception e) {
            LumiClient.LOG.debug("could not read usage", e);
        }
    }

    /** "5h 17% · 7d 2%" - short enough for the action bar or a status line. */
    public static synchronized String compact() {
        if (WINDOWS.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Window w : WINDOWS.values()) {
            if (w.utilization() <= 0 && w.name().contains("overage")) continue;
            if (sb.length() > 0) sb.append(" · ");
            sb.append(w.shortLabel()).append(' ').append(w.percent()).append('%');
        }
        return sb.toString();
    }

    /** The worst window right now, for deciding whether to warn. */
    public static synchronized double peakUtilization() {
        double peak = 0;
        for (Window w : WINDOWS.values()) {
            if (w.name().contains("overage")) continue;
            peak = Math.max(peak, w.utilization());
        }
        return peak;
    }

    /** A ten-cell bar: [####------] */
    public static String bar(double utilization) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, utilization)) * 10);
        return "[" + "■".repeat(filled) + "·".repeat(10 - filled) + "]";
    }

    public static String tokens(long n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 1_000) return String.format("%.1fk", n / 1_000.0);
        return Long.toString(n);
    }

    private static long num(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return (e == null || e.isJsonNull()) ? 0L : e.getAsLong();
    }

    private static String str(JsonObject o, String key, String fallback) {
        JsonElement e = o.get(key);
        return (e == null || e.isJsonNull()) ? fallback : e.getAsString();
    }
}
