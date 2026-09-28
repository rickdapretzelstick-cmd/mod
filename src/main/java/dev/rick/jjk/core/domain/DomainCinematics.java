package dev.rick.jjk.core.domain;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.net.DomainCinematicPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Tells clients to present a domain opening. Every opening is presented: the caster gets the full short cinematic,
 * nearby players a light one (the domain itself is physically building in their world either way), and a counter turns
 * it into a VERSUS presentation for both duellists, which then hands over to the domain clash.
 */
public final class DomainCinematics {
    private DomainCinematics() {}

    /** A domain began opening; it seals {@code startupTicks} + its formation time from now. */
    public static void opening(LivingEntity caster, DomainDefinition def, int startupTicks) {
        if (!(caster.level() instanceof ServerLevel level)) return;
        int titleAt = startupTicks + def.formingTicks();
        int[] ents = {caster.getId()};
        List<String> names = List.of(caster.getName().getString());
        List<String> doms = List.of(def.displayName());
        int[] cols = {def.clashColor()};
        DomainCinematicPayload solo = new DomainCinematicPayload(DomainCinematicPayload.SOLO, ents, names, doms, cols, titleAt, titleAt + 14);
        DomainCinematicPayload observe = new DomainCinematicPayload(DomainCinematicPayload.OBSERVE, ents, names, doms, cols, titleAt, 34);
        double range = JJKConfig.get().domain.observerRange;
        for (ServerPlayer p : level.players()) {
            if (p == caster) ServerPlayNetworking.send(p, solo);
            else if (p.distanceTo(caster) < range) ServerPlayNetworking.send(p, observe);
        }
    }

    /** A counter: both sorcerers get the versus presentation (the opener's own solo presentation escalates into it). */
    public static void versus(@Nullable LivingEntity opener, LivingEntity counter) {
        if (opener == null || !(counter.level() instanceof ServerLevel level)) return;
        DomainDefinition a = domainOf(opener), b = domainOf(counter);
        int[] ents = {opener.getId(), counter.getId()};
        List<String> names = List.of(opener.getName().getString(), counter.getName().getString());
        List<String> doms = List.of(a == null ? "Domain" : a.displayName(), b == null ? "Domain" : b.displayName());
        int[] cols = {a == null ? 0xFFFFFFFF : a.clashColor(), b == null ? 0xFFFFFFFF : b.clashColor()};
        DomainCinematicPayload vs = new DomainCinematicPayload(DomainCinematicPayload.VERSUS, ents, names, doms, cols, 0, 70);
        for (ServerPlayer p : level.players()) {
            if (p == opener || p == counter || p.distanceTo(counter) < JJKConfig.get().domain.observerRange) ServerPlayNetworking.send(p, vs);
        }
    }

    @Nullable
    private static DomainDefinition domainOf(LivingEntity e) {
        var c = Casters.getOrNull(e);
        if (c == null || c.character() == null) return null;
        var a = c.character().ability(dev.rick.jjk.core.ability.AbilitySlot.ULTIMATE, true);
        return a instanceof DomainAbility da ? da.domain() : null;
    }
}
