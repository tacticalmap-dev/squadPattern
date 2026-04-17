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
    private Integer customColor = null;

    public CohFlatButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    public void setCustomColor(Integer color) {
        this.customColor = color;
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

        // Tactical borders (Corners and thin lines)
        int borderColor = customColor != null ? customColor : (hovered ? CohUiTheme.HOVER_BORDER : CohUiTheme.BORDER_SUBTLE);
        int borderSemi = hovered ? CohUiTheme.HOVER_BORDER_SEMI : CohUiTheme.BORDER_SUBTLE;
        
        // Top and bottom lines
        graphics.fill(left + 2, top, right - 2, top + 1, borderColor);
        graphics.fill(left + 2, bottom - 1, right - 2, bottom, borderSemi);
        
        // Side lines
        graphics.fill(left, top + 2, left + 1, bottom - 2, CohUiTheme.BORDER_SUBTLE);
        graphics.fill(right - 1, top + 2, right, bottom - 2, CohUiTheme.BORDER_SUBTLE);

        // Tactical corner brackets
        graphics.fill(left, top, left + 3, top + 1, borderColor);
        graphics.fill(left, top, left + 1, top + 3, borderColor);
        
        graphics.fill(right - 3, top, right, top + 1, borderColor);
        graphics.fill(right - 1, top, right, top + 3, borderColor);
        
        graphics.fill(left, bottom - 1, left + 3, bottom, borderSemi);
        graphics.fill(left, bottom - 3, left + 1, bottom, borderSemi);
        
        graphics.fill(right - 3, bottom - 1, right, bottom, borderSemi);
        graphics.fill(right - 1, bottom - 3, right, bottom, borderSemi);

        // Highlight gradient
        graphics.fillGradient(left + 1, top + 1, right - 1, top + 8, 0x1CFFFFFF, 0x02000000);

        int textColor = active ? CohUiTheme.TEXT_PRIMARY : CohUiTheme.TEXT_DIM;
        if (hovered && active) {
            textColor = customColor != null ? customColor : CohUiTheme.TEXT_HIGHLIGHT;
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
        private Integer customColor = null;

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

        public CohFlatButtonBuilder color(int color) {
            this.customColor = color;
            return this;
        }

        public CohFlatButton build() {
            CohFlatButton btn = new CohFlatButton(x, y, width, height, message, onPress);
            if (customColor != null) {
                btn.setCustomColor(customColor);
            }
            return btn;
        }
    }
}
