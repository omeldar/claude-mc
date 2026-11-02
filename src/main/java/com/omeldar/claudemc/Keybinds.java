package com.omeldar.claudemc;

import com.omeldar.claudemc.ui.ApprovalScreen;
import com.omeldar.claudemc.ui.PromptScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Keys, all rebindable from Options - Controls - ClaudeMC. Defaults are chosen to avoid
 * vanilla bindings; anyone who disagrees can change them in the usual place.
 */
public final class Keybinds {
    private static final KeyBinding.Category CATEGORY =
            KeyBinding.Category.create(Identifier.of("claudemc", "main"));

    public static KeyBinding openLast;
    public static KeyBinding openPrompt;
    public static KeyBinding undo;

    private Keybinds() {}

    public static void register() {
        openPrompt = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.claudemc.prompt", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY));
        openLast = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.claudemc.last", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY));
        undo = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.claudemc.undo", InputUtil.Type.KEYSYM, InputUtil.UNKNOWN_KEY.getCode(), CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(Keybinds::tick);
    }

    private static void tick(MinecraftClient mc) {
        if (mc.player == null) return;

        while (openPrompt.wasPressed()) {
            mc.setScreen(new PromptScreen());
        }
        while (openLast.wasPressed()) {
            Proposal last = ClaudeMCClient.lastProposal();
            if (last == null) {
                Chat.overlay("no proposal yet");
            } else {
                mc.setScreen(new ApprovalScreen(last));
            }
        }
        while (undo.wasPressed()) {
            CommandQueue.enqueue("//undo");
        }
    }
}
