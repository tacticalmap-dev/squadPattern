package com.flowingsun.warpattern.cohmode.client;

import com.flowingsun.warpattern.cohmode.CohModeModels;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
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
    private boolean forceCampStep;

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
            forceCampStep = false;
            return;
        }
        if (s.selectedCamp == null) {
            selectedMode = null;
            forceCampStep = false;
        }
    }

    private UiStep resolveStep() {
        CohModeModels.LobbyStateView s = CohModeClientState.state();
        if (s.currentRoom != null) {
            return UiStep.ROOM_PREP;
        }
        if (s.selectedCamp == null || forceCampStep) {
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

        addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("Refresh"), b ->
                CohModeClientState.sendAction("refresh", CohModeClientState.json()))
                .bounds(left, top, 80, 20).build());
        if (renderedStep != UiStep.CAMP) {
            addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("Back"), b -> onBack())
                    .bounds(left + 86, top, 80, 20).build());
        }
        addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("Close"), b -> onClose())
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
        int modeY = top;
        addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("Matchmaking"), b -> {
            selectedMode = UiMode.MATCHMAKING;
            rebuildUi();
        }).bounds(left, modeY, 150, 20).build());

        addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("Room Mode"), b -> {
            selectedMode = UiMode.ROOM;
            rebuildUi();
        }).bounds(left + 156, modeY, 150, 20).build());
    }

    private void buildMatchStep(int left, int top) {
        int roleY = top;
        addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("Ready / Join Queue"), b ->
                CohModeClientState.sendAction("join_random", CohModeClientState.json()))
                .bounds(left, roleY, 140, 20).build());
        addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("Leave Queue"), b ->
                CohModeClientState.sendAction("leave_random", CohModeClientState.json()))
                .bounds(left + 146, roleY, 110, 20).build());

        inviteNameBox = new EditBox(font, left, roleY + 28, 150, 20, Component.literal("Invite player"));
        inviteNameBox.setHint(Component.literal("Player name"));
        addRenderableWidget(inviteNameBox);

        addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("Invite Party"), b -> {
            String name = inviteNameBox.getValue().trim();
            if (name.isEmpty()) {
                return;
            }
            JsonObject payload = CohModeClientState.json();
            payload.addProperty("targetName", name);
            CohModeClientState.sendAction("invite_party", payload);
        }).bounds(left + 156, roleY + 28, 110, 20).build());
    }

    private void buildRoomEntryStep(int left, int top) {
        addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("Create Room"), b ->
                CohModeClientState.sendAction("create_room", CohModeClientState.json()))
                .bounds(left, top, 120, 20).build());

        roomIdBox = new EditBox(font, left, top + 28, 120, 20, Component.literal("Room ID"));
        roomIdBox.setHint(Component.literal("Room ID"));
        addRenderableWidget(roomIdBox);

        addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("Join Room"), b -> {
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

        addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("Invite Party"), b -> {
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
        addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("Leave Room"), b ->
                CohModeClientState.sendAction("leave_room", CohModeClientState.json()))
                .bounds(left, top, 110, 20).build());

        addCampButtons(left + 116, top);

        int roleY = top + 28;
        addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("Claim Cmdr"), b ->
                CohModeClientState.sendAction("claim_commander", CohModeClientState.json()))
                .bounds(left, roleY, 110, 20).build());
        addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("Release Cmdr"), b ->
                CohModeClientState.sendAction("release_commander", CohModeClientState.json()))
                .bounds(left + 116, roleY, 110, 20).build());
        addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("Toggle Ready"), b ->
                CohModeClientState.sendAction("toggle_ready", CohModeClientState.json()))
                .bounds(left + 232, roleY, 110, 20).build());
        addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("Start Room"), b ->
                CohModeClientState.sendAction("start_room", CohModeClientState.json()))
                .bounds(left + 348, roleY, 110, 20).build());

        mapNameBox = new EditBox(font, left, roleY + 28, 150, 20, Component.literal("Map name"));
        mapNameBox.setHint(Component.literal("Map name"));
        addRenderableWidget(mapNameBox);

        addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("Set Room Map"), b -> {
            String map = mapNameBox.getValue().trim();
            if (map.isEmpty()) {
                return;
            }
            JsonObject payload = CohModeClientState.json();
            payload.addProperty("mapName", map);
            CohModeClientState.sendAction("set_room_map", payload);
        }).bounds(left + 156, roleY + 28, 120, 20).build());

        inviteNameBox = new EditBox(font, left, roleY + 56, 150, 20, Component.literal("Invite player"));
        inviteNameBox.setHint(Component.literal("Player name"));
        addRenderableWidget(inviteNameBox);

        addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("Invite Room"), b -> {
            String name = inviteNameBox.getValue().trim();
            if (name.isEmpty()) {
                return;
            }
            JsonObject payload = CohModeClientState.json();
            payload.addProperty("targetName", name);
            CohModeClientState.sendAction("invite_room", payload);
        }).bounds(left + 156, roleY + 56, 120, 20).build());

        kickNameBox = new EditBox(font, left + 282, roleY + 56, 140, 20, Component.literal("Kick player"));
        kickNameBox.setHint(Component.literal("Kick name"));
        addRenderableWidget(kickNameBox);

        addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("Kick"), b -> {
            String name = kickNameBox.getValue().trim();
            if (name.isEmpty()) {
                return;
            }
            JsonObject payload = CohModeClientState.json();
            payload.addProperty("targetName", name);
            CohModeClientState.sendAction("kick_room_member", payload);
        }).bounds(left + 428, roleY + 56, 70, 20).build());
    }

    private void addCampButtons(int left, int top) {
        addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("Red (Warsaw)"), b -> selectCamp(CohModeModels.Camp.RED))
                .bounds(left, top, 110, 20).build());
        addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("Blue (NATO)"), b -> selectCamp(CohModeModels.Camp.BLUE))
                .bounds(left + 116, top, 110, 20).build());
    }

    private void selectCamp(CohModeModels.Camp camp) {
        CohModeClientState.state().selectedCamp = camp;
        forceCampStep = false;
        JsonObject payload = CohModeClientState.json();
        payload.addProperty("camp", camp.name());
        CohModeClientState.sendAction("select_camp", payload);
        if (renderedStep == UiStep.CAMP || renderedStep == UiStep.MODE) {
            rebuildUi();
        }
    }

    private void onBack() {
        UiStep step = renderedStep == null ? resolveStep() : renderedStep;
        switch (step) {
            case MODE -> {
                forceCampStep = true;
                selectedMode = null;
                rebuildUi();
            }
            case MATCHMAKING, ROOM_ENTRY -> {
                selectedMode = null;
                forceCampStep = false;
                rebuildUi();
            }
            case ROOM_PREP -> {
                // Step 4 -> Step 3(Room Entry): leave current room then show room entry flow.
                selectedMode = UiMode.ROOM;
                forceCampStep = false;
                CohModeClientState.sendAction("leave_room", CohModeClientState.json());
            }
            default -> {
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        CohModeModels.LobbyStateView s = CohModeClientState.state();
        UiStep step = renderedStep == null ? resolveStep() : renderedStep;
        renderChrome(graphics, step);

        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(font, "Cohmode Flow UI", width / 2, 8, CohUiTheme.TEXT_PRIMARY);
        graphics.drawString(font, "Step " + step.level + "/4 - " + step.title, 20, 8, CohUiTheme.TEXT_SECONDARY, false);

        int x = 20;
        int y = Math.max(170, height - 86);
        graphics.drawString(font, "Selected camp: " + (s.selectedCamp == null ? "none" : s.selectedCamp.label), x, y, CohUiTheme.TEXT_PRIMARY, false);
        y += 11;
        graphics.drawString(font, "Selected role: " + (s.selectedRole == null ? "none" : s.selectedRole.label), x, y, CohUiTheme.TEXT_PRIMARY, false);
        y += 11;
        graphics.drawString(font, "Queue red/blue: " + s.queue.redQueued + "/" + s.queue.blueQueued + (s.queue.queued ? " (you queued)" : ""), x, y, CohUiTheme.TEXT_SECONDARY, false);
        y += 11;

        if (s.party != null) {
            graphics.drawString(font, "Party leader: " + s.party.leaderName + "  members=" + String.join(", ", s.party.members), x, y, 0xFFB8D8FF, false);
            y += 11;
        }

        if (step == UiStep.CAMP) {
            graphics.drawString(font, "Step 1: choose your camp to continue.", 20, 52, CohUiTheme.TEXT_PRIMARY, false);
        } else if (step == UiStep.MODE) {
            graphics.drawString(font, "Step 2: choose matchmaking or room mode.", 20, 52, CohUiTheme.TEXT_PRIMARY, false);
            graphics.drawString(font, "Camp can only be changed in Step 1.", 20, 64, CohUiTheme.TEXT_SECONDARY, false);
        } else if (step == UiStep.MATCHMAKING) {
            graphics.drawString(font, "Step 3: ready up for queue (role selection moved to /warpattern cohmode role).", 20, 52, CohUiTheme.TEXT_PRIMARY, false);
            drawMapList(graphics, rightPanelLeft() + 12, 52, s.maps, height - 44);
        } else if (step == UiStep.ROOM_ENTRY) {
            graphics.drawString(font, "Step 3: create room or join existing room.", 20, 52, CohUiTheme.TEXT_PRIMARY, false);
            drawOpenRooms(graphics, rightPanelLeft() + 12, 52, s.rooms, height - 44);
        } else if (step == UiStep.ROOM_PREP) {
            graphics.drawString(font, "Step 4: room prep (camp/commander/ready/map/start).", 20, 52, CohUiTheme.TEXT_PRIMARY, false);
            drawRoomState(graphics, 20, 72, s);
            drawMapList(graphics, rightPanelLeft() + 12, 52, s.maps, height - 44);
        }

        if (s.statusText != null && !s.statusText.isEmpty()) {
            graphics.fillGradient(12, height - 22, width - 12, height - 6, 0x90202020, 0xD0101010);
            graphics.drawCenteredString(font, s.statusText, width / 2, height - 17, 0xFFF0F0A0);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        graphics.fillGradient(0, 0, width, height, CohUiTheme.BG_TOP, CohUiTheme.BG_BOTTOM);
    }

    private void drawRoomState(GuiGraphics graphics, int left, int top, CohModeModels.LobbyStateView s) {
        if (s.currentRoom == null) {
            return;
        }
        int y = top;
        graphics.drawString(font, "Room: " + s.currentRoom.roomId + " host=" + s.currentRoom.hostName
                + " map=" + (s.currentRoom.mapName == null ? "<none>" : s.currentRoom.mapName), left, y, 0xFFFFD080, false);
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
            graphics.drawString(font, line, left + 6, y, 0xFFFFD080, false);
            y += 10;
            if (y > height - 52) {
                break;
            }
        }
    }

    private void drawOpenRooms(GuiGraphics graphics, int left, int top, List<CohModeModels.RoomListItemView> rooms, int bottomLimit) {
        int y = top;
        graphics.drawString(font, "Open rooms:", left, y, CohUiTheme.TEXT_PRIMARY, false);
        y += 11;
        for (CohModeModels.RoomListItemView room : rooms) {
            String line = room.roomId + " host=" + room.hostName + " map="
                    + (room.mapName == null ? "<none>" : room.mapName)
                    + " players=" + room.members + "/" + room.maxPlayers;
            graphics.drawString(font, line, left, y, CohUiTheme.TEXT_SECONDARY, false);
            y += 10;
            if (y > bottomLimit) {
                break;
            }
        }
    }

    private void drawMapList(GuiGraphics graphics, int left, int top, List<CohModeModels.MapView> maps, int bottomLimit) {
        int y = top;
        graphics.drawString(font, "Ready maps:", left, y, CohUiTheme.TEXT_PRIMARY, false);
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

    private int rightPanelLeft() {
        return Math.max(width / 2 + 8, 292);
    }

    private void renderChrome(GuiGraphics graphics, UiStep step) {
        int headerAccentX = 10;
        int headerY = 8;
        int headerBottom = 18;
        graphics.fill(headerAccentX, headerY - 1, headerAccentX + 3, headerBottom + 1, CohUiTheme.HOVER_BORDER);
        graphics.fill(20, 24, width - 20, 25, CohUiTheme.DIVIDER);

        int panelTop = 40;
        int panelBottom = height - 28;
        int leftPanelLeft = 10;
        int leftPanelRight = rightPanelLeft() - 8;
        drawPanel(graphics, leftPanelLeft, panelTop, leftPanelRight, panelBottom, true);

        if (step == UiStep.MATCHMAKING || step == UiStep.ROOM_ENTRY || step == UiStep.ROOM_PREP) {
            drawPanel(graphics, rightPanelLeft(), panelTop, width - 10, panelBottom, false);
        }
    }

    private void drawPanel(GuiGraphics graphics, int left, int top, int right, int bottom, boolean highlightedTop) {
        if (right - left < 8 || bottom - top < 8) {
            return;
        }
        graphics.fillGradient(left, top, right, bottom, CohUiTheme.CARD_BG_TOP, CohUiTheme.CARD_BG_BOTTOM);
        graphics.fill(left, top, right, top + 1, highlightedTop ? CohUiTheme.HOVER_BORDER_SEMI : CohUiTheme.BORDER_SUBTLE);
        graphics.fill(left, bottom - 1, right, bottom, CohUiTheme.BORDER_SUBTLE);
        graphics.fill(left, top, left + 1, bottom, CohUiTheme.BORDER_SUBTLE);
        graphics.fill(right - 1, top, right, bottom, CohUiTheme.BORDER_SUBTLE);
        graphics.fillGradient(left + 1, top + 1, right - 1, top + 14, 0x1CFFFFFF, 0x02000000);
    }
}
