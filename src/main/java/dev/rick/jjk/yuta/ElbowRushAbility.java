package dev.rick.jjk.yuta;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.HitResult;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.gojo.TeleportAbility;
import dev.rick.jjk.hakari.HakariCombat;
import dev.rick.jjk.util.Motion;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Elbow Rush (JJS True Love, 15s). He dashes about 38.5 studs forward swinging his right arm into an elbow strike (4)
 * that sends them spinning away, then appears behind them with Rika in front so both barrage them from back and front
 * (5; 8 with Rika), and a last blow launches them far ahead (6). Unblockable; can't bypass ragdoll; no lock-on. The
 * Awakening's drain halts during it; a missed elbow has tremendous endlag; if Rika was busy when it landed the barrage
 * is his alone. Rika attacking them gives Yuta their technique. Afterward he fights with his fists again.
 */
public final class ElbowRushAbility extends Ability {
    public static final String ID = "elbow_rush";

    public ElbowRushAbility() {
        super(ID);
    }

    @Override
    public Kind kind() {
        return Kind.INSTANT;
    }

    @Override
    public float cost(AbilityCaster caster) {
        return 60f;
    }

    @Override
    public int cooldown(AbilityCaster caster) {
        return YutaCombat.cfg().elbowCooldown;
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        YutaState.of(ctx.user()).fists = true;
        Statuses.remove(ctx.user(), CombatStatus.KATANA);
        return new Instance(this, ctx);
    }

    static final class Instance extends AbilityInstance {
        private final Vec3 dir;
        @Nullable private LivingEntity victim;
        @Nullable private RikaEntity rika;
        private int hitAt = -1;
        private int endAt = -1;
        private int flurryHits;

        Instance(Ability ability, AbilityContext ctx) {
            super(ability, ctx);
            dir = HakariCombat.flat(ctx.user());
        }

        @Override
        public void start() {
            Anim.play(user, "yuta_elbow_rush");
            setPhase(0, YutaCombat.cfg().elbowDashTicks);
            Fx.play(level, "elbow_dash", user.position().add(0, 1, 0), dir, 1f, user.getId());
        }

        @Override
        public void tick() {
            JJKConfig.Yuta cfg = YutaCombat.cfg();
            // The Awakening's timer stands still meanwhile.
            if (caster.isAwakened()) caster.setAwakening(caster.awakening() + caster.character().awakeningDrainPerSecond() / 20f);
            if (endAt >= 0) {
                if (victim == null) Motion.set(user, user.getDeltaMovement().multiply(0.3, 1, 0.3));
                if (age >= endAt) finish();
                return;
            }
            if (victim != null) {
                barrage(cfg);
                return;
            }
            HakariCombat.drive(user, dir, cfg.elbowDistance / cfg.elbowDashTicks);
            LivingEntity t = HakariCombat.firstInFront(user, 1.6, 1.8, 2.2);
            if (t != null && !YutaCombat.ragdolled(t) || t != null && dev.rick.jjk.core.combat.Combat.isAirborne(t)) {
                elbow(cfg, t);
                return;
            }
            if (age >= cfg.elbowDashTicks) {
                // Missed: tremendous endlag.
                Anim.play(user, "yuta_elbow_whiff");
                endAt = age + 34;
            }
        }

        private void elbow(JJKConfig.Yuta cfg, LivingEntity t) {
            Motion.set(user, Vec3.ZERO);
            HitResult r = HakariCombat.hit(YutaCombat.strike(user, ID, cfg.elbowDamage, true)
                    .knockback(Knockback.set(dir.scale(0.7).add(0, 0.25, 0))).hitstun(40).fx("yuta_impact", 1.2f).build(), t);
            if (!r.connected()) {
                endAt = age + 20;
                return;
            }
            victim = t;
            hitAt = age;
            YutaCombat.setTarget(user, t);
            Fx.play(level, "elbow_hit", t.getBoundingBox().getCenter(), dir, 1f, user.getId());
            // Rika joins in unless she is busy with a move of her own.
            YutaState s = YutaState.of(user);
            RikaEntity rk = s.rika();
            if (rk != null && !s.rikaBusy(level.getGameTime())) {
                rika = rk;
                YutaCombat.busy(user, 30);
            }
            setPhase(1, 30);
        }

        private void barrage(JJKConfig.Yuta cfg) {
            int t = age - hitAt;
            if (!victim.isAlive()) {
                finish();
                return;
            }
            if (t == 6) {
                // Appears behind them, Rika in front.
                Vec3 behind = TeleportAbility.behindSpot(user, victim);
                if (behind != null) {
                    Vec3 look = victim.getBoundingBox().getCenter().subtract(behind.add(0, user.getEyeHeight(), 0));
                    float yaw = (float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90f;
                    // Height the teleport gives is never fall damage (only falling below where they left counts).
                    dev.rick.jjk.core.combat.LaunchHeight.displaced(user, user.getY());
                    user.teleportTo(level, behind.x, behind.y, behind.z, Set.of(), yaw, user.getXRot(), false);
                }
                Fx.play(level, "elbow_appear", user.position().add(0, 1, 0), dir, 1f, user.getId());
                Anim.play(user, "yuta_elbow_barrage");
                if (rika != null) Anim.playOn(rika, "rika_barrage");
            }
            if (t >= 6) {
                Statuses.apply(victim, CombatStatus.GRABBED, 3);
                Motion.set(user, Vec3.ZERO);
                Motion.set(victim, new Vec3(0, 0.02, 0));
                if (rika != null) {
                    Vec3 front = victim.position().subtract(user.position());
                    Vec3 f = new Vec3(front.x, 0, front.z);
                    f = f.lengthSqr() < 1e-4 ? dir : f.normalize();
                    rika.moveTo(victim.position().add(f.scale(1.6)).add(0, -0.3, 0), 1.8, 3);
                }
            }
            // Six blows across the barrage, 5 in all (8 with Rika).
            if (t >= 8 && t <= 23 && (t - 8) % 3 == 0) {
                flurryHits++;
                float total = rika != null ? cfg.elbowFlurryRikaDamage : cfg.elbowFlurryDamage;
                var b = (rika != null && flurryHits % 2 == 0 ? YutaCombat.rika(user, rika, ID, total / 6f, false) : YutaCombat.strike(user, ID, total / 6f, true))
                        .tag(AttackTag.UNBLOCKABLE).knockback(Knockback.HOLD).hitstun(14).noComboScaling().fx("elbow_barrage_hit", 0.8f);
                HitResult r = HakariCombat.hit(b.build(), victim);
                if (rika != null && flurryHits == 1) YutaCombat.copyFrom(user, r.target());
            }
            if (t == 27) {
                Statuses.remove(victim, CombatStatus.GRABBED);
                Anim.play(user, "yuta_elbow_final");
                Vec3 ahead = HakariCombat.flat(user);
                if (YutaCombat.finishable(victim)) {
                    YutaCombat.execute(user, victim, ID, "elbow_final");
                } else {
                    HakariCombat.hit(YutaCombat.strike(user, ID, cfg.elbowFinalDamage, true)
                            .knockback(Knockback.set(ahead.scale(2.4).add(0, 0.6, 0))).hitstun(30).status(CombatStatus.LAUNCHED, 30)
                            .noComboScaling().fx("elbow_final", 1.4f).build(), victim);
                }
                Fx.shake(level, victim.position(), 20, 0.7f, 8);
                if (rika != null) YutaCombat.free(user);
                endAt = age + 10;
            }
        }

        @Override
        public float movementMultiplier() {
            return 0f;
        }

        @Override
        public void end() {
            if (victim != null) Statuses.remove(victim, CombatStatus.GRABBED);
            if (rika != null) YutaCombat.free(user);
        }
    }
}
