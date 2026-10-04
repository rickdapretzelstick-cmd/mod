package dev.rick.jjk.ryu;

import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.clash.BeamCounters;
import dev.rick.jjk.core.combat.Combat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * True Cannon's answer to True Love Beam: his Ultimate key, in the reaction window, is Every Last Drop fired straight
 * back at once (no charge, no line: he turns, points and lets it go). It takes what Every Last Drop always takes, the
 * full Awakening meter, and needs him not to be overheated.
 */
public final class RyuBeamCounter implements BeamCounters.Counter {
    public static void register() {
        BeamCounters.register(RyuCharacter.ID, new RyuBeamCounter());
    }

    @Override
    public String kind() {
        return "eld";
    }

    @Override
    public String name() {
        return "EVERY LAST DROP";
    }

    private static Ability eld(AbilityCaster c) {
        return c.character().ability(AbilitySlot.ULTIMATE, false);
    }

    @Override
    public boolean ready(AbilityCaster c) {
        if (c.character() == null || !(eld(c) instanceof EveryLastDropAbility)) return false;
        if (c.isAwakened() || Combat.actionsLocked(c.owner) || !RyuCombat.canDischarge(c.owner)) return false;
        return c.awakeningUnlocked() && (c.noCost() || c.awakening() >= c.maxAwakening());
    }

    @Override
    public Vec3 origin(LivingEntity user, Vec3 at) {
        return RyuCombat.fingertip(user, at.subtract(user.getEyePosition()).normalize());
    }

    @Override
    public boolean fire(AbilityCaster c, Vec3 at) {
        if (!ready(c) || !(c.owner.level() instanceof ServerLevel level)) return false;
        if (!c.noCost()) c.setAwakening(0);
        Ability ability = eld(c);
        AbilityContext ctx = new AbilityContext(c, c.owner, level, AbilitySlot.ULTIMATE, 0, 0, null);
        c.begin(EveryLastDropAbility.counter(ability, ctx, at));
        return true;
    }
}
