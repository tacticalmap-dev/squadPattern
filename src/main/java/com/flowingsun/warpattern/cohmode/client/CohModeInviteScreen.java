package com.flowingsun.warpattern.cohmode.client;

import com.flowingsun.warpattern.cohmode.net.CohModeInviteS2C;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Modal invite popup for party/room invitations.
 */
public class CohModeInviteScreen extends Screen {
    private final Screen parent;
    private final CohModeInviteS2C invite;

    public CohModeInviteScreen(Screen parent, CohModeInviteS2C invite) {
        super(Component.literal("Cohmode Invite"));
        this.parent = parent;
        this.invite = invite;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int centerY = height / 2;

        addRenderableWidget(Button.builder(Component.literal("Accept"), b -> {
            JsonObject payload = CohModeClientState.json();
            payload.addProperty("inviteId", invite.inviteId);
            CohModeClientState.sendAction("accept_invite", payload);
            onClose();
        }).bounds(centerX - 104, centerY + 20, 100, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Decline"), b -> {
            JsonObject payload = CohModeClientState.json();
            payload.addProperty("inviteId", invite.inviteId);
            CohModeClientState.sendAction("decline_invite", payload);
            onClose();
        }).bounds(centerX + 4, centerY + 20, 100, 20).build());
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        String kind = invite.kind.name().toLowerCase();
        String roomSuffix = invite.roomId == null ? "" : (" room=" + invite.roomId);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 36, 0xFFFFFF);
        graphics.drawCenteredString(font, "From: " + invite.fromName, width / 2, height / 2 - 10, 0xD0D0D0);
        graphics.drawCenteredString(font, "Type: " + kind + roomSuffix, width / 2, height / 2 + 2, 0xD0D0D0);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
