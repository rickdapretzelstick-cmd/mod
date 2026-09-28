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

    private ModBlocks() {}

    private static Block register(String name, int light) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JJK.id(name));
        return Blocks.register(key, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(-1.0f, 3600000.0f).noLootTable()
                .lightLevel(s -> light).pushReaction(PushReaction.IMMOVEABLE));
    }

    public static boolean isDomainBlock(net.minecraft.world.level.block.state.BlockState s) {
        return s.is(DOMAIN_BARRIER) || s.is(DOMAIN_FLOOR);
    }

    public static void init() {}
}
