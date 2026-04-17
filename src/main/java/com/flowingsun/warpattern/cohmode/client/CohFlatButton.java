package com.flowingsun.warpattern.cohmode.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/**
 * Flat-highlight button style aligned with codPattern UI.
 */
public class CohFlatButton extends Button {

    public CohFlatButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    public static CohFlatButton of(int x, int y, int width, int height, Component message, OnPress onPress) {
        return new CohFlatButton(x, y, width, height, message, onPress);
    }

    public static CohFlatButtonBuilder flatBuilder(Component message, OnPress onPress) {
        return new CohFlatButtonBuilder(message, onPress);
    }

    @Override
    protected void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int left = getX();
        int top = getY();
        int right = left + width;
        int bottom = top + height;
        boolean hovered = isHoveredOrFocused();

        int bgTop = hovered ? CohUiTheme.HOVER_BG_TOP : CohUiTheme.CARD_BG_TOP;
        int bgBottom = hovered ? CohUiTheme.HOVER_BG_BOTTOM : CohUiTheme.CARD_BG_BOTTOM;
        graphics.fillGradient(left, top, right, bottom, bgTop, bgBottom);

        // Thin border and highlight bars reproduce codPattern flat-button feedback.
        graphics.fill(left, top, right, top + 1, hovered ? CohUiTheme.HOVER_BORDER : CohUiTheme.BORDER_SUBTLE);
        graphics.fill(left, bottom - 1, right, bottom, hovered ? CohUiTheme.HOVER_BORDER_SEMI : CohUiTheme.BORDER_SUBTLE);
        graphics.fill(left, top, left + 1, bottom, CohUiTheme.BORDER_SUBTLE);
        graphics.fill(right - 1, top, right, bottom, CohUiTheme.BORDER_SUBTLE);
        graphics.fillGradient(left + 1, top + 1, right - 1, top + 8, 0x1CFFFFFF, 0x02000000);

        int textColor = active ? CohUiTheme.TEXT_PRIMARY : CohUiTheme.TEXT_DIM;
        if (hovered && active) {
            textColor = 0xFFFFFFFF;
        }
        graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(), left + width / 2, top + (height - 8) / 2, textColor);
    }

    public static final class CohFlatButtonBuilder {
        private final Component message;
        private final OnPress onPress;
        private int x;
        private int y;
        private int width = 150;
        private int height = 20;

        private CohFlatButtonBuilder(Component message, OnPress onPress) {
            this.message = message;
            this.onPress = onPress;
        }

        public CohFlatButtonBuilder bounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            return this;
        }

        public CohFlatButton build() {
            return new CohFlatButton(x, y, width, height, message, onPress);
        }
    }
}
