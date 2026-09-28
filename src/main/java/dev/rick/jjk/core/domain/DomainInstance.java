package dev.rick.jjk.core.domain;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** One expanded domain. */
public final class DomainInstance {
    public enum Phase { FORMING, ACTIVE, CLASHING, COLLAPSING, ENDED }

    public enum EndReason { EXPIRED, OWNER_LOST, OWNER_LEFT, CLASH_LOST, CANCELLED }

    public final int id;
    public final DomainDefinition definition;
    public final LivingEntity owner;
    public final ServerLevel level;
    public final Vec3 center;
    public final double radius;
    final int duration;
    Phase phase = Phase.FORMING;
    int age;
    int phaseAge;
    int activeAge;
    @Nullable DomainInstance clashWith;
    @Nullable dev.rick.jjk.core.domain.clash.ClashSession clash;
    /** onActivated has run (a domain that expands straight into a clash activates only if it wins). */
    boolean activated;
    @Nullable EndReason endReason;
    @Nullable dev.rick.jjk.core.domain.structure.DomainStructure structure;
    /** Victim UUID → ticks spent inside. */
    final Map<UUID, Integer> victims = new HashMap<>();

    DomainInstance(int id, DomainDefinition definition, LivingEntity owner, ServerLevel level, Vec3 center, double radius, int duration) {
        this.id = id;
        this.definition = definition;
        this.owner = owner;
        this.level = level;
        this.center = center;
        this.radius = radius;
        this.duration = duration;
    }

    @Nullable
    public dev.rick.jjk.core.domain.clash.ClashSession clash() {
        return clash;
    }

    public Phase phase() {
        return phase;
    }

    public int age() {
        return age;
    }

    public int remaining() {
        return Math.max(0, duration - activeAge);
    }

    @Nullable
    public dev.rick.jjk.core.domain.structure.DomainStructure structure() {
        return structure;
    }

    public boolean contains(Vec3 pos) {
        return pos.distanceToSqr(center) <= radius * radius;
    }

    public boolean contains(LivingEntity e) {
        return contains(e.getBoundingBox().getCenter());
    }

    public boolean isVictim(LivingEntity e) {
        return victims.containsKey(e.getUUID());
    }

    public boolean isLive() {
        return phase == Phase.ACTIVE || phase == Phase.FORMING || phase == Phase.CLASHING;
    }

    @Nullable
    public DomainInstance clashingWith() {
        return clashWith;
    }

    @Nullable
    public EndReason endReason() {
        return endReason;
    }
}
