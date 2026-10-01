package dev.rick.jjk.yuta;

import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.hakari.HakariCombat;
import dev.rick.jjk.util.Motion;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Rika Launch (JJS base Rika, 10s shared; 6s if it feints). Yuta motions for Rika to get behind him and give him a quick
 * boost forward. Airborne, she puts her arms under him and launches him upward instead. Used in the middle of one of
 * his own moves (or a basic attack), it feints that move into the pose, on the shorter cooldown.
 */
public final class RikaLaunchAbility extends Ability {
    public static final String ID = "rika_launch";

    public RikaLaunchAbility() {
        super(ID);
    }

    @Override
    public Kind kind() {
        return Kind.INSTANT;
    }

    @Override
    public boolean isTechnique() {
        return false;
    }

    @Override
    public int cooldown(AbilityCaster caster) {
        return YutaCombat.cfg().rikaLaunchCooldown;
    }

    @Override
    public @Nullable String checkActivation(AbilityContext ctx) {
        return YutaState.of(ctx.user()).rikaBusy(ctx.level().getGameTime()) ? "rika_busy" : null;
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        YutaCombat.shareBaseRikaCooldown(ctx.caster(), cooldown(ctx.caster()));
        YutaCombat.backToYuta(ctx.caster());
        return new Instance(this, ctx);
    }

    /**
     * Pressed during one of his moves or a basic attack: that move is feinted into the launch, and the shared cooldown
     * is the shorter one. Returns true if it went off.
     */
    static boolean feint(AbilityCaster caster, AbilitySlot slot, Ability ability, AbilityContext ctx) {
        boolean move = caster.isBusy();
        boolean m1 = !move && caster.melee.isCommitted();
        if (!move && !m1) return false;
        if (!caster.isReady(slot) || YutaState.of(caster.owner).rikaBusy(ctx.level().getGameTime())) return false;
        if (move) {
            // The feinted move is given back.
            Ability feinted = caster.cast().ability;
            for (int m = 0; m < dev.rick.jjk.core.character.JJKCharacter.MODES; m++) {
                for (AbilitySlot s : AbilitySlot.values()) if (caster.character().ability(s, m) == feinted) caster.resetSlot(s, m);
            }
            caster.interrupt("feint");
        } else {
            caster.melee.feint();
        }
        YutaCombat.shareBaseRikaCooldown(caster, YutaCombat.cfg().rikaLaunchFeintCooldown);
        YutaCombat.backToYuta(caster);
        caster.begin(new Instance(ability, ctx));
        return true;
    }

    static final class Instance extends AbilityInstance {
        private final boolean air;
        private final Vec3 dir;
        private final RikaEntity rika;

        Instance(Ability ability, AbilityContext ctx) {
            super(ability, ctx);
            air = Combat.isAirborne(ctx.user()) || !ctx.user().onGround();
            dir = HakariCombat.flat(ctx.user());
            rika = YutaCombat.summonRika(ctx.user());
        }

        @Override
        public void start() {
            YutaCombat.busy(user, 12);
            Anim.play(user, air ? "yuta_rika_launch_air" : "yuta_rika_launch");
            if (rika != null) Anim.playOn(rika, air ? "rika_launch_air" : "rika_launch");
            setPhase(0, 6);
        }

        @Override
        public void tick() {
            if (age < 5) {
                // She gets behind him (under him in the air).
                if (rika != null) rika.moveTo(user.position().add(dir.scale(air ? -0.4 : -1.3)).add(0, air ? -1.6 : -0.2, 0), 2.4, 2);
                Motion.set(user, new Vec3(user.getDeltaMovement().x * 0.5, air ? 0.02 : Math.min(0, user.getDeltaMovement().y), user.getDeltaMovement().z * 0.5));
                return;
            }
            if (age == 5) {
                double sp = YutaCombat.cfg().rikaLaunchSpeed;
                Motion.set(user, air ? dir.scale(sp * 0.25).add(0, sp * 0.85, 0) : dir.scale(sp).add(0, 0.42, 0));
                user.resetFallDistance();
                Statuses.apply(user, CombatStatus.HOVER, air ? 0 : 6);
                if (rika != null) Fx.play(level, "rika_launch", user.position().add(0, 0.6, 0), air ? new Vec3(0, 1, 0) : dir, 1f, rika.getId());
            }
            if (age >= 12) finish();
        }

        @Override
        public float movementMultiplier() {
            return age < 5 ? 0f : 1f;
        }

        @Override
        public void end() {
            YutaCombat.free(user);
            user.resetFallDistance();
        }
    }
}
