package dev.rick.jjk.core.ability.common;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.util.Motion;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Quick evasive burst in the input direction with a few invulnerable frames. Works in the air. */
public class DashAbility extends Ability {
    public DashAbility() {
        super("dash");
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
        return JJKConfig.get().dash.cooldown;
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        JJKConfig.Dash cfg = JJKConfig.get().dash;
        var user = ctx.user();
        boolean air = Combat.isAirborne(user);
        Vec3 dir = ctx.inputDirection();
        dir = new Vec3(dir.x, 0, dir.z);
        if (dir.lengthSqr() < 1e-4) dir = new Vec3(user.getLookAngle().x, 0, user.getLookAngle().z);
        dir = dir.normalize();
        double speed = air ? cfg.airSpeed : cfg.speed;
        Motion.set(user, new Vec3(dir.x * speed, air ? 0.12 : Math.max(0.05, user.getDeltaMovement().y), dir.z * speed));
        Statuses.apply(user, CombatStatus.EVADING, cfg.invulnerabilityTicks);
        if (air) Statuses.apply(user, CombatStatus.HOVER, 5);
        Anim.play(user, "dash_" + direction(ctx));
        Fx.play(ctx.level(), "dash", user.position().add(0, 0.9, 0), dir, 1f, user.getId());
        return null;
    }

    private static String direction(AbilityContext ctx) {
        if (Math.abs(ctx.strafe()) > Math.abs(ctx.forward())) return ctx.strafe() > 0 ? "left" : "right";
        return ctx.forward() < -0.01f ? "back" : "forward";
    }
}
