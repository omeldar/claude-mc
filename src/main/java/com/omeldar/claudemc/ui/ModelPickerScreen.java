package com.omeldar.claudemc.ui;

import com.omeldar.claudemc.ClaudeRunner;
import com.omeldar.claudemc.Config;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Picks a model from the configured list. Cycling through fifteen entries one click at a time
 * was the wrong shape once exact ids were added, so this shows them all at once.
 */
public class ModelPickerScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget custom;

    public ModelPickerScreen(Screen parent) {
        super(Text.literal("Model"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        Config cfg = Config.get();
        String[] models = cfg.models;

        int w = Math.min(460, this.width - 40);
        int left = (this.width - w) / 2;
        int half = (w - 6) / 2;
        int rows = (models.length + 1) / 2;
        int top = Math.max(44, this.height / 2 - (rows * 22 + 70) / 2);

        for (int i = 0; i < models.length; i++) {
            final String id = models[i];
            int col = i % 2;
            int row = i / 2;
            boolean current = id.equalsIgnoreCase(cfg.model);

            ButtonWidget b = ButtonWidget.builder(
                    Text.literal(current ? "✓ " + id : id)
                            .formatted(current ? Formatting.GREEN : Formatting.WHITE),
                    btn -> choose(id))
                .dimensions(left + col * (half + 6), top + row * 22, half, 20)
                .build();
            addDrawableChild(b);
        }

        int y = top + rows * 22 + 10;
        custom = new TextFieldWidget(this.textRenderer, left, y, w, 18, Text.literal("model id"));
        custom.setMaxLength(128);
        custom.setPlaceholder(Text.literal("any other exact id, then Enter")
                .formatted(Formatting.DARK_GRAY));
        addDrawableChild(custom);

        y += 24;
        addDrawableChild(ButtonWidget.builder(Text.literal("Use typed id"), b -> {
            String typed = custom.getText().trim();
            if (!typed.isEmpty()) choose(typed);
        }).dimensions(left, y, half, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> this.close())
                .dimensions(left + half + 6, y, half, 20).build());
    }

    private void choose(String id) {
        Config cfg = Config.get();
        cfg.model = id;
        cfg.rememberModel(id);
        cfg.save();
        this.close();
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int w = Math.min(460, this.width - 40);
        int left = (this.width - w) / 2;

        context.drawTextWithShadow(this.textRenderer,
                Text.literal("Model").formatted(Formatting.BOLD), left, 18, 0xFFE83A2A);

        String note = "An alias means the newest of its family; an exact id pins a version.";
        if (!ClaudeRunner.resolvedModel().isBlank()) {
            note = "Last run as " + ClaudeRunner.resolvedModel() + ".  " + note;
        }
        context.drawTextWithShadow(this.textRenderer,
                Text.literal(this.textRenderer.trimToWidth(note, w)).formatted(Formatting.GRAY),
                left, 30, 0xFF7A7A7A);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
