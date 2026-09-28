package dev.rick.jjk.core.character;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.domain.DomainInstance;
import dev.rick.jjk.core.domain.DomainManager;
import dev.rick.jjk.gojo.GojoCharacter;
import dev.rick.jjk.registry.ModAttachments;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/** Assigning characters to players, persisting the choice, and cleaning up on death/leave. */
public final class CharacterService {
    private CharacterService() {}

    public static void init() {
        ServerPlayerEvents.JOIN.register(CharacterService::restore);
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> restore(newPlayer));
        ServerPlayerEvents.LEAVE.register(player -> cleanup(player, DomainInstance.EndReason.OWNER_LOST));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            AbilityCaster c = Casters.getOrNull(entity);
            if (c != null && c.character() != null) c.character().onDeath(c);
            cleanup(entity, DomainInstance.EndReason.OWNER_LOST);
        });
        // A character's own state can refuse a death (Hakari's Jackpot).
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            AbilityCaster c = Casters.getOrNull(entity);
            return c == null || c.character() == null || !c.character().preventDeath(c, source, amount);
        });
    }

    /**
     * Why this entity can't change character right now, or null if it can. Switching is only allowed from a neutral
     * state: not mid-attack or mid-cast, not in or near a domain or clash, not awakened (or in Jackpot), not stunned,
     * and not hit in the last few seconds.
     */
    @Nullable
    public static String switchBlocked(LivingEntity entity) {
        AbilityCaster c = Casters.get(entity);
        if (!entity.isAlive()) return "You can't switch while dead.";
        if (c.isCasting() || !c.overlays().isEmpty()) return "Finish your technique first.";
        if (c.melee.isCommitted()) return "Finish your attack first.";
        if (c.isAwakened()) return "You can't switch while awakened.";
        var state = dev.rick.jjk.core.combat.Combat.state(entity);
        if (state.has(dev.rick.jjk.core.combat.CombatStatus.CLASHING)) return "You can't switch during a domain clash.";
        if (state.actionsLocked() || state.movementLocked() || state.has(dev.rick.jjk.core.combat.CombatStatus.AWAKENING)) {
            return "You can't switch while stunned.";
        }
        if (DomainManager.ownedBy(entity) != null) return "You can't switch while your domain is open.";
        for (DomainInstance d : DomainManager.all((net.minecraft.server.level.ServerLevel) entity.level())) {
            if (d.isLive() && d.contains(entity)) return "You can't switch inside a domain.";
        }
        if (entity.getLastHurtByMob() != null && entity.tickCount - entity.getLastHurtByMobTimestamp() < 60) return "You're still in combat.";
        if (c.character() != null) {
            String own = c.character().switchBlocked(c);
            if (own != null) return own;
        }
        return null;
    }

    /** A player picked a character on the select screen. Returns why not, or null once switched. */
    @Nullable
    public static String select(ServerPlayer player, String id) {
        JJKCharacter next = id.isEmpty() ? null : Characters.get(id);
        if (!id.isEmpty() && next == null) return "Unknown character.";
        AbilityCaster c = Casters.get(player);
        if (c.character() == next) return null;
        String blocked = switchBlocked(player);
        if (blocked != null) return blocked;
        assign(player, next);
        return null;
    }

    private static void restore(ServerPlayer player) {
        String id = player.getAttached(ModAttachments.CHARACTER);
        if (id == null && JJKConfig.get().general.autoAssignGojo) id = GojoCharacter.ID;
        if (id != null && !id.isEmpty()) assign(player, Characters.get(id));
        else sync(player);
    }

    public static void assign(LivingEntity entity, @Nullable JJKCharacter character) {
        AbilityCaster caster = Casters.get(entity);
        caster.setCharacter(character);
        if (entity instanceof ServerPlayer sp) {
            sp.setAttached(ModAttachments.CHARACTER, character == null ? "" : character.id);
            sync(sp);
        }
    }

    public static void sync(ServerPlayer player) {
        ServerPlayNetworking.send(player, Casters.get(player).buildSync());
    }

    private static void cleanup(LivingEntity entity, DomainInstance.EndReason reason) {
        AbilityCaster c = Casters.getOrNull(entity);
        if (c != null) c.shutdown("left");
        DomainManager.collapseOwnedBy(entity, reason);
    }
}
