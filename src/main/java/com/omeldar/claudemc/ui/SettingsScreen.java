package com.omeldar.claudemc.ui;

import com.omeldar.claudemc.Config;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.KeybindsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * ClaudeMC's settings, reachable from Mod Menu's Mods tab (the cog next to the entry) or
 * from {@code /claude settings}. Plain vanilla widgets - no config-library dependency, so
 * it works whatever else is installed.
 */
public class SettingsScreen extends Screen {
    private static final String[] MODELS = {"opus", "sonnet", "haiku", "fable"};
    private static final String[] EFFORTS = {"", "low", "medium", "high"};
    private static final int[] DELAYS = {2, 3, 5, 8, 12};
    private static final int[] MAX_COMMANDS = {12, 24, 48};
    private static final int[] SCAN_RADII = {8, 16, 24, 32, 48, 64};

    private final Screen parent;
    private TextFieldWidget portField;
    private TextFieldWidget pathField;
    private int portLabelY;
    private int pathLabelY;

    public SettingsScreen(Screen parent) {
        super(Text.literal("ClaudeMC settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        Config cfg = Config.get();

        int w = Math.min(420, this.width - 40);
        int left = (this.width - w) / 2;
        int half = (w - 6) / 2;
        int y = 46;

        addDrawableChild(cycler(left, y, half,
                () -> "Model: " + cfg.model,
                () -> { cfg.model = nextString(MODELS, cfg.model); }));
        addDrawableChild(cycler(left + half + 6, y, half,
                () -> "Effort: " + (cfg.effort == null || cfg.effort.isBlank() ? "default" : cfg.effort),
                () -> { cfg.effort = nextString(EFFORTS, cfg.effort == null ? "" : cfg.effort); }));

        y += 24;
        addDrawableChild(cycler(left, y, half,
                () -> "Live progress: " + (cfg.streaming ? "on" : "off"),
                () -> { cfg.streaming = !cfg.streaming; }));
        addDrawableChild(cycler(left + half + 6, y, half,
                () -> "Progress in chat: " + (cfg.showProgressInChat ? "on" : "off"),
                () -> { cfg.showProgressInChat = !cfg.showProgressInChat; }));

        y += 24;
        addDrawableChild(cycler(left, y, half,
                () -> "Gap between commands: " + cfg.commandDelayTicks + " ticks",
                () -> { cfg.commandDelayTicks = nextInt(DELAYS, cfg.commandDelayTicks); }));
        addDrawableChild(cycler(left + half + 6, y, half,
                () -> "Max commands: " + cfg.maxCommands,
                () -> { cfg.maxCommands = nextInt(MAX_COMMANDS, cfg.maxCommands); }));

        y += 24;
        addDrawableChild(cycler(left, y, half,
                () -> "Default scan radius: " + cfg.defaultScanRadius,
                () -> { cfg.defaultScanRadius = nextInt(SCAN_RADII, cfg.defaultScanRadius); }));
        addDrawableChild(cycler(left + half + 6, y, half,
                () -> "World tools: " + (cfg.bridgeEnabled ? "on" : "off"),
                () -> { cfg.bridgeEnabled = !cfg.bridgeEnabled; }));

        y += 30;
        portLabelY = y + 5;
        portField = new TextFieldWidget(this.textRenderer, left + 150, y, w - 150, 18,
                Text.literal("port"));
        portField.setMaxLength(5);
        portField.setText(String.valueOf(cfg.bridgePort));
        addDrawableChild(portField);

        y += 24;
        pathLabelY = y + 5;
        pathField = new TextFieldWidget(this.textRenderer, left + 150, y, w - 150, 18,
                Text.literal("claude path"));
        pathField.setMaxLength(512);
        pathField.setText(cfg.claudePath == null ? "" : cfg.claudePath);
        pathField.setPlaceholder(Text.literal("auto-detect").formatted(Formatting.DARK_GRAY));
        addDrawableChild(pathField);

        int footer = this.height - 32;
        addDrawableChild(ButtonWidget.builder(Text.literal("Key binds..."), b -> {
            save();
            MinecraftClient mc = MinecraftClient.getInstance();
            mc.setScreen(new KeybindsScreen(this, mc.options));
        }).dimensions(left, footer, half, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> this.close())
                .dimensions(left + half + 6, footer, half, 20).build());
    }

    /** A button whose label is recomputed from the config every time it is pressed. */
    private ButtonWidget cycler(int x, int y, int w, Label label, Runnable advance) {
        return ButtonWidget.builder(Text.literal(label.get()), b -> {
            advance.run();
            b.setMessage(Text.literal(label.get()));
        }).dimensions(x, y, w, 20).build();
    }

    private interface Label { String get(); }

    private static String nextString(String[] options, String current) {
        for (int i = 0; i < options.length; i++) {
            if (options[i].equalsIgnoreCase(current)) return options[(i + 1) % options.length];
        }
        return options[0];
    }

    private static int nextInt(int[] options, int current) {
        for (int i = 0; i < options.length; i++) {
            if (options[i] == current) return options[(i + 1) % options.length];
        }
        return options[0];
    }

    private void save() {
        Config cfg = Config.get();
        try {
            int port = Integer.parseInt(portField.getText().trim());
            if (port >= 1024 && port <= 65535) cfg.bridgePort = port;
        } catch (NumberFormatException ignored) {
            // leave the old port alone rather than writing something unusable
        }
        cfg.claudePath = pathField.getText().trim();
        cfg.save();
    }

    @Override
    public void close() {
        save();
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int w = Math.min(420, this.width - 40);
        int left = (this.width - w) / 2;

        context.drawTextWithShadow(this.textRenderer,
                Text.literal("ClaudeMC").formatted(Formatting.BOLD), left, 18, 0xFFD97757);
        context.drawTextWithShadow(this.textRenderer,
                Text.literal("Changes apply to the next message.").formatted(Formatting.GRAY),
                left, 30, 0xFF7A7A7A);

        context.drawTextWithShadow(this.textRenderer, Text.literal("World tools port"),
                left, portLabelY, 0xFFBBBBBB);
        context.drawTextWithShadow(this.textRenderer, Text.literal("claude executable"),
                left, pathLabelY, 0xFFBBBBBB);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
