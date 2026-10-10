package dev.rick.jjk.progression.investigation;

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

import java.util.function.Consumer;

/**
 * A Cursed Breach: the one way into every cursed realm. Where an incident (or a cursed battle room) is anchored, space
 * has torn: a slow dark rift hanging in the air, its edges crawling, the air and dust around it pulled in, a low drone.
 * Use it (right-click) and you are taken (the short pull of {@link CursedRealms#pull}) into that incident's realm.
 *
 * <p>It is only a door: which realm it opens, and whether it is still open, is {@link CursedBreaches}'. It is never saved
 * (the site raises it again whenever someone is near and the incident is still open), so a breach can never outlive its
 * incident, duplicate across a reload, or be left behind. It can't be hurt, pushed or moved.
 */
public class CursedBreachEntity extends Entity {
    /** 0 waiting; 1 someone is already inside (it churns faster: the fight is on, and you can join it). */
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(CursedBreachEntity.class, EntityDataSerializers.INT);
    /** The client's per-tick look (particles, sound): set by the client mod, a no-op on a dedicated server. */
    public static Consumer<CursedBreachEntity> clientTick = e -> {};

    /** The incident id, or {@link CursedRealms#ROOM} + a battle room's key. */
    String target = "";

    public CursedBreachEntity(EntityType<? extends CursedBreachEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(STATE, 0);
    }

    public String target() {
        return target;
    }

    public int breachState() {
        return entityData.get(STATE);
    }

    void setBreachState(int s) {
        if (breachState() != s) entityData.set(STATE, s);
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        if (level() instanceof ServerLevel level) CursedBreaches.tick(level, this);
        else clientTick.accept(this);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player instanceof ServerPlayer sp) CursedBreaches.use(sp, this);
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    public boolean skipAttackInteraction(Entity source) {
        // Hitting it is the same as reaching into it.
        if (source instanceof ServerPlayer sp) CursedBreaches.use(sp, this);
        return true;
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
        return distance < 96 * 96;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        target = input.getStringOr("Target", "");
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putString("Target", target);
    }
}
