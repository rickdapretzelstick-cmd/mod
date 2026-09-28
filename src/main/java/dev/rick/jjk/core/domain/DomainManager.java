package dev.rick.jjk.core.domain;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.core.combat.Targeting;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.domain.clash.ClashManager;
import dev.rick.jjk.core.domain.clash.ClashSession;
import dev.rick.jjk.core.net.DomainPayload;
import dev.rick.jjk.util.Motion;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runs every domain in every level: formation, sure-hit application, the barrier, clashes, cancellation and expiry.
 * Framework rules that apply to all domains:
 * <ul>
 *   <li>One live domain per owner.</li>
 *   <li>The owner must stay inside; leaving or dying collapses the domain.</li>
 *   <li>A domain expanded where another is live starts a clash; the weaker one collapses.</li>
 *   <li>Someone standing inside their own live domain is immune to other domains' sure-hit.</li>
 *   <li>When a domain collapses its owner's technique burns out for a while.</li>
 * </ul>
 */
public final class DomainManager {
    private static final Map<ServerLevel, List<DomainInstance>> DOMAINS = new IdentityHashMap<>();
    private static int nextId = 1;

    private DomainManager() {}

    public static List<DomainInstance> all(ServerLevel level) {
        return DOMAINS.getOrDefault(level, List.of());
    }

    @Nullable
    public static DomainInstance ownedBy(LivingEntity owner) {
        if (!(owner.level() instanceof ServerLevel level)) return null;
        for (DomainInstance d : all(level)) if (d.owner == owner && d.isLive()) return d;
        return null;
    }

    /** Live domains whose barrier contains this point. */
    public static List<DomainInstance> at(ServerLevel level, Vec3 pos) {
        List<DomainInstance> out = new ArrayList<>();
        for (DomainInstance d : all(level)) if (d.isLive() && d.contains(pos)) out.add(d);
        return out;
    }

    /** "Is the target inside a domain?" (someone else's). */
    public static boolean isInsideEnemyDomain(LivingEntity e) {
        if (!(e.level() instanceof ServerLevel level)) return Combat.has(e, CombatStatus.IN_DOMAIN);
        for (DomainInstance d : at(level, e.getBoundingBox().getCenter())) if (d.owner != e) return true;
        return false;
    }

    @Nullable
    public static DomainInstance expand(LivingEntity owner, DomainDefinition def) {
        if (!(owner.level() instanceof ServerLevel level) || ownedBy(owner) != null) return null;
        net.minecraft.core.BlockPos anchor = net.minecraft.core.BlockPos.containing(owner.position().add(0, 0.2, 0));
        var spec = JJKConfig.get().domain.physicalStructure ? def.structure(owner) : null;
        // Gameplay boundary sits just inside the physical shell so the two always agree.
        double radius = spec != null ? spec.radius() - spec.thickness() : def.radius(owner);
        Vec3 center = spec != null ? Vec3.atBottomCenterOf(anchor).add(0, 0.5, 0) : owner.position().add(0, 0.5, 0);
        DomainInstance d = new DomainInstance(nextId++, def, owner, level, center, radius, def.duration(owner));
        DOMAINS.computeIfAbsent(level, l -> new ArrayList<>()).add(d);
        if (spec != null) d.structure = dev.rick.jjk.core.domain.structure.DomainStructures.create(level, anchor, spec);
        Fx.play(level, "domain_expand", d.center, Vec3.ZERO, (float) d.radius, owner.getId());
        Fx.shake(level, d.center, d.radius * 2.5, 0.8f, 20);
        // Overlapping another live domain → clash.
        for (DomainInstance other : all(level)) {
            if (other == d || !other.isLive() || other.owner == owner || other.clashWith != null) continue;
            if (other.center.distanceTo(d.center) < other.radius + d.radius) {
                startClash(d, other);
                break;
            }
        }
        sync(d);
        return d;
    }

    public static void cancel(DomainInstance d, DomainInstance.EndReason reason) {
        if (d.phase == DomainInstance.Phase.COLLAPSING || d.phase == DomainInstance.Phase.ENDED) return;
        end(d, reason);
    }

    public static void tick(ServerLevel level) {
        List<DomainInstance> list = DOMAINS.get(level);
        if (list == null || list.isEmpty()) return;
        for (DomainInstance d : List.copyOf(list)) tickDomain(d);
        ClashManager.tick(level);
        list.removeIf(d -> d.phase == DomainInstance.Phase.ENDED);
    }

    private static void tickDomain(DomainInstance d) {
        d.age++;
        d.phaseAge++;
        if (d.phase != DomainInstance.Phase.COLLAPSING && ownerLost(d)) {
            end(d, DomainInstance.EndReason.OWNER_LOST);
        }
        switch (d.phase) {
            case FORMING -> {
                if (d.phaseAge >= d.definition.formingTicks()) {
                    if (d.clashWith != null) setPhase(d, DomainInstance.Phase.CLASHING);
                    else {
                        setPhase(d, DomainInstance.Phase.ACTIVE);
                        d.activated = true;
                        d.definition.onActivated(d);
                    }
                }
            }
            case ACTIVE -> tickActive(d);
            case CLASHING -> tickClash(d);
            case COLLAPSING -> {
                // Gameplay is already off and victims released; now give the world back.
                if (d.structure != null && d.phaseAge == Math.max(1, d.definition.collapseTicks() / 2)) {
                    dev.rick.jjk.core.domain.structure.DomainStructures.beginRestore(d.structure);
                }
                if (d.phaseAge >= d.definition.collapseTicks()) {
                    d.phase = DomainInstance.Phase.ENDED;
                    sendRemoved(d);
                }
            }
            default -> {}
        }
        if (d.age % 20 == 0 && d.phase != DomainInstance.Phase.ENDED) sync(d);
    }

    private static boolean ownerLost(DomainInstance d) {
        return !d.owner.isAlive() || d.owner.isRemoved() || d.owner.level() != d.level;
    }

    private static void tickActive(DomainInstance d) {
        d.activeAge++;
        if (!d.contains(d.owner)) {
            end(d, DomainInstance.EndReason.OWNER_LEFT);
            return;
        }
        double r = d.radius;
        AABB box = new AABB(d.center, d.center).inflate(r + 6);
        List<LivingEntity> nearby = d.level.getEntitiesOfClass(LivingEntity.class, box, e -> e != d.owner && e.isAlive());
        for (LivingEntity e : nearby) {
            double dist = e.getBoundingBox().getCenter().distanceTo(d.center);
            boolean inside = dist <= r;
            boolean victim = d.victims.containsKey(e.getUUID());
            if (inside && Targeting.canTarget(d.owner, e) && !protectedByOwnDomain(d, e)) {
                int t = d.victims.merge(e.getUUID(), 1, Integer::sum);
                Statuses.apply(e, CombatStatus.IN_DOMAIN, 5);
                d.definition.applySureHit(d, e, t);
                if (d.definition.closedBarrier() && JJKConfig.get().domain.closedBarrier && dist > r - 1.5) push(e, d.center, true);
            } else if (victim && !inside) {
                if (d.definition.closedBarrier() && JJKConfig.get().domain.closedBarrier && dist < r + 4) {
                    // The barrier holds: dragged back in.
                    push(e, d.center, true);
                } else {
                    release(d, e);
                }
            }
        }
        // Victims that vanished (died, logged out, teleported far away).
        Iterator<Map.Entry<UUID, Integer>> it = d.victims.entrySet().iterator();
        while (it.hasNext()) {
            Entity e = d.level.getEntity(it.next().getKey());
            if (!(e instanceof LivingEntity le) || !le.isAlive() || le.distanceToSqr(d.center) > (r + 6) * (r + 6)) {
                if (e instanceof LivingEntity le2 && le2.isAlive()) d.definition.onRelease(d, le2);
                it.remove();
            }
        }
        d.definition.onTick(d);
        if (d.activeAge >= d.duration) end(d, DomainInstance.EndReason.EXPIRED);
    }

    private static boolean protectedByOwnDomain(DomainInstance d, LivingEntity e) {
        for (DomainInstance other : all(d.level)) {
            if (other != d && other.owner == e && other.isLive() && other.contains(e)) return true;
        }
        return false;
    }

    private static void push(LivingEntity e, Vec3 center, boolean inward) {
        Vec3 d = center.subtract(e.getBoundingBox().getCenter());
        Vec3 flat = new Vec3(d.x, d.y * 0.5, d.z);
        if (flat.lengthSqr() < 1e-4) return;
        Motion.set(e, flat.normalize().scale(inward ? 0.6 : -0.6));
    }

    private static void release(DomainInstance d, LivingEntity e) {
        d.victims.remove(e.getUUID());
        d.definition.onRelease(d, e);
    }

    // --- Clash ---

    private static void startClash(DomainInstance a, DomainInstance b) {
        a.clashWith = b;
        b.clashWith = a;
        if (b.phase == DomainInstance.Phase.ACTIVE) setPhase(b, DomainInstance.Phase.CLASHING);
        // Both domains pause (no sure-hit) while their owners duel for control. The duel decides the winner.
        // Participants in expansion order (b was already standing).
        ClashSession session = ClashManager.start(b, a, DomainManager::clashFinished);
        a.clash = session;
        b.clash = session;
        Vec3 mid = a.center.add(b.center).scale(0.5);
        Fx.play(a.level, "domain_clash", mid, b.center.subtract(a.center), (float) Math.max(a.radius, b.radius), a.owner.getId());
    }

    private static void tickClash(DomainInstance d) {
        DomainInstance other = d.clashWith;
        if (other == null || !other.isLive()) {
            ClashManager.cancel(d.clash);
            d.clash = null;
            d.clashWith = null;
            setPhase(d, DomainInstance.Phase.ACTIVE);
        }
    }

    /** The duel is over: the winner overwhelms the loser, whose domain collapses and gives its blocks back. */
    private static void clashFinished(ClashSession s) {
        DomainInstance winner = s.winnerDomain();
        for (var p : s.participants()) {
            DomainInstance d = p.domain;
            d.clash = null;
            if (d == winner) continue;
            d.clashWith = null;
            if (winner != null) end(d, DomainInstance.EndReason.CLASH_LOST);
            else if (d.phase == DomainInstance.Phase.CLASHING) setPhase(d, DomainInstance.Phase.ACTIVE);
        }
        if (winner == null) return;
        winner.clashWith = null;
        if (winner.phase == DomainInstance.Phase.CLASHING || winner.phase == DomainInstance.Phase.FORMING) {
            setPhase(winner, DomainInstance.Phase.ACTIVE);
            // A domain that expanded straight into the clash activates now, for the first time.
            if (!winner.activated) {
                winner.activated = true;
                winner.definition.onActivated(winner);
            }
        }
        Fx.play(winner.level, "domain_clash_end", winner.center, Vec3.ZERO, (float) winner.radius, winner.owner.getId());
        Fx.play(winner.level, "clash_win", winner.owner.position().add(0, 1, 0), Vec3.ZERO, (float) winner.radius, winner.owner.getId());
        Fx.shake(winner.level, winner.center, winner.radius * 3, 1.0f, 20);
    }

    // --- Ending ---

    private static void end(DomainInstance d, DomainInstance.EndReason reason) {
        if (d.phase == DomainInstance.Phase.COLLAPSING || d.phase == DomainInstance.Phase.ENDED) return;
        d.endReason = reason;
        DomainInstance partner = d.clashWith;
        d.clashWith = null;
        if (d.clash != null && d.clash.phase() != ClashSession.Phase.ENDED) ClashManager.cancel(d.clash);
        d.clash = null;
        if (partner != null) {
            partner.clashWith = null;
            partner.clash = null;
            if (partner.phase == DomainInstance.Phase.CLASHING) setPhase(partner, DomainInstance.Phase.ACTIVE);
        }
        for (UUID id : List.copyOf(d.victims.keySet())) {
            if (d.level.getEntity(id) instanceof LivingEntity le) d.definition.onRelease(d, le);
        }
        d.victims.clear();
        d.definition.onCollapse(d, reason);
        if (d.owner.isAlive()) Statuses.apply(d.owner, CombatStatus.BURNOUT, JJKConfig.get().resources.domainBurnout);
        setPhase(d, DomainInstance.Phase.COLLAPSING);
        Fx.play(d.level, "domain_collapse", d.center, Vec3.ZERO, (float) d.radius, d.owner.getId());
    }

    private static void setPhase(DomainInstance d, DomainInstance.Phase phase) {
        d.phase = phase;
        d.phaseAge = 0;
        sync(d);
    }

    // --- Sync ---

    private static int phaseCode(DomainInstance d) {
        return switch (d.phase) {
            case FORMING -> DomainPayload.FORMING;
            case ACTIVE -> DomainPayload.ACTIVE;
            case CLASHING -> DomainPayload.CLASHING;
            case COLLAPSING -> DomainPayload.COLLAPSING;
            case ENDED -> DomainPayload.REMOVED;
        };
    }

    private static void sync(DomainInstance d) {
        DomainPayload p = new DomainPayload(d.id, d.owner.getId(), d.definition.id(), d.center, (float) d.radius, phaseCode(d),
                d.phase == DomainInstance.Phase.ACTIVE ? d.activeAge : d.phaseAge, d.duration, d.clashWith != null ? d.clashWith.id : -1);
        double range = d.radius + 96;
        for (ServerPlayer p2 : d.level.players()) if (p2.distanceToSqr(d.center) < range * range) ServerPlayNetworking.send(p2, p);
    }

    private static void sendRemoved(DomainInstance d) {
        DomainPayload p = new DomainPayload(d.id, d.owner.getId(), d.definition.id(), d.center, (float) d.radius, DomainPayload.REMOVED, 0, 0, -1);
        for (ServerPlayer p2 : d.level.players()) ServerPlayNetworking.send(p2, p);
    }

    /** Collapse everything owned by an entity (disconnect, death, dimension change). */
    public static void collapseOwnedBy(LivingEntity owner, DomainInstance.EndReason reason) {
        for (List<DomainInstance> list : DOMAINS.values()) {
            for (DomainInstance d : List.copyOf(list)) if (d.owner == owner) cancel(d, reason);
        }
    }

    public static void clear(ServerLevel level) {
        DOMAINS.remove(level);
    }

    public static void clearAll() {
        DOMAINS.clear();
    }
}
