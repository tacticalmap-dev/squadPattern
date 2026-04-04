package com.flowingsun.squadpattern.client;

import com.flowingsun.squadpattern.config.SquadConfig;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;

public class HudConfigScreen extends Screen {
    private final Screen parent;
    private OptionsList list;

    public HudConfigScreen(Screen parent) {
        super(Component.literal("SquadPattern HUD Config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        list = new OptionsList(minecraft, width, height, 32, height - 32, 24);
        list.addSmall(makeDoubleOption("hudLeftX", SquadConfig.HUD_LEFT_X), makeDoubleOption("hudRightX", SquadConfig.HUD_RIGHT_X));
        list.addSmall(makeDoubleOption("hudTopY", SquadConfig.HUD_TOP_Y), makeDoubleOption("hudCenterY", SquadConfig.HUD_CENTER_Y));
        list.addBig(makeDoubleOption("hudScale", SquadConfig.HUD_SCALE));
        addRenderableWidget(list);
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(width / 2 - 50, height - 24, 100, 20).build());
    }

    private OptionInstance<Double> makeDoubleOption(String key, ForgeConfigSpec.DoubleValue value) {
        return new OptionInstance<>(
                key,
                OptionInstance.noTooltip(),
                (txt, v) -> Component.literal(key + ": " + String.format("%.2f", v)),
                OptionInstance.UnitDouble.INSTANCE,
                value.get(),
                v -> value.set(v)
        );
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        list.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}

