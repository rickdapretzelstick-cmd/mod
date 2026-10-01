package dev.rick.jjk.yuta;

import dev.rick.jjk.entity.TechniqueEntity;
import dev.rick.jjk.registry.ModEntities;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Rika, the Queen of Curses (Cursed Partners). An intangible, invisible-to-hits manifestation that floats at Yuta's
 * side: she can't be struck, pushed or targeted, and every blow she lands is Yuta's.
 *
 * <p>Where she goes is decided every tick, in order: a move driving her somewhere ({@link #moveTo}), Yuta piloting her
 * with his movement keys while her moveset is up, being stationed where she was left, or her place at his side (to
 * his right, partly manifested; behind him once fully manifested in True Love). She always faces her target if she has
 * one, otherwise the way he faces, and never strays further than {@link #MAX_RANGE} from him.
 */
public class RikaEntity extends TechniqueEntity {
    /** Synced look: shown at all, piloted (her moveset is up), fully manifested (True Love), busy with a move. */
    public static final int VISIBLE = 1, PILOTED = 2, FULL = 4, BUSY = 8, STATIONED = 16;
    private static final EntityDataAccessor<Integer> FLAGS = SynchedEntityData.defineId(RikaEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(RikaEntity.class, EntityDataSerializers.INT);
    /** 100 studs from Yuta. */
    public static final double MAX_RANGE = 28;

    @Nullable private Vec3 destination;
    private double destinationSpeed;
    @Nullable private LivingEntity target;
    @Nullable private Vec3 station;
    private int moveTicks;

    public RikaEntity(EntityType<? extends RikaEntity> type, Level level) {
        super(type, level);
    }

    public static RikaEntity summon(ServerLevel level, LivingEntity owner) {
        RikaEntity r = new RikaEntity(ModEntities.RIKA, level);
        r.setOwner(owner);
        Vec3 at = r.sideSpot(owner);
        r.setPos(at.x, at.y, at.z);
        r.setYRot(owner.getYRot());
        level.addFreshEntity(r);
        return r;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FLAGS, VISIBLE);
        builder.define(TARGET, -1);
    }

    public int flags() {
        return entityData.get(FLAGS);
    }

    public boolean has(int flag) {
        return (flags() & flag) != 0;
    }

    public void set(int flag, boolean on) {
        int f = flags();
        int n = on ? f | flag : f & ~flag;
        if (n != f) entityData.set(FLAGS, n);
    }

    /** The entity she faces and attacks, or null. */
    @Nullable
    public LivingEntity target() {
        if (target != null && (!target.isAlive() || target.level() != level())) target = null;
        return target;
    }

    public void setTarget(@Nullable LivingEntity t) {
        target = t;
        entityData.set(TARGET, t == null ? -1 : t.getId());
    }

    /** Client side: the synced target. */
    @Nullable
    public Entity syncedTarget() {
        int id = entityData.get(TARGET);
        return id < 0 ? null : level().getEntity(id);
    }

    /** Drives her to {@code pos} at up to {@code speed} blocks a tick for the next {@code ticks} ticks (a move's hover). */
    public void moveTo(Vec3 pos, double speed, int ticks) {
        destination = pos;
        destinationSpeed = speed;
        moveTicks = ticks;
    }

    /** Leaves her where she is (the special held while recalling her). */
    public void station() {
        station = position();
        set(STATIONED, true);
    }

    public void recall() {
        station = null;
        destination = null;
        set(STATIONED, false);
    }

    /** Her place at his side: to his right, a little behind; fully manifested, she looms right behind him. */
    public Vec3 sideSpot(LivingEntity owner) {
        float yaw = owner.getYRot() * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
        if (has(FULL)) return owner.position().add(fwd.scale(-1.9)).add(right.scale(0.5)).add(0, 0.1, 0);
        return owner.position().add(right.scale(1.6)).add(fwd.scale(-0.6)).add(0, 0.25, 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;
        LivingEntity o = owner;
        if (ownerLost() || o == null) {
            discard();
            return;
        }
        Vec3 want;
        double speed;
        if (destination != null && moveTicks > 0) {
            moveTicks--;
            want = destination;
            speed = destinationSpeed;
            if (moveTicks == 0) destination = null;
        } else if (has(PILOTED) && o instanceof ServerPlayer sp) {
            want = position().add(pilot(sp));
            speed = 1.4;
        } else if (station != null) {
            want = station;
            speed = 1.2;
        } else {
            want = sideSpot(o);
            speed = 1.6;
        }
        // Never further than 100 studs from Yuta.
        Vec3 rel = want.subtract(o.position());
        if (rel.length() > MAX_RANGE) want = o.position().add(rel.normalize().scale(MAX_RANGE));
        Vec3 step = want.subtract(position());
        double len = step.length();
        if (len > speed) step = step.scale(speed / len);
        else step = step.scale(Math.min(1, 0.55 + 0.45 * len / Math.max(1e-3, speed)));
        if (len > 64) step = want.subtract(position());
        setPos(getX() + step.x, getY() + step.y, getZ() + step.z);
        setDeltaMovement(step);

        // Face the target if she has one, otherwise the way he faces (or the way she is flying while piloted).
        LivingEntity t = target();
        float yaw;
        if (t != null) {
            Vec3 d = t.position().subtract(position());
            yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        } else if (has(PILOTED) && step.horizontalDistanceSqr() > 0.01) {
            yaw = (float) (Mth.atan2(step.z, step.x) * Mth.RAD_TO_DEG) - 90f;
        } else {
            yaw = o.getYRot();
        }
        setYRot(Mth.approachDegrees(getYRot(), yaw, 25f));
        setYHeadRot(getYRot());
    }

    /** One tick of flight from the player's movement keys, relative to where they look (jump rises, sneak sinks). */
    private Vec3 pilot(ServerPlayer p) {
        Input in = p.getLastClientInput();
        float yaw = p.getYRot() * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 left = new Vec3(fwd.z, 0, -fwd.x);
        Vec3 d = Vec3.ZERO;
        if (in.forward()) d = d.add(fwd);
        if (in.backward()) d = d.subtract(fwd);
        if (in.left()) d = d.add(left);
        if (in.right()) d = d.subtract(left);
        if (in.jump()) d = d.add(0, 0.6, 0);
        if (in.shift()) d = d.add(0, -0.6, 0);
        return d.lengthSqr() < 1e-4 ? Vec3.ZERO : d.normalize().scale(1.4);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 160 * 160;
    }
}
