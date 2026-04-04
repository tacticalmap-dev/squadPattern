package com.flowingsun.squadpattern.vp;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class VictoryPointProtection {
    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent event) {
        if (!isCapturePoint(event.getState().getBlock())) {
            return;
        }

        Player player = event.getPlayer();
        if (player instanceof ServerPlayer sp && sp.hasPermissions(2)) {
            return;
        }

        event.setCanceled(true);
        if (player != null) {
            player.displayClientMessage(Component.translatable("message.squadpattern.capture_point.protected"), true);
        }
    }

    @SubscribeEvent
    public void onExplosionDetonate(ExplosionEvent.Detonate event) {
        event.getAffectedBlocks().removeIf(pos -> isVictoryPoint(event.getLevel(), pos));
    }

    private static boolean isVictoryPoint(net.minecraft.world.level.LevelAccessor level, BlockPos pos) {
        return isCapturePoint(level.getBlockState(pos).getBlock());
    }

    private static boolean isCapturePoint(net.minecraft.world.level.block.Block block) {
        return block == VictoryPointRuntime.VICTORY_POINT.get()
                || block == VictoryPointRuntime.NORMAL_POINT.get()
                || block == VictoryPointRuntime.AMMO_POINT.get()
                || block == VictoryPointRuntime.OIL_POINT.get();
    }
}
