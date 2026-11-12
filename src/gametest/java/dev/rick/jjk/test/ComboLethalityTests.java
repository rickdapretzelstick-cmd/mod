package dev.rick.jjk.test;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.character.CharacterService;
import dev.rick.jjk.core.character.Characters;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.combat.HitResolver;
import dev.rick.jjk.core.combat.HitResult;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.entity.TrainingDummy;
import dev.rick.jjk.registry.ModEntities;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Multi-hit specials finish before they kill: an earlier hit of one ({@code Hit.Builder.sparing}) hurts, knocks and
 * stuns as always but never takes the last point of health, so the move plays out and its final hit is the lethal one;
 * basic attacks are never sparing. Ryu's Second Helping, a homing dash onto someone, refuses with nobody to dash at.
 */
public class ComboLethalityTests {
    private static ServerPlayer fighter(GameTestHelper h) {
        JJKConfig.get().general.autoAssignGojo = false;
        for (int x = 0; x < 10; x++) for (int z = 0; z < 10; z++) h.setBlock(x, 0, z, Blocks.STONE);
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.setPos(h.absoluteVec(new Vec3(2.5, 1, 2.5)));
        p.setYRot(-90);
        p.setYHeadRot(-90);
        return p;
    }

    private static TrainingDummy dummy(GameTestHelper h, ServerPlayer p, float health) {
        TrainingDummy d = h.spawn(ModEntities.TRAINING_DUMMY, new Vec3(3.9, 1, 2.5));
        d.setMode(TrainingDummy.Mode.STAND);
        d.setAutoHeal(false);
        d.setHealth(health);
        return d;
    }

    private static HitResult blow(ServerPlayer p, TrainingDummy d, float damage, boolean sparing) {
        Hit.Builder b = Hit.builder(p, "test_combo").damage(damage).tag(AttackTag.MELEE).origin(p.getEyePosition()).noComboScaling()
                .knockback(Knockback.HOLD).hitstun(10);
        if (sparing) b.sparing();
        return HitResolver.resolve(b.build(), d);
    }

    @GameTest(maxTicks = 40, environment = "jjk-test:black_flash_a")
    public void earlierHitsSpareTheLastPointAndTheFinalHitKills(GameTestHelper h) {
        ServerPlayer p = fighter(h);
        // 1. Enough health for the whole move: every hit does its full damage.
        TrainingDummy a = dummy(h, p, 20f);
        blow(p, a, 4f, true);
        if (Math.abs(a.getHealth() - 16f) > 0.01f) h.fail("a sparing hit should do its full damage when it isn't lethal: " + a.getHealth());
        // 2. Would die on the first hit: it survives it at one point, still hurt and reacting.
        TrainingDummy b = dummy(h, p, 3f);
        HitResult r = blow(p, b, 10f, true);
        if (!r.connected()) h.fail("the sparing hit must still connect");
        if (!b.isAlive() || b.getHealth() > 1.01f || b.getHealth() < 0.99f) h.fail("a sparing hit must leave one point: " + b.getHealth());
        // 3. Would die halfway: two sparing hits leave one point, the final (ordinary) hit kills.
        blow(p, b, 10f, true);
        if (!b.isAlive()) h.fail("still alive after the second sparing hit");
        blow(p, b, 2f, false);
        if (b.isAlive()) h.fail("the final hit must kill");
        // 4. Just enough to die on the final hit; and an ordinary (basic-attack) hit kills outright at any point.
        TrainingDummy c = dummy(h, p, 5f);
        blow(p, c, 4f, true);
        blow(p, c, 4f, false);
        if (c.isAlive()) h.fail("the final hit kills what the earlier ones left");
        TrainingDummy m1 = dummy(h, p, 3f);
        blow(p, m1, 10f, false);
        if (m1.isAlive()) h.fail("an ordinary hit (an M1) kills as it always did");
        h.succeed();
    }

    @GameTest(maxTicks = 40, environment = "jjk-test:black_flash_a")
    public void secondHelpingRefusesWithoutATarget(GameTestHelper h) {
        ServerPlayer p = fighter(h);
        CharacterService.assign(p, Characters.get("ryu"));
        AbilityCaster c = Casters.get(p);
        c.setNoCost(true);
        if (c.input(AbilitySlot.SKILL_3, true, 0, 0, null)) h.fail("Second Helping activated with nobody to dash at");
        if (c.isBusy()) h.fail("nothing should be casting");
        TrainingDummy d = dummy(h, p, 20f);
        p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, d.getEyePosition());
        if (!c.input(AbilitySlot.SKILL_3, true, 0, 0, d)) h.fail("Second Helping should activate on a target in front: " + c.lastRefusal);
        h.succeed();
    }
}
