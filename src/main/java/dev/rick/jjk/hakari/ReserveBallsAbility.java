package dev.rick.jjk.hakari;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.domain.DomainInstance;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.entity.PachinkoBallEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 1 — Reserve Balls. Hakari flicks one small steel ball. It flies about 65 studs, ricocheting off whatever it meets
 * (for as long as it has distance left, or much further inside his domain). A target it hits is stunned for a moment,
 * or ragdolled away if the ball had travelled 15 studs or less.
 * <ul>
 *   <li>Shutter Doors pressed during the wind-up: the doors manifest where the ball lands and bounce a target it stunned.</li>
 *   <li>Inside Idle Death Gamble: where the ball lands is remembered, and pressing Reserve Balls again within 8 seconds
 *       is Renewal (see {@link #renew}).</li>
 * </ul>
 */
public final class ReserveBallsAbility extends Ability {
    public static final String ID = "reserve_balls";

    public ReserveBallsAbility() {
        super(ID);
    }

    @Override
    public Kind kind() {
        return Kind.INSTANT;
    }

    @Override
    public float cost(AbilityCaster caster) {
        return JJKConfig.get().hakari.ballsCost;
    }

    @Override
    public int cooldown(AbilityCaster caster) {
        return JJKConfig.get().hakari.ballsCooldown;
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        LivingEntity target = HakariCombat.aim(ctx.user(), 28, ctx.targetHint());
        return new Flick(this, ctx, target);
    }

    static final class Flick extends AbilityInstance implements DoorCombo {
        @Nullable private final LivingEntity target;
        private boolean doors;

        Flick(Ability ability, AbilityContext ctx, @Nullable LivingEntity target) {
            super(ability, ctx);
            this.target = target;
        }

        @Override
        public void start() {
            Anim.play(user, "reserve_balls");
            setPhase(0, JJKConfig.get().hakari.ballsWindup + 6);
            HakariCombat.visual(user, 1);
        }

        @Override
        public boolean acceptsDoors() {
            return !doors && age < JJKConfig.get().hakari.ballsWindup;
        }

        @Override
        public void addDoors() {
            doors = true;
            HakariCombat.visual(user, 1);
            Fx.play(level, "combo_doors", user.position().add(0, 1.4, 0), HakariCombat.flat(user), 1f, user.getId());
        }

        @Override
        public void tick() {
            JJKConfig.Hakari cfg = JJKConfig.get().hakari;
            if (age == cfg.ballsWindup) {
                Vec3 hand = user.getEyePosition().add(HakariCombat.flat(user).scale(0.7)).add(0, -0.3, 0);
                Vec3 aimAt = target != null && target.isAlive() ? target.getBoundingBox().getCenter() : user.getEyePosition().add(user.getLookAngle().scale(20));
                Vec3 dir = aimAt.subtract(hand).normalize().add(0, 0.03, 0).normalize();
                PachinkoBallEntity.fire(level, user, hand, dir, doors);
                Fx.play(level, "ball_throw", hand, dir, doors ? 1.2f : 1f, user.getId());
            }
            if (age >= cfg.ballsWindup + 5) finish();
        }

        @Override
        public float movementMultiplier() {
            return 0.6f;
        }
    }

    /**
     * Where a ball stopped: the doors of the combination, and (inside Hakari's own domain, if it hit someone) the moment
     * Renewal rewinds to. {@code stunned} is the target the ball stunned (not ragdolled), if any; {@code hit} whether it
     * landed on anyone at all.
     */
    public static void landed(ServerLevel level, LivingEntity owner, Vec3 at, boolean doors, @Nullable LivingEntity stunned, boolean hit) {
        if (doors) {
            Vec3 spot = stunned != null ? stunned.position() : ShutterDoorsAbility.ground(level, at);
            ShutterTrap.open(level, owner, stunned != null ? ShutterTrap.Mode.BOUNCE : ShutterTrap.Mode.STRIKE, spot, stunned,
                    stunned != null ? JJKConfig.get().hakari.comboDoorDamage : JJKConfig.get().hakari.shutterDamage);
        }
        Gamble g = IdleDeathGamble.gambleOf(owner);
        AbilityCaster c = dev.rick.jjk.core.ability.Casters.getOrNull(owner);
        if (hit && g != null && c != null && !c.isAwakened()) {
            DomainInstance d = g.domain;
            List<LivingEntity> everyone = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class,
                    new net.minecraft.world.phys.AABB(d.center, d.center).inflate(d.radius + 1), e -> e.isAlive() && d.contains(e)));
            if (!everyone.contains(owner)) everyone.add(owner);
            HakariState.of(owner).renewal = HakariState.snapshot(level.getGameTime() + JJKConfig.get().hakari.renewalWindow, owner.getHealth(), everyone);
            Fx.play(level, "renewal_mark", at, Vec3.ZERO, 1f, owner.getId());
        }
    }

    /**
     * Renewal: a "Consecutive Effect" inside Idle Death Gamble. Pressing Reserve Balls again within 8 seconds of a ball
     * landing rewinds to that moment: everyone goes back where they stood, and any damage Hakari took since is undone.
     * Returns false if there is nothing to rewind to.
     */
    static boolean renew(LivingEntity owner) {
        if (!(owner.level() instanceof ServerLevel level)) return false;
        HakariState hs = HakariState.of(owner);
        HakariState.Renewal r = hs.renewal;
        if (r == null || level.getGameTime() > r.expires() || IdleDeathGamble.gambleOf(owner) == null) {
            hs.renewal = null;
            return false;
        }
        hs.renewal = null;
        for (HakariState.Spot s : r.spots()) {
            LivingEntity e = s.entity();
            if (!e.isAlive() || e.level() != level) continue;
            Fx.play(level, "renewal_trail", e.position().add(0, 1, 0), s.pos().subtract(e.position()), 1f, owner.getId());
            e.teleportTo(s.pos().x, s.pos().y, s.pos().z);
            e.setDeltaMovement(Vec3.ZERO);
            e.needsSync = true;
            e.resetFallDistance();
        }
        if (owner.getHealth() < r.health()) owner.setHealth(r.health());
        Fx.play(level, "renewal", owner.position().add(0, 1.2, 0), Vec3.ZERO, 1f, owner.getId());
        return true;
    }
}
