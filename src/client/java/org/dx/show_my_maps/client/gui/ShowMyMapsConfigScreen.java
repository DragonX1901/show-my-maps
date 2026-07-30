package org.dx.show_my_maps.client.gui;

//? if >=26 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.dx.show_my_maps.client.ShowMyMapsConfig;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Plain vanilla widgets, so the mod carries no config library. Mod Menu opens this,
 * and everything saves when you leave.
 */
public class ShowMyMapsConfigScreen extends Screen {
    private static final int ROW_HEIGHT = 24;
    private static final int WIDGET_WIDTH = 310;

    private final @Nullable Screen parent;

    public ShowMyMapsConfigScreen(@Nullable Screen parent) {
        super(Component.translatable("screen.show_my_maps.config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ShowMyMapsConfig config = ShowMyMapsConfig.get();
        int columnWidth = (WIDGET_WIDTH - 4) / 2;
        int leftColumn = this.width / 2 - WIDGET_WIDTH / 2;
        int rightColumn = leftColumn + columnWidth + 4;
        int top = 36;

        addRenderableWidget(CycleButton.onOffBuilder(config.slotPreview)
            .create(leftColumn, top, columnWidth, 20, Component.translatable("option.show_my_maps.slot_preview"),
                (button, value) -> config.slotPreview = value));
        addRenderableWidget(new IntSlider(rightColumn, top, columnWidth, "option.show_my_maps.slot_preview_size", 8, 16,
            () -> config.slotPreviewSize, value -> config.slotPreviewSize = value));

        addRenderableWidget(CycleButton.onOffBuilder(config.tooltipEnabled)
            .create(leftColumn, top + ROW_HEIGHT, columnWidth, 20, Component.translatable("option.show_my_maps.tooltip"),
                (button, value) -> config.tooltipEnabled = value));
        addRenderableWidget(new IntSlider(rightColumn, top + ROW_HEIGHT, columnWidth, "option.show_my_maps.tooltip_size",
            ShowMyMapsConfig.MIN_SIZE, ShowMyMapsConfig.MAX_SIZE, () -> config.tooltipSize, value -> config.tooltipSize = value));

        addRenderableWidget(CycleButton.onOffBuilder(config.containerTooltip)
            .create(leftColumn, top + ROW_HEIGHT * 2, columnWidth, 20, Component.translatable("option.show_my_maps.container_tooltip"),
                (button, value) -> config.containerTooltip = value));
        addRenderableWidget(CycleButton.onOffBuilder(config.cacheMapData)
            .create(rightColumn, top + ROW_HEIGHT * 2, columnWidth, 20, Component.translatable("option.show_my_maps.cache"),
                (button, value) -> config.cacheMapData = value));

        addRenderableWidget(CycleButton.onOffBuilder(config.serverNotice)
            .create(leftColumn, top + ROW_HEIGHT * 3, columnWidth, 20, Component.translatable("option.show_my_maps.server_notice"),
                (button, value) -> config.serverNotice = value));

        addRenderableWidget(CycleButton.onOffBuilder(config.mapShare)
            .create(rightColumn, top + ROW_HEIGHT * 3, columnWidth, 20, Component.translatable("option.show_my_maps.map_share"),
                (button, value) -> config.mapShare = value));

        EditBox shareUrl = new EditBox(this.font, leftColumn, top + ROW_HEIGHT * 4, WIDGET_WIDTH, 20,
            Component.translatable("option.show_my_maps.map_share_url"));
        shareUrl.setMaxLength(256);
        shareUrl.setHint(Component.translatable("option.show_my_maps.map_share_url"));
        shareUrl.setValue(config.mapShareUrl);
        shareUrl.setResponder(value -> config.mapShareUrl = value);
        addRenderableWidget(shareUrl);

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
            .bounds(leftColumn, Math.min(top + ROW_HEIGHT * 5 + 8, this.height - 28), WIDGET_WIDTH, 20)
            .build());
    }

    //? if >=26 {
    /*@Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, this.title, this.width / 2, 16, -1);
    }
    *///?} else {
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 16, -1);
    }
    //?}

    @Override
    public void onClose() {
        ShowMyMapsConfig.get().save();

        if (this.minecraft != null) {
            //? if >=26.2 {
            /*this.minecraft.setScreenAndShow(this.parent);
            *///?} else {
            this.minecraft.setScreen(this.parent);
            //?}
        }
    }

    private static class IntSlider extends AbstractSliderButton {
        private final String key;
        private final int min;
        private final int max;
        private final IntConsumer setter;

        IntSlider(int x, int y, int width, String key, int min, int max, IntSupplier getter, IntConsumer setter) {
            super(x, y, width, 20, Component.empty(), (getter.getAsInt() - min) / (double) (max - min));
            this.key = key;
            this.min = min;
            this.max = max;
            this.setter = setter;
            updateMessage();
        }

        private int currentValue() {
            return Mth.clamp((int) Math.round(this.min + this.value * (this.max - this.min)), this.min, this.max);
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable(this.key).append(": ").append(String.valueOf(currentValue())));
        }

        @Override
        protected void applyValue() {
            this.setter.accept(currentValue());
        }
    }
}
