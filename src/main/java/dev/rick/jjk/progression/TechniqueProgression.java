package dev.rick.jjk.progression;

import dev.rick.jjk.JJK;
import dev.rick.jjk.config.JJKConfig;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Survival progression: what technique a player legitimately has.
 *
 * <ul>
 *   <li><b>Survival</b> (and Adventure) players start as ordinary people. Their kit is only ever one they permanently
 *   acquired in the world ({@link PlayerProgression}, checked against {@link KitOwnership}); the character select
 *   screen can't hand them one.</li>
 *   <li><b>Creative</b> is the sandbox: the K screen picks any character, even one someone else owns. That pick is
 *   temporary and never ownership; leaving Creative puts back the player's legitimate kit (or none).</li>
 * </ul>
 *
 * Kits are earned through {@link #acquire}, which every acquisition path calls with its own {@link KitAcquisition}
 * describing what happens when the kit is free, already theirs, or someone else's. The server decides everything;
 * clients only get {@link ProgressionPayload} to shape their screens.
 */
public final class TechniqueProgression {
    public static final String NOT_AWAKENED = "You have not awakened a cursed technique.";

    /** Last game mode class seen per player (true = Creative), to catch the switch back into Survival. */
    private static final Map<UUID, Boolean> WAS_SANDBOX = new HashMap<>();
    /** Last state sent to each player, so sync only goes out when something changed. */
    private static final Map<UUID, ProgressionPayload> SENT = new HashMap<>();
    /** Consequences that must land after the current action has finished (the end of this level's tick). */
    private static final List<Runnable> END_OF_TICK = new ArrayList<>();
    /** Test hook: players treated as Creative (true) or Survival (false) whatever their game mode. */
    private static final Map<UUID, Boolean> SANDBOX_OVERRIDE = new HashMap<>();

    private TechniqueProgression() {}

    public static void init() {
        ServerTickEvents.END_LEVEL_TICK.register(TechniqueProgression::tick);
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
        Boolean forced = SANDBOX_OVERRIDE.get(player.getUUID());
        return forced != null ? forced : player.isCreative();
    }

    /** Whether Survival progression decides this player's kit right now (progression on, not in the sandbox). */
    public static boolean governs(ServerPlayer player) {
        return enabled() && !isSandbox(player);
    }

    public static KitOwnership ownership(ServerPlayer player) {
        ServerLevel level = player.level();
        return KitOwnership.get(level.getServer());
    }

    public static PlayerProgression progression(ServerPlayer player) {
        return PlayerProgression.decode(player.getAttached(ModAttachments.PROGRESSION));
    }

    static void setProgression(ServerPlayer player, PlayerProgression p) {
        player.setAttached(ModAttachments.PROGRESSION, p.encode());
    }

    /** The kit this player legitimately plays in Survival, or null: their current kit, if the world agrees they own it. */
    @Nullable
    public static JJKCharacter legitimateCharacter(ServerPlayer player) {
        String kit = progression(player).kit();
        if (kit.isEmpty() || !ownership(player).isOwner(kit, player.getUUID())) return null;
        return Characters.get(kit);
    }

    /**
     * Why the character select screen can't switch this player to {@code id}, or null if it can. In the sandbox anything
     * goes; under progression only kits the player owns, and with none owned there is nothing to give.
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
        if (!governs(player) || id.isEmpty()) return;
        PlayerProgression p = progression(player);
        if (p.owns(id) && !p.kit().equals(id)) setProgression(player, p.withCurrent(id));
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
                JJK.LOGGER.warn("{} listed kit {} that the world doesn't give them; dropped it", player.getName().getString(), kit);
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
        CharacterService.assign(player, legitimateCharacter(player));
        WAS_SANDBOX.put(player.getUUID(), isSandbox(player));
        sync(player, true);
    }

    /** Puts a governed player back on their legitimate kit if they are on anything else. */
    public static void enforce(ServerPlayer player) {
        if (!governs(player)) return;
        validate(player);
        JJKCharacter legit = legitimateCharacter(player);
        if (Casters.get(player).character() != legit) CharacterService.assign(player, legit);
    }

    /** Runs {@code r} at the end of this tick, after whatever is happening now (eating, using) has completed. */
    public static void endOfTick(Runnable r) {
        END_OF_TICK.add(r);
    }

    private static void tick(ServerLevel level) {
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
        for (ServerPlayer p : List.copyOf(level.players())) {
            boolean sandbox = isSandbox(p);
            Boolean was = WAS_SANDBOX.put(p.getUUID(), sandbox);
            // Out of Creative: the temporary pick goes, the legitimate kit (if any) comes back.
            if (was != null && was && !sandbox) enforce(p);
            sync(p, false);
        }
    }

    // --- Acquisition ---

    /**
     * Claims a kit for a player: atomically in {@link KitOwnership}, recorded on the player, given to them in Survival,
     * synced. Every acquisition path ends here (through {@link #acquire}); admins use it directly.
     */
    public static KitOwnership.ClaimResult tryClaimKit(ServerPlayer player, String kit, String source) {
        if (kit.isEmpty() || !player.isAlive() || player.isSpectator()) return KitOwnership.ClaimResult.FAILED;
        KitOwnership.ClaimResult r = ownership(player).tryClaim(player.getUUID(), player.getName().getString(), kit, source);
        if (r == KitOwnership.ClaimResult.CLAIMED || r == KitOwnership.ClaimResult.ALREADY_OWNER) {
            PlayerProgression p = progression(player);
            PlayerProgression next = r == KitOwnership.ClaimResult.CLAIMED ? p.withKit(kit).withCurrent(kit) : p.withKit(kit);
            if (!next.equals(p)) setProgression(player, next);
            if (r == KitOwnership.ClaimResult.CLAIMED && governs(player)) CharacterService.assign(player, Characters.get(kit));
            sync(player, true);
        }
        return r;
    }

    /**
     * Runs an acquisition (eating a cursed object, a ritual...): the sandbox gets its own handling, otherwise the kit is
     * claimed and the acquisition's outcome for that result runs.
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

    @Nullable
    static ServerPlayer online(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer p : level.players()) if (p.getUUID().equals(id)) return p;
        }
        return null;
    }

    private static void refresh(@Nullable ServerPlayer p) {
        if (p == null) return;
        validate(p);
        enforce(p);
        sync(p, true);
    }

    /** Releases a kit from whoever owns it; an online owner loses it at once. */
    @Nullable
    public static KitOwnership.Owner release(MinecraftServer server, String kit) {
        KitOwnership.Owner old = KitOwnership.get(server).release(kit);
        if (old != null) refresh(online(server, old.uuid()));
        return old;
    }

    /** Gives a kit to a player, taking it from anyone who had it. */
    public static boolean transfer(MinecraftServer server, String kit, ServerPlayer to) {
        KitOwnership.Owner old = KitOwnership.get(server).owner(kit);
        if (!KitOwnership.get(server).transfer(kit, to.getUUID(), to.getName().getString(), "admin")) return false;
        if (old != null && !old.uuid().equals(to.getUUID())) refresh(online(server, old.uuid()));
        setProgression(to, validate(to).withCurrent(kit));
        refresh(to);
        return true;
    }

    // --- Client sync ---

    /** Sends the player what their screens need (only when it changed, unless forced). */
    public static void sync(ServerPlayer player, boolean force) {
        PlayerProgression p = progression(player);
        ProgressionPayload payload = new ProgressionPayload(governs(player), p.kit(), p.kits());
        ProgressionPayload last = SENT.get(player.getUUID());
        if (!force && payload.equals(last)) return;
        SENT.put(player.getUUID(), payload);
        ServerPlayNetworking.send(player, payload);
    }

    // --- Test hooks ---

    /** Test hook: treat this player as Creative ({@code true}), Survival ({@code false}), or by game mode ({@code null}). */
    public static void setSandboxForTesting(ServerPlayer player, @Nullable Boolean sandbox) {
        if (sandbox == null) SANDBOX_OVERRIDE.remove(player.getUUID());
        else SANDBOX_OVERRIDE.put(player.getUUID(), sandbox);
    }
}
