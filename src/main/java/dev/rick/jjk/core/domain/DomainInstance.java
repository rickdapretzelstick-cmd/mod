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
    /** It has been in a clash (its opening rules, like breaking on an empty space, don't apply after one). */
    boolean clashed;
    @Nullable dev.rick.jjk.core.domain.clash.ClashSession clash;
    /** onActivated has run (a domain that expands straight into a clash activates only if it wins). */
    boolean activated;
    @Nullable EndReason endReason;
    @Nullable dev.rick.jjk.core.domain.structure.DomainStructure structure;
    /** Victim UUID → ticks spent inside. */
    final Map<UUID, Integer> victims = new HashMap<>();
    /** The split with the domain this one is clashing with, while the clash (and the winner's conquest) runs. */
    @Nullable ClashFront front;
    /** Territory taken from domains this one beat in a clash: their space is now part of this domain. */
    final java.util.List<Annex> annexes = new java.util.ArrayList<>();
    /** The domain that consumed this one in a clash (it has nothing of its own left to show). */
    @Nullable DomainInstance consumedBy;

    /** A conquered domain's sphere and its blocks, held (and eventually restored) by the winner. */
    public record Annex(Vec3 center, double radius, @Nullable dev.rick.jjk.core.domain.structure.DomainStructure structure) {}

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

    /** Ticks it has been open (active, not forming or clashing). */
    public int activeAge() {
        return activeAge;
    }

    @Nullable
    public dev.rick.jjk.core.domain.structure.DomainStructure structure() {
        return structure;
    }

    public boolean contains(Vec3 pos) {
        return depth(pos) >= 0;
    }

    /** How far inside this domain's space a point is, in blocks (negative outside). Conquered territory counts. */
    public double depth(Vec3 pos) {
        double best = radius - pos.distanceTo(center);
        for (Annex a : annexes) best = Math.max(best, a.radius() - pos.distanceTo(a.center()));
        return best;
    }

    /** The center of the part of this domain a point is deepest in (where the barrier pushes it back toward). */
    public Vec3 anchor(Vec3 pos) {
        Vec3 at = center;
        double best = radius - pos.distanceTo(center);
        for (Annex a : annexes) {
            double d = a.radius() - pos.distanceTo(a.center());
            if (d > best) {
                best = d;
                at = a.center();
            }
        }
        return at;
    }

    public java.util.List<Annex> annexes() {
        return java.util.Collections.unmodifiableList(annexes);
    }

    @Nullable
    public ClashFront front() {
        return front;
    }

    public boolean contains(LivingEntity e) {
        return contains(e.getBoundingBox().getCenter());
    }

    public boolean isVictim(LivingEntity e) {
        return victims.containsKey(e.getUUID());
    }

    public boolean clashed() {
        return clashed;
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
