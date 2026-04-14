package com.flowingsun.warpattern.cohmode.net;

import com.flowingsun.warpattern.WarPatternMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

/**
 * Network channel for cohmode UI and matchmaking actions.
 */
public final class CohModeNetwork {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(WarPatternMod.MOD_ID, "cohmode"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    private CohModeNetwork() {
    }

    public static void init() {
        int id = 0;
        CHANNEL.registerMessage(
                id++,
                CohModeActionC2S.class,
                CohModeActionC2S::encode,
                CohModeActionC2S::decode,
                CohModeActionC2S::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        CHANNEL.registerMessage(
                id++,
                CohModeStateS2C.class,
                CohModeStateS2C::encode,
                CohModeStateS2C::decode,
                CohModeStateS2C::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
        CHANNEL.registerMessage(
                id++,
                CohModeInviteS2C.class,
                CohModeInviteS2C::encode,
                CohModeInviteS2C::decode,
                CohModeInviteS2C::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }

    public static void sendTo(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
