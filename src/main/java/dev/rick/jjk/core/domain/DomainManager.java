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
 *   <li>The owner may walk out and back in: the domain stays open until it expires, is cancelled or loses a clash;
 *   the owner dying or leaving the dimension collapses it.</li>
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
        if (spec != null) d.structure = dev.rick.jjk.core.domain.structure.DomainStructures.create(level, anchor, spec, def.formingTicks());
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
        tickFronts(list);
        list.removeIf(d -> d.phase == DomainInstance.Phase.ENDED);
    }

    private static void tickDomain(DomainInstance d) {
        d.age++;
        d.phaseAge++;
        if (d.phase != DomainInstance.Phase.COLLAPSING && ownerLost(d)) {
            end(d, DomainInstance.EndReason.OWNER_LOST);
        }
        // No domain lasts forever: past its ceiling (forming + duration + a clash allowance, never more than the
        // configured maximum) it expires, whatever phase it is stuck in.
        if (d.phase != DomainInstance.Phase.COLLAPSING && d.phase != DomainInstance.Phase.ENDED && d.age >= lifetimeLimit(d)) {
            dev.rick.jjk.JJK.LOGGER.warn("Domain {} ({}) reached its lifetime ceiling in phase {}: expiring it", d.id, d.definition.displayName(), d.phase);
            end(d, DomainInstance.EndReason.EXPIRED);
            if (d.phase != DomainInstance.Phase.COLLAPSING && d.phase != DomainInstance.Phase.ENDED) {
                // A conquest hand-off left it standing: collapse it directly.
                d.endReason = DomainInstance.EndReason.EXPIRED;
                setPhase(d, DomainInstance.Phase.COLLAPSING);
            }
        }
        switch (d.phase) {
            case FORMING -> {
                if (d.phaseAge >= d.definition.formingTicks()) {
                    // The last sections connect: a pulse runs through the whole domain.
                    Fx.play(d.level, "domain_sealed", d.center, Vec3.ZERO, (float) d.radius, d.owner.getId());
                    Fx.shake(d.level, d.center, d.radius * 2.5, 0.7f, 16);
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
                if (d.phaseAge == Math.max(1, d.definition.collapseTicks() / 2)) {
                    if (d.structure != null) dev.rick.jjk.core.domain.structure.DomainStructures.beginRestore(d.structure);
                    // Conquered territory goes back with the domain that took it.
                    for (DomainInstance.Annex a : d.annexes) {
                        if (a.structure() != null) dev.rick.jjk.core.domain.structure.DomainStructures.beginRestore(a.structure());
                    }
                }
                if (d.phaseAge >= Math.min(Math.max(1, d.definition.collapseTicks()), COLLAPSE_CEILING)) {
                    d.phase = DomainInstance.Phase.ENDED;
                    sendRemoved(d);
                }
            }
            default -> {}
        }
        if (d.age % 20 == 0 && d.phase != DomainInstance.Phase.ENDED) sync(d);
    }

    /** Longest a collapse may take to give the world back (ticks). */
    static final int COLLAPSE_CEILING = 400;

    /** The tick of its life at which a domain expires no matter what. */
    public static int lifetimeLimit(DomainInstance d) {
        JJKConfig.Domain cfg = JJKConfig.get().domain;
        int max = Math.max(200, cfg.maxLifetimeTicks);
        long limit = (long) Math.max(0, d.definition.formingTicks()) + Math.max(20, d.duration) + Math.max(0, cfg.clashAllowanceTicks);
        return (int) Math.min(max, limit);
    }

    private static boolean ownerLost(DomainInstance d) {
        return !d.owner.isAlive() || d.owner.isRemoved() || d.owner.level() != d.level;
    }

    private static void tickActive(DomainInstance d) {
        d.activeAge++;
        // Where the owner (or anyone) stands doesn't decide the domain's life: it stays open, its structure and its
        // effects with it, until it expires, is cancelled, loses a clash, or its owner dies or leaves the dimension.
        // Its sure-hit only ever applies to those inside it (below).
        AABB box = new AABB(d.center, d.center).inflate(d.radius + 6);
        for (DomainInstance.Annex a : d.annexes) box = box.minmax(new AABB(a.center(), a.center()).inflate(a.radius() + 6));
        List<LivingEntity> nearby = d.level.getEntitiesOfClass(LivingEntity.class, box, e -> e != d.owner && e.isAlive());
        for (LivingEntity e : nearby) {
            Vec3 at = e.getBoundingBox().getCenter();
            // How far inside the domain's space (its own sphere, or territory it conquered) the target is.
            double depth = d.depth(at);
            boolean inside = depth >= 0;
            boolean victim = d.victims.containsKey(e.getUUID());
            if (inside && Targeting.canTarget(d.owner, e) && !protectedByOwnDomain(d, e)) {
                int t = d.victims.merge(e.getUUID(), 1, Integer::sum);
                Statuses.apply(e, CombatStatus.IN_DOMAIN, 5);
                d.definition.applySureHit(d, e, t);
                if (d.definition.closedBarrier() && JJKConfig.get().domain.closedBarrier && depth < 1.5) push(e, d.anchor(at), true);
            } else if (victim && !inside) {
                if (d.definition.closedBarrier() && JJKConfig.get().domain.closedBarrier && depth > -4) {
                    // The barrier holds: dragged back in.
                    push(e, d.anchor(at), true);
                } else {
                    release(d, e);
                }
            }
        }
        // Victims that vanished (died, logged out, teleported far away).
        Iterator<Map.Entry<UUID, Integer>> it = d.victims.entrySet().iterator();
        while (it.hasNext()) {
            Entity e = d.level.getEntity(it.next().getKey());
            if (!(e instanceof LivingEntity le) || !le.isAlive() || d.depth(le.getBoundingBox().getCenter()) < -6) {
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
        // Both interiors, side by side: the space is split between the two owners (participant 0 is the meter's + side).
        var territory = dev.rick.jjk.core.domain.structure.ClashTerritory.of(b.structure, a.structure);
        if (territory != null) {
            ClashFront front = new ClashFront(b, a, territory);
            a.front = front;
            b.front = front;
        }
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

    /** Moves every split boundary: with the duel's meter, or across the loser's space once the duel is decided. */
    private static void tickFronts(List<DomainInstance> list) {
        java.util.Set<ClashFront> seen = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (DomainInstance d : List.copyOf(list)) {
            ClashFront f = d.front;
            if (f == null || !seen.add(f)) continue;
            if (f.conquering()) {
                if (f.conquer()) finishConquest(f, DomainInstance.EndReason.CLASH_LOST);
            } else {
                ClashSession session = f.a.clash != null ? f.a.clash : f.b.clash;
                if (session != null) f.follow(session.meter());
            }
            if (f.a.front == f && f.a.age % 2 == 0) {
                sync(f.a);
                sync(f.b);
            }
        }
    }

    /**
     * The winner has taken the whole space: the loser's domain ends, and its blocks (already in the winner's materials)
     * become the winner's territory until the winner's own domain ends.
     */
    private static void finishConquest(ClashFront f, DomainInstance.EndReason reason) {
        DomainInstance winner = f.winner, loser = f.other(winner);
        if (f.territory.front() != f.t) f.territory.paint(f.t);
        f.a.front = null;
        f.b.front = null;
        winner.annexes.add(new DomainInstance.Annex(loser.center, loser.radius, loser.structure));
        loser.structure = null;
        loser.consumedBy = winner;
        end(loser, reason);
        crown(winner);
        sync(winner);
    }

    /** A split that is called off (someone left mid-duel): both sides go back to their own design. */
    private static void releaseFront(ClashFront f) {
        f.a.front = null;
        f.b.front = null;
        f.territory.release();
    }

    /** The duel is over: the winner overwhelms the loser, whose domain collapses and gives its blocks back. */
    private static void clashFinished(ClashSession s) {
        DomainInstance winner = s.winnerDomain();
        ClashFront front = winner != null ? winner.front : null;
        if (front != null && front.other(winner).isLive()) {
            // The winner's side now sweeps across and consumes the loser's (see tickFronts); the loser ends after.
            for (var p : s.participants()) p.domain.clash = null;
            front.beginConquest(winner);
            Fx.play(winner.level, "clash_win", winner.owner.position().add(0, 1, 0), Vec3.ZERO, (float) winner.radius, winner.owner.getId());
            return;
        }
        for (var p : s.participants()) {
            if (p.domain.front != null) releaseFront(p.domain.front);
        }
        for (var p : s.participants()) {
            DomainInstance d = p.domain;
            d.clash = null;
            if (d == winner) continue;
            d.clashWith = null;
            if (winner != null) end(d, DomainInstance.EndReason.CLASH_LOST);
            else if (d.phase == DomainInstance.Phase.CLASHING) setPhase(d, DomainInstance.Phase.ACTIVE);
        }
        if (winner == null) return;
        crown(winner);
        Fx.play(winner.level, "clash_win", winner.owner.position().add(0, 1, 0), Vec3.ZERO, (float) winner.radius, winner.owner.getId());
    }

    /** The clash is won: the winner's domain takes over and runs by its normal rules. */
    private static void crown(DomainInstance winner) {
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
        Fx.shake(winner.level, winner.center, winner.radius * 3, 1.0f, 20);
    }

    // --- Ending ---

    private static void end(DomainInstance d, DomainInstance.EndReason reason) {
        if (d.phase == DomainInstance.Phase.COLLAPSING || d.phase == DomainInstance.Phase.ENDED) return;
        if (d.front != null) {
            ClashFront f = d.front;
            // The loser dropping out mid-conquest just hands the winner the rest; anything else calls the split off.
            if (f.conquering() && f.winner != d) {
                f.t = f.winner == f.a ? Math.max(f.t, 1.5) : Math.min(f.t, -0.5);
                finishConquest(f, reason);
                return;
            }
            releaseFront(f);
        }
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
        if (d.owner.isAlive() && d.definition.burnoutOnCollapse(d, reason)) Statuses.apply(d.owner, CombatStatus.BURNOUT, JJKConfig.get().resources.domainBurnout);
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
        // The split: how far toward the other domain this one's side reaches (CONSUMED: nothing of it is left).
        float split = 0;
        int splitWith = -1;
        if (d.front != null) {
            split = (float) d.front.splitFor(d);
            splitWith = d.front.other(d).id;
        } else if (d.consumedBy != null) {
            split = DomainPayload.CONSUMED;
            splitWith = d.consumedBy.id;
        }
        float[] annex = new float[d.annexes.size() * 4];
        for (int i = 0; i < d.annexes.size(); i++) {
            DomainInstance.Annex a = d.annexes.get(i);
            annex[i * 4] = (float) a.center().x;
            annex[i * 4 + 1] = (float) a.center().y;
            annex[i * 4 + 2] = (float) a.center().z;
            annex[i * 4 + 3] = (float) a.radius();
        }
        DomainPayload p = new DomainPayload(d.id, d.owner.getId(), d.definition.id(), d.center, (float) d.radius, phaseCode(d),
                d.phase == DomainInstance.Phase.ACTIVE ? d.activeAge : d.phaseAge, d.duration, d.clashWith != null ? d.clashWith.id : -1,
                d.definition.formingTicks(), d.structure != null ? d.structure.spec.thickness() : 0, split, splitWith, annex);
        double range = d.radius + 96;
        for (ServerPlayer p2 : d.level.players()) if (p2.distanceToSqr(d.center) < range * range) ServerPlayNetworking.send(p2, p);
    }

    private static void sendRemoved(DomainInstance d) {
        DomainPayload p = new DomainPayload(d.id, d.owner.getId(), d.definition.id(), d.center, (float) d.radius, DomainPayload.REMOVED, 0, 0, -1, 1, 0, 0, -1, new float[0]);
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
