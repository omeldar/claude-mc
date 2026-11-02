package com.omeldar.claudecraft.ui;

import com.omeldar.claudecraft.Chat;
import com.omeldar.claudecraft.CommandPermissions;
import com.omeldar.claudecraft.CommandQueue;
import com.omeldar.claudecraft.Config;
import com.omeldar.claudecraft.Proposal;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** The approval menu: every command Claude proposed, with a button to run each one. */
public class ApprovalScreen extends Screen {
    private static final int PER_PAGE = 5;
    private static final int ROW_HEIGHT = 32;

    private static final int COLOR_TITLE = 0xFFFFD700;
    private static final int COLOR_CMD = 0xFFFFFFFF;
    private static final int COLOR_NOTE = 0xFFA0A0A0;
    private static final int COLOR_DIM = 0xFF808080;
    private static final int COLOR_DONE = 0xFF5C8A5C;

    private final Proposal proposal;
    private final int page;

    public ApprovalScreen(Proposal proposal) {
        this(proposal, 0);
    }

    public ApprovalScreen(Proposal proposal, int page) {
        super(Text.literal("Claude suggests"));
        this.proposal = proposal;
        this.page = page;
    }

    private int pageCount() {
        return Math.max(1, (proposal.size() + PER_PAGE - 1) / PER_PAGE);
    }

    private int listTop() {
        return CommandPermissions.allowed(MinecraftClient.getInstance()) ? 64 : 80;
    }

    @Override
    protected void init() {
        super.init();
        int left = Math.max(16, this.width / 2 - 220);
        int right = Math.min(this.width - 16, this.width / 2 + 220);
        int first = page * PER_PAGE;
        int last = Math.min(proposal.size(), first + PER_PAGE);

        for (int i = first; i < last; i++) {
            final Proposal.Step step = proposal.commands.get(i);
            int y = listTop() + (i - first) * ROW_HEIGHT;
            boolean blocked = Config.get().isBlocked(step.cmd);

            ButtonWidget run = ButtonWidget.builder(
                    Text.literal(step.ran ? "Sent" : "Run"),
                    b -> {
                        CommandQueue.enqueue(step.cmd);
                        step.ran = true;
                        b.setMessage(Text.literal("Sent"));
                        b.active = false;
                    })
                .dimensions(right - 100, y - 2, 44, 20)
                .build();
            run.active = !step.ran && !blocked && CommandPermissions.allowed(MinecraftClient.getInstance());
            addDrawableChild(run);

            addDrawableChild(ButtonWidget.builder(
                    Text.literal("Copy"),
                    b -> {
                        MinecraftClient.getInstance().keyboard.setClipboard(step.cmd);
                        Chat.dim("copied: " + step.cmd);
                    })
                .dimensions(right - 52, y - 2, 44, 20)
                .build());
        }

        int footer = this.height - 30;

        ButtonWidget runAll = ButtonWidget.builder(Text.literal("Run all"), b -> runAll())
                .dimensions(left, footer, 70, 20).build();
        runAll.active = CommandPermissions.allowed(MinecraftClient.getInstance());
        addDrawableChild(runAll);

        addDrawableChild(ButtonWidget.builder(Text.literal("Undo"), b -> {
            CommandQueue.enqueue("//undo");
            Chat.dim("queued //undo");
        }).dimensions(left + 74, footer, 56, 20).build());

        ButtonWidget prev = ButtonWidget.builder(Text.literal("< Prev"),
                b -> switchPage(page - 1))
            .dimensions(left + 134, footer, 56, 20).build();
        prev.active = page > 0;
        addDrawableChild(prev);

        ButtonWidget next = ButtonWidget.builder(Text.literal("Next >"),
                b -> switchPage(page + 1))
            .dimensions(left + 194, footer, 56, 20).build();
        next.active = page < pageCount() - 1;
        addDrawableChild(next);

        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> this.close())
                .dimensions(right - 60, footer, 60, 20).build());
    }

    private void switchPage(int target) {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.setScreen(new ApprovalScreen(proposal, Math.max(0, Math.min(pageCount() - 1, target))));
    }

    private void runAll() {
        int queued = 0;
        for (Proposal.Step s : proposal.commands) {
            if (s.ran || Config.get().isBlocked(s.cmd)) continue;
            CommandQueue.enqueue(s.cmd);
            s.ran = true;
            queued++;
        }
        Chat.dim("queued " + queued + " command" + (queued == 1 ? "" : "s"));
        this.close();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int left = Math.max(16, this.width / 2 - 220);
        int right = Math.min(this.width - 16, this.width / 2 + 220);

        context.drawTextWithShadow(this.textRenderer,
                Text.literal("Claude suggests").formatted(net.minecraft.util.Formatting.BOLD),
                left, 18, COLOR_TITLE);

        if (proposal.summary != null && !proposal.summary.isBlank()) {
            context.drawTextWithShadow(this.textRenderer,
                    Text.literal(this.textRenderer.trimToWidth(proposal.summary, right - left)),
                    left, 32, COLOR_CMD);
        }

        String meta = proposal.size() + " command" + (proposal.size() == 1 ? "" : "s")
                + "   page " + (page + 1) + "/" + pageCount()
                + "   queued " + CommandQueue.pending();
        context.drawTextWithShadow(this.textRenderer, Text.literal(meta), left, 46, COLOR_DIM);

        if (!CommandPermissions.allowed(MinecraftClient.getInstance())) {
            context.fill(left - 4, 56, right, 72, 0x50FF0000);
            context.drawTextWithShadow(this.textRenderer,
                    Text.literal(this.textRenderer.trimToWidth(
                            "Read-only here - nothing can be run. "
                                    + CommandPermissions.explain(MinecraftClient.getInstance()),
                            right - left)),
                    left, 60, 0xFFFF8080);
        }

        int first = page * PER_PAGE;
        int last = Math.min(proposal.size(), first + PER_PAGE);
        for (int i = first; i < last; i++) {
            Proposal.Step step = proposal.commands.get(i);
            int y = listTop() + (i - first) * ROW_HEIGHT;
            int textWidth = (right - 108) - left;

            context.fill(left - 4, y - 5, right, y + 23, 0x30000000);

            context.drawTextWithShadow(this.textRenderer,
                    Text.literal(this.textRenderer.trimToWidth((i + 1) + ". " + step.cmd, textWidth)),
                    left, y, step.ran ? COLOR_DONE : COLOR_CMD);

            if (step.note != null && !step.note.isBlank()) {
                context.drawTextWithShadow(this.textRenderer,
                        Text.literal(this.textRenderer.trimToWidth(step.note, textWidth)),
                        left, y + 12, COLOR_NOTE);
            }
        }

        if (proposal.isEmpty()) {
            context.drawTextWithShadow(this.textRenderer,
                    Text.literal("Claude did not propose any commands."),
                    left, listTop(), COLOR_NOTE);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
