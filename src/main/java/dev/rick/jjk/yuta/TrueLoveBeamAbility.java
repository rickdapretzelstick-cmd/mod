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
import dev.rick.jjk.registry.ModDamageTypes;
import dev.rick.jjk.util.Destruction;
import dev.rick.jjk.util.Motion;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.combat.Targeting;
import dev.rick.jjk.hakari.HakariCombat;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;

import java.util.HashSet;
import java.util.Set;

/**
 * True Love Beam (JJS awakened Rika, 40s). With their combined cursed energy Yuta and Rika conjure a small pink orb while
 * he aims; then Rika takes over, planting herself behind him, growing, her jaw opening wide over his head as she charges
 * it, the path it will take traced on the ground; and from her mouth comes an overwhelming beam that erases everything in
 * its path (100, less the more players it catches). Unblockable, bypasses ragdoll; an explosion and a beam. Once she is in
 * place he can move again, though she is busy until it's over. The finisher atomizes them into black mist.
 *
 * <p>The beam's shape ({@link TrueLoveBeamProfile}) is the hitbox: it shoots out over a few ticks, holds, collapses, and
 * only does damage while it is drawn, once to each target. Its path is fixed once Rika is in place (no tracking).
 *
 * <p>Used again during the wind-up (or automatically, if Rika is busy with an attack): the orb's energy is let go at
 * once from his hands, re-aimed, in a smaller, faster beam (22.4, 15s).
 *
 * <p>Safety: he dies, leaves the dimension, or Rika is gone before or during the beam: it stops at once, for everyone
 * (the visual is cut with {@code beam_stop}), and does no more damage. Stunned before Rika is in place, the move is
 * interrupted as usual; after that it is hers, and goes on even if he is stunned.
 */
public final class TrueLoveBeamAbility extends Ability {
    public static final String ID = "true_love_beam";

    public TrueLoveBeamAbility() {
        super(ID);
    }

    @Override
    public Kind kind() {
        return Kind.INSTANT;
    }

    @Override
    public float cost(AbilityCaster caster) {
        return YutaCombat.cfg().beamCost;
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
        private final int conjure;
        private boolean quick;
        private int fireAt;
        private int fireEnd;
        /** The orb in his hands (and the quick beam's origin). */
        private Vec3 orb = Vec3.ZERO;
        /** Where the beam comes from (Rika's mouth, or the orb for the quick one), and where it goes. */
        private Vec3 origin = Vec3.ZERO;
        private Vec3 dir = Vec3.ZERO;
        /** Where Rika stands, planted, and the way she faces. */
        private Vec3 rikaFeet = Vec3.ZERO;
        private float rikaYaw;
        private double range, radius;
        private int life, grow, collapse;
        private boolean drawn;
        @Nullable private RikaEntity rika;
        private final Set<LivingEntity> hit = new HashSet<>();
        private float perTarget;
        private double destroyedTo;

        Instance(Ability ability, AbilityContext ctx, boolean rikaBusy) {
            super(ability, ctx);
            slot = ctx.slot();
            mode = ctx.caster().mode();
            conjure = YutaCombat.cfg().beamConjureTicks;
            quick = rikaBusy;
        }

        /** The key the beam was cast from. */
        public AbilitySlot slot() {
            return slot;
        }

        /** Pressed again in the wind-up: the quick beam, right now. */
        public boolean quickPress() {
            if (quick || age >= fireAt) return false;
            goQuick(2);
            return true;
        }

        private void goQuick(int delay) {
            JJKConfig.Yuta cfg = YutaCombat.cfg();
            quick = true;
            fireAt = age + delay;
            if (rika != null) YutaCombat.free(user);
            caster.resetSlot(slot, mode);
            caster.startCooldown(slot, mode, cfg.beamQuickCooldown);
            Anim.play(user, "yuta_beam_quick");
            aimOrb();
            origin = orb;
            setShape(cfg);
            fireEnd = fireAt + life;
            setPhase(0, fireAt - age);
        }

        private void setShape(JJKConfig.Yuta cfg) {
            range = quick ? cfg.beamQuickRange : cfg.beamRange;
            radius = quick ? cfg.beamQuickRadius : cfg.beamRadius;
            life = quick ? cfg.beamQuickTicks : cfg.beamTicks;
            grow = quick ? 2 : cfg.beamGrowTicks;
            collapse = quick ? 3 : cfg.beamCollapseTicks;
            dir = aimPoint(origin, range).subtract(origin).normalize();
        }

        @Override
        public void start() {
            JJKConfig.Yuta cfg = YutaCombat.cfg();
            aimOrb();
            Anim.play(user, "yuta_beam_conjure");
            Fx.play(level, "beam_orb", orb, user.getLookAngle(), 1f, user.getId());
            if (quick) {
                // Rika is busy: the orb goes off as the small beam straight away.
                goQuick(cfg.beamQuickWindup);
                return;
            }
            fireAt = cfg.beamWindup;
            fireEnd = fireAt + cfg.beamTicks;
            setPhase(0, conjure);
            rika = YutaCombat.summonRika(user);
            YutaCombat.busy(user, fireEnd + 4);
            if (rika != null) Anim.playOn(rika, "rika_beam");
        }

        private void aimOrb() {
            Vec3 look = user.getLookAngle().normalize();
            orb = user.getEyePosition().add(look.scale(1.2)).add(0, -0.35, 0);
        }

        /** What he is aiming at: the first block or body under the crosshair, or the end of the beam's reach. */
        private Vec3 aimPoint(Vec3 from, double reach) {
            Vec3 eye = user.getEyePosition();
            Vec3 look = user.getLookAngle().normalize();
            Vec3 far = eye.add(look.scale(reach));
            var block = level.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user));
            Vec3 end = block.getType() == HitResult.Type.MISS ? far : block.getLocation();
            var ent = ProjectileUtil.getEntityHitResult(level, user, eye, end, new AABB(eye, end).inflate(1),
                    e -> e instanceof LivingEntity && e.isAlive() && e != rika && !e.isSpectator(), 0.3f);
            if (ent != null) end = ent.getEntity().getBoundingBox().getCenter();
            // Aiming at the ground right in front would send it into the floor at his feet: never closer than 8 blocks.
            if (end.distanceTo(from) < 8) end = from.add(end.subtract(from).normalize().scale(8));
            return end;
        }

        /** Rika plants herself behind him and lines her mouth up with his aim. */
        private void plantRika(JJKConfig.Yuta cfg) {
            Vec3 look = user.getLookAngle();
            Vec3 flat = new Vec3(look.x, 0, look.z);
            flat = flat.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : flat.normalize();
            rikaFeet = user.position().subtract(flat.scale(TrueLoveBeamProfile.BEHIND));
            rikaYaw = (float) (Mth.atan2(flat.z, flat.x) * Mth.RAD_TO_DEG) - 90f;
            origin = rikaFeet.add(0, TrueLoveBeamProfile.MOUTH_UP, 0).add(flat.scale(TrueLoveBeamProfile.MOUTH_FORWARD));
            setShape(cfg);
        }

        @Override
        public void tick() {
            JJKConfig.Yuta cfg = YutaCombat.cfg();
            if (!user.isAlive() || user.level() != level) {
                stop();
                return;
            }
            boolean needsRika = !quick && age >= conjure;
            if (!quick && rika != null && (!rika.isAlive() || rika.isRemoved())) rika = null;
            if (needsRika && rika == null) {
                // She is gone (dismissed, or the Awakening ran out): nothing left to fire it.
                Fx.play(level, "beam_fizzle", origin, dir, 1f, user.getId());
                stop();
                return;
            }
            if (!quick && age < conjure) {
                // Conjuring: he holds still and aims; she rises behind him.
                aimOrb();
                Motion.set(user, new Vec3(0, Math.min(0, user.getDeltaMovement().y), 0));
                if (rika != null) {
                    Vec3 look = user.getLookAngle();
                    Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
                    rika.moveTo(user.position().subtract(flat.scale(TrueLoveBeamProfile.BEHIND)), 1.2, 2);
                }
            }
            if (!quick && age == conjure) {
                // In place: the path is locked and traced, and her charge begins.
                plantRika(cfg);
                setPhase(1, fireAt - age);
                Fx.play(level, "beam_rika_eye", origin, dir.scale(range), fireAt - age, rika.getId());
            }
            if (needsRika && age < fireEnd) {
                rika.moveTo(rikaFeet, 1.6, 2);
                rika.setYRot(rikaYaw);
            }
            if (quick && age < fireAt) {
                aimOrb();
                origin = orb;
                dir = aimPoint(origin, range).subtract(origin).normalize();
            }
            if (age == fireAt) fire(cfg);
            if (drawn && age >= fireAt && age < fireEnd) beam(cfg, age - fireAt);
            if (age >= fireEnd) {
                drawn = false;
                if (age >= fireEnd + 6) finish();
            }
        }

        private void fire(JJKConfig.Yuta cfg) {
            drawn = true;
            setPhase(2, fireEnd - fireAt);
            // The client draws it from the server's numbers (so the drawn beam is the hitbox, whatever its own config says):
            // first its radius, grow-in and collapse, then origin, direction times length and life in ticks.
            Fx.play(level, "beam_shape", origin, new Vec3(radius, grow, collapse), life, user.getId());
            Fx.play(level, quick ? "beam_quick" : "true_love_beam", origin, dir.scale(range), life, user.getId());
            Fx.shake(level, origin, quick ? 24 : 64, quick ? 0.6f : 1.4f, quick ? 8 : 30);
            // Less damage the more players it catches (counted along its full length).
            long players = level.getEntitiesOfClass(Player.class, new AABB(origin, origin.add(dir.scale(range))).inflate(radius),
                    p -> Targeting.canTarget(user, p) && inside(p, range, 1f)).size();
            perTarget = (quick ? cfg.beamQuickDamage : cfg.beamDamage) / Math.max(1, players);
        }

        /** One tick of the beam: only what it covers right now, each target once. */
        private void beam(JJKConfig.Yuta cfg, int t) {
            double front = TrueLoveBeamProfile.front(range, grow, t);
            float scale = TrueLoveBeamProfile.scale(t, life, collapse);
            if (!quick && front > destroyedTo) {
                // It erases what's in its path as its front passes (restored later like all battle damage).
                for (double d = Math.max(2, destroyedTo); d < front; d += 2.5) {
                    Destruction.sphere(level, origin.add(dir.scale(d)), Math.min(2.2, TrueLoveBeamProfile.radius(d, range, radius)), 60f, 40, user, null);
                }
                destroyedTo = front;
            }
            if (scale <= 0) return;
            AABB box = new AABB(origin, origin.add(dir.scale(front))).inflate(radius + 1);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> Targeting.canTarget(user, e))) {
                if (hit.contains(e) || !inside(e, front, scale)) continue;
                hit.add(e);
                if (YutaCombat.finishable(e) || e.getHealth() <= perTarget) {
                    YutaCombat.execute(user, e, ID, "beam_finisher");
                    continue;
                }
                var b = Hit.builder(user, ID).type(ModDamageTypes.TECHNIQUE).damage(perTarget)
                        .tag(AttackTag.TECHNIQUE, AttackTag.UNBLOCKABLE, AttackTag.EXPLOSION, AttackTag.OTG, AttackTag.ULTIMATE)
                        .origin(origin).knockback(Knockback.set(dir.scale(quick ? 1.0 : 1.6).add(0, 0.5, 0))).hitstun(40)
                        .status(CombatStatus.LAUNCHED, 40).noComboScaling().fx("beam_hit", quick ? 1f : 1.6f);
                HakariCombat.hit(b.build(), e);
            }
        }

        /** Inside the beam's drawn shape right now: its feet, middle or head. */
        private boolean inside(LivingEntity e, double front, float scale) {
            double body = e.getBbWidth() / 2 + 0.15;
            for (Vec3 p : new Vec3[] {e.position().add(0, 0.2, 0), e.getBoundingBox().getCenter(), e.getEyePosition()}) {
                if (TrueLoveBeamProfile.contains(origin, dir, front, radius, scale, p, body)) return true;
            }
            return false;
        }

        /** Cut short: no more damage, and every client collapses the beam (and drops the telegraph) now. */
        private void stop() {
            if (drawn || age >= conjure) Fx.play(level, "beam_stop", origin, dir, 30f, user.getId());
            drawn = false;
            finish();
        }

        /** Once Rika is in place he is free to move (she keeps going on her own). */
        @Override
        public boolean exclusive() {
            return quick ? age < fireEnd : age < conjure;
        }

        @Override
        public boolean uninterruptible() {
            return !quick && age >= conjure;
        }

        @Override
        public float movementMultiplier() {
            return exclusive() ? 0f : 1f;
        }

        @Override
        public void interrupt(String reason) {
            if (drawn || !quick && age >= conjure) Fx.play(level, "beam_stop", origin, dir, 30f, user.getId());
            drawn = false;
            super.interrupt(reason);
        }

        @Override
        public void end() {
            drawn = false;
            if (rika != null) YutaCombat.free(user);
        }
    }
}
