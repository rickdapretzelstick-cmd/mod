package dev.rick.jjk.yuta;

import dev.rick.jjk.JJK;
import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.ability.common.GuardAbility;
import dev.rick.jjk.core.character.JJKCharacter;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatEvents;
import dev.rick.jjk.core.combat.CombatState;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.melee.MeleeMoveset;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.yuji.YujiDashAbility;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Yuta Okkotsu and Rika Orimoto — Cursed Partners, after the Jujutsu Shenanigans character. 90 max HP. Four movesets:
 * <ol start="0">
 *   <li>Base: Severing Path (Veilstep), Resolute Slash (Resolute Black Flash), Outburst, Second Wind; Rika on the
 *       Special; katana M1s (Swordsmanship).</li>
 *   <li>True Love (Awakening, 60 s): Elbow Rush, Copy, Energy Ripple (Fakeout), Authentic Mutual Love (Jacob's
 *       Ladder); the Copy Wheel on the Awakening key; Steel Arm M1s.</li>
 *   <li>Base Rika (the Special pressed with her out): Rika Smash, Rika Launch, Rika Haymaker, one shared cooldown.</li>
 *   <li>Awakened Rika: Rika Downslam, Rika Slam, True Love Beam, Rika Throw, each on its own cooldown.</li>
 * </ol>
 */
public final class YutaCharacter extends JJKCharacter {
    public static final String ID = "yuta";
    public static final int RIKA = 2, RIKA_AWAKENED = 3;
    private static final Identifier HEALTH_ID = JJK.id("cursed_partners_health");
    private static boolean hooked;

    private final MeleeMoveset katana = new MeleeMoveset("yuta_", "yuta_", 1.0f, 1.0f, 1.0f, 1.0f, true, false, YutaCombat::katanaSwing);
    /** Steel Arm: the extra jabs don't change the string's total damage. */
    private final MeleeMoveset steel = new MeleeMoveset("yuta_steel_", "yuta_steel_", 0.89f, 1.0f, 1.0f, 1.0f, true, false, YutaCombat::steelSwing);

    public YutaCharacter() {
        super(ID);
        YujiDashAbility dash = new YujiDashAbility();
        GuardAbility guard = new GuardAbility();
        RikaAbility rika = new RikaAbility();
        TrueLoveAbility trueLove = new TrueLoveAbility();
        CopyWheelAbility wheel = new CopyWheelAbility();
        // Base (JJS): 1 Severing Path, 2 Resolute Slash, 3 Outburst, 4 Second Wind, Special Rika, Awakening True Love.
        bind(AbilitySlot.DASH, dash);
        bind(AbilitySlot.GUARD, guard);
        bind(AbilitySlot.SKILL_1, new SeveringPathAbility());
        bind(AbilitySlot.SKILL_2, new ResoluteSlashAbility());
        bind(AbilitySlot.SKILL_3, new OutburstAbility());
        bind(AbilitySlot.SKILL_4, new SecondWindAbility());
        bind(AbilitySlot.SKILL_5, rika);
        bind(AbilitySlot.ULTIMATE, trueLove);
        // True Love: 1 Elbow Rush, 2 Copy, 3 Energy Ripple, 4 Authentic Mutual Love, Awakening key the Copy Wheel.
        bindAwakened(AbilitySlot.DASH, dash);
        bindAwakened(AbilitySlot.GUARD, guard);
        bindAwakened(AbilitySlot.SKILL_1, new ElbowRushAbility());
        bindAwakened(AbilitySlot.SKILL_2, new CopyAbility());
        bindAwakened(AbilitySlot.SKILL_3, new EnergyRippleAbility());
        bindAwakened(AbilitySlot.SKILL_4, AuthenticMutualLoveAbility.INSTANCE);
        bindAwakened(AbilitySlot.SKILL_5, rika);
        bindAwakened(AbilitySlot.ULTIMATE, wheel);
        // Base Rika: 1 Rika Smash, 2 Rika Launch, 3 Rika Haymaker (one shared cooldown).
        bindMode(RIKA, AbilitySlot.DASH, dash);
        bindMode(RIKA, AbilitySlot.GUARD, guard);
        bindMode(RIKA, AbilitySlot.SKILL_1, new RikaSmashAbility());
        bindMode(RIKA, AbilitySlot.SKILL_2, new RikaLaunchAbility());
        bindMode(RIKA, AbilitySlot.SKILL_3, new RikaHaymakerAbility());
        bindMode(RIKA, AbilitySlot.SKILL_5, rika);
        bindMode(RIKA, AbilitySlot.ULTIMATE, trueLove);
        // Awakened Rika: 1 Rika Downslam, 2 Rika Slam, 3 True Love Beam, 4 Rika Throw.
        bindMode(RIKA_AWAKENED, AbilitySlot.DASH, dash);
        bindMode(RIKA_AWAKENED, AbilitySlot.GUARD, guard);
        bindMode(RIKA_AWAKENED, AbilitySlot.SKILL_1, new RikaDownslamAbility());
        bindMode(RIKA_AWAKENED, AbilitySlot.SKILL_2, new RikaSlamAbility());
        bindMode(RIKA_AWAKENED, AbilitySlot.SKILL_3, new TrueLoveBeamAbility());
        bindMode(RIKA_AWAKENED, AbilitySlot.SKILL_4, new RikaThrowAbility());
        bindMode(RIKA_AWAKENED, AbilitySlot.SKILL_5, rika);
        bindMode(RIKA_AWAKENED, AbilitySlot.ULTIMATE, wheel);
        hook();
    }

    /** Whoever Yuta hits becomes Rika's target. */
    private static synchronized void hook() {
        if (hooked) return;
        hooked = true;
        CombatEvents.HIT_RESOLVED.add(r -> {
            if (!r.outcome().contacted() || r.target() == r.hit().attacker) return;
            AbilityCaster c = Casters.getOrNull(r.hit().attacker);
            if (c != null && c.character() instanceof YutaCharacter && r.target().isAlive()) YutaCombat.setTarget(r.hit().attacker, r.target());
        });
    }

    @Override
    public String displayName() {
        return "Yuta";
    }

    @Override
    public String title() {
        return "Cursed Partners";
    }

    @Override
    public String description() {
        return "A swordsman and the Queen of Curses: Severing Path, Resolute Slash, Outburst and Second Wind, with Rika's own moveset on the Special. True Love brings her out fully: Copy, Energy Ripple, the True Love Beam and Authentic Mutual Love.";
    }

    @Override
    public float maxEnergy() {
        return JJKConfig.get().yuta.maxCursedEnergy;
    }

    @Override
    public float regenPerSecond() {
        return JJKConfig.get().yuta.regenPerSecond;
    }

    @Override
    public MeleeMoveset melee() {
        return katana;
    }

    @Override
    public MeleeMoveset melee(AbilityCaster caster) {
        return caster.isAwakened() && YutaState.of(caster.owner).fists ? steel : katana;
    }

    @Override
    public int mode(AbilityCaster caster) {
        boolean rika = YutaState.of(caster.owner).rikaMode;
        if (caster.isAwakened()) return rika ? RIKA_AWAKENED : AWAKENED;
        return rika ? RIKA : BASE;
    }

    /** While her moveset is up his movement keys fly Rika, not him. */
    @Override
    public float movementMultiplier(AbilityCaster caster) {
        return YutaState.of(caster.owner).rikaMode ? 0f : 1f;
    }

    /** True Love lasts 60 seconds: the meter is the timer (Elbow Rush halts it). */
    @Override
    public float awakeningDrainPerSecond() {
        return JJKConfig.get().awakening.max / Math.max(1, JJKConfig.get().yuta.awakeningSeconds);
    }

    @Override
    public boolean interceptInput(AbilityCaster caster, AbilitySlot slot, Ability ability, @Nullable Entity targetHint) {
        LivingEntity user = caster.owner;
        YutaState s = YutaState.of(user);
        var cast = caster.cast() != null && !caster.cast().isFinished() ? caster.cast() : null;
        // The Copy Wheel is open: the skill keys pick a technique, the Special turns the page.
        if (s.wheelOpen && caster.isAwakened() && slot != AbilitySlot.ULTIMATE && slot != AbilitySlot.DASH && slot != AbilitySlot.GUARD) {
            CopyWheelAbility.press(caster, slot);
            return true;
        }
        if (ability instanceof ResoluteSlashAbility && cast instanceof ResoluteSlashAbility.Instance r && r.againPress()) return true;
        if (ability instanceof SeveringPathAbility && cast instanceof SecondWindAbility.Instance sw && sw.pummelPress(caster, slot)) return true;
        if (ability instanceof EnergyRippleAbility && cast instanceof EnergyRippleAbility.Instance er && er.fakeoutPress()) return true;
        // True Love Beam's key again in its wind-up: the quick beam. Firing it put Rika's moveset away, so the key now
        // shows his own move; it is the beam's own slot that counts.
        for (var o : caster.overlays()) if (o instanceof TrueLoveBeamAbility.Instance b && !b.isFinished() && b.slot() == slot && b.quickPress()) return true;
        if (cast instanceof TrueLoveBeamAbility.Instance b && b.slot() == slot && b.quickPress()) return true;
        if (ability instanceof AuthenticMutualLoveAbility && AuthenticMutualLoveAbility.ladderPress(caster, targetHint)) return true;
        if (ability instanceof RikaLaunchAbility && user.level() instanceof ServerLevel level) {
            AbilityContext ctx = new AbilityContext(caster, user, level, slot, 0, 0, targetHint);
            if (RikaLaunchAbility.feint(caster, slot, ability, ctx)) return true;
        }
        return false;
    }

    @Override
    public void tick(AbilityCaster caster) {
        LivingEntity e = caster.owner;
        YutaState s = YutaState.of(e);
        CombatState state = Combat.state(e);
        long now = e.level().getGameTime();
        // Swordsmanship's holster and necklace, for everyone to see.
        if (state.get(CombatStatus.CURSED_PARTNERS) < 20) state.set(CombatStatus.CURSED_PARTNERS, 200);
        RikaEntity r = s.rika();
        if (caster.isAwakened()) {
            if (state.get(CombatStatus.STEEL_ARM) < 20 && !caster.isCasting(TrueLoveAbility.ID)) state.set(CombatStatus.STEEL_ARM, 200);
            // The katana went back in its holster: his steel-cased fists again.
            if (!state.has(CombatStatus.KATANA) && !s.fists) {
                s.fists = true;
                caster.markDirty();
            }
            if (r == null && !caster.isCasting(TrueLoveAbility.ID) && e.level() instanceof ServerLevel) {
                r = YutaCombat.summonRika(e);
            }
            if (r != null && !r.has(RikaEntity.FULL)) r.set(RikaEntity.FULL, true);
        } else if (state.has(CombatStatus.STEEL_ARM)) {
            state.remove(CombatStatus.STEEL_ARM);
        }
        if (r == null && s.rikaMode) {
            s.rikaMode = false;
            caster.markDirty();
        }
        if (r != null) {
            r.set(RikaEntity.PILOTED, s.rikaMode && !s.rikaBusy(now));
            if (!s.rikaBusy(now) && r.has(RikaEntity.BUSY)) r.set(RikaEntity.BUSY, false);
        }
        if (s.jabAt >= 0 && now >= s.jabAt) YutaCombat.jab(e);
        if (e.tickCount % 20 == 0) YutaSync.send(e);
        applyHealth(caster);
    }

    /** Cursed Partners' 90 max HP. */
    private static void applyHealth(AbilityCaster caster) {
        AttributeInstance attr = caster.owner.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null) return;
        double want = JJKConfig.get().yuta.maxHealthShare - 1.0;
        AttributeModifier cur = attr.getModifier(HEALTH_ID);
        if (cur != null && Math.abs(cur.amount() - want) < 1e-4) return;
        attr.removeModifier(HEALTH_ID);
        if (want < 0) attr.addTransientModifier(new AttributeModifier(HEALTH_ID, want, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        if (caster.owner.getHealth() > caster.owner.getMaxHealth()) caster.owner.setHealth(caster.owner.getMaxHealth());
    }

    @Override
    public void onAssigned(AbilityCaster caster) {
        applyHealth(caster);
        YutaSync.send(caster.owner);
    }

    @Override
    public void onAwakeningChanged(AbilityCaster caster, boolean awakened) {
        if (awakened) return;
        LivingEntity e = caster.owner;
        YutaState s = YutaState.of(e);
        // True Love is over: Rika is dismissed, the casing comes off, the wheel closes.
        Combat.state(e).remove(CombatStatus.STEEL_ARM);
        s.fists = false;
        s.wheelOpen = false;
        RikaAbility.dismiss(e);
        if (e.level() instanceof ServerLevel sl) Fx.play(sl, "true_love_end", e.position().add(0, 1.2, 0), Vec3.ZERO, 1f, e.getId());
        caster.markDirty();
    }

    @Override
    public void onDeath(AbilityCaster caster) {
        // Copied techniques are lost on death.
        YutaState.clear(caster.owner);
        YutaSync.send(caster.owner);
    }

    @Override
    public void onRemoved(AbilityCaster caster) {
        AttributeInstance attr = caster.owner.getAttribute(Attributes.MAX_HEALTH);
        if (attr != null) attr.removeModifier(HEALTH_ID);
        CombatState st = Combat.state(caster.owner);
        st.remove(CombatStatus.CURSED_PARTNERS);
        st.remove(CombatStatus.KATANA);
        st.remove(CombatStatus.STEEL_ARM);
        caster.owner.setInvisible(false);
        YutaState.clear(caster.owner);
        YutaSync.send(caster.owner);
    }
}
