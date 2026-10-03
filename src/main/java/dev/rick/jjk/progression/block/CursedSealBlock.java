package dev.rick.jjk.progression.block;

import dev.rick.jjk.progression.CursedEncounters;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * The seal set into the floor of a cursed battle room. It is the room's encounter marker: placed by the structure
 * generation with a scheduled tick, it registers its site with {@link CursedEncounters} the first time it ticks in the
 * world, then keeps checking who is in the room and hands that to the site's encounter (none is implemented yet: the
 * Finger Bearer will be). Unbreakable, like the room's purpose.
 */
public class CursedSealBlock extends Block {
    /** Which structure the room belongs to: {@link CursedEncounters#SITES}. */
    public static final IntegerProperty SITE = IntegerProperty.create("site", 0, 1);
    public static final int CHECK_TICKS = 40;

    public CursedSealBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(SITE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(SITE);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        CursedEncounters.tickSeal(level, pos, CursedEncounters.SITES.get(state.getValue(SITE)));
        level.scheduleTick(pos, this, CHECK_TICKS);
    }

    /** A seal that lost its schedule (placed by a command) starts again. */
    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.getBlockTicks().hasScheduledTick(pos, this)) level.scheduleTick(pos, this, CHECK_TICKS);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) != 0) return;
        double a = random.nextDouble() * Math.PI * 2, r = 0.3 + random.nextDouble() * 0.15;
        level.addParticle(ParticleTypes.SQUID_INK, pos.getX() + 0.5 + Math.cos(a) * r, pos.getY() + 1.02, pos.getZ() + 0.5 + Math.sin(a) * r, 0, 0.015, 0);
    }
}
