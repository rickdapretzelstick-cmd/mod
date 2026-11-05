package dev.rick.jjk.progression.training;

import dev.rick.jjk.progression.investigation.CursedRealms;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A piece of a relic-trial room drawn from its Blockbench model ({@code models/bb/<model>.bbmodel}): a whole room's
 * furnishings at once, or one of its working parts (the CRT's screen, the Blue, the reels, the altar's blade, the
 * target's face). A room has a handful of these, never one per cube.
 *
 * <p>It only shows. What it shows is the server's: the scene sets {@link #setState}/{@link #setParam} (the television's
 * mood, the reels' symbols and whether each is spinning, the Blue's size, a glow, the target shattered) and every client
 * draws exactly that; anything that merely moves (a ring turning, a symbol bobbing) runs from the shared game time.
 *
 * <p>It can't be hurt, pushed, hit or collided with, is never saved (a restart closes the realm arena anyway) and removes
 * itself if it ever finds itself outside an open realm arena.
 */
public class TrialPropEntity extends Entity {
    public static final int STATIC = 0, CRT = 1, BLUE = 2, REELS = 3, GLOW = 4, TARGET = 5;
    /** The mod's own Rika, shown as a presence (her model and idle clip; no entity of hers, no owner, no AI); param: scale. */
    public static final int RIKA = 6;
    /** Flags: hidden (the target's face, shattered). */
    public static final int HIDDEN = 1;

    private static final EntityDataAccessor<String> MODEL = SynchedEntityData.defineId(TrialPropEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(TrialPropEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(TrialPropEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> PARAM = SynchedEntityData.defineId(TrialPropEntity.class, EntityDataSerializers.FLOAT);
    /** Game time of the last state change (the client times a reel's settle from it). */
    private static final EntityDataAccessor<Integer> STAMP = SynchedEntityData.defineId(TrialPropEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FLAGS = SynchedEntityData.defineId(TrialPropEntity.class, EntityDataSerializers.INT);

    public TrialPropEntity(EntityType<? extends TrialPropEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public static int kind(String name) {
        return switch (name) {
            case "crt" -> CRT;
            case "blue" -> BLUE;
            case "reels" -> REELS;
            case "glow" -> GLOW;
            case "target" -> TARGET;
            case "rika" -> RIKA;
            default -> STATIC;
        };
    }

    /** A prop for {@code model}, at {@code at} (the room's origin corner: its models are drawn in room coordinates). */
    public static TrialPropEntity create(ServerLevel level, String model, int kind, Vec3 at) {
        TrialPropEntity e = new TrialPropEntity(dev.rick.jjk.registry.ModEntities.TRIAL_PROP, level);
        e.entityData.set(MODEL, model);
        e.entityData.set(KIND, kind);
        e.entityData.set(PARAM, 1f);
        e.setPos(at.x, at.y, at.z);
        return e;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(MODEL, "");
        b.define(KIND, STATIC);
        b.define(STATE, 0);
        b.define(PARAM, 1f);
        b.define(STAMP, 0);
        b.define(FLAGS, 0);
    }

    public String model() {
        return entityData.get(MODEL);
    }

    public int kind() {
        return entityData.get(KIND);
    }

    public int state() {
        return entityData.get(STATE);
    }

    public float param() {
        return entityData.get(PARAM);
    }

    public int stamp() {
        return entityData.get(STAMP);
    }

    public boolean has(int flag) {
        return (entityData.get(FLAGS) & flag) != 0;
    }

    public void setState(int s) {
        if (state() != s) {
            entityData.set(STATE, s);
            entityData.set(STAMP, (int) level().getGameTime());
        }
    }

    public void setParam(float p) {
        if (Math.abs(param() - p) > 1e-3f) entityData.set(PARAM, p);
    }

    public void setFlag(int flag, boolean on) {
        int f = entityData.get(FLAGS), n = on ? f | flag : f & ~flag;
        if (n != f) entityData.set(FLAGS, n);
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        // Never outlives its room: a prop that finds no open arena around it (an interrupted session) goes.
        if (level() instanceof ServerLevel && tickCount % 40 == 0 && CursedRealms.arenaHere(this) == null) discard();
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeHitByProjectile() {
        return false;
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        return true;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE_ENTITY;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 160 * 160;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {}

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {}
}
