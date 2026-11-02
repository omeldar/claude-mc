package com.omeldar.claudemc;

import com.omeldar.claudemc.ui.SettingsScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Puts a cog next to ClaudeMC in Mod Menu's Mods tab, so the settings live where every other
 * mod's settings live. Mod Menu is optional - this entrypoint is only loaded when it is there.
 */
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return SettingsScreen::new;
    }
}
