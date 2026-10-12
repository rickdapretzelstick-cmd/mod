package dev.rick.jjk.core.ability.common;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.combat.Combat;
import org.jetbrains.annotations.Nullable;

/** Hold to guard. See {@link GuardDefense} for what guarding does to incoming attacks. */
public class GuardAbility extends Ability {
    public GuardAbility() {
        super("guard");
    }

    @Override
    public Kind kind() {
        return Kind.HOLD;
    }

    @Override
    public boolean isTechnique() {
        return false;
    }

    /** The guard's pose clip for this user. */
    protected String anim(net.minecraft.world.entity.LivingEntity user) {
        // Gojo doesn't need to block (JJS): Infinity lets him just float there at ease.
        var character = dev.rick.jjk.core.ability.Casters.get(user).character();
        if (character != null && dev.rick.jjk.gojo.GojoCharacter.ID.equals(character.id)) return "guard_gojo";
        // Swordsmanship: with the katana out, Yuta blocks with the blade.
        if (character != null && dev.rick.jjk.yuta.YutaCharacter.ID.equals(character.id)
                && Combat.has(user, dev.rick.jjk.core.combat.CombatStatus.KATANA)) return "yuta_guard_katana";
        return "guard";
    }

    /** As the guard goes up (a weapon's own guard settles what it was doing first). */
    protected void onGuard(net.minecraft.world.entity.LivingEntity user) {}

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        return new AbilityInstance(this, ctx) {
            @Override
            public void start() {
                Combat.state(user).startGuard(level.getGameTime());
                onGuard(user);
                Anim.play(user, anim(user));
            }

            @Override
            public void tick() {
                if (!held || !Combat.state(user).isGuarding()) finish();
            }

            @Override
            public void end() {
                Combat.state(user).stopGuard();
                Anim.stop(user);
            }

            @Override
            public float movementMultiplier() {
                return JJKConfig.get().guard.guardMoveSpeed;
            }
        };
    }
}
