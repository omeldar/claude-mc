package com.omeldar.claudecraft;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.omeldar.claudecraft.ui.ApprovalScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;

public class ClaudeCraftClient implements ClientModInitializer {
    public static final String MOD_ID = "claudecraft";
    public static final Logger LOG = LoggerFactory.getLogger("ClaudeCraft");

    private static Proposal lastProposal = null;

    @Override
    public void onInitializeClient() {
        Workspace.ensure();
        Config.get();
        ClaudeRunner.restoreSession();
        BridgeServer.start();

        ClientTickEvents.END_CLIENT_TICK.register(CommandQueue::tick);

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> {
            LiteralCommandNode<FabricClientCommandSource> node = dispatcher.register(tree("claude"));
            // /ai as a shorthand for the same thing.
            dispatcher.register(ClientCommandManager.literal("ai").redirect(node));
        });

        LOG.info("ClaudeCraft ready - workspace at {}", Workspace.root());
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> tree(String name) {
        return ClientCommandManager.literal(name)

            .then(ClientCommandManager.literal("new")
                .then(ClientCommandManager.argument("prompt", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        ask(StringArgumentType.getString(ctx, "prompt"), true, false, 0);
                        return 1;
                    })))

            .then(ClientCommandManager.literal("shot")
                .then(ClientCommandManager.argument("prompt", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        ask(StringArgumentType.getString(ctx, "prompt"), false, true, 0);
                        return 1;
                    })))

            .then(ClientCommandManager.literal("scan")
                .then(ClientCommandManager.argument("radius", IntegerArgumentType.integer(1, 64))
                    .then(ClientCommandManager.argument("prompt", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            ask(StringArgumentType.getString(ctx, "prompt"), false, false,
                                    IntegerArgumentType.getInteger(ctx, "radius"));
                            return 1;
                        }))))

            // Targets of the clickable [Run] / [Run all] links printed in chat.
            .then(ClientCommandManager.literal("run")
                .then(ClientCommandManager.argument("index", IntegerArgumentType.integer(1, 64))
                    .executes(ctx -> {
                        runOne(IntegerArgumentType.getInteger(ctx, "index"));
                        return 1;
                    })))

            .then(ClientCommandManager.literal("runall").executes(ctx -> {
                runAll();
                return 1;
            }))

            .then(ClientCommandManager.literal("last").executes(ctx -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (lastProposal == null) {
                    Chat.dim("no proposal yet");
                } else {
                    mc.execute(() -> mc.setScreen(new ApprovalScreen(lastProposal)));
                }
                return 1;
            }))

            .then(ClientCommandManager.literal("session").executes(ctx -> {
                String sid = ClaudeRunner.sessionId();
                Chat.dim(sid == null ? "no session yet - the next prompt starts one"
                                     : "session " + sid);
                return 1;
            }))

            .then(ClientCommandManager.literal("status").executes(ctx -> {
                status();
                return 1;
            }))

            .then(ClientCommandManager.literal("cancel").executes(ctx -> {
                CommandQueue.clear();
                Chat.dim("queue cleared");
                return 1;
            }))

            .then(ClientCommandManager.literal("undo").executes(ctx -> {
                CommandQueue.enqueue("//undo");
                return 1;
            }))

            .then(ClientCommandManager.argument("prompt", StringArgumentType.greedyString())
                .executes(ctx -> {
                    ask(StringArgumentType.getString(ctx, "prompt"), false, false, 0);
                    return 1;
                }))

            .executes(ctx -> {
                help();
                return 1;
            });
    }

    private static void runOne(int oneBased) {
        if (lastProposal == null || oneBased > lastProposal.size()) {
            Chat.warn("no such command in the last proposal");
            return;
        }
        Proposal.Step step = lastProposal.commands.get(oneBased - 1);
        if (step.ran) {
            Chat.dim("already sent: " + step.cmd);
            return;
        }
        CommandQueue.enqueue(step.cmd);
        step.ran = true;
    }

    private static void runAll() {
        if (lastProposal == null) {
            Chat.warn("no proposal to run");
            return;
        }
        int queued = 0;
        for (Proposal.Step s : lastProposal.commands) {
            if (s.ran || Config.get().isBlocked(s.cmd)) continue;
            CommandQueue.enqueue(s.cmd);
            s.ran = true;
            queued++;
        }
        Chat.dim("queued " + queued + " command" + (queued == 1 ? "" : "s"));
    }

    private static void help() {
        Chat.info(Text.literal("ClaudeCraft").formatted(Formatting.GOLD, Formatting.BOLD));
        Chat.dim("/claude <prompt>           ask, keeping the current session");
        Chat.dim("/claude new <prompt>       start a fresh session");
        Chat.dim("/claude shot <prompt>      attach your newest screenshot (press F2 first)");
        Chat.dim("/claude scan <r> <prompt>  attach a block scan of radius r");
        Chat.dim("/claude last               reopen the approval menu");
        Chat.dim("/claude undo               queue //undo");
        Chat.dim("/claude cancel             drop anything still queued");
        Chat.dim("/claude status             session, permissions, bridge");
        Chat.dim("/ai works as a shorthand for all of these.");
    }

    private static void status() {
        MinecraftClient mc = MinecraftClient.getInstance();
        Config cfg = Config.get();
        Chat.info(Text.literal("ClaudeCraft status").formatted(Formatting.GOLD));
        Chat.dim("model        " + cfg.model);
        Chat.dim("session      " + Optional.ofNullable(ClaudeRunner.sessionId()).orElse("none yet"));
        Chat.dim("claude       " + ClaudeRunner.resolveExecutable());
        Chat.dim("queue        " + CommandQueue.pending() + " pending");
        Chat.dim("bridge       " + (cfg.bridgeEnabled ? "127.0.0.1:" + cfg.bridgePort : "off"));
        Chat.dim("workspace    " + Workspace.root());
        if (CommandPermissions.allowed(mc)) {
            Chat.info(Text.literal("commands     allowed (" + CommandPermissions.authority(mc) + ")")
                    .formatted(Formatting.GREEN));
        } else {
            Chat.info(Text.literal("commands     READ-ONLY (" + CommandPermissions.authority(mc) + ")")
                    .formatted(Formatting.RED));
            Chat.dim(CommandPermissions.explain(mc));
        }
    }

    /** Builds the prompt with whatever context was asked for, then hands it to the CLI. */
    private static void ask(String prompt, boolean freshSession, boolean withShot, int scanRadius) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (ClaudeRunner.isBusy()) {
            Chat.warn("still waiting on the last reply");
            return;
        }

        Workspace.ensure();
        ContextSnapshot.write(mc);

        StringBuilder full = new StringBuilder(prompt.trim());
        full.append("\n\n---\nLive game context is in context.json in your working directory; "
                  + "it was just rewritten for this message.");

        if (withShot) {
            Path copied = attachNewestScreenshot();
            if (copied == null) {
                Chat.warn("no recent screenshot found - press F2 first, then run the command again");
                return;
            }
            full.append("\nThe player attached a screenshot: shots/")
                .append(copied.getFileName())
                .append(" - read it, it shows what they are looking at.");
            Chat.dim("attached " + copied.getFileName());
        }

        if (scanRadius > 0) {
            Path scan = WorldScan.dump(mc, scanRadius);
            if (scan != null) {
                full.append("\nA block scan of the area is at scans/")
                    .append(scan.getFileName())
                    .append(" - read it for the blocks actually around the player.");
                Chat.dim("attached " + scan.getFileName());
            }
        }

        String notice = CommandPermissions.promptNotice(mc);
        if (!notice.isEmpty()) full.append('\n').append(notice);

        if (freshSession) {
            ClaudeRunner.newSession();
            Chat.dim("starting a new session");
        }
        if (!CommandPermissions.allowed(mc)) {
            Chat.dim("read-only here - answering with advice only");
        }

        Chat.overlay("thinking...");
        ClaudeRunner.ask(full.toString(),
            proposal -> mc.execute(() -> deliver(mc, proposal)),
            error -> {
                Chat.warn(error);
                Chat.overlay("failed");
            });
    }

    private static void deliver(MinecraftClient mc, Proposal proposal) {
        lastProposal = proposal;

        if (proposal.prose != null && !proposal.prose.isBlank()) {
            for (String line : proposal.prose.split("\n")) {
                if (!line.isBlank()) Chat.info(Text.literal(line.strip()));
            }
        }

        if (proposal.isEmpty()) {
            Chat.overlay("answered");
            return;
        }

        if (!CommandPermissions.allowed(mc)) {
            Chat.warn("Claude proposed " + proposal.size()
                    + " command(s) but none can run here. " + CommandPermissions.explain(mc));
            Chat.overlay("read-only");
            return;
        }

        Chat.proposal(proposal);
        Chat.overlay(proposal.size() + " command" + (proposal.size() == 1 ? "" : "s") + " ready");
    }

    /** Copies the newest file from the screenshots folder into the workspace. */
    private static Path attachNewestScreenshot() {
        try {
            File dir = FabricLoader.getInstance().getGameDir().resolve("screenshots").toFile();
            File[] files = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".png"));
            if (files == null || files.length == 0) return null;
            File newest = Arrays.stream(files)
                    .max(Comparator.comparingLong(File::lastModified))
                    .orElse(null);
            if (newest == null) return null;
            if (System.currentTimeMillis() - newest.lastModified() > 10 * 60 * 1000L) return null;

            String name = "shot-" + LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".png";
            Path target = Workspace.shots().resolve(name);
            Files.copy(newest.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (Exception e) {
            LOG.warn("could not attach screenshot", e);
            return null;
        }
    }
}
