package dev.rick.jjk.progression.tool.rifle;

import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.clash.BeamCounters;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The rifle's answer in the shared beam-clash system: a player holding a rifle that has its beam charged and ready can
 * answer any ultimate beam (and is answered by any), by letting go of use while the window is open. Charging alone
 * never starts a clash: the shared engagement rules (in its path, in sight, the window open) decide, the same as for
 * Yuta and Ryu.
 */
public final class RifleCounter implements BeamCounters.Counter {
    static final RifleCounter INSTANCE = new RifleCounter();

    private RifleCounter() {}

    public static void register() {
        BeamCounters.registerHeld(e -> e instanceof ServerPlayer p && RifleServer.isRifle(p.getMainHandItem()) && RifleRules.beamUnlocked(p) ? INSTANCE : null);
    }

    @Override
    public String kind() {
        return RifleBeam.KIND;
    }

    @Override
    public String name() {
        return "CURSED RIFLE";
    }

    @Override
    public boolean ready(AbilityCaster caster) {
        return caster.owner instanceof ServerPlayer p && RifleServer.phase(p) == RifleServer.Phase.READY
                && RifleServer.energy(p) >= RifleRules.beamCost(p);
    }

    @Override
    public Vec3 origin(LivingEntity user, Vec3 at) {
        return RifleServer.muzzle(user, at.subtract(user.getEyePosition()).normalize());
    }

    @Override
    public boolean fire(AbilityCaster caster, Vec3 at) {
        return caster.owner instanceof ServerPlayer p && RifleServer.answer(p, at);
    }
}
