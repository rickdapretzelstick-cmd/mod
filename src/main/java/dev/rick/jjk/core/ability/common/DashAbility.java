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

    /**
     * Ragdoll escape: dashing while ragdolled (launched, spiked or knocked down) clears the ragdoll and dashes out with
     * the usual invulnerability. Grabs, pulls, guard breaks and domain stuns can't be escaped. Returns true if used.
     */
    public static boolean ragdollEscape(AbilityCaster caster, dev.rick.jjk.core.combat.CombatState state, float forward, float strafe) {
        var user = caster.owner;
        boolean ragdolled = state.has(CombatStatus.LAUNCHED) || state.has(CombatStatus.SPIKED) || state.has(CombatStatus.KNOCKDOWN);
        if (!ragdolled) return false;
        for (CombatStatus hold : new CombatStatus[] {CombatStatus.GRABBED, CombatStatus.PULLED, CombatStatus.OVERLOAD, CombatStatus.GUARD_BROKEN,
                CombatStatus.CLASHING, CombatStatus.AWAKENING, CombatStatus.GAMBLING, CombatStatus.TRUE_RAGDOLL, CombatStatus.STOPPED}) {
            if (state.has(hold)) return false;
        }
        long now = user.level().getGameTime();
        if (now < state.ragdollEscapeReadyAt) return false;
        JJKConfig.Dash cfg = JJKConfig.get().dash;
        state.ragdollEscapeReadyAt = now + cfg.ragdollEscapeCooldown;
        for (CombatStatus s : new CombatStatus[] {CombatStatus.HITSTUN, CombatStatus.LAUNCHED, CombatStatus.SPIKED, CombatStatus.KNOCKDOWN}) state.remove(s);
        Vec3 look = new Vec3(user.getLookAngle().x, 0, user.getLookAngle().z);
        if (look.lengthSqr() < 1e-4) look = new Vec3(0, 0, 1);
        look = look.normalize();
        Vec3 side = new Vec3(-look.z, 0, look.x);
        Vec3 dir = look.scale(forward).add(side.scale(-strafe));
        if (dir.lengthSqr() < 1e-4) dir = look.scale(-1); // no input: back away
        dir = dir.normalize();
        Motion.set(user, new Vec3(dir.x * cfg.speed * 0.8, 0.25, dir.z * cfg.speed * 0.8));
        Statuses.apply(user, CombatStatus.EVADING, cfg.invulnerabilityTicks + 6);
        Statuses.apply(user, CombatStatus.WAKEUP, 10);
        Anim.play(user, "dash_back");
        if (user.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            Fx.play(sl, "dash", user.position().add(0, 0.9, 0), dir, 1f, user.getId());
        }
        caster.startCooldown(dev.rick.jjk.core.ability.AbilitySlot.DASH, cfg.cooldown);
        return true;
    }

    private static String direction(AbilityContext ctx) {
        if (Math.abs(ctx.strafe()) > Math.abs(ctx.forward())) return ctx.strafe() > 0 ? "left" : "right";
        return ctx.forward() < -0.01f ? "back" : "forward";
    }
}
