package com.flowingsun.warpattern.cohmode.net;

import com.flowingsun.warpattern.cohmode.CohModeService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client-to-server action packet from the cohmode UI.
 */
public class CohModeActionC2S {
    public final String action;
    public final String payloadJson;

    public CohModeActionC2S(String action, String payloadJson) {
        this.action = action;
        this.payloadJson = payloadJson == null ? "{}" : payloadJson;
    }

    public static void encode(CohModeActionC2S pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.action, 128);
        buf.writeUtf(pkt.payloadJson, 32767);
    }

    public static CohModeActionC2S decode(FriendlyByteBuf buf) {
        return new CohModeActionC2S(buf.readUtf(128), buf.readUtf(32767));
    }

    public static void handle(CohModeActionC2S pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null) {
                CohModeService.INSTANCE.handleClientAction(sender, pkt.action, pkt.payloadJson);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
