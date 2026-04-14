package com.flowingsun.warpattern.cohmode.net;

import com.flowingsun.warpattern.cohmode.client.CohModeClientHooks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server-to-client full UI state snapshot packet.
 */
public class CohModeStateS2C {
    public final String stateJson;
    public final boolean openScreen;

    public CohModeStateS2C(String stateJson, boolean openScreen) {
        this.stateJson = stateJson;
        this.openScreen = openScreen;
    }

    public static void encode(CohModeStateS2C pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.stateJson, 262144);
        buf.writeBoolean(pkt.openScreen);
    }

    public static CohModeStateS2C decode(FriendlyByteBuf buf) {
        return new CohModeStateS2C(buf.readUtf(262144), buf.readBoolean());
    }

    public static void handle(CohModeStateS2C pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> CohModeClientHooks.applyState(pkt.stateJson, pkt.openScreen)));
        ctx.get().setPacketHandled(true);
    }
}
