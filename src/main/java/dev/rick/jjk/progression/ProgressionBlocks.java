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

    /** The Prison Realm's cell (built only while someone is sealed; given back to the world on release). */
    public static final Block PRISON_WALL = Blocks.register(key("prison_wall"), dev.rick.jjk.progression.prison.PrisonCellBlocks.Wall::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN).strength(-1.0f, 3600000.0f).noLootTable().lightLevel(s -> 6)
                    .pushReaction(net.minecraft.world.level.material.PushReaction.IMMOVEABLE).isValidSpawn((s, l, p, t) -> false));
    public static final Block SEAL_LOCK = Blocks.register(key("seal_lock"), dev.rick.jjk.progression.prison.PrisonCellBlocks.SealLock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN).strength(-1.0f, 3600000.0f).noLootTable()
                    .lightLevel(s -> s.getValue(dev.rick.jjk.progression.prison.PrisonCellBlocks.SealLock.STATE) == 1 ? 15 : 5)
                    .pushReaction(net.minecraft.world.level.material.PushReaction.IMMOVEABLE));
    public static final Block PRISON_CORE = Blocks.register(key("prison_core"), dev.rick.jjk.progression.prison.PrisonCellBlocks.Core::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN).strength(-1.0f, 3600000.0f).noLootTable()
                    .lightLevel(s -> s.getValue(dev.rick.jjk.progression.prison.PrisonCellBlocks.Core.OPEN) ? 15 : 7)
                    .pushReaction(net.minecraft.world.level.material.PushReaction.IMMOVEABLE).isValidSpawn((s, l, p, t) -> false));

    /** A village's news board (put up by the bell; also craftable-free decoration for builders via commands). */
    public static final Block NEWS_BOARD = Blocks.register(key("news_board"), dev.rick.jjk.progression.investigation.NewsBoardBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).strength(2.0f).noOcclusion().ignitedByLava());

    /** The hunting lodge's gun rack (sealed, holding the Cursed Rifle, empty) and the scope on its windowsill. */
    public static final Block GUN_RACK = Blocks.register(key("gun_rack"), dev.rick.jjk.progression.investigation.GunRackBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.SPRUCE_PLANKS).strength(-1.0f, 3600000.0f).noLootTable().noOcclusion()
                    .lightLevel(s -> s.getValue(dev.rick.jjk.progression.investigation.GunRackBlock.RACK) == dev.rick.jjk.progression.investigation.GunRackBlock.Rack.OPEN ? 6 : 0));
    public static final Block MOUNTED_SCOPE = Blocks.register(key("mounted_scope"), dev.rick.jjk.progression.investigation.MountedScopeBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BARS).strength(-1.0f, 3600000.0f).noLootTable().noOcclusion());

    private ProgressionBlocks() {}

    private static ResourceKey<Block> key(String name) {
        return ResourceKey.create(Registries.BLOCK, JJK.id(name));
    }

    public static void init() {}
}
