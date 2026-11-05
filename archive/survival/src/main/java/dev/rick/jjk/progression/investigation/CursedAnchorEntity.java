package dev.rick.jjk.progression.investigation;

import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.progression.curse.CommonCurseEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A cursed anchor: the objective of a "destroy a guarded cursed anchor" contract. A dark, slowly turning obelisk rooted
 * in the arena. It never moves or attacks; any weapon damages it. While one of its contract's curses still stands near
 * it, it is <b>guarded</b>: a shell of cursed energy turns most of each blow aside, so the guards come first. Destroying
 * it completes the contract (the rest of its curses scatter).
 */
public class CursedAnchorEntity extends PathfinderMob {
    private static final EntityDataAccessor<Boolean> GUARDED = SynchedEntityData.defineId(CursedAnchorEntity.class, EntityDataSerializers.BOOLEAN);
    /** Guards count within this distance; a guarded anchor takes this share of each hit. */
    public static final double GUARD_RANGE = 10;
    public static final float GUARDED_SHARE = 0.25f;
    private String incident = "";
    @Nullable private Vec3 root;

    public CursedAnchorEntity(EntityType<? extends CursedAnchorEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 60).add(Attributes.MOVEMENT_SPEED, 0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.FOLLOW_RANGE, 1);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(GUARDED, true);
    }

    public boolean guarded() {
        return entityData.get(GUARDED);
    }

    public String incidentId() {
        return incident;
    }

    /** Roots it for a contract, with health for its grade. */
    public void setup(String incidentId, Vec3 at, double health) {
        incident = incidentId;
        root = at;
        setPos(at.x, at.y, at.z);
        var hp = getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(health);
        setHealth((float) health);
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        if (level().isClientSide()) return;
        if (root == null) root = position();
        if (position().distanceToSqr(root) > 0.01) setPos(root.x, root.y, root.z);
        if (tickCount % 10 == 0 && level() instanceof ServerLevel level) {
            boolean g = guardsNear(level);
            if (g != guarded()) {
                entityData.set(GUARDED, g);
                if (!g) {
                    Fx.sound(level, position(), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 1f, 0.7f);
                    Fx.play(level, "curse_exposed", position().add(0, 1.2, 0), Vec3.ZERO, 1.2f, getId());
                }
            }
        }
        if (tickCount % 60 == 0 && level() instanceof ServerLevel level) Fx.sound(level, position(), SoundEvents.RESPAWN_ANCHOR_AMBIENT, 0.6f, 0.6f);
    }

    private boolean guardsNear(ServerLevel level) {
        if (incident.isEmpty()) return false;
        for (CommonCurseEntity c : level.getEntitiesOfClass(CommonCurseEntity.class, getBoundingBox().inflate(GUARD_RANGE))) {
            if (c.isAlive() && incident.equals(c.incidentId()) && !c.dormant()) return true;
        }
        return false;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (guarded()) {
            amount *= GUARDED_SHARE;
            Fx.sound(level, position(), SoundEvents.SHIELD_BLOCK.value(), 0.8f, 0.6f);
        }
        return super.hurtServer(level, source, amount);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double d) {
        return false;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.DEEPSLATE_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.RESPAWN_ANCHOR_DEPLETE.value();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putString("JjkIncident", incident);
        if (root != null) out.store("JjkRoot", BlockPos.CODEC, BlockPos.containing(root));
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        incident = in.getStringOr("JjkIncident", "");
        root = in.read("JjkRoot", BlockPos.CODEC).map(Vec3::atBottomCenterOf).orElse(null);
    }
}
