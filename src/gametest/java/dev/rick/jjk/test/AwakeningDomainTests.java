package dev.rick.jjk.test;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.character.CharacterService;
import dev.rick.jjk.core.character.Characters;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.combat.HitResolver;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.domain.DomainInstance;
import dev.rick.jjk.core.domain.DomainManager;
import dev.rick.jjk.core.domain.structure.DomainStructure;
import dev.rick.jjk.core.domain.structure.DomainStructures;
import dev.rick.jjk.entity.TrainingDummy;
import dev.rick.jjk.gojo.BlueAbility;
import dev.rick.jjk.gojo.GojoCharacter;
import dev.rick.jjk.gojo.HollowPurpleAbility;
import dev.rick.jjk.gojo.RedAbility;
import dev.rick.jjk.gojo.UnlimitedVoid;
import dev.rick.jjk.registry.ModBlocks;
import dev.rick.jjk.registry.ModEntities;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/** Awakening (meter, transition, moveset swap, costs, drain) and the physical domain (snapshot, protection, restore). */
public class AwakeningDomainTests {
    private static JJKConfig testConfig;

    private static void applyConfig() {
        if (testConfig != null && JJKConfig.get() == testConfig) return;
        JJKConfig cfg = new JJKConfig();
        cfg.general.autoAssignGojo = false;
        cfg.domain.radius = 6;
        cfg.domain.duration = 60;
        cfg.domain.startup = 10;
        cfg.domain.clashDuration = 20;
        cfg.purple.range = 14;
        cfg.awakening.transitionTicks = 10;
        JJKConfig.set(cfg);
        testConfig = cfg;
    }

    private static void floor(GameTestHelper h) {
        applyConfig();
        for (int x = -4; x < 12; x++) for (int z = -4; z < 12; z++) h.setBlock(x, 0, z, Blocks.STONE);
    }

    private static TrainingDummy dummy(GameTestHelper h, double x, double z) {
        TrainingDummy d = h.spawn(ModEntities.TRAINING_DUMMY, new Vec3(x, 1, z));
        d.setMode(TrainingDummy.Mode.STAND);
        return d;
    }

    private static TrainingDummy gojo(GameTestHelper h, double x, double z) {
        TrainingDummy g = dummy(h, x, z);
        CharacterService.assign(g, Characters.get(GojoCharacter.ID));
        return g;
    }

    // --- Awakening ---

    @GameTest(maxTicks = 100)
    public void meterFillsFromCombatAndGatesAwakening(GameTestHelper h) {
        floor(h);
        TrainingDummy target = dummy(h, 5, 4);
        TrainingDummy g = gojo(h, 3, 4);
        AbilityCaster c = Casters.get(g);
        h.assertTrue(c.awakening() == 0, "meter starts empty");
        h.assertTrue(!c.input(AbilitySlot.ULTIMATE, true, 0, 0, null) && "meter_not_full".equals(c.lastRefusal), "can't awaken on an empty meter");
        for (int i = 0; i < 5; i++) {
            Combat.state(target).remove(CombatStatus.HITSTUN);
            HitResolver.resolve(Hit.builder(g, "t").damage(4).tag(AttackTag.MELEE).knockback(Knockback.NONE).build(), target);
        }
        h.assertTrue(c.awakening() > 5, "landing hits builds the meter (" + c.awakening() + ")");
        float before = c.awakening();
        HitResolver.resolve(Hit.builder(target, "t").damage(4).tag(AttackTag.MELEE).build(), g); // Infinity stops it: no damage taken
        c.setAwakening(c.maxAwakening());
        h.startSequence()
                .thenExecute(() -> h.assertTrue(c.input(AbilitySlot.ULTIMATE, true, 0, 0, null), "full meter awakens"))
                .thenExecute(() -> h.assertTrue(Combat.has(g, CombatStatus.AWAKENING), "transition state (untouchable, rooted)"))
                .thenWaitUntil(() -> h.assertTrue(c.isAwakened(), "awakened after the transition"))
                .thenExecute(() -> {
                    h.assertValueEqual(c.ability(AbilitySlot.SKILL_1).id, BlueAbility.MAX_ID, "Z is Max Blue");
                    h.assertValueEqual(c.ability(AbilitySlot.SKILL_2).id, RedAbility.MAX_ID, "X is Max Red");
                    h.assertValueEqual(c.ability(AbilitySlot.SKILL_3).id, HollowPurpleAbility.ID, "C is Hollow Purple");
                    h.assertValueEqual(c.ability(AbilitySlot.ULTIMATE).id, UnlimitedVoid.ID, "G is Infinite Void");
                    h.assertTrue(c.toggled("infinity"), "Infinity always on while awakened");
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    h.assertTrue(c.awakening() < c.maxAwakening(), "the meter drains while awakened");
                    c.setAwakening(0.05f);
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    h.assertTrue(!c.isAwakened(), "empty meter ends Awakening");
                    h.assertValueEqual(c.ability(AbilitySlot.SKILL_1).id, BlueAbility.ID, "back to base Blue");
                    c.gainAwakening(10);
                    h.assertTrue(c.awakening() == 0, "no refill right after Awakening ends");
                })
                .thenSucceed();
    }

    @GameTest(maxTicks = 60)
    public void awakenedMovesSpendTheMeter(GameTestHelper h) {
        floor(h);
        TrainingDummy g = gojo(h, 2, 4);
        AbilityCaster c = Casters.get(g);
        c.enterAwakening();
        c.setAwakening(30);
        h.assertTrue(c.input(AbilitySlot.SKILL_2, true, 0, 0, null), "Max Red with 30 meter");
        float after = c.awakening();
        h.assertTrue(Math.abs(after - (30 - JJKConfig.get().awakening.maxRedCost)) < 0.5f, "Max Red cost its share (" + after + ")");
        c.input(AbilitySlot.SKILL_2, false, 0, 0, null);
        h.runAfterDelay(30, () -> {
            h.assertTrue(!c.input(AbilitySlot.SKILL_3, true, 0, 0, null) && "awakening".equals(c.lastRefusal),
                    "not enough meter left for Hollow Purple (" + c.lastRefusal + ")");
            h.succeed();
        });
    }

    @GameTest(maxTicks = 80, padding = 12, environment = "jjk-test:pull_d")
    public void maxBlueOutclassesBlue(GameTestHelper h) {
        floor(h);
        // A target just outside normal Blue's reach but well inside Max Blue's.
        TrainingDummy far = dummy(h, 7.5, 7.5);
        TrainingDummy g = gojo(h, 0.5, 7.5);
        Casters.get(g).setNoCost(true);
        Vec3 core = h.absoluteVec(new Vec3(1.5, 1.6, 1.5));
        double dist = far.getBoundingBox().getCenter().distanceTo(core);
        h.assertTrue(dist > JJKConfig.get().blue.pullRadius && dist < JJKConfig.get().maxBlue.pullRadius, "setup: target between the two reaches (" + dist + ")");
        dev.rick.jjk.entity.BlueEntity[] blue = new dev.rick.jjk.entity.BlueEntity[1];
        h.startSequence()
                .thenExecute(() -> blue[0] = dev.rick.jjk.entity.BlueEntity.spawn(h.getLevel(), g, core, dev.rick.jjk.entity.BlueEntity.Params.normal()))
                .thenIdle(8)
                .thenExecute(() -> {
                    h.assertTrue(!Combat.has(far, CombatStatus.PULLED), "normal Blue can't reach it");
                    blue[0].discard();
                    blue[0] = dev.rick.jjk.entity.BlueEntity.spawn(h.getLevel(), g, core, dev.rick.jjk.entity.BlueEntity.Params.max());
                })
                .thenIdle(12)
                .thenExecute(() -> {
                    h.assertTrue(Combat.has(far, CombatStatus.PULLED), "Max Blue grabs it");
                    h.assertTrue(far.getBoundingBox().getCenter().distanceTo(core) < dist - 3, "and drags it in (" + far.getBoundingBox().getCenter().distanceTo(core) + ")");
                    blue[0].discard();
                })
                .thenSucceed();
    }

    // --- Physical domain ---

    private record Scene(BlockPos chest, BlockPos doorLow, BlockPos doorHigh, BlockPos stone, BlockPos water, BlockPos floorPos, Map<BlockPos, BlockState> before) {}

    /** Builds a little scene inside where the domain will form and records every block around it. */
    private static Scene scene(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos chest = h.absolutePos(new BlockPos(6, 1, 4));
        level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
        if (level.getBlockEntity(chest) instanceof ChestBlockEntity be) {
            be.setItem(0, new ItemStack(Items.DIAMOND, 5));
            be.setItem(5, new ItemStack(Items.GOLDEN_APPLE, 2));
        }
        BlockPos doorLow = h.absolutePos(new BlockPos(2, 1, 6));
        BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST);
        level.setBlock(doorLow, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 3);
        level.setBlock(doorLow.above(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
        BlockPos stone = h.absolutePos(new BlockPos(4, 3, 7));
        level.setBlock(stone, Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 3);
        BlockPos water = h.absolutePos(new BlockPos(6, 1, 2));
        level.setBlock(water, Blocks.OAK_SLAB.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, true), 3);
        Map<BlockPos, BlockState> before = new HashMap<>();
        BlockPos c = h.absolutePos(new BlockPos(4, 1, 4));
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-8, -8, -8), c.offset(8, 8, 8))) before.put(p.immutable(), level.getBlockState(p));
        return new Scene(chest, doorLow, doorLow.above(), stone, water, h.absolutePos(new BlockPos(4, 0, 4)), before);
    }

    private static int countDomainBlocks(ServerLevel level, BlockPos c) {
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-9, -9, -9), c.offset(9, 9, 9))) if (ModBlocks.isDomainBlock(level.getBlockState(p))) n++;
        return n;
    }

    @GameTest(maxTicks = 200, padding = 10)
    public void domainBuildsSealedStructureAndRestoresExactly(GameTestHelper h) {
        floor(h);
        Scene sc = scene(h);
        TrainingDummy g = gojo(h, 4.5, 4.5);
        ServerLevel level = h.getLevel();
        BlockPos c = h.absolutePos(new BlockPos(4, 1, 4));
        DomainInstance[] d = new DomainInstance[1];
        h.startSequence()
                .thenExecute(() -> {
                    d[0] = DomainManager.expand(g, UnlimitedVoid.INSTANCE);
                    h.assertTrue(d[0] != null && d[0].structure() != null, "domain with a structure");
                    h.assertTrue(DomainStructures.hasSnapshotOnDisk(d[0].structure()), "snapshot saved to disk before building");
                })
                .thenWaitUntil(() -> h.assertTrue(d[0].structure().state() == DomainStructure.State.BUILT, "structure built"))
                .thenExecute(() -> {
                    h.assertTrue(level.getBlockState(sc.floorPos()).is(ModBlocks.DOMAIN_FLOOR), "floor under the owner");
                    h.assertTrue(level.getBlockState(sc.chest()).isAir(), "interior cleared (chest gone for now)");
                    h.assertTrue(level.getBlockState(c.above(6)).is(ModBlocks.DOMAIN_BARRIER), "ceiling");
                    h.assertTrue(level.getBlockState(c.below(6)).is(ModBlocks.DOMAIN_BARRIER), "sealed underneath");
                    h.assertTrue(level.getBlockState(c.east(6)).is(ModBlocks.DOMAIN_BARRIER), "walls");
                    h.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(c).inflate(10)).isEmpty(),
                            "replacing the chest dropped nothing");
                    // Protection: breaking is refused, explosions do nothing.
                    var breaker = h.makeMockServerPlayerInLevel();
                    BlockPos wall = c.east(6);
                    h.assertTrue(!PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, breaker, wall, level.getBlockState(wall), null),
                            "players can't break the barrier");
                    breaker.discard();
                    level.explode(null, wall.getX() + 0.5, wall.getY() + 0.5, wall.getZ() - 1.5, 4f, Level.ExplosionInteraction.TNT);
                    h.assertTrue(level.getBlockState(wall).is(ModBlocks.DOMAIN_BARRIER), "TNT can't breach it");
                    DomainManager.cancel(d[0], DomainInstance.EndReason.CANCELLED);
                })
                .thenWaitUntil(() -> h.assertTrue(d[0].structure().state() == DomainStructure.State.DONE, "structure restored"))
                .thenIdle(2)
                .thenExecute(() -> {
                    int wrong = 0;
                    BlockPos firstWrong = null;
                    for (Map.Entry<BlockPos, BlockState> e : sc.before().entrySet()) {
                        if (!level.getBlockState(e.getKey()).equals(e.getValue())) {
                            wrong++;
                            if (firstWrong == null) firstWrong = e.getKey();
                        }
                    }
                    h.assertTrue(wrong == 0, wrong + " blocks differ after restore, e.g. " + firstWrong + ": was "
                            + (firstWrong == null ? "" : sc.before().get(firstWrong)) + " now " + (firstWrong == null ? "" : level.getBlockState(firstWrong)));
                    h.assertTrue(level.getBlockEntity(sc.chest()) instanceof ChestBlockEntity be && be.getItem(0).is(Items.DIAMOND)
                            && be.getItem(0).getCount() == 5 && be.getItem(5).is(Items.GOLDEN_APPLE), "chest contents restored");
                    h.assertTrue(countDomainBlocks(level, c) == 0, "no domain blocks left");
                    h.assertTrue(!DomainStructures.hasSnapshotOnDisk(d[0].structure()), "snapshot deleted after restore");
                })
                .thenSucceed();
    }

    @GameTest(maxTicks = 120, padding = 10)
    public void crashedDomainIsRecoveredFromDisk(GameTestHelper h) {
        floor(h);
        Scene sc = scene(h);
        TrainingDummy g = gojo(h, 4.5, 4.5);
        ServerLevel level = h.getLevel();
        DomainInstance[] d = new DomainInstance[1];
        h.startSequence()
                .thenExecute(() -> d[0] = DomainManager.expand(g, UnlimitedVoid.INSTANCE))
                .thenWaitUntil(() -> h.assertTrue(d[0].structure().state() == DomainStructure.State.BUILT, "built"))
                .thenExecute(() -> {
                    // Simulate a crash: the server forgets the live structure; only the file on disk remains.
                    DomainStructures.forgetForTesting(d[0].structure());
                    int recovered = DomainStructures.recoverFromDisk(level.getServer());
                    h.assertTrue(recovered >= 1, "recovered from disk");
                    h.assertTrue(level.getBlockEntity(sc.chest()) instanceof ChestBlockEntity be && be.getItem(0).getCount() == 5, "chest back with items");
                    h.assertTrue(level.getBlockState(sc.stone()).is(Blocks.MOSSY_COBBLESTONE), "blocks back");
                    h.assertTrue(level.getBlockState(sc.floorPos()).is(Blocks.STONE), "floor back");
                    h.assertTrue(level.getBlockState(sc.doorHigh()).equals(sc.before().get(sc.doorHigh())), "door top half back");
                    DomainManager.cancel(d[0], DomainInstance.EndReason.CANCELLED);
                })
                .thenSucceed();
    }

    @GameTest(maxTicks = 160, padding = 10)
    public void entitiesAreLiftedOutOfRestoredBlocks(GameTestHelper h) {
        floor(h);
        ServerLevel level = h.getLevel();
        BlockPos pillar = h.absolutePos(new BlockPos(6, 1, 4));
        for (int i = 0; i < 3; i++) level.setBlock(pillar.above(i), Blocks.STONE.defaultBlockState(), 3);
        TrainingDummy g = gojo(h, 4.5, 4.5);
        TrainingDummy victim = dummy(h, 2.5, 4.5);
        DomainInstance[] d = new DomainInstance[1];
        h.startSequence()
                .thenExecute(() -> d[0] = DomainManager.expand(g, UnlimitedVoid.INSTANCE))
                .thenWaitUntil(() -> h.assertTrue(d[0].structure().state() == DomainStructure.State.BUILT, "built"))
                .thenExecute(() -> {
                    // Stand where the pillar used to be (it was cleared by the domain).
                    victim.teleportTo(pillar.getX() + 0.5, pillar.getY(), pillar.getZ() + 0.5);
                    DomainManager.cancel(d[0], DomainInstance.EndReason.CANCELLED);
                })
                .thenWaitUntil(() -> h.assertTrue(d[0].structure().state() == DomainStructure.State.DONE, "restored"))
                .thenExecute(() -> {
                    h.assertTrue(level.getBlockState(pillar.above(1)).is(Blocks.STONE), "pillar restored");
                    h.assertTrue(level.noCollision(victim), "victim not stuck inside the pillar (y=" + victim.getY() + ")");
                })
                .thenSucceed();
    }
}
