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

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        return new AbilityInstance(this, ctx) {
            @Override
            public void start() {
                Combat.state(user).startGuard(level.getGameTime());
                // Gojo doesn't need to block (JJS): Infinity lets him just float there at ease.
                var character = dev.rick.jjk.core.ability.Casters.get(user).character();
                Anim.play(user, character != null && dev.rick.jjk.gojo.GojoCharacter.ID.equals(character.id) ? "guard_gojo" : "guard");
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
