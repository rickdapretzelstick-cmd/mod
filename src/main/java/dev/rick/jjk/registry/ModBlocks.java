package dev.rick.jjk.registry;

import dev.rick.jjk.JJK;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * Domain structure blocks. They only ever exist while a domain is active and are always restored away afterwards.
 * Unbreakable, blast-proof, immovable and lootless so nothing can tunnel through a domain barrier.
 */
public final class ModBlocks {
    public static final Block DOMAIN_BARRIER = register("domain_barrier", 3);
    public static final Block DOMAIN_FLOOR = register("domain_floor", 7);
    /** Idle Death Gamble: a neon-lit pachinko wall and a casino floor. */
    public static final Block IDG_BARRIER = register("idg_barrier", 11);
    public static final Block IDG_FLOOR = register("idg_floor", 9);

    // Model-only blocks: never placed in the world, only drawn by renderers (Hakari's doors, balls and the slot reels),
    // so his techniques are made of real Minecraft block models and textures.
    public static final Block SHUTTER_PANEL = register("shutter_panel", 0);
    public static final Block GAMBLE_DOOR = register("gamble_door", 0);
    public static final Block PACHINKO_BALL = register("pachinko_ball", 0);
    public static final ReelBlock REEL = registerReel();

    private ModBlocks() {}

    private static Block register(String name, int light) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JJK.id(name));
        return Blocks.register(key, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(-1.0f, 3600000.0f).noLootTable()
                .lightLevel(s -> light).pushReaction(PushReaction.IMMOVEABLE));
    }

    private static ReelBlock registerReel() {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JJK.id("reel"));
        return (ReelBlock) Blocks.register(key, ReelBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(-1.0f, 3600000.0f)
                .noLootTable().lightLevel(s -> 15).pushReaction(PushReaction.IMMOVEABLE));
    }

    public static boolean isDomainBlock(net.minecraft.world.level.block.state.BlockState s) {
        return s.is(DOMAIN_BARRIER) || s.is(DOMAIN_FLOOR) || s.is(IDG_BARRIER) || s.is(IDG_FLOOR);
    }

    /** One slot-machine reel face: a number from 1 to 7, or 0 for a blank spinning blur. */
    public static final class ReelBlock extends Block {
        public static final net.minecraft.world.level.block.state.properties.IntegerProperty DIGIT =
                net.minecraft.world.level.block.state.properties.IntegerProperty.create("digit", 0, 7);

        public ReelBlock(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(DIGIT, 0));
        }

        @Override
        protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, net.minecraft.world.level.block.state.BlockState> b) {
            b.add(DIGIT);
        }
    }

    public static void init() {}
}
