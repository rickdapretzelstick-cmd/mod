package dev.rick.jjk.hakari;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.hitbox.HitShape;
import dev.rick.jjk.core.hitbox.HitboxQuery;
import dev.rick.jjk.entity.HakariDoorEntity;
import dev.rick.jjk.registry.ModDamageTypes;
import dev.rick.jjk.util.Motion;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A pair of shutter doors from the "Private Pure Love Train" pachinko game, standing on the field. Every one of Hakari's
 * door moves is one of these, in a different mode:
 * <ul>
 *   <li>{@link Mode#STRIKE} — Shutter Doors: rise either side of the target and slam shut on their torso (stun). A target
 *       low enough is shut in completely (the finisher). If nobody is caught the doors linger (see below).</li>
 *   <li>{@link Mode#BOUNCE} — Reserve Balls' doors: they appear where the ball landed; a target the ball stunned is caught
 *       and bounced off them a few times.</li>
 *   <li>{@link Mode#HOLD} — Fever Crush: the doors clamp the target in place for the axe kick.</li>
 *   <li>{@link Mode#SUSPEND} — Fever Breaker: the target hangs in front of the doors for the dropkick.</li>
 * </ul>
 * Lingering doors (after a miss, 7 seconds): Hakari can jump on them to bounce high, shattering them; a ragdolled enemy
 * falling onto them bounces a few times, taking damage each time, before they break.
 */
public final class ShutterTrap {
    public enum Mode { STRIKE, BOUNCE, HOLD, SUSPEND }

    private static final List<ShutterTrap> ACTIVE = new ArrayList<>();

    final ServerLevel level;
    final LivingEntity owner;
    final Mode mode;
    @Nullable LivingEntity target;
    Vec3 center;
    private final Vec3 across;
    private final float yaw;
    private final HakariDoorEntity left, right;
    private int age;
    private boolean slammed, lingering, done;
    private int lingerAge, bounces;
    /** The doors' own damage when they shut (Shutter Doors' 8, the combination's 3, Fever Crush's 8). */
    private final float damage;

    private ShutterTrap(ServerLevel level, LivingEntity owner, Mode mode, Vec3 center, @Nullable LivingEntity target, Vec3 facing, float damage) {
        this.level = level;
        this.owner = owner;
        this.mode = mode;
        this.center = center;
        this.target = target;
        this.damage = damage;
        Vec3 f = new Vec3(facing.x, 0, facing.z);
        f = f.lengthSqr() < 1e-4 ? HakariCombat.flat(owner) : f.normalize();
        across = new Vec3(-f.z, 0, f.x);
        yaw = (float) (Mth.atan2(f.z, f.x) * Mth.RAD_TO_DEG) - 90f;
        int life = JJKConfig.get().hakari.shutterLingerTicks + 80;
        left = HakariDoorEntity.spawn(level, owner, HakariDoorEntity.SHUTTER, doorPos(-1, 0), yaw, life);
        right = HakariDoorEntity.spawn(level, owner, HakariDoorEntity.SHUTTER, doorPos(1, 0), yaw, life);
        Fx.play(level, "shutter_appear", center, across, 1f, owner.getId());
    }

    /** Doors across the line from Hakari to {@code center}, closing sideways onto whoever stands there. */
    public static ShutterTrap open(ServerLevel level, LivingEntity owner, Mode mode, Vec3 center, @Nullable LivingEntity target, float damage) {
        ShutterTrap t = new ShutterTrap(level, owner, mode, center, target, center.subtract(owner.position()), damage);
        ACTIVE.add(t);
        return t;
    }

    public static void tick(ServerLevel level) {
        for (ShutterTrap t : List.copyOf(ACTIVE)) {
            if (t.level != level) continue;
            if (t.done || !t.owner.isAlive() || t.owner.level() != level) {
                t.remove();
                continue;
            }
            t.tick();
        }
    }

    public static void clearAll() {
        for (ShutterTrap t : List.copyOf(ACTIVE)) t.remove();
        ACTIVE.clear();
    }

    public boolean caught() {
        return slammed && !lingering && target != null;
    }

    public boolean isDone() {
        return done;
    }

    private static JJKConfig.Hakari cfg() {
        return JJKConfig.get().hakari;
    }

    /** Height of the flat panels' underside above the target's feet: they shut across the torso. */
    static final double PANEL_Y = 1.1;
    /** The panels' top surface (what lingering doors are bounced off). */
    static final double PANEL_TOP = PANEL_Y + HakariDoorEntity.SHUTTER_THICKNESS;

    /**
     * Where a door lies: {@code side} of the center, {@code close} 0 = wide open .. 1 = shut. The doors are flat
     * panels; shut, their inner edges meet over the target (half a panel's width either side).
     */
    private Vec3 doorPos(int side, float close) {
        double gap = Mth.lerp(close, 2.4, HakariDoorEntity.SHUTTER_WIDTH / 2);
        double rise = Math.min(1, age / (double) Math.max(1, cfg().shutterRiseTicks));
        return center.add(across.scale(side * gap)).add(0, PANEL_Y - 2.6 * (1 - rise), 0);
    }

    private void place(float close) {
        if (!left.isRemoved()) left.place(doorPos(-1, close), yaw);
        if (!right.isRemoved()) right.place(doorPos(1, close), yaw);
    }

    private void tick() {
        age++;
        JJKConfig.Hakari cfg = cfg();
        int rise = cfg.shutterRiseTicks, close = cfg.shutterCloseTicks;
        if (lingering) {
            linger();
            return;
        }
        // A held target keeps the doors on them until they shut.
        if (!slammed && target != null && target.isAlive()) center = target.position();
        float c = age < rise ? 0 : Math.min(1, (age - rise) / (float) close);
        place(c);
        if (!slammed && age >= rise + close) slam();
        if (!slammed) return;
        switch (mode) {
            case STRIKE -> {
                if (target == null) {
                    startLinger();
                } else if (age >= rise + close + cfg.shutterHoldTicks) {
                    shatter(false);
                }
            }
            case BOUNCE -> bounceTarget();
            case HOLD, SUSPEND -> {
                // Held until the move that set it up lets go (see release), or a safety limit.
                if (target != null && target.isAlive()) {
                    Statuses.apply(target, CombatStatus.GRABBED, 3);
                    Vec3 hold = center.add(0, mode == Mode.SUSPEND ? 0.6 : 0, 0);
                    Motion.set(target, hold.subtract(target.position()).scale(0.6));
                }
                if (age > rise + close + 60) release(true);
            }
        }
    }

    private void slam() {
        slammed = true;
        JJKConfig.Hakari cfg = cfg();
        Fx.play(level, "shutter_slam", center.add(0, 1.2, 0), across, 1f, owner.getId());
        Fx.shake(level, center, 16, 0.5f, 8);
        if (mode == Mode.SUSPEND) return; // the kick already landed; the doors are the backdrop
        List<LivingEntity> caught = HitboxQuery.targets(owner, HitShape.orientedBox(center.subtract(across.scale(1.4)).add(0, 1.2, 0), across, 2.8, 1.6, 2.6), 0.3, false);
        if (target != null && !caught.contains(target)) target = null;
        if (target == null && !caught.isEmpty()) target = caught.getFirst();
        if (target == null) return;
        // Shut in completely: the finisher.
        if (mode == Mode.STRIKE && HakariCombat.finishable(target)) {
            place(1);
            HakariCombat.execute(owner, target, ShutterDoorsAbility.ID, "shutter_finisher");
            return;
        }
        Hit hit = Hit.builder(owner, ShutterDoorsAbility.ID).type(ModDamageTypes.TECHNIQUE).damage(damage)
                .tag(AttackTag.TECHNIQUE, AttackTag.PROJECTILE).origin(center).knockback(Knockback.NONE)
                .hitstun(mode == Mode.STRIKE ? cfg.shutterHitstun : 40).guardDamage(3).fx("shutter_crush", 1f).build();
        if (!HakariCombat.hit(hit, target).connected()) target = null;
    }

    /** The combination: a target the ball stunned bounces off the doors. */
    private void bounceTarget() {
        JJKConfig.Hakari cfg = cfg();
        if (target == null || !target.isAlive()) {
            if (target == null && age > cfg.shutterRiseTicks + cfg.shutterCloseTicks + 4) startLinger();
            else if (target != null) shatter(false);
            return;
        }
        int since = age - cfg.shutterRiseTicks - cfg.shutterCloseTicks;
        if (since > 0 && since % 10 == 0 && bounces < cfg.doorBounces) {
            bounce(target);
            if (bounces >= cfg.doorBounces) shatterLater = age + 10;
        }
        if (shatterLater > 0 && age >= shatterLater) shatter(false);
    }

    private int shatterLater;

    private void bounce(LivingEntity e) {
        bounces++;
        Hit hit = Hit.builder(owner, ShutterDoorsAbility.ID).type(ModDamageTypes.TECHNIQUE).damage(cfg().doorBounceDamage)
                .tag(AttackTag.TECHNIQUE, AttackTag.OTG).origin(center).knockback(Knockback.set(new Vec3(0, 0.75, 0)))
                .hitstun(14).status(CombatStatus.LAUNCHED, 12).noComboScaling().fx("door_bounce", 1f).build();
        HakariCombat.hit(hit, e);
        Fx.play(level, "door_bounce", center.add(0, PANEL_TOP, 0), Vec3.ZERO, 1f, owner.getId());
    }

    private void startLinger() {
        lingering = true;
        lingerAge = 0;
        target = null;
        place(1f);
        Fx.play(level, "shutter_linger", center.add(0, 1.2, 0), across, 1f, owner.getId());
    }

    /** Missed doors stand for a while: a spring for Hakari, a trampoline for anyone ragdolled onto them. */
    private void linger() {
        JJKConfig.Hakari cfg = cfg();
        place(1f);
        if (++lingerAge > cfg.shutterLingerTicks) {
            shatter(false);
            return;
        }
        // Lingering, the two panels lie flush as one platform (2.2 across each, 2.8 long): landing on its top bounces.
        AABB top = new AABB(center.x - 2.0, center.y + PANEL_TOP - 0.3, center.z - 2.0, center.x + 2.0, center.y + PANEL_TOP + 1.0, center.z + 2.0);
        // Hakari lands on them: a high bounce, and they shatter.
        if (owner.getBoundingBox().intersects(top) && owner.getDeltaMovement().y <= 0.05) {
            Motion.set(owner, new Vec3(owner.getDeltaMovement().x, cfg.shutterBounceLaunch, owner.getDeltaMovement().z));
            owner.resetFallDistance();
            Fx.play(level, "door_bounce", center.add(0, PANEL_TOP, 0), Vec3.ZERO, 1.4f, owner.getId());
            shatter(true);
            return;
        }
        // A ragdolled enemy falling onto them bounces, and bounces again.
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, top.inflate(0.4), e -> e != owner && e.isAlive())) {
            boolean ragdolled = Combat.has(e, CombatStatus.LAUNCHED) || Combat.has(e, CombatStatus.KNOCKDOWN) || Combat.has(e, CombatStatus.SPIKED);
            if (!ragdolled || e.getDeltaMovement().y > 0.05) continue;
            bounce(e);
            if (bounces >= cfg.doorBounces) {
                shatter(false);
                return;
            }
        }
    }

    /** The move that holds the target (Fever Crush, Fever Breaker) is done with the doors. */
    public void release(boolean shatter) {
        if (target != null) Statuses.remove(target, CombatStatus.GRABBED);
        if (shatter) shatter(false);
        else startLinger();
    }

    public void shatter(boolean byBounce) {
        if (done) return;
        done = true;
        Fx.play(level, "shutter_shatter", center.add(0, 1.2, 0), across, byBounce ? 1.3f : 1f, owner.getId());
        remove();
    }

    private void remove() {
        done = true;
        if (target != null && mode != Mode.STRIKE) Statuses.remove(target, CombatStatus.GRABBED);
        left.discard();
        right.discard();
        ACTIVE.remove(this);
    }
}
