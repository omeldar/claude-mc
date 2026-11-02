package com.omeldar.claudecraft;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/**
 * Everything ClaudeCraft prints. Messages are tagged with the mod's own glyph rather than a
 * player head, and proposed commands are printed as clickable lines so they can be run without
 * opening the menu.
 */
public final class Chat {
    private Chat() {}

    /** Bitmap font supplying the Claude mark at U+E000. */
    public static final Identifier ICON_FONT = Identifier.of("claudecraft", "icons");
    private static final String GLYPH = "";

    /** Claude clay, so the mark reads as itself rather than as chat gold. */
    private static final int CLAY = 0xD97757;

    public static MutableText badge() {
        return Text.literal(GLYPH)
                .styled(s -> s.withFont(new StyleSpriteSource.Font(ICON_FONT)).withColor(TextColor.fromRgb(CLAY)));
    }

    private static MutableText prefix() {
        return Text.empty().append(badge()).append(Text.literal(" "));
    }

    private static void send(Text body) {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.sendMessage(Text.empty().append(prefix()).append(body), false);
            }
        });
    }

    public static void info(Text body) {
        send(body);
    }

    public static void info(String body) {
        send(Text.literal(body).formatted(Formatting.WHITE));
    }

    public static void warn(String body) {
        send(Text.literal(body).formatted(Formatting.RED));
    }

    public static void dim(String body) {
        send(Text.literal(body).formatted(Formatting.GRAY));
    }

    /** A short-lived line above the hotbar, used for progress rather than chat spam. */
    public static void overlay(String body) {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.sendMessage(
                        Text.empty().append(badge()).append(Text.literal(" " + body)
                                .formatted(Formatting.GRAY)), true);
            }
        });
    }

    /**
     * Prints one clickable line per proposed command. Clicking [Run] fires our own client
     * command, so nothing depends on how the server parses a chat click.
     */
    public static void proposal(Proposal proposal) {
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean allowed = CommandPermissions.allowed(mc);

        MutableText header = Text.empty()
                .append(Text.literal(proposal.size() + " command"
                        + (proposal.size() == 1 ? "" : "s")).formatted(Formatting.GOLD));
        if (proposal.summary != null && !proposal.summary.isBlank()) {
            header.append(Text.literal(" - " + proposal.summary).formatted(Formatting.GRAY));
        }
        send(header);

        for (int i = 0; i < proposal.commands.size(); i++) {
            Proposal.Step step = proposal.commands.get(i);
            final int index = i;
            boolean runnable = allowed && !Config.get().isBlocked(step.cmd);

            MutableText line = Text.literal("  " + (i + 1) + ". ").formatted(Formatting.DARK_GRAY);
            line.append(Text.literal(step.cmd).formatted(Formatting.WHITE));

            if (runnable) {
                line.append(Text.literal("  [Run]").styled(s -> s
                        .withColor(Formatting.GREEN)
                        .withBold(true)
                        .withClickEvent(new ClickEvent.RunCommand("/claude run " + (index + 1)))
                        .withHoverEvent(new HoverEvent.ShowText(
                                Text.literal("Run: " + step.cmd)))));
            } else {
                line.append(Text.literal("  [blocked]").formatted(Formatting.DARK_RED));
            }

            line.append(Text.literal("  [Copy]").styled(s -> s
                    .withColor(Formatting.AQUA)
                    .withClickEvent(new ClickEvent.CopyToClipboard(step.cmd))
                    .withHoverEvent(new HoverEvent.ShowText(Text.literal("Copy to clipboard")))));

            MinecraftClient.getInstance().execute(() -> {
                if (mc.player != null) mc.player.sendMessage(line, false);
            });

            if (step.note != null && !step.note.isBlank()) {
                final String note = step.note;
                MinecraftClient.getInstance().execute(() -> {
                    if (mc.player != null) {
                        mc.player.sendMessage(
                                Text.literal("      " + note).formatted(Formatting.DARK_GRAY), false);
                    }
                });
            }
        }

        if (proposal.size() > 1 && allowed) {
            send(Text.literal("[Run all]").styled(s -> s
                        .withColor(Formatting.GREEN)
                        .withBold(true)
                        .withClickEvent(new ClickEvent.RunCommand("/claude runall")))
                    .append(Text.literal("  "))
                    .append(Text.literal("[Open menu]").styled(s -> s
                        .withColor(Formatting.YELLOW)
                        .withClickEvent(new ClickEvent.RunCommand("/claude last")))));
        }
    }
}
