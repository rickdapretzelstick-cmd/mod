package dev.rick.jjk.ryu;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.util.Motion;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Restyle (JJS True Cannon Special, 17s). "Sweet!": he poses to cool down, -60% Overheat (1s). At 100% he pulls out a
 * comb and redoes his hair instead: far longer open, but back to 0% (2.75s). Awakened: he wipes his face and cracks his
 * knuckles, "Sweet!", and regains 10% of both his health and his Awakening (2s; nothing if the Awakening ends during it).
 * All of it can be interrupted, and gives nothing then.
 */
public final class RestyleAbility extends Ability {
    public static final String ID = "restyle";

    public RestyleAbility() {
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
        return RyuCombat.cfg().restyleCooldown;
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        JJKConfig.Ryu cfg = RyuCombat.cfg();
        boolean awakened = ctx.caster().isAwakened();
        boolean comb = !awakened && RyuState.of(ctx.user()).overheated();
        int length = awakened ? cfg.restyleAwakenedTicks : comb ? cfg.restyleCombTicks : cfg.restyleTicks;
        return new AbilityInstance(this, ctx) {
            @Override
            public void start() {
                Anim.play(user, awakened ? "ryu_restyle_knuckles" : comb ? "ryu_restyle_comb" : "ryu_restyle_pose");
                Fx.play(level, comb ? "ryu_comb" : "ryu_sweet", user.getEyePosition(), Vec3.ZERO, length, user.getId());
                // JJS: the comb, then his hair settling; or the quick comb and "SWEEET"; awakened, the recovery.
                RyuCombat.sfx(user, awakened ? "ryu_recovery" : comb ? "ryu_comb" : "ryu_sweet_comb", 1f);
                setPhase(0, length);
            }

            @Override
            public void tick() {
                Motion.set(user, new Vec3(0, Math.min(0, user.getDeltaMovement().y), 0));
                if (!awakened && age == (comb ? length / 2 : 10)) RyuCombat.sfx(user, comb ? "ryu_hair" : "ryu_sweet", 1f);
                if (age < length) return;
                if (awakened) {
                    if (caster.isAwakened()) {
                        user.heal(cfg.restyleHeal);
                        caster.setAwakening(Math.min(caster.maxAwakening(), caster.awakening() + caster.maxAwakening() * cfg.restyleMeter));
                        Fx.play(level, "ryu_restyle_done", user.position().add(0, 1, 0), Vec3.ZERO, 1f, user.getId());
                    }
                } else {
                    RyuCombat.addHeat(user, comb ? -100f : -cfg.restyleCool);
                    Fx.play(level, "ryu_cooled", user.getEyePosition().add(0, 0.4, 0), Vec3.ZERO, 1f, user.getId());
                }
                finish();
            }

            @Override
            public float movementMultiplier() {
                return 0f;
            }
        };
    }
}
