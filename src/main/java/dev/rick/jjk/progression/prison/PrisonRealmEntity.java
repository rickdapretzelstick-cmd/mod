package dev.rick.jjk.progression.prison;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The Prison Realm out in the world: the cube opening and sealing on someone, then lying closed where it caught them.
 * It is only the realm's body: every decision (the capture, the rescue, the release) is {@link PrisonRealm}'s, which
 * checks this entity against the world's record each tick. It can't be hurt, pushed, burnt, blown up, picked up or
 * moved, it is saved with its chunk, and a lost one is raised again; a stale copy (a duplicate, an old save) removes
 * itself. Its phase and the rescue's progress are synced for the renderer.
 */
public class PrisonRealmEntity extends Entity {
    /** 0 sealing (the full sequence), 1 sealed (closed, marked), 2 opening (the release). */
    public static final int SEALING = 0, SEALED = 1, OPENING = 2;
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(PrisonRealmEntity.class, EntityDataSerializers.INT);
    /** 0-100: how far someone outside has got opening it. */
    private static final EntityDataAccessor<Integer> RESCUE = SynchedEntityData.defineId(PrisonRealmEntity.class, EntityDataSerializers.INT);

    @Nullable UUID realmId;
    @Nullable UUID target;
    /** Ticks in the current phase (saved: a sequence interrupted by a restart is resolved from it). */
    int phaseAge;
    /** Where the target stood when the restraints caught them (in-memory: a restart mid-pull fails the seal). */
    @Nullable Vec3 caughtAt;

    public PrisonRealmEntity(EntityType<? extends PrisonRealmEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(PHASE, SEALING);
        builder.define(RESCUE, 0);
    }

    public int realmPhase() {
        return entityData.get(PHASE);
    }

    void setRealmPhase(int p) {
        if (realmPhase() != p) phaseAge = 0;
        entityData.set(PHASE, p);
    }

    public int rescue() {
        return entityData.get(RESCUE);
    }

    void setRescue(int percent) {
        entityData.set(RESCUE, Math.max(0, Math.min(100, percent)));
    }

    @Nullable
    public UUID realmId() {
        return realmId;
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        if (level() instanceof ServerLevel level) {
            phaseAge++;
            PrisonRealm.entityTick(level, this);
        }
    }

    // --- Nothing moves or breaks it ---

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean skipAttackInteraction(Entity source) {
        // Hitting it does nothing (and never releases anyone).
        if (source instanceof ServerPlayer p && realmPhase() == SEALED) PrisonRealm.hint(p);
        return true;
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
        // The marker is meant to be seen from far off.
        return distance < 512 * 512;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player instanceof ServerPlayer sp) PrisonRealm.interact(sp, this);
        return InteractionResult.SUCCESS;
    }

    // --- Saved with its chunk ---

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        realmId = uuid(input.getStringOr("Realm", ""));
        target = uuid(input.getStringOr("Target", ""));
        phaseAge = input.getIntOr("PhaseAge", 0);
        entityData.set(PHASE, input.getIntOr("RealmPhase", SEALING));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        if (realmId != null) output.putString("Realm", realmId.toString());
        if (target != null) output.putString("Target", target.toString());
        output.putInt("PhaseAge", phaseAge);
        output.putInt("RealmPhase", realmPhase());
    }

    @Nullable
    private static UUID uuid(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
