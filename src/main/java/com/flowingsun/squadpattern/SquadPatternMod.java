package com.flowingsun.squadpattern;

import com.flowingsun.vppoints.api.VpPointsApi;
import com.flowingsun.squadpattern.match.SquadMatchService;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;

/**
 * Main Forge mod entry point.
 * Keeps only match-flow orchestration and VpPoints callback wiring.
 */
@Mod(SquadPatternMod.MOD_ID)
public class SquadPatternMod {
    public static final String MOD_ID = "squadpattern";

    public SquadPatternMod() {
        // Listen to VP match-finish events and delegate post-finish flow back to host teleport logic.
        VpPointsApi.setMatchFinishListener(SquadMatchService.INSTANCE::onVpPointsMatchFinished);

        // Host-side orchestration remains Forge-event driven.
        MinecraftForge.EVENT_BUS.register(SquadMatchService.INSTANCE);
    }
}
