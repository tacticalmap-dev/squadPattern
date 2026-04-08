package com.flowingsun.squadpattern.integration;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.*;

public final class FtbTeamsCompat {
    public static final FtbTeamsCompat INSTANCE = new FtbTeamsCompat();
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Map<String, Set<UUID>> createdPartyTeamIdsByMap = new HashMap<>();

    public void syncMapTeams(String mapId, MinecraftServer server, String teamAName, Set<UUID> teamAPlayers, String teamBName, Set<UUID> teamBPlayers) {
        if (mapId == null || server == null) return;
        if (!isAvailableAndLoaded()) return;

        disbandMapTeams(mapId, server);
        Object manager = manager();
        if (manager == null) return;

        Set<UUID> createdIds = new HashSet<>();
        createAndFillParty(mapId, manager, server, teamAName, teamAPlayers, createdIds);
        createAndFillParty(mapId, manager, server, teamBName, teamBPlayers, createdIds);

        if (!createdIds.isEmpty()) {
            createdPartyTeamIdsByMap.put(mapId, createdIds);
            LOGGER.info("FTB Teams synced for map '{}': {} temporary parties", mapId, createdIds.size());
        }
    }

    public void disbandMapTeams(String mapId, MinecraftServer server) {
        if (mapId == null || server == null) return;
        if (!isAvailableAndLoaded()) return;

        Set<UUID> ids = createdPartyTeamIdsByMap.remove(mapId);
        if (ids == null || ids.isEmpty()) return;

        Object manager = manager();
        if (manager == null) return;

        for (UUID id : ids) {
            Object team = optionalValue(invokeOneArg(manager, "getTeamByID", id));
            if (team == null) continue;
            if (!bool(invokeNoArgs(team, "isPartyTeam"))) continue;

            if (!forceDisband(team, server)) {
                fallbackDisbandByLeavingAll(team);
            }
        }

        LOGGER.info("FTB Teams disbanded for map '{}': {} temporary parties", mapId, ids.size());
    }

    private void createAndFillParty(
            String mapId,
            Object manager,
            MinecraftServer server,
            String teamName,
            Set<UUID> teamPlayers,
            Set<UUID> createdIds
    ) {
        if (teamName == null || teamPlayers == null || teamPlayers.isEmpty()) {
            return;
        }

        ServerPlayer owner = pickOwner(server, manager, teamPlayers);
        if (owner == null) {
            return;
        }

        Object party = createPartyTeam(manager, owner, buildPartyName(mapId, teamName));
        if (party == null) {
            return;
        }

        UUID partyId = teamId(party);
        if (partyId != null) {
            createdIds.add(partyId);
        }

        for (UUID playerId : teamPlayers) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                continue;
            }
            movePlayerToParty(manager, party, player, server);
        }
    }

    private boolean isAvailableAndLoaded() {
        try {
            Class<?> apiClass = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI");
            Object api = apiClass.getMethod("api").invoke(null);
            if (api == null) return false;
            return bool(invokeNoArgs(api, "isManagerLoaded"));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private Object manager() {
        try {
            Class<?> apiClass = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI");
            Object api = apiClass.getMethod("api").invoke(null);
            return invokeNoArgs(api, "getManager");
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String buildPartyName(String mapId, String teamName) {
        String raw = mapId + "_" + teamName;
        return raw.replaceAll("[^a-zA-Z0-9_\\- ]", "_");
    }

    private ServerPlayer pickOwner(MinecraftServer server, Object manager, Set<UUID> members) {
        for (UUID id : members) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) continue;
            Object current = optionalValue(invokeOneArg(manager, "getTeamForPlayer", p));
            if (current != null && !bool(invokeNoArgs(current, "isPartyTeam"))) {
                return p;
            }
        }
        for (UUID id : members) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) return p;
        }
        return null;
    }

    private Object createPartyTeam(Object manager, ServerPlayer owner, String partyName) {
        try {
            Object current = optionalValue(invokeOneArg(manager, "getTeamForPlayer", owner));
            if (current != null && bool(invokeNoArgs(current, "isPartyTeam"))) {
                if (!leaveParty(current, owner.getUUID())) {
                    forceDisband(current, owner.server);
                }
            }
            return invokeWithFallback(manager, "createPartyTeam", owner, partyName, null, null);
        } catch (Throwable e) {
            LOGGER.warn("Failed to create FTB party '{}': {}", partyName, e.getMessage());
            return null;
        }
    }

    private void movePlayerToParty(Object manager, Object targetParty, ServerPlayer player, MinecraftServer server) {
        UUID playerId = player.getUUID();
        UUID targetId = teamId(targetParty);
        if (targetId == null) return;

        Object current = optionalValue(invokeOneArg(manager, "getTeamForPlayer", player));
        UUID currentId = teamId(current);
        if (targetId.equals(currentId)) return;

        if (current != null && bool(invokeNoArgs(current, "isPartyTeam"))) {
            leaveParty(current, playerId);
        }

        joinParty(targetParty, player);

        Object now = optionalValue(invokeOneArg(manager, "getTeamForPlayer", player));
        UUID nowId = teamId(now);
        if (!targetId.equals(nowId) && now != null && bool(invokeNoArgs(now, "isPartyTeam"))) {
            forceDisband(now, server);
            joinParty(targetParty, player);
        }
    }

    private boolean joinParty(Object party, ServerPlayer player) {
        try {
            Object res = invokeOneArg(party, "join", player);
            return res != null;
        } catch (Throwable e) {
            LOGGER.debug("FTB party join failed for {}: {}", player.getGameProfile().getName(), e.getMessage());
            return false;
        }
    }

    private boolean leaveParty(Object party, UUID playerId) {
        try {
            Object res = invokeOneArg(party, "leave", playerId);
            return res != null;
        } catch (Throwable e) {
            LOGGER.debug("FTB party leave failed for {}: {}", playerId, e.getMessage());
            return false;
        }
    }

    private boolean forceDisband(Object party, MinecraftServer server) {
        try {
            Object source = server.createCommandSourceStack().withSuppressedOutput();
            Object res = invokeOneArg(party, "forceDisband", source);
            return res != null;
        } catch (Throwable e) {
            return false;
        }
    }

    private void fallbackDisbandByLeavingAll(Object party) {
        Object membersObj = invokeNoArgs(party, "getMembers");
        if (!(membersObj instanceof Collection<?> members)) return;
        List<UUID> ids = new ArrayList<>();
        for (Object o : members) {
            if (o instanceof UUID u) ids.add(u);
        }
        for (UUID id : ids) {
            leaveParty(party, id);
        }
    }

    private UUID teamId(Object teamObj) {
        Object id = invokeNoArgs(teamObj, "getId");
        return id instanceof UUID u ? u : null;
    }

    private Object invokeNoArgs(Object target, String method) {
        if (target == null) return null;
        try {
            Method m = target.getClass().getMethod(method);
            return m.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Object invokeOneArg(Object target, String method, Object arg) {
        if (target == null || arg == null) return null;
        for (Method m : target.getClass().getMethods()) {
            if (!m.getName().equals(method) || m.getParameterCount() != 1) continue;
            if (!m.getParameterTypes()[0].isAssignableFrom(arg.getClass())) continue;
            try {
                return m.invoke(target, arg);
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private Object invokeWithFallback(Object target, String method, Object... args) {
        if (target == null) return null;
        for (Method m : target.getClass().getMethods()) {
            if (!m.getName().equals(method) || m.getParameterCount() != args.length) continue;
            Class<?>[] p = m.getParameterTypes();
            boolean ok = true;
            for (int i = 0; i < p.length; i++) {
                Object a = args[i];
                if (a == null) continue;
                if (!p[i].isAssignableFrom(a.getClass())) {
                    ok = false;
                    break;
                }
            }
            if (!ok) continue;
            try {
                return m.invoke(target, args);
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private Object optionalValue(Object maybeOptional) {
        if (maybeOptional instanceof Optional<?> opt) {
            return opt.orElse(null);
        }
        return maybeOptional;
    }

    private boolean bool(Object o) {
        return o instanceof Boolean b && b;
    }
}
