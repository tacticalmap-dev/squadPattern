package com.flowingsun.warpattern.cohmode.client;

import com.flowingsun.warpattern.cohmode.net.CohModeInviteS2C;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Modal invite popup for party/room invitations.
 */
public class CohModeInviteScreen extends Screen {
    private static final int POPUP_MIN_WIDTH = 240;
    private static final int POPUP_MAX_WIDTH = 380;

    private final Screen parent;
    private final CohModeInviteS2C invite;

    public CohModeInviteScreen(Screen parent, CohModeInviteS2C invite) {
        super(Component.literal("协作模式邀请"));
        this.parent = parent;
        this.invite = invite;
    }

    @Override
    protected void init() {
        int popupWidth = popupWidth();
        int popupHeight = popupHeight();
        int popupLeft = (this.width - popupWidth) / 2;
        int popupTop = (this.height - popupHeight) / 2;
        int popupBottom = popupTop + popupHeight;
        int buttonY = popupBottom - 30;

        addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("接受"), b -> {
            JsonObject payload = CohModeClientState.json();
            payload.addProperty("inviteId", invite.inviteId);
            CohModeClientState.sendAction("accept_invite", payload);
            onClose();
        }).bounds(popupLeft + popupWidth / 2 - 104, buttonY, 100, 20).build());

        addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("拒绝"), b -> {
            JsonObject payload = CohModeClientState.json();
            payload.addProperty("inviteId", invite.inviteId);
            CohModeClientState.sendAction("decline_invite", payload);
            onClose();
        }).bounds(popupLeft + popupWidth / 2 + 4, buttonY, 100, 20).build());
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0, 0, this.width, this.height, 0xA0000000, 0xA0000000);
        int popupWidth = popupWidth();
        int popupHeight = popupHeight();
        int popupLeft = (this.width - popupWidth) / 2;
        int popupTop = (this.height - popupHeight) / 2;
        int popupRight = popupLeft + popupWidth;
        int popupBottom = popupTop + popupHeight;
        drawPopupPanel(graphics, popupLeft, popupTop, popupRight, popupBottom);

        String kind = switch (invite.kind) {
            case PARTY -> "组队邀请";
            case ROOM -> "房间邀请";
        };
        String roomSuffix = invite.roomId == null ? "" : (" 房间号=" + invite.roomId);
        int titleY = popupTop + 14;
        graphics.drawCenteredString(font, title, width / 2, titleY, CohUiTheme.TEXT_PRIMARY);
        graphics.drawCenteredString(font, "来自：" + invite.fromName, width / 2, titleY + 22, CohUiTheme.TEXT_SECONDARY);
        graphics.drawCenteredString(font, "类型：" + kind + roomSuffix, width / 2, titleY + 34, CohUiTheme.TEXT_SECONDARY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private int popupWidth() {
        int widthByScreen = Math.max(POPUP_MIN_WIDTH, this.width - 80);
        return Math.min(POPUP_MAX_WIDTH, widthByScreen);
    }

    private int popupHeight() {
        return 130;
    }

    private void drawPopupPanel(GuiGraphics graphics, int left, int top, int right, int bottom) {
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
        graphics.fillGradient(left + 1, top + 1, right - 1, top + 18, 0x1CFFFFFF, 0x02000000);
        graphics.fill(left + 12, top + 34, right - 12, top + 35, CohUiTheme.DIVIDER);
        
        // Scanline effect
        for (int i = top + 2; i < bottom - 2; i += 4) {
            graphics.fill(left + 1, i, right - 1, i + 1, 0x10000000);
        }
    }
}
