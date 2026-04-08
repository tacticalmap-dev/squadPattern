package com.flowingsun.squadpattern.vp;

import com.flowingsun.squadpattern.config.SquadConfig;
import com.flowingsun.squadpattern.match.SquadMatchService;
import com.flowingsun.squadpattern.net.MatchHudSyncS2C;
import com.flowingsun.squadpattern.net.SquadNetwork;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class VictoryMatchManager {
    public static final VictoryMatchManager INSTANCE = new VictoryMatchManager();

    public record TeamSide(String name, int color) {
    }

    public static final class PointSnapshot {
        public long pos;
        public float progress;
        public String ownerTeam;
        public boolean contested;
        public boolean capturing;
    }

    public static final class MatchState {
        public String mapId;
        public String mapName;
        public ResourceLocation worldId;
        public TeamSide teamA;
        public TeamSide teamB;
        public float pointsA;
        public float pointsB;
        public int ammoA;
        public int oilA;
        public int ammoB;
        public int oilB;
        public final List<PointSnapshot> points = new ArrayList<>();
    }

    private final Map<String, MatchState> states = new ConcurrentHashMap<>();

    private VictoryMatchManager() {
    }

    public void startMatch(SquadMatchService.ActiveMatchView ctx) {
        MatchState s = new MatchState();
        s.mapId = ctx.mapId();
        s.mapName = ctx.mapName();
        s.worldId = ctx.worldId();
        s.teamA = new TeamSide(ctx.teamA(), ctx.colorA());
        s.teamB = new TeamSide(ctx.teamB(), ctx.colorB());
        s.pointsA = SquadConfig.INITIAL_VICTORY_POINTS.get();
        s.pointsB = SquadConfig.INITIAL_VICTORY_POINTS.get();
        s.ammoA = SquadConfig.INITIAL_AMMO.get();
        s.ammoB = SquadConfig.INITIAL_AMMO.get();
        s.oilA = SquadConfig.INITIAL_OIL.get();
        s.oilB = SquadConfig.INITIAL_OIL.get();
        states.put(s.mapId, s);
    }

    public void resetMap(String mapId) {
        states.remove(mapId);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer() == null) {
            return;
        }
        drainByOwnedVictoryPoints(event.getServer());
        int tickCount = event.getServer().getTickCount();
        produceTeamResources(tickCount);
        if (tickCount % 20L == 0L) {
            syncHud(event.getServer());
        }
    }

    private void drainByOwnedVictoryPoints(MinecraftServer server) {
        Map<String, Integer> ownedByA = new HashMap<>();
        Map<String, Integer> ownedByB = new HashMap<>();

        for (VictoryPointBlockEntity point : VictoryPointBlockEntity.ACTIVE_POINTS) {
            if (point.isRemoved() || point.getLevel() == null || point.getMapId() == null) {
                continue;
            }
            if (point.getPointType() != CapturePointType.VICTORY) {
                continue;
            }
            MatchState st = states.get(point.getMapId());
            if (st == null) {
                continue;
            }
            if (st.teamA.name.equals(point.getOwnerTeam())) {
                ownedByA.merge(st.mapId, 1, Integer::sum);
            } else if (st.teamB.name.equals(point.getOwnerTeam())) {
                ownedByB.merge(st.mapId, 1, Integer::sum);
            }
        }

        float perTick = (float) (SquadConfig.DRAIN_PER_MINUTE.get() / 1200.0D);
        for (MatchState s : states.values()) {
            int a = ownedByA.getOrDefault(s.mapId, 0);
            int b = ownedByB.getOrDefault(s.mapId, 0);
            if (a > 0) {
                s.pointsB -= perTick * a;
            }
            if (b > 0) {
                s.pointsA -= perTick * b;
            }
            if (s.pointsA <= 0F || s.pointsB <= 0F) {
                SquadMatchService.INSTANCE.finishByTickets(s.mapId, server);
            }
        }
    }

    private void produceTeamResources(int tickCount) {
        int cycleTicks = Math.max(1, SquadConfig.RESOURCE_CYCLE_SECONDS.get() * 20);
        if (tickCount % cycleTicks != 0L) {
            return;
        }
        for (VictoryPointBlockEntity point : VictoryPointBlockEntity.ACTIVE_POINTS) {
            if (point.isRemoved() || point.getLevel() == null || point.getMapId() == null || point.getOwnerTeam() == null) {
                continue;
            }
            MatchState st = states.get(point.getMapId());
            if (st == null) {
                continue;
            }
            CapturePointType type = point.getPointType();
            int ammoGain = type.ammoPerCycle();
            int oilGain = type.oilPerCycle();
            if (ammoGain <= 0 && oilGain <= 0) {
                continue;
            }
            if (st.teamA.name.equals(point.getOwnerTeam())) {
                st.ammoA += ammoGain;
                st.oilA += oilGain;
            } else if (st.teamB.name.equals(point.getOwnerTeam())) {
                st.ammoB += ammoGain;
                st.oilB += oilGain;
            }
        }
    }

    private void syncHud(MinecraftServer server) {
        Map<String, List<VictoryPointBlockEntity>> pointsByMap = new HashMap<>();
        Map<ResourceLocation, List<VictoryPointBlockEntity>> pointsByWorld = new HashMap<>();
        for (VictoryPointBlockEntity point : VictoryPointBlockEntity.ACTIVE_POINTS) {
            if (point.isRemoved() || point.getLevel() == null) {
                continue;
            }
            if (point.getMapId() != null) {
                pointsByMap.computeIfAbsent(point.getMapId(), k -> new ArrayList<>()).add(point);
            }
            pointsByWorld.computeIfAbsent(point.getLevel().dimension().location(), k -> new ArrayList<>()).add(point);
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Optional<SquadMatchService.PlayerMatchContext> c = SquadMatchService.INSTANCE.contextFor(player);
            if (c.isEmpty()) {
                continue;
            }
            MatchState st = states.get(c.get().mapId());
            if (st == null) {
                continue;
            }
            st.points.clear();
            List<VictoryPointBlockEntity> orderedPoints = new ArrayList<>(pointsByMap.getOrDefault(st.mapId, List.of()));
            if (orderedPoints.isEmpty() && st.worldId != null) {
                orderedPoints.addAll(pointsByWorld.getOrDefault(st.worldId, List.of()));
            }
            orderedPoints.sort(Comparator.comparingLong(be -> be.getBlockPos().asLong()));
            for (VictoryPointBlockEntity be : orderedPoints) {
                if (!be.getPointType().showInTopHud()) {
                    continue;
                }
                PointSnapshot ps = new PointSnapshot();
                ps.pos = be.getBlockPos().asLong();
                ps.progress = be.getProgressForHud(st.teamA.name, st.teamB.name);
                ps.ownerTeam = be.getOwnerTeam();
                ps.contested = be.isContested();
                ps.capturing = be.isCapturing();
                st.points.add(ps);
            }
            SquadNetwork.sendTo(player, new MatchHudSyncS2C(
                    st.mapId,
                    st.teamA.name, st.teamA.color, Math.max(0, st.pointsA), st.ammoA, st.oilA,
                    st.teamB.name, st.teamB.color, Math.max(0, st.pointsB), st.ammoB, st.oilB,
                    st.points
            ));
        }
    }
}
