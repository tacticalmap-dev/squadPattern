package com.flowingsun.warpattern.cohmode.client;

import com.flowingsun.warpattern.cohmode.net.CohModeInviteS2C;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Client entrypoints called by network packet handlers.
 */
public final class CohModeClientHooks {
    private CohModeClientHooks() {
    }

    public static void applyState(String stateJson, boolean openScreen) {
        CohModeClientState.applyStateJson(stateJson);
        Minecraft mc = Minecraft.getInstance();
        if (!openScreen) {
            return;
        }
        if (mc.screen instanceof CohModeScreen) {
            return;
        }
        mc.setScreen(new CohModeScreen());
    }

    public static void showInvite(CohModeInviteS2C invite) {
        Minecraft mc = Minecraft.getInstance();
        Screen parent = mc.screen;
        mc.setScreen(new CohModeInviteScreen(parent, invite));
    }
}
