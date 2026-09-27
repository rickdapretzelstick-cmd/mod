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
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> cleanup(entity, DomainInstance.EndReason.OWNER_LOST));
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
