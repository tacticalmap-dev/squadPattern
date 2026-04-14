package com.flowingsun.warpattern;

import com.flowingsun.vppoints.api.VpPointsApi;
import com.flowingsun.warpattern.cohmode.CohModeConfig;
import com.flowingsun.warpattern.cohmode.CohModeService;
import com.flowingsun.warpattern.cohmode.net.CohModeNetwork;
import com.flowingsun.warpattern.match.SquadMatchService;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;

/**
 * Main Forge mod entry point.
 * Keeps match-flow orchestration, cohmode feature wiring, and VpPoints callback wiring.
 */
@Mod(WarPatternMod.MOD_ID)
public class WarPatternMod {
    public static final String MOD_ID = "warpattern";

    public WarPatternMod() {
        CohModeConfig.register();
        CohModeNetwork.init();

        // Listen to VP match-finish events and delegate post-finish flow back to host teleport logic.
        VpPointsApi.setMatchFinishListener(SquadMatchService.INSTANCE::onVpPointsMatchFinished);

        // Host-side orchestration remains Forge-event driven.
        MinecraftForge.EVENT_BUS.register(SquadMatchService.INSTANCE);
        MinecraftForge.EVENT_BUS.register(CohModeService.INSTANCE);
    }
}
