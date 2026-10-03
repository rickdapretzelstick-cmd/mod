package dev.rick.jjk.progression;

import dev.rick.jjk.JJK;
import dev.rick.jjk.progression.block.CursedCauldronBlock;
import dev.rick.jjk.progression.block.CursedSealBlock;
import dev.rick.jjk.progression.block.CursedSoulSandBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** The Survival progression's blocks. */
public final class ProgressionBlocks {
    /** Soul sand with a soul still in it: found only under Soul Sand Valley bone blocks. */
    public static final Block CURSED_SOUL_SAND = Blocks.register(key("cursed_soul_sand"), CursedSoulSandBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.SOUL_SAND).lightLevel(s -> 1));
    /** A cauldron filling with cursed energy (1/4 to 4/4); never an item, it is a plain cauldron poured into. */
    public static final CursedCauldronBlock CURSED_CAULDRON = (CursedCauldronBlock) Blocks.register(key("cursed_energy_cauldron"),
            CursedCauldronBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.CAULDRON).lightLevel(s -> 2 + s.getValue(CursedCauldronBlock.LEVEL) * 2));
    /** The seal at the heart of a cursed battle room: where its encounter waits. */
    public static final CursedSealBlock CURSED_SEAL = (CursedSealBlock) Blocks.register(key("cursed_seal"), CursedSealBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.POLISHED_BLACKSTONE).strength(-1.0f, 3600000.0f).noLootTable().lightLevel(s -> 3));

    private ProgressionBlocks() {}

    private static ResourceKey<Block> key(String name) {
        return ResourceKey.create(Registries.BLOCK, JJK.id(name));
    }

    public static void init() {}
}
