package com.flowingsun.warpattern.cohmode.client;

import com.flowingsun.warpattern.cohmode.CohModeModels;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Role loadout customization screen: 5 slots with per-slot option dropdowns.
 */
public class CohRoleBackpackScreen extends Screen {
    private final Screen parent;
    private final CohModeModels.Role role;

    private int panelLeft;
    private int panelTop;
    private int panelRight;
    private int panelBottom;
    private int expandedSlot = -1;
    private final List<OptionIconEntry> optionIcons = new ArrayList<>();

    public CohRoleBackpackScreen(Screen parent, CohModeModels.Role role) {
        super(Component.literal("Role Loadout"));
        this.parent = parent;
        this.role = role == null ? CohModeModels.Role.RIFLEMAN : role;
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(520, width - 24);
        int panelHeight = Math.min(300, height - 28);
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;
        panelRight = panelLeft + panelWidth;
        panelBottom = panelTop + panelHeight;
        rebuildLocalWidgets();
    }

    private void rebuildLocalWidgets() {
        clearWidgets();
        optionIcons.clear();
        addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("Refresh"), b ->
                CohModeClientState.sendAction("refresh", CohModeClientState.json()))
                .bounds(panelLeft + 12, panelTop + 10, 90, 20).build());
        addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("Back"), b -> onClose())
                .bounds(panelRight - 102, panelTop + 10, 90, 20).build());

        CohModeModels.RoleBackpackView roleView = roleView();
        if (roleView == null || roleView.slots == null || roleView.slots.isEmpty()) {
            return;
        }

        int y = panelTop + 52;
        List<CohModeModels.BackpackSlotView> slots = roleView.slots.stream()
                .sorted(Comparator.comparingInt(slot -> slot.slotIndex))
                .toList();
        for (CohModeModels.BackpackSlotView slot : slots) {
            if (y + 20 > panelBottom - 20) {
                break;
            }
            final int slotIndex = slot.slotIndex;
            addRenderableWidget(CohFlatButton.flatBuilder(Component.literal(expandedSlot == slotIndex ? "Collapse" : "Choose"), b -> {
                expandedSlot = expandedSlot == slotIndex ? -1 : slotIndex;
                rebuildLocalWidgets();
            }).bounds(panelLeft + 192, y - 2, 96, 20).build());

            if (expandedSlot == slotIndex && slot.options != null && !slot.options.isEmpty()) {
                int optionY = y + 22;
                for (CohModeModels.BackpackItemOptionView option : slot.options) {
                    if (optionY + 20 > panelBottom - 14) {
                        break;
                    }
                    String label = trimLabel(optionLabel(option), 34);
                    addRenderableWidget(CohFlatButton.flatBuilder(Component.literal(label), b -> {
                        sendSelection(slotIndex, option.itemId);
                        expandedSlot = -1;
                        rebuildLocalWidgets();
                    }).bounds(panelLeft + 192, optionY, 300, 20).build());
                    optionIcons.add(new OptionIconEntry(panelLeft + 196, optionY + 2, itemStackOf(option.itemId, option.count)));
                    optionY += 22;
                }
                y = optionY + 2;
            } else {
                y += 28;
            }
        }
    }

    private void sendSelection(int slotIndex, String itemId) {
        JsonObject payload = CohModeClientState.json();
        payload.addProperty("role", role.name());
        payload.addProperty("slot", slotIndex);
        payload.addProperty("item", itemId == null ? "" : itemId);
        CohModeClientState.sendAction("set_backpack_item", payload);
    }

    private CohModeModels.RoleBackpackView roleView() {
        CohModeModels.LobbyStateView state = CohModeClientState.state();
        if (state == null || state.roleBackpacks == null) {
            return null;
        }
        for (CohModeModels.RoleBackpackView view : state.roleBackpacks) {
            if (view != null && view.role == role) {
                return view;
            }
        }
        return null;
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        graphics.fillGradient(0, 0, width, height, CohUiTheme.BG_TOP, CohUiTheme.BG_BOTTOM);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        drawPanel(graphics, panelLeft, panelTop, panelRight, panelBottom);
        super.render(graphics, mouseX, mouseY, partialTick);

        CohModeModels.RoleBackpackView roleView = roleView();
        graphics.fill(panelLeft + 12, panelTop + 30, panelRight - 12, panelTop + 31, CohUiTheme.DIVIDER);
        graphics.fill(panelLeft + 8, panelTop + 7, panelLeft + 11, panelTop + 21, CohUiTheme.HOVER_BORDER);
        graphics.drawCenteredString(font, role.label + " Loadout", width / 2, panelTop + 10, CohUiTheme.TEXT_PRIMARY);
        graphics.drawString(font, "Each slot supports configurable option lists from role_backpacks.json", panelLeft + 12, panelTop + 36, CohUiTheme.TEXT_SECONDARY, false);

        if (roleView == null || roleView.slots == null || roleView.slots.isEmpty()) {
            graphics.drawString(font, "No loadout slot configuration found for this role.", panelLeft + 12, panelTop + 60, 0xFFFF9090, false);
            return;
        }

        int y = panelTop + 52;
        for (CohModeModels.BackpackSlotView slot : roleView.slots.stream().sorted(Comparator.comparingInt(v -> v.slotIndex)).toList()) {
            if (y + 18 > panelBottom - 20) {
                break;
            }
            String selectedText = selectedLabel(slot);
            graphics.drawString(font, "Slot " + (slot.slotIndex + 1) + ": " + slot.slotName, panelLeft + 14, y + 2, CohUiTheme.TEXT_PRIMARY, false);
            graphics.drawString(font, trimLabel(selectedText, 44), panelLeft + 296, y + 2, CohUiTheme.TEXT_SECONDARY, false);
            if (expandedSlot == slot.slotIndex && slot.options != null && !slot.options.isEmpty()) {
                int optionY = y + 22 + (slot.options.size() * 22);
                y = Math.min(optionY + 2, panelBottom - 18);
            } else {
                y += 28;
            }
        }

        CohModeModels.LobbyStateView state = CohModeClientState.state();
        if (state.statusText != null && !state.statusText.isEmpty()) {
            graphics.drawCenteredString(font, state.statusText, width / 2, panelBottom - 14, 0xFFF0F0A0);
        }

        for (OptionIconEntry icon : optionIcons) {
            if (icon.stack == null || icon.stack.isEmpty()) {
                continue;
            }
            graphics.renderItem(icon.stack, icon.x, icon.y);
        }
    }

    private String selectedLabel(CohModeModels.BackpackSlotView slot) {
        if (slot == null || slot.options == null || slot.options.isEmpty()) {
            return "<none>";
        }
        String selected = slot.selectedItemId == null ? "" : slot.selectedItemId;
        for (CohModeModels.BackpackItemOptionView option : slot.options) {
            if (option != null && option.itemId != null && option.itemId.equalsIgnoreCase(selected)) {
                return optionLabel(option);
            }
        }
        return optionLabel(slot.options.get(0));
    }

    private String optionLabel(CohModeModels.BackpackItemOptionView option) {
        if (option == null) {
            return "<none>";
        }
        String base = option.label == null || option.label.isBlank() ? option.itemId : option.label;
        return option.count > 1 ? base + " x" + option.count : base;
    }

    private String trimLabel(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        if (max <= 3) {
            return text.substring(0, Math.max(0, max));
        }
        return text.substring(0, max - 3) + "...";
    }

    private ItemStack itemStackOf(String itemId, int count) {
        ResourceLocation id = ResourceLocation.tryParse(itemId == null ? "" : itemId);
        if (id == null) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item, Math.max(1, count));
    }

    private void drawPanel(GuiGraphics graphics, int left, int top, int right, int bottom) {
        graphics.fillGradient(left, top, right, bottom, CohUiTheme.CARD_BG_TOP, CohUiTheme.CARD_BG_BOTTOM);
        graphics.fill(left, top, right, top + 1, CohUiTheme.HOVER_BORDER_SEMI);
        graphics.fill(left, bottom - 1, right, bottom, CohUiTheme.BORDER_SUBTLE);
        graphics.fill(left, top, left + 1, bottom, CohUiTheme.BORDER_SUBTLE);
        graphics.fill(right - 1, top, right, bottom, CohUiTheme.BORDER_SUBTLE);
        graphics.fillGradient(left + 1, top + 1, right - 1, top + 16, 0x1CFFFFFF, 0x02000000);
    }

    private record OptionIconEntry(int x, int y, ItemStack stack) {
    }
}
