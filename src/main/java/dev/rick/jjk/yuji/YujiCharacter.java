package dev.rick.jjk.yuji;

import dev.rick.jjk.JJK;
import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.ability.common.GuardAbility;
import dev.rick.jjk.core.character.JJKCharacter;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatState;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.melee.MeleeMoveset;
import dev.rick.jjk.core.fx.Fx;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Yuji Itadori — Vessel, after the Jujutsu Shenanigans character. A close-range brawler: four melee moves that flow into
 * each other (Cursed Strikes, Crushing Blow, Divergent Fist with its Black Flash, the Manji Kick counter) and Combat
 * Instincts on the Special, which feints any of them. His Awakening is the King of Curses: Sukuna takes over for 60
 * seconds, heals 45 HP, and fights with Shrine — ranged slashing M1s, Cleave, Dismantle, Open, Rush and Malevolent Shrine.
 * 85 max HP.
 */
public final class YujiCharacter extends JJKCharacter {
    public static final String ID = "yuji";
    private static final Identifier HEALTH_ID = JJK.id("vessel_health");
    private final MeleeMoveset melee = new MeleeMoveset("", "", 1.0f, 1.0f, 1.05f);
    private final MeleeMoveset shrine = new MeleeMoveset("shrine_", "shrine_", 1.0f, 0.8f, 1.0f,
            JJKConfig.get().yuji.shrineRangeMultiplier, false, true, YujiCombat::shrineSwing);

    public YujiCharacter() {
        super(ID);
        YujiDashAbility dash = new YujiDashAbility();
        GuardAbility guard = new GuardAbility();
        // Base kit (JJS): 1 Cursed Strikes, 2 Crushing Blow, 3 Divergent Fist, 4 Manji Kick, Special Combat Instincts.
        bind(AbilitySlot.DASH, dash);
        bind(AbilitySlot.GUARD, guard);
        bind(AbilitySlot.SKILL_1, new CursedStrikesAbility());
        bind(AbilitySlot.SKILL_2, new CrushingBlowAbility());
        bind(AbilitySlot.SKILL_3, new DivergentFistAbility());
        bind(AbilitySlot.SKILL_4, new ManjiKickAbility());
        bind(AbilitySlot.SKILL_5, new CombatInstinctsAbility());
        bind(AbilitySlot.ULTIMATE, new KingOfCursesAbility());
        // King of Curses (JJS): 1 Dismantle, 2 Open, 3 Rush, 4 Malevolent Shrine, Special Cleave.
        bindAwakened(AbilitySlot.DASH, dash);
        bindAwakened(AbilitySlot.GUARD, guard);
        bindAwakened(AbilitySlot.SKILL_1, new DismantleAbility());
        bindAwakened(AbilitySlot.SKILL_2, new OpenAbility());
        bindAwakened(AbilitySlot.SKILL_3, new RushAbility());
        bindAwakened(AbilitySlot.SKILL_4, new MalevolentShrineAbility());
        bindAwakened(AbilitySlot.SKILL_5, new CleaveAbility());
    }

    @Override
    public String displayName() {
        return "Yuji";
    }

    @Override
    public String title() {
        return "Vessel";
    }

    @Override
    public String description() {
        return "Cursed Strikes, Crushing Blow, Divergent Fist and Manji Kick, feinted with Combat Instincts. Awaken the King of Curses for Shrine: Dismantle, Cleave, Open and Malevolent Shrine.";
    }

    @Override
    public float maxEnergy() {
        return JJKConfig.get().yuji.maxCursedEnergy;
    }

    @Override
    public float regenPerSecond() {
        return JJKConfig.get().yuji.regenPerSecond;
    }

    @Override
    public MeleeMoveset melee() {
        return melee;
    }

    @Override
    public MeleeMoveset melee(AbilityCaster caster) {
        // Shrine: the basic attacks become slashes reaching three times as far.
        return caster.isAwakened() ? shrine : melee;
    }

    /** King of Curses lasts 60 seconds: the meter is the timer. */
    @Override
    public float awakeningDrainPerSecond() {
        return JJKConfig.get().awakening.max / Math.max(1, JJKConfig.get().yuji.awakeningSeconds);
    }

    @Override
    public boolean interceptInput(AbilityCaster caster, AbilitySlot slot, Ability ability, @Nullable Entity targetHint) {
        var cast = caster.cast() != null && !caster.cast().isFinished() ? caster.cast() : null;
        if (!caster.isAwakened()) {
            // Divergent Fist pressed again while he flashes white: the Black Flash.
            if (ability instanceof DivergentFistAbility && cast instanceof DivergentFistAbility.Instance df && df.blackFlashPress()) return true;
            // Combat Instincts during an M1's or a skill's wind-up: the feint.
            if (ability instanceof CombatInstinctsAbility) {
                boolean skill = cast instanceof Feintable f && f.feintable();
                boolean m1 = !skill && caster.melee.inStartup();
                if (!skill && !m1) return false;
                if (!caster.isReady(slot)) return false;
                if (skill) {
                    for (AbilitySlot s : AbilitySlot.values()) if (caster.ability(s) == cast.ability) caster.resetSlot(s);
                    caster.interrupt("feint");
                } else {
                    caster.melee.feint();
                }
                YujiCombat.feintCost(caster);
                caster.startCooldown(slot, ability.cooldown(caster));
                CombatInstinctsAbility.feintFx(caster.owner);
                return true;
            }
            // A chained Black Flash: Divergent Fist stays off cooldown while the chain runs.
            if (ability instanceof DivergentFistAbility && !caster.isReady(slot) && YujiState.of(caster.owner).chain > 0
                    && caster.owner.level().getGameTime() <= YujiState.of(caster.owner).chainUntil) {
                caster.resetSlot(slot);
                return false;
            }
            return false;
        }
        // World Cutting Slash: Rush during Dismantle's wind-up, then Open, then Cleave.
        if (cast instanceof DismantleAbility.Instance d) {
            if (ability instanceof RushAbility && d.chant(1)) return true;
            if (ability instanceof OpenAbility && d.chant(2)) return true;
            if (ability instanceof CleaveAbility && caster.isReady(slot) && d.chant(3)) return true;
        }
        return false;
    }

    @Override
    public void onAbilityUsed(AbilityCaster caster, Ability ability, AbilitySlot slot) {
        // Another move during a Black Flash chain ends it, and Divergent Fist goes on its cooldown after all.
        YujiState ys = YujiState.of(caster.owner);
        if (ys.chain > 0 && !(ability instanceof DivergentFistAbility) && !(ability instanceof CombatInstinctsAbility)
                && !(ability instanceof YujiDashAbility)) {
            endChain(caster);
        }
    }

    /** The Black Flash chain is over: Divergent Fist's cooldown finally runs. */
    static void endChain(AbilityCaster caster) {
        YujiState ys = YujiState.of(caster.owner);
        if (ys.chain <= 0) return;
        ys.breakChain();
        for (AbilitySlot s : AbilitySlot.values()) {
            if (caster.character() != null && caster.character().ability(s, false) instanceof DivergentFistAbility a) {
                if (!caster.isAwakened()) caster.startCooldown(s, a.cooldown(caster));
            }
        }
    }

    @Override
    public void tick(AbilityCaster caster) {
        var e = caster.owner;
        YujiState ys = YujiState.of(e);
        if (ys.chain > 0 && e.level().getGameTime() > ys.chainUntil && !(caster.cast() instanceof DivergentFistAbility.Instance)) endChain(caster);
        // Sukuna's marks and aura, for everyone to see.
        CombatState state = Combat.state(e);
        if (caster.isAwakened()) {
            if (state.get(CombatStatus.SUKUNA) < 20) state.set(CombatStatus.SUKUNA, 60);
            if (e.tickCount % 14 == 0 && e.level() instanceof ServerLevel level) {
                Fx.play(level, "sukuna_aura", e.position().add(0, 1, 0), Vec3.ZERO, 1f, e.getId());
            }
        } else if (state.has(CombatStatus.SUKUNA)) {
            state.remove(CombatStatus.SUKUNA);
        }
        applyHealth(caster);
    }

    /** Vessel's 85 max HP: a lasting share off the entity's max health while he is Vessel. */
    private static void applyHealth(AbilityCaster caster) {
        AttributeInstance attr = caster.owner.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null) return;
        double want = JJKConfig.get().yuji.maxHealthShare - 1.0;
        AttributeModifier cur = attr.getModifier(HEALTH_ID);
        if (cur != null && Math.abs(cur.amount() - want) < 1e-4) return;
        attr.removeModifier(HEALTH_ID);
        if (want < 0) attr.addTransientModifier(new AttributeModifier(HEALTH_ID, want, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        if (caster.owner.getHealth() > caster.owner.getMaxHealth()) caster.owner.setHealth(caster.owner.getMaxHealth());
    }

    @Override
    public void onAssigned(AbilityCaster caster) {
        applyHealth(caster);
    }

    @Override
    public void onAwakeningChanged(AbilityCaster caster, boolean awakened) {
        if (!awakened) {
            Combat.state(caster.owner).remove(CombatStatus.SUKUNA);
            if (caster.owner.level() instanceof ServerLevel sl) {
                Fx.play(sl, "sukuna_end", caster.owner.position().add(0, 1.2, 0), Vec3.ZERO, 1f, caster.owner.getId());
            }
        }
    }

    @Override
    public void onDeath(AbilityCaster caster) {
        YujiState.clear(caster.owner);
    }

    @Override
    public void onRemoved(AbilityCaster caster) {
        AttributeInstance attr = caster.owner.getAttribute(Attributes.MAX_HEALTH);
        if (attr != null) attr.removeModifier(HEALTH_ID);
        Combat.state(caster.owner).remove(CombatStatus.SUKUNA);
        Combat.state(caster.owner).remove(CombatStatus.MANJI);
        YujiState.clear(caster.owner);
    }
}
