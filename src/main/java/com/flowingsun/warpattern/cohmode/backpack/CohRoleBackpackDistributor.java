package com.flowingsun.warpattern.cohmode.backpack;

import com.flowingsun.warpattern.cohmode.CohModeModels;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

/**
 * Applies configured role backpacks to players.
 */
public final class CohRoleBackpackDistributor {
    private static final Logger LOGGER = LogUtils.getLogger();

    private CohRoleBackpackDistributor() {
    }

    public static void distribute(MinecraftServer server, Map<UUID, CohModeModels.Role> roleByPlayer) {
        if (server == null || roleByPlayer == null || roleByPlayer.isEmpty()) {
            return;
        }
        CohRoleBackpackConfig config = CohRoleBackpackRepository.loadOrCreate(server);
        for (Map.Entry<UUID, CohModeModels.Role> entry : roleByPlayer.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            CohModeModels.Role role = entry.getValue() == null ? CohModeModels.Role.RIFLEMAN : entry.getValue();
            applyBackpack(server, player, role, config);
        }
    }

    private static void applyBackpack(MinecraftServer server, ServerPlayer player, CohModeModels.Role role, CohRoleBackpackConfig config) {
        if (player == null || config == null) {
            return;
        }
        CohRoleBackpackConfig.RoleBackpack backpack = config.resolveRoleBackpack(role);
        if (backpack == null) {
            return;
        }

        if (backpack.slots != null && !backpack.slots.isEmpty()) {
            Map<Integer, String> selections = CohRoleBackpackSelectionRepository.getRoleSelections(server, player.getUUID(), role);
            backpack.slots.stream()
                    .filter(slot -> slot != null)
                    .sorted(Comparator.comparingInt(slot -> slot.slotIndex))
                    .forEach(slot -> {
                        String selectedItem = selections.get(slot.slotIndex);
                        CohRoleBackpackConfig.ItemEntry resolved = resolveSlotSelection(slot, selectedItem);
                        if (resolved != null) {
                            applyItemByGive(server, player, resolved);
                        }
                    });
            return;
        }

        // Legacy fallback for old config files that only contain "items".
        if (backpack.items == null || backpack.items.isEmpty()) {
            return;
        }
        for (CohRoleBackpackConfig.ItemEntry itemEntry : backpack.items) {
            applyItemByGive(server, player, itemEntry);
        }
    }

    private static CohRoleBackpackConfig.ItemEntry resolveSlotSelection(CohRoleBackpackConfig.SlotEntry slot, String selectedItem) {
        if (slot == null || slot.options == null || slot.options.isEmpty()) {
            return null;
        }
        if (selectedItem != null && !selectedItem.isBlank()) {
            for (CohRoleBackpackConfig.ItemEntry option : slot.options) {
                if (option != null && option.item != null && option.item.equalsIgnoreCase(selectedItem)) {
                    return option;
                }
            }
        }
        if (slot.defaultItem != null && !slot.defaultItem.isBlank()) {
            for (CohRoleBackpackConfig.ItemEntry option : slot.options) {
                if (option != null && option.item != null && option.item.equalsIgnoreCase(slot.defaultItem)) {
                    return option;
                }
            }
        }
        return slot.options.get(0);
    }

    private static void applyItemByGive(MinecraftServer server, ServerPlayer player, CohRoleBackpackConfig.ItemEntry itemEntry) {
        if (server == null || player == null || itemEntry == null || itemEntry.item == null || itemEntry.item.isBlank()) {
            return;
        }
        ResourceLocation itemId = ResourceLocation.tryParse(itemEntry.item);
        if (itemId == null) {
            LOGGER.warn("Invalid role backpack item id '{}' for player '{}'", itemEntry.item, player.getGameProfile().getName());
            return;
        }
        Item item = BuiltInRegistries.ITEM.get(itemId);
        if (item == null || item == Items.AIR) {
            LOGGER.warn("Unknown role backpack item id '{}' for player '{}'", itemId, player.getGameProfile().getName());
            return;
        }

        String nbtPart = itemEntry.nbt == null || itemEntry.nbt.isBlank() ? "" : itemEntry.nbt.trim();
        String itemExpr = itemId + nbtPart;
        int count = Math.max(1, itemEntry.count);

        CommandSourceStack source = server.createCommandSourceStack()
                .withSuppressedOutput()
                .withPermission(4)
                .withEntity(player)
                .withPosition(player.position());
        String command = "give @s " + itemExpr + " " + count;
        int result = server.getCommands().performPrefixedCommand(source, command);
        if (result <= 0) {
            LOGGER.warn("Failed to run give command '{}' for player '{}'", command, player.getGameProfile().getName());
        }
    }
}
