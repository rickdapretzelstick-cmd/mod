package dev.rick.jjk.ryu;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.clash.BeamClashManager;
import dev.rick.jjk.core.clash.BeamClashSession;
import dev.rick.jjk.core.clash.ClashBeam;
import dev.rick.jjk.core.clash.ClashCommon;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.combat.Targeting;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.hakari.HakariCombat;
import dev.rick.jjk.registry.ModDamageTypes;
import dev.rick.jjk.util.Destruction;
import dev.rick.jjk.util.Motion;
import dev.rick.jjk.yuta.TrueLoveBeamProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every Last Drop. (JJS True Cannon Awakening). Starving for a satisfying battle, he points his finger forward and charges
 * his strongest blast yet with all of his cursed energy — "Let's use every last drop!" — then lets the hyper-concentrated
 * beam loose: a narrow, blinding lance of cursed energy, unblockable, uninterruptible, an explosion and a beam. 104,
 * less the more it catches and the farther they are. 100% Overheat. Fired with his Overheat between 80% and 100% he
 * enters his Awakening as the blast dies down (90 seconds, heals 25); otherwise the meter is spent for nothing.
 *
 * <p>A {@link ClashBeam}: it meets True Love Beam in a beam clash; and pressed as the answer to one coming at him, it
 * fires almost at once, aimed straight back.
 */
public final class EveryLastDropAbility extends Ability {
    public static final String ID = "every_last_drop";
    public static final int COUNTER_WINDUP = 10;

    public EveryLastDropAbility() {
        super(ID);
    }

    @Override
    public Kind kind() {
        return Kind.INSTANT;
    }

    @Override
    public float awakeningCost(AbilityCaster caster) {
        return caster.maxAwakening();
    }

    @Override
    public @Nullable String checkActivation(AbilityContext ctx) {
        if (ctx.caster().isAwakened()) return "awakened";
        return RyuCombat.canDischarge(ctx.user()) ? null : "overheated";
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        return new Instance(this, ctx, null);
    }

    public static Instance counter(Ability ability, AbilityContext ctx, Vec3 at) {
        return new Instance(ability, ctx, at);
    }

    public static final class Instance extends AbilityInstance implements ClashBeam {
        @Nullable private final Vec3 counterAt;
        private final float heatBefore;
        private int lockAt, fireAt, fireEnd, hold, grow = 3, collapse = 8;
        private Vec3 origin = Vec3.ZERO, dir = Vec3.ZERO;
        private double range, radius;
        private boolean drawn, locked, awakenDone;
        private final Set<LivingEntity> hit = new HashSet<>();
        private int players = 1;
        private List<BlockPos> carve = List.of();
        private List<Double> carveAt = List.of();
        private int carved;
        @Nullable private BeamClashSession clash;
        /** Its strength when a clash took hold of it (1 fresh; less the longer it had poured out first). */
        private float strength = 1f;
        private boolean measured;

        Instance(Ability ability, AbilityContext ctx, @Nullable Vec3 counterAt) {
            super(ability, ctx);
            this.counterAt = counterAt;
            this.heatBefore = RyuCombat.heat(ctx.user());
        }

        @Override
        public void start() {
            JJKConfig.Ryu cfg = RyuCombat.cfg();
            range = cfg.eldRange;
            radius = cfg.eldRadius;
            hold = cfg.eldTicks;
            if (counterAt != null) {
                fireAt = COUNTER_WINDUP;
                lockAt = 0;
                dir = counterAt.subtract(RyuCombat.fingertip(user, counterAt.subtract(user.getEyePosition()))).normalize();
                ClashCommon.face(user, counterAt);
                locked = true;
                Anim.play(user, "ryu_eld_point");
                Fx.play(level, "eld_counter", user.getEyePosition(), dir, 1f, user.getId());
            } else {
                fireAt = cfg.eldCharge;
                lockAt = Math.max(0, fireAt - 14);
                dir = user.getLookAngle().normalize();
                Anim.play(user, "ryu_eld_charge");
                Fx.play(level, "eld_voice", user.getEyePosition(), dir, 1f, user.getId());
            }
            origin = RyuCombat.fingertip(user, dir);
            fireEnd = fireAt + hold + collapse;
            setPhase(0, fireAt);
            Fx.play(level, "eld_charge", origin, dir, fireAt, user.getId());
            // The charge has begun (the counter skips it: it fires back already answering): its window opens now.
            if (counterAt == null) BeamClashManager.threaten(this, level.getGameTime() + fireAt);
        }

        @Override
        public void tick() {
            if (!user.isAlive() || user.level() != level) {
                stop();
                return;
            }
            Motion.set(user, new Vec3(0, Math.min(0, user.getDeltaMovement().y), 0));
            if (age < fireAt) {
                if (!locked) dir = user.getLookAngle().normalize();
                origin = RyuCombat.fingertip(user, dir);
                if (!locked && age >= lockAt) {
                    // Committed: it goes where he points now (the window has been open since the charge began).
                    locked = true;
                    Anim.play(user, "ryu_eld_point");
                }
                if ((fireAt - age) % 4 == 0) Fx.play(level, "eld_gather", origin, dir, 1f - (fireAt - age) / (float) Math.max(1, fireAt), user.getId());
            }
            if (age == fireAt) fire();
            if (clash != null) {
                hold = Math.max(hold, age - fireAt + 2);
                fireEnd = fireAt + hold + collapse;
            }
            if (drawn && age >= fireAt && age < fireEnd) beam(age - fireAt);
            if (age >= fireEnd) {
                drawn = false;
                afterBlast();
                if (age >= fireEnd + 4) finish();
            }
        }

        private void fire() {
            drawn = true;
            setPhase(1, hold);
            Anim.play(user, "ryu_eld_fire");
            Fx.play(level, "eld_shape", origin, new Vec3(radius, grow, collapse), hold, user.getId());
            Fx.play(level, "eld_fire", origin, dir.scale(range), hold, user.getId());
            Fx.shake(level, origin, 80, 1.5f, 30);
            players = Math.max(1, level.getEntitiesOfClass(Player.class, new AABB(origin, origin.add(dir.scale(range))).inflate(radius + 1),
                    p -> Targeting.canTarget(user, p) && inside(p, range, 1f)).size());
            planCarve();
            BeamClashManager.fired(this);
        }

        /** The same five-by-five square tunnel True Love Beam tears, torn at its edge. */
        private void planCarve() {
            List<BlockPos> cells = new ArrayList<>();
            List<Double> at = new ArrayList<>();
            TrueLoveBeamProfile.carve(origin, dir, range, radius, JJKConfig.get().yuta.beamCarveEdge, cells, at);
            carve = cells;
            carveAt = at;
            carved = 0;
        }

        private double reach(int t) {
            double front = TrueLoveBeamProfile.front(range, grow, t);
            if (clash != null) front = Math.min(front, clash.reach(this));
            return front;
        }

        private void beam(int t) {
            double front = reach(t);
            boolean live = t < hold;
            if (live) {
                int budget = 70;
                while (carved < carve.size() && budget > 0 && !Destruction.budgetExhausted(level)) {
                    if (carveAt.get(carved) > front) break;
                    BlockPos p = carve.get(carved++);
                    if (level.getBlockState(p).isAir()) continue;
                    if (Destruction.destroy(level, p, 60f, user, ID)) budget--;
                }
            }
            if (t % 4 == 0) {
                Vec3 end = origin.add(dir.scale(front));
                var clip = level.clip(new ClipContext(origin.add(dir.scale(2)), end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user));
                Vec3 strike = clip.getType() == HitResult.Type.MISS ? end : clip.getLocation();
                Fx.play(level, "eld_pulse", strike, dir, t, user.getId());
            }
            if (!live) return;
            JJKConfig.Ryu cfg = RyuCombat.cfg();
            AABB box = new AABB(origin, origin.add(dir.scale(front))).inflate(radius + 1.5);
            float scale = TrueLoveBeamProfile.scale(t, hold, collapse);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> Targeting.canTarget(user, e))) {
                if (hit.contains(e) || !inside(e, front, scale)) continue;
                // Held in a clash, the collision stands between it and the other contestant.
                if (clash != null && clash.sideOf(e) != null) continue;
                hit.add(e);
                double along = e.getBoundingBox().getCenter().subtract(origin).dot(dir);
                float dmg = Mth.lerp((float) Mth.clamp(along / range, 0, 1), cfg.eldDamage, cfg.eldMinDamage) / players;
                if (RyuCombat.finishable(e) || e.getHealth() <= dmg) {
                    RyuCombat.execute(user, e, ID, "eld_finisher");
                    continue;
                }
                Hit h = Hit.builder(user, ID).type(ModDamageTypes.TECHNIQUE).damage(dmg)
                        .tag(AttackTag.TECHNIQUE, AttackTag.UNBLOCKABLE, AttackTag.EXPLOSION, AttackTag.OTG, AttackTag.ULTIMATE).origin(origin)
                        .knockback(Knockback.set(dir.scale(1.7).add(0, 0.55, 0))).hitstun(40).status(CombatStatus.LAUNCHED, 40)
                        .noComboScaling().fx("eld_hit", 1.6f).build();
                HakariCombat.hit(h, e);
            }
        }

        /** Inside the square, exactly as it is drawn (the same profile as True Love Beam). */
        private boolean inside(LivingEntity e, double front, float scale) {
            double body = e.getBbWidth() / 2 + 0.15;
            for (Vec3 p : new Vec3[] {e.position().add(0, 0.2, 0), e.getBoundingBox().getCenter(), e.getEyePosition()}) {
                if (TrueLoveBeamProfile.contains(origin, dir, front, radius, scale, p, body)) return true;
            }
            return false;
        }

        /** The blast dies down: 100% Overheat, and (fired hot enough) his Awakening. */
        private void afterBlast() {
            if (awakenDone) return;
            awakenDone = true;
            JJKConfig.Ryu cfg = RyuCombat.cfg();
            RyuCombat.setHeat(user, 100f);
            if (heatBefore >= cfg.awakenFrom && heatBefore < 100f && !caster.isAwakened() && user.isAlive()) {
                caster.enterAwakening();
                user.heal(cfg.eldHeal);
                Fx.play(level, "ryu_awaken", user.position().add(0, 1.2, 0), Vec3.ZERO, 1f, user.getId());
                Fx.shake(level, user.position(), 40, 0.8f, 16);
            }
        }

        private void stop() {
            if (drawn || age >= fireAt) Fx.play(level, "eld_stop", origin, dir, 1f, user.getId());
            drawn = false;
            BeamClashManager.beamGone(this);
            finish();
        }

        // --- ClashBeam ---

        @Override
        public LivingEntity beamOwner() {
            return user;
        }

        @Override
        public ServerLevel beamLevel() {
            return level;
        }

        @Override
        public Vec3 beamOrigin() {
            return origin;
        }

        @Override
        public Vec3 beamDir() {
            return dir;
        }

        @Override
        public double beamRange() {
            return range;
        }

        @Override
        public boolean beamLive() {
            return drawn && clash == null && age >= fireAt && age - fireAt < hold && !isFinished();
        }

        @Override
        public String beamKind() {
            return "eld";
        }

        @Override
        public float beamStrength() {
            return strength;
        }

        @Override
        public void enterClash(BeamClashSession session, Vec3 newOrigin, Vec3 newDir) {
            if (!measured) {
                measured = true;
                int poured = Math.max(0, age - fireAt);
                strength = (float) Mth.clamp(1.0 - poured / (double) Math.max(1, RyuCombat.cfg().eldTicks), 0, 1);
            }
            clash = session;
            if (newDir.lengthSqr() > 1e-6) {
                dir = newDir.normalize();
                Fx.play(level, "eld_reaim", origin, dir.scale(range), 0, user.getId());
            }
            Fx.play(level, "eld_hold", origin, dir, 1f, user.getId());
        }

        @Override
        public void leaveClash(boolean won, int extraTicks) {
            clash = null;
            if (!won) {
                Fx.play(level, "eld_stop", origin, dir, 1f, user.getId());
                drawn = false;
                fireEnd = Math.min(fireEnd, age + 1);
                return;
            }
            hold = age - fireAt + extraTicks;
            fireEnd = fireAt + hold + collapse;
            hit.clear();
            Fx.play(level, "eld_release", origin, dir, extraTicks, user.getId());
        }

        @Override
        public boolean uninterruptible() {
            return true;
        }

        @Override
        public float movementMultiplier() {
            return 0f;
        }

        @Override
        public void interrupt(String reason) {
            if (drawn) Fx.play(level, "eld_stop", origin, dir, 1f, user.getId());
            drawn = false;
            BeamClashManager.beamGone(this);
            super.interrupt(reason);
        }

        @Override
        public void end() {
            drawn = false;
            BeamClashManager.beamGone(this);
            if (!awakenDone && age >= fireAt) afterBlast();
        }
    }
}
