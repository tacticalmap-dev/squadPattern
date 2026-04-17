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
        super(Component.literal("Cohmode Invite"));
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

        addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("Accept"), b -> {
            JsonObject payload = CohModeClientState.json();
            payload.addProperty("inviteId", invite.inviteId);
            CohModeClientState.sendAction("accept_invite", payload);
            onClose();
        }).bounds(popupLeft + popupWidth / 2 - 104, buttonY, 100, 20).build());

        addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("Decline"), b -> {
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

        String kind = invite.kind.name().toLowerCase();
        String roomSuffix = invite.roomId == null ? "" : (" room=" + invite.roomId);
        int titleY = popupTop + 14;
        graphics.drawCenteredString(font, title, width / 2, titleY, CohUiTheme.TEXT_PRIMARY);
        graphics.drawCenteredString(font, "From: " + invite.fromName, width / 2, titleY + 22, CohUiTheme.TEXT_SECONDARY);
        graphics.drawCenteredString(font, "Type: " + kind + roomSuffix, width / 2, titleY + 34, CohUiTheme.TEXT_SECONDARY);
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
        graphics.fill(left, top, right, top + 1, CohUiTheme.HOVER_BORDER_SEMI);
        graphics.fill(left, bottom - 1, right, bottom, CohUiTheme.BORDER_SUBTLE);
        graphics.fill(left, top, left + 1, bottom, CohUiTheme.BORDER_SUBTLE);
        graphics.fill(right - 1, top, right, bottom, CohUiTheme.BORDER_SUBTLE);
        graphics.fillGradient(left + 1, top + 1, right - 1, top + 18, 0x1CFFFFFF, 0x02000000);
        graphics.fill(left + 12, top + 34, right - 12, top + 35, CohUiTheme.DIVIDER);
    }
}
