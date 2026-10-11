package dev.rick.jjk.test;

import com.mojang.serialization.JsonOps;
import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.character.CharacterService;
import dev.rick.jjk.core.character.Characters;
import dev.rick.jjk.core.character.JJKCharacter;
import dev.rick.jjk.progression.KitOwnership;
import dev.rick.jjk.progression.TechniqueProgression;
import dev.rick.jjk.progression.mastery.Mastery;
import dev.rick.jjk.progression.mastery.MasteryData;
import dev.rick.jjk.progression.mastery.MasteryNode;
import dev.rick.jjk.progression.mastery.MasteryTree;
import dev.rick.jjk.progression.mastery.MasteryTrees;
import dev.rick.jjk.registry.ModEntities;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Mastery: every tree loads and is sound; every move of every kit is in its tree; every variant and awakening the code
 * gates has a node that opens it; purchases follow ownership, prerequisites and cost; gating applies only to a Survival
 * player on their own kit; the record survives a save. One environment per test (they swap the shared config).
 */
public class MasteryTests {
    /** Every unlock key the code asks {@link Mastery#unlocked} about. */
    static final List<String> GATED = List.of(
            "red.limitless", "max_red.limitless", "rapid_punches.face_grater", "awaken.zero_two", "gojo.awakening",
            "reserve_balls.doors", "fever_breaker.doors", "reserve_balls.renewal", "hakari.awakening",
            "divergent_fist.black_flash", "divergent_fist.chain", "combat_instincts.throw", "dismantle.world_cutting_slash", "yuji.awakening",
            "resolute_slash.black_flash", "second_wind.pummel", "energy_ripple.fakeout", "authentic_mutual_love.jacobs_ladder",
            "rika_launch.feint", "severing_path.veilstep", "outburst.full_stage", "yuta.awakening",
            "granite_blast.dash", "granite_blast.charged", "not_invited.held", "ryu.awakening",
            "tool.slaughter_demon.flurry", "tool.slaughter_demon.precision", "tool.slaughter_demon.sd_quickstep_r", "tool.slaughter_demon.severing_point",
            "tool.slaughter_demon.parry", "tool.slaughter_demon.sd_flurry_r", "tool.slaughter_demon.thousand_cuts",
            "tool.cursed_cleaver.heavy_swing", "tool.cursed_cleaver.guard_break", "tool.cursed_cleaver.shockwave", "tool.cursed_cleaver.momentum",
            "tool.cursed_cleaver.splitter", "tool.cursed_cleaver.iron_wall", "tool.cursed_cleaver.cl_heavy_r", "tool.cursed_cleaver.executioner",
            "tool.cursed_rifle.volley", "tool.cursed_rifle.lens_flare", "tool.cursed_rifle.beam");

    private static ServerPlayer survivor(GameTestHelper h) {
        JJKConfig.get().general.autoAssignGojo = false;
        JJKConfig.get().progression.enabled = true;
        JJKConfig.get().mastery.enabled = true;
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        p.setPos(h.absoluteVec(new Vec3(1, 1, 1)));
        return p;
    }

    /** Makes {@code p} the legitimate owner of a kit, playing it in Survival. */
    private static void own(ServerPlayer p, String kit) {
        KitOwnership own = KitOwnership.get(p.level().getServer());
        if (own.owner(kit) != null) own.release(kit);
        TechniqueProgression.tryClaimKit(p, kit, "test");
        CharacterService.assign(p, Characters.get(kit));
    }

    @GameTest(environment = "jjk-test:mastery_a")
    public void everyTreeLoadsAndEveryMoveHasNodes(GameTestHelper h) {
        Set<String> ids = new HashSet<>();
        for (MasteryTree t : MasteryTrees.all()) ids.add(t.id());
        for (String kit : List.of("gojo", "hakari", "yuji", "yuta", "ryu")) {
            MasteryTree t = MasteryTrees.get(MasteryTree.techniqueId(kit));
            h.assertTrue(t != null, "a technique tree for " + kit);
            h.assertTrue(t.nodes().stream().anyMatch(n -> n.tier() == MasteryNode.Tier.AWAKENING), kit + " has an Awakening node");
            MasteryNode awaken = t.nodes().stream().filter(n -> n.tier() == MasteryNode.Tier.AWAKENING).findFirst().orElseThrow();
            h.assertTrue(!awaken.requires().isEmpty() && awaken.cost() >= 400, kit + "'s Awakening is expensive and has prerequisites");
            // Every move named by the tree is a real move of the kit, and every move of the kit appears in the tree.
            JJKCharacter c = Characters.get(kit);
            Set<String> moves = new HashSet<>();
            for (Ability a : c.abilitiesAllModes()) moves.add(a.id);
            moves.remove("guard");
            moves.remove("dash");
            moves.removeIf(m -> m.endsWith("_dash"));
            // Infinity is Gojo's passive toggle (bound only when the config puts it in the moveset), still a move of his.
            if (kit.equals("gojo")) moves.add("infinity");
            // The Copy Wheel is a menu (it picks Copy's technique), not a move: Copy's node covers it.
            moves.remove("copy_wheel");
            Set<String> covered = new HashSet<>();
            for (MasteryNode n : t.nodes()) {
                if (!n.move().isEmpty()) h.assertTrue(moves.contains(n.move()), kit + " node " + n.id() + " names a real move: " + n.move());
                covered.add(n.move());
                for (MasteryNode.Effect f : n.effects()) {
                    if (f.op() != MasteryNode.Effect.Op.UNLOCK) covered.add(f.key().substring(0, f.key().indexOf('.')));
                }
            }
            Set<String> missing = new HashSet<>(moves);
            missing.removeAll(covered);
            h.assertTrue(missing.isEmpty(), kit + ": moves with no Mastery node: " + missing);
        }
        h.assertTrue(ids.contains("tool/slaughter_demon") && ids.contains("tool/cursed_cleaver"), "both tool trees");
        // Every gated behaviour has a node that opens it.
        Set<String> unlocks = new HashSet<>();
        for (MasteryTree t : MasteryTrees.all()) for (MasteryNode n : t.nodes()) for (MasteryNode.Effect f : n.effects()) {
            if (f.op() == MasteryNode.Effect.Op.UNLOCK) unlocks.add(f.key());
        }
        for (String k : GATED) h.assertTrue(unlocks.contains(k), "a node unlocks " + k);
        h.succeed();
    }

    @GameTest(environment = "jjk-test:mastery_b")
    public void badTreesAreRejected(GameTestHelper h) {
        String sameRow = """
                {"id":"t","kind":"TOOL","owner":"x","title":"T","lanes":["a"],"nodes":[
                {"id":"a","name":"A","cost":1,"lane":0,"row":0},{"id":"b","name":"B","cost":1,"lane":0,"row":0,"requires":["a"]}]}""";
        String unknown = """
                {"id":"t","kind":"TOOL","owner":"x","title":"T","lanes":["a"],"nodes":[{"id":"a","name":"A","cost":1,"row":1,"requires":["zz"]}]}""";
        for (String bad : List.of(sameRow, unknown)) {
            boolean threw = false;
            try {
                MasteryTrees.parseForTest(bad);
            } catch (RuntimeException e) {
                threw = true;
            }
            h.assertTrue(threw, "rejected: " + bad);
        }
        h.succeed();
    }

    @GameTest(environment = "jjk-test:mastery_c")
    public void techniqueTreesAreRetiredToolTreesStillDevelop(GameTestHelper h) {
        ServerPlayer owner = survivor(h);
        own(owner, "gojo");
        String tree = MasteryTree.techniqueId("gojo");
        // A technique comes whole from its storyline: every move and R variant, no nodes to buy.
        h.assertTrue(Mastery.unlocked(owner, "red.limitless") && Mastery.unlocked(owner, "rapid_punches.face_grater"), "the base kit is whole");
        Mastery.award(owner, tree, 500);
        h.assertTrue(Mastery.data(owner).points(tree) == 0, "no Mastery into a technique tree");
        h.assertTrue(Mastery.purchase(owner, tree, "red_focus") == Mastery.Result.NOT_YOURS, "nothing to buy in it");
        // Cursed tool trees still develop: prerequisites and cost.
        String tool = "tool/slaughter_demon";
        h.assertTrue(Mastery.purchase(owner, tool, "flurry") != Mastery.Result.OK, "needs what it builds on (and Mastery)");
        Mastery.award(owner, tool, 8);
        h.assertTrue(Mastery.data(owner).points(tool) == 8, "awarded");
        h.assertTrue(Mastery.purchase(owner, tool, "keen_edge") == Mastery.Result.OK, "bought");
        h.assertTrue(Mastery.purchase(owner, tool, "keen_edge") == Mastery.Result.OWNED, "no buying twice");
        Mastery.respec(owner, tool);
        h.assertTrue(Mastery.data(owner).points(tool) == 8, "respec refunds");
        h.succeed();
    }

    @GameTest(environment = "jjk-test:mastery_d")
    public void gatingAppliesOnlyToASurvivalPlayerOnTheirOwnKit(GameTestHelper h) {
        ServerPlayer p = survivor(h);
        own(p, "gojo");
        h.assertTrue(Mastery.techniqueGated(p) && !Mastery.unlocked(p, "gojo.awakening"), "Survival on their own kit: the Awakening is closed");
        h.assertTrue(Mastery.unlocked(p, "red.limitless"), "but the base kit is whole");
        p.setGameMode(GameType.CREATIVE);
        h.assertTrue(Mastery.unlocked(p, "gojo.awakening") && Mastery.unlocked(p, "tool.cursed_cleaver.heavy_swing"), "Creative: everything");
        p.setGameMode(GameType.SURVIVAL);
        // A bot is never gated.
        var dummy = ModEntities.TRAINING_DUMMY.create(h.getLevel(), EntitySpawnReason.MOB_SUMMONED);
        h.assertTrue(Mastery.unlocked(dummy, "red.limitless") && Mastery.param(dummy, "red.damage") == 1.0, "bots fight with the full kit");
        // A tool's moves are gated for every Survival player, kit or not.
        ServerPlayer none = survivor(h);
        h.assertTrue(!Mastery.unlocked(none, "tool.cursed_cleaver.heavy_swing"), "tool mechanics need their node");
        boolean was = JJKConfig.get().progression.enabled;
        JJKConfig.get().progression.enabled = false;
        try {
            h.assertTrue(Mastery.unlocked(p, "gojo.awakening"), "progression off: everything");
        } finally {
            JJKConfig.get().progression.enabled = was;
        }
        h.succeed();
    }

    @GameTest(maxTicks = 120, environment = "jjk-test:mastery_e")
    public void awakeningWaitsForItsOwnStory(GameTestHelper h) {
        ServerPlayer p = survivor(h);
        own(p, "gojo");
        AbilityCaster c = Casters.get(p);
        c.setAwakening(c.maxAwakening());
        boolean pressed = c.input(AbilitySlot.ULTIMATE, true, 0, 0, null);
        h.assertTrue(!pressed && "mastery".equals(c.lastRefusal), "refused: the base storyline never grants the Awakening: " + c.lastRefusal);
        // A later storyline (or an admin) opens it.
        TechniqueProgression.setAwakened(p, "gojo", true);
        h.assertTrue(Mastery.unlocked(p, "gojo.awakening"), "now open");
        h.assertTrue(c.input(AbilitySlot.ULTIMATE, true, 0, 0, null), "awakening starts: " + c.lastRefusal);
        TechniqueProgression.setAwakened(p, "gojo", false);
        h.succeed();
    }

    @GameTest(environment = "jjk-test:mastery_f")
    public void theRecordSurvivesASave(GameTestHelper h) {
        MasteryData d = MasteryData.EMPTY.earn("tool/slaughter_demon", 40).buy("tool/slaughter_demon", "keen_edge", 8)
                .recordExorcism("GRADE_3", false).recordIncident("GRADE_3");
        var json = MasteryData.CODEC.encodeStart(JsonOps.INSTANCE, d).getOrThrow();
        MasteryData back = MasteryData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        h.assertTrue(back.equals(d), "round trip: " + back);
        h.assertTrue(back.points("tool/slaughter_demon") == 32 && back.has("tool/slaughter_demon", "keen_edge"), "points and purchases");
        h.succeed();
    }

    @GameTest(environment = "jjk-test:mastery_g")
    public void cooldownAndCostNodesChangeTheMove(GameTestHelper h) {
        ServerPlayer p = survivor(h);
        own(p, "gojo");
        AbilityCaster c = Casters.get(p);
        AbilitySlot slot = null;
        for (AbilitySlot s : AbilitySlot.values()) if (c.ability(s) != null && c.ability(s).id.equals("teleport")) slot = s;
        h.assertTrue(slot != null, "Limitless is bound");
        int base = c.ability(slot).cooldown(c);
        c.startCooldown(slot, base);
        int before = c.cooldown(slot);
        c.resetSlot(slot);
        String tree = MasteryTree.techniqueId("gojo");
        Mastery.set(p, Mastery.data(p).buy(tree, "thin_infinity", 0).buy(tree, "blink", 0));
        c.startCooldown(slot, base);
        int after = c.cooldown(slot);
        h.assertTrue(after == Math.round(before * 0.8f), "Quick Blink: " + before + " -> " + after);
        h.succeed();
    }
}
