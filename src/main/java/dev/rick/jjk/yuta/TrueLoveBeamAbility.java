package dev.rick.jjk.yuta;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.clash.BeamClashManager;
import dev.rick.jjk.core.clash.BeamClashSession;
import dev.rick.jjk.core.clash.ClashBeam;
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
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * True Love Beam (JJS awakened Rika, 40s). With their combined cursed energy Yuta and Rika conjure the charge while he
 * aims; then Rika takes over, planting herself behind him, her jaw opening wide over his head as the energy floods into
 * her mouth, the path it will take traced on the ground; and from her mouth erupts an overwhelming torrent: a SQUARE
 * five-block-wide, five-block-tall wall of cursed energy ({@link TrueLoveBeamProfile}) that holds at full size for
 * exactly five seconds once it appears, boring through everything in its path. Unblockable, bypasses ragdoll; an
 * explosion and a beam. Once she is in place he can move again, though she is busy until it's over. The finisher
 * atomizes them into black mist.
 *
 * <p>The drawn torrent is the hitbox. Whoever stays in it takes damage on contact and again every half second (less the
 * more players it catches), and is carried along it rather than flung clear. It carves a five-by-five path through the
 * terrain as it bores in, ragged at its edges (restored later like all battle damage).
 *
 * <p>Used again during the wind-up (or automatically, if Rika is busy with an attack): the charge is let go at once from
 * his hands, re-aimed, in a smaller, faster beam (22.4, 15s).
 *
 * <p>It is a {@link ClashBeam}: met by Ryu's Every Last Drop it becomes a beam clash, and fired as the counter to his
 * (Ultimate pressed in the reaction window) it skips the conjuring: Rika answers at once.
 *
 * <p>Safety: he dies, leaves the dimension, or Rika is gone before or during the beam: it stops at once, for everyone
 * (the visual is cut with {@code beam_stop}), and does no more damage.
 */
public final class TrueLoveBeamAbility extends Ability {
    public static final String ID = "true_love_beam";
    /** Ticks a countering Rika takes from the press to the release. */
    public static final int COUNTER_WINDUP = 10;

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
        return new Instance(this, ctx, busy, null);
    }

    /**
     * Rika answers an incoming beam: the full True Love Beam, aimed at {@code at} (the other beam's source), fired after
     * {@link #COUNTER_WINDUP} ticks. The caller has already spent what it costs.
     */
    public static Instance counter(Ability ability, AbilityContext ctx, Vec3 at) {
        return new Instance(ability, ctx, false, at);
    }

    /** Where Rika's mouth will be, planted behind {@code user} facing {@code at}: the counter's source. */
    public static Vec3 mouthFor(LivingEntity user, Vec3 at) {
        Vec3 toward = at.subtract(user.position());
        Vec3 flat = new Vec3(toward.x, 0, toward.z);
        flat = flat.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : flat.normalize();
        return user.position().subtract(flat.scale(TrueLoveBeamProfile.BEHIND)).add(0, TrueLoveBeamProfile.MOUTH_UP, 0)
                .add(flat.scale(TrueLoveBeamProfile.MOUTH_FORWARD));
    }

    public static final class Instance extends AbilityInstance implements ClashBeam {
        private final AbilitySlot slot;
        private final int mode;
        private final int conjure;
        private boolean quick;
        /** The counter: where to aim (null for an ordinary cast). */
        @Nullable private final Vec3 counterAt;
        private int fireAt;
        private int fireEnd;
        /** Ticks the torrent holds at full size once fired (grows longer while a clash holds it, or when it wins one). */
        private int hold;
        private Vec3 orb = Vec3.ZERO;
        private Vec3 origin = Vec3.ZERO;
        private Vec3 dir = Vec3.ZERO;
        private Vec3 rikaFeet = Vec3.ZERO;
        private float rikaYaw;
        private double range, half;
        private int grow, collapse;
        private boolean drawn;
        @Nullable private RikaEntity rika;
        /** When each body caught in it is due its next hit (the first comes on contact). */
        private final Map<LivingEntity, Integer> nextHit = new HashMap<>();
        private float damageScale = 1f;
        /** Where it is striking right now (the first thing in its path, or its end), sent to every client. */
        private Vec3 strike = Vec3.ZERO;
        /** The path it carves, nearest first: each block with how far along the beam it lies. */
        private List<Cell> carve = List.of();
        private int carved;
        @Nullable private BeamClashSession clash;

        private record Cell(BlockPos pos, double along) {}

        Instance(Ability ability, AbilityContext ctx, boolean rikaBusy, @Nullable Vec3 counterAt) {
            super(ability, ctx);
            slot = ctx.slot();
            mode = ctx.caster().mode();
            this.counterAt = counterAt;
            conjure = counterAt != null ? 0 : YutaCombat.cfg().beamConjureTicks;
            quick = rikaBusy && counterAt == null;
        }

        public AbilitySlot slot() {
            return slot;
        }

        /** Pressed again in the wind-up: the quick beam, right now. */
        public boolean quickPress() {
            if (quick || counterAt != null || age >= fireAt) return false;
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
            setShape(cfg, aimPoint(origin, cfg.beamQuickRange));
            fireEnd = fireAt + hold + collapse;
            setPhase(0, fireAt - age);
        }

        private void setShape(JJKConfig.Yuta cfg, Vec3 aim) {
            range = quick ? cfg.beamQuickRange : cfg.beamRange;
            half = quick ? cfg.beamQuickRadius : cfg.beamRadius;
            hold = quick ? cfg.beamQuickTicks : cfg.beamTicks;
            grow = quick ? 2 : cfg.beamGrowTicks;
            collapse = quick ? 3 : cfg.beamCollapseTicks;
            dir = aim.subtract(origin).normalize();
        }

        @Override
        public void start() {
            JJKConfig.Yuta cfg = YutaCombat.cfg();
            aimOrb();
            if (counterAt != null) {
                // Rika answers at once: no conjuring, she rears up behind him already facing the incoming blast.
                Anim.play(user, "yuta_beam_conjure");
                rika = YutaCombat.summonRika(user);
                if (rika == null) {
                    finish();
                    return;
                }
                Anim.playOn(rika, "rika_beam");
                fireAt = COUNTER_WINDUP;
                plantRika(cfg, counterAt);
                fireEnd = fireAt + hold + collapse;
                YutaCombat.busy(user, fireEnd + 4);
                setPhase(1, fireAt);
                Fx.play(level, "beam_rika_eye", origin, dir.scale(range), fireAt, rika.getId());
                Fx.play(level, "beam_counter", origin, dir, 1f, user.getId());
                return;
            }
            Anim.play(user, "yuta_beam_conjure");
            Fx.play(level, "beam_orb", orb, user.getLookAngle(), 1f, user.getId());
            if (quick) {
                goQuick(cfg.beamQuickWindup);
                return;
            }
            fireAt = cfg.beamWindup;
            hold = cfg.beamTicks;
            collapse = cfg.beamCollapseTicks;
            fireEnd = fireAt + hold + collapse;
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
            if (ent != null) return ent.getEntity().getBoundingBox().getCenter();
            if (block.getType() == HitResult.Type.BLOCK && block.getDirection() == net.minecraft.core.Direction.UP) {
                // Aimed at the floor: it would plunge into the ground a few blocks out (it fires from high up, at Rika's
                // mouth). Instead it sweeps out level, its body just clear of the ground, the way he faces.
                Vec3 flat = new Vec3(look.x, 0, look.z);
                if (flat.lengthSqr() > 1e-4) {
                    double out = Math.max(20, Math.min(reach, end.distanceTo(eye) * 2.5));
                    end = new Vec3(eye.x, end.y + (quick ? 1.0 : 2.6), eye.z).add(flat.normalize().scale(out));
                }
            }
            if (end.distanceTo(from) < 8) end = from.add(end.subtract(from).normalize().scale(8));
            return end;
        }

        /** Rika plants herself behind him and lines her mouth up with {@code aim} (his aim, or the incoming beam). */
        private void plantRika(JJKConfig.Yuta cfg, @Nullable Vec3 aim) {
            Vec3 toward = aim != null ? aim.subtract(user.position()) : user.getLookAngle();
            Vec3 flat = new Vec3(toward.x, 0, toward.z);
            flat = flat.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : flat.normalize();
            rikaFeet = user.position().subtract(flat.scale(TrueLoveBeamProfile.BEHIND));
            rikaYaw = (float) (Mth.atan2(flat.z, flat.x) * Mth.RAD_TO_DEG) - 90f;
            origin = rikaFeet.add(0, TrueLoveBeamProfile.MOUTH_UP, 0).add(flat.scale(TrueLoveBeamProfile.MOUTH_FORWARD));
            if (aim != null) {
                // Facing the incoming blast: he turns with her.
                dev.rick.jjk.core.clash.ClashCommon.face(user, aim);
            }
            setShape(cfg, aim != null ? aim : aimPoint(origin, cfg.beamRange));
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
                Fx.play(level, "beam_fizzle", origin, dir, 1f, user.getId());
                stop();
                return;
            }
            if (!quick && counterAt == null && age < conjure) {
                // Conjuring: he holds still and aims; she rises behind him, energy pouring in round them both.
                aimOrb();
                Motion.set(user, new Vec3(0, Math.min(0, user.getDeltaMovement().y), 0));
                if (rika != null) {
                    Vec3 look = user.getLookAngle();
                    Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
                    rika.moveTo(user.position().subtract(flat.scale(TrueLoveBeamProfile.BEHIND)), 1.2, 2);
                }
                if (age % 6 == 0) Fx.play(level, "beam_gather", orb, user.getLookAngle(), age / (float) conjure, user.getId());
            }
            if (!quick && counterAt == null && age == conjure) {
                plantRika(cfg, null);
                setPhase(1, fireAt - age);
                Fx.play(level, "beam_rika_eye", origin, dir.scale(range), fireAt - age, rika.getId());
                // Committed: anyone who could answer it gets their moment now.
                BeamClashManager.threaten(this, level.getGameTime() + (fireAt - age));
            }
            if (needsRika && age < fireEnd) {
                rika.moveTo(rikaFeet, counterAt != null ? 3.0 : 1.6, 2);
                rika.setYRot(rikaYaw);
            }
            if (!quick && age >= conjure && age < fireAt && (fireAt - age) % 5 == 0) {
                // The charge compressing at her mouth, brighter and tighter the closer to the release.
                Fx.play(level, "beam_compress", origin, dir, 1f - (fireAt - age) / (float) Math.max(1, fireAt - conjure), rika.getId());
            }
            if (quick && age < fireAt) {
                aimOrb();
                origin = orb;
                dir = aimPoint(origin, range).subtract(origin).normalize();
            }
            if (age == fireAt) fire(cfg);
            if (clash != null) {
                // Held by a clash: it pours on for as long as the clash lasts.
                hold = Math.max(hold, age - fireAt + 2);
                fireEnd = fireAt + hold + collapse;
            }
            if (drawn && age >= fireAt && age < fireEnd) beam(cfg, age - fireAt);
            if (age >= fireEnd) {
                drawn = false;
                if (age >= fireEnd + 6) finish();
            }
        }

        private void fire(JJKConfig.Yuta cfg) {
            drawn = true;
            setPhase(2, fireEnd - fireAt);
            // The client draws it from the server's numbers, so the drawn torrent is the hitbox whatever its own config
            // says: half-width, grow-in and collapse, then origin, direction times length and how long it holds.
            Fx.play(level, "beam_shape", origin, new Vec3(half, grow, collapse), hold, user.getId());
            Fx.play(level, quick ? "beam_quick" : "true_love_beam", origin, dir.scale(range), hold, user.getId());
            Fx.shake(level, origin, quick ? 24 : 80, quick ? 0.6f : 1.6f, quick ? 8 : 34);
            long players = level.getEntitiesOfClass(Player.class, new AABB(origin, origin.add(dir.scale(range))).inflate(half),
                    p -> Targeting.canTarget(user, p) && inside(p, range, 1f)).size();
            damageScale = 1f / Math.max(1, players);
            strike = clipStrike(range);
            if (!quick) {
                carve = planCarve(cfg);
                carved = 0;
                BeamClashManager.fired(this);
            }
        }

        /** The first solid thing along it (or its end, or the clash's collision point). */
        private Vec3 clipStrike(double reach) {
            Vec3 end = origin.add(dir.scale(reach));
            var clip = level.clip(new ClipContext(origin.add(dir.scale(2)), end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user));
            return clip.getType() == HitResult.Type.MISS ? end : clip.getLocation();
        }

        /**
         * The blocks it will carve, nearest first: the whole five-by-five square, and a ragged edge round it (each block
         * in that edge band goes or stays by a fixed noise, so the tunnel is torn, not cut).
         */
        private List<Cell> planCarve(JJKConfig.Yuta cfg) {
            Vec3[] f = TrueLoveBeamProfile.frame(dir);
            double edge = cfg.beamCarveEdge;
            Map<BlockPos, Double> cells = new LinkedHashMap<>();
            for (double s = 1.5; s <= range; s += 0.6) {
                double h = TrueLoveBeamProfile.half(s, range, half);
                if (h <= 0) continue;
                Vec3 c = origin.add(dir.scale(s));
                double reach = h + edge;
                for (double a = -reach; a <= reach; a += 0.6) {
                    for (double b = -reach; b <= reach; b += 0.6) {
                        double out = Math.max(Math.abs(a), Math.abs(b)) - h;
                        BlockPos bp = BlockPos.containing(c.add(f[0].scale(a)).add(f[1].scale(b)));
                        if (out > 0 && noise(bp) * edge < out) continue;
                        cells.putIfAbsent(bp, s);
                    }
                }
            }
            List<Cell> list = new ArrayList<>(cells.size());
            cells.forEach((p, s) -> list.add(new Cell(p, s)));
            list.sort((x, y) -> Double.compare(x.along, y.along));
            return list;
        }

        private static double noise(BlockPos p) {
            long h = p.asLong() * 0x9E3779B97F4A7C15L;
            h ^= h >>> 29;
            h *= 0xBF58476D1CE4E5B9L;
            h ^= h >>> 32;
            return (h & 0xFFFF) / 65535.0;
        }

        /** How far it gets right now: its front, cut off at a clash's collision point. */
        private double reach(int t) {
            double front = TrueLoveBeamProfile.front(range, grow, t);
            if (clash != null) front = Math.min(front, clash.reach(this));
            return front;
        }

        /** One tick of the beam. */
        private void beam(JJKConfig.Yuta cfg, int t) {
            double front = reach(t);
            float scale = TrueLoveBeamProfile.scale(t, hold, collapse);
            boolean live = TrueLoveBeamProfile.live(t, hold);
            if (!quick && live) {
                // Boring in: the carve list nearest first, as far as the front (or the clash) has got, a slice each tick.
                int budget = 140;
                while (carved < carve.size() && budget > 0 && !Destruction.budgetExhausted(level)) {
                    Cell c = carve.get(carved);
                    if (c.along > front) break;
                    carved++;
                    if (level.getBlockState(c.pos).isAir()) continue;
                    if (Destruction.destroy(level, c.pos, cfg.beamMaxHardness, user, ID)) budget--;
                }
            }
            if (scale <= 0) return;
            if (t % 4 == 0) {
                // Where it is striking now, so every client draws the impact there; every other one is a surge (a bigger
                // wave of energy running down it and crashing into the impact).
                strike = clipStrike(front);
                Fx.play(level, quick ? "beam_quick_pulse" : "beam_pulse", strike, dir, t, user.getId());
            }
            if (!live) return;
            AABB box = new AABB(origin, origin.add(dir.scale(front))).inflate(half + 1.5);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> Targeting.canTarget(user, e) && (Object) e != rika)) {
                if (!inside(e, front, scale)) continue;
                // Held in a clash, the collision stands between it and the other contestant.
                if (clash != null && clash.sideOf(e) != null) continue;
                // Carried along it, not flung clear: it bores them along with it.
                if (!quick) Motion.add(e, dir.scale(cfg.beamPush * 0.35));
                Integer due = nextHit.get(e);
                if (due != null && age < due) continue;
                boolean first = due == null;
                nextHit.put(e, age + (quick ? 999 : cfg.beamDamageInterval));
                float dmg = (quick ? cfg.beamQuickDamage : first ? cfg.beamDamage : cfg.beamTickDamage) * damageScale;
                if (YutaCombat.finishable(e) || e.getHealth() <= dmg) {
                    YutaCombat.execute(user, e, ID, "beam_finisher");
                    continue;
                }
                var b = Hit.builder(user, ID).type(ModDamageTypes.TECHNIQUE).damage(dmg)
                        .tag(AttackTag.TECHNIQUE, AttackTag.UNBLOCKABLE, AttackTag.EXPLOSION, AttackTag.OTG, AttackTag.ULTIMATE)
                        .origin(origin).knockback(Knockback.set(dir.scale(quick ? 1.0 : cfg.beamPush * 2.2).add(0, quick ? 0.5 : 0.18, 0)))
                        .hitstun(quick ? 40 : cfg.beamDamageInterval + 6)
                        .status(CombatStatus.LAUNCHED, quick ? 40 : cfg.beamDamageInterval + 6).noComboScaling().fx("beam_hit", quick ? 1f : 1.8f);
                HakariCombat.hit(b.build(), e);
            }
        }

        /** Inside the beam's drawn shape right now: its feet, middle or head. */
        private boolean inside(LivingEntity e, double front, float scale) {
            double body = e.getBbWidth() / 2 + 0.15;
            for (Vec3 p : new Vec3[] {e.position().add(0, 0.2, 0), e.getBoundingBox().getCenter(), e.getEyePosition()}) {
                if (TrueLoveBeamProfile.contains(origin, dir, front, half, scale, p, body)) return true;
            }
            return false;
        }

        private void stop() {
            if (drawn || age >= conjure) Fx.play(level, "beam_stop", origin, dir, 30f, user.getId());
            drawn = false;
            if (clash != null) BeamClashManager.beamGone(this);
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
            return drawn && !quick && clash == null && age >= fireAt && TrueLoveBeamProfile.live(age - fireAt, hold) && !isFinished();
        }

        /** Charging as a counter (aimed, about to fire): a clash can wait for it. */
        public boolean countering() {
            return counterAt != null && age < fireAt && !isFinished();
        }

        @Override
        public String beamKind() {
            return "tlb";
        }

        @Override
        public void enterClash(BeamClashSession session, Vec3 newOrigin, Vec3 newDir) {
            clash = session;
            if (newDir.lengthSqr() > 1e-6 && newDir.normalize().dot(dir) < 0.9999) {
                dir = newDir.normalize();
                Fx.play(level, "beam_reaim", origin, dir.scale(range), 0, user.getId());
            }
            Fx.play(level, "beam_hold", origin, dir, 1f, user.getId());
        }

        @Override
        public void leaveClash(boolean won, int extraTicks) {
            clash = null;
            if (!won) {
                Fx.play(level, "beam_stop", origin, dir, 30f, user.getId());
                drawn = false;
                fireEnd = Math.min(fireEnd, age + 1);
                return;
            }
            // Punching through: the full torrent, and its damage, for a while yet (hits start again on contact).
            hold = age - fireAt + extraTicks;
            fireEnd = fireAt + hold + collapse;
            nextHit.clear();
            Fx.play(level, "beam_release", origin, dir, extraTicks, user.getId());
        }

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
            if (clash != null) BeamClashManager.beamGone(this);
            super.interrupt(reason);
        }

        @Override
        public void end() {
            drawn = false;
            if (clash != null) BeamClashManager.beamGone(this);
            if (rika != null) YutaCombat.free(user);
        }
    }
}
