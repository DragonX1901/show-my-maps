package org.dx.show_my_maps.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import org.dx.show_my_maps.client.gui.ShowMyMapsConfigScreen;

/**
 * Fabric only loads this entrypoint when Mod Menu is present, so the class never
 * touches the game without it.
 */
public class ShowMyMapsModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ShowMyMapsConfigScreen::new;
    }
}
