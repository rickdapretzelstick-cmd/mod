package dev.rick.jjk.progression.block;

import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.progression.ProgressionItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoulSandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Soul sand with something still trapped in it. It only forms naturally right under the bone blocks of Soul Sand Valley
 * fossils. An empty glass bottle draws the soul out (Soul in a Bottle) and leaves plain soul sand behind. Mined, the
 * essence escapes and it drops plain soul sand.
 */
public class CursedSoulSandBlock extends SoulSandBlock {
    public CursedSoulSandBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                          BlockHitResult hit) {
        if (!stack.is(Items.GLASS_BOTTLE)) return super.useItemOn(stack, state, level, pos, player, hand, hit);
        if (level instanceof ServerLevel server) {
            // Decided on the server against the block as it is now: a second use on the same tick finds plain soul sand.
            if (!server.getBlockState(pos).is(this)) return InteractionResult.PASS;
            server.setBlock(pos, Blocks.SOUL_SAND.defaultBlockState(), Block.UPDATE_ALL);
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(ProgressionItems.SOUL_IN_A_BOTTLE)));
            Vec3 top = Vec3.atCenterOf(pos).add(0, 0.5, 0);
            Fx.play(server, "prog_extract", top, player.getEyePosition().subtract(top), 1f, player.getId());
            Fx.sound(server, top, SoundEvents.BOTTLE_FILL_DRAGONBREATH, 1f, 0.7f);
            Fx.sound(server, top, SoundEvents.SOUL_ESCAPE, 1.2f, 0.6f);
        }
        return InteractionResult.SUCCESS;
    }

    /** A faint sign that something is wrong with this sand, for whoever looks closely. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(6) != 0) return;
        // Out of whichever side faces open air (the top is usually under a bone block).
        net.minecraft.core.Direction side = net.minecraft.core.Direction.from2DDataValue(random.nextInt(4));
        if (random.nextInt(4) == 0) side = net.minecraft.core.Direction.UP;
        if (!level.getBlockState(pos.relative(side)).isAir()) return;
        double x = pos.getX() + 0.5 + side.getStepX() * 0.52 + (side.getStepX() == 0 ? random.nextDouble() - 0.5 : 0);
        double y = pos.getY() + 0.5 + side.getStepY() * 0.52 + (side.getStepY() == 0 ? random.nextDouble() - 0.5 : 0);
        double z = pos.getZ() + 0.5 + side.getStepZ() * 0.52 + (side.getStepZ() == 0 ? random.nextDouble() - 0.5 : 0);
        level.addParticle(random.nextInt(3) == 0 ? net.minecraft.core.particles.ParticleTypes.SOUL
                : net.minecraft.core.particles.ParticleTypes.SQUID_INK, x, y, z, side.getStepX() * 0.01, 0.01, side.getStepZ() * 0.01);
    }
}
