package com.flowingsun.warpattern.cohmode.backpack;

import com.flowingsun.warpattern.cohmode.CohModeModels;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Role-based backpack customization config for cohmode.
 * <p>
 * New format: role -> 5 loadout slots -> candidate item list.
 * Legacy field {@code items} is still supported as a fallback.
 */
public final class CohRoleBackpackConfig {
    public static final int SLOT_COUNT = 5;

    public Map<String, RoleBackpack> roleBackpacks = new LinkedHashMap<>();

    public static final class RoleBackpack {
        public String name;
        public List<SlotEntry> slots = new ArrayList<>();
        // Legacy fallback for older config shape.
        public List<ItemEntry> items = new ArrayList<>();
    }

    public static final class SlotEntry {
        public int slotIndex;
        public String slotName;
        // Inventory target slot; -1 means append/add to inventory.
        public int inventorySlot = -1;
        // Item id selected by default when player has no saved choice.
        public String defaultItem;
        public List<ItemEntry> options = new ArrayList<>();
    }

    public static final class ItemEntry {
        // -1 means append to first available inventory slot.
        public int slot = -1;
        public String item;
        public int count = 1;
        public String nbt;
        public String label;
    }

    public static CohRoleBackpackConfig createDefault() {
        CohRoleBackpackConfig config = new CohRoleBackpackConfig();
        for (CohModeModels.Role role : CohModeModels.Role.values()) {
            config.roleBackpacks.put(role.name(), createDefaultRoleBackpack(role));
        }
        return config;
    }

    public boolean ensureDefaults() {
        boolean changed = false;
        if (roleBackpacks == null) {
            roleBackpacks = new LinkedHashMap<>();
            changed = true;
        }

        for (CohModeModels.Role role : CohModeModels.Role.values()) {
            String canonicalKey = role.name();
            RoleBackpack backpack = resolveRoleBackpack(role);
            if (backpack == null) {
                roleBackpacks.put(canonicalKey, createDefaultRoleBackpack(role));
                changed = true;
                continue;
            }
            if (!roleBackpacks.containsKey(canonicalKey)) {
                roleBackpacks.put(canonicalKey, backpack);
                changed = true;
            }
            changed |= normalizeRoleBackpack(role, backpack);
        }
        return changed;
    }

    public RoleBackpack resolveRoleBackpack(CohModeModels.Role role) {
        if (role == null || roleBackpacks == null) {
            return null;
        }
        RoleBackpack exact = roleBackpacks.get(role.name());
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<String, RoleBackpack> entry : roleBackpacks.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(role.name())) {
                return entry.getValue();
            }
        }
        return null;
    }

    public static SlotEntry resolveSlot(RoleBackpack backpack, int slotIndex) {
        if (backpack == null || backpack.slots == null) {
            return null;
        }
        for (SlotEntry slot : backpack.slots) {
            if (slot != null && slot.slotIndex == slotIndex) {
                return slot;
            }
        }
        return null;
    }

    private static RoleBackpack createDefaultRoleBackpack(CohModeModels.Role role) {
        RoleBackpack backpack = new RoleBackpack();
        backpack.name = role.label;
        for (int slotIndex = 0; slotIndex < SLOT_COUNT; slotIndex++) {
            backpack.slots.add(createDefaultSlot(role, slotIndex));
        }
        return backpack;
    }

    private boolean normalizeRoleBackpack(CohModeModels.Role role, RoleBackpack backpack) {
        boolean changed = false;
        if (backpack.name == null || backpack.name.isBlank()) {
            backpack.name = role.label;
            changed = true;
        }
        if (backpack.slots == null) {
            backpack.slots = new ArrayList<>();
            changed = true;
        }
        if (backpack.items == null) {
            backpack.items = new ArrayList<>();
            changed = true;
        }

        // Convert legacy "items" format into the slot model when slot config is missing.
        if (backpack.slots.isEmpty() && !backpack.items.isEmpty()) {
            int slotIndex = 0;
            for (ItemEntry item : backpack.items) {
                if (item == null || item.item == null || item.item.isBlank() || slotIndex >= SLOT_COUNT) {
                    continue;
                }
                SlotEntry slot = new SlotEntry();
                slot.slotIndex = slotIndex;
                slot.slotName = defaultSlotName(slotIndex);
                slot.inventorySlot = item.slot;
                slot.defaultItem = item.item;
                slot.options.add(copyItem(item));
                backpack.slots.add(slot);
                slotIndex++;
            }
            changed = true;
        }

        Map<Integer, SlotEntry> byIndex = new HashMap<>();
        for (SlotEntry slot : backpack.slots) {
            if (slot == null || slot.slotIndex < 0 || slot.slotIndex >= SLOT_COUNT || byIndex.containsKey(slot.slotIndex)) {
                changed = true;
                continue;
            }
            byIndex.put(slot.slotIndex, slot);
        }

        List<SlotEntry> normalized = new ArrayList<>();
        for (int slotIndex = 0; slotIndex < SLOT_COUNT; slotIndex++) {
            SlotEntry slot = byIndex.get(slotIndex);
            if (slot == null) {
                slot = createDefaultSlot(role, slotIndex);
                changed = true;
            } else {
                changed |= normalizeSlot(role, slot, slotIndex);
            }
            normalized.add(slot);
        }
        normalized.sort(Comparator.comparingInt(slot -> slot.slotIndex));
        if (!sameShape(backpack.slots, normalized)) {
            changed = true;
        }
        backpack.slots = normalized;
        return changed;
    }

    private static boolean sameShape(List<SlotEntry> current, List<SlotEntry> normalized) {
        if (current == null || normalized == null || current.size() != normalized.size()) {
            return false;
        }
        for (int i = 0; i < current.size(); i++) {
            SlotEntry a = current.get(i);
            SlotEntry b = normalized.get(i);
            if (a == null || b == null) {
                return false;
            }
            if (a.slotIndex != b.slotIndex) {
                return false;
            }
        }
        return true;
    }

    private static boolean normalizeSlot(CohModeModels.Role role, SlotEntry slot, int slotIndex) {
        boolean changed = false;
        slot.slotIndex = slotIndex;
        if (slot.slotName == null || slot.slotName.isBlank()) {
            slot.slotName = defaultSlotName(slotIndex);
            changed = true;
        }
        if (slot.options == null) {
            slot.options = new ArrayList<>();
            changed = true;
        }
        List<ItemEntry> cleanedOptions = new ArrayList<>();
        for (ItemEntry option : slot.options) {
            if (option == null || option.item == null || option.item.isBlank()) {
                changed = true;
                continue;
            }
            if (option.count <= 0) {
                option.count = 1;
                changed = true;
            }
            cleanedOptions.add(option);
        }
        if (cleanedOptions.isEmpty()) {
            SlotEntry fallback = createDefaultSlot(role, slotIndex);
            cleanedOptions.addAll(fallback.options);
            if (slot.defaultItem == null || slot.defaultItem.isBlank()) {
                slot.defaultItem = fallback.defaultItem;
            }
            if (slot.inventorySlot < -1) {
                slot.inventorySlot = fallback.inventorySlot;
            }
            changed = true;
        }
        slot.options = cleanedOptions;
        if (slot.defaultItem == null || slot.defaultItem.isBlank()) {
            slot.defaultItem = cleanedOptions.get(0).item;
            changed = true;
        }
        if (slot.inventorySlot < -1) {
            slot.inventorySlot = -1;
            changed = true;
        }
        return changed;
    }

    private static SlotEntry createDefaultSlot(CohModeModels.Role role, int slotIndex) {
        SlotEntry slot = new SlotEntry();
        slot.slotIndex = slotIndex;
        slot.slotName = defaultSlotName(slotIndex);
        slot.inventorySlot = -1;

        switch (slotIndex) {
            case 0 -> {
                slot.options.add(option(primaryForRole(role), 1, null, "Primary"));
                slot.options.add(option("minecraft:stone_sword", 1, null, "Fallback"));
            }
            case 1 -> {
                slot.options.add(option(secondaryForRole(role), 1, null, "Secondary"));
                slot.options.add(option("minecraft:shield", 1, null, "Shield"));
            }
            case 2 -> {
                slot.options.add(option(utilityForRole(role), 1, null, "Utility"));
                slot.options.add(option("minecraft:torch", 16, null, "Torch x16"));
            }
            case 3 -> {
                slot.options.add(option("minecraft:cooked_beef", 16, null, "Food x16"));
                slot.options.add(option("minecraft:bread", 16, null, "Bread x16"));
            }
            case 4 -> {
                slot.options.add(option("minecraft:golden_apple", 2, null, "Support Item"));
                slot.options.add(option("minecraft:arrow", 32, null, "Arrow x32"));
            }
            default -> slot.options.add(option("minecraft:stick", 1, null, "Default"));
        }
        slot.defaultItem = slot.options.get(0).item;
        return slot;
    }

    private static String primaryForRole(CohModeModels.Role role) {
        return switch (Objects.requireNonNullElse(role, CohModeModels.Role.RIFLEMAN)) {
            case COMMANDER -> "minecraft:compass";
            case RIFLEMAN -> "minecraft:iron_sword";
            case ASSAULT -> "minecraft:iron_axe";
            case SUPPORT -> "minecraft:shield";
            case SNIPER -> "minecraft:bow";
        };
    }

    private static String secondaryForRole(CohModeModels.Role role) {
        return switch (Objects.requireNonNullElse(role, CohModeModels.Role.RIFLEMAN)) {
            case COMMANDER -> "minecraft:spyglass";
            case RIFLEMAN -> "minecraft:crossbow";
            case ASSAULT -> "minecraft:crossbow";
            case SUPPORT -> "minecraft:iron_sword";
            case SNIPER -> "minecraft:spyglass";
        };
    }

    private static String utilityForRole(CohModeModels.Role role) {
        return switch (Objects.requireNonNullElse(role, CohModeModels.Role.RIFLEMAN)) {
            case COMMANDER -> "minecraft:filled_map";
            case RIFLEMAN -> "minecraft:water_bucket";
            case ASSAULT -> "minecraft:tnt";
            case SUPPORT -> "minecraft:honey_bottle";
            case SNIPER -> "minecraft:ender_pearl";
        };
    }

    private static ItemEntry option(String itemId, int count, String nbt, String label) {
        ItemEntry entry = new ItemEntry();
        entry.item = itemId;
        entry.count = count;
        entry.nbt = nbt;
        entry.label = label;
        return entry;
    }

    private static ItemEntry copyItem(ItemEntry source) {
        ItemEntry copy = new ItemEntry();
        copy.slot = source.slot;
        copy.item = source.item;
        copy.count = source.count <= 0 ? 1 : source.count;
        copy.nbt = source.nbt;
        copy.label = source.label;
        return copy;
    }

    private static String defaultSlotName(int slotIndex) {
        return "Loadout Slot " + (slotIndex + 1);
    }
}
