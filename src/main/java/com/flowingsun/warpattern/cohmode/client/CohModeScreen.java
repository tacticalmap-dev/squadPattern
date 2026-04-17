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

    private static class FlowLayout {
        int startX, startY, maxWidth;
        int currentX, currentY;
        int rowHeight;
        int gapX = 6, gapY = 8;

        FlowLayout(int startX, int startY, int maxWidth) {
            this.startX = startX;
            this.startY = startY;
            this.maxWidth = maxWidth;
            this.currentX = startX;
            this.currentY = startY;
        }

        int[] next(int w, int h) {
            if (currentX + w > startX + maxWidth && currentX > startX) {
                currentX = startX;
                currentY += rowHeight + gapY;
                rowHeight = 0;
            }
            int[] bounds = new int[]{currentX, currentY, w, h};
            rowHeight = Math.max(rowHeight, h);
            currentX += w + gapX;
            return bounds;
        }

        void lineBreak(int extraGap) {
            if (currentX > startX) {
                currentX = startX;
                currentY += rowHeight + gapY;
                rowHeight = 0;
            }
            currentY += extraGap;
        }
    }

    private enum UiStep {
        CAMP(1, "选择阵营"),
        MODE(2, "选择模式"),
        MATCHMAKING(3, "随机匹配"),
        ROOM_ENTRY(3, "创建/加入房间"),
        ROOM_PREP(4, "房间准备");

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
        super(Component.literal("协作模式流程界面"));
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

        addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("刷新"), b ->
                CohModeClientState.sendAction("refresh", CohModeClientState.json()))
                .bounds(left, top, 80, 20).build());
        if (renderedStep != UiStep.CAMP) {
            addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("返回"), b -> onBack())
                    .bounds(left + 86, top, 80, 20).build());
        }
        addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("关闭"), b -> onClose())
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
        FlowLayout layout = new FlowLayout(left, top, rightPanelLeft() - 8 - left - 12);
        addCampButtons(layout);
    }

    private void buildModeStep(int left, int top) {
        FlowLayout layout = new FlowLayout(left, top, rightPanelLeft() - 8 - left - 12);
        int[] b = layout.next(150, 20);
        addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("随机匹配"), btn -> {
            selectedMode = UiMode.MATCHMAKING;
            rebuildUi();
        }).bounds(b[0], b[1], b[2], b[3]).build());

        b = layout.next(150, 20);
        addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("房间模式"), btn -> {
            selectedMode = UiMode.ROOM;
            rebuildUi();
        }).bounds(b[0], b[1], b[2], b[3]).build());
    }

    private void buildMatchStep(int left, int top) {
        FlowLayout layout = new FlowLayout(left, top, rightPanelLeft() - 8 - left - 12);
        
        int[] b = layout.next(140, 20);
        addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("准备并加入队列"), btn ->
                CohModeClientState.sendAction("join_random", CohModeClientState.json()))
                .bounds(b[0], b[1], b[2], b[3]).build());
                
        b = layout.next(110, 20);
        addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("离开队列"), btn ->
                CohModeClientState.sendAction("leave_random", CohModeClientState.json()))
                .bounds(b[0], b[1], b[2], b[3]).build());

        layout.lineBreak(8);

        b = layout.next(150, 20);
        inviteNameBox = new EditBox(font, b[0], b[1], b[2], b[3], Component.literal("邀请玩家"));
        inviteNameBox.setHint(Component.literal("玩家名"));
        addRenderableWidget(inviteNameBox);

        b = layout.next(110, 20);
        addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("邀请组队"), btn -> {
            String name = inviteNameBox.getValue().trim();
            if (name.isEmpty()) {
                return;
            }
            JsonObject payload = CohModeClientState.json();
            payload.addProperty("targetName", name);
            CohModeClientState.sendAction("invite_party", payload);
        }).bounds(b[0], b[1], b[2], b[3]).build());
    }

    private void buildRoomEntryStep(int left, int top) {
        FlowLayout layout = new FlowLayout(left, top, rightPanelLeft() - 8 - left - 12);
        
        int[] b = layout.next(120, 20);
        addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("创建房间"), btn ->
                CohModeClientState.sendAction("create_room", CohModeClientState.json()))
                .bounds(b[0], b[1], b[2], b[3]).build());

        layout.lineBreak(8);

        b = layout.next(120, 20);
        roomIdBox = new EditBox(font, b[0], b[1], b[2], b[3], Component.literal("房间号"));
        roomIdBox.setHint(Component.literal("房间号"));
        addRenderableWidget(roomIdBox);

        b = layout.next(120, 20);
        addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("加入房间"), btn -> {
            String roomId = roomIdBox.getValue().trim();
            if (roomId.isEmpty()) {
                return;
            }
            JsonObject payload = CohModeClientState.json();
            payload.addProperty("roomId", roomId);
            CohModeClientState.sendAction("join_room", payload);
        }).bounds(b[0], b[1], b[2], b[3]).build());

        layout.lineBreak(8);

        b = layout.next(150, 20);
        inviteNameBox = new EditBox(font, b[0], b[1], b[2], b[3], Component.literal("邀请玩家"));
        inviteNameBox.setHint(Component.literal("玩家名"));
        addRenderableWidget(inviteNameBox);

        b = layout.next(110, 20);
        addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("邀请组队"), btn -> {
            String name = inviteNameBox.getValue().trim();
            if (name.isEmpty()) {
                return;
            }
            JsonObject payload = CohModeClientState.json();
            payload.addProperty("targetName", name);
            CohModeClientState.sendAction("invite_party", payload);
        }).bounds(b[0], b[1], b[2], b[3]).build());
    }

    private void buildRoomPrepStep(int left, int top) {
        FlowLayout layout = new FlowLayout(left, top, rightPanelLeft() - 8 - left - 12);

        int[] b = layout.next(110, 20);
        addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("离开房间"), btn ->
                CohModeClientState.sendAction("leave_room", CohModeClientState.json()))
                .bounds(b[0], b[1], b[2], b[3]).build());

        addCampButtons(layout);

        layout.lineBreak(8);

        b = layout.next(110, 20);
        addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("申请指挥官"), btn ->
                CohModeClientState.sendAction("claim_commander", CohModeClientState.json()))
                .bounds(b[0], b[1], b[2], b[3]).build());
        b = layout.next(110, 20);
        addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("取消指挥官"), btn ->
                CohModeClientState.sendAction("release_commander", CohModeClientState.json()))
                .bounds(b[0], b[1], b[2], b[3]).build());
        b = layout.next(110, 20);
        addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("切换准备"), btn ->
                CohModeClientState.sendAction("toggle_ready", CohModeClientState.json()))
                .bounds(b[0], b[1], b[2], b[3]).build());
        b = layout.next(110, 20);
        addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("开始房间"), btn ->
                CohModeClientState.sendAction("start_room", CohModeClientState.json()))
                .bounds(b[0], b[1], b[2], b[3]).build());

        layout.lineBreak(8);

        b = layout.next(150, 20);
        mapNameBox = new EditBox(font, b[0], b[1], b[2], b[3], Component.literal("地图名"));
        mapNameBox.setHint(Component.literal("地图名"));
        addRenderableWidget(mapNameBox);

        b = layout.next(120, 20);
        addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("设置房间地图"), btn -> {
            String map = mapNameBox.getValue().trim();
            if (map.isEmpty()) {
                return;
            }
            JsonObject payload = CohModeClientState.json();
            payload.addProperty("mapName", map);
            CohModeClientState.sendAction("set_room_map", payload);
        }).bounds(b[0], b[1], b[2], b[3]).build());

        layout.lineBreak(8);

        b = layout.next(150, 20);
        inviteNameBox = new EditBox(font, b[0], b[1], b[2], b[3], Component.literal("邀请玩家"));
        inviteNameBox.setHint(Component.literal("玩家名"));
        addRenderableWidget(inviteNameBox);

        b = layout.next(120, 20);
        addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("邀请进房"), btn -> {
            String name = inviteNameBox.getValue().trim();
            if (name.isEmpty()) {
                return;
            }
            JsonObject payload = CohModeClientState.json();
            payload.addProperty("targetName", name);
            CohModeClientState.sendAction("invite_room", payload);
        }).bounds(b[0], b[1], b[2], b[3]).build());

        layout.lineBreak(8);

        b = layout.next(140, 20);
        kickNameBox = new EditBox(font, b[0], b[1], b[2], b[3], Component.literal("踢出玩家"));
        kickNameBox.setHint(Component.literal("玩家名"));
        addRenderableWidget(kickNameBox);

        b = layout.next(70, 20);
        addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("踢出"), btn -> {
            String name = kickNameBox.getValue().trim();
            if (name.isEmpty()) {
                return;
            }
            JsonObject payload = CohModeClientState.json();
            payload.addProperty("targetName", name);
            CohModeClientState.sendAction("kick_room_member", payload);
        }).bounds(b[0], b[1], b[2], b[3]).build());
    }

    private void addCampButtons(FlowLayout layout) {
        int[] b = layout.next(110, 20);
        addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("红队（华约）"), btn -> selectCamp(CohModeModels.Camp.RED))
                .bounds(b[0], b[1], b[2], b[3])
                .color(CohUiTheme.WARSAW_RED)
                .build());
        b = layout.next(110, 20);
        addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("蓝队（北约）"), btn -> selectCamp(CohModeModels.Camp.BLUE))
                .bounds(b[0], b[1], b[2], b[3])
                .color(CohUiTheme.NATO_BLUE)
                .build());
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

        graphics.drawCenteredString(font, "战术大厅终端", width / 2, 8, CohUiTheme.TEXT_PRIMARY);
        graphics.drawString(font, "作战阶段 " + step.level + "/4 - " + step.title, 20, 8, CohUiTheme.TEXT_HIGHLIGHT, false);

        int x = 20;
        int y = Math.max(170, height - 86);
        graphics.drawString(font, "当前阵营：" + (s.selectedCamp == null ? "未选择" : s.selectedCamp.label), x, y, CohUiTheme.TEXT_PRIMARY, false);
        y += 11;
        graphics.drawString(font, "当前兵种：" + (s.selectedRole == null ? "未选择" : s.selectedRole.label), x, y, CohUiTheme.TEXT_PRIMARY, false);
        y += 11;
        graphics.drawString(font, "队列状态（华约/北约）：" + s.queue.redQueued + "/" + s.queue.blueQueued + (s.queue.queued ? "（已在队列中）" : ""), x, y, CohUiTheme.TEXT_SECONDARY, false);
        y += 11;

        if (s.party != null) {
            graphics.drawString(font, "队长：" + s.party.leaderName + "  成员：" + String.join(", ", s.party.members), x, y, CohUiTheme.NATO_BLUE, false);
            y += 11;
        }

        if (step == UiStep.CAMP) {
            graphics.drawString(font, "> 第 1 阶段：选择你的阵营。", 20, 52, CohUiTheme.TEXT_PRIMARY, false);
        } else if (step == UiStep.MODE) {
            graphics.drawString(font, "> 第 2 阶段：选择进入方式。", 20, 52, CohUiTheme.TEXT_PRIMARY, false);
            graphics.drawString(font, "当前阶段不能修改阵营。", 20, 64, CohUiTheme.TEXT_SECONDARY, false);
        } else if (step == UiStep.MATCHMAKING) {
            graphics.drawString(font, "> 第 3 阶段：等待匹配（兵种配置可用 /warpattern cohmode role 打开）。", 20, 52, CohUiTheme.TEXT_PRIMARY, false);
            drawMapList(graphics, rightPanelLeft() + 12, 52, s.maps, height - 44);
        } else if (step == UiStep.ROOM_ENTRY) {
            graphics.drawString(font, "> 第 3 阶段：创建房间或加入已有房间。", 20, 52, CohUiTheme.TEXT_PRIMARY, false);
            drawOpenRooms(graphics, rightPanelLeft() + 12, 52, s.rooms, height - 44);
        } else if (step == UiStep.ROOM_PREP) {
            graphics.drawString(font, "> 第 4 阶段：房间准备与开局设置。", 20, 52, CohUiTheme.TEXT_PRIMARY, false);
            drawRoomState(graphics, 20, 72, s);
            drawMapList(graphics, rightPanelLeft() + 12, 52, s.maps, height - 44);
        }

        if (s.statusText != null && !s.statusText.isEmpty()) {
            graphics.fillGradient(12, height - 22, width - 12, height - 6, 0x90202020, 0xD0101010);
            graphics.drawCenteredString(font, "系统消息：" + s.statusText, width / 2, height - 17, CohUiTheme.TEXT_HIGHLIGHT);
        }
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

    private void drawRoomState(GuiGraphics graphics, int left, int top, CohModeModels.LobbyStateView s) {
        if (s.currentRoom == null) {
            return;
        }
        int y = top;
        graphics.drawString(font, "房间：" + s.currentRoom.roomId + "  房主=" + s.currentRoom.hostName
                + "  地图=" + (s.currentRoom.mapName == null ? "<未设置>" : s.currentRoom.mapName), left, y, 0xFFFFD080, false);
        y += 11;
        List<CohModeModels.RoomMemberView> members = s.currentRoom.members.stream()
                .sorted(Comparator.comparing(m -> m.playerName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        for (CohModeModels.RoomMemberView member : members) {
            String line = " - " + member.playerName + " ["
                    + (member.camp == null ? "未选阵营" : member.camp.label) + "/"
                    + (member.role == null ? "未选兵种" : member.role.label) + "]"
                    + (member.ready ? " 已准备" : " 未准备")
                    + (member.host ? " 房主" : "");
            graphics.drawString(font, line, left + 6, y, 0xFFFFD080, false);
            y += 10;
            if (y > height - 52) {
                break;
            }
        }
    }

    private void drawOpenRooms(GuiGraphics graphics, int left, int top, List<CohModeModels.RoomListItemView> rooms, int bottomLimit) {
        int y = top;
        graphics.drawString(font, "公开房间：", left, y, CohUiTheme.TEXT_PRIMARY, false);
        y += 11;
        for (CohModeModels.RoomListItemView room : rooms) {
            String line = room.roomId + "  房主=" + room.hostName + "  地图="
                    + (room.mapName == null ? "<未设置>" : room.mapName)
                    + "  人数=" + room.members + "/" + room.maxPlayers;
            graphics.drawString(font, line, left, y, CohUiTheme.TEXT_SECONDARY, false);
            y += 10;
            if (y > bottomLimit) {
                break;
            }
        }
    }

    private void drawMapList(GuiGraphics graphics, int left, int top, List<CohModeModels.MapView> maps, int bottomLimit) {
        int y = top;
        graphics.drawString(font, "可用地图：", left, y, CohUiTheme.TEXT_PRIMARY, false);
        y += 11;
        for (CohModeModels.MapView map : maps) {
            String line = (map.ready ? "[可用] " : "[未就绪] ")
                    + map.mapName + "  推荐人数 " + map.recommendedMinPlayers + "-" + map.recommendedMaxPlayers;
            graphics.drawString(font, line, left, y, map.ready ? 0xA0FFA0 : 0xFF9090, false);
            y += 10;
            if (y > bottomLimit) {
                break;
            }
        }
    }

    private int rightPanelLeft() {
        int minLeftPanelWidth = 220;
        int preferredLeftPanelWidth = 292;
        int half = width / 2 + 8;
        if (width < 500) {
            return Math.max(minLeftPanelWidth, (int)(width * 0.55));
        }
        return Math.max(half, preferredLeftPanelWidth);
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
        
        int borderColor = highlightedTop ? CohUiTheme.HOVER_BORDER_SEMI : CohUiTheme.BORDER_SUBTLE;
        
        // Draw tactical border with corners
        graphics.fill(left + 2, top, right - 2, top + 1, borderColor);
        graphics.fill(left + 2, bottom - 1, right - 2, bottom, CohUiTheme.BORDER_SUBTLE);
        graphics.fill(left, top + 2, left + 1, bottom - 2, CohUiTheme.BORDER_SUBTLE);
        graphics.fill(right - 1, top + 2, right, bottom - 2, CohUiTheme.BORDER_SUBTLE);
        
        // Corner accents
        int cornerColor = highlightedTop ? CohUiTheme.HOVER_BORDER : CohUiTheme.BORDER_SUBTLE;
        graphics.fill(left, top, left + 4, top + 2, cornerColor);
        graphics.fill(left, top, left + 2, top + 4, cornerColor);
        
        graphics.fill(right - 4, top, right, top + 2, cornerColor);
        graphics.fill(right - 2, top, right, top + 4, cornerColor);
        
        graphics.fill(left, bottom - 2, left + 4, bottom, cornerColor);
        graphics.fill(left, bottom - 4, left + 2, bottom, cornerColor);
        
        graphics.fill(right - 4, bottom - 2, right, bottom, cornerColor);
        graphics.fill(right - 2, bottom - 4, right, bottom, cornerColor);

        // Highlight gradient
        graphics.fillGradient(left + 1, top + 1, right - 1, top + 14, 0x1CFFFFFF, 0x02000000);
        
        // Scanline effect (Optional subtlety)
        for (int i = top + 2; i < bottom - 2; i += 4) {
            graphics.fill(left + 1, i, right - 1, i + 1, 0x10000000);
        }
    }
}
