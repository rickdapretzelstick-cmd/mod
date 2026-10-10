package dev.rick.jjk.progression.mastery;

import dev.rick.jjk.JJK;
import dev.rick.jjk.core.net.MasterySyncPayload;
import dev.rick.jjk.progression.KitOwnership;
import dev.rick.jjk.progression.TechniqueProgression;
import dev.rick.jjk.registry.ModAttachments;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Mastery: what a player has developed, and what that changes. Two kinds of tree ({@link MasteryTree}): a technique's
 * (only for the player who legitimately owns that kit) and a cursed tool's (anyone who fights with it). The server is the
 * only authority: it awards Mastery ({@link #award}), checks and applies purchases ({@link #purchase}) and syncs the
 * player's record ({@link MasterySyncPayload}).
 *
 * <p>Abilities ask two questions, with no knowledge of trees or characters:
 * <ul>
 *   <li>{@link #unlocked}: is a gated behaviour (an R variant, a follow-up, the awakening) available? A governed Survival
 *   player has it once a bought node grants the key. Everyone else (Creative's sandbox, progression switched off, any
 *   non-player such as a training dummy or a curse) has everything, so testing and bots fight with the full kit.</li>
 *   <li>{@link #param}: the multiplier on one of a move's numbers ({@code gojo.blue.range}): the product of every bought
 *   node's {@code mul} for that key, plus their {@code add}s. 1.0 for anyone with none (bots fight with the base
 *   numbers).</li>
 * </ul>
 */
public final class Mastery {
    /** Bought effects per player (recomputed when their record changes). */
    private static final Map<UUID, Effective> CACHE = new HashMap<>();

    private record Effective(Set<String> unlocks, Map<String, Double> mul, Map<String, Double> add) {}

    public enum Result { OK, UNKNOWN, NOT_YOURS, OWNED, NEEDS, POINTS, FAILED, DISABLED }

    /** Whether the Mastery trees are in play at all ({@code JJKConfig.mastery.enabled}). */
    public static boolean enabled() {
        return dev.rick.jjk.config.JJKConfig.get().mastery.enabled;
    }

    private Mastery() {}

    public static void init() {
        MasteryTrees.load();
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sync(handler.player));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> CACHE.clear());
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register((old, now, alive) -> {
            CACHE.remove(now.getUUID());
            sync(now);
        });
    }

    // --- The record ---

    public static MasteryData data(ServerPlayer p) {
        MasteryData d = p.getAttached(ModAttachments.MASTERY);
        return d == null ? MasteryData.EMPTY : d;
    }

    public static void set(ServerPlayer p, MasteryData d) {
        p.setAttached(ModAttachments.MASTERY, d);
        CACHE.remove(p.getUUID());
        sync(p);
    }

    // --- The questions abilities ask ---

    /** Whether Mastery gates this entity at all (a Survival player under progression). */
    public static boolean gated(@Nullable LivingEntity e) {
        return e instanceof ServerPlayer p && TechniqueProgression.governs(p);
    }

    /**
     * Whether a player's technique is gated: in Survival, playing the kit they legitimately own. A Creative test kit
     * carried into Survival is a test, not progression: it plays in full (and earns nothing).
     */
    public static boolean techniqueGated(@Nullable LivingEntity e) {
        if (!(e instanceof ServerPlayer p) || !TechniqueProgression.governs(p)) return false;
        var legit = TechniqueProgression.legitimateCharacter(p);
        var caster = dev.rick.jjk.core.ability.Casters.getOrNull(p);
        return legit != null && caster != null && caster.character() == legit;
    }

    /**
     * Whether a gated behaviour is available to {@code e}: a cursed tool's ({@code tool.<id>.<name>}) for any governed
     * Survival player who bought its node; a technique's when {@link #techniqueGated}.
     *
     * <p>Techniques are no longer developed through Mastery. A character's base kit, earned through its storyline, comes
     * whole (every move and every R variant); only the Awakening ({@code <kit>.awakening}) stays closed, until that
     * character's own later storyline opens it ({@link TechniqueProgression#awakened}). An Awakening node bought under
     * the old tree still counts, so no existing save loses it.
     */
    public static boolean unlocked(@Nullable LivingEntity e, String key) {
        boolean tool = key.startsWith("tool.");
        // With the trees off, every tool comes whole.
        if (tool && !enabled()) return true;
        if (tool ? !gated(e) : !techniqueGated(e)) return true;
        ServerPlayer p = (ServerPlayer) e;
        if (tool) return effective(p).unlocks.contains(key);
        if (!key.endsWith(".awakening")) return true;
        return TechniqueProgression.awakened(p, key.substring(0, key.length() - ".awakening".length())) || effective(p).unlocks.contains(key);
    }

    /** The multiplier on a move's number for {@code e} (1.0 untouched). */
    public static double param(@Nullable LivingEntity e, String key) {
        if (!(e instanceof ServerPlayer p)) return 1.0;
        if (!enabled()) return 1.0;
        Effective eff = effective(p);
        return eff.mul.getOrDefault(key, 1.0) + eff.add.getOrDefault(key, 0.0);
    }

    /** {@code base} scaled by {@link #param}. */
    public static double scaled(@Nullable LivingEntity e, String key, double base) {
        return base * param(e, key);
    }

    private static Effective effective(ServerPlayer p) {
        return CACHE.computeIfAbsent(p.getUUID(), id -> compute(data(p)));
    }

    static Effective compute(MasteryData d) {
        Set<String> unlocks = new HashSet<>();
        Map<String, Double> mul = new HashMap<>(), add = new HashMap<>();
        d.bought().forEach((treeId, nodes) -> {
            MasteryTree t = MasteryTrees.get(treeId);
            if (t == null) return;
            for (String id : nodes) {
                MasteryNode n = t.node(id);
                if (n == null) continue;
                for (MasteryNode.Effect f : n.effects()) {
                    switch (f.op()) {
                        case UNLOCK -> unlocks.add(f.key());
                        case MUL -> mul.merge(f.key(), f.value(), (a, b) -> a * b);
                        case ADD -> add.merge(f.key(), f.value(), Double::sum);
                    }
                }
            }
        });
        return new Effective(unlocks, mul, add);
    }

    // --- Earning and spending ---

    /**
     * Whether {@code p} may develop this tree: cursed tool trees only. The technique trees are retired (character
     * progression comes from story events and accomplishments now); their data stays for reference and old saves.
     */
    public static boolean mayDevelop(ServerPlayer p, MasteryTree t) {
        return t.kind() == MasteryTree.Kind.TOOL;
    }

    /** Adds Mastery to a tree (the server's reward paths call this; never the client). */
    public static void award(ServerPlayer p, String treeId, int amount) {
        if (amount <= 0 || !enabled()) return;
        MasteryTree t = MasteryTrees.get(treeId);
        if (t == null || !mayDevelop(p, t)) return;
        set(p, data(p).earn(treeId, amount));
    }

    /** Buys a node, if the player may: their tree, not owned yet, every prerequisite owned, enough Mastery. */
    public static Result purchase(ServerPlayer p, String treeId, String nodeId) {
        if (!enabled()) return Result.DISABLED;
        MasteryTree t = MasteryTrees.get(treeId);
        if (t == null) return Result.UNKNOWN;
        MasteryNode n = t.node(nodeId);
        if (n == null) return Result.UNKNOWN;
        if (!mayDevelop(p, t)) return Result.NOT_YOURS;
        MasteryData d = data(p);
        if (d.has(treeId, nodeId)) return Result.OWNED;
        for (String r : n.requires()) if (!d.has(treeId, r)) return Result.NEEDS;
        int cost = cost(n);
        if (d.points(treeId) < cost) return Result.POINTS;
        set(p, d.buy(treeId, nodeId, cost));
        JJK.LOGGER.info("{} bought {} in {} ({} Mastery)", p.getName().getString(), nodeId, treeId, n.cost());
        return Result.OK;
    }

    /** A node's price: its tree's cost under the config's multiplier. */
    public static int cost(MasteryNode n) {
        return (int) Math.max(0, Math.round(n.cost() * dev.rick.jjk.config.JJKConfig.get().mastery.costMultiplier));
    }

    /** Refunds everything bought in a tree (admin). */
    public static void respec(ServerPlayer p, String treeId) {
        MasteryTree t = MasteryTrees.get(treeId);
        MasteryData d = data(p);
        int refund = 0;
        if (t != null) for (String id : d.bought(treeId)) {
            MasteryNode n = t.node(id);
            if (n != null) refund += cost(n);
        }
        set(p, d.respec(treeId, refund));
    }

    public static void sync(ServerPlayer p) {
        String kit = TechniqueProgression.legitimateCharacter(p) == null ? "" : TechniqueProgression.legitimateCharacter(p).id;
        ServerPlayNetworking.send(p, new MasterySyncPayload(data(p), kit, techniqueGated(p), enabled()));
    }
}
