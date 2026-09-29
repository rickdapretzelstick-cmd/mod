package dev.rick.jjk.yuji;

import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.common.DashAbility;
import org.jetbrains.annotations.Nullable;

/** Vessel's dash: the front dash is shut off for a moment after Cursed Strikes lands or during a Black Flash chain. */
public final class YujiDashAbility extends DashAbility {
    @Override
    public @Nullable String checkActivation(AbilityContext ctx) {
        boolean front = ctx.forward() > 0.01f && Math.abs(ctx.strafe()) <= Math.abs(ctx.forward());
        boolean noInput = Math.abs(ctx.forward()) < 0.01f && Math.abs(ctx.strafe()) < 0.01f;
        if ((front || noInput) && ctx.level().getGameTime() < YujiState.of(ctx.user()).frontDashLockedUntil) return "front_dash_locked";
        return super.checkActivation(ctx);
    }
}
