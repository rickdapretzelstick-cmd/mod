package dev.rick.jjk.yuta;

import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.clash.BeamCounters;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.fx.Fx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Yuta's answer to Every Last Drop: his Ultimate key, in the reaction window, has Rika answer at once with True Love
 * Beam. Not awakened yet (full meter): True Love comes on in the same breath (no transformation) and she fires. Already
 * awakened: True Love Beam must be off cooldown, and its energy is spent as usual.
 */
public final class YutaBeamCounter implements BeamCounters.Counter {
    public static void register() {
        BeamCounters.register(YutaCharacter.ID, new YutaBeamCounter());
    }

    @Override
    public String kind() {
        return "tlb";
    }

    @Override
    public String name() {
        return "TRUE LOVE BEAM";
    }

    private static Ability beam(AbilityCaster c) {
        return c.character().ability(AbilitySlot.SKILL_3, YutaCharacter.RIKA_AWAKENED);
    }

    @Override
    public boolean ready(AbilityCaster c) {
        if (c.character() == null || !(beam(c) instanceof TrueLoveBeamAbility)) return false;
        if (Combat.actionsLocked(c.owner)) return false;
        if (!c.isAwakened()) return c.noCost() || c.awakening() >= c.maxAwakening();
        return c.cooldown(AbilitySlot.SKILL_3, YutaCharacter.RIKA_AWAKENED) <= 0 && c.canAfford(beam(c).cost(c));
    }

    @Override
    public Vec3 origin(LivingEntity user, Vec3 at) {
        return TrueLoveBeamAbility.mouthFor(user, at);
    }

    @Override
    public boolean fire(AbilityCaster c, Vec3 at) {
        if (!ready(c) || !(c.owner.level() instanceof ServerLevel level)) return false;
        Ability ability = beam(c);
        LivingEntity user = c.owner;
        if (!c.isAwakened()) {
            // True Love, in an instant: Rika answering a catastrophe doesn't wait for the transformation.
            c.enterAwakening();
            Fx.play(level, "awaken", user.position().add(0, 1.2, 0), Vec3.ZERO, 1f, user.getId());
        }
        if (!c.noCost()) c.spend(ability.cost(c));
        c.startCooldown(AbilitySlot.SKILL_3, YutaCharacter.RIKA_AWAKENED, ability.cooldown(c));
        AbilityContext ctx = new AbilityContext(c, user, level, AbilitySlot.SKILL_3, 0, 0, null);
        c.begin(TrueLoveBeamAbility.counter(ability, ctx, at));
        return true;
    }
}
