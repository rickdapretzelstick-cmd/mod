package dev.rick.jjk.progression;

import dev.rick.jjk.JJK;
import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.character.CharacterService;
import dev.rick.jjk.core.character.Characters;
import dev.rick.jjk.core.character.JJKCharacter;
import dev.rick.jjk.core.net.ProgressionPayload;
import dev.rick.jjk.registry.ModAttachments;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Survival progression: what technique a player legitimately has.
 *
 * <ul>
 *   <li><b>Survival</b> (and Adventure) players start as ordinary people. Their kit is only ever one they permanently
 *   acquired in the world ({@link PlayerProgression}, validated against {@link KitOwnership}); the character select
 *   screen can't hand them one.</li>
 *   <li><b>Creative</b> is the sandbox: the K menu picks any character, even one someone else owns. That pick is a
 *   <i>test kit</i>, never ownership: it stays when the player goes back to Survival (and through death and relogging)
 *   so they can test it there, until they pick something else in Creative, choose one of their own kits, or earn a
 *   kit. Survival alone can never swap: there the K menu only switches between kits the player owns.</li>
 * </ul>
 *
 * Kits are earned through {@link #acquire} (or {@link #tryClaimKit} directly), which every acquisition path calls with
 * its own {@link KitAcquisition} describing what happens when the kit is free, already theirs, or someone else's.
 * The server decides everything; clients only receive {@link ProgressionPayload} to shape their screens.
 */
public final class TechniqueProgression {
    public static final String NOT_AWAKENED = "You have not awakened a cursed technique.";

    /** Last game mode class seen per player (true = Creative), to catch the switch back into Survival. */
    private static final Map<UUID, Boolean> WAS_SANDBOX = new HashMap<>();
    /** Last state sent to each player, so sync only goes out when something changed. */
    private static final Map<UUID, ProgressionPayload> SENT = new HashMap<>();
    /** Consequences that must land once the current action has finished (end of this server tick). */
    private static final List<Runnable> END_OF_TICK = new java.util.ArrayList<>();

    private TechniqueProgression() {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(TechniqueProgression::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            END_OF_TICK.clear();
            WAS_SANDBOX.clear();
            SENT.clear();
            KitOwnership.unload();
        });
    }

    // --- Rules ---

    public static boolean enabled() {
        return JJKConfig.get().progression.enabled;
    }

    /** Creative: the testing sandbox where any kit can be picked and nothing counts as ownership. */
    public static boolean isSandbox(ServerPlayer player) {
        // The game mode itself, not isCreative() (26.3 answers that from the player's abilities, which can disagree).
        return player.gameMode.getGameModeForPlayer() == net.minecraft.world.level.GameType.CREATIVE;
    }

    /** Whether Survival progression decides this player's kit right now (progression on, not in the sandbox). */
    public static boolean governs(ServerPlayer player) {
        return enabled() && !isSandbox(player);
    }

    public static KitOwnership ownership(ServerPlayer player) {
        return KitOwnership.get(player.level().getServer());
    }

    public static PlayerProgression progression(ServerPlayer player) {
        PlayerProgression p = player.getAttached(ModAttachments.PROGRESSION);
        return p == null ? PlayerProgression.EMPTY : p;
    }

    static void setProgression(ServerPlayer player, PlayerProgression p) {
        player.setAttached(ModAttachments.PROGRESSION, p);
    }

    /** The kit this player legitimately plays in Survival, or null: their current kit, if the world agrees they own it. */
    @Nullable
    public static JJKCharacter legitimateCharacter(ServerPlayer player) {
        String kit = progression(player).kit();
        if (kit.isEmpty() || !ownership(player).isOwner(kit, player.getUUID())) return null;
        return Characters.get(kit);
    }

    /**
     * What the player plays in Survival: the test kit they last picked in Creative if any (never ownership), otherwise
     * their legitimate kit, or nothing.
     */
    @Nullable
    public static JJKCharacter playableCharacter(ServerPlayer player) {
        String test = progression(player).testKit();
        JJKCharacter t = test.isEmpty() ? null : Characters.get(test);
        return t != null ? t : legitimateCharacter(player);
    }

    /**
     * Why the character select screen can't switch this player to {@code id}, or null if it can. In the sandbox anything
     * goes; under progression only kits the player owns, and with none owned the screen has nothing to give.
     */
    @Nullable
    public static String selectionBlocked(ServerPlayer player, String id) {
        if (!governs(player)) return null;
        PlayerProgression p = validate(player);
        if (p.kits().isEmpty()) return NOT_AWAKENED;
        if (id.isEmpty() || !p.owns(id)) return "That technique isn't yours.";
        return null;
    }

    /** A governed player switched between kits they own on the select screen: that becomes their Survival kit. */
    public static void onSelected(ServerPlayer player, String id) {
        if (!enabled()) return;
        PlayerProgression p = progression(player);
        if (isSandbox(player)) {
            // A Creative pick is the test kit carried back into Survival (picking nothing clears it).
            if (!p.testKit().equals(id)) setProgression(player, p.withTestKit(id));
            sync(player, true);
            return;
        }
        if (id.isEmpty()) return;
        // Choosing one of their own kits in Survival puts the test kit away.
        if (p.owns(id) && (!p.kit().equals(id) || !p.testKit().isEmpty())) setProgression(player, p.withCurrent(id).withTestKit(""));
        sync(player, true);
    }

    // --- Join, respawn and game mode changes ---

    /**
     * Brings the player's own record in line with the world's: a kit the registry gives someone else is dropped (copied
     * or migrated player data can't make a second owner), and a kit the registry says is theirs is restored.
     */
    public static PlayerProgression validate(ServerPlayer player) {
        PlayerProgression p = progression(player);
        KitOwnership own = ownership(player);
        UUID me = player.getUUID();
        PlayerProgression fixed = p;
        for (String kit : p.kits()) {
            if (!own.isOwner(kit, me)) {
                JJK.LOGGER.warn("{} listed kit {} that the world gives to {}; dropped it", player.getName().getString(), kit,
                        own.owner(kit) == null ? "nobody" : own.owner(kit).uuid());
                fixed = fixed.withoutKit(kit);
            }
        }
        for (String kit : own.kitsOwnedBy(me)) if (!fixed.owns(kit)) fixed = fixed.withKit(kit);
        if (!fixed.kit().isEmpty() && !fixed.owns(fixed.kit())) fixed = fixed.withCurrent(fixed.kits().isEmpty() ? "" : fixed.kits().get(0));
        if (!fixed.equals(p)) setProgression(player, fixed);
        return fixed;
    }

    /**
     * Called by {@link CharacterService} when a governed player joins or respawns: they get their legitimate kit, or
     * nothing (a Creative pick saved before they logged out is not theirs).
     */
    public static void restore(ServerPlayer player) {
        validate(player);
        CharacterService.assign(player, playableCharacter(player));
        WAS_SANDBOX.put(player.getUUID(), isSandbox(player));
        sync(player, true);
    }

    /** Puts a governed player back on their legitimate kit if they are on anything else. */
    public static void enforce(ServerPlayer player) {
        if (!governs(player)) return;
        validate(player);
        JJKCharacter playable = playableCharacter(player);
        AbilityCaster c = Casters.get(player);
        if (c.character() != playable) CharacterService.assign(player, playable);
    }

    /** Runs {@code r} at the end of this server tick, after whatever is happening now (eating, using) has completed. */
    public static void endOfTick(Runnable r) {
        END_OF_TICK.add(r);
    }

    private static void tick(MinecraftServer server) {
        if (!END_OF_TICK.isEmpty()) {
            List<Runnable> due = List.copyOf(END_OF_TICK);
            END_OF_TICK.clear();
            for (Runnable r : due) {
                try {
                    r.run();
                } catch (RuntimeException e) {
                    JJK.LOGGER.error("Progression consequence failed", e);
                }
            }
        }
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        for (ServerPlayer p : players) {
            boolean sandbox = isSandbox(p);
            Boolean was = WAS_SANDBOX.put(p.getUUID(), sandbox);
            // Out of Creative: they keep playing the test kit they picked there (or their legitimate kit, or none).
            if (was != null && was && !sandbox) enforce(p);
            sync(p, false);
        }
        if (WAS_SANDBOX.size() > players.size() * 2 + 8) {
            java.util.Set<UUID> online = new java.util.HashSet<>();
            for (ServerPlayer p : players) online.add(p.getUUID());
            WAS_SANDBOX.keySet().retainAll(online);
            SENT.keySet().retainAll(online);
        }
    }

    // --- Acquisition ---

    /**
     * The one way a kit is earned. Validates the player, then claims the kit atomically in {@link KitOwnership}, records
     * it on the player, gives them the kit in Survival and syncs. In the sandbox nothing is claimed.
     */
    public static KitOwnership.ClaimResult tryClaimKit(ServerPlayer player, String kit, String source) {
        if (kit.isEmpty() || !player.isAlive() || player.isSpectator()) return KitOwnership.ClaimResult.FAILED;
        KitOwnership.ClaimResult r = ownership(player).tryClaim(player.getUUID(), player.getName().getString(), kit, source);
        if (r == KitOwnership.ClaimResult.CLAIMED || r == KitOwnership.ClaimResult.ALREADY_OWNER) {
            PlayerProgression p = progression(player);
            // An earned kit replaces any test kit at once.
            PlayerProgression next = r == KitOwnership.ClaimResult.CLAIMED ? p.withKit(kit).withCurrent(kit).withTestKit("") : p.withKit(kit);
            if (!next.equals(p)) setProgression(player, next);
            if (r == KitOwnership.ClaimResult.CLAIMED && governs(player)) CharacterService.assign(player, Characters.get(kit));
            sync(player, true);
        }
        return r;
    }

    /**
     * Runs an acquisition (eating a cursed object, a ritual...): the sandbox gets its own handling, otherwise the claim is
     * made and the acquisition's outcome for that result runs.
     */
    public static KitOwnership.ClaimResult acquire(ServerPlayer player, KitAcquisition acquisition) {
        if (isSandbox(player)) {
            acquisition.onSandbox(player);
            return KitOwnership.ClaimResult.FAILED;
        }
        String kit = acquisition.kit();
        // The owner using the same path again is its own case: never a second claim, never the stranger's fate.
        if (ownership(player).isOwner(kit, player.getUUID())) {
            validate(player);
            acquisition.onAlreadyOwned(player);
            return KitOwnership.ClaimResult.ALREADY_OWNER;
        }
        KitOwnership.ClaimResult r = tryClaimKit(player, kit, acquisition.id());
        switch (r) {
            case CLAIMED -> acquisition.onClaimed(player);
            case ALREADY_OWNER -> acquisition.onAlreadyOwned(player);
            case TAKEN -> acquisition.onTaken(player, ownership(player).owner(kit));
            case FAILED -> acquisition.onFailed(player);
        }
        return r;
    }

    /** Adds to one of the player's progression counters (an acquisition path's own bookkeeping). */
    public static int addCounter(ServerPlayer player, String name, int by) {
        PlayerProgression p = progression(player).addCounter(name, by);
        setProgression(player, p);
        return p.counter(name);
    }

    // --- Administration (/jjk kit) ---

    /** Releases a kit from whoever owns it; an online owner loses it at once. */
    @Nullable
    public static KitOwnership.Owner release(MinecraftServer server, String kit) {
        KitOwnership.Owner old = KitOwnership.get(server).release(kit);
        if (old != null) {
            ServerPlayer p = server.getPlayerList().getPlayer(old.uuid());
            if (p != null) {
                validate(p);
                enforce(p);
                sync(p, true);
            }
        }
        return old;
    }

    /** Gives a kit to a player, taking it from anyone who had it. */
    public static boolean transfer(MinecraftServer server, String kit, ServerPlayer to) {
        KitOwnership.Owner old = KitOwnership.get(server).owner(kit);
        if (!KitOwnership.get(server).transfer(kit, to.getUUID(), to.getName().getString(), "admin")) return false;
        if (old != null && !old.uuid().equals(to.getUUID())) {
            ServerPlayer prev = server.getPlayerList().getPlayer(old.uuid());
            if (prev != null) {
                validate(prev);
                enforce(prev);
                sync(prev, true);
            }
        }
        setProgression(to, validate(to).withCurrent(kit).withTestKit(""));
        enforce(to);
        sync(to, true);
        return true;
    }

    // --- Client sync ---

    /** Sends the player what their screens need (only when it changed, unless forced). */
    public static void sync(ServerPlayer player, boolean force) {
        PlayerProgression p = progression(player);
        ProgressionPayload payload = new ProgressionPayload(governs(player), p.kit(), p.kits(), CursePerception.canPerceive(player));
        ProgressionPayload last = SENT.get(player.getUUID());
        if (!force && payload.equals(last)) return;
        SENT.put(player.getUUID(), payload);
        ServerPlayNetworking.send(player, payload);
        // What Mastery shows follows the kit (the technique tab is the legitimately owned kit's).
        dev.rick.jjk.progression.mastery.Mastery.sync(player);
    }
}
