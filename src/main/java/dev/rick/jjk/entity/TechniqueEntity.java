package dev.rick.jjk.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * Base for technique manifestations (Blue, Red, Hollow Purple). They are server-simulated, never saved
 * (a technique doesn't survive a chunk unload or restart) and can't be hit or pushed.
 * Visuals are rendered client-side from the synced owner, scale and phase.
 */
public abstract class TechniqueEntity extends Entity {
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(TechniqueEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(TechniqueEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(TechniqueEntity.class, EntityDataSerializers.INT);

    @Nullable protected LivingEntity owner;

    protected TechniqueEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER, -1);
        builder.define(SCALE, 1f);
        builder.define(PHASE, 0);
    }

    public void setOwner(LivingEntity owner) {
        this.owner = owner;
        this.entityData.set(OWNER, owner.getId());
    }

    @Nullable
    public LivingEntity owner() {
        if (owner == null && level().isClientSide()) {
            Entity e = level().getEntity(entityData.get(OWNER));
            return e instanceof LivingEntity l ? l : null;
        }
        return owner;
    }

    public int ownerId() {
        return entityData.get(OWNER);
    }

    public float scale() {
        return entityData.get(SCALE);
    }

    public void setScale(float s) {
        entityData.set(SCALE, s);
    }

    public int phase() {
        return entityData.get(PHASE);
    }

    public void setPhase(int p) {
        entityData.set(PHASE, p);
    }

    /** True when the owner is gone, dead or in another dimension. */
    protected boolean ownerLost() {
        return owner == null || !owner.isAlive() || owner.isRemoved() || owner.level() != level();
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
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 256 * 256;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {}

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {}
}
