package org.dx.show_my_maps.client.tooltip;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
//? if >=26 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
//? if >=1.21.6 {
import net.minecraft.client.renderer.RenderPipelines;
//?} elif >=1.21.2 {
/*import net.minecraft.client.renderer.RenderType;
*///?}
//? if >=1.21.9 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}
import net.minecraft.world.item.ItemStack;

/** A slot grid of a container item's contents. Maps go through the item renderer, so they draw as art. */
public class ContainerPreviewTooltip implements ClientTooltipComponent {
    //? if >=1.21.9 {
    private static final Identifier SLOT_SPRITE = Identifier.withDefaultNamespace("container/bundle/slot_background");
    //?} elif >=1.21.2 {
    /*private static final ResourceLocation SLOT_SPRITE = ResourceLocation.withDefaultNamespace("container/bundle/slot_background");
    *///?} elif >=1.21 {
    /*// renamed to slot_background in 1.21.2
    private static final ResourceLocation SLOT_SPRITE = ResourceLocation.withDefaultNamespace("container/bundle/slot");
    *///?} elif >=1.20.2 {
    /*private static final ResourceLocation SLOT_SPRITE = new ResourceLocation("minecraft", "container/bundle/slot");
    *///?} else {
    /*// no GUI sprite atlas yet: a region of the bundle sheet
    private static final ResourceLocation SLOT_SPRITE = new ResourceLocation("minecraft", "textures/gui/container/bundle.png");
    *///?}
    private static final int SLOT = 18;
    private static final int COLUMNS = 9;

    private final List<ItemStack> items;

    public ContainerPreviewTooltip(List<ItemStack> items) {
        this.items = items;
    }

    @Override
    public int getWidth(Font font) {
        return Math.min(this.items.size(), COLUMNS) * SLOT;
    }

    //? if >=1.21.2 {
    @Override
    public int getHeight(Font font) {
        return rows() * SLOT;
    }

    @Override
    public boolean showTooltipWithItemInHand() {
        return true;
    }

    //?}

    //? if >=26 {
    /*@Override
    public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {
        draw(x, y, graphics);
    }
    *///?} elif >=1.21.2 {
    @Override
    public void renderImage(Font font, int x, int y, int width, int height, GuiGraphics graphics) {
        draw(x, y, graphics);
    }
    //?} else {
    /*@Override
    public int getHeight() {
        return rows() * SLOT;
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        draw(x, y, graphics);
    }
    *///?}

    //? if >=26 {
    /*private void draw(int x, int y, GuiGraphicsExtractor graphics) {
        Minecraft minecraft = Minecraft.getInstance();

        for (int index = 0; index < this.items.size(); index++) {
            int slotX = x + index % COLUMNS * SLOT;
            int slotY = y + index / COLUMNS * SLOT;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE, slotX, slotY, SLOT, SLOT);

            ItemStack stack = this.items.get(index);

            if (!stack.isEmpty()) {
                graphics.item(stack, slotX + 1, slotY + 1, index);
                graphics.itemDecorations(minecraft.font, stack, slotX + 1, slotY + 1);
            }
        }
    }
    *///?} else {
    private void draw(int x, int y, GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();

        for (int index = 0; index < this.items.size(); index++) {
            int slotX = x + index % COLUMNS * SLOT;
            int slotY = y + index / COLUMNS * SLOT;
            //? if >=1.21.6 {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE, slotX, slotY, SLOT, SLOT);
            //?} elif >=1.21.2 {
            /*graphics.blitSprite(RenderType::guiTextured, SLOT_SPRITE, slotX, slotY, SLOT, SLOT);
            *///?} elif >=1.20.2 {
            /*graphics.blitSprite(SLOT_SPRITE, slotX, slotY, SLOT, SLOT);
            *///?} else {
            /*graphics.blit(SLOT_SPRITE, slotX, slotY, 0, 0, SLOT, SLOT, 128, 128);
            *///?}

            ItemStack stack = this.items.get(index);

            if (!stack.isEmpty()) {
                graphics.renderItem(stack, slotX + 1, slotY + 1, index);
                graphics.renderItemDecorations(minecraft.font, stack, slotX + 1, slotY + 1);
            }
        }
    }
    //?}

    private int rows() {
        return Math.max(1, (this.items.size() + COLUMNS - 1) / COLUMNS);
    }
}
