package dev.rick.jjk.yuta;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.hitbox.HitShape;
import dev.rick.jjk.core.hitbox.HitboxQuery;
import dev.rick.jjk.registry.ModDamageTypes;
import dev.rick.jjk.util.Destruction;
import dev.rick.jjk.util.Motion;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * True Love Beam (JJS awakened Rika, 40s). With their combined cursed energy Yuta and Rika conjure a small pink orb;
 * then Rika takes over, growing as she places herself before it, reveals her eye and powers it into an overwhelming
 * beam that erases everything in its path (100, less the more people it hits). Unblockable, interruptible, bypasses
 * ragdoll; an explosion and a beam. Once she is in place he can move again, though she is busy until it's over. The
 * finisher atomizes them into black mist.
 *
 * <p>Used again during the wind-up (or automatically, if Rika is busy with an attack): the orb's energy is let go at
 * once in a smaller, faster beam (22.4, 15s).
 */
public final class TrueLoveBeamAbility extends Ability {
    public static final String ID = "true_love_beam";
    /** The orb is conjured, then Rika is in place (he is free from here). */
    static final int CONJURE = 22;

    public TrueLoveBeamAbility() {
        super(ID);
    }

    @Override
    public Kind kind() {
        return Kind.INSTANT;
    }

    @Override
    public float cost(AbilityCaster caster) {
        return 120f;
    }

    @Override
    public int cooldown(AbilityCaster caster) {
        return YutaCombat.cfg().beamCooldown;
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        boolean busy = YutaState.of(ctx.user()).rikaBusy(ctx.level().getGameTime());
        YutaCombat.backToYuta(ctx.caster());
        return new Instance(this, ctx, busy);
    }

    public static final class Instance extends AbilityInstance {
        private final AbilitySlot slot;
        private final int mode;
        private boolean quick;
        private int fireAt;
        private int fireEnd;
        private Vec3 orb = Vec3.ZERO;
        private Vec3 dir = Vec3.ZERO;
        @Nullable private RikaEntity rika;
        private final Map<LivingEntity, Boolean> hit = new HashMap<>();
        private float perTarget;

        Instance(Ability ability, AbilityContext ctx, boolean rikaBusy) {
            super(ability, ctx);
            slot = ctx.slot();
            mode = ctx.caster().mode();
            quick = rikaBusy;
        }

        /** Pressed again in the wind-up: the quick beam, right now. */
        public boolean quickPress() {
            if (quick || age >= fireAt) return false;
            goQuick();
            return true;
        }

        private void goQuick() {
            quick = true;
            fireAt = age + 2;
            fireEnd = fireAt + 6;
            if (rika != null) YutaCombat.free(user);
            caster.resetSlot(slot, mode);
            caster.startCooldown(slot, mode, YutaCombat.cfg().beamQuickCooldown);
            Anim.play(user, "yuta_beam_quick");
        }

        @Override
        public void start() {
            JJKConfig.Yuta cfg = YutaCombat.cfg();
            fireAt = cfg.beamWindup;
            fireEnd = fireAt + cfg.beamTicks;
            aim();
            Anim.play(user, "yuta_beam_conjure");
            setPhase(0, fireAt);
            Fx.play(level, "beam_orb", orb, dir, 1f, user.getId());
            if (quick) {
                // Rika is busy: the orb goes off as the small beam straight away.
                fireAt = 8;
                fireEnd = fireAt + 6;
                caster.resetSlot(slot, mode);
                caster.startCooldown(slot, mode, cfg.beamQuickCooldown);
                return;
            }
            rika = YutaCombat.summonRika(user);
            YutaCombat.busy(user, fireEnd + 4);
            if (rika != null) Anim.playOn(rika, "rika_beam");
        }

        private void aim() {
            dir = user.getLookAngle().normalize();
            orb = user.getEyePosition().add(dir.scale(1.8)).add(0, -0.2, 0);
        }

        @Override
        public void tick() {
            JJKConfig.Yuta cfg = YutaCombat.cfg();
            if (age < CONJURE && !quick || quick && age < fireAt) {
                aim();
                Motion.set(user, new Vec3(0, Math.min(0, user.getDeltaMovement().y), 0));
            }
            if (!quick && rika != null && age >= CONJURE && age < fireEnd) {
                // In place behind the orb, grown, her eye on the target.
                if (age == CONJURE) {
                    setPhase(1, fireAt - age);
                    Fx.play(level, "beam_rika_eye", orb, dir, 1f, rika.getId());
                }
                rika.moveTo(orb.subtract(dir.scale(1.6)).add(0, -1.6, 0), 1.6, 2);
                rika.setYRot((float) (Math.atan2(dir.z, dir.x) * 180 / Math.PI) - 90f);
            }
            if (age == fireAt) {
                double range = quick ? cfg.beamRange * 0.7 : cfg.beamRange;
                setPhase(2, fireEnd - fireAt);
                Fx.play(level, quick ? "beam_quick" : "true_love_beam", orb, dir, (float) range, user.getId());
                Fx.shake(level, orb, quick ? 24 : 64, quick ? 0.6f : 1.4f, quick ? 8 : 30);
                // Less damage the more players it catches.
                List<LivingEntity> in = targets(range, quick ? cfg.beamWidth * 0.55 : cfg.beamWidth);
                long players = in.stream().filter(e -> e instanceof Player).count();
                float dmg = quick ? cfg.beamQuickDamage : cfg.beamDamage;
                perTarget = dmg / Math.max(1, players);
                if (!quick) {
                    // It erases whatever is in its path.
                    for (double d = 2; d < range; d += 2.5) Destruction.sphere(level, orb.add(dir.scale(d)), 1.7, 60f, 40, user, null);
                }
            }
            if (age >= fireAt && age < fireEnd) {
                double range = quick ? cfg.beamRange * 0.7 : cfg.beamRange;
                for (LivingEntity t : targets(range, quick ? cfg.beamWidth * 0.55 : cfg.beamWidth)) {
                    if (hit.put(t, true) != null) continue;
                    if (YutaCombat.finishable(t) || t.getHealth() <= perTarget) {
                        YutaCombat.execute(user, t, ID, "beam_finisher");
                        continue;
                    }
                    var b = dev.rick.jjk.core.combat.Hit.builder(user, ID).type(ModDamageTypes.TECHNIQUE).damage(perTarget)
                            .tag(AttackTag.TECHNIQUE, AttackTag.UNBLOCKABLE, AttackTag.EXPLOSION, AttackTag.OTG, AttackTag.ULTIMATE)
                            .origin(orb).knockback(Knockback.set(dir.scale(1.6).add(0, 0.5, 0))).hitstun(40)
                            .status(CombatStatus.LAUNCHED, 40).noComboScaling().fx("beam_hit", quick ? 1f : 1.6f);
                    dev.rick.jjk.hakari.HakariCombat.hit(b.build(), t);
                }
            }
            if (age >= fireEnd + 6) finish();
        }

        private List<LivingEntity> targets(double range, double width) {
            return HitboxQuery.targets(user, HitShape.capsule(orb, orb.add(dir.scale(range)), width), 0.3, false);
        }

        /** Once Rika is in place he is free to move (she keeps going on her own). */
        @Override
        public boolean exclusive() {
            return quick ? age < fireEnd : age < CONJURE;
        }

        @Override
        public boolean uninterruptible() {
            return !quick && age >= CONJURE;
        }

        @Override
        public float movementMultiplier() {
            return exclusive() ? 0f : 1f;
        }

        @Override
        public void end() {
            if (rika != null) YutaCombat.free(user);
        }
    }
}
