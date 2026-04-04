package com.flowingsun.squadpattern.match;

import com.flowingsun.squadpattern.integration.FtbTeamsCompat;
import com.flowingsun.squadpattern.vp.VictoryMatchManager;
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
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.RegisterCommandsEvent;
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
import java.util.concurrent.CompletableFuture;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class SquadMatchService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAPS_TYPE = new TypeToken<Map<String, MapPreset>>() {}.getType();
    private static final int TEAM_A_COLOR = 0xFF4444;
    private static final int TEAM_B_COLOR = 0x4488FF;

    public static final SquadMatchService INSTANCE = new SquadMatchService();

    public record ActiveMatchView(
            String mapId,
            String mapName,
            ResourceLocation worldId,
            String teamA,
            int colorA,
            String teamB,
            int colorB,
            Set<UUID> teamAPlayers,
            Set<UUID> teamBPlayers
    ) {}

    public record PlayerMatchContext(
            String mapId,
            String mapName,
            String playerTeam,
            String teamA,
            int colorA,
            String teamB,
            int colorB
    ) {}

    private final Path storageFile;
    private final Path presetRoot;

    private final Map<String, MapPreset> mapPresets = new HashMap<>();
    private final Map<String, ActiveMatch> activeMatches = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerAssignment> playerAssignments = new ConcurrentHashMap<>();
    private final Map<ResourceLocation, String> worldToMapId = new ConcurrentHashMap<>();

    private SquadMatchService() {
        Path dir = FMLPaths.CONFIGDIR.get().resolve("squadpattern");
        this.storageFile = dir.resolve("maps.json");
        this.presetRoot = dir.resolve("presets");
        loadMaps();
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("squadpattern")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("map")
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

        event.getDispatcher().register(root);
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        activeMatches.clear();
        playerAssignments.clear();
        worldToMapId.clear();
    }

    private CompletableFuture<Suggestions> suggestMapNames(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(mapPresets.keySet(), builder);
    }

    public Optional<ActiveMatchView> activeForLevel(ServerLevel level) {
        String mapId = worldToMapId.get(level.dimension().location());
        if (mapId == null) {
            return Optional.empty();
        }
        ActiveMatch match = activeMatches.get(mapId);
        if (match == null) {
            return Optional.empty();
        }
        return Optional.of(match.view());
    }

    public Optional<PlayerMatchContext> contextFor(ServerPlayer player) {
        PlayerAssignment assignment = playerAssignments.get(player.getUUID());
        if (assignment == null) {
            return Optional.empty();
        }
        ActiveMatch match = activeMatches.get(assignment.mapId());
        if (match == null) {
            return Optional.empty();
        }
        return Optional.of(new PlayerMatchContext(
                match.mapId,
                match.mapName,
                assignment.team(),
                match.teamA,
                match.colorA,
                match.teamB,
                match.colorB
        ));
    }

    public String teamForPlayer(String mapId, UUID playerId) {
        PlayerAssignment assignment = playerAssignments.get(playerId);
        if (assignment == null || !assignment.mapId().equals(mapId)) {
            return null;
        }
        return assignment.team();
    }

    public void finishByTickets(String mapId, MinecraftServer server) {
        ActiveMatch match = activeMatches.get(mapId);
        if (match == null) {
            return;
        }
        endMatchInternal(match, server, true, "ticket-zero");
    }

    private int importMap(CommandSourceStack source, String rawMapName, String rawWorldName) {
        MinecraftServer server = source.getServer();
        String mapName = normalizeMapName(rawMapName);
        ResourceLocation worldId = parseWorldId(rawWorldName);
        ensureWorld(server, worldId);

        MapPreset preset = mapPresets.computeIfAbsent(mapName, k -> new MapPreset());
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

        ensurePresetBackup(server, preset);
        source.sendSuccess(() -> Component.literal("Map imported: " + mapName + " -> " + worldId), true);
        return Command.SINGLE_SUCCESS;
    }

    private int bindMapToCurrentWorld(CommandSourceStack source, String rawMapName) {
        String mapName = normalizeMapName(rawMapName);
        ResourceLocation worldId = source.getLevel().dimension().location();

        MapPreset preset = mapPresets.computeIfAbsent(mapName, k -> new MapPreset());
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

        ensurePresetBackup(source.getServer(), preset);
        source.sendSuccess(() -> Component.literal("Map bound: " + mapName + " -> " + worldId), true);
        return Command.SINGLE_SUCCESS;
    }

    private int setSpawn(CommandSourceStack source, String rawMapName, String side, int x, int y, int z) {
        String mapName = normalizeMapName(rawMapName);
        MapPreset preset = mapPresets.get(mapName);
        if (preset == null) {
            source.sendFailure(Component.literal("Unknown map: " + mapName + ". Use /squadpattern map import first."));
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
            source.sendFailure(Component.literal("Unknown map: " + mapName + ". Use /squadpattern map import first."));
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
        preset.bound1 = p1;
        preset.bound2 = p2;
        preset.saved = false;
        saveMaps();

        source.sendSuccess(() -> Component.literal("Bounds set for map '" + mapName + "': (" + x1 + "," + y1 + "," + z1 + ") -> (" + x2 + "," + y2 + "," + z2 + ")"), true);
        return Command.SINGLE_SUCCESS;
    }

    private int savePreset(CommandSourceStack source, String rawMapName) {
        String mapName = normalizeMapName(rawMapName);
        MapPreset preset = mapPresets.get(mapName);
        if (preset == null) {
            source.sendFailure(Component.literal("Unknown map: " + mapName + ". Use /squadpattern map import first."));
            return 0;
        }

        MinecraftServer server = source.getServer();
        String validateError = validatePresetBeforeUse(preset);
        if (validateError != null) {
            source.sendFailure(Component.literal("Map save check failed: " + validateError));
            return 0;
        }

        ResourceLocation worldId = worldIdOf(preset);
        if (worldId == null) {
            source.sendFailure(Component.literal("Map has invalid world binding."));
            return 0;
        }

        ensureWorld(server, worldId);
        Path worldDir = worldDirectory(server, worldId);
        Path backupDir = backupDirectory(worldId);
        if (!Files.exists(worldDir)) {
            source.sendFailure(Component.literal("World folder not found: " + worldDir));
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
        ResourceLocation worldId = worldIdOf(preset);
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
        Path worldDir = worldDirectory(server, worldId);
        Path backupDir = backupDirectory(worldId);
        if (!Files.exists(worldDir)) {
            source.sendFailure(Component.literal("World folder not found: " + worldDir));
            return 0;
        }

        deleteDirectory(backupDir);
        copyDirectory(worldDir, backupDir);
        preset.saved = true;
        saveMaps();
        source.sendSuccess(() -> Component.literal("Preset remade for map '" + mapName + "' -> " + backupDir), true);
        return Command.SINGLE_SUCCESS;
    }

    private int startMatch(CommandSourceStack source, String rawMapName, Collection<ServerPlayer> redPlayers, Collection<ServerPlayer> bluePlayers) {
        String mapName = normalizeMapName(rawMapName);
        MapPreset preset = mapPresets.get(mapName);
        if (preset == null) {
            source.sendFailure(Component.literal("Unknown map: " + mapName + ". Use /squadpattern map import first."));
            return 0;
        }
        String validateError = validatePresetBeforeUse(preset);
        if (validateError != null) {
            source.sendFailure(Component.literal("Map is not ready: " + validateError));
            return 0;
        }
        if (!preset.saved) {
            source.sendFailure(Component.literal("Map is not saved. Run /squadpattern map " + mapName + " save first."));
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

        ResourceLocation worldId = worldIdOf(preset);
        if (worldId == null) {
            source.sendFailure(Component.literal("Map has invalid world binding."));
            return 0;
        }

        MinecraftServer server = source.getServer();
        if (findActiveByMapName(mapName) != null) {
            source.sendFailure(Component.literal("Map is already running: " + mapName));
            return 0;
        }

        ensureWorld(server, worldId);
        ensurePresetBackup(server, preset);
        restorePresetWorld(server, mapName, worldId);

        ServerLevel level = getLevel(server, worldId);
        if (level == null) {
            source.sendFailure(Component.literal("Unable to load map world: " + worldId));
            return 0;
        }

        String mapId = "squad:" + mapName;
        ActiveMatch match = new ActiveMatch();
        match.mapId = mapId;
        match.mapName = mapName;
        match.worldId = worldId;
        match.teamA = "red";
        match.teamB = "blue";
        match.colorA = TEAM_A_COLOR;
        match.colorB = TEAM_B_COLOR;
        match.playersA.addAll(red);
        match.playersB.addAll(blue);
        match.bound1 = preset.bound1;
        match.bound2 = preset.bound2;

        activeMatches.put(mapId, match);
        worldToMapId.put(worldId, mapId);
        for (UUID uuid : red) {
            playerAssignments.put(uuid, new PlayerAssignment(mapId, "red"));
        }
        for (UUID uuid : blue) {
            playerAssignments.put(uuid, new PlayerAssignment(mapId, "blue"));
        }

        teleportPlayersToSpawn(server, level, preset.redSpawn, red);
        teleportPlayersToSpawn(server, level, preset.blueSpawn, blue);
        applyWorldBorderForMatch(level, match);

        VictoryMatchManager.INSTANCE.startMatch(match.view());
        FtbTeamsCompat.INSTANCE.syncMapTeams(mapId, server, "red", red, "blue", blue);

        source.sendSuccess(() -> Component.literal("Match started: " + mapName), true);
        return Command.SINGLE_SUCCESS;
    }

    private int endMatchByMapName(CommandSourceStack source, String rawMapName) {
        String mapName = normalizeMapName(rawMapName);
        ActiveMatch match = findActiveByMapName(mapName);
        if (match == null) {
            source.sendFailure(Component.literal("No active match for map: " + mapName));
            return 0;
        }

        endMatchInternal(match, source.getServer(), true, "manual");
        source.sendSuccess(() -> Component.literal("Match ended: " + mapName), true);
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

        VictoryMatchManager.INSTANCE.resetMap(match.mapId);
        FtbTeamsCompat.INSTANCE.disbandMapTeams(match.mapId, server);
        restoreWorldBorderForMatch(server, match);

        if (restorePreset) {
            restorePresetWorld(server, match.mapName, match.worldId);
        }

        LOGGER.info("Match ended: mapId='{}', reason='{}'", match.mapId, reason);
    }

    private void applyWorldBorderForMatch(ServerLevel level, ActiveMatch match) {
        if (match.bound1 == null || match.bound2 == null) {
            return;
        }
        WorldBorder border = level.getWorldBorder();
        match.worldBorderBackup = BorderSnapshot.capture(border);

        int minX = Math.min(match.bound1.x, match.bound2.x);
        int maxX = Math.max(match.bound1.x, match.bound2.x);
        int minZ = Math.min(match.bound1.z, match.bound2.z);
        int maxZ = Math.max(match.bound1.z, match.bound2.z);

        double width = Math.max(1.0D, (maxX - minX) + 1.0D);
        double depth = Math.max(1.0D, (maxZ - minZ) + 1.0D);
        // World border is square, so use the larger side to fully cover the selected area.
        double size = Math.max(width, depth);
        double centerX = (minX + maxX + 1.0D) / 2.0D;
        double centerZ = (minZ + maxZ + 1.0D) / 2.0D;

        border.setCenter(centerX, centerZ);
        border.setSize(size);
    }

    private void restoreWorldBorderForMatch(MinecraftServer server, ActiveMatch match) {
        if (match.worldBorderBackup == null) {
            return;
        }
        ServerLevel level = getLevel(server, match.worldId);
        if (level == null) {
            return;
        }
        match.worldBorderBackup.apply(level.getWorldBorder());
        match.worldBorderBackup = null;
    }

    private ActiveMatch findActiveByMapName(String mapName) {
        for (ActiveMatch match : activeMatches.values()) {
            if (match.mapName.equals(mapName)) {
                return match;
            }
        }
        return null;
    }

    private void teleportPlayersToSpawn(MinecraftServer server, ServerLevel fallbackLevel, SpawnPoint spawn, Set<UUID> players) {
        ResourceLocation dim = ResourceLocation.tryParse(spawn.dimension);
        ServerLevel target = dim == null ? fallbackLevel : getLevel(server, dim);
        if (target == null) {
            target = fallbackLevel;
        }

        for (UUID uuid : players) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) {
                continue;
            }
            player.teleportTo(target, spawn.x, spawn.y, spawn.z, spawn.yaw, spawn.pitch);
            player.setRespawnPosition(target.dimension(), new net.minecraft.core.BlockPos((int) spawn.x, (int) spawn.y, (int) spawn.z), spawn.yaw, true, false);
        }
    }

    private ResourceLocation parseWorldId(String raw) {
        ResourceLocation parsed = ResourceLocation.tryParse(raw);
        if (parsed != null) {
            return parsed;
        }
        return new ResourceLocation("multiworld", sanitize(raw));
    }

    private ResourceLocation worldIdOf(MapPreset preset) {
        return preset == null || preset.worldId == null ? null : ResourceLocation.tryParse(preset.worldId);
    }

    private String validatePresetBeforeUse(MapPreset preset) {
        ResourceLocation boundWorld = worldIdOf(preset);
        if (boundWorld == null) {
            return "invalid world binding";
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

    private void loadMaps() {
        if (!Files.exists(storageFile)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(storageFile)) {
            Map<String, MapPreset> loaded = GSON.fromJson(reader, MAPS_TYPE);
            if (loaded != null) {
                mapPresets.clear();
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

    private void ensurePresetBackup(MinecraftServer server, MapPreset preset) {
        ResourceLocation worldId = worldIdOf(preset);
        if (worldId == null) {
            return;
        }
        Path worldDir = worldDirectory(server, worldId);
        Path backupDir = backupDirectory(worldId);
        if (Files.exists(backupDir)) {
            return;
        }
        if (!Files.exists(worldDir)) {
            LOGGER.warn("No world folder to backup for map '{}': {}", preset.mapName, worldDir);
            return;
        }
        copyDirectory(worldDir, backupDir);
    }

    private void ensureWorld(MinecraftServer server, ResourceLocation worldId) {
        if (getLevel(server, worldId) != null) {
            return;
        }

        runServerCommand(server, "mw create " + worldId.getPath() + " NORMAL");
        if (getLevel(server, worldId) != null) {
            return;
        }

        Path dir = worldDirectory(server, worldId);
        if (Files.exists(dir)) {
            multiworldLoadSavedWorld(server, dir, worldId.toString());
        }
    }

    private void restorePresetWorld(MinecraftServer server, String mapName, ResourceLocation worldId) {
        Path worldDir = worldDirectory(server, worldId);
        Path backupDir = backupDirectory(worldId);
        if (!Files.exists(backupDir)) {
            LOGGER.warn("No preset backup found for map '{}'. Expected: {}", mapName, backupDir);
            return;
        }

        evacuatePlayersInWorld(server, worldId);
        unloadWorld(server, worldId);
        multiworldDeleteWorld(worldId.toString());
        deleteDirectory(worldDir);
        copyDirectory(backupDir, worldDir);
        multiworldLoadSavedWorld(server, worldDir, worldId.toString());
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
        Path root = server.getWorldPath(LevelResource.ROOT);
        return root.resolve("dimensions").resolve(worldId.getNamespace()).resolve(worldId.getPath());
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

    private void multiworldDeleteWorld(String worldId) {
        try {
            Class<?> mw = Class.forName("me.isaiah.multiworld.MultiworldMod");
            Object creator = mw.getMethod("get_world_creator").invoke(null);
            if (creator != null) {
                creator.getClass().getMethod("delete_world", String.class).invoke(creator, worldId);
            }
        } catch (Exception ex) {
            LOGGER.warn("Failed to call Multiworld delete_world for {}", worldId, ex);
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
        ResourceLocation worldId;
        String teamA;
        String teamB;
        int colorA;
        int colorB;
        final Set<UUID> playersA = new HashSet<>();
        final Set<UUID> playersB = new HashSet<>();
        BoundPoint bound1;
        BoundPoint bound2;
        BorderSnapshot worldBorderBackup;

        ActiveMatchView view() {
            return new ActiveMatchView(
                    mapId,
                    mapName,
                    worldId,
                    teamA,
                    colorA,
                    teamB,
                    colorB,
                    Set.copyOf(playersA),
                    Set.copyOf(playersB)
            );
        }
    }

    private record PlayerAssignment(String mapId, String team) {}

    private static final class MapPreset {
        String mapName;
        String worldId;
        SpawnPoint redSpawn;
        SpawnPoint blueSpawn;
        BoundPoint bound1;
        BoundPoint bound2;
        boolean saved;
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

    private static final class BorderSnapshot {
        double centerX;
        double centerZ;
        double size;
        int warningBlocks;
        int warningTime;
        double damagePerBlock;
        double damageSafeZone;

        static BorderSnapshot capture(WorldBorder border) {
            BorderSnapshot snapshot = new BorderSnapshot();
            snapshot.centerX = border.getCenterX();
            snapshot.centerZ = border.getCenterZ();
            snapshot.size = border.getSize();
            snapshot.warningBlocks = border.getWarningBlocks();
            snapshot.warningTime = border.getWarningTime();
            snapshot.damagePerBlock = border.getDamagePerBlock();
            snapshot.damageSafeZone = border.getDamageSafeZone();
            return snapshot;
        }

        void apply(WorldBorder border) {
            border.setCenter(centerX, centerZ);
            border.setSize(size);
            border.setWarningBlocks(warningBlocks);
            border.setWarningTime(warningTime);
            border.setDamagePerBlock(damagePerBlock);
            border.setDamageSafeZone(damageSafeZone);
        }
    }
}
