package dev.rick.jjk.test;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.character.CharacterService;
import dev.rick.jjk.core.character.Characters;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.entity.TrainingDummy;
import dev.rick.jjk.gojo.GojoCharacter;
import dev.rick.jjk.hakari.HakariCharacter;
import dev.rick.jjk.registry.ModEntities;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

/**
 * Every move of both characters, in every kit, against a dummy: each one plays out and frees its user, leaves nobody
 * held, and goes on its Jujutsu Shenanigans wiki cooldown.
 */
public class MovesetAuditTests {
    /** The wiki's cooldowns, in ticks. */
    static final Map<String, Integer> WIKI_COOLDOWN = Map.ofEntries(
            // Gojo (Honored One)
            Map.entry("blue", 13 * 20), Map.entry("red", 20 * 20), Map.entry("rapid_punches", 15 * 20), Map.entry("twofold_kick", 18 * 20),
            Map.entry("teleport", 15 * 20), Map.entry("max_blue", 17 * 20), Map.entry("max_red", 10 * 20), Map.entry("hollow_purple", 40 * 20),
            Map.entry("unlimited_void", 120 * 20),
            // Hakari (Restless Gambler)
            Map.entry("reserve_balls", 12 * 20), Map.entry("shutter_doors", 15 * 20), Map.entry("rough_energy", 14 * 20),
            Map.entry("fever_breaker", 23 * 20), Map.entry("door_guard", 16 * 20), Map.entry("lucky_volley", 10 * 20),
            Map.entry("lucky_rushdown", 15 * 20), Map.entry("overwhelming_luck", 20 * 20), Map.entry("energy_surge", 25 * 20),
            Map.entry("rhythm", 8 * 20),
            // Yuji (Vessel / King of Curses)
            Map.entry("cursed_strikes", 14 * 20), Map.entry("crushing_blow", 15 * 20), Map.entry("divergent_fist", 18 * 20),
            Map.entry("manji_kick", 20 * 20), Map.entry("combat_instincts", 2 * 20), Map.entry("cleave", 12 * 20), Map.entry("dismantle", 13 * 20),
            Map.entry("open", 40 * 20), Map.entry("rush", 15 * 20), Map.entry("malevolent_shrine", 120 * 20));
    /** Moves that only do something alongside another (Combat Instincts feints; alone it needs a throwable). */
    static final java.util.Set<String> NOT_STANDALONE = java.util.Set.of("combat_instincts");

    private static final AbilitySlot[] MOVES = {AbilitySlot.SKILL_1, AbilitySlot.SKILL_2, AbilitySlot.SKILL_3, AbilitySlot.SKILL_4, AbilitySlot.SKILL_5};

    private static void floor(GameTestHelper h) {
        // The shared test config (whichever class set it) is fine: only cooldowns and completion are checked here.
        for (int x = -2; x < 10; x++) for (int z = -2; z < 10; z++) h.setBlock(x, 0, z, Blocks.STONE);
    }

    private static TrainingDummy dummy(GameTestHelper h, double x, double z) {
        TrainingDummy d = h.spawn(ModEntities.TRAINING_DUMMY, new Vec3(x, 1, z));
        d.setMode(TrainingDummy.Mode.STAND);
        return d;
    }

    private static void face(LivingEntity e, Vec3 target) {
        Vec3 d = target.subtract(e.getEyePosition());
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
        e.setYRot(yaw);
        e.setYHeadRot(yaw);
        e.setYBodyRot(yaw);
        e.setXRot(pitch);
    }

    @GameTest(maxTicks = 5)
    public void configCooldownsMatchTheWiki(GameTestHelper h) {
        JJKConfig defaults = new JJKConfig();
        JJKConfig saved = JJKConfig.get();
        JJKConfig.set(defaults);
        try {
            for (String character : new String[] {GojoCharacter.ID, HakariCharacter.ID, dev.rick.jjk.yuji.YujiCharacter.ID}) {
                var ch = Characters.get(character);
                for (Ability a : ch.abilitiesAllModes()) {
                    Integer want = WIKI_COOLDOWN.get(a.id);
                    if (want == null) continue;
                    h.assertValueEqual(a.cooldown(null), want, a.id + " cooldown (wiki)");
                }
            }
        } finally {
            JJKConfig.set(saved);
        }
        h.succeed();
    }

    /** Presses every move of the kit in turn at a dummy in front and checks each one. */
    private static void runKit(GameTestHelper h, String character, boolean awakened) {
        floor(h);
        TrainingDummy target = dummy(h, 5, 4);
        target.setAutoHeal(true);
        TrainingDummy user = dummy(h, 2, 4);
        CharacterService.assign(user, Characters.get(character));
        AbilityCaster c = Casters.get(user);
        c.setNoCost(false);
        if (awakened) c.enterAwakening();
        Vec3 home = user.position(), spot = target.position();
        GameTestSequence seq = h.startSequence();
        for (AbilitySlot slot : MOVES) {
            int[] pressedAt = new int[1];
            String[] id = new String[1];
            seq.thenExecute(() -> {
                // Reset the scene: both back in place, facing, fresh, full resources.
                user.teleportTo(home.x, home.y, home.z);
                target.teleportTo(spot.x, spot.y, spot.z);
                target.setHealth(target.getMaxHealth());
                Combat.state(target).clearAll();
                Combat.state(user).clearAll();
                face(user, target.getBoundingBox().getCenter());
                c.setEnergy(c.maxEnergy());
                if (awakened) c.setAwakening(c.maxAwakening());
                Ability a = c.ability(slot);
                id[0] = a == null || NOT_STANDALONE.contains(a.id) ? "" : a.id;
                if (id[0].isEmpty()) return;
                pressedAt[0] = (int) h.getTick();
                h.assertTrue(c.input(slot, true, 0, 0, target), character + (awakened ? " (awakened) " : " ") + a.id + " activates (" + c.lastRefusal + ")");
            }).thenIdle(4).thenExecute(() -> {
                if (!id[0].isEmpty()) c.input(slot, false, 0, 0, target);
            }).thenWaitUntil(() -> {
                if (id[0].isEmpty()) return;
                h.assertTrue(!c.isBusy() && !c.melee.isCommitted(), id[0] + " finishes and frees its user");
                // Techniques that outlive their cast (Lapse Blue MAX's vortex) have to run out first.
                h.assertTrue(h.getLevel().getEntitiesOfClass(dev.rick.jjk.entity.TechniqueEntity.class, user.getBoundingBox().inflate(40),
                        e -> e.owner() == user).isEmpty(), id[0] + "'s technique has run out");
            }).thenIdle(3).thenExecute(() -> {
                if (id[0].isEmpty()) return;
                String who = character + (awakened ? " (awakened) " : " ") + id[0];
                h.assertTrue(!Combat.has(target, CombatStatus.GRABBED) && !Combat.has(target, CombatStatus.PULLED), who + ": nobody left held");
                h.assertTrue(!Combat.has(user, CombatStatus.GRABBED) && !Combat.has(user, CombatStatus.AWAKENING), who + ": its user isn't stuck");
                Integer want = WIKI_COOLDOWN.get(id[0]);
                if (want != null) {
                    int cd = c.cooldown(slot);
                    int elapsed = (int) h.getTick() - pressedAt[0];
                    h.assertTrue(cd <= want && cd >= want - elapsed - 2, who + " is on its wiki cooldown (" + cd + " of " + want + ", " + elapsed + " ticks in)");
                }
            });
        }
        seq.thenSucceed();
    }

    @GameTest(maxTicks = 900, padding = 16, skyAccess = true)
    public void gojoBaseKitPlaysOut(GameTestHelper h) {
        runKit(h, GojoCharacter.ID, false);
    }

    @GameTest(maxTicks = 900, padding = 16, skyAccess = true)
    public void gojoSixEyesKitPlaysOut(GameTestHelper h) {
        runKit(h, GojoCharacter.ID, true);
    }

    @GameTest(maxTicks = 900, padding = 16, skyAccess = true)
    public void hakariBaseKitPlaysOut(GameTestHelper h) {
        runKit(h, HakariCharacter.ID, false);
    }

    @GameTest(maxTicks = 900, padding = 16, skyAccess = true)
    public void yujiBaseKitPlaysOut(GameTestHelper h) {
        runKit(h, dev.rick.jjk.yuji.YujiCharacter.ID, false);
    }

    // Open's blast reaches 48 blocks: its own widely spaced batch.
    @GameTest(maxTicks = 1200, padding = 60, skyAccess = true, environment = "jjk-test:blast")
    public void yujiKingOfCursesKitPlaysOut(GameTestHelper h) {
        runKit(h, dev.rick.jjk.yuji.YujiCharacter.ID, true);
    }

    @GameTest(maxTicks = 900, padding = 16, skyAccess = true)
    public void hakariJackpotKitPlaysOut(GameTestHelper h) {
        runKit(h, HakariCharacter.ID, true);
    }
}
