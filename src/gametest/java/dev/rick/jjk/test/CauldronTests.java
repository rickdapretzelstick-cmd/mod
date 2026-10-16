package dev.rick.jjk.test;

import dev.rick.jjk.progression.ProgressionBlocks;
import dev.rick.jjk.progression.ProgressionItems;
import dev.rick.jjk.progression.block.CauldronInfusions;
import dev.rick.jjk.progression.block.CursedCauldronBlock;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * A finished cauldron ritual gives back only what it made: never the item that went in as well (turning back into a plain
 * cauldron is not the ritual being broken off). Breaking the cauldron mid-ritual still gives back what went in.
 */
public class CauldronTests {
    @GameTest(environment = "jjk-test:cauldron_a")
    public void aFinishedRitualGivesOnlyItsResult(GameTestHelper h) {
        BlockPos at = h.absolutePos(new BlockPos(2, 1, 2));
        h.setBlock(2, 0, 2, Blocks.STONE);
        int glasses = CauldronInfusions.indexOf(new ItemStack(ProgressionItems.GLASSES));
        BlockState full = ProgressionBlocks.CURSED_CAULDRON.defaultBlockState().setValue(CursedCauldronBlock.LEVEL, CursedCauldronBlock.MAX_LEVEL)
                .setValue(CursedCauldronBlock.INFUSION, CursedCauldronBlock.STAGES).setValue(CursedCauldronBlock.HELD, glasses);
        h.getLevel().setBlock(at, full, 3);
        h.getLevel().getBlockState(at).tick(h.getLevel(), at, h.getLevel().getRandom());
        List<ItemEntity> items = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(3));
        h.assertTrue(h.getLevel().getBlockState(at).is(Blocks.CAULDRON), "back to a plain cauldron");
        h.assertTrue(items.size() == 1 && items.getFirst().getItem().is(ProgressionItems.CURSED_GLASSES),
                "only the result rises out: " + items.stream().map(e -> e.getItem().toString()).toList());
        items.forEach(ItemEntity::discard);
        // Broken mid-ritual: what went in comes back.
        h.getLevel().setBlock(at, full.setValue(CursedCauldronBlock.INFUSION, 2), 3);
        h.getLevel().destroyBlock(at, false);
        List<ItemEntity> back = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(3));
        h.assertTrue(back.stream().anyMatch(e -> e.getItem().is(ProgressionItems.GLASSES)), "broken off: the glasses come back");
        back.forEach(ItemEntity::discard);
        h.succeed();
    }
}
