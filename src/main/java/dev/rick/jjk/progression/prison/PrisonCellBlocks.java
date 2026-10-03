package dev.rick.jjk.progression.prison;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The inside of the Prison Realm: unbreakable flesh walls, the four seal locks and the core. They exist only while
 * someone is sealed (the cell is built over whatever was there and gives it back on release) and never drop anything.
 */
public final class PrisonCellBlocks {
    private PrisonCellBlocks() {}

    /** The cell's walls, floor and ceiling. */
    public static class Wall extends Block {

        public Wall(Properties p) {
            super(p);
        }
    }

    /** A seal lock: 0 dark (closed), 1 glowing (open: use it now), 2 broken this stage. */
    public static class SealLock extends Block {
        public static final IntegerProperty STATE = IntegerProperty.create("lock", 0, 2);

        public SealLock(Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(STATE, 0));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(STATE);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (level instanceof ServerLevel && player instanceof ServerPlayer sp) PrisonRealm.useLock(sp, pos);
            return InteractionResult.SUCCESS;
        }
    }

    /** The core in the cell's floor: sealed until all three stages are broken, then the way out. */
    public static class Core extends Block {
        public static final BooleanProperty OPEN = BooleanProperty.create("open");

        public Core(Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(OPEN, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(OPEN);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (level instanceof ServerLevel && player instanceof ServerPlayer sp) PrisonRealm.useCore(sp);
            return InteractionResult.SUCCESS;
        }
    }
}
