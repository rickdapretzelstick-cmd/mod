package dev.rick.jjk;

import dev.rick.jjk.command.JJKCommand;
import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.ability.common.GuardDefense;
import dev.rick.jjk.core.character.CharacterService;
import dev.rick.jjk.core.character.Characters;
import dev.rick.jjk.core.combat.CombatEvents;
import dev.rick.jjk.core.defense.Defenses;
import dev.rick.jjk.core.defense.VanillaDamageBridge;
import dev.rick.jjk.core.domain.DomainManager;
import dev.rick.jjk.core.hitbox.HitboxManager;
import dev.rick.jjk.core.net.Network;
import dev.rick.jjk.gojo.GojoCharacter;
import dev.rick.jjk.gojo.InfinityDefense;
import dev.rick.jjk.registry.ModAttachments;
import dev.rick.jjk.registry.ModEntities;
import dev.rick.jjk.registry.ModSounds;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/** Common (both sides) initialisation. */
public final class Bootstrap {
    private static boolean done;

    private Bootstrap() {}

    public static synchronized void init() {
        if (done) return;
        done = true;
        JJKConfig.load();
        ModSounds.init();
        dev.rick.jjk.registry.ModBlocks.init();
        ModEntities.init();
        ModAttachments.init();
        Network.init();
        dev.rick.jjk.progression.TechniqueProgression.init();
        CommandRegistrationCallback.EVENT.register((dispatcher, ctx, selection) -> dev.rick.jjk.progression.ProgressionCommand.register(dispatcher));

        Characters.register(new GojoCharacter());
        Characters.register(new dev.rick.jjk.hakari.HakariCharacter());
        Characters.register(new dev.rick.jjk.yuji.YujiCharacter());
        Characters.register(new dev.rick.jjk.yuta.YutaCharacter());
        Characters.register(new dev.rick.jjk.ryu.RyuCharacter());
        dev.rick.jjk.yuta.YutaBeamCounter.register();
        dev.rick.jjk.ryu.RyuBeamCounter.register();
        Defenses.register(new InfinityDefense());
        Defenses.register(new dev.rick.jjk.hakari.DoorGuardDefense());
        Defenses.register(new dev.rick.jjk.yuji.ManjiKickDefense());
        Defenses.register(new dev.rick.jjk.yuta.OutburstDefense());
        Defenses.register(new dev.rick.jjk.yuta.ClairvoyanceDefense());
        Defenses.register(new GuardDefense());
        VanillaDamageBridge.init();
        CharacterService.init();
        dev.rick.jjk.core.domain.structure.DomainStructures.init();
        dev.rick.jjk.core.world.WorldRestoration.init();

        // Anything that locks casting interrupts whatever the entity was doing.
        CombatEvents.STATUS_APPLIED.add((entity, status, ticks) -> {
            if (!status.interruptsCasting) return;
            AbilityCaster c = Casters.getOrNull(entity);
            if (c != null) {
                c.interrupt(status.id);
                c.melee.cancel();
            }
            if (status.locksActions) dev.rick.jjk.core.combat.Combat.state(entity).stopGuard();
        });

        dev.rick.jjk.core.character.AwakeningGain.init();

        ServerTickEvents.END_LEVEL_TICK.register(level -> {
            // /tick freeze stops JJK too (players still tick while frozen; nothing of ours should).
            if (!level.tickRateManager().runsNormally()) return;
            HitboxManager.tick(level);
            DomainManager.tick(level);
            dev.rick.jjk.hakari.ShutterTrap.tick(level);
            dev.rick.jjk.gojo.UnlimitedPurple.tick(level);
            dev.rick.jjk.core.clash.BeamClashManager.tick(level);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> dev.rick.jjk.core.clash.BeamClashManager.clear());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            dev.rick.jjk.core.clash.BeamClashManager.clear();
            HitboxManager.clearAll();
            DomainManager.clearAll();
            dev.rick.jjk.hakari.IdleDeathGamble.clearAll();
            dev.rick.jjk.hakari.ShutterTrap.clearAll();
            dev.rick.jjk.gojo.UnlimitedPurple.clearAll();
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, ctx, selection) -> JJKCommand.register(dispatcher));
        JJK.LOGGER.info("Jujutsu loaded: {} character(s)", Characters.ids().spliterator().getExactSizeIfKnown());
    }
}
