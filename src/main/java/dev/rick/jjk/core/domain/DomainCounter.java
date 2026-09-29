package dev.rick.jjk.core.domain;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.net.DomainCounterPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The domain counter. When someone starts opening a domain, every sorcerer close enough who has a full Awakening
 * meter, has a domain, and isn't awakened yet gets a short window in which their ordinary Awakening button answers
 * instead: they awaken instantly and open their own domain at once. The two domains then collide and the existing
 * domain clash decides who wins; the counter guarantees nothing.
 */
public final class DomainCounter {
    private record Window(UUID opener, int openerId, long expires) {}

    private static final Map<UUID, Window> WINDOWS = new HashMap<>();

    private DomainCounter() {}

    /** Someone began opening a domain: offer the counter to everyone who could answer it. */
    public static void opening(LivingEntity opener, DomainDefinition def) {
        if (!(opener.level() instanceof ServerLevel level)) return;
        JJKConfig.Domain cfg = JJKConfig.get().domain;
        long expires = level.getGameTime() + cfg.counterWindowTicks;
        double range = cfg.counterRange;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(opener.blockPosition()).inflate(range), Entity::isAlive)) {
            if (e == opener || e.distanceTo(opener) > range) continue;
            AbilityCaster c = Casters.getOrNull(e);
            if (c == null || !eligible(c)) continue;
            WINDOWS.put(e.getUUID(), new Window(opener.getUUID(), opener.getId(), expires));
            if (e instanceof ServerPlayer sp) {
                ServerPlayNetworking.send(sp, new DomainCounterPayload(opener.getId(), cfg.counterWindowTicks, def.displayName()));
            }
        }
    }

    /** Full meter, a domain to open, not awakened yet, not already holding a domain. */
    public static boolean eligible(AbilityCaster c) {
        return !c.isAwakened() && (c.noCost() || c.awakening() >= c.maxAwakening()) && domainAbility(c) != null && DomainManager.ownedBy(c.owner) == null;
    }

    /**
     * The domain this sorcerer can open: on the Ultimate key while awakened (Gojo), or in the base kit (Hakari, whose
     * domain is what leads to his Jackpot).
     */
    @Nullable
    public static Ability domainAbility(AbilityCaster c) {
        if (c.character() == null) return null;
        // Awakened kit first (Gojo's Infinite Void is its fourth move), then the base kit (Hakari's domain).
        for (boolean awakened : new boolean[] {true, false}) {
            Ability a = c.character().ability(AbilitySlot.ULTIMATE, awakened);
            if (a instanceof DomainAbility) return a;
            for (AbilitySlot s : AbilitySlot.values()) {
                a = c.character().ability(s, awakened);
                if (a instanceof DomainAbility) return a;
            }
        }
        return null;
    }

    /** Whether this sorcerer's Awakening button is a counter right now. */
    public static boolean canCounter(AbilityCaster c) {
        Window w = WINDOWS.get(c.owner.getUUID());
        if (w == null) return false;
        if (!(c.owner.level() instanceof ServerLevel level) || level.getGameTime() > w.expires) {
            WINDOWS.remove(c.owner.getUUID());
            return false;
        }
        Entity opener = level.getEntity(w.opener);
        if (!(opener instanceof LivingEntity le) || !le.isAlive()) return false;
        // The opening must still be happening: mid-cast, or its domain already up.
        AbilityCaster oc = Casters.getOrNull(le);
        boolean stillOpening = DomainManager.ownedBy(le) != null || oc != null && oc.isCasting() && oc.cast().ability instanceof DomainAbility;
        return stillOpening && eligible(c);
    }

    /**
     * Pressed Awakening during a window: instant Awakening, then this sorcerer's domain opens immediately. Returns
     * false (and does nothing) if there is no valid window, so the button falls back to a normal Awakening.
     */
    public static boolean tryCounter(AbilityCaster c) {
        if (!canCounter(c)) return false;
        Window w = WINDOWS.remove(c.owner.getUUID());
        ServerLevel level = (ServerLevel) c.owner.level();
        LivingEntity user = c.owner;
        LivingEntity opener = (LivingEntity) level.getEntity(w.opener);
        Ability ability = domainAbility(c);
        DomainDefinition def = ((DomainAbility) ability).domain();
        // No long transformation: the eyes open and the domain answers in the same breath. (Characters whose domain
        // is the way into their awakened state skip straight to it, spending the full meter.)
        if (c.character().awakensOnCounter()) {
            c.enterAwakening();
            Fx.play(level, "awaken", user.position().add(0, 1.2, 0), Vec3.ZERO, 1f, user.getId());
        } else if (!c.noCost()) {
            c.setAwakening(0);
        }
        Fx.play(level, "domain_counter", user.position().add(0, 1.2, 0), opener.position().subtract(user.position()), 1f, user.getId());
        Fx.shake(level, user.position(), 48, 1.0f, 18);
        float meter = ability.awakeningCost(c);
        if (meter > 0 && !c.noCost()) c.setAwakening(c.awakening() - meter);
        c.startCooldown(ability, ability.cooldown(c));
        Anim.play(user, "domain_release");
        DomainCinematics.versus(opener, user);
        DomainInstance d = DomainManager.expand(user, def);
        return d != null;
    }

    /** Test/admin hook: forget all open windows. */
    public static void clear() {
        WINDOWS.clear();
    }
}
