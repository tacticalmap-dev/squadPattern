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
        super(Component.literal("Cohmode Role UI"));
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

        addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("Refresh"), b ->
                CohModeClientState.sendAction("refresh", CohModeClientState.json()))
                .bounds(left, top, 80, 20).build());
        addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("Close"), b -> onClose())
                .bounds(panelRight - 92, top, 80, 20).build());

        int y = top + 34;
        addRoleButton(left, y, "Commander", CohModeModels.Role.COMMANDER);
        addRoleButton(left + 116, y, "Rifleman", CohModeModels.Role.RIFLEMAN);
        addRoleButton(left + 232, y, "Assault", CohModeModels.Role.ASSAULT);
        addRoleButton(left, y + 26, "Support", CohModeModels.Role.SUPPORT);
        addRoleButton(left + 116, y + 26, "Sniper", CohModeModels.Role.SNIPER);
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
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        drawPanel(graphics, panelLeft, panelTop, panelRight, panelBottom);
        super.render(graphics, mouseX, mouseY, partialTick);

        CohModeModels.LobbyStateView s = CohModeClientState.state();
        graphics.fill(panelLeft + 12, panelTop + 30, panelRight - 12, panelTop + 31, CohUiTheme.DIVIDER);
        graphics.fill(panelLeft + 8, panelTop + 7, panelLeft + 11, panelTop + 21, CohUiTheme.HOVER_BORDER);
        graphics.drawCenteredString(font, "Standalone Role Selection", width / 2, panelTop + 10, CohUiTheme.TEXT_PRIMARY);
        graphics.drawString(font, "Double-click role to open loadout customization", panelLeft + 12, panelTop + 90, CohUiTheme.TEXT_SECONDARY, false);
        graphics.drawString(font, "Selected role: " + (s.selectedRole == null ? "none" : s.selectedRole.label), panelLeft + 12, panelTop + 106, CohUiTheme.TEXT_PRIMARY, false);
        graphics.drawString(font, "Selected camp: " + (s.selectedCamp == null ? "none" : s.selectedCamp.label), panelLeft + 12, panelTop + 118, CohUiTheme.TEXT_SECONDARY, false);
        if (s.statusText != null && !s.statusText.isEmpty()) {
            graphics.drawCenteredString(font, s.statusText, width / 2, panelBottom - 12, 0xFFF0F0A0);
        }
    }

    private void drawPanel(GuiGraphics graphics, int left, int top, int right, int bottom) {
        graphics.fillGradient(left, top, right, bottom, CohUiTheme.CARD_BG_TOP, CohUiTheme.CARD_BG_BOTTOM);
        graphics.fill(left, top, right, top + 1, CohUiTheme.HOVER_BORDER_SEMI);
        graphics.fill(left, bottom - 1, right, bottom, CohUiTheme.BORDER_SUBTLE);
        graphics.fill(left, top, left + 1, bottom, CohUiTheme.BORDER_SUBTLE);
        graphics.fill(right - 1, top, right, bottom, CohUiTheme.BORDER_SUBTLE);
        graphics.fillGradient(left + 1, top + 1, right - 1, top + 16, 0x1CFFFFFF, 0x02000000);
    }
}
