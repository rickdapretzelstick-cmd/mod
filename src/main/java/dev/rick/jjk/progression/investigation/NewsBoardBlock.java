package dev.rick.jjk.progression.investigation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A village's news board: a weathered board on two posts with notices pinned to it, put up by the bell. Using it reads
 * the notices ({@link Investigations#readBoard}). It is part of the village, not a quest giver: no glow, no markers; the
 * only sign of news is how much paper is pinned to it ({@link #NOTICES}).
 */
public class NewsBoardBlock extends HorizontalDirectionalBlock {
    /** How many notices are pinned up (0 to 4): it changes as reports come in and go stale. */
    public static final net.minecraft.world.level.block.state.properties.IntegerProperty NOTICES =
            net.minecraft.world.level.block.state.properties.IntegerProperty.create("notices", 0, 4);
    private static final VoxelShape NS = Block.box(0, 0, 6, 16, 16, 10);
    private static final VoxelShape EW = Block.box(6, 0, 0, 10, 16, 16);

    public NewsBoardBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(NOTICES, 2));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, NOTICES);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? NS : EW;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) Investigations.readBoard(sp, sl, pos);
        return InteractionResult.SUCCESS;
    }
}
