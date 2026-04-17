package com.flowingsun.warpattern.cohmode.backpack;

import com.flowingsun.warpattern.cohmode.CohModeModels;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stores player-selected item ids for role backpack loadout slots.
 */
public final class CohRoleBackpackSelectionRepository {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path loadedPath;
    private static SelectionStore loadedStore;

    private CohRoleBackpackSelectionRepository() {
    }

    public static synchronized Path resolvePath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT)
                .resolve("serverconfig")
                .resolve("warpattern")
                .resolve("cohmode")
                .resolve("role_backpack_selections.json");
    }

    public static synchronized Map<Integer, String> getRoleSelections(MinecraftServer server, UUID playerId, CohModeModels.Role role) {
        if (server == null || playerId == null || role == null) {
            return Map.of();
        }
        SelectionStore store = loadOrCreate(server);
        PlayerSelection player = store.players.get(playerId.toString());
        if (player == null || player.roles == null) {
            return Map.of();
        }
        RoleSelection roleSelection = player.roles.get(role.name());
        if (roleSelection == null || roleSelection.slots == null || roleSelection.slots.isEmpty()) {
            return Map.of();
        }
        Map<Integer, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : roleSelection.slots.entrySet()) {
            try {
                int slot = Integer.parseInt(entry.getKey());
                String item = entry.getValue();
                if (slot >= 0 && slot < CohRoleBackpackConfig.SLOT_COUNT && item != null && !item.isBlank()) {
                    out.put(slot, item);
                }
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    public static synchronized String getSelectedItem(MinecraftServer server, UUID playerId, CohModeModels.Role role, int slotIndex) {
        return getRoleSelections(server, playerId, role).get(slotIndex);
    }

    public static synchronized void setSelectedItem(MinecraftServer server, UUID playerId, CohModeModels.Role role, int slotIndex, String itemId) {
        if (server == null || playerId == null || role == null || slotIndex < 0 || slotIndex >= CohRoleBackpackConfig.SLOT_COUNT) {
            return;
        }
        String normalizedItem = itemId == null ? "" : itemId.trim();
        SelectionStore store = loadOrCreate(server);
        PlayerSelection player = store.players.computeIfAbsent(playerId.toString(), k -> new PlayerSelection());
        if (player.roles == null) {
            player.roles = new LinkedHashMap<>();
        }
        RoleSelection roleSelection = player.roles.computeIfAbsent(role.name(), k -> new RoleSelection());
        if (roleSelection.slots == null) {
            roleSelection.slots = new LinkedHashMap<>();
        }
        if (normalizedItem.isEmpty()) {
            roleSelection.slots.remove(Integer.toString(slotIndex));
        } else {
            roleSelection.slots.put(Integer.toString(slotIndex), normalizedItem);
        }
        save();
    }

    public static synchronized SelectionStore loadOrCreate(MinecraftServer server) {
        Path path = resolvePath(server);
        if (loadedStore != null && path.equals(loadedPath)) {
            return loadedStore;
        }
        loadedPath = path;
        try {
            if (Files.exists(path)) {
                try (Reader reader = Files.newBufferedReader(path)) {
                    SelectionStore parsed = GSON.fromJson(reader, SelectionStore.class);
                    loadedStore = parsed == null ? new SelectionStore() : parsed;
                    loadedStore.ensureDefaults();
                    return loadedStore;
                }
            }
        } catch (Exception ignored) {
        }
        loadedStore = new SelectionStore();
        save();
        return loadedStore;
    }

    public static synchronized void save() {
        if (loadedPath == null || loadedStore == null) {
            return;
        }
        try {
            Files.createDirectories(loadedPath.getParent());
            try (Writer writer = Files.newBufferedWriter(loadedPath)) {
                GSON.toJson(loadedStore, writer);
            }
        } catch (Exception ignored) {
        }
    }

    public static final class SelectionStore {
        public Map<String, PlayerSelection> players = new LinkedHashMap<>();

        void ensureDefaults() {
            if (players == null) {
                players = new LinkedHashMap<>();
            }
            for (PlayerSelection player : players.values()) {
                if (player != null && player.roles == null) {
                    player.roles = new LinkedHashMap<>();
                }
                if (player == null || player.roles == null) {
                    continue;
                }
                for (RoleSelection role : player.roles.values()) {
                    if (role != null && role.slots == null) {
                        role.slots = new LinkedHashMap<>();
                    }
                }
            }
        }
    }

    public static final class PlayerSelection {
        public Map<String, RoleSelection> roles = new LinkedHashMap<>();
    }

    public static final class RoleSelection {
        public Map<String, String> slots = new LinkedHashMap<>();
    }
}
