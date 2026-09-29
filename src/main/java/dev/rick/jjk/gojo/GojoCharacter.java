package dev.rick.jjk.gojo;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.ability.common.DashAbility;
import dev.rick.jjk.core.ability.common.GuardAbility;
import dev.rick.jjk.core.character.JJKCharacter;
import dev.rick.jjk.core.combat.melee.MeleeMoveset;

/**
 * Gojo Satoru. Six Eyes make his technique cheap to run (high regen), Limitless gives Blue, Red and Hollow Purple,
 * and Unlimited Void is his domain. Infinity is implemented and bound to C, but is only part of the active moveset
 * when {@code infinity.inMoveset} is set (see {@link #infinityInMoveset()}).
 */
public final class GojoCharacter extends JJKCharacter {
    public static final String ID = "gojo";
    public final InfinityAbility infinity = new InfinityAbility();
    private final MeleeMoveset melee = new MeleeMoveset("", "", 1.0f, 1.0f, 1.0f);

    public GojoCharacter() {
        super(ID);
        DashAbility dash = new DashAbility();
        GuardAbility guard = new GuardAbility();
        TeleportAbility limitless = new TeleportAbility();
        // Base kit (JJS): 1 Lapse Blue, 2 Reversal Red, 3 Rapid Punches, 4 Twofold Kick, Special Limitless.
        bind(AbilitySlot.DASH, dash);
        bind(AbilitySlot.GUARD, guard);
        bind(AbilitySlot.SKILL_1, new BlueAbility());
        bind(AbilitySlot.SKILL_2, new RedAbility());
        bind(AbilitySlot.SKILL_3, new RapidPunchesAbility());
        bind(AbilitySlot.SKILL_4, new TwofoldKickAbility());
        bind(AbilitySlot.SKILL_5, limitless);
        bind(AbilitySlot.ULTIMATE, new AwakenAbility());
        // Six Eyes (JJS): 1 Lapse Blue MAX, 2 Reversal Red MAX, 3 Hollow Purple, 4 Infinite Void, Special Limitless.
        bindAwakened(AbilitySlot.DASH, dash);
        bindAwakened(AbilitySlot.GUARD, guard);
        bindAwakened(AbilitySlot.SKILL_1, new BlueAbility(true));
        bindAwakened(AbilitySlot.SKILL_2, new RedAbility(true));
        bindAwakened(AbilitySlot.SKILL_3, new HollowPurpleAbility());
        bindAwakened(AbilitySlot.SKILL_4, new UnlimitedVoidAbility());
        bindAwakened(AbilitySlot.SKILL_5, limitless);
        // Infinity stays implemented (config infinity.inMoveset) but isn't part of the JJS kit; its old key is gone.
    }

    @Override
    public String displayName() {
        return "Gojo";
    }

    @Override
    public String title() {
        return "Honored One";
    }

    @Override
    public String description() {
        return "Lapse Blue, Reversal Red, Rapid Punches and Twofold Kick, with Limitless on the Special. Awaken the Six Eyes for the MAX techniques, Hollow Purple and Infinite Void.";
    }

    /** Infinity is implemented but currently kept out of the moveset unless the config brings it back. */
    public static boolean infinityInMoveset() {
        var cfg = JJKConfig.get().infinity;
        return cfg.enabled && cfg.inMoveset;
    }

    @Override
    public dev.rick.jjk.core.ability.Ability ability(AbilitySlot slot) {
        return ability(slot, false);
    }

    @Override
    public dev.rick.jjk.core.ability.Ability ability(AbilitySlot slot, boolean awakened) {
        dev.rick.jjk.core.ability.Ability a = super.ability(slot, awakened);
        // Out of the moveset: its slot is simply empty (no key, no HUD slot, can't be activated).
        return a == infinity && !infinityInMoveset() ? null : a;
    }

    /** Six Eyes lasts 60 seconds (JJS): the meter is the timer. */
    @Override
    public float awakeningDrainPerSecond() {
        return JJKConfig.get().awakening.max / Math.max(1, JJKConfig.get().gojo.awakeningSeconds);
    }

    @Override
    public boolean interceptInput(AbilityCaster caster, AbilitySlot slot, dev.rick.jjk.core.ability.Ability ability,
                                  @org.jetbrains.annotations.Nullable net.minecraft.world.entity.Entity targetHint) {
        // Limitless during a Reversal Red wind-up / Red MAX charge: the special variants.
        if (ability instanceof TeleportAbility && caster.cast() instanceof LimitlessCombo combo && !caster.cast().isFinished() && combo.acceptsLimitless()) {
            boolean costsSpecial = !caster.isAwakened();
            if (costsSpecial && !caster.isReady(slot)) return false;
            if (combo.limitless(targetHint) && costsSpecial) caster.startCooldown(slot, ability.cooldown(caster));
            return true;
        }
        return false;
    }

    @Override
    public void onDeath(AbilityCaster caster) {
        GojoState.clear(caster.owner);
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
        // Infinity is Gojo's default state while it is part of the moveset. Otherwise make sure a save from when it was
        // doesn't leave it running.
        if (infinityInMoveset()) infinity.turnOn(caster, false);
        else if (caster.toggled(InfinityAbility.ID)) infinity.toggleOff(caster, "not_in_moveset");
    }

    @Override
    public void tick(AbilityCaster caster) {
        if (infinityInMoveset()) {
            if (caster.isAwakened() && !caster.toggled(InfinityAbility.ID)
                    && !dev.rick.jjk.core.combat.Combat.has(caster.owner, dev.rick.jjk.core.combat.CombatStatus.BURNOUT)) {
                infinity.turnOn(caster, false);
            }
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
            if (infinityInMoveset()) infinity.turnOn(caster, false);
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
