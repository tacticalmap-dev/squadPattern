package com.flowingsun.warpattern.cohmode.client;

import com.flowingsun.warpattern.cohmode.CohModeModels;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Main command-opened cohmode UI screen.
 */
public class CohModeScreen extends Screen {
    private EditBox inviteNameBox;
    private EditBox roomIdBox;
    private EditBox mapNameBox;
    private EditBox kickNameBox;

    public CohModeScreen() {
        super(Component.literal("Cohmode Match UI"));
    }

    @Override
    protected void init() {
        int left = 12;
        int top = 20;

        addRenderableWidget(Button.builder(Component.literal("Refresh"), b -> {
            CohModeClientState.sendAction("refresh", CohModeClientState.json());
        }).bounds(left, top, 80, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(width - 92, top, 80, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Red (Warsaw)"), b -> {
            JsonObject payload = CohModeClientState.json();
            payload.addProperty("camp", CohModeModels.Camp.RED.name());
            CohModeClientState.sendAction("select_camp", payload);
        }).bounds(left, top + 30, 110, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Blue (NATO)"), b -> {
            JsonObject payload = CohModeClientState.json();
            payload.addProperty("camp", CohModeModels.Camp.BLUE.name());
            CohModeClientState.sendAction("select_camp", payload);
        }).bounds(left + 116, top + 30, 110, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Cycle Role"), b -> {
            CohModeModels.Role next = nextRole();
            JsonObject payload = CohModeClientState.json();
            payload.addProperty("role", next.name());
            CohModeClientState.sendAction("select_role", payload);
        }).bounds(left + 232, top + 30, 100, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Join Random"), b -> {
            CohModeClientState.sendAction("join_random", CohModeClientState.json());
        }).bounds(left, top + 58, 110, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Leave Queue"), b -> {
            CohModeClientState.sendAction("leave_random", CohModeClientState.json());
        }).bounds(left + 116, top + 58, 110, 20).build());

        inviteNameBox = new EditBox(font, left, top + 86, 150, 20, Component.literal("Invite player"));
        inviteNameBox.setHint(Component.literal("Player name"));
        addRenderableWidget(inviteNameBox);

        addRenderableWidget(Button.builder(Component.literal("Invite Party"), b -> {
            String name = inviteNameBox.getValue().trim();
            if (name.isEmpty()) {
                return;
            }
            JsonObject payload = CohModeClientState.json();
            payload.addProperty("targetName", name);
            CohModeClientState.sendAction("invite_party", payload);
        }).bounds(left + 156, top + 86, 90, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Create Room"), b -> {
            CohModeClientState.sendAction("create_room", CohModeClientState.json());
        }).bounds(left + 252, top + 86, 100, 20).build());

        roomIdBox = new EditBox(font, left, top + 114, 120, 20, Component.literal("Room ID"));
        roomIdBox.setHint(Component.literal("Room ID"));
        addRenderableWidget(roomIdBox);

        addRenderableWidget(Button.builder(Component.literal("Join Room"), b -> {
            String roomId = roomIdBox.getValue().trim();
            if (roomId.isEmpty()) {
                return;
            }
            JsonObject payload = CohModeClientState.json();
            payload.addProperty("roomId", roomId);
            CohModeClientState.sendAction("join_room", payload);
        }).bounds(left + 126, top + 114, 100, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Leave Room"), b -> {
            CohModeClientState.sendAction("leave_room", CohModeClientState.json());
        }).bounds(left + 232, top + 114, 100, 20).build());

        mapNameBox = new EditBox(font, left, top + 142, 120, 20, Component.literal("Map name"));
        mapNameBox.setHint(Component.literal("Map name"));
        addRenderableWidget(mapNameBox);

        addRenderableWidget(Button.builder(Component.literal("Set Room Map"), b -> {
            String map = mapNameBox.getValue().trim();
            if (map.isEmpty()) {
                return;
            }
            JsonObject payload = CohModeClientState.json();
            payload.addProperty("mapName", map);
            CohModeClientState.sendAction("set_room_map", payload);
        }).bounds(left + 126, top + 142, 100, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Toggle Ready"), b -> {
            CohModeClientState.sendAction("toggle_ready", CohModeClientState.json());
        }).bounds(left + 232, top + 142, 100, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Claim Cmdr"), b -> {
            CohModeClientState.sendAction("claim_commander", CohModeClientState.json());
        }).bounds(left, top + 170, 110, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Release Cmdr"), b -> {
            CohModeClientState.sendAction("release_commander", CohModeClientState.json());
        }).bounds(left + 116, top + 170, 110, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Start Room"), b -> {
            CohModeClientState.sendAction("start_room", CohModeClientState.json());
        }).bounds(left + 232, top + 170, 100, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Invite Room"), b -> {
            String name = inviteNameBox.getValue().trim();
            if (name.isEmpty()) {
                return;
            }
            JsonObject payload = CohModeClientState.json();
            payload.addProperty("targetName", name);
            CohModeClientState.sendAction("invite_room", payload);
        }).bounds(left + 338, top + 86, 90, 20).build());

        kickNameBox = new EditBox(font, left + 338, top + 114, 120, 20, Component.literal("Kick player"));
        kickNameBox.setHint(Component.literal("Kick name"));
        addRenderableWidget(kickNameBox);

        addRenderableWidget(Button.builder(Component.literal("Kick"), b -> {
            String name = kickNameBox.getValue().trim();
            if (name.isEmpty()) {
                return;
            }
            JsonObject payload = CohModeClientState.json();
            payload.addProperty("targetName", name);
            CohModeClientState.sendAction("kick_room_member", payload);
        }).bounds(left + 464, top + 114, 60, 20).build());
    }

    private CohModeModels.Role nextRole() {
        CohModeModels.Role[] roles = CohModeModels.Role.values();
        CohModeModels.Role current = CohModeClientState.state().selectedRole;
        int index = 0;
        if (current != null) {
            for (int i = 0; i < roles.length; i++) {
                if (roles[i] == current) {
                    index = i;
                    break;
                }
            }
        }
        return roles[(index + 1) % roles.length];
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        CohModeModels.LobbyStateView s = CohModeClientState.state();

        int x = 12;
        int y = 210;
        graphics.drawString(font, "Selected camp: " + (s.selectedCamp == null ? "none" : s.selectedCamp.label), x, y, 0xFFFFFF, false);
        y += 11;
        graphics.drawString(font, "Selected role: " + (s.selectedRole == null ? "none" : s.selectedRole.label), x, y, 0xFFFFFF, false);
        y += 11;
        graphics.drawString(font, "Queue red/blue: " + s.queue.redQueued + "/" + s.queue.blueQueued + (s.queue.queued ? " (you queued)" : ""), x, y, 0xC8C8C8, false);
        y += 11;

        if (s.party != null) {
            graphics.drawString(font, "Party leader: " + s.party.leaderName + "  members=" + String.join(", ", s.party.members), x, y, 0xB0E0FF, false);
            y += 11;
        }

        if (s.currentRoom != null) {
            graphics.drawString(font, "Room: " + s.currentRoom.roomId + " host=" + s.currentRoom.hostName + " map=" + (s.currentRoom.mapName == null ? "<none>" : s.currentRoom.mapName), x, y, 0xFFD080, false);
            y += 11;
            List<CohModeModels.RoomMemberView> members = s.currentRoom.members.stream()
                    .sorted(Comparator.comparing(m -> m.playerName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
            for (CohModeModels.RoomMemberView member : members) {
                String memberLine = " - " + member.playerName + " ["
                        + (member.camp == null ? "no-camp" : member.camp.name()) + "/"
                        + (member.role == null ? "no-role" : member.role.name()) + "]"
                        + (member.ready ? " ready" : " not-ready")
                        + (member.host ? " host" : "");
                graphics.drawString(font, memberLine, x + 6, y, 0xFFD080, false);
                y += 10;
                if (y > height - 50) {
                    break;
                }
            }
        }

        int rightX = width / 2 + 20;
        int rightY = 58;
        graphics.drawString(font, "Open rooms:", rightX, rightY, 0xFFFFFF, false);
        rightY += 11;
        for (CohModeModels.RoomListItemView room : s.rooms) {
            String line = room.roomId + " host=" + room.hostName + " map=" + (room.mapName == null ? "<none>" : room.mapName)
                    + " players=" + room.members + "/" + room.maxPlayers;
            graphics.drawString(font, line, rightX, rightY, 0xC8C8C8, false);
            rightY += 10;
            if (rightY > height - 120) {
                break;
            }
        }

        int mapY = rightY + 6;
        graphics.drawString(font, "Ready maps and recommended players:", rightX, mapY, 0xFFFFFF, false);
        mapY += 11;
        for (CohModeModels.MapView map : s.maps) {
            String line = (map.ready ? "[ok] " : "[x] ") + map.mapName + " " + map.recommendedMinPlayers + "-" + map.recommendedMaxPlayers;
            graphics.drawString(font, line, rightX, mapY, map.ready ? 0xA0FFA0 : 0xFF9090, false);
            mapY += 10;
            if (mapY > height - 40) {
                break;
            }
        }

        if (s.statusText != null && !s.statusText.isEmpty()) {
            graphics.drawCenteredString(font, s.statusText, width / 2, height - 14, 0xF0F0A0);
        }
    }
}
