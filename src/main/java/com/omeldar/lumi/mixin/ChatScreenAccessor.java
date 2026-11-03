package com.omeldar.lumi.mixin;

import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reaches the chat input box so its 256-character cap can be lifted while the line is one of
 * ours. Nothing is injected into vanilla behaviour - this only exposes the field.
 */
@Mixin(ChatScreen.class)
public interface ChatScreenAccessor {
    @Accessor("chatField")
    TextFieldWidget lumipet$chatField();
}
