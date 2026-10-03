package dev.rick.jjk.progression.block;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.progression.ProgressionBlocks;
import dev.rick.jjk.progression.ProgressionItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * A cauldron holding cursed energy, poured in one Cursed Energy in a Bottle at a time: 1/4 up to 4/4 (full). Once full,
 * Glasses thrown into it are taken by the energy: it reacts, spirals into them, collapses inward with a flash, all four
 * units are spent, and Cursed Glasses rise out. The cauldron is left empty (a plain cauldron again).
 *
 * <p>Everything runs on the server through scheduled block ticks (they are saved with the chunk), so a ritual in
 * progress survives the chunk unloading. The glasses are held by the block's own state (INFUSION) from the moment they go
 * in, so nothing loose can be picked out or pushed away mid-ritual; breaking the cauldron gives them back.
 */
public class CursedCauldronBlock extends AbstractCauldronBlock {
    public static final int MAX_LEVEL = 4;
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 1, MAX_LEVEL);
    /** 0 = still; 1..STAGES while glasses are being infused. */
    public static final int STAGES = 5;
    public static final IntegerProperty INFUSION = IntegerProperty.create("infusion", 0, STAGES);
    /** While infusing: true when what it holds is a Dormant Prison Realm (not glasses). */
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty REALM =
            net.minecraft.world.level.block.state.properties.BooleanProperty.create("realm");
    /** How often a full cauldron looks for glasses in it. */
    private static final int WATCH_TICKS = 5;

    /** No vanilla interactions (26.3 keeps the dispatcher's registration private): {@link #useItemOn} handles its items. */
    private static final CauldronInteraction.Dispatcher INTERACTIONS = new CauldronInteraction.Dispatcher();

    public CursedCauldronBlock(Properties properties) {
        super(properties, INTERACTIONS);
        registerDefaultState(stateDefinition.any().setValue(LEVEL, 1).setValue(INFUSION, 0).setValue(REALM, false));
    }

    /**
     * Pouring rules: a bottle into an empty (vanilla) cauldron starts it at 1/4; into this one it adds a level. The empty
     * cauldron's side goes through Fabric's use-block event, since 26.3's cauldron dispatchers can't be added to.
     */
    public static void registerInteractions() {
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (!stack.is(ProgressionItems.CURSED_ENERGY_BOTTLE) || !level.getBlockState(hit.getBlockPos()).is(Blocks.CAULDRON)) return InteractionResult.PASS;
            return pour(level, hit.getBlockPos(), player, hand, stack, 0);
        });
    }

    /**
     * A bottle poured in adds a level; the plain glasses, used on a full cauldron, are lowered into the energy and the
     * ritual takes them (Cursed Glasses rise out).
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                          BlockHitResult hit) {
        if (stack.is(ProgressionItems.CURSED_ENERGY_BOTTLE)) {
            if (state.getValue(LEVEL) >= MAX_LEVEL || state.getValue(INFUSION) > 0) return InteractionResult.TRY_WITH_EMPTY_HAND;
            return pour(level, pos, player, hand, stack, state.getValue(LEVEL));
        }
        if (stack.is(ProgressionItems.DORMANT_PRISON_REALM)) {
            if (state.getValue(LEVEL) < MAX_LEVEL || state.getValue(INFUSION) > 0) return InteractionResult.TRY_WITH_EMPTY_HAND;
            if (level instanceof ServerLevel server) {
                // Only one Prison Realm can exist in a world: while it does, the energy won't take another cube.
                if (!dev.rick.jjk.progression.prison.PrisonRealm.canForge(server.getServer())) {
                    player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("The energy recoils: a Prison Realm already exists in this world.")
                            .withStyle(net.minecraft.ChatFormatting.GRAY));
                    Fx.sound(server, Vec3.atCenterOf(pos), SoundEvents.SHULKER_HURT_CLOSED, 0.8f, 0.6f);
                    return InteractionResult.FAIL;
                }
                stack.consume(1, player);
                server.setBlock(pos, state.setValue(INFUSION, 1).setValue(REALM, true), Block.UPDATE_ALL);
                Vec3 core = Vec3.atBottomCenterOf(pos).add(0, contentY(MAX_LEVEL) + 0.05, 0);
                Fx.play(server, "prog_infuse", core, Vec3.ZERO, 1, -1);
                Fx.sound(server, core, SoundEvents.SHULKER_BOX_OPEN, 0.8f, 0.4f);
                Fx.sound(server, core, SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.6f, 0.5f);
                server.scheduleTick(pos, this, stageTicks());
            }
            return InteractionResult.SUCCESS;
        }
        if (stack.is(ProgressionItems.GLASSES)) {
            if (state.getValue(LEVEL) < MAX_LEVEL || state.getValue(INFUSION) > 0) return InteractionResult.TRY_WITH_EMPTY_HAND;
            if (level instanceof ServerLevel server) {
                // The cauldron takes the glasses itself (its INFUSION state holds them), so nothing loose can drift out.
                stack.consume(1, player);
                server.setBlock(pos, state.setValue(INFUSION, 1), Block.UPDATE_ALL);
                Vec3 core = Vec3.atBottomCenterOf(pos).add(0, contentY(MAX_LEVEL) + 0.05, 0);
                Fx.play(server, "prog_infuse", core, Vec3.ZERO, 1, -1);
                Fx.sound(server, core, SoundEvents.BOTTLE_EMPTY, 0.8f, 0.5f);
                Fx.sound(server, core, SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.5f, 1.6f);
                server.scheduleTick(pos, this, stageTicks());
            }
            return InteractionResult.SUCCESS;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    private static InteractionResult pour(Level level, BlockPos pos, net.minecraft.world.entity.player.Player player,
                                          net.minecraft.world.InteractionHand hand, ItemStack stack, int from) {
        if (level instanceof ServerLevel server) {
            BlockState now = server.getBlockState(pos);
            // Re-read on the server: two bottles in one tick can't both land on the same level.
            int current = now.is(ProgressionBlocks.CURSED_CAULDRON) ? now.getValue(LEVEL) : now.is(Blocks.CAULDRON) ? 0 : -1;
            if (current != from || current >= MAX_LEVEL) return InteractionResult.PASS;
            int next = current + 1;
            server.setBlock(pos, ProgressionBlocks.CURSED_CAULDRON.defaultBlockState().setValue(LEVEL, next), Block.UPDATE_ALL);
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.GLASS_BOTTLE)));
            Vec3 c = Vec3.atCenterOf(pos);
            Fx.play(server, "prog_pour", c.add(0, contentY(next) - 0.5, 0), Vec3.ZERO, next, -1);
            Fx.sound(server, c, SoundEvents.BOTTLE_EMPTY, 1f, 0.8f);
            Fx.sound(server, c, SoundEvents.SCULK_CATALYST_BLOOM, 0.6f + next * 0.15f, 0.6f + next * 0.08f);
            if (next == MAX_LEVEL) {
                Fx.play(server, "prog_cauldron_full", c, Vec3.ZERO, 1f, -1);
                Fx.sound(server, c, SoundEvents.RESPAWN_ANCHOR_CHARGE, 1f, 0.5f);
                server.scheduleTick(pos, ProgressionBlocks.CURSED_CAULDRON, WATCH_TICKS);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** Height of the energy's surface above the block's floor (blocks). */
    public static double contentY(int level) {
        return (6.0 + level * 2.25) / 16.0;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(LEVEL, INFUSION, REALM);
    }

    @Override
    public boolean isFull(BlockState state) {
        return state.getValue(LEVEL) >= MAX_LEVEL;
    }

    @Override
    protected double getContentHeight(BlockState state) {
        return contentY(state.getValue(LEVEL));
    }

    // --- The ritual ---

    /** A full cauldron missing its watch (placed by a command, a world from before...) picks it back up. */
    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(LEVEL) >= MAX_LEVEL;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.getBlockTicks().hasScheduledTick(pos, this)) level.scheduleTick(pos, this, WATCH_TICKS);
    }

    private static int stageTicks() {
        return Math.max(4, JJKConfig.get().progression.infusionTicks / STAGES);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(LEVEL) < MAX_LEVEL) return;
        int stage = state.getValue(INFUSION);
        Vec3 core = Vec3.atBottomCenterOf(pos).add(0, contentY(MAX_LEVEL) + 0.05, 0);
        if (stage == 0) {
            // Glasses tossed in are taken too (the cauldron holds them from here).
            ItemEntity thrown = glassesIn(level, pos);
            if (thrown != null) {
                ItemStack held = thrown.getItem();
                if (held.getCount() > 1) {
                    ItemStack rest = held.copy();
                    rest.shrink(1);
                    thrown.setItem(rest);
                } else {
                    thrown.discard();
                }
                level.setBlock(pos, state.setValue(INFUSION, 1), Block.UPDATE_ALL);
                Fx.play(level, "prog_infuse", core, Vec3.ZERO, 1, -1);
                Fx.sound(level, core, SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.5f, 1.6f);
                level.scheduleTick(pos, this, stageTicks());
                return;
            }
            level.scheduleTick(pos, this, WATCH_TICKS);
            return;
        }
        if (stage < STAGES) {
            int next = stage + 1;
            level.setBlock(pos, state.setValue(INFUSION, next), Block.UPDATE_ALL);
            Fx.play(level, "prog_infuse", core, Vec3.ZERO, next, -1);
            Fx.sound(level, core, SoundEvents.BEACON_AMBIENT, 0.8f, 0.5f + next * 0.15f);
            if (next == STAGES - 1) Fx.sound(level, core, SoundEvents.RESPAWN_ANCHOR_DEPLETE, 1f, 0.6f);
            level.scheduleTick(pos, this, stageTicks());
            return;
        }
        // Collapse: the energy is spent into what it held, and that rises out awakened.
        level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), Block.UPDATE_ALL);
        ItemStack result = new ItemStack(ProgressionItems.CURSED_GLASSES);
        if (state.getValue(REALM)) {
            // The world gives the cube its one id now (or, if another realm appeared meanwhile, it stays dormant).
            java.util.UUID id = dev.rick.jjk.progression.prison.PrisonRealm.forge(level.getServer());
            result = id != null ? dev.rick.jjk.progression.prison.PrisonRealmItem.create(id) : new ItemStack(ProgressionItems.DORMANT_PRISON_REALM);
            if (id != null) Fx.sound(level, core, SoundEvents.ENDER_DRAGON_GROWL, 0.5f, 1.6f);
        }
        ItemEntity out = new ItemEntity(level, core.x, core.y + 0.4, core.z, result);
        out.setDeltaMovement(0, 0.3, 0);
        out.setPickUpDelay(20);
        level.addFreshEntity(out);
        Fx.play(level, "prog_infuse_done", core, Vec3.ZERO, 1f, out.getId());
        Fx.sound(level, core, SoundEvents.ZOMBIE_VILLAGER_CURE, 0.7f, 1.4f);
        Fx.sound(level, core, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2f, 0.5f);
    }

    /** Broken mid-ritual: the glasses it was holding come back out (nothing is lost). */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved) {
        super.affectNeighborsAfterRemoval(state, level, pos, moved);
        if (state.getValue(INFUSION) > 0 && !level.getBlockState(pos).is(this)) {
            Block.popResource(level, pos, new ItemStack(state.getValue(REALM) ? ProgressionItems.DORMANT_PRISON_REALM : ProgressionItems.GLASSES));
        }
    }

    /** Glasses lying in the energy. */
    private static ItemEntity glassesIn(ServerLevel level, BlockPos pos) {
        AABB inside = new AABB(pos.getX() + 0.1, pos.getY() + 0.2, pos.getZ() + 0.1, pos.getX() + 0.9, pos.getY() + 1.1, pos.getZ() + 0.9);
        List<ItemEntity> found = level.getEntitiesOfClass(ItemEntity.class, inside, e -> e.isAlive() && e.getItem().is(ProgressionItems.GLASSES));
        return found.isEmpty() ? null : found.get(0);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        int lv = state.getValue(LEVEL);
        if (random.nextInt(MAX_LEVEL + 2 - lv) != 0) return;
        double y = pos.getY() + contentY(lv) + 0.02;
        double x = pos.getX() + 0.2 + random.nextDouble() * 0.6, z = pos.getZ() + 0.2 + random.nextDouble() * 0.6;
        level.addParticle(lv >= MAX_LEVEL && random.nextInt(3) == 0 ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.SQUID_INK, x, y, z, 0, 0.02, 0);
        if (lv >= MAX_LEVEL && random.nextInt(4) == 0) level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 0, 0.01, 0);
    }
}
