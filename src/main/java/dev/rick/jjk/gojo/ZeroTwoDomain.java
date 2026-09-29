package dev.rick.jjk.gojo;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.combat.HitResolver;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.core.combat.Targeting;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.hakari.HakariCombat;
import dev.rick.jjk.registry.ModDamageTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The 0.2 Domain (JJS alternate Awakening): the Special pressed during the Awakening sequence. Right after the blindfold
 * comes off Gojo expands Infinite Void for two tenths of a second — losing his i-frames — which spreads the sure hit
 * over a wide range but exposes everyone in it for only 7 seconds. Then a long rush at unbelievable speed in three
 * phases (total i-frames during each run, lost briefly between them): 7 hits of 5, 6 hits of 20, a last hit of 65.
 * Holding the heads of two targets when it ends finishes them both. Afterwards he is burnt out: back to the base
 * moveset, all of it on cooldown except Limitless.
 */
final class ZeroTwoDomain {
    private static final int GAP = 4;
    private final ServerLevel level;
    private final AbilityCaster caster;
    private final LivingEntity user;
    private final List<LivingEntity> targets = new ArrayList<>();
    private int age;
    private int phase1End, phase2Start, phase2End, finalAt;
    private boolean done;

    ZeroTwoDomain(ServerLevel level, AbilityCaster caster, LivingEntity user) {
        this.level = level;
        this.caster = caster;
        this.user = user;
        JJKConfig.Gojo cfg = JJKConfig.get().gojo;
        Statuses.remove(user, CombatStatus.AWAKENING); // no i-frames for the 0.2 seconds
        Vec3 c = user.position();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(cfg.zeroTwoRadius),
                e -> e != user && e.isAlive() && Targeting.canTarget(user, e) && e.distanceTo(user) <= cfg.zeroTwoRadius)) {
            targets.add(e);
            Statuses.apply(e, CombatStatus.OVERLOAD, cfg.zeroTwoStun);
            Fx.play(level, "domain_surehit", e.position().add(0, 1, 0), Vec3.ZERO, 1f, e.getId());
        }
        targets.sort((a, b) -> Double.compare(a.distanceToSqr(user), b.distanceToSqr(user)));
        Fx.play(level, "domain_sealed", c, Vec3.ZERO, (float) cfg.zeroTwoRadius * 0.5f, user.getId());
        Fx.play(level, "sfx:zero_two_open", c, Vec3.ZERO, 5f, user.getId());
        Fx.play(level, "music:zero_two_music", c, Vec3.ZERO, 4f, user.getId());
        Fx.flash(level, c, cfg.zeroTwoRadius + 16, 0xE0FFFFFF, 6);
        phase1End = 8 + cfg.zeroTwoPhase1Hits * 3;
        phase2Start = phase1End + GAP;
        phase2End = phase2Start + cfg.zeroTwoPhase2Hits * 5;
        finalAt = phase2End + GAP + 6;
        Anim.play(user, "domain_release");
    }

    private void aliveOnly() {
        targets.removeIf(t -> !t.isAlive() || t.level() != level);
    }

    /** Dashes to (and hits) the target for hit number {@code n}. */
    private void strike(int n, float damage, boolean last) {
        aliveOnly();
        if (targets.isEmpty()) return;
        LivingEntity t = targets.get(n % targets.size());
        Vec3 spot = TeleportAbility.aroundFront(user, t, (n * 97) % 360);
        if (spot != null) TeleportAbility.arrive(level, user, spot, t);
        Anim.play(user, n % 2 == 0 ? "light_1" : "light_2");
        Fx.play(level, "sfx:zero_two_hit", t.getBoundingBox().getCenter(), Vec3.ZERO, 1.5f, user.getId());
        Hit hit = Hit.builder(user, "zero_two_domain").type(ModDamageTypes.MELEE).damage(damage)
                .tag(AttackTag.MELEE, AttackTag.UNBLOCKABLE, AttackTag.OTG, AttackTag.ULTIMATE).origin(user.getEyePosition())
                .knockback(last ? Knockback.directional(HakariCombat.flat(user), 2.2, 0.8) : Knockback.HOLD).hitstun(last ? 30 : 12)
                .noComboScaling().fx(last ? "finisher" : "hit_heavy", last ? 1f : 0.8f).build();
        if (last) hit = hit.toBuilder().status(CombatStatus.LAUNCHED, 30).build();
        HitResolver.resolve(hit, t);
    }

    /** True when finished. */
    boolean tick() {
        if (done) return true;
        JJKConfig.Gojo cfg = JJKConfig.get().gojo;
        age++;
        boolean running = (age >= 8 && age < phase1End) || (age >= phase2Start && age < phase2End) || (age >= finalAt - 4 && age <= finalAt);
        if (running) Statuses.apply(user, CombatStatus.EVADING, 2);
        // Each run starts with a burst of speed; the second phase is the barrage; the last hit slows everything down.
        if (age == 8 || age == phase2Start) Fx.play(level, "sfx:zero_two_boost", user.position(), Vec3.ZERO, 2f, user.getId());
        if (age == phase2Start) Fx.play(level, "sfx:zero_two_barrage", user.position(), Vec3.ZERO, 2f, user.getId());
        if (age == finalAt - 4) Fx.play(level, "sfx:zero_two_slowdown", user.position(), Vec3.ZERO, 2f, user.getId());
        if (age >= 8 && age < phase1End && (age - 8) % 3 == 0) strike((age - 8) / 3, cfg.zeroTwoPhase1Damage, false);
        if (age >= phase2Start && age < phase2End && (age - phase2Start) % 5 == 0) strike(100 + (age - phase2Start) / 5, cfg.zeroTwoPhase2Damage, false);
        if (age == finalAt) {
            aliveOnly();
            if (targets.size() >= 2) {
                // Holding the heads of two targets: both are finished, and he gasps for air.
                Statuses.apply(user, CombatStatus.EVADING, 40);
                Anim.play(user, "awaken");
                Fx.play(level, "sfx:zero_two_breathe", user.position(), Vec3.ZERO, 2f, user.getId());
                GojoCombat.execute(user, targets.get(0), "zero_two_domain", "finisher");
                GojoCombat.execute(user, targets.get(1), "zero_two_domain", "finisher");
            } else {
                strike(0, cfg.zeroTwoFinalDamage, true);
            }
            Fx.shake(level, user.position(), 40, 1.3f, 18);
        }
        if (age >= finalAt + 10) {
            burnOut();
            return true;
        }
        return false;
    }

    /** Burnt out: the base moveset, all on cooldown except Limitless. */
    private void burnOut() {
        if (done) return;
        done = true;
        caster.endAwakening("zero_two_domain");
        for (AbilitySlot s : new AbilitySlot[] {AbilitySlot.SKILL_1, AbilitySlot.SKILL_2, AbilitySlot.SKILL_3, AbilitySlot.SKILL_4}) {
            Ability a = caster.ability(s);
            if (a != null) caster.startCooldown(s, a.cooldown(caster));
        }
    }

    /** The cast ended early (death, disconnect): still burnt out. */
    void abort() {
        burnOut();
    }
}
