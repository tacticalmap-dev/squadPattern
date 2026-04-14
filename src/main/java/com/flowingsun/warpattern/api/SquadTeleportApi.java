package com.flowingsun.warpattern.api;

import com.flowingsun.warpattern.match.SquadMatchService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

/**
 * Teleport-flow API exported by the host map mod.
 *
 * This API keeps map/runtime world preparation and all teleport-related flows in warpattern:
 * - pre-match team teleport
 * - reconnect teleport
 * - post-match world restoration path
 */
public final class SquadTeleportApi {
    private SquadTeleportApi() {
    }

    public static int startMatch(
            CommandSourceStack source,
            String mapName,
            Collection<ServerPlayer> redPlayers,
            Collection<ServerPlayer> bluePlayers
    ) {
        return SquadMatchService.INSTANCE.startMatchApi(source, mapName, redPlayers, bluePlayers);
    }

    public static int endMatchByMapName(CommandSourceStack source, String mapName) {
        return SquadMatchService.INSTANCE.endMatchByMapNameApi(source, mapName);
    }

    public static void handleReconnect(ServerPlayer player) {
        SquadMatchService.INSTANCE.handleReconnectApi(player);
    }
}

