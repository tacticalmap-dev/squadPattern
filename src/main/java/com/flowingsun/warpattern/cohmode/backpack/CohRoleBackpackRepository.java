package com.flowingsun.warpattern.cohmode.backpack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Repository for role backpack config persisted per server world.
 */
public final class CohRoleBackpackRepository {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path loadedPath;
    private static CohRoleBackpackConfig loadedConfig;

    private CohRoleBackpackRepository() {
    }

    public static synchronized Path resolvePath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT)
                .resolve("serverconfig")
                .resolve("warpattern")
                .resolve("cohmode")
                .resolve("role_backpacks.json");
    }

    public static synchronized CohRoleBackpackConfig loadOrCreate(MinecraftServer server) {
        Path path = resolvePath(server);
        if (loadedConfig != null && path.equals(loadedPath)) {
            return loadedConfig;
        }
        loadedPath = path;

        try {
            if (Files.exists(path)) {
                try (Reader reader = Files.newBufferedReader(path)) {
                    CohRoleBackpackConfig parsed = GSON.fromJson(reader, CohRoleBackpackConfig.class);
                    loadedConfig = parsed == null ? CohRoleBackpackConfig.createDefault() : parsed;
                    boolean changed = false;
                    if (loadedConfig.roleBackpacks == null || loadedConfig.roleBackpacks.isEmpty()) {
                        loadedConfig = CohRoleBackpackConfig.createDefault();
                        changed = true;
                    } else {
                        changed = loadedConfig.ensureDefaults();
                    }
                    if (changed) {
                        save();
                    }
                    return loadedConfig;
                }
            }
        } catch (Exception ignored) {
        }

        loadedConfig = CohRoleBackpackConfig.createDefault();
        loadedConfig.ensureDefaults();
        save();
        return loadedConfig;
    }

    public static synchronized CohRoleBackpackConfig reload(MinecraftServer server) {
        loadedPath = null;
        loadedConfig = null;
        return loadOrCreate(server);
    }

    public static synchronized void save() {
        if (loadedPath == null || loadedConfig == null) {
            return;
        }
        try {
            Files.createDirectories(loadedPath.getParent());
            try (Writer writer = Files.newBufferedWriter(loadedPath)) {
                GSON.toJson(loadedConfig, writer);
            }
        } catch (Exception ignored) {
        }
    }
}
