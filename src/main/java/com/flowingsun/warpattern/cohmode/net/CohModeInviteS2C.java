package com.flowingsun.warpattern.cohmode.net;

import com.flowingsun.warpattern.cohmode.CohModeModels;
import com.flowingsun.warpattern.cohmode.client.CohModeClientHooks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Invitation popup packet.
 */
public class CohModeInviteS2C {
    public final String inviteId;
    public final CohModeModels.InviteKind kind;
    public final String fromName;
    public final String roomId;
    public final long expireAtMillis;

    public CohModeInviteS2C(String inviteId, CohModeModels.InviteKind kind, String fromName, String roomId, long expireAtMillis) {
        this.inviteId = inviteId;
        this.kind = kind;
        this.fromName = fromName;
        this.roomId = roomId;
        this.expireAtMillis = expireAtMillis;
    }

    public static void encode(CohModeInviteS2C pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.inviteId, 128);
        buf.writeEnum(pkt.kind);
        buf.writeUtf(pkt.fromName, 64);
        buf.writeUtf(pkt.roomId == null ? "" : pkt.roomId, 64);
        buf.writeLong(pkt.expireAtMillis);
    }

    public static CohModeInviteS2C decode(FriendlyByteBuf buf) {
        String roomId = buf.readUtf(64);
        return new CohModeInviteS2C(
                buf.readUtf(128),
                buf.readEnum(CohModeModels.InviteKind.class),
                buf.readUtf(64),
                roomId.isEmpty() ? null : roomId,
                buf.readLong()
        );
    }

    public static void handle(CohModeInviteS2C pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> CohModeClientHooks.showInvite(pkt)));
        ctx.get().setPacketHandled(true);
    }
}
