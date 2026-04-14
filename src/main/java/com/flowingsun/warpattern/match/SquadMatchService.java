package com.flowingsun.warpattern.match;

import com.flowingsun.vppoints.api.VpPointsApi;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Core match orchestration service:
 * map presets, runtime world instances, player assignment, teleports, and lifecycle commands.
 */
public final class SquadMatchService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAPS_TYPE = new TypeToken<Map<String, MapPreset>>() {
    }.getType();
    private static final int TEAM_A_COLOR = 0xFF4444;
    private static final int TEAM_B_COLOR = 0x4488FF;
    // Teleport verification tuned to reduce redundant correction teleports (and packet churn) on unstable connections.
    private static final double TELEPORT_SETTLED_DISTANCE_SQR = 16.0D; // 4 blocks radius.
    private static final int TELEPORT_VERIFY_MAX_ATTEMPTS = 2;
    private static final long TELEPORT_VERIFY_INITIAL_DELAY_TICKS = 2L;
    private static final long TELEPORT_VERIFY_RETRY_DELAY_TICKS = 20L;

    public static final SquadMatchService INSTANCE = new SquadMatchService();

    private final Path storageFile;
    private final Path presetRoot;

    private final Map<String, MapPreset> mapPresets = new HashMap<>();
    // mapId -> running match instance (each instance has its own runtime world).
    private final Map<String, ActiveMatch> activeMatches = new ConcurrentHashMap<>();
    // player -> assigned running match/team.
    private final Map<UUID, PlayerAssignment> playerAssignments = new ConcurrentHashMap<>();
    // runtime world dimension -> running mapId.
    private final Map<ResourceLocation, String> worldToMapId = new ConcurrentHashMap<>();
    private final Map<UUID, String> disconnectedMatchByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, PendingTeleport> pendingTeleports = new ConcurrentHashMap<>();
    // Monotonic counter for runtime world id generation.
    private long runtimeMatchCounter;
    private long maintenanceTick;

    private SquadMatchService() {
        Path dir = FMLPaths.CONFIGDIR.get().resolve("warpattern");
        this.storageFile = dir.resolve("maps.json");
        this.presetRoot = FMLPaths.GAMEDIR.get().resolve("bakmap");
        loadMaps();
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("warpattern")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("map")
                        .then(Commands.literal("delete")
                                .then(Commands.argument("mapName", StringArgumentType.word()).suggests(this::suggestMapNames)
                                        .executes(ctx -> deleteMap(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "mapName")
                                        ))))
                        .then(Commands.literal("import")
                                .then(Commands.argument("mapName", StringArgumentType.word())
                                        .then(Commands.argument("worldName", StringArgumentType.word())
                                                .executes(ctx -> importMap(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "mapName"),
                                                        StringArgumentType.getString(ctx, "worldName")
                                                )))))
                        .then(Commands.argument("mapName", StringArgumentType.word())
                                .suggests(this::suggestMapNames)
                                .then(Commands.literal("bindhere")
                                        .executes(ctx -> bindMapToCurrentWorld(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "mapName")
                                        )))
                                .then(Commands.literal("spawn")
                                        .then(Commands.literal("red")
                                                .then(Commands.literal("set")
                                                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                                                .executes(ctx -> setSpawn(
                                                                                        ctx.getSource(),
                                                                                        StringArgumentType.getString(ctx, "mapName"),
                                                                                        "red",
                                                                                        IntegerArgumentType.getInteger(ctx, "x"),
                                                                                        IntegerArgumentType.getInteger(ctx, "y"),
                                                                                        IntegerArgumentType.getInteger(ctx, "z")
                                                                                )))))))
                                        .then(Commands.literal("blue")
                                                .then(Commands.literal("set")
                                                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                                                .executes(ctx -> setSpawn(
                                                                                        ctx.getSource(),
                                                                                        StringArgumentType.getString(ctx, "mapName"),
                                                                                        "blue",
                                                                                        IntegerArgumentType.getInteger(ctx, "x"),
                                                                                        IntegerArgumentType.getInteger(ctx, "y"),
                                                                                        IntegerArgumentType.getInteger(ctx, "z")
                                                                                ))))))))
                                .then(Commands.literal("bounds")
                                        .then(Commands.literal("set")
                                                .then(Commands.argument("x1", IntegerArgumentType.integer())
                                                        .then(Commands.argument("y1", IntegerArgumentType.integer())
                                                                .then(Commands.argument("z1", IntegerArgumentType.integer())
                                                                        .then(Commands.argument("x2", IntegerArgumentType.integer())
                                                                                .then(Commands.argument("y2", IntegerArgumentType.integer())
                                                                                        .then(Commands.argument("z2", IntegerArgumentType.integer())
                                                                                                .executes(ctx -> setBoundsByCoords(
                                                                                                        ctx.getSource(),
                                                                                                        StringArgumentType.getString(ctx, "mapName"),
                                                                                                        IntegerArgumentType.getInteger(ctx, "x1"),
                                                                                                        IntegerArgumentType.getInteger(ctx, "y1"),
                                                                                                        IntegerArgumentType.getInteger(ctx, "z1"),
                                                                                                        IntegerArgumentType.getInteger(ctx, "x2"),
                                                                                                        IntegerArgumentType.getInteger(ctx, "y2"),
                                                                                                        IntegerArgumentType.getInteger(ctx, "z2")
                                                                                                ))))))))))
                                .then(Commands.literal("save")
                                        .executes(ctx -> savePreset(ctx.getSource(), StringArgumentType.getString(ctx, "mapName"))))
                                .then(Commands.literal("recommend")
                                        .then(Commands.argument("minPlayers", IntegerArgumentType.integer(1))
                                                .then(Commands.argument("maxPlayers", IntegerArgumentType.integer(1))
                                                        .executes(ctx -> setRecommendedPlayers(
                                                                ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "mapName"),
                                                                IntegerArgumentType.getInteger(ctx, "minPlayers"),
                                                                IntegerArgumentType.getInteger(ctx, "maxPlayers")
                                                        )))))
                                .then(Commands.literal("preset")
                                        .then(Commands.literal("remake")
                                                .executes(ctx -> remakePreset(ctx.getSource(), StringArgumentType.getString(ctx, "mapName")))))))
                .then(Commands.literal("preset")
                        .then(Commands.literal("remake")
                                .then(Commands.argument("mapName", StringArgumentType.word()).suggests(this::suggestMapNames)
                                        .executes(ctx -> remakePreset(ctx.getSource(), StringArgumentType.getString(ctx, "mapName"))))))
                .then(Commands.literal("match")
                        .then(Commands.literal("start")
                                .then(Commands.argument("mapName", StringArgumentType.word()).suggests(this::suggestMapNames)
                                        .then(Commands.argument("redPlayers", EntityArgument.players())
                                                .then(Commands.argument("bluePlayers", EntityArgument.players())
                                                        .executes(ctx -> startMatch(
                                                                ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "mapName"),
                                                                EntityArgument.getPlayers(ctx, "redPlayers"),
                                                                EntityArgument.getPlayers(ctx, "bluePlayers")
                                                        ))))))
                        .then(Commands.literal("end")
                                .then(Commands.argument("mapName", StringArgumentType.word()).suggests(this::suggestMapNames)
                                        .executes(ctx -> endMatchByMapName(ctx.getSource(), StringArgumentType.getString(ctx, "mapName"))))));

        LiteralArgumentBuilder<CommandSourceStack> game = Commands.literal("game")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("start")
                        .then(Commands.argument("mapName", StringArgumentType.word()).suggests(this::suggestMapNames)
                                .executes(ctx -> startMatchFromGameCommand(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "mapName"),
                                        null,
                                        null
                                ))
                                .then(Commands.argument("redPlayers", EntityArgument.players())
                                        .executes(ctx -> startMatchFromGameCommand(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "mapName"),
                                                EntityArgument.getPlayers(ctx, "redPlayers"),
                                                null
                                        ))
                                        .then(Commands.argument("bluePlayers", EntityArgument.players())
                                                .executes(ctx -> startMatchFromGameCommand(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "mapName"),
                                                        EntityArgument.getPlayers(ctx, "redPlayers"),
                                                        EntityArgument.getPlayers(ctx, "bluePlayers")
                                                ))))))
                .then(Commands.literal("end")
                        .then(Commands.argument("mapName", StringArgumentType.word()).suggests(this::suggestMapNames)
                                .executes(ctx -> endMatchByMapName(ctx.getSource(), StringArgumentType.getString(ctx, "mapName")))));

        event.getDispatcher().register(root);
        event.getDispatcher().register(game);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        recoverStaleRuntimeState(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        MinecraftServer server = event.getServer();
        for (ActiveMatch match : new ArrayList<>(activeMatches.values())) {
            VpPointsApi.endMatch(match.mapId, server, "server-stopping");
            if (match.runtimeWorld) {
                destroyRuntimeMatchWorld(server, match.worldId);
            }
        }
        activeMatches.clear();
        playerAssignments.clear();
        worldToMapId.clear();
        disconnectedMatchByPlayer.clear();
        pendingTeleports.clear();
        maintenanceTick = 0L;
    }

    private void recoverStaleRuntimeState(MinecraftServer server) {
        if (server == null) {
            return;
        }

        int staleMatches = activeMatches.size();
        int staleAssignments = playerAssignments.size();
        int staleWorldMappings = worldToMapId.size();
        int staleDisconnected = disconnectedMatchByPlayer.size();
        int stalePendingTeleports = pendingTeleports.size();

        if (staleMatches <= 0
                && staleAssignments <= 0
                && staleWorldMappings <= 0
                && staleDisconnected <= 0
                && stalePendingTeleports <= 0) {
            return;
        }

        LOGGER.warn(
                "Detected stale runtime match state on server start. Recovering: matches={}, assignments={}, worldMappings={}, disconnected={}, pendingTeleports={}",
                staleMatches, staleAssignments, staleWorldMappings, staleDisconnected, stalePendingTeleports
        );

        for (ActiveMatch match : new ArrayList<>(activeMatches.values())) {
            try {
                endMatchInternal(match, server, false, "startup-recovery");
            } catch (Exception ex) {
                LOGGER.warn("Failed to recover stale match '{}'", match.mapId, ex);
            }
        }

        // Safety net for any partial recovery (orphan assignments/map-links with no active match object).
        activeMatches.clear();
        playerAssignments.clear();
        worldToMapId.clear();
        disconnectedMatchByPlayer.clear();
        pendingTeleports.clear();
        maintenanceTick = 0L;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer() == null) {
            return;
        }
        processPendingTeleports(event.getServer());
        maintenanceTick++;
        if (maintenanceTick % 40L != 0L) {
            return;
        }
        cleanupMapsForDeletedWorlds(event.getServer());
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PlayerAssignment assignment = playerAssignments.get(player.getUUID());
        if (assignment == null) {
            return;
        }
        if (activeMatches.containsKey(assignment.mapId())) {
            disconnectedMatchByPlayer.put(player.getUUID(), assignment.mapId());
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        handleReconnect(player);
    }

    private CompletableFuture<Suggestions> suggestMapNames(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(mapPresets.keySet(), builder);
    }

    /**
     * Host-facing API: starts one match and includes pre-match world preparation + team teleports.
     */
    public int startMatchApi(
            CommandSourceStack source,
            String mapName,
            Collection<ServerPlayer> redPlayers,
            Collection<ServerPlayer> bluePlayers
    ) {
        return startMatch(source, mapName, redPlayers, bluePlayers);
    }

    /**
     * Host-facing API: ends active matches by map name; post-match restoration/teleport stays in this mod.
     */
    public int endMatchByMapNameApi(CommandSourceStack source, String mapName) {
        return endMatchByMapName(source, mapName);
    }

    /**
     * Host-facing API: applies reconnect workflow (including in-match return teleport when applicable).
     */
    public void handleReconnectApi(ServerPlayer player) {
        handleReconnect(player);
    }

    public record MapMatchmakingView(String mapName, int recommendedMinPlayers, int recommendedMaxPlayers, boolean ready) {
    }

    public synchronized List<MapMatchmakingView> listMapMatchmakingViews() {
        List<MapMatchmakingView> out = new ArrayList<>();
        for (Map.Entry<String, MapPreset> entry : mapPresets.entrySet()) {
            MapPreset preset = entry.getValue();
            normalizeMapPreset(preset);
            boolean ready = preset.saved && validatePresetBeforeUse(preset) == null;
            out.add(new MapMatchmakingView(entry.getKey(), preset.recommendedMinPlayers, preset.recommendedMaxPlayers, ready));
        }
        out.sort(Comparator.comparing(MapMatchmakingView::mapName, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    public synchronized Optional<String> pickRecommendedMapForPlayers(int totalPlayers) {
        List<MapMatchmakingView> candidates = listMapMatchmakingViews()
                .stream()
                .filter(MapMatchmakingView::ready)
                .filter(v -> totalPlayers >= v.recommendedMinPlayers() && totalPlayers <= v.recommendedMaxPlayers())
                .toList();
        if (!candidates.isEmpty()) {
            return Optional.of(candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())).mapName());
        }
        List<MapMatchmakingView> fallbacks = listMapMatchmakingViews()
                .stream()
                .filter(MapMatchmakingView::ready)
                .toList();
        if (fallbacks.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(fallbacks.get(ThreadLocalRandom.current().nextInt(fallbacks.size())).mapName());
    }

    public void onVpPointsMatchFinished(com.flowingsun.vppoints.match.SquadMatchService.MatchFinished result, MinecraftServer server) {
        if (result == null || server == null) {
            return;
        }
        ActiveMatch match = activeMatches.get(result.mapId());
        if (match == null) {
            return;
        }
        endMatchInternal(match, server, true, result.reason());
    }

    private int startMatchFromGameCommand(
            CommandSourceStack source,
            String rawMapName,
            Collection<ServerPlayer> redPlayersArg,
            Collection<ServerPlayer> bluePlayersArg
    ) {
        MinecraftServer server = source.getServer();
        Collection<ServerPlayer> redPlayers = redPlayersArg;
        Collection<ServerPlayer> bluePlayers = bluePlayersArg;

        if (redPlayers == null && bluePlayers == null) {
            List<ServerPlayer> candidates = onlineMatchCandidates(server);
            candidates.sort(Comparator.comparing(p -> p.getGameProfile().getName(), String.CASE_INSENSITIVE_ORDER));
            List<ServerPlayer> red = new ArrayList<>();
            List<ServerPlayer> blue = new ArrayList<>();
            for (int i = 0; i < candidates.size(); i++) {
                if ((i & 1) == 0) {
                    red.add(candidates.get(i));
                } else {
                    blue.add(candidates.get(i));
                }
            }
            redPlayers = red;
            bluePlayers = blue;
        } else if (redPlayers != null && bluePlayers == null) {
            Set<UUID> redIds = new HashSet<>();
            for (ServerPlayer p : redPlayers) {
                redIds.add(p.getUUID());
            }
            List<ServerPlayer> blue = new ArrayList<>();
            for (ServerPlayer candidate : onlineMatchCandidates(server)) {
                if (!redIds.contains(candidate.getUUID())) {
                    blue.add(candidate);
                }
            }
            bluePlayers = blue;
        } else if (redPlayers == null) {
            Set<UUID> blueIds = new HashSet<>();
            for (ServerPlayer p : bluePlayers) {
                blueIds.add(p.getUUID());
            }
            List<ServerPlayer> red = new ArrayList<>();
            for (ServerPlayer candidate : onlineMatchCandidates(server)) {
                if (!blueIds.contains(candidate.getUUID())) {
                    red.add(candidate);
                }
            }
            redPlayers = red;
        }

        return startMatch(source, rawMapName, redPlayers, bluePlayers);
    }

    private int importMap(CommandSourceStack source, String rawMapName, String rawWorldName) {
        MinecraftServer server = source.getServer();
        String mapName = normalizeMapName(rawMapName);
        ResourceLocation worldId = parseWorldId(rawWorldName);
        if (!isSupportedMapWorld(worldId)) {
            source.sendFailure(Component.literal("Unsupported map world: " + worldId + ". Use a Multiworld dimension id."));
            return 0;
        }
        ensureWorld(server, worldId);

        MapPreset preset = mapPresets.computeIfAbsent(mapName, k -> new MapPreset());
        normalizeMapPreset(preset);
        ResourceLocation oldWorld = worldIdOf(preset);
        preset.mapName = mapName;
        preset.worldId = worldId.toString();
        if (oldWorld != null && !oldWorld.equals(worldId)) {
            preset.redSpawn = null;
            preset.blueSpawn = null;
            preset.bound1 = null;
            preset.bound2 = null;
        }
        preset.saved = false;
        saveMaps();

        source.sendSuccess(() -> Component.literal("Map imported: " + mapName + " -> " + worldId), true);
        return Command.SINGLE_SUCCESS;
    }

    private int bindMapToCurrentWorld(CommandSourceStack source, String rawMapName) {
        String mapName = normalizeMapName(rawMapName);
        ResourceLocation worldId = source.getLevel().dimension().location();
        if (!isSupportedMapWorld(worldId)) {
            source.sendFailure(Component.literal("Current world is not a Multiworld map world: " + worldId + "."));
            return 0;
        }

        MapPreset preset = mapPresets.computeIfAbsent(mapName, k -> new MapPreset());
        normalizeMapPreset(preset);
        ResourceLocation oldWorld = worldIdOf(preset);
        preset.mapName = mapName;
        preset.worldId = worldId.toString();
        if (oldWorld != null && !oldWorld.equals(worldId)) {
            preset.redSpawn = null;
            preset.blueSpawn = null;
            preset.bound1 = null;
            preset.bound2 = null;
        }
        preset.saved = false;
        saveMaps();

        source.sendSuccess(() -> Component.literal("Map bound: " + mapName + " -> " + worldId), true);
        return Command.SINGLE_SUCCESS;
    }

    private int setSpawn(CommandSourceStack source, String rawMapName, String side, int x, int y, int z) {
        String mapName = normalizeMapName(rawMapName);
        MapPreset preset = mapPresets.get(mapName);
        if (preset == null) {
            source.sendFailure(Component.literal("Unknown map: " + mapName + ". Use /warpattern map import first."));
            return 0;
        }

        ResourceLocation boundWorld = worldIdOf(preset);
        if (boundWorld == null) {
            source.sendFailure(Component.literal("Map has invalid world binding."));
            return 0;
        }

        SpawnPoint sp = new SpawnPoint();
        sp.dimension = boundWorld.toString();
        sp.x = x + 0.5D;
        sp.y = y;
        sp.z = z + 0.5D;
        sp.yaw = source.getRotation().y;
        sp.pitch = source.getRotation().x;

        if ("red".equals(side)) {
            preset.redSpawn = sp;
        } else {
            preset.blueSpawn = sp;
        }
        preset.saved = false;
        saveMaps();

        source.sendSuccess(() -> Component.literal("Spawn set: " + mapName + " " + side + " -> (" + x + "," + y + "," + z + ")"), true);
        return Command.SINGLE_SUCCESS;
    }

    private int setBoundsByCoords(CommandSourceStack source, String rawMapName, int x1, int y1, int z1, int x2, int y2, int z2) {
        String mapName = normalizeMapName(rawMapName);
        MapPreset preset = mapPresets.get(mapName);
        if (preset == null) {
            source.sendFailure(Component.literal("Unknown map: " + mapName + ". Use /warpattern map import first."));
            return 0;
        }

        ResourceLocation boundWorld = worldIdOf(preset);
        if (boundWorld == null) {
            source.sendFailure(Component.literal("Map has invalid world binding."));
            return 0;
        }

        BoundPoint p1 = new BoundPoint();
        p1.dimension = boundWorld.toString();
        p1.x = x1;
        p1.y = y1;
        p1.z = z1;
        BoundPoint p2 = new BoundPoint();
        p2.dimension = boundWorld.toString();
        p2.x = x2;
        p2.y = y2;
        p2.z = z2;
        normalizeBoundsToSquare(p1, p2);
        preset.bound1 = p1;
        preset.bound2 = p2;
        preset.saved = false;
        saveMaps();

        source.sendSuccess(() -> Component.literal(
                "Bounds set (square) for map '" + mapName + "': (" + p1.x + "," + p1.y + "," + p1.z + ") -> (" + p2.x + "," + p2.y + "," + p2.z + ")"
        ), true);
        return Command.SINGLE_SUCCESS;
    }

    private int setRecommendedPlayers(CommandSourceStack source, String rawMapName, int minPlayers, int maxPlayers) {
        String mapName = normalizeMapName(rawMapName);
        MapPreset preset = mapPresets.get(mapName);
        if (preset == null) {
            source.sendFailure(Component.literal("Unknown map: " + mapName + ". Use /warpattern map import first."));
            return 0;
        }
        if (maxPlayers < minPlayers) {
            source.sendFailure(Component.literal("maxPlayers must be >= minPlayers."));
            return 0;
        }
        preset.recommendedMinPlayers = minPlayers;
        preset.recommendedMaxPlayers = maxPlayers;
        saveMaps();

        source.sendSuccess(() -> Component.literal(
                "Recommended players for map '" + mapName + "': " + minPlayers + "-" + maxPlayers
        ), true);
        return Command.SINGLE_SUCCESS;
    }

    private int savePreset(CommandSourceStack source, String rawMapName) {
        String mapName = normalizeMapName(rawMapName);
        MapPreset preset = mapPresets.get(mapName);
        if (preset == null) {
            source.sendFailure(Component.literal("Unknown map: " + mapName + ". Use /warpattern map import first."));
            return 0;
        }

        MinecraftServer server = source.getServer();
        String validateError = validatePresetBeforeUse(preset);
        if (validateError != null) {
            source.sendFailure(Component.literal("Map save check failed: " + validateError));
            return 0;
        }

        ResourceLocation worldId = resolveWorldBindingForRuntime(source, source.getServer(), preset, mapName);
        if (worldId == null) {
            source.sendFailure(Component.literal("Map has invalid world binding."));
            return 0;
        }

        ensureWorld(server, worldId);
        Path worldDir = existingWorldDirectory(server, worldId);
        Path backupDir = backupDirectory(worldId);
        if (!Files.exists(worldDir)) {
            unloadWorld(server, worldId);
            worldDir = existingWorldDirectory(server, worldId);
        }
        if (!Files.exists(worldDir)) {
            source.sendFailure(Component.literal("World folder not found: " + worldDirectory(server, worldId) + " (legacy: " + legacyWorldDirectory(server, worldId) + ")"));
            return 0;
        }

        deleteDirectory(backupDir);
        copyDirectory(worldDir, backupDir);
        preset.saved = true;
        saveMaps();

        source.sendSuccess(() -> Component.literal("Map '" + mapName + "' saved and locked for match start."), true);
        return Command.SINGLE_SUCCESS;
    }

    private int remakePreset(CommandSourceStack source, String rawMapName) {
        String mapName = normalizeMapName(rawMapName);
        MapPreset preset = mapPresets.get(mapName);
        if (preset == null) {
            source.sendFailure(Component.literal("Unknown map: " + mapName));
            return 0;
        }

        MinecraftServer server = source.getServer();
        ResourceLocation worldId = resolveWorldBindingForRuntime(source, source.getServer(), preset, mapName);
        if (worldId == null) {
            source.sendFailure(Component.literal("Map has invalid world binding."));
            return 0;
        }
        if (preset.bound1 != null && preset.bound2 != null
                && (!worldId.toString().equals(preset.bound1.dimension) || !worldId.toString().equals(preset.bound2.dimension))) {
            source.sendFailure(Component.literal("Bounds must be set in the bound map world: " + worldId));
            return 0;
        }

        ensureWorld(server, worldId);
        Path worldDir = existingWorldDirectory(server, worldId);
        Path backupDir = backupDirectory(worldId);
        if (!Files.exists(worldDir)) {
            unloadWorld(server, worldId);
            worldDir = existingWorldDirectory(server, worldId);
        }
        if (!Files.exists(worldDir)) {
            source.sendFailure(Component.literal("World folder not found: " + worldDirectory(server, worldId) + " (legacy: " + legacyWorldDirectory(server, worldId) + ")"));
            return 0;
        }

        deleteDirectory(backupDir);
        copyDirectory(worldDir, backupDir);
        preset.saved = true;
        saveMaps();
        source.sendSuccess(() -> Component.literal("Preset remade for map '" + mapName + "' -> " + backupDir), true);
        return Command.SINGLE_SUCCESS;
    }

    private int deleteMap(CommandSourceStack source, String rawMapName) {
        String mapName = normalizeMapName(rawMapName);
        MapPreset preset = mapPresets.get(mapName);
        if (preset == null) {
            source.sendFailure(Component.literal("Unknown map: " + mapName));
            return 0;
        }

        if (!findActivesByMapName(mapName).isEmpty()) {
            source.sendFailure(Component.literal("Map is running. End the match first: " + mapName));
            return 0;
        }

        ResourceLocation worldId = worldIdOf(preset);
        boolean worldShared = worldId != null && isWorldReferencedByOtherMap(mapName, worldId);
        if (worldId != null) {
            // Keep the actual Multiworld world; delete only map-related data.
            if (!worldShared) {
                deleteDirectory(backupDirectory(worldId));
            }
        }

        worldToMapId.entrySet().removeIf(entry -> {
            ActiveMatch match = activeMatches.get(entry.getValue());
            return match != null && mapName.equals(match.mapName);
        });
        mapPresets.remove(mapName);
        saveMaps();
        if (worldId != null && worldShared) {
            source.sendSuccess(() -> Component.literal("Map deleted: " + mapName + " (world preserved, shared backup kept)"), true);
        } else {
            source.sendSuccess(() -> Component.literal("Map deleted: " + mapName + " (world preserved)"), true);
        }
        return Command.SINGLE_SUCCESS;
    }

    private int startMatch(CommandSourceStack source, String rawMapName, Collection<ServerPlayer> redPlayers, Collection<ServerPlayer> bluePlayers) {
        String mapName = normalizeMapName(rawMapName);
        MapPreset preset = mapPresets.get(mapName);
        if (preset == null) {
            source.sendFailure(Component.literal("Unknown map: " + mapName + ". Use /warpattern map import first."));
            return 0;
        }
        String validateError = validatePresetBeforeUse(preset);
        if (validateError != null) {
            source.sendFailure(Component.literal("Map is not ready: " + validateError));
            return 0;
        }
        if (!preset.saved) {
            source.sendFailure(Component.literal("Map is not saved. Run /warpattern map " + mapName + " save first."));
            return 0;
        }

        Set<UUID> red = new LinkedHashSet<>();
        for (ServerPlayer p : redPlayers) {
            red.add(p.getUUID());
        }
        Set<UUID> blue = new LinkedHashSet<>();
        for (ServerPlayer p : bluePlayers) {
            blue.add(p.getUUID());
        }

        Set<UUID> overlap = new HashSet<>(red);
        overlap.retainAll(blue);
        if (!overlap.isEmpty()) {
            source.sendFailure(Component.literal("A player cannot be in both teams."));
            return 0;
        }
        if (red.isEmpty() || blue.isEmpty()) {
            source.sendFailure(Component.literal("Both teams must have at least one player."));
            return 0;
        }

        ResourceLocation templateWorldId = resolveWorldBindingForRuntime(source, source.getServer(), preset, mapName);
        if (templateWorldId == null) {
            source.sendFailure(Component.literal("Map has invalid world binding."));
            return 0;
        }

        MinecraftServer server = source.getServer();
        Set<UUID> allPlayers = new HashSet<>(red);
        allPlayers.addAll(blue);
        for (UUID uuid : allPlayers) {
            if (playerAssignments.containsKey(uuid)) {
                ServerPlayer conflict = server.getPlayerList().getPlayer(uuid);
                String name = conflict == null ? uuid.toString() : conflict.getGameProfile().getName();
                source.sendFailure(Component.literal("Player is already in an active match: " + name));
                return 0;
            }
        }

        Path backupDir = backupDirectory(templateWorldId);
        if (!Files.exists(backupDir)) {
            source.sendFailure(Component.literal("Map backup missing. Run /warpattern map " + mapName + " save first."));
            return 0;
        }
        // Each match runs in an isolated world cloned from the map template backup.
        ResourceLocation worldId = createRuntimeMatchWorld(server, mapName, templateWorldId);
        if (worldId == null) {
            source.sendFailure(Component.literal("Failed to create runtime world for map: " + mapName));
            return 0;
        }

        ServerLevel level = getLevel(server, worldId);
        if (level == null) {
            source.sendFailure(Component.literal("Unable to load map world: " + worldId));
            return 0;
        }

        String mapId = mapIdOf(mapName, worldId);
        ActiveMatch match = new ActiveMatch();
        match.mapId = mapId;
        match.mapName = mapName;
        match.templateWorldId = templateWorldId;
        match.worldId = worldId;
        match.runtimeWorld = true;
        match.teamA = "red";
        match.teamB = "blue";
        match.colorA = TEAM_A_COLOR;
        match.colorB = TEAM_B_COLOR;
        match.playersA.addAll(red);
        match.playersB.addAll(blue);
        match.bound1 = preset.bound1;
        match.bound2 = preset.bound2;

        int minX = Math.min(match.bound1.x, match.bound2.x);
        int maxX = Math.max(match.bound1.x, match.bound2.x);
        int minZ = Math.min(match.bound1.z, match.bound2.z);
        int maxZ = Math.max(match.bound1.z, match.bound2.z);
        boolean vpStarted = VpPointsApi.startMatch(new com.flowingsun.vppoints.match.SquadMatchService.StartMatchRequest(
                server,
                mapId,
                mapName,
                worldId,
                match.teamA,
                match.colorA,
                match.teamB,
                match.colorB,
                Set.copyOf(red),
                Set.copyOf(blue),
                new com.flowingsun.vppoints.match.SquadMatchService.MatchBounds(minX, maxX, minZ, maxZ)
        ));
        if (!vpStarted) {
            if (match.runtimeWorld) {
                destroyRuntimeMatchWorld(server, worldId);
            }
            source.sendFailure(Component.literal("VP Points start failed for map: " + mapName));
            return 0;
        }

        activeMatches.put(mapId, match);
        worldToMapId.put(worldId, mapId);
        for (UUID uuid : red) {
            playerAssignments.put(uuid, new PlayerAssignment(mapId, "red"));
        }
        for (UUID uuid : blue) {
            playerAssignments.put(uuid, new PlayerAssignment(mapId, "blue"));
        }

        clearPlayersInventory(server, red);
        clearPlayersInventory(server, blue);
        teleportPlayersToSpawn(server, level, preset.redSpawn, red, mapId);
        teleportPlayersToSpawn(server, level, preset.blueSpawn, blue, mapId);

        source.sendSuccess(() -> Component.literal("Match started: " + mapName + " [" + mapId + "]"), true);
        return Command.SINGLE_SUCCESS;
    }

    private int endMatchByMapName(CommandSourceStack source, String rawMapName) {
        String mapName = normalizeMapName(rawMapName);
        List<ActiveMatch> matches = findActivesByMapName(mapName);
        if (matches.isEmpty()) {
            source.sendFailure(Component.literal("No active match for map: " + mapName));
            return 0;
        }

        // End all concurrent instances of the same map name in one command.
        for (ActiveMatch match : matches) {
            endMatchInternal(match, source.getServer(), true, "manual");
        }
        source.sendSuccess(() -> Component.literal("Match ended: " + mapName + " (" + matches.size() + " instance(s))"), true);
        return Command.SINGLE_SUCCESS;
    }

    private void endMatchInternal(ActiveMatch match, MinecraftServer server, boolean restorePreset, String reason) {
        activeMatches.remove(match.mapId);
        worldToMapId.remove(match.worldId);

        for (UUID uuid : match.playersA) {
            playerAssignments.remove(uuid);
        }
        for (UUID uuid : match.playersB) {
            playerAssignments.remove(uuid);
        }
        pendingTeleports.keySet().removeIf(uuid -> match.playersA.contains(uuid) || match.playersB.contains(uuid));

        VpPointsApi.endMatch(match.mapId, server, reason);

        if (match.runtimeWorld) {
            // Runtime worlds are disposable; always destroy to avoid cross-match pollution.
            destroyRuntimeMatchWorld(server, match.worldId);
        } else if (restorePreset) {
            restorePresetWorld(server, match.mapName, match.worldId);
        }

        LOGGER.info("Match ended: mapId='{}', reason='{}'", match.mapId, reason);
    }

    private List<ActiveMatch> findActivesByMapName(String mapName) {
        List<ActiveMatch> result = new ArrayList<>();
        for (ActiveMatch match : activeMatches.values()) {
            if (match.mapName.equals(mapName)) {
                result.add(match);
            }
        }
        return result;
    }

    private String mapIdOf(String mapName, ResourceLocation runtimeWorldId) {
        return "squad:" + mapName + "@" + runtimeWorldId;
    }

    private boolean isWorldReferencedByOtherMap(String mapName, ResourceLocation worldId) {
        String target = worldId.toString();
        for (Map.Entry<String, MapPreset> entry : mapPresets.entrySet()) {
            if (entry.getKey().equals(mapName)) {
                continue;
            }
            MapPreset preset = entry.getValue();
            if (preset == null || preset.worldId == null) {
                continue;
            }
            if (target.equals(preset.worldId)) {
                return true;
            }
        }
        return false;
    }

    private List<ServerPlayer> onlineMatchCandidates(MinecraftServer server) {
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator() || player.isCreative()) {
                continue;
            }
            players.add(player);
        }
        return players;
    }

    private void cleanupMapsForDeletedWorlds(MinecraftServer server) {
        List<String> removedMaps = new ArrayList<>();
        Iterator<Map.Entry<String, MapPreset>> iterator = mapPresets.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, MapPreset> entry = iterator.next();
            String mapName = entry.getKey();
            MapPreset preset = entry.getValue();
            ResourceLocation worldId = worldIdOf(preset);
            if (worldId == null) {
                continue;
            }
            if (getLevel(server, worldId) != null) {
                continue;
            }

            Path worldDir = worldDirectory(server, worldId);
            Path legacyDir = legacyWorldDirectory(server, worldId);
            if (Files.exists(worldDir) || Files.exists(legacyDir)) {
                continue;
            }

            List<ActiveMatch> running = findActivesByMapName(mapName);
            for (ActiveMatch match : running) {
                teleportPlayersToDefault(server, match.playersA);
                teleportPlayersToDefault(server, match.playersB);
                endMatchInternal(match, server, false, "map-world-deleted");
            }

            worldToMapId.entrySet().removeIf(e -> {
                if (worldId.equals(e.getKey())) {
                    return true;
                }
                ActiveMatch match = activeMatches.get(e.getValue());
                return match != null && mapName.equals(match.mapName);
            });
            deleteDirectory(backupDirectory(worldId));
            iterator.remove();
            removedMaps.add(mapName + " -> " + worldId);
        }

        if (!removedMaps.isEmpty()) {
            saveMaps();
            LOGGER.info("Removed maps because bound worlds were deleted: {}", String.join(", ", removedMaps));
        }
    }

    private void clearPlayersInventory(MinecraftServer server, Set<UUID> players) {
        for (UUID uuid : players) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) {
                continue;
            }
            player.getInventory().clearContent();
            clearCuriosInventory(player);
            player.inventoryMenu.broadcastChanges();
        }
    }

    private void teleportPlayersToDefault(MinecraftServer server, Set<UUID> players) {
        for (UUID uuid : players) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) {
                continue;
            }
            teleportToDefaultWorld(player);
        }
    }

    private void clearCuriosInventory(ServerPlayer player) {
        try {
            Class<?> curiosApiClass = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Object helper = curiosApiClass.getMethod("getCuriosHelper").invoke(null);
            Object lazyOptional = helper.getClass()
                    .getMethod("getCuriosHandler", LivingEntity.class)
                    .invoke(helper, player);
            Object resolved = lazyOptional.getClass().getMethod("resolve").invoke(lazyOptional);
            if (!(resolved instanceof Optional<?> optional) || optional.isEmpty()) {
                return;
            }

            Object curiosHandler = optional.get();
            Object curiosMapObj = curiosHandler.getClass().getMethod("getCurios").invoke(curiosHandler);
            if (!(curiosMapObj instanceof Map<?, ?> curiosMap)) {
                return;
            }

            for (Object stacksHandler : curiosMap.values()) {
                if (stacksHandler == null) {
                    continue;
                }
                clearItemHandlerSlots(callNoArg(stacksHandler, "getStacks"));
                clearItemHandlerSlots(callNoArg(stacksHandler, "getCosmeticStacks"));
            }
        } catch (ClassNotFoundException ignored) {
            // Curios is not installed, nothing to clear.
        } catch (Throwable ex) {
            LOGGER.warn("Failed to clear curios inventory for player {}", player.getGameProfile().getName(), ex);
        }
    }

    private Object callNoArg(Object target, String method) {
        if (target == null) {
            return null;
        }
        try {
            return target.getClass().getMethod(method).invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void clearItemHandlerSlots(Object handler) {
        if (handler == null) {
            return;
        }
        try {
            int slots = (int) handler.getClass().getMethod("getSlots").invoke(handler);
            Method setStackInSlot = handler.getClass().getMethod("setStackInSlot", int.class, ItemStack.class);
            for (int i = 0; i < slots; i++) {
                setStackInSlot.invoke(handler, i, ItemStack.EMPTY);
            }
        } catch (Throwable ignored) {
            // Non-modifiable handler type; ignore.
        }
    }

    private void handleReconnect(ServerPlayer player) {
        UUID playerId = player.getUUID();
        String mapId = disconnectedMatchByPlayer.remove(playerId);
        if (mapId == null) {
            return;
        }

        ActiveMatch match = activeMatches.get(mapId);
        if (match == null || !isPlayerInMatch(match, playerId)) {
            teleportToDefaultWorld(player);
            return;
        }

        teleportPlayerBackToMatch(player, match);
    }

    private boolean isPlayerInMatch(ActiveMatch match, UUID playerId) {
        if (match.playersA.contains(playerId) || match.playersB.contains(playerId)) {
            return true;
        }
        PlayerAssignment assignment = playerAssignments.get(playerId);
        return assignment != null && match.mapId.equals(assignment.mapId());
    }

    private void teleportPlayerBackToMatch(ServerPlayer player, ActiveMatch match) {
        MinecraftServer server = player.server;
        if (server == null) {
            return;
        }

        ensureWorld(server, match.worldId);
        ServerLevel mapLevel = getLevel(server, match.worldId);
        if (mapLevel == null) {
            LOGGER.warn("Reconnect failed for {}: map world {} is not available", player.getGameProfile().getName(), match.worldId);
            teleportToDefaultWorld(player);
            return;
        }

        String team = teamForPlayerInMatch(match, player.getUUID());
        SpawnPoint spawn = spawnForTeam(match.mapName, team);
        if (spawn == null) {
            LOGGER.warn("Reconnect failed for {}: missing team spawn for map {}", player.getGameProfile().getName(), match.mapName);
            teleportToDefaultWorld(player);
            return;
        }

        teleportPlayerToSpawn(server, player, mapLevel, spawn, match.mapId);
    }

    private SpawnPoint spawnForTeam(String mapName, String team) {
        MapPreset preset = mapPresets.get(mapName);
        if (preset == null) {
            return null;
        }
        if ("red".equals(team)) {
            return preset.redSpawn;
        }
        if ("blue".equals(team)) {
            return preset.blueSpawn;
        }
        return null;
    }

    private String teamForPlayerInMatch(ActiveMatch match, UUID playerId) {
        if (match.playersA.contains(playerId)) {
            return "red";
        }
        if (match.playersB.contains(playerId)) {
            return "blue";
        }
        PlayerAssignment assignment = playerAssignments.get(playerId);
        if (assignment != null && match.mapId.equals(assignment.mapId())) {
            return assignment.team();
        }
        return null;
    }

    private void teleportToDefaultWorld(ServerPlayer player) {
        MinecraftServer server = player.server;
        if (server == null) {
            return;
        }
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }

        BlockPos spawn = overworld.getSharedSpawnPos();
        double x = spawn.getX() + 0.5D;
        double y = spawn.getY() + 1D;
        double z = spawn.getZ() + 0.5D;
        if (!isTeleportSettled(player, overworld.dimension().location(), x, y, z)) {
            player.teleportTo(overworld, x, y, z, player.getYRot(), player.getXRot());
        }
        player.setRespawnPosition(overworld.dimension(), spawn, 0.0F, true, false);
        pendingTeleports.remove(player.getUUID());
    }

    private void teleportPlayersToSpawn(MinecraftServer server, ServerLevel fallbackLevel, SpawnPoint spawn, Set<UUID> players, String mapId) {
        for (UUID uuid : players) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) {
                continue;
            }
            teleportPlayerToSpawn(server, player, fallbackLevel, spawn, mapId);
        }
    }

    private void teleportPlayerToSpawn(MinecraftServer server, ServerPlayer player, ServerLevel fallbackLevel, SpawnPoint spawn, String mapId) {
        // Always teleport to the active match world to avoid stale/mismatched spawn dimension data.
        ServerLevel target = fallbackLevel;
        player.stopRiding();
        player.teleportTo(target, spawn.x, spawn.y, spawn.z, spawn.yaw, spawn.pitch);
        if (!player.serverLevel().dimension().equals(target.dimension())) {
            LOGGER.warn(
                    "Teleport dimension mismatch for '{}': expected {}, actual {}. Retrying teleport.",
                    player.getGameProfile().getName(),
                    target.dimension().location(),
                    player.serverLevel().dimension().location()
            );
            player.teleportTo(target, spawn.x, spawn.y, spawn.z, spawn.yaw, spawn.pitch);
            if (!player.serverLevel().dimension().equals(target.dimension())) {
                boolean moved = multiworldTeleportPlayer(player, target, spawn);
                if (!moved) {
                    LOGGER.warn(
                            "Fallback Multiworld teleport did not move '{}' to {} (still at {}).",
                            player.getGameProfile().getName(),
                            target.dimension().location(),
                            player.serverLevel().dimension().location()
                    );
                }
            }
        }
        player.setRespawnPosition(target.dimension(), new BlockPos((int) spawn.x, (int) spawn.y, (int) spawn.z), spawn.yaw, true, false);
        if (isTeleportSettled(player, target.dimension().location(), spawn.x, spawn.y, spawn.z)) {
            pendingTeleports.remove(player.getUUID());
        } else {
            scheduleTeleportVerification(server, player, mapId, target, spawn);
        }
        LOGGER.info(
                "Teleported player '{}' to {} at ({}, {}, {}) [spawnDimension={}]",
                player.getGameProfile().getName(),
                target.dimension().location(),
                spawn.x, spawn.y, spawn.z,
                spawn.dimension
        );
    }

    private void scheduleTeleportVerification(MinecraftServer server, ServerPlayer player, String mapId, ServerLevel target, SpawnPoint spawn) {
        if (mapId == null || mapId.isBlank()) {
            pendingTeleports.remove(player.getUUID());
            return;
        }
        PendingTeleport pending = new PendingTeleport();
        pending.playerId = player.getUUID();
        pending.mapId = mapId;
        pending.worldId = target.dimension().location();
        pending.x = spawn.x;
        pending.y = spawn.y;
        pending.z = spawn.z;
        pending.yaw = spawn.yaw;
        pending.pitch = spawn.pitch;
        pending.nextCheckTick = server.getTickCount() + TELEPORT_VERIFY_INITIAL_DELAY_TICKS;
        pending.attemptsRemaining = TELEPORT_VERIFY_MAX_ATTEMPTS;
        pendingTeleports.put(pending.playerId, pending);
    }

    private void processPendingTeleports(MinecraftServer server) {
        if (pendingTeleports.isEmpty()) {
            return;
        }
        long now = server.getTickCount();
        Iterator<Map.Entry<UUID, PendingTeleport>> iterator = pendingTeleports.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingTeleport> entry = iterator.next();
            PendingTeleport pending = entry.getValue();
            if (pending == null || now < pending.nextCheckTick) {
                continue;
            }

            ActiveMatch match = activeMatches.get(pending.mapId);
            PlayerAssignment assignment = playerAssignments.get(pending.playerId);
            if (match == null || assignment == null || !pending.mapId.equals(assignment.mapId())) {
                iterator.remove();
                continue;
            }

            ServerPlayer player = server.getPlayerList().getPlayer(pending.playerId);
            if (player == null) {
                iterator.remove();
                continue;
            }

            if (isTeleportSettled(player, pending.worldId, pending.x, pending.y, pending.z)) {
                iterator.remove();
                continue;
            }

            ServerLevel level = getLevel(server, pending.worldId);
            if (level == null) {
                ensureWorld(server, pending.worldId);
                level = getLevel(server, pending.worldId);
            }
            if (level == null) {
                LOGGER.warn(
                        "Teleport correction failed for '{}': target world '{}' unavailable.",
                        player.getGameProfile().getName(),
                        pending.worldId
                );
                iterator.remove();
                continue;
            }

            player.stopRiding();
            player.teleportTo(level, pending.x, pending.y, pending.z, pending.yaw, pending.pitch);
            if (!player.serverLevel().dimension().equals(level.dimension())) {
                SpawnPoint fallbackSpawn = new SpawnPoint();
                fallbackSpawn.dimension = pending.worldId.toString();
                fallbackSpawn.x = pending.x;
                fallbackSpawn.y = pending.y;
                fallbackSpawn.z = pending.z;
                fallbackSpawn.yaw = pending.yaw;
                fallbackSpawn.pitch = pending.pitch;
                multiworldTeleportPlayer(player, level, fallbackSpawn);
            }
            player.setRespawnPosition(level.dimension(), new BlockPos((int) pending.x, (int) pending.y, (int) pending.z), pending.yaw, true, false);
            if (isTeleportSettled(player, pending.worldId, pending.x, pending.y, pending.z)) {
                iterator.remove();
                continue;
            }
            pending.attemptsRemaining--;
            if (pending.attemptsRemaining <= 0) {
                iterator.remove();
            } else {
                pending.nextCheckTick = now + TELEPORT_VERIFY_RETRY_DELAY_TICKS;
            }
            LOGGER.info(
                    "Teleport corrected for '{}' -> {} at ({}, {}, {}) [attemptsLeft={}]",
                    player.getGameProfile().getName(),
                    pending.worldId,
                    pending.x, pending.y, pending.z,
                    Math.max(0, pending.attemptsRemaining)
            );
        }
    }

    private boolean multiworldTeleportPlayer(ServerPlayer player, ServerLevel target, SpawnPoint spawn) {
        try {
            Class<?> mwClass = Class.forName("me.isaiah.multiworld.MultiworldMod");
            Object creator = mwClass.getMethod("get_world_creator").invoke(null);
            if (creator == null) {
                return false;
            }

            Method teleMethod = null;
            for (Method m : creator.getClass().getMethods()) {
                if ("teleleport".equals(m.getName()) && m.getParameterCount() == 5) {
                    teleMethod = m;
                    break;
                }
            }
            if (teleMethod == null) {
                return false;
            }

            teleMethod.invoke(creator, player, target, spawn.x, spawn.y, spawn.z);
            // Ensure rotation and exact position after Multiworld move.
            player.teleportTo(target, spawn.x, spawn.y, spawn.z, spawn.yaw, spawn.pitch);
            return player.serverLevel().dimension().equals(target.dimension());
        } catch (Throwable ex) {
            LOGGER.warn("Failed to invoke Multiworld teleleport for {}", player.getGameProfile().getName(), ex);
            return false;
        }
    }

    private ResourceLocation parseWorldId(String raw) {
        String input = Objects.requireNonNullElse(raw, "").trim();
        if (input.indexOf(':') < 0) {
            return new ResourceLocation("multiworld", sanitize(input));
        }
        ResourceLocation parsed = ResourceLocation.tryParse(input);
        if (parsed != null) {
            if ("minecraft".equals(parsed.getNamespace()) && !isVanillaDimensionId(parsed)) {
                return new ResourceLocation("multiworld", sanitize(parsed.getPath()));
            }
            return parsed;
        }
        return new ResourceLocation("multiworld", sanitize(input));
    }

    private ResourceLocation worldIdOf(MapPreset preset) {
        return preset == null || preset.worldId == null ? null : ResourceLocation.tryParse(preset.worldId);
    }

    private ResourceLocation resolveWorldBindingForRuntime(CommandSourceStack source, MinecraftServer server, MapPreset preset, String mapName) {
        ResourceLocation worldId = worldIdOf(preset);
        if (worldId == null) {
            return null;
        }
        if (!isSupportedMapWorld(worldId)) {
            source.sendFailure(Component.literal("Unsupported map world binding: " + worldId + "."));
            return null;
        }
        ensureWorld(server, worldId);
        if (getLevel(server, worldId) == null) {
            LOGGER.warn("Map '{}' world '{}' could not be loaded at runtime", mapName, worldId);
            source.sendFailure(Component.literal("World is not loaded: " + worldId));
            return null;
        }
        return worldId;
    }

    private String validatePresetBeforeUse(MapPreset preset) {
        ResourceLocation boundWorld = worldIdOf(preset);
        if (boundWorld == null) {
            return "invalid world binding";
        }
        if (!isSupportedMapWorld(boundWorld)) {
            return "map world must be a non-vanilla custom dimension";
        }
        if (preset.redSpawn == null || preset.blueSpawn == null) {
            return "both team spawns are required";
        }
        if (preset.bound1 == null || preset.bound2 == null) {
            return "map bounds are required";
        }
        String world = boundWorld.toString();
        if (!world.equals(preset.redSpawn.dimension) || !world.equals(preset.blueSpawn.dimension)) {
            return "spawn dimension does not match bound world";
        }
        if (!world.equals(preset.bound1.dimension) || !world.equals(preset.bound2.dimension)) {
            return "bounds dimension does not match bound world";
        }

        int minX = Math.min(preset.bound1.x, preset.bound2.x);
        int maxX = Math.max(preset.bound1.x, preset.bound2.x);
        int minY = Math.min(preset.bound1.y, preset.bound2.y);
        int maxY = Math.max(preset.bound1.y, preset.bound2.y);
        int minZ = Math.min(preset.bound1.z, preset.bound2.z);
        int maxZ = Math.max(preset.bound1.z, preset.bound2.z);

        if (!inCuboid(preset.redSpawn.x, preset.redSpawn.y, preset.redSpawn.z, minX, maxX, minY, maxY, minZ, maxZ)) {
            return "red spawn is outside map bounds";
        }
        if (!inCuboid(preset.blueSpawn.x, preset.blueSpawn.y, preset.blueSpawn.z, minX, maxX, minY, maxY, minZ, maxZ)) {
            return "blue spawn is outside map bounds";
        }
        return null;
    }

    private boolean inCuboid(double x, double y, double z, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        return x >= minX && x <= (maxX + 1)
                && y >= minY && y <= (maxY + 1)
                && z >= minZ && z <= (maxZ + 1);
    }

    private void normalizeBoundsToSquare(BoundPoint p1, BoundPoint p2) {
        int dx = p2.x - p1.x;
        int dz = p2.z - p1.z;
        int size = Math.max(Math.abs(dx), Math.abs(dz));
        if (size <= 0) {
            return;
        }

        int sx = Integer.compare(dx, 0);
        int sz = Integer.compare(dz, 0);
        if (sx == 0) {
            sx = 1;
        }
        if (sz == 0) {
            sz = 1;
        }

        p2.x = p1.x + sx * size;
        p2.z = p1.z + sz * size;
    }

    private String normalizeMapName(String input) {
        String cleaned = sanitize(input);
        return cleaned.isBlank() ? "map" : cleaned;
    }

    private String sanitize(String source) {
        String lower = Objects.requireNonNullElse(source, "map").toLowerCase(Locale.ROOT).trim();
        String cleaned = lower.replaceAll("[^a-z0-9_/.-]", "_");
        cleaned = cleaned.replaceAll("_+", "_");
        if (cleaned.length() > 48) {
            cleaned = cleaned.substring(0, 48);
        }
        return cleaned;
    }

    private void normalizeMapPreset(MapPreset preset) {
        if (preset == null) {
            return;
        }
        if (preset.recommendedMinPlayers <= 0) {
            preset.recommendedMinPlayers = 2;
        }
        if (preset.recommendedMaxPlayers <= 0) {
            preset.recommendedMaxPlayers = Math.max(16, preset.recommendedMinPlayers);
        }
        if (preset.recommendedMaxPlayers < preset.recommendedMinPlayers) {
            preset.recommendedMaxPlayers = preset.recommendedMinPlayers;
        }
    }

    private void loadMaps() {
        if (!Files.exists(storageFile)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(storageFile)) {
            Map<String, MapPreset> loaded = GSON.fromJson(reader, MAPS_TYPE);
            if (loaded != null) {
                mapPresets.clear();
                loaded.values().forEach(this::normalizeMapPreset);
                mapPresets.putAll(loaded);
            }
        } catch (Exception ex) {
            LOGGER.warn("Failed to load map file: {}", storageFile, ex);
        }
    }

    private void saveMaps() {
        try {
            Files.createDirectories(storageFile.getParent());
            try (Writer writer = Files.newBufferedWriter(storageFile)) {
                GSON.toJson(mapPresets, MAPS_TYPE, writer);
            }
        } catch (IOException ex) {
            LOGGER.warn("Failed to save map file: {}", storageFile, ex);
        }
    }

    private void ensureWorld(MinecraftServer server, ResourceLocation worldId) {
        Path dir = worldDirectory(server, worldId);
        Path legacyDir = legacyWorldDirectory(server, worldId);

        if (getLevel(server, worldId) != null) {
            // Already loaded: just make sure at least one known folder exists.
            if (!Files.exists(dir) && Files.exists(legacyDir)) {
                deleteDirectory(dir);
                copyDirectory(legacyDir, dir);
            }
            return;
        }

        if (Files.exists(dir)) {
            multiworldLoadSavedWorld(server, dir, worldId.toString());
            if (getLevel(server, worldId) != null) {
                return;
            }
        }

        if (Files.exists(legacyDir)) {
            deleteDirectory(dir);
            copyDirectory(legacyDir, dir);
            multiworldLoadSavedWorld(server, dir, worldId.toString());
            if (getLevel(server, worldId) != null) {
                return;
            }
        }

        runServerCommand(server, "mw create " + worldId + " NORMAL");
        if (getLevel(server, worldId) != null) {
            return;
        }

        if (Files.exists(dir)) {
            multiworldLoadSavedWorld(server, dir, worldId.toString());
            if (getLevel(server, worldId) != null) {
                return;
            }
        }
        if (Files.exists(legacyDir)) {
            multiworldLoadSavedWorld(server, legacyDir, worldId.toString());
        }
    }

    private boolean isTeleportSettled(ServerPlayer player, ResourceLocation worldId, double x, double y, double z) {
        if (player == null || worldId == null) {
            return false;
        }
        if (!player.serverLevel().dimension().location().equals(worldId)) {
            return false;
        }
        return player.distanceToSqr(x, y, z) <= TELEPORT_SETTLED_DISTANCE_SQR;
    }

    private ResourceLocation createRuntimeMatchWorld(MinecraftServer server, String mapName, ResourceLocation templateWorldId) {
        // Retry a few ids in case of stale folders from abnormal shutdown.
        for (int attempts = 0; attempts < 20; attempts++) {
            ResourceLocation runtimeWorldId = nextRuntimeWorldId(mapName, templateWorldId.getNamespace());
            Path runtimeDir = worldDirectory(server, runtimeWorldId);
            Path runtimeLegacyDir = legacyWorldDirectory(server, runtimeWorldId);
            if (getLevel(server, runtimeWorldId) != null || Files.exists(runtimeDir) || Files.exists(runtimeLegacyDir)) {
                continue;
            }
            if (!restoreWorldFromBackup(server, mapName, templateWorldId, runtimeWorldId)) {
                continue;
            }
            if (getLevel(server, runtimeWorldId) != null) {
                return runtimeWorldId;
            }
        }
        return null;
    }

    private ResourceLocation nextRuntimeWorldId(String mapName, String namespace) {
        runtimeMatchCounter++;
        // Keep ids short/readable while staying collision-resistant in a single server runtime.
        String token = Long.toString(System.currentTimeMillis(), 36)
                + Long.toString(runtimeMatchCounter, 36)
                + Integer.toString(ThreadLocalRandom.current().nextInt(1296), 36);
        String path = sanitize("match_" + mapName + "_" + token);
        return new ResourceLocation(namespace, path);
    }

    private void destroyRuntimeMatchWorld(MinecraftServer server, ResourceLocation worldId) {
        if (server == null || worldId == null) {
            return;
        }
        // Best-effort teardown for both modern multiworld folder and legacy dimension folder.
        Path worldDir = worldDirectory(server, worldId);
        Path legacyDir = legacyWorldDirectory(server, worldId);
        evacuatePlayersInWorld(server, worldId);
        unloadWorld(server, worldId);
        multiworldDeleteWorld(server, worldId);
        deleteDirectory(worldDir);
        deleteDirectory(legacyDir);
    }

    private boolean restoreWorldFromBackup(MinecraftServer server, String mapName, ResourceLocation backupSourceWorldId, ResourceLocation targetWorldId) {
        Path worldDir = worldDirectory(server, targetWorldId);
        Path legacyDir = legacyWorldDirectory(server, targetWorldId);
        Path backupDir = backupDirectory(backupSourceWorldId);
        if (!Files.exists(backupDir)) {
            LOGGER.warn("No preset backup found for map '{}'. Expected: {}", mapName, backupDir);
            return false;
        }

        evacuatePlayersInWorld(server, targetWorldId);
        unloadWorld(server, targetWorldId);
        multiworldDeleteWorld(server, targetWorldId);
        deleteDirectory(worldDir);
        deleteDirectory(legacyDir);
        // Copy backup into both layouts so different loaders can still find the world.
        copyDirectory(backupDir, worldDir);
        copyDirectory(backupDir, legacyDir);
        multiworldLoadSavedWorld(server, worldDir, targetWorldId.toString());
        if (getLevel(server, targetWorldId) == null && Files.exists(legacyDir)) {
            multiworldLoadSavedWorld(server, legacyDir, targetWorldId.toString());
        }
        return getLevel(server, targetWorldId) != null;
    }

    private void restorePresetWorld(MinecraftServer server, String mapName, ResourceLocation worldId) {
        restoreWorldFromBackup(server, mapName, worldId, worldId);
    }

    private void evacuatePlayersInWorld(MinecraftServer server, ResourceLocation worldId) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.serverLevel().dimension().location().equals(worldId)) {
                continue;
            }
            double x = overworld.getSharedSpawnPos().getX() + 0.5D;
            double y = overworld.getSharedSpawnPos().getY() + 1D;
            double z = overworld.getSharedSpawnPos().getZ() + 0.5D;
            player.teleportTo(overworld, x, y, z, player.getYRot(), player.getXRot());
            player.setRespawnPosition(overworld.dimension(), overworld.getSharedSpawnPos(), 0.0F, true, false);
        }
    }

    private void unloadWorld(MinecraftServer server, ResourceLocation worldId) {
        ServerLevel level = getLevel(server, worldId);
        if (level == null) {
            return;
        }
        ServerChunkCache chunkSource = level.getChunkSource();
        chunkSource.save(true);
    }

    private ServerLevel getLevel(MinecraftServer server, ResourceLocation worldId) {
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, worldId);
        return server.getLevel(key);
    }

    private Path worldDirectory(MinecraftServer server, ResourceLocation worldId) {
        Path worldRoot = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path serverRoot = worldRoot.getParent();
        if (serverRoot == null) {
            serverRoot = worldRoot;
        }
        return serverRoot.resolve("multiworld").resolve(worldFolderName(worldId));
    }

    private Path legacyWorldDirectory(MinecraftServer server, ResourceLocation worldId) {
        Path root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        return root.resolve("dimensions").resolve(worldId.getNamespace()).resolve(worldId.getPath());
    }

    private String worldFolderName(ResourceLocation worldId) {
        if ("multiworld".equals(worldId.getNamespace())) {
            return worldId.getPath();
        }
        return worldId.toString().replace('/', '_').replace(':', '_');
    }

    private boolean isSupportedMapWorld(ResourceLocation worldId) {
        return worldId != null && !"minecraft".equals(worldId.getNamespace());
    }

    private boolean isVanillaDimensionId(ResourceLocation worldId) {
        if (!"minecraft".equals(worldId.getNamespace())) {
            return false;
        }
        String path = worldId.getPath();
        return "overworld".equals(path) || "the_nether".equals(path) || "the_end".equals(path);
    }

    private Path existingWorldDirectory(MinecraftServer server, ResourceLocation worldId) {
        Path preferred = worldDirectory(server, worldId);
        if (Files.exists(preferred)) {
            return preferred;
        }
        Path legacy = legacyWorldDirectory(server, worldId);
        if (Files.exists(legacy)) {
            return legacy;
        }
        return preferred;
    }

    private Path backupDirectory(ResourceLocation worldId) {
        return presetRoot.resolve(worldId.getNamespace()).resolve(worldId.getPath());
    }

    private void runServerCommand(MinecraftServer server, String raw) {
        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSuppressedOutput(),
                raw
        );
    }

    private void multiworldDeleteWorld(MinecraftServer server, ResourceLocation worldId) {
        if (server == null || worldId == null) {
            return;
        }

        // Multiworld command path is more resilient across implementations.
        if (getLevel(server, worldId) != null) {
            runServerCommand(server, "mw delete " + worldId);
        }
        if (getLevel(server, worldId) == null) {
            return;
        }

        String id = worldId.toString();
        try {
            Class<?> mw = Class.forName("me.isaiah.multiworld.MultiworldMod");
            Object creator = mw.getMethod("get_world_creator").invoke(null);
            if (creator != null) {
                creator.getClass().getMethod("delete_world", String.class).invoke(creator, id);
            }
        } catch (Exception ex) {
            LOGGER.warn("Failed to call Multiworld delete_world for {}", id, ex);
        }

        if (getLevel(server, worldId) != null) {
            LOGGER.warn("World still loaded after delete attempt: {}", worldId);
        }
    }

    private void multiworldLoadSavedWorld(MinecraftServer server, Path worldDir, String worldId) {
        try {
            Class<?> utils = Class.forName("me.isaiah.multiworld.Utils");
            Method method = utils.getMethod("loadSavedMultiworldWorld", MinecraftServer.class, Path.class, Optional.class);
            method.invoke(null, server, worldDir, Optional.of(worldId));
        } catch (Exception ex) {
            LOGGER.warn("Failed to reload saved Multiworld world {}", worldId, ex);
        }
    }

    private void deleteDirectory(Path dir) {
        try {
            if (!Files.exists(dir)) {
                return;
            }
            try (var walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                });
            }
        } catch (Exception ex) {
            LOGGER.warn("Failed to delete directory {}", dir, ex);
        }
    }

    private void copyDirectory(Path src, Path dst) {
        try {
            Files.createDirectories(dst);
            try (var walk = Files.walk(src)) {
                walk.forEach(path -> {
                    try {
                        Path relative = src.relativize(path);
                        Path target = dst.resolve(relative);
                        if (Files.isDirectory(path)) {
                            Files.createDirectories(target);
                        } else {
                            Files.createDirectories(target.getParent());
                            Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                        }
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                });
            }
        } catch (Exception ex) {
            LOGGER.warn("Failed to copy directory {} -> {}", src, dst, ex);
        }
    }

    private static final class ActiveMatch {
        String mapId;
        String mapName;
        // Original map template world that provides immutable backup source.
        ResourceLocation templateWorldId;
        // Actual world where this match instance runs.
        ResourceLocation worldId;
        // true for cloned runtime world; false for legacy direct-world mode.
        boolean runtimeWorld;
        String teamA;
        String teamB;
        int colorA;
        int colorB;
        final Set<UUID> playersA = new HashSet<>();
        final Set<UUID> playersB = new HashSet<>();
        BoundPoint bound1;
        BoundPoint bound2;

    }

    private record PlayerAssignment(String mapId, String team) {
    }

    private static final class PendingTeleport {
        UUID playerId;
        String mapId;
        ResourceLocation worldId;
        double x;
        double y;
        double z;
        float yaw;
        float pitch;
        long nextCheckTick;
        int attemptsRemaining;
    }

    private static final class MapPreset {
        String mapName;
        String worldId;
        SpawnPoint redSpawn;
        SpawnPoint blueSpawn;
        BoundPoint bound1;
        BoundPoint bound2;
        boolean saved;
        int recommendedMinPlayers = 2;
        int recommendedMaxPlayers = 16;
    }

    private static final class SpawnPoint {
        String dimension;
        double x;
        double y;
        double z;
        float yaw;
        float pitch;
    }

    private static final class BoundPoint {
        String dimension;
        int x;
        int y;
        int z;
    }
}

