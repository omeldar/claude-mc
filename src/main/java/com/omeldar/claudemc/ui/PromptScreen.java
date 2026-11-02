package com.omeldar.claudemc.ui;

import com.omeldar.claudemc.ClaudeMCClient;
import com.omeldar.claudemc.ClaudeRunner;
import com.omeldar.claudemc.CommandPermissions;
import com.omeldar.claudemc.Config;
import com.omeldar.claudemc.Usage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

/**
 * The prompt hub: type a question, pick what to attach, switch model and effort, send.
 *
 * <p>Attachments are independent toggles, so a screenshot and a scan can go together.
 * Choices are remembered between opens.
 */
public class PromptScreen extends Screen {
    private static final String[] MODELS = {"opus", "sonnet", "haiku", "fable"};
    private static final String[] EFFORTS = {"", "low", "medium", "high"};
    private static final int[] RADII = {0, 8, 16, 24, 32, 48, 64};

    // Remembered across opens.
    private static boolean attachShot = false;
    private static int radiusIndex = 0;
    private static boolean freshSession = false;
    private static String draft = "";

    private TextFieldWidget field;

    public PromptScreen() {
        super(Text.literal("Ask Claude"));
    }

    @Override
    protected void init() {
        super.init();
        int w = Math.min(420, this.width - 40);
        int left = (this.width - w) / 2;
        int top = this.height / 2 - 60;

        field = new TextFieldWidget(this.textRenderer, left, top, w, 20,
                Text.literal("prompt"));
        field.setMaxLength(1024);
        field.setPlaceholder(Text.literal("what should I help with?").formatted(Formatting.DARK_GRAY));
        field.setText(draft);
        addDrawableChild(field);
        this.setInitialFocus(field);

        int row = top + 28;
        int third = (w - 8) / 3;

        addDrawableChild(ButtonWidget.builder(shotLabel(), b -> {
            attachShot = !attachShot;
            b.setMessage(shotLabel());
        }).dimensions(left, row, third, 20).build());

        addDrawableChild(ButtonWidget.builder(scanLabel(), b -> {
            radiusIndex = (radiusIndex + 1) % RADII.length;
            b.setMessage(scanLabel());
        }).dimensions(left + third + 4, row, third, 20).build());

        addDrawableChild(ButtonWidget.builder(sessionLabel(), b -> {
            freshSession = !freshSession;
            b.setMessage(sessionLabel());
        }).dimensions(left + 2 * (third + 4), row, third, 20).build());

        row += 24;
        int half = (w - 4) / 2;

        addDrawableChild(ButtonWidget.builder(modelLabel(), b -> {
            Config cfg = Config.get();
            cfg.model = next(MODELS, cfg.model);
            cfg.save();
            b.setMessage(modelLabel());
        }).dimensions(left, row, half, 20).build());

        addDrawableChild(ButtonWidget.builder(effortLabel(), b -> {
            Config cfg = Config.get();
            cfg.effort = next(EFFORTS, cfg.effort == null ? "" : cfg.effort);
            cfg.save();
            b.setMessage(effortLabel());
        }).dimensions(left + half + 4, row, half, 20).build());

        row += 28;
        addDrawableChild(ButtonWidget.builder(Text.literal("Ask"), b -> submit())
                .dimensions(left, row, half, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> this.close())
                .dimensions(left + half + 4, row, half, 20).build());
    }

    private static String next(String[] options, String current) {
        for (int i = 0; i < options.length; i++) {
            if (options[i].equalsIgnoreCase(current)) return options[(i + 1) % options.length];
        }
        return options[0];
    }

    private static Text shotLabel() {
        return Text.literal("Screenshot: " + (attachShot ? "on" : "off"))
                .formatted(attachShot ? Formatting.GREEN : Formatting.GRAY);
    }

    private static Text scanLabel() {
        int r = RADII[radiusIndex];
        return Text.literal(r == 0 ? "Scan: off" : "Scan: r=" + r)
                .formatted(r == 0 ? Formatting.GRAY : Formatting.GREEN);
    }

    private static Text sessionLabel() {
        return Text.literal(freshSession ? "Session: new" : "Session: keep")
                .formatted(freshSession ? Formatting.YELLOW : Formatting.GRAY);
    }

    private static Text modelLabel() {
        return Text.literal("Model: " + Config.get().model);
    }

    private static Text effortLabel() {
        String e = Config.get().effort;
        return Text.literal("Effort: " + (e == null || e.isBlank() ? "default" : e));
    }

    private void submit() {
        String prompt = field.getText().trim();
        if (prompt.isEmpty()) return;
        draft = "";
        boolean fresh = freshSession;
        freshSession = false;   // one-shot
        this.close();
        ClaudeMCClient.ask(prompt, fresh, attachShot, RADII[radiusIndex]);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            submit();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void removed() {
        if (field != null) draft = field.getText();
        super.removed();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int w = Math.min(420, this.width - 40);
        int left = (this.width - w) / 2;
        int top = this.height / 2 - 60;

        context.drawTextWithShadow(this.textRenderer,
                Text.literal("Ask Claude").formatted(Formatting.BOLD),
                left, top - 26, 0xFFD97757);

        MinecraftClient mc = MinecraftClient.getInstance();
        String state = ClaudeRunner.isBusy() ? "busy - a reply is still coming"
                : CommandPermissions.allowed(mc)
                    ? "commands allowed (" + CommandPermissions.authority(mc) + ")"
                    : "read-only here - advice only";
        context.drawTextWithShadow(this.textRenderer, Text.literal(state),
                left, top - 14,
                CommandPermissions.allowed(mc) ? 0xFF7A7A7A : 0xFFFF8080);

        String hint = "Enter to send   Esc to close";
        if (Usage.hasLimits()) hint = hint + "      quota  " + Usage.compact();
        context.drawTextWithShadow(this.textRenderer,
                Text.literal(hint).formatted(Formatting.DARK_GRAY),
                left, this.height / 2 + 36, 0xFF606060);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
