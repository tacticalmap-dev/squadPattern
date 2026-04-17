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
    private static final int PANEL_MAX_WIDTH = 520;
    private static final int PANEL_MAX_HEIGHT = 300;
    private static final int PANEL_MARGIN = 24;
    private static final int HEADER_Y = 52;
    private static final int ROW_HEIGHT = 28;
    private static final int OPTION_ROW_HEIGHT = 22;
    private static final int BUTTON_X = 192;
    private static final int BUTTON_WIDTH = 96;
    private static final int OPTION_BUTTON_WIDTH = 300;
    private static final int OPTION_ICON_X_OFFSET = 4;
    private static final int OPTION_ICON_Y_OFFSET = 2;

    private final Screen parent;
    private final CohModeModels.Role role;

    private int panelLeft;
    private int panelTop;
    private int panelRight;
    private int panelBottom;
    private int expandedSlot = -1;
    private final List<OptionIconEntry> optionIcons = new ArrayList<>();

    public CohRoleBackpackScreen(Screen parent, CohModeModels.Role role) {
        super(Component.literal("兵种装备配置"));
        this.parent = parent;
        this.role = role == null ? CohModeModels.Role.RIFLEMAN : role;
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(PANEL_MAX_WIDTH, width - PANEL_MARGIN);
        int panelHeight = Math.min(PANEL_MAX_HEIGHT, height - 28);
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;
        panelRight = panelLeft + panelWidth;
        panelBottom = panelTop + panelHeight;
        rebuildLocalWidgets();
    }

    private void rebuildLocalWidgets() {
        clearWidgets();
        optionIcons.clear();
        addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("刷新"), b ->
                CohModeClientState.sendAction("refresh", CohModeClientState.json()))
                .bounds(panelLeft + 12, panelTop + 10, 90, 20).build());
        addRenderableWidget(CohFlatButton.flatBuilder(Component.literal("返回"), b -> onClose())
                .bounds(panelRight - 102, panelTop + 10, 90, 20).build());

        CohModeModels.RoleBackpackView roleView = roleView();
        if (roleView == null || roleView.slots == null || roleView.slots.isEmpty()) {
            return;
        }

        int panelWidth = panelRight - panelLeft;
        int buttonX = Math.min(192, (int)(panelWidth * 0.4));
        int optionButtonWidth = Math.min(300, panelWidth - buttonX - 12);
        int buttonWidth = Math.min(96, optionButtonWidth);

        int y = panelTop + HEADER_Y;
        List<CohModeModels.BackpackSlotView> slots = sortedSlots(roleView);
        for (CohModeModels.BackpackSlotView slot : slots) {
            if (y + 20 > panelBottom - 20) {
                break;
            }
            final int slotIndex = slot.slotIndex;
            addRenderableWidget(CohFlatButton.flatBuilder(Component.literal(expandedSlot == slotIndex ? "收起" : "选择"), b -> {
                expandedSlot = expandedSlot == slotIndex ? -1 : slotIndex;
                rebuildLocalWidgets();
            }).bounds(panelLeft + buttonX, y - 2, buttonWidth, 20).build());

            if (expandedSlot == slotIndex && slot.options != null && !slot.options.isEmpty()) {
                int optionY = y + OPTION_ROW_HEIGHT;
                for (CohModeModels.BackpackItemOptionView option : slot.options) {
                    if (optionY + 20 > panelBottom - 14) {
                        break;
                    }
                    String label = trimLabel(optionLabel(option), 34);
                    addRenderableWidget(CohFlatButton.flatBuilder(Component.literal(label), b -> {
                        sendSelection(slotIndex, option.itemId);
                        expandedSlot = -1;
                        rebuildLocalWidgets();
                    }).bounds(panelLeft + buttonX, optionY, optionButtonWidth, 20).build());
                    optionIcons.add(new OptionIconEntry(
                            panelLeft + buttonX + OPTION_ICON_X_OFFSET,
                            optionY + OPTION_ICON_Y_OFFSET,
                            itemStackOf(option.itemId, option.count))
                    );
                    optionY += OPTION_ROW_HEIGHT;
                }
                y = optionY + 2;
            } else {
                y += ROW_HEIGHT;
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
        
        // Tactical grid background
        int gridColor = 0x10FFFFFF;
        for (int x = 0; x < width; x += 20) {
            graphics.fill(x, 0, x + 1, height, gridColor);
        }
        for (int y = 0; y < height; y += 20) {
            graphics.fill(0, y, width, y + 1, gridColor);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        drawPanel(graphics, panelLeft, panelTop, panelRight, panelBottom);
        super.render(graphics, mouseX, mouseY, partialTick);

        CohModeModels.RoleBackpackView roleView = roleView();
        graphics.fill(panelLeft + 12, panelTop + 30, panelRight - 12, panelTop + 31, CohUiTheme.DIVIDER);
        graphics.fill(panelLeft + 8, panelTop + 7, panelLeft + 11, panelTop + 21, CohUiTheme.HOVER_BORDER);
        graphics.drawCenteredString(font, role.label + "装备配置", width / 2, panelTop + 10, CohUiTheme.TEXT_PRIMARY);
        graphics.drawString(font, "> 为当前兵种配置出战装备栏位", panelLeft + 12, panelTop + 36, CohUiTheme.TEXT_HIGHLIGHT, false);

        if (roleView == null || roleView.slots == null || roleView.slots.isEmpty()) {
            graphics.drawString(font, "[!] 当前兵种没有可用的装备配置。", panelLeft + 12, panelTop + 60, 0xFFFF5050, false);
            return;
        }

        int panelWidth = panelRight - panelLeft;
        int buttonX = Math.min(192, (int)(panelWidth * 0.4));
        int textRightX = buttonX + Math.min(96, panelWidth - buttonX - 12) + 8;

        int y = panelTop + HEADER_Y;
        for (CohModeModels.BackpackSlotView slot : sortedSlots(roleView)) {
            if (y + 18 > panelBottom - 20) {
                break;
            }
            String selectedText = selectedLabel(slot);
            graphics.drawString(font, "槽位 " + (slot.slotIndex + 1) + "：" + slot.slotName, panelLeft + 14, y + 2, CohUiTheme.TEXT_PRIMARY, false);
            graphics.drawString(font, trimLabel(selectedText, 44), panelLeft + textRightX, y + 2, CohUiTheme.TEXT_SECONDARY, false);
            if (expandedSlot == slot.slotIndex && slot.options != null && !slot.options.isEmpty()) {
                int optionY = y + OPTION_ROW_HEIGHT + (slot.options.size() * OPTION_ROW_HEIGHT);
                y = Math.min(optionY + 2, panelBottom - 18);
            } else {
                y += ROW_HEIGHT;
            }
        }

        CohModeModels.LobbyStateView state = CohModeClientState.state();
        if (state.statusText != null && !state.statusText.isEmpty()) {
            graphics.drawCenteredString(font, "系统消息：" + state.statusText, width / 2, panelBottom - 14, CohUiTheme.TEXT_HIGHLIGHT);
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
            return "<无>";
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
            return "<无>";
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

    private List<CohModeModels.BackpackSlotView> sortedSlots(CohModeModels.RoleBackpackView roleView) {
        if (roleView == null || roleView.slots == null) {
            return List.of();
        }
        return roleView.slots.stream()
                .sorted(Comparator.comparingInt(slot -> slot.slotIndex))
                .toList();
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
        
        int borderColor = CohUiTheme.HOVER_BORDER_SEMI;
        
        // Draw tactical border with corners
        graphics.fill(left + 2, top, right - 2, top + 1, borderColor);
        graphics.fill(left + 2, bottom - 1, right - 2, bottom, CohUiTheme.BORDER_SUBTLE);
        graphics.fill(left, top + 2, left + 1, bottom - 2, CohUiTheme.BORDER_SUBTLE);
        graphics.fill(right - 1, top + 2, right, bottom - 2, CohUiTheme.BORDER_SUBTLE);
        
        // Corner accents
        int cornerColor = CohUiTheme.HOVER_BORDER;
        graphics.fill(left, top, left + 4, top + 2, cornerColor);
        graphics.fill(left, top, left + 2, top + 4, cornerColor);
        
        graphics.fill(right - 4, top, right, top + 2, cornerColor);
        graphics.fill(right - 2, top, right, top + 4, cornerColor);
        
        graphics.fill(left, bottom - 2, left + 4, bottom, cornerColor);
        graphics.fill(left, bottom - 4, left + 2, bottom, cornerColor);
        
        graphics.fill(right - 4, bottom - 2, right, bottom, cornerColor);
        graphics.fill(right - 2, bottom - 4, right, bottom, cornerColor);

        // Highlight gradient
        graphics.fillGradient(left + 1, top + 1, right - 1, top + 16, 0x1CFFFFFF, 0x02000000);
        
        // Scanline effect
        for (int i = top + 2; i < bottom - 2; i += 4) {
            graphics.fill(left + 1, i, right - 1, i + 1, 0x10000000);
        }
    }

    private record OptionIconEntry(int x, int y, ItemStack stack) {
    }
}
