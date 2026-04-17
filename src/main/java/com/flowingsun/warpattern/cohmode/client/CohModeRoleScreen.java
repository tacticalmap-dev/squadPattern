package com.flowingsun.warpattern.cohmode.client;

import com.flowingsun.warpattern.cohmode.CohModeModels;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Standalone role selection UI opened via command.
 */
public class CohModeRoleScreen extends Screen {
    private static final long DOUBLE_CLICK_MILLIS = 350L;

    private int panelLeft;
    private int panelTop;
    private int panelRight;
    private int panelBottom;
    private CohModeModels.Role lastClickedRole;
    private long lastRoleClickAt;

    public CohModeRoleScreen() {
        super(Component.literal("Coh 模式兵种界面"));
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(420, width - 24);
        int panelHeight = Math.min(190, height - 28);
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;
        panelRight = panelLeft + panelWidth;
        panelBottom = panelTop + panelHeight;

        int left = panelLeft + 12;
        int top = panelTop + 10;

        addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("刷新"), b ->
                CohModeClientState.sendAction("refresh", CohModeClientState.json()))
                .bounds(left, top, 80, 20).build());
        addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("关闭"), b -> onClose())
                .bounds(panelRight - 92, top, 80, 20).build());

        int y = top + 34;
        int currentX = left;
        int currentY = y;
        int btnWidth = 110;
        int gapX = 6;
        int gapY = 26;
        int maxWidth = panelWidth - 24;
        
        Object[][] roles = {
            {"指挥官", CohModeModels.Role.COMMANDER},
            {"步枪手", CohModeModels.Role.RIFLEMAN},
            {"突击手", CohModeModels.Role.ASSAULT},
            {"支援兵", CohModeModels.Role.SUPPORT},
            {"狙击手", CohModeModels.Role.SNIPER}
        };

        for (Object[] r : roles) {
            if (currentX + btnWidth > left + maxWidth && currentX > left) {
                currentX = left;
                currentY += gapY;
            }
            addRoleButton(currentX, currentY, (String) r[0], (CohModeModels.Role) r[1]);
            currentX += btnWidth + gapX;
        }
    }

    private void addRoleButton(int x, int y, String label, CohModeModels.Role role) {
        addRenderableWidget(CohFlatButton.flatBuilder(Component.literal(label), b -> {
            long now = System.currentTimeMillis();
            boolean doubleClick = lastClickedRole == role && (now - lastRoleClickAt) <= DOUBLE_CLICK_MILLIS;
            lastClickedRole = role;
            lastRoleClickAt = now;

            JsonObject payload = CohModeClientState.json();
            payload.addProperty("role", role.name());
            CohModeClientState.sendAction("select_role", payload);

            if (doubleClick && minecraft != null) {
                minecraft.setScreen(new CohRoleBackpackScreen(this, role));
            }
        }).bounds(x, y, 110, 20).build());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        graphics.fillGradient(0, 0, width, height, CohUiTheme.BG_TOP, CohUiTheme.BG_BOTTOM);
        
        // Tactical grid background
        int gridColor = 0x10FFFFFF;
        for (int x = 0; x < width; x += 20) {
            graphics.fill(x, 0, x + 1, height, gridColor);
        }
        for (int y = 0; y < height; y += 20) {
            graphics.fill(0, y, width, y + 1, gridColor);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        drawPanel(graphics, panelLeft, panelTop, panelRight, panelBottom);
        super.render(graphics, mouseX, mouseY, partialTick);

        CohModeModels.LobbyStateView s = CohModeClientState.state();
        graphics.fill(panelLeft + 12, panelTop + 30, panelRight - 12, panelTop + 31, CohUiTheme.DIVIDER);
        graphics.fill(panelLeft + 8, panelTop + 7, panelLeft + 11, panelTop + 21, CohUiTheme.HOVER_BORDER);
        graphics.drawCenteredString(font, "战术兵种选择", width / 2, panelTop + 10, CohUiTheme.TEXT_PRIMARY);
        graphics.drawString(font, "> 双击兵种可打开装备自定义界面", panelLeft + 12, panelTop + 90, CohUiTheme.TEXT_HIGHLIGHT, false);
        graphics.drawString(font, "当前兵种：" + (s.selectedRole == null ? "未选择" : s.selectedRole.label), panelLeft + 12, panelTop + 106, CohUiTheme.TEXT_PRIMARY, false);
        graphics.drawString(font, "当前阵营：" + (s.selectedCamp == null ? "未选择" : s.selectedCamp.label), panelLeft + 12, panelTop + 118, CohUiTheme.TEXT_SECONDARY, false);
        if (s.statusText != null && !s.statusText.isEmpty()) {
            graphics.drawCenteredString(font, "系统消息：" + s.statusText, width / 2, panelBottom - 12, CohUiTheme.TEXT_HIGHLIGHT);
        }
    }

    private void drawPanel(GuiGraphics graphics, int left, int top, int right, int bottom) {
        graphics.fillGradient(left, top, right, bottom, CohUiTheme.CARD_BG_TOP, CohUiTheme.CARD_BG_BOTTOM);
        
        int borderColor = CohUiTheme.HOVER_BORDER_SEMI;
        
        // Draw tactical border with corners
        graphics.fill(left + 2, top, right - 2, top + 1, borderColor);
        graphics.fill(left + 2, bottom - 1, right - 2, bottom, CohUiTheme.BORDER_SUBTLE);
        graphics.fill(left, top + 2, left + 1, bottom - 2, CohUiTheme.BORDER_SUBTLE);
        graphics.fill(right - 1, top + 2, right, bottom - 2, CohUiTheme.BORDER_SUBTLE);
        
        // Corner accents
        int cornerColor = CohUiTheme.HOVER_BORDER;
        graphics.fill(left, top, left + 4, top + 2, cornerColor);
        graphics.fill(left, top, left + 2, top + 4, cornerColor);
        
        graphics.fill(right - 4, top, right, top + 2, cornerColor);
        graphics.fill(right - 2, top, right, top + 4, cornerColor);
        
        graphics.fill(left, bottom - 2, left + 4, bottom, cornerColor);
        graphics.fill(left, bottom - 4, left + 2, bottom, cornerColor);
        
        graphics.fill(right - 4, bottom - 2, right, bottom, cornerColor);
        graphics.fill(right - 2, bottom - 4, right, bottom, cornerColor);

        // Highlight gradient
        graphics.fillGradient(left + 1, top + 1, right - 1, top + 16, 0x1CFFFFFF, 0x02000000);
        
        // Scanline effect
        for (int i = top + 2; i < bottom - 2; i += 4) {
            graphics.fill(left + 1, i, right - 1, i + 1, 0x10000000);
        }
    }
}
