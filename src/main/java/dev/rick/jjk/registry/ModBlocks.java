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
    /** Idle Death Gamble: a bright white room, a pale tiled floor and framed hatches in the floor. */
    public static final Block IDG_BARRIER = register("idg_barrier", 15);
    public static final Block IDG_FLOOR = register("idg_floor", 12);
    public static final Block IDG_PANEL = register("idg_panel", 12);

    // Model-only blocks: never placed in the world, only drawn by renderers (Hakari's doors and balls, and the trains and
    // seven-segment counters inside his domain),
    // so his techniques are made of real Minecraft block models and textures.
    public static final Block SHUTTER_PANEL = register("shutter_panel", 0);
    public static final Block GAMBLE_DOOR = register("gamble_door", 0);
    public static final Block PACHINKO_BALL = register("pachinko_ball", 0);
    public static final PropBlock IDG_PROP = registerProp();

    private ModBlocks() {}

    private static Block register(String name, int light) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JJK.id(name));
        return Blocks.register(key, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(-1.0f, 3600000.0f).noLootTable()
                .lightLevel(s -> light).pushReaction(PushReaction.IMMOVEABLE));
    }

    private static PropBlock registerProp() {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JJK.id("idg_prop"));
        return (PropBlock) Blocks.register(key, PropBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(-1.0f, 3600000.0f)
                .noLootTable().lightLevel(s -> 15).pushReaction(PushReaction.IMMOVEABLE));
    }

    public static boolean isDomainBlock(net.minecraft.world.level.block.state.BlockState s) {
        return s.is(DOMAIN_BARRIER) || s.is(DOMAIN_FLOOR) || s.is(IDG_BARRIER) || s.is(IDG_FLOOR) || s.is(IDG_PANEL);
    }

    /** A prop drawn inside Idle Death Gamble: {@link #TRAIN_CAR} or {@link #LED_SEGMENT}. */
    public static final class PropBlock extends Block {
        public static final int TRAIN_CAR = 0, LED_SEGMENT = 1;
        public static final net.minecraft.world.level.block.state.properties.IntegerProperty PART =
                net.minecraft.world.level.block.state.properties.IntegerProperty.create("part", 0, 1);

        public PropBlock(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(PART, 0));
        }

        @Override
        protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, net.minecraft.world.level.block.state.BlockState> b) {
            b.add(PART);
        }
    }

    public static void init() {}
}
