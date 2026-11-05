package dev.rick.jjk.progression;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.registry.ModAttachments;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Hostility for curses that need perception, kept apart from perception itself.
 *
 * <ul>
 *   <li><b>Acquiring</b> a player as a target needs one of: the player perceives curses right now, the curse is already
 *   hostile toward them, or they just hurt it. So an unseen curse doesn't jump someone for walking into its room.</li>
 *   <li><b>Hostility</b> is remembered on the curse (saved with it) from the moment it targets someone or is hurt by
 *   them, and refreshed while the fight goes on. Taking the glasses off afterwards changes what the player sees, not
 *   what the curse wants: it keeps attacking.</li>
 * </ul>
 *
 * Vanilla-goal mobs are covered by the {@code Mob.setTarget} guard; curses built for the mod pass {@link #mayTarget} to
 * their targeting goals so they pick a valid target in the first place.
 */
public final class CurseAggro {
    private CurseAggro() {}

    public static void init() {
        // Hits in either direction keep (or start) the fight: being hurt is a legitimate reason to turn on someone.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            Entity attacker = source.getEntity();
            if (attacker instanceof Player p && entity instanceof Mob curse && CursePerception.requiresPerception(curse)) markHostile(curse, p);
            if (entity instanceof Player p && attacker instanceof Mob curse && CursePerception.requiresPerception(curse)) markHostile(curse, p);
            return true;
        });
    }

    /** Whether {@code curse} may take {@code target} as its target. Anything other than a perception-gated curse hunting a player is unaffected. */
    public static boolean mayTarget(Mob curse, LivingEntity target) {
        if (!(target instanceof Player player) || !CursePerception.requiresPerception(curse)) return true;
        if (isHostileTo(curse, player)) return true;
        if (curse.getLastHurtByMob() == player) return true;
        return CursePerception.canPerceive(player);
    }

    /** The curse just took {@code target}: from now on it is hostile toward them. */
    public static void onTargeted(Mob curse, LivingEntity target) {
        if (target instanceof Player player && CursePerception.requiresPerception(curse)) markHostile(curse, player);
    }

    public static boolean isHostileTo(Mob curse, Player player) {
        Map<String, Long> m = curse.getAttached(ModAttachments.CURSE_HOSTILITY);
        if (m == null) return false;
        Long at = m.get(player.getUUID().toString());
        if (at == null) return false;
        long memory = JJKConfig.get().progression.curseHostilityMemorySeconds * 20L;
        // Still its target: the fight is ongoing however long ago it began.
        return curse.getTarget() == player || curse.level().getGameTime() - at <= memory;
    }

    public static void markHostile(Mob curse, Player player) {
        Map<String, Long> m = curse.getAttached(ModAttachments.CURSE_HOSTILITY);
        Map<String, Long> next = m == null ? new HashMap<>() : new HashMap<>(m);
        long now = curse.level().getGameTime();
        long memory = JJKConfig.get().progression.curseHostilityMemorySeconds * 20L;
        next.values().removeIf(t -> now - t > memory);
        next.put(player.getUUID().toString(), now);
        curse.setAttached(ModAttachments.CURSE_HOSTILITY, next);
    }

    /** Forgets everyone (an encounter reset, a curse calmed by a technique...). */
    public static void calm(Mob curse) {
        curse.removeAttached(ModAttachments.CURSE_HOSTILITY);
    }
}
