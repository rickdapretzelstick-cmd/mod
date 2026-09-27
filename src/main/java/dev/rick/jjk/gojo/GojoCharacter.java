package dev.rick.jjk.gojo;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.ability.common.DashAbility;
import dev.rick.jjk.core.ability.common.GuardAbility;
import dev.rick.jjk.core.character.JJKCharacter;
import dev.rick.jjk.core.combat.melee.MeleeMoveset;

/**
 * Gojo Satoru. Six Eyes make his technique cheap to run (high regen), Limitless gives Infinity, Blue, Red and
 * Hollow Purple, and Unlimited Void is his domain.
 */
public final class GojoCharacter extends JJKCharacter {
    public static final String ID = "gojo";
    public final InfinityAbility infinity = new InfinityAbility();
    private final MeleeMoveset melee = new MeleeMoveset("", "", 1.0f, 1.0f, 1.0f);

    public GojoCharacter() {
        super(ID);
        bind(AbilitySlot.DASH, new DashAbility());
        bind(AbilitySlot.GUARD, new GuardAbility());
        bind(AbilitySlot.SKILL_1, new BlueAbility());
        bind(AbilitySlot.SKILL_2, new RedAbility());
        bind(AbilitySlot.SKILL_3, new HollowPurpleAbility());
        bind(AbilitySlot.SKILL_4, new TeleportAbility());
        bind(AbilitySlot.SKILL_5, infinity);
        bind(AbilitySlot.ULTIMATE, new UnlimitedVoidAbility());
    }

    @Override
    public float maxEnergy() {
        return JJKConfig.get().resources.gojoMaxCursedEnergy;
    }

    @Override
    public float regenPerSecond() {
        return JJKConfig.get().resources.gojoRegenPerSecond;
    }

    @Override
    public MeleeMoveset melee() {
        return melee;
    }

    @Override
    public void onAssigned(AbilityCaster caster) {
        // Infinity is Gojo's default state.
        if (JJKConfig.get().infinity.enabled) infinity.turnOn(caster, false);
    }

    @Override
    public void tick(AbilityCaster caster) {
        infinity.tickField(caster);
    }
}
