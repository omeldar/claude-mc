package com.omeldar.claudemc.ui;

import com.omeldar.claudemc.Chat;
import com.omeldar.claudemc.CommandPermissions;
import com.omeldar.claudemc.CommandQueue;
import com.omeldar.claudemc.Config;
import com.omeldar.claudemc.Proposal;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * The approval menu. Every proposed command sits in an editable field, so it can be adjusted
 * before it is sent - a radius nudged, a block swapped - without retyping the whole thing.
 */
public class ApprovalScreen extends Screen {
    private static final int PER_PAGE = 5;
    private static final int ROW_HEIGHT = 34;

    private static final int COLOR_TITLE = 0xFFD97757;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_NOTE = 0xFFA0A0A0;
    private static final int COLOR_DIM = 0xFF808080;

    private final Proposal proposal;
    private final int page;
    private final List<Row> rows = new ArrayList<>();

    private record Row(Proposal.Step step, TextFieldWidget field, ButtonWidget run) {}

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

    private boolean allowed() {
        return CommandPermissions.allowed(MinecraftClient.getInstance());
    }

    private int listTop() {
        return allowed() ? 64 : 80;
    }

    @Override
    protected void init() {
        super.init();
        rows.clear();

        int left = Math.max(16, this.width / 2 - 230);
        int right = Math.min(this.width - 16, this.width / 2 + 230);
        int first = page * PER_PAGE;
        int last = Math.min(proposal.size(), first + PER_PAGE);

        for (int i = first; i < last; i++) {
            final Proposal.Step step = proposal.commands.get(i);
            int y = listTop() + (i - first) * ROW_HEIGHT;
            boolean blocked = Config.get().isBlocked(step.cmd);

            TextFieldWidget field = new TextFieldWidget(this.textRenderer,
                    left, y, (right - 104) - left, 18, Text.literal("command"));
            field.setMaxLength(512);
            field.setText(step.cmd);
            field.setEditable(!step.ran && !blocked && allowed());
            addDrawableChild(field);

            ButtonWidget run = ButtonWidget.builder(
                    Text.literal(step.ran ? "Sent" : "Run"),
                    b -> {
                        String edited = field.getText().trim();
                        if (edited.isEmpty()) return;
                        step.cmd = edited;            // keep the edit for Run all and /claude run
                        CommandQueue.enqueue(edited);
                        step.ran = true;
                        field.setEditable(false);
                        b.setMessage(Text.literal("Sent"));
                        b.active = false;
                    })
                .dimensions(right - 100, y - 1, 44, 20)
                .build();
            run.active = !step.ran && !blocked && allowed();
            addDrawableChild(run);

            addDrawableChild(ButtonWidget.builder(Text.literal("Copy"), b -> {
                MinecraftClient.getInstance().keyboard.setClipboard(field.getText());
                Chat.dim("copied: " + field.getText());
            }).dimensions(right - 52, y - 1, 44, 20).build());

            rows.add(new Row(step, field, run));
        }

        int footer = this.height - 30;

        ButtonWidget runAll = ButtonWidget.builder(Text.literal("Run all"), b -> runAll())
                .dimensions(left, footer, 70, 20).build();
        runAll.active = allowed();
        addDrawableChild(runAll);

        addDrawableChild(ButtonWidget.builder(Text.literal("Undo"), b -> {
            CommandQueue.enqueue("//undo");
            Chat.dim("queued //undo");
        }).dimensions(left + 74, footer, 56, 20).build());

        ButtonWidget prev = ButtonWidget.builder(Text.literal("< Prev"), b -> switchPage(page - 1))
                .dimensions(left + 134, footer, 56, 20).build();
        prev.active = page > 0;
        addDrawableChild(prev);

        ButtonWidget next = ButtonWidget.builder(Text.literal("Next >"), b -> switchPage(page + 1))
                .dimensions(left + 194, footer, 56, 20).build();
        next.active = page < pageCount() - 1;
        addDrawableChild(next);

        addDrawableChild(ButtonWidget.builder(Text.literal("Ask again"),
                b -> MinecraftClient.getInstance().setScreen(new PromptScreen()))
                .dimensions(left + 254, footer, 70, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> this.close())
                .dimensions(right - 60, footer, 60, 20).build());
    }

    private void switchPage(int target) {
        commitEdits();
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.setScreen(new ApprovalScreen(proposal, Math.max(0, Math.min(pageCount() - 1, target))));
    }

    /** Copies whatever is in the fields back onto the proposal so edits are not lost. */
    private void commitEdits() {
        for (Row row : rows) {
            String edited = row.field().getText().trim();
            if (!edited.isEmpty()) row.step().cmd = edited;
        }
    }

    private void runAll() {
        commitEdits();
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
    public void removed() {
        commitEdits();
        super.removed();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int left = Math.max(16, this.width / 2 - 230);
        int right = Math.min(this.width - 16, this.width / 2 + 230);

        context.drawTextWithShadow(this.textRenderer,
                Text.literal("Claude suggests").formatted(Formatting.BOLD),
                left, 18, COLOR_TITLE);

        if (proposal.summary != null && !proposal.summary.isBlank()) {
            context.drawTextWithShadow(this.textRenderer,
                    Text.literal(this.textRenderer.trimToWidth(proposal.summary, right - left)),
                    left, 32, COLOR_TEXT);
        }

        context.drawTextWithShadow(this.textRenderer,
                Text.literal(proposal.size() + " command" + (proposal.size() == 1 ? "" : "s")
                        + "   page " + (page + 1) + "/" + pageCount()
                        + "   queued " + CommandQueue.pending()
                        + "   editable"),
                left, 46, COLOR_DIM);

        if (!allowed()) {
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
            if (step.note != null && !step.note.isBlank()) {
                context.drawTextWithShadow(this.textRenderer,
                        Text.literal(this.textRenderer.trimToWidth(
                                (i + 1) + ". " + step.note, right - left)),
                        left, y + 20, COLOR_NOTE);
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
