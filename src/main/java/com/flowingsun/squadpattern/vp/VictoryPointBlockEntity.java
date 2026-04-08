package com.flowingsun.squadpattern.vp;

import com.flowingsun.squadpattern.config.SquadConfig;
import com.flowingsun.squadpattern.match.SquadMatchService;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;

public class VictoryPointBlockEntity extends BlockEntity {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Set<VictoryPointBlockEntity> ACTIVE_POINTS = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final boolean DEBUG_CAPTURE_ACTIONBAR = true;
    private static final boolean DEBUG_CAPTURE_LOG = true;

    private String mapId;
    private String mapName;
    private String teamA;
    private String teamB;
    private int teamAColor = 0xFF4444;
    private int teamBColor = 0x4488FF;
    private CapturePointType pointType = CapturePointType.VICTORY;

    // signed progress: +1 fully owned by teamA, -1 fully owned by teamB
    private float progressSigned;
    private String ownerTeam;
    private boolean contested;
    private boolean capturing;

    public VictoryPointBlockEntity(BlockPos pos, BlockState state) {
        super(VictoryPointRuntime.BE_TYPE.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        pointType = resolveTypeFromState();
        ACTIVE_POINTS.add(this);
    }

    @Override
    public void setRemoved() {
        ACTIVE_POINTS.remove(this);
        super.setRemoved();
    }

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, VictoryPointBlockEntity be) {
        be.tickServer(level);
    }

    private void tickServer(ServerLevel level) {
        pointType = resolveTypeFromState();

        Optional<SquadMatchService.ActiveMatchView> matchOpt = SquadMatchService.INSTANCE.activeForLevel(level);
        if (matchOpt.isEmpty()) {
            contested = false;
            capturing = false;
            mapId = null;
            return;
        }

        SquadMatchService.ActiveMatchView match = matchOpt.get();
        bindContext(match);

        Map<String, Integer> countByTeam = new HashMap<>();
        ChunkPos cp = new ChunkPos(getBlockPos());
        int minX = cp.getMinBlockX();
        int minZ = cp.getMinBlockZ();
        int maxX = cp.getMaxBlockX() + 1;
        int maxZ = cp.getMaxBlockZ() + 1;
        AABB zone = new AABB(minX, level.getMinBuildHeight(), minZ, maxX, level.getMaxBuildHeight(), maxZ);

        // Count all non-spectator players (including creative) so capture works during admin testing.
        List<ServerPlayer> players = level.getEntitiesOfClass(ServerPlayer.class, zone, p -> !p.isSpectator());
        for (ServerPlayer player : players) {
            UUID pid = player.getUUID();
            String playerTeam = null;
            if (match.teamAPlayers().contains(pid)) {
                playerTeam = teamA;
            } else if (match.teamBPlayers().contains(pid)) {
                playerTeam = teamB;
            } else {
                // Fallback to runtime assignment map for compatibility with older states.
                playerTeam = SquadMatchService.INSTANCE.teamForPlayer(match.mapId(), pid);
            }
            if (playerTeam != null) {
                countByTeam.merge(playerTeam, 1, Integer::sum);
            }
        }

        int a = countByTeam.getOrDefault(teamA, 0);
        int b = countByTeam.getOrDefault(teamB, 0);
        int diff = a - b;
        float base = (float) (1.0D / SquadConfig.CAPTURE_SECONDS.get()) / 20.0F;
        float delta = 0F;
        int totalPlayers = a + b;

        if (diff == 0) {
            contested = totalPlayers > 0;
            // Equal presence means contested, not progressing capture ownership.
            capturing = false;
            if (!contested && Math.abs(progressSigned) < 1F) {
                if (progressSigned > 0) {
                    delta = -base;
                } else if (progressSigned < 0) {
                    delta = base;
                }
            }
        } else {
            contested = false;
            if (diff > 0) {
                capturing = totalPlayers > 0 && progressSigned < 1F;
            } else {
                capturing = totalPlayers > 0 && progressSigned > -1F;
            }
            delta = base * diff;
        }

        float prev = progressSigned;
        progressSigned = contested ? clamp(progressSigned + delta, -1F, 1F) : Math.abs(progressSigned) < Math.abs(delta) ? 0 : progressSigned + delta;

        if (progressSigned == 1F) {
            ownerTeam = teamA;
        } else if (progressSigned == -1F) {
            ownerTeam = teamB;
        } else if (progressSigned == 0F || progressSigned > 0 ^ prev > 0) {
            ownerTeam = null;
        }

        if (prev != progressSigned || delta != 0F) {
            setChanged();
            if (level.getGameTime() % 10L == 0L) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }

        if (DEBUG_CAPTURE_ACTIONBAR && level.getGameTime() % 20L == 0L && !players.isEmpty()) {
            String owner = ownerTeam == null ? "neutral" : ownerTeam;
            String dbg = "[VP-DBG] "
                    + pointType.name().toLowerCase(Locale.ROOT)
                    + " @" + getBlockPos().getX() + "," + getBlockPos().getY() + "," + getBlockPos().getZ()
                    + " red=" + a
                    + " blue=" + b
                    + " diff=" + diff
                    + " progress=" + String.format(Locale.ROOT, "%.3f", progressSigned)
                    + " owner=" + owner
                    + " contested=" + contested
                    + " capturing=" + capturing;
            Component line = Component.literal(dbg);
            for (ServerPlayer p : players) {
                p.displayClientMessage(line, true);
            }
            if (DEBUG_CAPTURE_LOG) {
                StringBuilder names = new StringBuilder();
                for (int i = 0; i < players.size(); i++) {
                    if (i > 0) {
                        names.append(",");
                    }
                    names.append(players.get(i).getGameProfile().getName());
                }
                LOGGER.info("{} players=[{}]", dbg, names);
            }
        }
    }

    private void bindContext(SquadMatchService.ActiveMatchView ctx) {
        this.mapId = ctx.mapId();
        this.mapName = ctx.mapName();
        this.teamA = ctx.teamA();
        this.teamB = ctx.teamB();
        this.teamAColor = ctx.colorA();
        this.teamBColor = ctx.colorB();
    }

    private CapturePointType resolveTypeFromState() {
        if (getBlockState().getBlock() instanceof VictoryPointBlock pointBlock) {
            return pointBlock.getPointType();
        }
        return CapturePointType.VICTORY;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    public String getMapId() {
        return mapId;
    }

    public String getOwnerTeam() {
        return ownerTeam;
    }

    public boolean isContested() {
        return contested;
    }

    public boolean isCapturing() {
        return capturing;
    }

    public float getProgressForHud(String teamAName, String teamBName) {
        if (teamAName == null || teamBName == null || teamA == null || teamB == null) {
            return 0F;
        }
        if (!teamA.equals(teamAName) || !teamB.equals(teamBName)) {
            return 0F;
        }
        return progressSigned;
    }

    public String getDisplayStatus() {
        if (contested) {
            return "Contested";
        }
        if (ownerTeam == null) {
            return "Neutral";
        }
        return "Owned";
    }

    public float getSignedProgress() {
        return progressSigned;
    }

    public int getTeamAColor() {
        return teamAColor;
    }

    public int getTeamBColor() {
        return teamBColor;
    }

    public String getTeamA() {
        return teamA;
    }

    public String getTeamB() {
        return teamB;
    }

    public CapturePointType getPointType() {
        return pointType;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (mapId != null) tag.putString("mapId", mapId);
        if (mapName != null) tag.putString("mapName", mapName);
        if (teamA != null) tag.putString("teamA", teamA);
        if (teamB != null) tag.putString("teamB", teamB);
        if (ownerTeam != null) tag.putString("ownerTeam", ownerTeam);
        tag.putInt("teamAColor", teamAColor);
        tag.putInt("teamBColor", teamBColor);
        tag.putFloat("progress", progressSigned);
        tag.putBoolean("contested", contested);
        tag.putBoolean("capturing", capturing);
        tag.putString("pointType", pointType.name());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        mapId = tag.contains("mapId") ? tag.getString("mapId") : null;
        mapName = tag.contains("mapName") ? tag.getString("mapName") : null;
        teamA = tag.contains("teamA") ? tag.getString("teamA") : null;
        teamB = tag.contains("teamB") ? tag.getString("teamB") : null;
        ownerTeam = tag.contains("ownerTeam") ? tag.getString("ownerTeam") : null;
        teamAColor = tag.getInt("teamAColor");
        teamBColor = tag.getInt("teamBColor");
        progressSigned = tag.getFloat("progress");
        contested = tag.getBoolean("contested");
        capturing = tag.getBoolean("capturing");
        if (tag.contains("pointType")) {
            try {
                pointType = CapturePointType.valueOf(tag.getString("pointType"));
            } catch (Exception ignored) {
                pointType = CapturePointType.VICTORY;
            }
        } else {
            pointType = CapturePointType.VICTORY;
        }
    }
}
