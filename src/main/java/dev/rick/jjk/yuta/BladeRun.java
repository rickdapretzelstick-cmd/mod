package dev.rick.jjk.yuta;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.HitResult;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.core.domain.DomainInstance;
import dev.rick.jjk.core.domain.DomainManager;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.hitbox.HitShape;
import dev.rick.jjk.core.hitbox.HitboxQuery;
import dev.rick.jjk.hakari.HakariCombat;
import dev.rick.jjk.util.Motion;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A katana taken up inside Authentic Mutual Love: Yuta runs about 55 studs at the nearest enemy and swings it, and the
 * technique it carries goes off, on the one it hit or (missed) in its own way:
 * <ul>
 *   <li>Shrine: Cleave slashes the one hit four times (5 each), stunning them and knocking them back; missed, a large
 *       horizontal Dismantle slash goes out ahead (20). Finishers take the limbs and head, or bisect them.</li>
 *   <li>Thin Ice Breaker: the sky breaks like thin ice (20) and their ragdoll can't be cancelled; missed, the shattered
 *       air still harms whoever is in the way (15, no true ragdoll).</li>
 *   <li>Clairvoyance: blood drawn, a manga panel marks them for 10 seconds and their attacks on him are dodged;
 *       missed, the nearest one is marked from afar for 5.</li>
 *   <li>Cursed Speech: "落ちれ!" ("Plummet!"), grounding them (15); missed, "止まれ!" ("Stop!") freezes everyone in the
 *       domain for about 3 seconds (360-blockable). Its finisher: "死ね!" ("Die!").</li>
 *   <li>Shikigami: three flying Rika heads swarm them for 3 seconds (27); missed, they swarm the nearest one anyway,
 *       blockable to half and unable to finish anyone.</li>
 * </ul>
 * Every swing is 8, unblockable, bypassing ragdoll; a direct hit counts toward Jacob's Ladder.
 */
public final class BladeRun extends AbilityInstance {
    private final DomainTechnique technique;
    @Nullable private LivingEntity target;
    private Vec3 dir;
    private int swingAt = -1;
    private int endAt = -1;

    BladeRun(AbilityCaster caster, LivingEntity user, DomainTechnique technique) {
        super(AuthenticMutualLoveAbility.INSTANCE, new AbilityContext(caster, user, (ServerLevel) user.level(), AbilitySlot.SKILL_4, 0, 0, null));
        this.technique = technique;
        dir = HakariCombat.flat(user);
    }

    @Override
    public void start() {
        DomainInstance d = DomainManager.ownedBy(user);
        if (d != null) {
            LivingEntity best = null;
            double bd = Double.MAX_VALUE;
            for (LivingEntity e : AuthenticMutualLove.enemies(d)) {
                double dd = e.distanceToSqr(user);
                if (dd < bd) {
                    bd = dd;
                    best = e;
                }
            }
            target = best;
        }
        YutaCombat.drawKatana(user);
        YutaState.of(user).fists = true;
        Anim.play(user, "yuta_blade_run");
        setPhase(0, YutaCombat.cfg().bladeRunTicks);
        Fx.play(level, "blade_pickup", user.position().add(0, 1, 0), Vec3.ZERO, technique.ordinal(), user.getId());
    }

    @Override
    public void tick() {
        JJKConfig.Yuta cfg = YutaCombat.cfg();
        if (endAt >= 0) {
            Motion.set(user, user.getDeltaMovement().multiply(0.4, 1, 0.4));
            if (age >= endAt) finish();
            return;
        }
        if (swingAt < 0) {
            if (target != null && target.isAlive()) {
                Vec3 to = target.position().subtract(user.position());
                Vec3 flat = new Vec3(to.x, 0, to.z);
                if (flat.lengthSqr() > 1e-4) dir = flat.normalize();
                HakariCombat.faceTowards(user, target.getBoundingBox().getCenter());
                if (flat.length() < 2.0) {
                    swing(cfg);
                    return;
                }
            }
            HakariCombat.drive(user, dir, cfg.bladeRun / cfg.bladeRunTicks);
            if (age >= cfg.bladeRunTicks) swing(cfg);
        }
    }

    private void swing(JJKConfig.Yuta cfg) {
        swingAt = age;
        Motion.set(user, dir.scale(0.2));
        Anim.play(user, "yuta_blade_swing");
        Fx.play(level, "blade_swing", user.position().add(0, 1.1, 0), dir, technique.ordinal(), user.getId());
        LivingEntity hit = HakariCombat.firstInFront(user, 2.8, 2.4, 2.4);
        boolean landed = false;
        if (hit != null) {
            HitResult r = HakariCombat.hit(YutaCombat.strike(user, AuthenticMutualLoveAbility.ID, cfg.bladeDamage, true).tag(AttackTag.NO_METER).tag(AttackTag.OTG)
                    .knockback(Knockback.HOLD).hitstun(20).noComboScaling().fx("blade_hit", 1.2f).build(), hit);
            landed = r.connected();
            if (landed) {
                YutaState s = YutaState.of(user);
                s.ladderHits++;
                // The fourth: Jacob's Ladder is ready (a gold burst round him, for everyone to see).
                if (s.ladderHits == cfg.ladderHits && !s.ladderUsed) Fx.play(level, "ladder_ready", user.position().add(0, 1, 0), Vec3.ZERO, 1f, user.getId());
                YutaSync.send(user);
            }
        }
        if (landed) onHit(cfg, hit);
        else onMiss(cfg);
        endAt = age + 14;
    }

    private void onHit(JJKConfig.Yuta cfg, LivingEntity t) {
        Vec3 at = t.getBoundingBox().getCenter();
        switch (technique) {
            case SHRINE -> {
                // Cleave: four slashes, stunned and knocked back.
                Fx.play(level, "aml_cleave", at, dir, 1f, user.getId());
                if (YutaCombat.finishable(t)) {
                    YutaCombat.execute(user, t, AuthenticMutualLoveAbility.ID, "aml_shrine_finisher");
                    return;
                }
                for (int i = 0; i < 4; i++) {
                    boolean last = i == 3;
                    HakariCombat.hit(YutaCombat.strike(user, AuthenticMutualLoveAbility.ID, cfg.shrineSlashDamage, true).tag(AttackTag.NO_METER).tag(AttackTag.TECHNIQUE, AttackTag.OTG)
                            .knockback(last ? Knockback.directional(dir, 1.3, 0.35) : Knockback.HOLD).hitstun(last ? 30 : 12)
                            .noComboScaling().fx("aml_cleave_hit", 1f).build(), t);
                }
            }
            case THIN_ICE_BREAKER -> {
                Fx.play(level, "thin_ice_breaker", at, dir, 1f, user.getId());
                Fx.shake(level, at, 24, 0.9f, 10);
                HakariCombat.hit(YutaCombat.strike(user, AuthenticMutualLoveAbility.ID, cfg.thinIceDamage, true).tag(AttackTag.NO_METER).tag(AttackTag.TECHNIQUE, AttackTag.OTG, AttackTag.SURE_HIT)
                        .knockback(Knockback.set(dir.scale(1.0).add(0, 0.7, 0))).hitstun(40).status(CombatStatus.LAUNCHED, 40)
                        .status(CombatStatus.TRUE_RAGDOLL, 60).noComboScaling().fx("yuta_impact", 1.4f).build(), t);
            }
            case CLAIRVOYANCE -> {
                Fx.play(level, "clairvoyance_mark", at, dir, 1f, t.getId());
                Statuses.apply(t, CombatStatus.CLAIRVOYANCE, cfg.clairvoyanceTicks);
                YutaState.of(user).markedBy(t);
            }
            case CURSED_SPEECH -> {
                if (YutaCombat.finishable(t)) {
                    // "Die!"
                    Fx.play(level, "speech_die", user.getEyePosition(), dir, 1f, user.getId());
                    YutaCombat.execute(user, t, AuthenticMutualLoveAbility.ID, "speech_die_finisher");
                    return;
                }
                // "Plummet!"
                Fx.play(level, "speech_plummet", user.getEyePosition(), dir, 1f, user.getId());
                HakariCombat.hit(YutaCombat.strike(user, AuthenticMutualLoveAbility.ID, cfg.plummetDamage, true).tag(AttackTag.NO_METER).tag(AttackTag.TECHNIQUE, AttackTag.OTG)
                        .knockback(Knockback.set(new Vec3(0, -1.6, 0))).hitstun(30).status(CombatStatus.SPIKED, 30)
                        .noComboScaling().fx("yuta_impact", 1.3f).build(), t);
            }
            case SHIKIGAMI -> caster.addOverlay(new ShikigamiSwarm(caster, user, t, false));
        }
    }

    private void onMiss(JJKConfig.Yuta cfg) {
        DomainInstance d = DomainManager.ownedBy(user);
        switch (technique) {
            case SHRINE -> {
                // A large horizontal Dismantle out ahead.
                Vec3 from = user.position().add(0, 1.1, 0);
                Fx.play(level, "aml_dismantle", from, dir, 1f, user.getId());
                for (LivingEntity t : HitboxQuery.targets(user, HitShape.orientedBox(from, dir, 14, 6, 2.4), 0.3, false)) {
                    if (YutaCombat.finishable(t)) {
                        YutaCombat.execute(user, t, AuthenticMutualLoveAbility.ID, "aml_bisect_finisher");
                        continue;
                    }
                    HakariCombat.hit(YutaCombat.strike(user, AuthenticMutualLoveAbility.ID, cfg.shrineMissDamage, true).tag(AttackTag.NO_METER).tag(AttackTag.TECHNIQUE, AttackTag.EXPLOSION, AttackTag.OTG)
                            .knockback(Knockback.directional(dir, 1.6, 0.4)).hitstun(26).status(CombatStatus.LAUNCHED, 24)
                            .noComboScaling().fx("yuta_impact", 1.4f).build(), t);
                }
            }
            case THIN_ICE_BREAKER -> {
                Vec3 from = user.position().add(0, 1.1, 0);
                Fx.play(level, "thin_ice_breaker", from.add(dir.scale(3)), dir, 1.4f, user.getId());
                for (LivingEntity t : HitboxQuery.targets(user, HitShape.orientedBox(from, dir, 9, 4, 3), 0.3, false)) {
                    HakariCombat.hit(YutaCombat.strike(user, AuthenticMutualLoveAbility.ID, cfg.thinIceMissDamage, true).tag(AttackTag.NO_METER).tag(AttackTag.TECHNIQUE, AttackTag.OTG)
                            .knockback(Knockback.directional(dir, 1.1, 0.5)).hitstun(26).status(CombatStatus.LAUNCHED, 26)
                            .noComboScaling().fx("yuta_impact", 1.1f).build(), t);
                }
            }
            case CLAIRVOYANCE -> {
                // Marked from afar, for half as long.
                LivingEntity t = nearest(d);
                if (t != null) {
                    Fx.play(level, "clairvoyance_mark", t.getBoundingBox().getCenter(), dir, 1f, t.getId());
                    Statuses.apply(t, CombatStatus.CLAIRVOYANCE, cfg.clairvoyanceMissTicks);
                    YutaState.of(user).markedBy(t);
                }
            }
            case CURSED_SPEECH -> {
                // "Stop!": everybody in the domain freezes.
                Fx.play(level, "speech_stop", user.getEyePosition(), dir, d != null ? (float) d.radius : 12f, user.getId());
                if (d != null) {
                    for (LivingEntity t : AuthenticMutualLove.enemies(d)) {
                        HakariCombat.hit(YutaCombat.strike(user, AuthenticMutualLoveAbility.ID, 0, false).tag(AttackTag.NO_METER).tag(AttackTag.BLOCKABLE_360, AttackTag.TECHNIQUE, AttackTag.OTG)
                                .knockback(Knockback.set(Vec3.ZERO)).status(CombatStatus.STOPPED, cfg.stopTicks).noComboScaling()
                                .fx("speech_bound", 1f).build(), t);
                    }
                }
            }
            case SHIKIGAMI -> {
                LivingEntity t = nearest(d);
                if (t != null) caster.addOverlay(new ShikigamiSwarm(caster, user, t, true));
            }
        }
    }

    @Nullable
    private LivingEntity nearest(@Nullable DomainInstance d) {
        if (d == null) return target;
        List<LivingEntity> all = AuthenticMutualLove.enemies(d);
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : all) {
            double dd = e.distanceToSqr(user);
            if (dd < bd) {
                bd = dd;
                best = e;
            }
        }
        return best;
    }

    @Override
    public float movementMultiplier() {
        return 0f;
    }

    /** Three winged Rika heads swarming someone for three seconds (27). Missed swing: blockable to half, can't finish. */
    static final class ShikigamiSwarm extends AbilityInstance {
        private final LivingEntity victim;
        private final boolean weak;

        ShikigamiSwarm(AbilityCaster caster, LivingEntity user, LivingEntity victim, boolean weak) {
            super(AuthenticMutualLoveAbility.INSTANCE, new AbilityContext(caster, user, (ServerLevel) user.level(), AbilitySlot.SKILL_4, 0, 0, null));
            this.victim = victim;
            this.weak = weak;
        }

        @Override
        public void start() {
            Fx.play(level, "shikigami_swarm", victim.getBoundingBox().getCenter(), Vec3.ZERO, YutaCombat.cfg().shikigamiTicks, victim.getId());
        }

        @Override
        public void tick() {
            JJKConfig.Yuta cfg = YutaCombat.cfg();
            if (!victim.isAlive() || age > cfg.shikigamiTicks) {
                finish();
                return;
            }
            if (age % 10 != 0) return;
            float per = cfg.shikigamiDamage / (cfg.shikigamiTicks / 10f);
            boolean guarding = Combat.isGuarding(victim);
            if (weak && guarding) per *= 0.5f;
            if (weak && victim.getHealth() <= per + 0.5f) per = Math.max(0, victim.getHealth() - 1f);
            if (!weak && YutaCombat.finishable(victim)) {
                YutaCombat.execute(user, victim, AuthenticMutualLoveAbility.ID, "shikigami_finisher");
                finish();
                return;
            }
            if (per <= 0) return;
            HakariCombat.hit(dev.rick.jjk.core.combat.Hit.builder(user, AuthenticMutualLoveAbility.ID).type(dev.rick.jjk.registry.ModDamageTypes.TECHNIQUE)
                    .damage(per).tag(AttackTag.TECHNIQUE, AttackTag.OTG, AttackTag.UNBLOCKABLE, AttackTag.NO_METER).origin(victim.position().add(0, 2, 0))
                    .knockback(Knockback.HOLD).hitstun(12).noComboScaling().fx("shikigami_bite", 1f).build(), victim);
        }

        @Override
        public boolean exclusive() {
            return false;
        }

        @Override
        public boolean uninterruptible() {
            return true;
        }
    }
}
