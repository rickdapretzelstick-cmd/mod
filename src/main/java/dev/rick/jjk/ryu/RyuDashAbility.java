package dev.rick.jjk.ryu;

import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.ability.common.DashAbility;
import org.jetbrains.annotations.Nullable;

/** True Cannon's dash: the ordinary one, remembering when he last dashed forward (Granite Blast's dash variant). */
public final class RyuDashAbility extends DashAbility {
    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        boolean front = ctx.forward() > 0.01f && Math.abs(ctx.strafe()) <= Math.abs(ctx.forward())
                || Math.abs(ctx.forward()) < 0.01f && Math.abs(ctx.strafe()) < 0.01f;
        if (front) RyuState.of(ctx.user()).frontDashAt = ctx.level().getGameTime();
        return super.activate(ctx);
    }
}
