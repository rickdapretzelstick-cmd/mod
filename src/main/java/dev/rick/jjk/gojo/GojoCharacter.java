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
        DashAbility dash = new DashAbility();
        GuardAbility guard = new GuardAbility();
        TeleportAbility teleport = new TeleportAbility();
        // Base kit.
        bind(AbilitySlot.DASH, dash);
        bind(AbilitySlot.GUARD, guard);
        bind(AbilitySlot.SKILL_1, new BlueAbility());
        bind(AbilitySlot.SKILL_2, new RedAbility());
        bind(AbilitySlot.SKILL_3, infinity);
        bind(AbilitySlot.SKILL_4, teleport);
        bind(AbilitySlot.ULTIMATE, new AwakenAbility());
        // Awakened kit: the finishers. Infinity is simply always on.
        bindAwakened(AbilitySlot.DASH, dash);
        bindAwakened(AbilitySlot.GUARD, guard);
        bindAwakened(AbilitySlot.SKILL_1, new BlueAbility(true));
        bindAwakened(AbilitySlot.SKILL_2, new RedAbility(true));
        bindAwakened(AbilitySlot.SKILL_3, new HollowPurpleAbility());
        bindAwakened(AbilitySlot.SKILL_4, teleport);
        bindAwakened(AbilitySlot.ULTIMATE, new UnlimitedVoidAbility());
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
        if (caster.isAwakened() && !caster.toggled(InfinityAbility.ID)
                && !dev.rick.jjk.core.combat.Combat.has(caster.owner, dev.rick.jjk.core.combat.CombatStatus.BURNOUT)) {
            infinity.turnOn(caster, false);
        }
        infinity.tickField(caster);
        // Visual state everyone can see: blindfold on in the base kit, off (eyes open) while awakened.
        var state = dev.rick.jjk.core.combat.Combat.state(caster.owner);
        var show = caster.isAwakened() ? dev.rick.jjk.core.combat.CombatStatus.AWAKENED : dev.rick.jjk.core.combat.CombatStatus.BLINDFOLD;
        var hide = show == dev.rick.jjk.core.combat.CombatStatus.AWAKENED ? dev.rick.jjk.core.combat.CombatStatus.BLINDFOLD
                : dev.rick.jjk.core.combat.CombatStatus.AWAKENED;
        if (state.has(hide)) state.remove(hide);
        if (state.get(show) < 20) state.set(show, 60);
    }

    @Override
    public void onAwakeningChanged(AbilityCaster caster, boolean awakened) {
        if (awakened) {
            infinity.turnOn(caster, false);
        } else if (caster.owner.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            dev.rick.jjk.core.fx.Fx.play(sl, "awaken_end", caster.owner.position().add(0, 1.2, 0), net.minecraft.world.phys.Vec3.ZERO, 1f, caster.owner.getId());
        }
    }

    @Override
    public void onRemoved(AbilityCaster caster) {
        var state = dev.rick.jjk.core.combat.Combat.state(caster.owner);
        state.remove(dev.rick.jjk.core.combat.CombatStatus.BLINDFOLD);
        state.remove(dev.rick.jjk.core.combat.CombatStatus.AWAKENED);
    }
}
