package dev.rick.jjk.core.anim;

import dev.rick.jjk.JJK;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.clash.BeamClashManager;
import dev.rick.jjk.core.clash.ClashCommon;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.domain.DomainManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Gets avatars out of animations they are stuck in. The server is the authority:
 * <ul>
 *   <li>A held pose ({@link Anim}'s held clips: charges, stances, guards) belongs to something going on: a cast, an
 *   overlay, a melee charge, a guard, a clash or their own domain. One left showing with none of those for
 *   {@link #GRACE} ticks lost its end event: it is released (blending back to idle or movement on every client).</li>
 *   <li>A cast running far past what it declared is ended by the caster's own watchdog ({@code AbilityCaster}).</li>
 *   <li>Respawning, (re)joining and changing dimension start from a clean slate: any held pose is released for
 *   everyone, so a client that kept an old one (a missed packet, a reloaded entity) snaps back in step.</li>
 * </ul>
 * Nothing here touches a pose that is still legitimately playing: busy means busy, however long it takes.
 */
public final class AnimRecovery {
    /** Ticks a held pose may show with nothing behind it. */
    public static final int GRACE = 20;
    private static final Map<LivingEntity, Integer> IDLE = new WeakHashMap<>();
    /** Poses released so far (tests, debugging). */
    public static int recovered;

    private AnimRecovery() {}

    public static void init() {
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> resync(newPlayer));
        ServerPlayerEvents.JOIN.register(AnimRecovery::resync);
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> resync(player));
    }

    /** A fresh start: nothing held, for the player and everyone tracking them. */
    public static void resync(ServerPlayer player) {
        Anim.forget(player);
        IDLE.remove(player);
        Anim.play(player, "", 1f);
    }

    /** Every server tick, for each entity with a caster. */
    public static void tick(LivingEntity e, @Nullable AbilityCaster caster) {
        if (!Anim.isHolding(e)) {
            IDLE.remove(e);
            return;
        }
        if (busy(e, caster)) {
            IDLE.remove(e);
            return;
        }
        int idle = IDLE.merge(e, 1, Integer::sum);
        if (idle >= GRACE) {
            IDLE.remove(e);
            recovered++;
            JJK.LOGGER.debug("[recovery] released a stale held pose on {}", e.getName().getString());
            Anim.stop(e);
        }
    }

    /** Something is going on that a held pose may belong to. */
    public static boolean busy(LivingEntity e, @Nullable AbilityCaster caster) {
        if (!e.isAlive()) return false;
        if (caster != null && (caster.isCasting() || !caster.overlays().isEmpty() || caster.melee.isAttacking())) return true;
        return Combat.isGuarding(e) || ClashCommon.clashing(e) || BeamClashManager.sessionOf(e) != null || DomainManager.ownedBy(e) != null;
    }
}
