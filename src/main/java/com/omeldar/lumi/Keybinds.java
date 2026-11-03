package com.omeldar.lumi;

import com.omeldar.lumi.ui.ApprovalScreen;
import com.omeldar.lumi.ui.PromptScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.option.KeybindsScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Keys, all rebindable from Options - Controls - Key Binds under the Lumi category, or via
 * the "Key binds..." button in the mod's own settings screen.
 *
 * <p>Defaults dodge both vanilla and the popular build mods: C is Zoomify's zoom, and V, M and
 * I belong to Litematica (print, main menu, info overlay). H and U are clear of all of them.
 * That is only true of one particular setup though, so on joining a world the bindings are
 * checked against everything else installed and any clash is reported in chat.
 */
public final class Keybinds {
    private static final KeyBinding.Category CATEGORY =
            KeyBinding.Category.create(Identifier.of("lumipet", "main"));

    public static KeyBinding openLast;
    public static KeyBinding openPrompt;
    public static KeyBinding undo;

    private static boolean wasInWorld = false;
    private static int sinceJoin = -1;

    private Keybinds() {}

    public static void register() {
        openPrompt = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.lumipet.prompt", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY));
        openLast = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.lumipet.last", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_U, CATEGORY));
        undo = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.lumipet.undo", InputUtil.Type.KEYSYM, InputUtil.UNKNOWN_KEY.getCode(), CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(Keybinds::tick);
    }

    public static List<KeyBinding> ours() {
        List<KeyBinding> list = new ArrayList<>();
        if (openPrompt != null) list.add(openPrompt);
        if (openLast != null) list.add(openLast);
        if (undo != null) list.add(undo);
        return list;
    }

    private static void tick(MinecraftClient mc) {
        boolean inWorld = mc.player != null;
        if (inWorld && !wasInWorld) sinceJoin = 0;
        if (!inWorld) sinceJoin = -1;
        wasInWorld = inWorld;

        // Wait a couple of seconds so the warning is not buried in join spam.
        if (sinceJoin >= 0 && sinceJoin < 60) {
            sinceJoin++;
            if (sinceJoin == 60) reportConflicts(mc, false);
        }

        if (mc.player == null) return;

        while (openPrompt.wasPressed()) {
            mc.setScreen(new PromptScreen());
        }
        while (openLast.wasPressed()) {
            Proposal last = LumiClient.lastProposal();
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

    /** Every other binding sharing a key with one of ours, ignoring the F3 debug family. */
    public static List<String> conflictsFor(MinecraftClient mc, KeyBinding mine) {
        List<String> clashes = new ArrayList<>();
        if (mine == null || mine.isUnbound() || mc.options == null) return clashes;
        for (KeyBinding other : mc.options.allKeys) {
            if (other == mine || other.isUnbound()) continue;
            // F3 combos only fire with the modifier held, so they are not real clashes.
            if (other.getCategory() == KeyBinding.Category.DEBUG) continue;
            if (ours().contains(other)) continue;
            if (!other.getBoundKeyTranslationKey().equals(mine.getBoundKeyTranslationKey())) continue;
            clashes.add(KeyBinding.getLocalizedName(other.getId()).get().getString());
        }
        return clashes;
    }

    /**
     * Prints any clash in chat. When {@code always} is false this stays quiet if nothing
     * clashes, which is what the join check wants.
     */
    public static void reportConflicts(MinecraftClient mc, boolean always) {
        if (mc.player == null) return;
        boolean any = false;
        for (KeyBinding mine : ours()) {
            List<String> clashes = conflictsFor(mc, mine);
            if (clashes.isEmpty()) continue;
            any = true;
            String key = mine.getBoundKeyLocalizedText().getString();
            String name = KeyBinding.getLocalizedName(mine.getId()).get().getString();
            Chat.info(Text.literal(key).formatted(Formatting.YELLOW, Formatting.BOLD)
                    .append(Text.literal(" is bound to both ").formatted(Formatting.WHITE))
                    .append(Text.literal(name).formatted(Formatting.GOLD))
                    .append(Text.literal(" and ").formatted(Formatting.WHITE))
                    .append(Text.literal(String.join(", ", clashes)).formatted(Formatting.GOLD)));
        }

        if (any) {
            Chat.info(rebindLink("Both will fire on the same press. "));
        } else if (always) {
            Chat.dim("no key conflicts");
            for (KeyBinding mine : ours()) {
                String key = mine.isUnbound() ? "unbound" : mine.getBoundKeyLocalizedText().getString();
                Chat.dim("  " + KeyBinding.getLocalizedName(mine.getId()).get().getString()
                        + "  -  " + key);
            }
        }
    }

    private static MutableText rebindLink(String lead) {
        return Text.literal(lead).formatted(Formatting.GRAY)
                .append(Text.literal("[Rebind]").styled(s -> s
                        .withColor(Formatting.AQUA)
                        .withBold(true)
                        .withClickEvent(new ClickEvent.RunCommand("/lumi keys"))));
    }

    /** Opens vanilla's rebinding screen. */
    public static void openKeybindScreen(MinecraftClient mc) {
        mc.setScreen(new KeybindsScreen(mc.currentScreen, mc.options));
    }
}
