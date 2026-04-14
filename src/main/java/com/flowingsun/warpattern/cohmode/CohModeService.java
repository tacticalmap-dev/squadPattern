package com.flowingsun.warpattern.cohmode;

import com.flowingsun.vppoints.api.VpPointsApi;
import com.flowingsun.warpattern.cohmode.net.CohModeInviteS2C;
import com.flowingsun.warpattern.cohmode.net.CohModeNetwork;
import com.flowingsun.warpattern.cohmode.net.CohModeStateS2C;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Cohmode server state and command-opened UI flow.
 */
public final class CohModeService {
    public static final CohModeService INSTANCE = new CohModeService();
    private static final Gson GSON = new Gson();

    private final Map<UUID, PlayerProfile> profiles = new ConcurrentHashMap<>();
    private final Map<UUID, QueueEntry> queueByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> partyLeaderByMember = new ConcurrentHashMap<>();
    private final Map<UUID, Party> partyByLeader = new ConcurrentHashMap<>();
    private final Map<String, Room> roomById = new ConcurrentHashMap<>();
    private final Map<UUID, String> roomByPlayer = new ConcurrentHashMap<>();
    private final Map<String, PendingInvite> inviteById = new ConcurrentHashMap<>();
    private final Map<String, Long> inviteCooldownAt = new ConcurrentHashMap<>();

    private long ticks;

    private CohModeService() {
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("cohmode")
                .then(Commands.literal("open")
                        .executes(ctx -> openSelf(ctx.getSource()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> openTarget(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))));
        event.getDispatcher().register(root);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer() == null) {
            return;
        }
        ticks++;
        if (ticks % 20L != 0L) {
            return;
        }
        cleanupInvites();
        processRandomQueue(event.getServer());
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID id = player.getUUID();
        queueByPlayer.remove(id);
        leaveRoom(player.server, id);
        leaveParty(player.server, id);
        inviteById.values().removeIf(inv -> inv.fromId.equals(id) || inv.targetId.equals(id));
    }

    public void handleClientAction(ServerPlayer player, String action, String payloadJson) {
        JsonObject payload = parsePayload(payloadJson);
        switch (action) {
            case "refresh" -> push(player, false);
            case "select_camp" -> selectCamp(player, payload);
            case "select_role" -> selectRole(player, payload);
            case "join_random" -> joinRandom(player);
            case "leave_random" -> {
                queueByPlayer.remove(player.getUUID());
                status(player, "Left random queue.");
                push(player, false);
            }
            case "create_room" -> createRoom(player);
            case "join_room" -> joinRoom(player, getString(payload, "roomId"));
            case "leave_room" -> {
                leaveRoom(player.server, player.getUUID());
                push(player, false);
            }
            case "toggle_ready" -> toggleReady(player);
            case "set_room_map" -> setRoomMap(player, payload);
            case "start_room" -> startRoom(player);
            case "invite_party" -> invite(player, payload, CohModeModels.InviteKind.PARTY);
            case "invite_room" -> invite(player, payload, CohModeModels.InviteKind.ROOM);
            case "accept_invite" -> handleInviteReply(player, payload, true);
            case "decline_invite" -> handleInviteReply(player, payload, false);
            case "claim_commander" -> setCommander(player, true);
            case "release_commander" -> setCommander(player, false);
            case "kick_room_member" -> kickRoomMember(player, payload);
            default -> {
                status(player, "Unknown action: " + action, true);
                push(player, false);
            }
        }
    }

    private int openSelf(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Player-only command."));
            return 0;
        }
        push(player, true);
        return Command.SINGLE_SUCCESS;
    }

    private int openTarget(CommandSourceStack source, ServerPlayer target) {
        if (target == null) {
            return 0;
        }
        push(target, true);
        source.sendSuccess(() -> Component.literal("Opened cohmode UI for " + target.getGameProfile().getName()), true);
        return Command.SINGLE_SUCCESS;
    }

    private void push(ServerPlayer player, boolean openScreen) {
        String json = GSON.toJson(buildState(player));
        CohModeNetwork.sendTo(player, new CohModeStateS2C(json, openScreen));
    }

    private JsonObject parsePayload(String payloadJson) {
        try {
            return JsonParser.parseString(payloadJson == null ? "{}" : payloadJson).getAsJsonObject();
        } catch (Exception ignored) {
            return new JsonObject();
        }
    }

    private String getString(JsonObject payload, String key) {
        if (payload == null || !payload.has(key) || payload.get(key).isJsonNull()) {
            return "";
        }
        return Objects.requireNonNullElse(payload.get(key).getAsString(), "").trim();
    }

    private CohModeModels.Camp parseCamp(String text) {
        try {
            return CohModeModels.Camp.valueOf(text.toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return null;
        }
    }

    private CohModeModels.Role parseRole(String text) {
        try {
            return CohModeModels.Role.valueOf(text.toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return null;
        }
    }

    private PlayerProfile profile(ServerPlayer player) {
        PlayerProfile profile = profiles.computeIfAbsent(player.getUUID(), id -> new PlayerProfile());
        profile.lastName = player.getGameProfile().getName();
        if (profile.role == null) {
            profile.role = CohModeModels.Role.RIFLEMAN;
        }
        return profile;
    }

    private String nameOf(MinecraftServer server, UUID id) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        PlayerProfile profile = profiles.get(id);
        return profile == null || profile.lastName == null ? id.toString().substring(0, 8) : profile.lastName;
    }

    private void status(ServerPlayer player, String text) {
        status(player, text, false);
    }

    private void status(ServerPlayer player, String text, boolean error) {
        profile(player).status = text;
        player.displayClientMessage(Component.literal(text), true);
    }

    private void selectCamp(ServerPlayer player, JsonObject payload) {
        CohModeModels.Camp camp = parseCamp(getString(payload, "camp"));
        if (camp == null) {
            status(player, "Invalid camp.", true);
            push(player, false);
            return;
        }
        profile(player).camp = camp;
        status(player, "Selected camp: " + camp.label);
        push(player, false);
    }

    private void selectRole(ServerPlayer player, JsonObject payload) {
        CohModeModels.Role role = parseRole(getString(payload, "role"));
        if (role == null) {
            status(player, "Invalid role.", true);
            push(player, false);
            return;
        }
        if (roomByPlayer.containsKey(player.getUUID()) && role == CohModeModels.Role.COMMANDER) {
            status(player, "Use Claim Cmdr in room mode.", true);
            push(player, false);
            return;
        }
        profile(player).role = role;
        status(player, "Selected role: " + role.label);
        push(player, false);
    }

    private void joinRandom(ServerPlayer player) {
        UUID playerId = player.getUUID();
        if (roomByPlayer.containsKey(playerId)) {
            status(player, "Leave room before queueing.", true);
            push(player, false);
            return;
        }

        UUID leader = partyLeaderByMember.getOrDefault(playerId, playerId);
        if (!leader.equals(playerId)) {
            status(player, "Only party leader can queue.", true);
            push(player, false);
            return;
        }

        Party party = partyByLeader.get(leader);
        List<UUID> members = party == null ? List.of(playerId) : new ArrayList<>(party.members);
        if (members.size() > CohModeConfig.maxPartySize()) {
            status(player, "Party exceeds max size " + CohModeConfig.maxPartySize(), true);
            push(player, false);
            return;
        }

        CohModeModels.Camp camp = profile(player).camp;
        if (camp == null) {
            status(player, "Select camp first.", true);
            push(player, false);
            return;
        }

        long now = System.currentTimeMillis();
        int commanders = 0;
        for (UUID memberId : members) {
            ServerPlayer online = player.server.getPlayerList().getPlayer(memberId);
            if (online == null) {
                continue;
            }
            PlayerProfile prof = profile(online);
            if (prof.camp == null) {
                prof.camp = camp;
            }
            if (prof.camp != camp) {
                status(player, "Party members must use the same camp.", true);
                push(player, false);
                return;
            }
            if (prof.role == CohModeModels.Role.COMMANDER) {
                commanders++;
                if (commanders > 1) {
                    prof.role = CohModeModels.Role.RIFLEMAN;
                }
            }
        }

        for (UUID memberId : members) {
            ServerPlayer online = player.server.getPlayerList().getPlayer(memberId);
            if (online == null) {
                continue;
            }
            QueueEntry entry = new QueueEntry();
            entry.playerId = memberId;
            entry.partyLeader = leader;
            entry.camp = profile(online).camp;
            entry.role = profile(online).role;
            entry.joinedAt = now;
            entry.kd = globalKd(player.server, memberId);
            queueByPlayer.put(memberId, entry);
            push(online, false);
        }

        status(player, "Queued " + members.size() + " player(s).");
        push(player, false);
    }

    private void createRoom(ServerPlayer player) {
        if (roomByPlayer.containsKey(player.getUUID())) {
            status(player, "Already in a room.", true);
            push(player, false);
            return;
        }
        queueByPlayer.remove(player.getUUID());
        Room room = new Room();
        room.id = nextRoomId();
        room.hostId = player.getUUID();
        RoomMember host = new RoomMember();
        host.camp = profile(player).camp;
        host.role = profile(player).role;
        host.ready = false;
        room.members.put(player.getUUID(), host);
        roomById.put(room.id, room);
        roomByPlayer.put(player.getUUID(), room.id);
        status(player, "Room created: " + room.id);
        push(player, false);
    }

    private void joinRoom(ServerPlayer player, String roomId) {
        if (roomId.isBlank()) {
            status(player, "Room ID required.", true);
            push(player, false);
            return;
        }
        Room room = roomById.get(roomId);
        if (room == null) {
            status(player, "Room not found: " + roomId, true);
            push(player, false);
            return;
        }
        if (room.members.size() >= CohModeConfig.roomMaxPlayers()) {
            status(player, "Room is full.", true);
            push(player, false);
            return;
        }
        leaveRoom(player.server, player.getUUID());
        queueByPlayer.remove(player.getUUID());
        RoomMember member = new RoomMember();
        member.camp = null;
        member.role = CohModeModels.Role.RIFLEMAN;
        room.members.put(player.getUUID(), member);
        roomByPlayer.put(player.getUUID(), room.id);
        status(player, "Joined room " + room.id);
        pushRoom(player.server, room);
    }

    private void leaveRoom(MinecraftServer server, UUID playerId) {
        String roomId = roomByPlayer.remove(playerId);
        if (roomId == null) {
            return;
        }
        Room room = roomById.get(roomId);
        if (room == null) {
            return;
        }
        room.members.remove(playerId);
        if (room.members.isEmpty()) {
            roomById.remove(room.id);
            return;
        }
        if (room.hostId.equals(playerId)) {
            room.hostId = room.members.keySet().iterator().next();
        }
        pushRoom(server, room);
    }

    private void toggleReady(ServerPlayer player) {
        Room room = roomById.get(roomByPlayer.get(player.getUUID()));
        if (room == null) {
            status(player, "Not in room.", true);
            push(player, false);
            return;
        }
        RoomMember member = room.members.get(player.getUUID());
        if (member != null) {
            member.ready = !member.ready;
        }
        status(player, member != null && member.ready ? "Ready." : "Unready.");
        pushRoom(player.server, room);
    }

    private void setRoomMap(ServerPlayer player, JsonObject payload) {
        Room room = roomById.get(roomByPlayer.get(player.getUUID()));
        if (room == null || !room.hostId.equals(player.getUUID())) {
            status(player, "Only room host can set map.", true);
            push(player, false);
            return;
        }
        String mapName = normalize(getString(payload, "mapName"));
        if (mapName.isBlank()) {
            status(player, "Map name required.", true);
            push(player, false);
            return;
        }
        boolean ready = com.flowingsun.warpattern.match.SquadMatchService.INSTANCE.listMapMatchmakingViews().stream()
                .anyMatch(map -> map.ready() && map.mapName().equals(mapName));
        if (!ready) {
            status(player, "Map is not ready: " + mapName, true);
            push(player, false);
            return;
        }
        room.mapName = mapName;
        status(player, "Room map set: " + mapName);
        pushRoom(player.server, room);
    }

    private void setCommander(ServerPlayer player, boolean claim) {
        Room room = roomById.get(roomByPlayer.get(player.getUUID()));
        if (room == null) {
            profile(player).role = claim ? CohModeModels.Role.COMMANDER : CohModeModels.Role.RIFLEMAN;
            status(player, claim ? "Commander selected." : "Commander released.");
            push(player, false);
            return;
        }
        RoomMember self = room.members.get(player.getUUID());
        if (self == null) {
            push(player, false);
            return;
        }
        if (!claim) {
            self.role = CohModeModels.Role.RIFLEMAN;
            self.ready = false;
            pushRoom(player.server, room);
            return;
        }
        if (self.camp == null) {
            status(player, "Choose camp before claiming commander.", true);
            push(player, false);
            return;
        }
        boolean used = room.members.values().stream().anyMatch(member -> member != self && member.camp == self.camp && member.role == CohModeModels.Role.COMMANDER);
        if (used) {
            status(player, "Commander slot already taken in your camp.", true);
            push(player, false);
            return;
        }
        self.role = CohModeModels.Role.COMMANDER;
        self.ready = false;
        status(player, "Commander claimed.");
        pushRoom(player.server, room);
    }

    private void kickRoomMember(ServerPlayer host, JsonObject payload) {
        Room room = roomById.get(roomByPlayer.get(host.getUUID()));
        if (room == null || !room.hostId.equals(host.getUUID())) {
            status(host, "Only room host can kick.", true);
            push(host, false);
            return;
        }
        String name = getString(payload, "targetName");
        UUID target = room.members.keySet().stream().filter(id -> name.equalsIgnoreCase(nameOf(host.server, id))).findFirst().orElse(null);
        if (target == null || target.equals(host.getUUID())) {
            status(host, "Kick target not found.", true);
            push(host, false);
            return;
        }
        room.members.remove(target);
        roomByPlayer.remove(target);
        ServerPlayer kicked = host.server.getPlayerList().getPlayer(target);
        if (kicked != null) {
            status(kicked, "You were removed from room " + room.id + ".", true);
            push(kicked, false);
        }
        status(host, "Player removed.");
        pushRoom(host.server, room);
    }

    private void invite(ServerPlayer from, JsonObject payload, CohModeModels.InviteKind kind) {
        String targetName = getString(payload, "targetName");
        ServerPlayer target = from.server.getPlayerList().getPlayers().stream()
                .filter(p -> p.getGameProfile().getName().equalsIgnoreCase(targetName))
                .findFirst()
                .orElse(null);
        if (target == null || target.getUUID().equals(from.getUUID())) {
            status(from, "Valid online target required.", true);
            push(from, false);
            return;
        }
        if (kind == CohModeModels.InviteKind.ROOM) {
            Room room = roomById.get(roomByPlayer.get(from.getUUID()));
            if (room == null || !room.hostId.equals(from.getUUID())) {
                status(from, "Only room host can invite to room.", true);
                push(from, false);
                return;
            }
        }
        String key = kind.name() + ":" + from.getUUID() + ":" + target.getUUID();
        long now = System.currentTimeMillis();
        long last = inviteCooldownAt.getOrDefault(key, 0L);
        if (now - last < CohModeConfig.inviteCooldownMillis()) {
            status(from, "Invite cooldown active.", true);
            push(from, false);
            return;
        }
        inviteCooldownAt.put(key, now);
        PendingInvite invite = new PendingInvite();
        invite.id = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        invite.kind = kind;
        invite.fromId = from.getUUID();
        invite.targetId = target.getUUID();
        invite.roomId = kind == CohModeModels.InviteKind.ROOM ? roomByPlayer.get(from.getUUID()) : null;
        invite.expireAt = now + CohModeConfig.inviteExpireMillis();
        inviteById.put(invite.id, invite);
        CohModeNetwork.sendTo(target, new CohModeInviteS2C(invite.id, invite.kind, from.getGameProfile().getName(), invite.roomId, invite.expireAt));
        status(from, "Invite sent.");
        push(from, false);
        push(target, false);
    }

    private void handleInviteReply(ServerPlayer player, JsonObject payload, boolean accept) {
        String inviteId = getString(payload, "inviteId");
        PendingInvite invite = inviteById.get(inviteId);
        if (invite == null || !invite.targetId.equals(player.getUUID())) {
            status(player, "Invite not found.", true);
            push(player, false);
            return;
        }
        if (invite.expireAt < System.currentTimeMillis()) {
            inviteById.remove(invite.id);
            status(player, "Invite expired.", true);
            push(player, false);
            return;
        }
        inviteById.remove(invite.id);

        if (!accept) {
            status(player, "Invite declined.");
            ServerPlayer inviter = player.server.getPlayerList().getPlayer(invite.fromId);
            if (inviter != null) {
                status(inviter, "Invite declined by " + player.getGameProfile().getName(), true);
                push(inviter, false);
            }
            push(player, false);
            return;
        }

        if (invite.kind == CohModeModels.InviteKind.PARTY) {
            acceptPartyInvite(player, invite);
        } else {
            joinRoom(player, invite.roomId == null ? "" : invite.roomId);
        }
    }

    private void acceptPartyInvite(ServerPlayer player, PendingInvite invite) {
        ServerPlayer inviter = player.server.getPlayerList().getPlayer(invite.fromId);
        if (inviter == null) {
            status(player, "Inviter is offline.", true);
            push(player, false);
            return;
        }
        UUID leader = partyLeaderByMember.getOrDefault(inviter.getUUID(), inviter.getUUID());
        Party party = partyByLeader.computeIfAbsent(leader, key -> {
            Party created = new Party();
            created.leaderId = key;
            created.members.add(key);
            partyLeaderByMember.put(key, key);
            return created;
        });
        if (party.members.size() >= CohModeConfig.maxPartySize()) {
            status(player, "Party is full.", true);
            push(player, false);
            return;
        }
        leaveParty(player.server, player.getUUID());
        leaveRoom(player.server, player.getUUID());
        queueByPlayer.remove(player.getUUID());

        party.members.add(player.getUUID());
        partyLeaderByMember.put(player.getUUID(), party.leaderId);
        status(player, "Joined party.");
        pushParty(player.server, party);
    }

    private void leaveParty(MinecraftServer server, UUID playerId) {
        UUID leader = partyLeaderByMember.get(playerId);
        if (leader == null) {
            return;
        }
        Party party = partyByLeader.get(leader);
        if (party == null) {
            partyLeaderByMember.remove(playerId);
            return;
        }
        party.members.remove(playerId);
        partyLeaderByMember.remove(playerId);
        if (party.members.isEmpty()) {
            partyByLeader.remove(leader);
            return;
        }
        if (leader.equals(playerId)) {
            UUID newLeader = party.members.iterator().next();
            partyByLeader.remove(leader);
            party.leaderId = newLeader;
            partyByLeader.put(newLeader, party);
            for (UUID memberId : party.members) {
                partyLeaderByMember.put(memberId, newLeader);
            }
        }
        pushParty(server, party);
    }

    private void pushParty(MinecraftServer server, Party party) {
        for (UUID memberId : party.members) {
            ServerPlayer online = server.getPlayerList().getPlayer(memberId);
            if (online != null) {
                push(online, false);
            }
        }
    }

    private void pushRoom(MinecraftServer server, Room room) {
        for (UUID memberId : room.members.keySet()) {
            ServerPlayer online = server.getPlayerList().getPlayer(memberId);
            if (online != null) {
                push(online, false);
            }
        }
    }

    private void startRoom(ServerPlayer host) {
        Room room = roomById.get(roomByPlayer.get(host.getUUID()));
        if (room == null || !room.hostId.equals(host.getUUID())) {
            status(host, "Only host can start room.", true);
            push(host, false);
            return;
        }
        if (room.mapName == null || room.mapName.isBlank()) {
            status(host, "Room map must be set before start.", true);
            push(host, false);
            return;
        }

        List<ServerPlayer> red = new ArrayList<>();
        List<ServerPlayer> blue = new ArrayList<>();
        int redCommanders = 0;
        int blueCommanders = 0;
        for (Map.Entry<UUID, RoomMember> entry : room.members.entrySet()) {
            ServerPlayer online = host.server.getPlayerList().getPlayer(entry.getKey());
            if (online == null) {
                status(host, "All room players must be online.", true);
                push(host, false);
                return;
            }
            RoomMember member = entry.getValue();
            if (!member.ready || member.camp == null) {
                status(host, "All room players must choose camp and be ready.", true);
                push(host, false);
                return;
            }
            if (member.camp == CohModeModels.Camp.RED) {
                red.add(online);
                if (member.role == CohModeModels.Role.COMMANDER) {
                    redCommanders++;
                }
            } else {
                blue.add(online);
                if (member.role == CohModeModels.Role.COMMANDER) {
                    blueCommanders++;
                }
            }
        }
        if (red.isEmpty() || blue.isEmpty()) {
            status(host, "Both camps need players.", true);
            push(host, false);
            return;
        }
        if (redCommanders > 1 || blueCommanders > 1) {
            status(host, "Each camp can have only one commander.", true);
            push(host, false);
            return;
        }

        int result = com.flowingsun.warpattern.match.SquadMatchService.INSTANCE.startMatchApi(
                host.server.createCommandSourceStack().withSuppressedOutput(),
                room.mapName,
                red,
                blue
        );
        if (result <= 0) {
            status(host, "Start room match failed.", true);
            push(host, false);
            return;
        }
        Set<UUID> members = Set.copyOf(room.members.keySet());
        roomById.remove(room.id);
        members.forEach(roomByPlayer::remove);
        members.forEach(queueByPlayer::remove);
        for (UUID memberId : members) {
            ServerPlayer online = host.server.getPlayerList().getPlayer(memberId);
            if (online != null) {
                status(online, "Room match started on map " + room.mapName + ".");
                push(online, false);
            }
        }
    }

    private void processRandomQueue(MinecraftServer server) {
        int minTeam = CohModeConfig.randomMinTeamSize();
        int maxTeam = CohModeConfig.randomMaxTeamSize();

        List<QueueEntry> redPool = queueByPlayer.values().stream()
                .filter(entry -> entry.camp == CohModeModels.Camp.RED)
                .sorted(Comparator.comparingLong(entry -> entry.joinedAt))
                .toList();
        List<QueueEntry> bluePool = queueByPlayer.values().stream()
                .filter(entry -> entry.camp == CohModeModels.Camp.BLUE)
                .sorted(Comparator.comparingLong(entry -> entry.joinedAt))
                .toList();
        if (redPool.size() < minTeam || bluePool.size() < minTeam) {
            return;
        }

        int teamSize = Math.min(maxTeam, Math.min(redPool.size(), bluePool.size()));
        List<QueueEntry> red = selectByParty(redPool, teamSize, null);
        if (red.size() < minTeam) {
            return;
        }
        double redAvgKd = red.stream().mapToDouble(entry -> entry.kd).average().orElse(1.0D);
        List<QueueEntry> blue = selectByParty(bluePool, red.size(), redAvgKd);
        if (blue.size() < minTeam) {
            return;
        }
        double blueAvgKd = blue.stream().mapToDouble(entry -> entry.kd).average().orElse(1.0D);
        red = selectByParty(redPool, blue.size(), blueAvgKd);
        if (red.size() < minTeam) {
            return;
        }

        normalizeCommander(red);
        normalizeCommander(blue);
        balanceRoles(red, blue);

        Optional<String> map = com.flowingsun.warpattern.match.SquadMatchService.INSTANCE.pickRecommendedMapForPlayers(teamSize * 2);
        if (map.isEmpty()) {
            return;
        }

        List<ServerPlayer> redPlayers = red.stream()
                .map(entry -> server.getPlayerList().getPlayer(entry.playerId))
                .filter(Objects::nonNull)
                .toList();
        List<ServerPlayer> bluePlayers = blue.stream()
                .map(entry -> server.getPlayerList().getPlayer(entry.playerId))
                .filter(Objects::nonNull)
                .toList();
        if (redPlayers.size() < minTeam || bluePlayers.size() < minTeam) {
            return;
        }

        int result = com.flowingsun.warpattern.match.SquadMatchService.INSTANCE.startMatchApi(
                server.createCommandSourceStack().withSuppressedOutput(),
                map.get(),
                redPlayers,
                bluePlayers
        );
        if (result <= 0) {
            return;
        }

        red.forEach(entry -> queueByPlayer.remove(entry.playerId));
        blue.forEach(entry -> queueByPlayer.remove(entry.playerId));
        for (ServerPlayer p : redPlayers) {
            status(p, "Random match started on map " + map.get() + ".");
            push(p, false);
        }
        for (ServerPlayer p : bluePlayers) {
            status(p, "Random match started on map " + map.get() + ".");
            push(p, false);
        }
    }

    private void normalizeCommander(List<QueueEntry> entries) {
        List<QueueEntry> commanders = entries.stream()
                .filter(entry -> entry.role == CohModeModels.Role.COMMANDER)
                .sorted(Comparator.comparingLong(entry -> entry.joinedAt))
                .toList();
        for (int i = 1; i < commanders.size(); i++) {
            commanders.get(i).role = CohModeModels.Role.RIFLEMAN;
        }
    }

    private void balanceRoles(List<QueueEntry> red, List<QueueEntry> blue) {
        for (CohModeModels.Role role : CohModeModels.Role.values()) {
            if (role == CohModeModels.Role.COMMANDER || role == CohModeModels.Role.RIFLEMAN) {
                continue;
            }
            int rc = (int) red.stream().filter(entry -> entry.role == role).count();
            int bc = (int) blue.stream().filter(entry -> entry.role == role).count();
            if (rc > bc) {
                demote(red, role, rc - bc);
            } else if (bc > rc) {
                demote(blue, role, bc - rc);
            }
        }
    }

    private void demote(List<QueueEntry> entries, CohModeModels.Role role, int extras) {
        List<QueueEntry> pool = entries.stream()
                .filter(entry -> entry.role == role)
                .sorted(Comparator.comparingLong((QueueEntry entry) -> entry.joinedAt).reversed())
                .toList();
        for (int i = 0; i < Math.min(extras, pool.size()); i++) {
            pool.get(i).role = CohModeModels.Role.RIFLEMAN;
        }
    }

    private List<QueueEntry> selectByParty(List<QueueEntry> pool, int targetSize, Double kdAnchor) {
        if (targetSize <= 0 || pool.isEmpty()) {
            return new ArrayList<>();
        }
        Map<UUID, List<QueueEntry>> grouped = new java.util.LinkedHashMap<>();
        for (QueueEntry entry : pool) {
            grouped.computeIfAbsent(entry.partyLeader, key -> new ArrayList<>()).add(entry);
        }
        List<PartyQueueGroup> groups = new ArrayList<>();
        for (Map.Entry<UUID, List<QueueEntry>> group : grouped.entrySet()) {
            List<QueueEntry> players = group.getValue().stream()
                    .sorted(Comparator.comparingLong(entry -> entry.joinedAt))
                    .toList();
            long joinedAt = players.stream().mapToLong(entry -> entry.joinedAt).min().orElse(System.currentTimeMillis());
            double avgKd = players.stream().mapToDouble(entry -> entry.kd).average().orElse(1.0D);
            groups.add(new PartyQueueGroup(players, joinedAt, avgKd));
        }
        Comparator<PartyQueueGroup> order = kdAnchor == null
                ? Comparator.comparingLong(group -> group.joinedAt)
                : Comparator.comparingDouble((PartyQueueGroup group) -> Math.abs(group.averageKd - kdAnchor))
                .thenComparingLong(group -> group.joinedAt);
        groups.sort(order);

        List<QueueEntry> out = new ArrayList<>();
        for (PartyQueueGroup group : groups) {
            if (out.size() + group.players.size() > targetSize) {
                continue;
            }
            out.addAll(group.players);
            if (out.size() == targetSize) {
                break;
            }
        }
        return out;
    }

    private String normalize(String name) {
        return Objects.requireNonNullElse(name, "").trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_");
    }

    private String nextRoomId() {
        for (int i = 0; i < 100; i++) {
            String id = "R" + Integer.toString(ThreadLocalRandom.current().nextInt(36 * 36 * 36), 36).toUpperCase(Locale.ROOT);
            if (!roomById.containsKey(id)) {
                return id;
            }
        }
        return "R" + Long.toString(System.currentTimeMillis(), 36).toUpperCase(Locale.ROOT);
    }

    private void cleanupInvites() {
        long now = System.currentTimeMillis();
        inviteById.values().removeIf(invite -> invite.expireAt < now);
    }

    private double globalKd(MinecraftServer server, UUID playerId) {
        double kd = VpPointsApi.globalCombatOf(server, playerId).map(view -> view.kd()).orElse(1.0D);
        if (!Double.isFinite(kd) || kd <= 0D) {
            return 1.0D;
        }
        return kd;
    }

    private CohModeModels.LobbyStateView buildState(ServerPlayer player) {
        CohModeModels.LobbyStateView state = new CohModeModels.LobbyStateView();
        PlayerProfile profile = profile(player);
        state.selfName = player.getGameProfile().getName();
        state.selectedCamp = profile.camp;
        state.selectedRole = profile.role;
        state.statusText = profile.status;
        state.serverTimeMillis = System.currentTimeMillis();

        QueueEntry selfQueue = queueByPlayer.get(player.getUUID());
        state.queue.queued = selfQueue != null;
        state.queue.joinedAtMillis = selfQueue == null ? 0L : selfQueue.joinedAt;
        state.queue.redQueued = (int) queueByPlayer.values().stream().filter(entry -> entry.camp == CohModeModels.Camp.RED).count();
        state.queue.blueQueued = (int) queueByPlayer.values().stream().filter(entry -> entry.camp == CohModeModels.Camp.BLUE).count();

        UUID leader = partyLeaderByMember.get(player.getUUID());
        if (leader != null) {
            Party party = partyByLeader.get(leader);
            if (party != null) {
                CohModeModels.PartyView pv = new CohModeModels.PartyView();
                pv.leader = leader.equals(player.getUUID());
                pv.leaderName = nameOf(player.server, leader);
                pv.maxSize = CohModeConfig.maxPartySize();
                for (UUID memberId : party.members) {
                    pv.members.add(nameOf(player.server, memberId));
                }
                state.party = pv;
            }
        }

        long now = System.currentTimeMillis();
        for (PendingInvite invite : inviteById.values()) {
            if (!invite.targetId.equals(player.getUUID()) || invite.expireAt < now) {
                continue;
            }
            CohModeModels.InviteView iv = new CohModeModels.InviteView();
            iv.id = invite.id;
            iv.kind = invite.kind;
            iv.fromName = nameOf(player.server, invite.fromId);
            iv.roomId = invite.roomId;
            iv.expireAtMillis = invite.expireAt;
            state.invites.add(iv);
        }
        state.invites.sort(Comparator.comparingLong(invite -> invite.expireAtMillis));

        String selfRoomId = roomByPlayer.get(player.getUUID());
        if (selfRoomId != null) {
            Room room = roomById.get(selfRoomId);
            if (room != null) {
                CohModeModels.RoomView rv = new CohModeModels.RoomView();
                rv.roomId = room.id;
                rv.hostName = nameOf(player.server, room.hostId);
                rv.mapName = room.mapName;
                rv.selfHost = room.hostId.equals(player.getUUID());
                int redCmd = 0;
                int blueCmd = 0;
                boolean allReady = true;
                boolean hasRed = false;
                boolean hasBlue = false;
                for (Map.Entry<UUID, RoomMember> member : room.members.entrySet()) {
                    CohModeModels.RoomMemberView mv = new CohModeModels.RoomMemberView();
                    mv.playerName = nameOf(player.server, member.getKey());
                    mv.camp = member.getValue().camp;
                    mv.role = member.getValue().role;
                    mv.ready = member.getValue().ready;
                    mv.host = room.hostId.equals(member.getKey());
                    rv.members.add(mv);
                    if (!mv.ready || mv.camp == null) {
                        allReady = false;
                    }
                    if (mv.camp == CohModeModels.Camp.RED) {
                        hasRed = true;
                        if (mv.role == CohModeModels.Role.COMMANDER) {
                            redCmd++;
                        }
                    } else if (mv.camp == CohModeModels.Camp.BLUE) {
                        hasBlue = true;
                        if (mv.role == CohModeModels.Role.COMMANDER) {
                            blueCmd++;
                        }
                    }
                }
                rv.members.sort(Comparator.comparing(member -> member.playerName, String.CASE_INSENSITIVE_ORDER));
                rv.canStart = allReady && hasRed && hasBlue && redCmd <= 1 && blueCmd <= 1 && rv.mapName != null;
                state.currentRoom = rv;
            }
        }

        for (Room room : roomById.values()) {
            CohModeModels.RoomListItemView rv = new CohModeModels.RoomListItemView();
            rv.roomId = room.id;
            rv.hostName = nameOf(player.server, room.hostId);
            rv.mapName = room.mapName;
            rv.members = room.members.size();
            rv.maxPlayers = CohModeConfig.roomMaxPlayers();
            state.rooms.add(rv);
        }
        state.rooms.sort(Comparator.comparing(room -> room.roomId, String.CASE_INSENSITIVE_ORDER));

        for (com.flowingsun.warpattern.match.SquadMatchService.MapMatchmakingView map : com.flowingsun.warpattern.match.SquadMatchService.INSTANCE.listMapMatchmakingViews()) {
            CohModeModels.MapView mv = new CohModeModels.MapView();
            mv.mapName = map.mapName();
            mv.recommendedMinPlayers = map.recommendedMinPlayers();
            mv.recommendedMaxPlayers = map.recommendedMaxPlayers();
            mv.ready = map.ready();
            state.maps.add(mv);
        }

        return state;
    }

    private static final class PlayerProfile {
        String lastName;
        CohModeModels.Camp camp;
        CohModeModels.Role role;
        String status;
    }

    private static final class QueueEntry {
        UUID playerId;
        UUID partyLeader;
        CohModeModels.Camp camp;
        CohModeModels.Role role;
        long joinedAt;
        double kd;
    }

    private record PartyQueueGroup(List<QueueEntry> players, long joinedAt, double averageKd) {
    }

    private static final class Party {
        UUID leaderId;
        Set<UUID> members = new java.util.LinkedHashSet<>();
    }

    private static final class Room {
        String id;
        UUID hostId;
        String mapName;
        Map<UUID, RoomMember> members = new HashMap<>();
    }

    private static final class RoomMember {
        CohModeModels.Camp camp;
        CohModeModels.Role role = CohModeModels.Role.RIFLEMAN;
        boolean ready;
    }

    private static final class PendingInvite {
        String id;
        CohModeModels.InviteKind kind;
        UUID fromId;
        UUID targetId;
        String roomId;
        long expireAt;
    }
}
