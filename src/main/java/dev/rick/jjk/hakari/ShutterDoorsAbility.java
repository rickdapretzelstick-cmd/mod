package dev.rick.jjk.hakari;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.util.Aim;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 2 — Shutter Doors. The shutter doors of the "Private Pure Love Train" pachinko game rise out of the ground either side
 * of the target (up to 25 studs away) and close on their torso, stunning them in place, and Hakari's melee chain jumps
 * to its third hit so he can follow straight up. A target low enough is shut in completely (the finisher). Doors that
 * catch nobody linger for 7 seconds: Hakari can bounce high off them, and a ragdolled enemy bounces on them. See
 * {@link ShutterTrap}. Pressed during Reserve Balls' or Fever Breaker's wind-up it combines with them instead.
 */
public final class ShutterDoorsAbility extends Ability {
    public static final String ID = "shutter_doors";

    public ShutterDoorsAbility() {
        super(ID);
    }

    @Override
    public Kind kind() {
        return Kind.INSTANT;
    }

    @Override
    public float cost(AbilityCaster caster) {
        return JJKConfig.get().hakari.shutterCost;
    }

    @Override
    public int cooldown(AbilityCaster caster) {
        return JJKConfig.get().hakari.shutterCooldown;
    }

    /** The ground under a point (the doors rise out of it). */
    static Vec3 ground(net.minecraft.world.level.Level level, Vec3 p) {
        var hit = level.clip(new net.minecraft.world.level.ClipContext(p.add(0, 0.5, 0), p.add(0, -6, 0),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? p : hit.getLocation();
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        JJKConfig.Hakari cfg = JJKConfig.get().hakari;
        Aim.Target aim = Aim.point(ctx.user(), cfg.shutterRange, 12, ctx.targetHint());
        LivingEntity target = aim.entity();
        Vec3 spot = target != null ? target.position() : ground(ctx.level(), aim.point());
        return new AbilityInstance(this, ctx) {
            @Override
            public void start() {
                Anim.play(user, "shutter_sign");
                setPhase(0, 6);
                ShutterTrap.open(level, user, ShutterTrap.Mode.STRIKE, spot, target, cfg.shutterDamage);
                caster.melee.setChainIndex(2, level.getGameTime());
                HakariCombat.visual(user, 1);
            }

            @Override
            public void tick() {
                if (age >= 6) finish();
            }
        };
    }
}
