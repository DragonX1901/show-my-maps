package org.dx.show_my_maps.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import org.dx.show_my_maps.client.gui.ShowMyMapsConfigScreen;

/** Only loaded when Mod Menu is installed. */
public class ShowMyMapsModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ShowMyMapsConfigScreen::new;
    }
}
