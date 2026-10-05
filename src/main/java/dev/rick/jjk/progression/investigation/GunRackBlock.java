package dev.rick.jjk.progression.investigation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The hunting lodge's gun rack. {@code SEALED}: chained shut with a cursed seal (nothing to take). {@code OPEN}: the
 * seal broken and the Cursed Rifle resting on its pegs, for whoever has a claim on it ({@link LodgeRewards}).
 * {@code EMPTY}: every claim taken. The rifle on the rack is the block's own model, not a loose item.
 */
public class GunRackBlock extends HorizontalDirectionalBlock {
    public enum Rack implements StringRepresentable {
        SEALED, OPEN, EMPTY;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final EnumProperty<Rack> RACK = EnumProperty.create("rack", Rack.class);
    private static final VoxelShape NS = Block.box(0, 0, 13, 16, 16, 16), SN = Block.box(0, 0, 0, 16, 16, 3),
            EW = Block.box(0, 0, 0, 3, 16, 16), WE = Block.box(13, 0, 0, 16, 16, 16);

    public GunRackBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(RACK, Rack.SEALED));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, RACK);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
    }

    /** It hangs on the wall behind it: FACING is the way it faces (into the room). */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (state.getValue(FACING)) {
            case NORTH -> NS;
            case SOUTH -> SN;
            case EAST -> EW;
            default -> WE;
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) LodgeRewards.useRack(sp, sl, pos);
        return InteractionResult.SUCCESS;
    }
}
