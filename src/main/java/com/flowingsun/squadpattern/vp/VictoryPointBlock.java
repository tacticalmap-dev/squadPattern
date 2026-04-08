package com.flowingsun.squadpattern.vp;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class VictoryPointBlock extends BaseEntityBlock implements EntityBlock {
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(1, 0, 1, 15, 2, 15),
            Block.box(3, 2, 3, 13, 12, 13)
    );

    private final CapturePointType pointType;

    public VictoryPointBlock(Properties properties) {
        this(properties, CapturePointType.VICTORY);
    }

    public VictoryPointBlock(Properties properties, CapturePointType pointType) {
        super(properties);
        this.pointType = pointType;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (hasOtherPointInChunk(level, pos)) {
            return null;
        }
        return super.getStateForPlacement(context);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && hasOtherPointInChunk(level, pos)) {
            level.removeBlock(pos, false);
            popResource(level, pos, new ItemStack(this.asItem()));
            if (placer instanceof ServerPlayer sp) {
                sp.sendSystemMessage(Component.translatable("message.squadpattern.capture_point.chunk_duplicate"));
            }
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VictoryPointBlockEntity(pos, state);
    }

    public CapturePointType getPointType() {
        return pointType;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(net.minecraft.world.level.Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, VictoryPointRuntime.BE_TYPE.get(), VictoryPointBlock::tickServer);
    }

    private static void tickServer(Level level, BlockPos pos, BlockState state, VictoryPointBlockEntity be) {
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            VictoryPointBlockEntity.serverTick(serverLevel, pos, state, be);
        }
    }

    private static boolean hasOtherPointInChunk(LevelAccessor level, BlockPos targetPos) {
        int cx = targetPos.getX() >> 4;
        int cz = targetPos.getZ() >> 4;
        for (VictoryPointBlockEntity be : VictoryPointBlockEntity.ACTIVE_POINTS) {
            if (be == null || be.isRemoved() || be.getLevel() != level) {
                continue;
            }
            BlockPos p = be.getBlockPos();
            if (Objects.equals(p, targetPos)) {
                continue;
            }
            if ((p.getX() >> 4) == cx && (p.getZ() >> 4) == cz) {
                return true;
            }
        }
        return false;
    }
}
