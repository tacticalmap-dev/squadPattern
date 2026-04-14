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
 * Command-opened cohmode UI with a guided 4-level flow.
 */
public class CohModeScreen extends Screen {
    private enum UiMode {
        MATCHMAKING,
        ROOM
    }

    private enum UiStep {
        CAMP(1, "Select Camp"),
        MODE(2, "Select Mode"),
        MATCHMAKING(3, "Matchmaking"),
        ROOM_ENTRY(3, "Create/Join Room"),
        ROOM_PREP(4, "Room Ready");

        final int level;
        final String title;

        UiStep(int level, String title) {
            this.level = level;
            this.title = title;
        }
    }

    private EditBox inviteNameBox;
    private EditBox roomIdBox;
    private EditBox mapNameBox;
    private EditBox kickNameBox;
    private UiMode selectedMode;
    private UiStep renderedStep;

    public CohModeScreen() {
        super(Component.literal("Cohmode Flow UI"));
    }

    @Override
    protected void init() {
        syncModeFromState();
        rebuildUi();
    }

    @Override
    public void tick() {
        super.tick();
        if (inviteNameBox != null) {
            inviteNameBox.tick();
        }
        if (roomIdBox != null) {
            roomIdBox.tick();
        }
        if (mapNameBox != null) {
            mapNameBox.tick();
        }
        if (kickNameBox != null) {
            kickNameBox.tick();
        }

        syncModeFromState();
        UiStep current = resolveStep();
        if (current != renderedStep) {
            rebuildUi();
        }
    }

    private void syncModeFromState() {
        CohModeModels.LobbyStateView s = CohModeClientState.state();
        if (s.currentRoom != null) {
            selectedMode = UiMode.ROOM;
            return;
        }
        if (s.selectedCamp == null) {
            selectedMode = null;
        }
    }

    private UiStep resolveStep() {
        CohModeModels.LobbyStateView s = CohModeClientState.state();
        if (s.currentRoom != null) {
            return UiStep.ROOM_PREP;
        }
        if (s.selectedCamp == null) {
            return UiStep.CAMP;
        }
        if (selectedMode == null) {
            return UiStep.MODE;
        }
        return selectedMode == UiMode.MATCHMAKING ? UiStep.MATCHMAKING : UiStep.ROOM_ENTRY;
    }

    private void rebuildUi() {
        clearWidgets();
        inviteNameBox = null;
        roomIdBox = null;
        mapNameBox = null;
        kickNameBox = null;

        renderedStep = resolveStep();
        int left = 12;
        int top = 20;

        addRenderableWidget(Button.builder(Component.literal("Refresh"), b ->
                CohModeClientState.sendAction("refresh", CohModeClientState.json()))
                .bounds(left, top, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(width - 92, top, 80, 20).build());

        switch (renderedStep) {
            case CAMP -> buildCampStep(left, top + 32);
            case MODE -> buildModeStep(left, top + 32);
            case MATCHMAKING -> buildMatchStep(left, top + 32);
            case ROOM_ENTRY -> buildRoomEntryStep(left, top + 32);
            case ROOM_PREP -> buildRoomPrepStep(left, top + 32);
        }
    }

    private void buildCampStep(int left, int top) {
        addCampButtons(left, top);
    }

    private void buildModeStep(int left, int top) {
        addRenderableWidget(Button.builder(Component.literal("Matchmaking"), b -> {
            selectedMode = UiMode.MATCHMAKING;
            rebuildUi();
        }).bounds(left, top, 150, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Room Mode"), b -> {
            selectedMode = UiMode.ROOM;
            rebuildUi();
        }).bounds(left + 156, top, 150, 20).build());

        addCampButtons(left, top + 28);
    }

    private void buildMatchStep(int left, int top) {
        addRenderableWidget(Button.builder(Component.literal("Back To Mode"), b -> {
            selectedMode = null;
            rebuildUi();
        }).bounds(left, top, 120, 20).build());

        int roleY = top + 28;
        addRenderableWidget(Button.builder(Component.literal("Rifleman"), b -> selectRole(CohModeModels.Role.RIFLEMAN))
                .bounds(left, roleY, 95, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Assault"), b -> selectRole(CohModeModels.Role.ASSAULT))
                .bounds(left + 101, roleY, 95, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Support"), b -> selectRole(CohModeModels.Role.SUPPORT))
                .bounds(left + 202, roleY, 95, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Sniper"), b -> selectRole(CohModeModels.Role.SNIPER))
                .bounds(left, roleY + 24, 95, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Commander"), b -> selectRole(CohModeModels.Role.COMMANDER))
                .bounds(left + 101, roleY + 24, 95, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Ready / Join Queue"), b ->
                CohModeClientState.sendAction("join_random", CohModeClientState.json()))
                .bounds(left, roleY + 56, 140, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Leave Queue"), b ->
                CohModeClientState.sendAction("leave_random", CohModeClientState.json()))
                .bounds(left + 146, roleY + 56, 110, 20).build());

        inviteNameBox = new EditBox(font, left, roleY + 84, 150, 20, Component.literal("Invite player"));
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
        }).bounds(left + 156, roleY + 84, 110, 20).build());
    }

    private void buildRoomEntryStep(int left, int top) {
        addRenderableWidget(Button.builder(Component.literal("Back To Mode"), b -> {
            selectedMode = null;
            rebuildUi();
        }).bounds(left, top, 120, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Create Room"), b ->
                CohModeClientState.sendAction("create_room", CohModeClientState.json()))
                .bounds(left + 126, top, 120, 20).build());

        roomIdBox = new EditBox(font, left, top + 28, 120, 20, Component.literal("Room ID"));
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
        }).bounds(left + 126, top + 28, 120, 20).build());

        inviteNameBox = new EditBox(font, left, top + 56, 150, 20, Component.literal("Invite player"));
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
        }).bounds(left + 156, top + 56, 110, 20).build());
    }

    private void buildRoomPrepStep(int left, int top) {
        addRenderableWidget(Button.builder(Component.literal("Leave Room"), b ->
                CohModeClientState.sendAction("leave_room", CohModeClientState.json()))
                .bounds(left, top, 110, 20).build());

        addCampButtons(left + 116, top);

        int roleY = top + 28;
        addRenderableWidget(Button.builder(Component.literal("Rifleman"), b -> selectRole(CohModeModels.Role.RIFLEMAN))
                .bounds(left, roleY, 95, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Assault"), b -> selectRole(CohModeModels.Role.ASSAULT))
                .bounds(left + 101, roleY, 95, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Support"), b -> selectRole(CohModeModels.Role.SUPPORT))
                .bounds(left + 202, roleY, 95, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Sniper"), b -> selectRole(CohModeModels.Role.SNIPER))
                .bounds(left + 303, roleY, 95, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Claim Cmdr"), b ->
                CohModeClientState.sendAction("claim_commander", CohModeClientState.json()))
                .bounds(left, roleY + 24, 110, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Release Cmdr"), b ->
                CohModeClientState.sendAction("release_commander", CohModeClientState.json()))
                .bounds(left + 116, roleY + 24, 110, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Toggle Ready"), b ->
                CohModeClientState.sendAction("toggle_ready", CohModeClientState.json()))
                .bounds(left + 232, roleY + 24, 110, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Start Room"), b ->
                CohModeClientState.sendAction("start_room", CohModeClientState.json()))
                .bounds(left + 348, roleY + 24, 110, 20).build());

        mapNameBox = new EditBox(font, left, roleY + 52, 150, 20, Component.literal("Map name"));
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
        }).bounds(left + 156, roleY + 52, 120, 20).build());

        inviteNameBox = new EditBox(font, left, roleY + 80, 150, 20, Component.literal("Invite player"));
        inviteNameBox.setHint(Component.literal("Player name"));
        addRenderableWidget(inviteNameBox);

        addRenderableWidget(Button.builder(Component.literal("Invite Room"), b -> {
            String name = inviteNameBox.getValue().trim();
            if (name.isEmpty()) {
                return;
            }
            JsonObject payload = CohModeClientState.json();
            payload.addProperty("targetName", name);
            CohModeClientState.sendAction("invite_room", payload);
        }).bounds(left + 156, roleY + 80, 120, 20).build());

        kickNameBox = new EditBox(font, left + 282, roleY + 80, 140, 20, Component.literal("Kick player"));
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
        }).bounds(left + 428, roleY + 80, 70, 20).build());
    }

    private void addCampButtons(int left, int top) {
        addRenderableWidget(Button.builder(Component.literal("Red (Warsaw)"), b -> selectCamp(CohModeModels.Camp.RED))
                .bounds(left, top, 110, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Blue (NATO)"), b -> selectCamp(CohModeModels.Camp.BLUE))
                .bounds(left + 116, top, 110, 20).build());
    }

    private void selectCamp(CohModeModels.Camp camp) {
        JsonObject payload = CohModeClientState.json();
        payload.addProperty("camp", camp.name());
        CohModeClientState.sendAction("select_camp", payload);
    }

    private void selectRole(CohModeModels.Role role) {
        JsonObject payload = CohModeClientState.json();
        payload.addProperty("role", role.name());
        CohModeClientState.sendAction("select_role", payload);
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
        UiStep step = renderedStep == null ? resolveStep() : renderedStep;

        graphics.drawCenteredString(font, "Cohmode Flow UI", width / 2, 6, 0xFFFFFF);
        graphics.drawString(font, "Step " + step.level + "/4 - " + step.title, 12, 6, 0xD8D8D8, false);

        int x = 12;
        int y = 170;
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

        if (step == UiStep.CAMP) {
            graphics.drawString(font, "Step 1: choose your camp to continue.", 12, 64, 0xFFFFFF, false);
        } else if (step == UiStep.MODE) {
            graphics.drawString(font, "Step 2: choose matchmaking or room mode.", 12, 64, 0xFFFFFF, false);
        } else if (step == UiStep.MATCHMAKING) {
            graphics.drawString(font, "Step 3: pick role then ready up (join queue).", 12, 64, 0xFFFFFF, false);
            drawMapList(graphics, width / 2 + 20, 64, s.maps, height - 44);
        } else if (step == UiStep.ROOM_ENTRY) {
            graphics.drawString(font, "Step 3: create room or join existing room.", 12, 64, 0xFFFFFF, false);
            drawOpenRooms(graphics, width / 2 + 20, 64, s.rooms, height - 44);
        } else if (step == UiStep.ROOM_PREP) {
            graphics.drawString(font, "Step 4: room prep (camp/role/ready/map/start).", 12, 64, 0xFFFFFF, false);
            drawRoomState(graphics, 12, 78, s);
            drawMapList(graphics, width / 2 + 20, 64, s.maps, height - 44);
        }

        if (s.statusText != null && !s.statusText.isEmpty()) {
            graphics.drawCenteredString(font, s.statusText, width / 2, height - 14, 0xF0F0A0);
        }
    }

    private void drawRoomState(GuiGraphics graphics, int left, int top, CohModeModels.LobbyStateView s) {
        if (s.currentRoom == null) {
            return;
        }
        int y = top;
        graphics.drawString(font, "Room: " + s.currentRoom.roomId + " host=" + s.currentRoom.hostName
                + " map=" + (s.currentRoom.mapName == null ? "<none>" : s.currentRoom.mapName), left, y, 0xFFD080, false);
        y += 11;
        List<CohModeModels.RoomMemberView> members = s.currentRoom.members.stream()
                .sorted(Comparator.comparing(m -> m.playerName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        for (CohModeModels.RoomMemberView member : members) {
            String line = " - " + member.playerName + " ["
                    + (member.camp == null ? "no-camp" : member.camp.name()) + "/"
                    + (member.role == null ? "no-role" : member.role.name()) + "]"
                    + (member.ready ? " ready" : " not-ready")
                    + (member.host ? " host" : "");
            graphics.drawString(font, line, left + 6, y, 0xFFD080, false);
            y += 10;
            if (y > height - 52) {
                break;
            }
        }
    }

    private void drawOpenRooms(GuiGraphics graphics, int left, int top, List<CohModeModels.RoomListItemView> rooms, int bottomLimit) {
        int y = top;
        graphics.drawString(font, "Open rooms:", left, y, 0xFFFFFF, false);
        y += 11;
        for (CohModeModels.RoomListItemView room : rooms) {
            String line = room.roomId + " host=" + room.hostName + " map="
                    + (room.mapName == null ? "<none>" : room.mapName)
                    + " players=" + room.members + "/" + room.maxPlayers;
            graphics.drawString(font, line, left, y, 0xC8C8C8, false);
            y += 10;
            if (y > bottomLimit) {
                break;
            }
        }
    }

    private void drawMapList(GuiGraphics graphics, int left, int top, List<CohModeModels.MapView> maps, int bottomLimit) {
        int y = top;
        graphics.drawString(font, "Ready maps:", left, y, 0xFFFFFF, false);
        y += 11;
        for (CohModeModels.MapView map : maps) {
            String line = (map.ready ? "[ok] " : "[x] ")
                    + map.mapName + " " + map.recommendedMinPlayers + "-" + map.recommendedMaxPlayers;
            graphics.drawString(font, line, left, y, map.ready ? 0xA0FFA0 : 0xFF9090, false);
            y += 10;
            if (y > bottomLimit) {
                break;
            }
        }
    }
}
