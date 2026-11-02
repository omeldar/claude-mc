package com.omeldar.claudecraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.omeldar.claudecraft.ui.ApprovalScreen;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * A tiny loopback-only HTTP server so an <em>already running</em> Claude Code session - one you
 * started in a terminal yourself - can read the game's state and push proposals into the
 * approval menu, without the mod starting its own session.
 *
 * <p>Bound to 127.0.0.1 and guarded by a token written to {@code claudecraft/bridge.json}.
 */
public final class BridgeServer {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static HttpServer server;

    private BridgeServer() {}

    public static synchronized void start() {
        Config cfg = Config.get();
        if (!cfg.bridgeEnabled || server != null) return;
        try {
            server = HttpServer.create(
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), cfg.bridgePort), 0);
            server.createContext("/ping", BridgeServer::handlePing);
            server.createContext("/context", BridgeServer::handleContext);
            server.createContext("/propose", BridgeServer::handlePropose);
            server.setExecutor(Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "claudecraft-bridge");
                t.setDaemon(true);
                return t;
            }));
            server.start();

            Workspace.ensure();
            JsonObject info = new JsonObject();
            info.addProperty("url", "http://127.0.0.1:" + cfg.bridgePort);
            info.addProperty("token", cfg.bridgeToken);
            info.addProperty("header", "X-ClaudeCraft-Token");
            Files.writeString(Workspace.bridgeFile(), GSON.toJson(info), StandardCharsets.UTF_8);

            ClaudeCraftClient.LOG.info("bridge listening on 127.0.0.1:{}", cfg.bridgePort);
        } catch (Exception e) {
            ClaudeCraftClient.LOG.warn("bridge could not start on port {}", cfg.bridgePort, e);
            server = null;
        }
    }

    public static synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    private static boolean authorised(HttpExchange ex) {
        String header = ex.getRequestHeaders().getFirst("X-ClaudeCraft-Token");
        if (header != null && header.equals(Config.get().bridgeToken)) return true;
        String query = ex.getRequestURI().getQuery();
        return query != null && query.contains("token=" + Config.get().bridgeToken);
    }

    private static void handlePing(HttpExchange ex) throws IOException {
        JsonObject o = new JsonObject();
        o.addProperty("ok", true);
        o.addProperty("mod", "claudecraft");
        MinecraftClient mc = MinecraftClient.getInstance();
        o.addProperty("inWorld", mc != null && mc.world != null);
        respond(ex, 200, GSON.toJson(o));
    }

    private static void handleContext(HttpExchange ex) throws IOException {
        if (!authorised(ex)) { respond(ex, 401, "{\"error\":\"bad token\"}"); return; }
        MinecraftClient mc = MinecraftClient.getInstance();
        CompletableFuture<String> future = new CompletableFuture<>();
        mc.execute(() -> {
            try {
                future.complete(ContextSnapshot.json(mc));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        try {
            respond(ex, 200, future.get(5, TimeUnit.SECONDS));
        } catch (Exception e) {
            respond(ex, 500, "{\"error\":\"could not capture context\"}");
        }
    }

    private static void handlePropose(HttpExchange ex) throws IOException {
        if (!authorised(ex)) { respond(ex, 401, "{\"error\":\"bad token\"}"); return; }
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            respond(ex, 405, "{\"error\":\"use POST\"}");
            return;
        }
        String body;
        try (InputStream in = ex.getRequestBody()) {
            body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        Proposal p;
        try {
            p = GSON.fromJson(body, Proposal.class);
        } catch (Exception e) {
            respond(ex, 400, "{\"error\":\"body is not valid json\"}");
            return;
        }
        if (p == null || p.isEmpty()) {
            respond(ex, 400, "{\"error\":\"no commands in proposal\"}");
            return;
        }

        int cap = Config.get().maxCommands;
        if (p.commands.size() > cap) {
            p.commands = p.commands.subList(0, cap);
        }
        for (Proposal.Step s : p.commands) {
            if (s.note == null) s.note = "";
            if (s.cmd != null) s.cmd = s.cmd.trim();
            if (s.cmd != null && Config.get().isBlocked(s.cmd)) {
                s.note = "[blocked by config] " + s.note;
                s.ran = true;
            }
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        final Proposal proposal = p;
        mc.execute(() -> {
            if (mc.player != null) {
                mc.setScreen(new ApprovalScreen(proposal));
            }
        });

        JsonObject o = new JsonObject();
        o.addProperty("ok", true);
        o.addProperty("commands", proposal.commands.size());
        o.addProperty("commandsAllowed", CommandPermissions.allowed(mc));
        o.addProperty("authority", CommandPermissions.authority(mc));
        respond(ex, 200, GSON.toJson(o));
    }

    private static void respond(HttpExchange ex, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
