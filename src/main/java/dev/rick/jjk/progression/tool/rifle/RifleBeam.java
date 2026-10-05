package dev.rick.jjk.progression.tool.rifle;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.clash.BeamClashManager;
import dev.rick.jjk.core.clash.BeamClashSession;
import dev.rick.jjk.core.clash.ClashBeam;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.combat.HitResolver;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.combat.Targeting;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.util.Destruction;
import dev.rick.jjk.util.Motion;
import dev.rick.jjk.yuta.TrueLoveBeamProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Cursed Rifle's beam: one ultimate beam among the others in the shared clash system ({@link ClashBeam}, kind
 * {@code "rifle"}). Its shape is the same square torrent as True Love Beam and Every Last Drop
 * ({@link TrueLoveBeamProfile}: the server hits with exactly what the clients draw), its width and its strength in a
 * clash both following its output ({@link RifleRules#output}): the first unlock is narrow and is overpowered by a fresh
 * full-power beam; Maximum Output is as wide and as strong as either, and the two struggle on even terms.
 *
 * <p>It exists from the moment the arms start to deploy (so its charge can be answered, as the shared rules say), fires
 * on release once ready, and is held, uncut, for as long as a clash holds it. Terrain it carves is restored like all
 * battle damage ({@link Destruction}).
 */
public final class RifleBeam implements ClashBeam {
    public static final String KIND = "rifle";
    private static final int GROW = 3, COLLAPSE = 8;

    private final ServerPlayer owner;
    private final ServerLevel level;
    private final float output;
    private final double half;
    private final double range;
    private Vec3 origin, dir;
    /** Ticks since it fired (-1: still charging). */
    private int t = -1;
    private int hold;
    private boolean drawn;
    private boolean done;
    @Nullable private BeamClashSession clash;
    private float strength = 1f;
    private boolean measured;
    private final Map<LivingEntity, Integer> nextHit = new HashMap<>();
    private final List<BlockPos> carve = new ArrayList<>();
    private final List<Double> carveAlong = new ArrayList<>();
    private int carved;
    private float damageScale = 1f;

    RifleBeam(ServerPlayer owner, ServerLevel level, float output) {
        this.owner = owner;
        this.level = level;
        this.output = output;
        this.half = RifleRules.half(output);
        this.range = JJKConfig.get().rifle.beamRange;
        this.dir = owner.getLookAngle();
        this.origin = RifleServer.muzzle(owner, dir);
    }

    public float output() {
        return output;
    }

    public double half() {
        return half;
    }

    public boolean firing() {
        return t >= 0 && !done;
    }

    public boolean inClash() {
        return clash != null;
    }

    /** Ticks it will be drawn for once fired (hold plus the collapse). */
    int lifetime() {
        return hold + COLLAPSE;
    }

    /** While charging: where it would fire from and to (the counter window and the clash use this). */
    void aim(Vec3 o, Vec3 d) {
        if (t >= 0) return;
        origin = o;
        dir = d.normalize();
    }

    void fire(Vec3 o, Vec3 d, int holdTicks) {
        origin = o;
        dir = d.normalize();
        hold = holdTicks;
        t = 0;
        drawn = true;
        long players = level.getEntitiesOfClass(ServerPlayer.class, new AABB(origin, origin.add(dir.scale(range))).inflate(half),
                p -> p != owner && Targeting.canTarget(owner, p) && inside(p, range, 1f)).size();
        damageScale = 1f / Math.max(1, players);
        TrueLoveBeamProfile.carve(origin, dir, range, half, JJKConfig.get().rifle.beamCarveEdge, carve, carveAlong);
        // The client draws it from these numbers: shape first (half-width, grow, collapse; scale = output), then the beam.
        Fx.play(level, "rifle_beam_shape", origin, new Vec3(half, GROW, COLLAPSE), output, owner.getId());
        Fx.play(level, "rifle_beam_fire", origin, dir.scale(range), hold, owner.getId());
        Fx.sound(level, origin, SoundEvents.WARDEN_SONIC_BOOM, 2.2f, 0.7f + 0.3f * output);
        Fx.sound(level, origin, SoundEvents.BEACON_ACTIVATE, 2f, 1.8f);
        Fx.shake(level, origin, 40 + 40 * output, 0.6f + output, 20);
        BeamClashManager.fired(this);
    }

    /** One tick of the fired beam; false once it is over. */
    boolean tick() {
        if (done) return false;
        if (t < 0) return true;
        t++;
        if (clash != null) hold = Math.max(hold, t + 2);
        if (t >= hold + COLLAPSE) {
            end();
            return false;
        }
        double front = reach();
        float scale = TrueLoveBeamProfile.scale(t, hold, COLLAPSE);
        boolean live = TrueLoveBeamProfile.live(t, hold);
        JJKConfig.Rifle cfg = JJKConfig.get().rifle;
        if (live) {
            int budget = 60 + Math.round(80 * output);
            while (carved < carve.size() && budget > 0 && !Destruction.budgetExhausted(level)) {
                if (carveAlong.get(carved) > front) break;
                BlockPos bp = carve.get(carved++);
                if (level.getBlockState(bp).isAir()) continue;
                if (Destruction.destroy(level, bp, cfg.beamMaxHardness * output, owner, "cursed_rifle")) budget--;
            }
        }
        if (t % 4 == 0) Fx.play(level, "rifle_beam_pulse", strike(front), dir, t, owner.getId());
        if (!live || scale <= 0) return true;
        AABB box = new AABB(origin, origin.add(dir.scale(front))).inflate(half + 1.5);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != owner && e.isAlive() && Targeting.canTarget(owner, e))) {
            if (!inside(e, front, scale)) continue;
            if (clash != null && clash.sideOf(e) != null) continue;
            Motion.add(e, dir.scale(0.12));
            Integer due = nextHit.get(e);
            if (due != null && t < due) continue;
            boolean first = due == null;
            nextHit.put(e, t + cfg.beamDamageInterval);
            float dmg = (first ? cfg.beamDamage : cfg.beamTickDamage) * Mth.clamp(output, 0.3f, 1f) * damageScale;
            HitResolver.resolve(Hit.builder(owner, "cursed_rifle_beam").damage(dmg)
                    .tag(AttackTag.PROJECTILE, AttackTag.UNBLOCKABLE, AttackTag.EXPLOSION, AttackTag.ULTIMATE)
                    .origin(origin).knockback(Knockback.set(dir.scale(0.6 + 0.5 * output).add(0, 0.15, 0)))
                    .hitstun(cfg.beamDamageInterval + 4).status(CombatStatus.LAUNCHED, cfg.beamDamageInterval + 4).noComboScaling()
                    .fx("beam_hit", 1f + output).build(), e);
        }
        return true;
    }

    private double reach() {
        double front = TrueLoveBeamProfile.front(range, GROW, t);
        if (clash != null) front = Math.min(front, clash.reach(this));
        return front;
    }

    private Vec3 strike(double reach) {
        Vec3 end = origin.add(dir.scale(reach));
        var clip = level.clip(new ClipContext(origin.add(dir.scale(1.5)), end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        return clip.getType() == HitResult.Type.MISS ? end : clip.getLocation();
    }

    private boolean inside(LivingEntity e, double front, float scale) {
        double body = e.getBbWidth() / 2 + 0.15;
        for (Vec3 p : new Vec3[] {e.position().add(0, 0.2, 0), e.getBoundingBox().getCenter(), e.getEyePosition()}) {
            if (TrueLoveBeamProfile.contains(origin, dir, front, half, scale, p, body)) return true;
        }
        return false;
    }

    /** Ends it at once (cancelled, switched away, its owner gone); whatever clash it was in can't go on. */
    void stop() {
        if (done) return;
        if (drawn) Fx.play(level, "rifle_beam_stop", origin, dir, 1f, owner.getId());
        end();
    }

    private void end() {
        done = true;
        drawn = false;
        BeamClashManager.beamGone(this);
    }

    // --- ClashBeam ---

    @Override
    public LivingEntity beamOwner() {
        return owner;
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
        return drawn && !done && clash == null && t >= 0 && t < hold;
    }

    @Override
    public String beamKind() {
        return KIND;
    }

    /** Its freshness (as the other beams measure it) times its output: the first unlock can't hold a full-power beam. */
    @Override
    public float beamStrength() {
        return strength * output;
    }

    @Override
    public void enterClash(BeamClashSession session, Vec3 newOrigin, Vec3 newDir) {
        if (!measured) {
            measured = true;
            strength = (float) Mth.clamp(1.0 - Math.max(0, t) / (double) Math.max(1, hold), 0, 1);
        }
        clash = session;
        if (newDir.lengthSqr() > 1e-6) {
            dir = newDir.normalize();
            Fx.play(level, "rifle_beam_reaim", origin, dir.scale(range), 0, owner.getId());
        }
        Fx.play(level, "rifle_beam_hold", origin, dir, 1f, owner.getId());
    }

    @Override
    public void leaveClash(boolean won, int extraTicks) {
        clash = null;
        if (!won) {
            Fx.play(level, "rifle_beam_stop", origin, dir, 1f, owner.getId());
            hold = Math.min(hold, t + 1);
            return;
        }
        hold = t + extraTicks;
        nextHit.clear();
        Fx.play(level, "rifle_beam_release", origin, dir, extraTicks, owner.getId());
    }
}
