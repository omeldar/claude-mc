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
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

/**
 * The prompt hub: a proper multi-line box for detailed prompts, toggles for what to attach,
 * and model/effort switches.
 *
 * <p>The box wraps and scrolls rather than running off the side, so Enter inserts a newline
 * and Ctrl+Enter sends - the bargain every chat app makes.
 */
public class PromptScreen extends Screen {
    private static final String[] EFFORTS = {"", "low", "medium", "high"};
    private static final int[] RADII = {0, 8, 16, 24, 32, 48, 64, 96, 128, 192, 256};
    private static final int BOX_HEIGHT = 88;

    // Remembered across opens.
    private static boolean attachShot = false;
    private static int radiusIndex = 0;
    private static boolean freshSession = false;
    private static String draft = "";

    private EditBoxWidget prompt;
    private int boxTop;

    public PromptScreen() {
        super(Text.literal("Ask Claude"));
    }

    @Override
    protected void init() {
        super.init();
        int w = Math.min(460, this.width - 40);
        int left = (this.width - w) / 2;
        int top = Math.max(46, this.height / 2 - 100);
        boxTop = top;

        prompt = new EditBoxWidget.Builder()
                .x(left)
                .y(top)
                .placeholder(Text.literal("what should I help with?").formatted(Formatting.DARK_GRAY))
                .build(this.textRenderer, w, BOX_HEIGHT, Text.literal("prompt"));
        prompt.setMaxLength(8000);
        prompt.setMaxLines(128);
        prompt.setText(draft);
        addDrawableChild(prompt);
        this.setInitialFocus(prompt);

        int y = top + BOX_HEIGHT + 8;
        int third = (w - 8) / 3;

        addDrawableChild(ButtonWidget.builder(shotLabel(), b -> {
            attachShot = !attachShot;
            b.setMessage(shotLabel());
        }).dimensions(left, y, third, 20).build());

        addDrawableChild(ButtonWidget.builder(scanLabel(), b -> {
            radiusIndex = (radiusIndex + 1) % RADII.length;
            b.setMessage(scanLabel());
        }).dimensions(left + third + 4, y, third, 20).build());

        addDrawableChild(ButtonWidget.builder(sessionLabel(), b -> {
            freshSession = !freshSession;
            b.setMessage(sessionLabel());
        }).dimensions(left + 2 * (third + 4), y, third, 20).build());

        y += 24;
        int half = (w - 4) / 2;

        addDrawableChild(ButtonWidget.builder(modelLabel(),
                b -> MinecraftClient.getInstance().setScreen(new ModelPickerScreen(this)))
                .dimensions(left, y, half, 20).build());

        addDrawableChild(ButtonWidget.builder(effortLabel(), b -> {
            Config cfg = Config.get();
            cfg.effort = next(EFFORTS, cfg.effort == null ? "" : cfg.effort);
            cfg.save();
            b.setMessage(effortLabel());
        }).dimensions(left + half + 4, y, half, 20).build());

        y += 28;
        addDrawableChild(ButtonWidget.builder(Text.literal("Ask"), b -> submit())
                .dimensions(left, y, half, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> this.close())
                .dimensions(left + half + 4, y, half, 20).build());
    }

    private static String next(String[] options, String current) {
        if (options == null || options.length == 0) return current;
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
        String text = prompt.getText().trim();
        if (text.isEmpty()) return;
        draft = "";
        boolean fresh = freshSession;
        freshSession = false;   // one-shot
        this.close();
        ClaudeMCClient.ask(text, fresh, attachShot, RADII[radiusIndex]);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        boolean ctrl = (input.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
        if (ctrl && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            submit();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void removed() {
        if (prompt != null) draft = prompt.getText();
        super.removed();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int w = Math.min(460, this.width - 40);
        int left = (this.width - w) / 2;

        context.drawTextWithShadow(this.textRenderer,
                Text.literal("Ask Claude").formatted(Formatting.BOLD),
                left, boxTop - 26, 0xFFD97757);

        MinecraftClient mc = MinecraftClient.getInstance();
        String state = ClaudeRunner.isBusy() ? "busy - a reply is still coming"
                : CommandPermissions.allowed(mc)
                    ? "commands allowed (" + CommandPermissions.authority(mc) + ")"
                    : "read-only here - advice only";
        context.drawTextWithShadow(this.textRenderer, Text.literal(state),
                left, boxTop - 14,
                CommandPermissions.allowed(mc) ? 0xFF7A7A7A : 0xFFFF8080);

        String hint = "Ctrl+Enter to send   Enter for a new line   Esc to close";
        if (Usage.hasLimits()) hint = hint + "      quota  " + Usage.compact();
        context.drawTextWithShadow(this.textRenderer,
                Text.literal(hint).formatted(Formatting.DARK_GRAY),
                left, boxTop + BOX_HEIGHT + 84, 0xFF606060);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
