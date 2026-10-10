package dev.rick.jjk.test;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.world.WorldRestoration;
import dev.rick.jjk.entity.TrainingDummy;
import dev.rick.jjk.hakari.HakariCombat;
import dev.rick.jjk.progression.ProgressionItems;
import dev.rick.jjk.progression.block.CauldronInfusions;
import dev.rick.jjk.progression.mastery.Mastery;
import dev.rick.jjk.progression.mastery.MasteryData;
import dev.rick.jjk.progression.mastery.MasteryNode;
import dev.rick.jjk.progression.mastery.MasteryTrees;
import dev.rick.jjk.progression.tool.CursedTools;
import dev.rick.jjk.progression.tool.kit.BladeKit;
import dev.rick.jjk.progression.tool.kit.CursedKits;
import dev.rick.jjk.progression.tool.kit.CursedSlot;
import dev.rick.jjk.progression.tool.kit.GripProfile;
import dev.rick.jjk.registry.ModEntities;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The Cursed Blade: made from a netherite sword in the cauldron; its moveset only from the Cursed Item slot (never just
 * by holding it), two-handed; its learned moves wait on its own tree; Heavy Slash cleaves and breaks a guard, Rising Cut
 * launches, Cursed Wave travels and cuts (plants it passes come back later), Thorn Guard stops a blow and answers it with
 * thorns, Black Thorn launches everything round it.
 */
public class BladeTests {
    private static ServerPlayer wielder(GameTestHelper h, double x, double z) {
        JJKConfig.get().general.autoAssignGojo = false;
        JJKConfig.get().progression.enabled = true;
        for (int i = -1; i < 24; i++) for (int k = -1; k < 24; k++) h.setBlock(i, 0, k, Blocks.STONE);
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.getAbilities().invulnerable = false;
        p.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        p.snapTo(h.absoluteVec(new Vec3(x, 1, z)).x, h.absoluteVec(new Vec3(x, 1, z)).y, h.absoluteVec(new Vec3(x, 1, z)).z, 0f, 0f);
        p.setYHeadRot(0f);
        CursedSlot.set(p, new ItemStack(ProgressionItems.CURSED_BLADE));
        CursedKits.update(p);
        Casters.get(p).setNoCost(true);
        // A mock player is never ticked by the server (a real one is, through its connection): run its combat upkeep
        // (casts, statuses) each tick the way a real player's own tick does.
        h.onEachTick(() -> {
            if (p.isAlive()) dev.rick.jjk.core.combat.CombatTicker.tick(p, Combat.stateOrNull(p), Casters.getOrNull(p));
        });
        return p;
    }

    private static TrainingDummy dummy(GameTestHelper h, double x, double z) {
        TrainingDummy d = h.spawn(ModEntities.TRAINING_DUMMY, new Vec3(x, 1, z));
        d.setMode(TrainingDummy.Mode.STAND);
        return d;
    }

    /** Every node of the blade's tree, bought (what a long career with it would have). */
    private static void masterAll(ServerPlayer p) {
        MasteryData d = Mastery.data(p);
        for (MasteryNode n : MasteryTrees.get("tool/cursed_blade").nodes()) d = d.buy("tool/cursed_blade", n.id(), 0);
        Mastery.set(p, d);
    }

    private static void press(AbilityCaster c, AbilitySlot slot) {
        c.input(slot, true, 0, 0, null);
        c.input(slot, false, 0, 0, null);
    }

    @GameTest(environment = "jjk-test:blade_a")
    public void madeInTheCauldronItsMovesetOnlyFromTheSlot(GameTestHelper h) {
        int i = CauldronInfusions.indexOf(new ItemStack(Items.NETHERITE_SWORD));
        h.assertTrue(i >= 0 && CauldronInfusions.get(i).result().make(h.getLevel(), h.absolutePos(BlockPos.ZERO)).is(ProgressionItems.CURSED_BLADE),
                "a netherite sword in a full cauldron becomes the Cursed Blade");
        JJKConfig.get().general.autoAssignGojo = false;
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        AbilityCaster c = Casters.get(p);
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ProgressionItems.CURSED_BLADE));
        CursedKits.update(p);
        h.assertTrue(c.toolKit() == null, "holding it is not enough: no moveset");
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        h.assertTrue(CursedSlot.fits(new ItemStack(ProgressionItems.CURSED_BLADE)), "it fits the Cursed Item slot");
        CursedSlot.set(p, new ItemStack(ProgressionItems.CURSED_BLADE));
        CursedKits.update(p);
        h.assertTrue(c.toolKit() instanceof BladeKit k && k.grip() == GripProfile.TWO_HAND, "equipped: its two-handed kit");
        h.assertTrue("cb_heavy_slash".equals(c.ability(AbilitySlot.SKILL_1).id) && "cb_cursed_wave".equals(c.ability(AbilitySlot.SKILL_2).id)
                && "cb_thorn_lunge".equals(c.ability(AbilitySlot.SKILL_3).id) && "cb_thorn_guard".equals(c.ability(AbilitySlot.SKILL_4).id)
                && "cb_rising_cut".equals(c.ability(AbilitySlot.SKILL_5).id) && "cb_black_thorn".equals(c.ability(AbilitySlot.ULTIMATE).id), "its six moves");
        h.assertTrue(!CursedTools.CURSED_BLADE.unique() && CursedKits.credited(p) == CursedTools.CURSED_BLADE, "rare, not unique; its hits are its own");
        // Its learned moves wait on its own tree.
        press(c, AbilitySlot.SKILL_2);
        h.assertTrue(!c.isCasting("cb_cursed_wave") && "mastery".equals(c.lastRefusal), "Cursed Wave waits on its node: " + c.lastRefusal);
        CursedSlot.set(p, ItemStack.EMPTY);
        CursedKits.update(p);
        h.assertTrue(c.toolKit() == null, "taken off, it's gone");
        h.succeed();
    }

    @GameTest(maxTicks = 80, environment = "jjk-test:blade_b")
    public void heavySlashCleavesAndRisingCutLaunches(GameTestHelper h) {
        ServerPlayer p = wielder(h, 3.5, 3.5);
        AbilityCaster c = Casters.get(p);
        TrainingDummy t = dummy(h, 3.5, 5.5);
        float hp = t.getHealth();
        press(c, AbilitySlot.SKILL_1);
        h.assertTrue(c.isCasting("cb_heavy_slash"), "Heavy Slash starts: " + c.lastRefusal);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(t.getHealth() < hp, "the cleave lands"))
                .thenExecute(() -> h.assertTrue(hp - t.getHealth() >= JJKConfig.get().cursedTools.cbHeavyDamage * 0.5f, "and hits hard: " + (hp - t.getHealth())))
                .thenIdle(20)
                .thenExecute(() -> {
                    t.setPos(h.absoluteVec(new Vec3(3.5, 1, 5.5)));
                    press(c, AbilitySlot.SKILL_5);
                    h.assertTrue(c.isCasting("cb_rising_cut"), "Rising Cut starts: " + c.lastRefusal);
                })
                .thenWaitUntil(() -> h.assertTrue(Combat.state(t).has(CombatStatus.LAUNCHED), "and throws them into the air"))
                .thenSucceed();
    }

    @GameTest(maxTicks = 120, padding = 8, environment = "jjk-test:blade_c")
    public void cursedWaveTravelsCutsAndTheCutPlantsComeBack(GameTestHelper h) {
        // The test plot is walled in with barriers eight blocks deep (which stop the crescent, as any wall does).
        ServerPlayer p = wielder(h, 3.5, 0.5);
        masterAll(p);
        AbilityCaster c = Casters.get(p);
        TrainingDummy far = dummy(h, 3.5, 6.5);
        BlockPos grass = h.absolutePos(new BlockPos(3, 1, 3));
        h.getLevel().setBlock(grass, Blocks.SHORT_GRASS.defaultBlockState(), 3);
        float hp = far.getHealth();
        c.input(AbilitySlot.SKILL_2, true, 0, 0, null);
        h.assertTrue(c.isCasting("cb_cursed_wave"), "charging: " + c.lastRefusal);
        h.startSequence()
                .thenIdle(12)
                .thenExecute(() -> c.input(AbilitySlot.SKILL_2, false, 0, 0, null))
                .thenWaitUntil(() -> h.assertTrue(far.getHealth() < hp, "the crescent reaches them, six blocks out"))
                .thenExecute(() -> {
                    h.assertTrue(h.getLevel().getBlockState(grass).isAir(), "the grass in its path is cut");
                    h.assertTrue(WorldRestoration.recordAt(h.getLevel(), grass) != null, "and the battle-damage system will put it back");
                })
                .thenSucceed();
    }

    @GameTest(maxTicks = 80, environment = "jjk-test:blade_d")
    public void thornGuardStopsABlowAndAnswersWithThorns(GameTestHelper h) {
        ServerPlayer p = wielder(h, 3.5, 3.5);
        masterAll(p);
        AbilityCaster c = Casters.get(p);
        TrainingDummy attacker = dummy(h, 3.5, 5.0);
        float mine = p.getHealth(), theirs = attacker.getHealth();
        press(c, AbilitySlot.SKILL_4);
        h.assertTrue(c.isCasting("cb_thorn_guard"), "Thorn Guard: " + c.lastRefusal);
        h.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    HakariCombat.hit(Hit.builder(attacker, "test_punch").damage(6f).tag(AttackTag.MELEE).origin(attacker.getEyePosition()).build(), p);
                    h.assertTrue(p.getHealth() >= mine, "the blow is stopped");
                })
                .thenWaitUntil(() -> h.assertTrue(attacker.getHealth() < theirs, "the thorns answer it"))
                .thenExecute(() -> h.assertTrue(Combat.state(attacker).has(CombatStatus.LAUNCHED), "and throw them up"))
                .thenSucceed();
    }

    @GameTest(maxTicks = 120, padding = 8, environment = "jjk-test:blade_e")
    public void blackThornLaunchesEverythingRoundIt(GameTestHelper h) {
        ServerPlayer p = wielder(h, 8.5, 8.5);
        masterAll(p);
        AbilityCaster c = Casters.get(p);
        TrainingDummy a = dummy(h, 8.5, 12.5), b = dummy(h, 3.5, 8.5), behind = dummy(h, 8.5, 3.5);
        press(c, AbilitySlot.ULTIMATE);
        h.assertTrue(c.isCasting("cb_black_thorn"), "Black Thorn: " + c.lastRefusal);
        h.succeedWhen(() -> {
            for (TrainingDummy t : new TrainingDummy[] {a, b, behind}) {
                h.assertTrue(t.getHealth() < t.getMaxHealth(), "the thorns reach everyone round it");
            }
        });
    }
}
