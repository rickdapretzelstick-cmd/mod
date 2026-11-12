package dev.rick.jjk.ryu;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.combat.Targeting;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.hitbox.HitShape;
import dev.rick.jjk.core.hitbox.HitboxQuery;
import dev.rick.jjk.hakari.HakariCombat;
import dev.rick.jjk.util.Motion;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Appetizer (JJS True Cannon 4, 18s). Two quick Granite Blasts from his forehead (80 studs, stun, 4 each), then he
 * caresses his hair to let out a third, vertical ray that ragdolls everyone 60 studs in front of him up and toward him (8);
 * 10% Overheat a blast. Overheating (even after one) stops the remaining blasts and skips to the final ray, which then
 * throws them away instead; the animation of the blasts still plays out. The finisher chars them like Granite Blast.
 */
public final class AppetizerAbility extends Ability {
    public static final String ID = "appetizer";
    private static final int[] BLASTS = {8, 17};
    private static final int RAY = 32, END = 44;
    /** The sweep: the beam starts pitched down at the floor ahead and rises through the horizon to straight up. */
    public static final int SWEEP_TICKS = 10;
    public static final double PITCH_FROM = 18, PITCH_TO = -78;

    /** The beam's direction at tick {@code k} of the sweep, from his flat facing {@code flat}. */
    public static Vec3 sweepDir(Vec3 flat, int k) {
        double pitch = Math.toRadians(PITCH_FROM + (PITCH_TO - PITCH_FROM) * Math.min(1.0, k / (double) (SWEEP_TICKS - 1)));
        return new Vec3(flat.x * Math.cos(pitch), -Math.sin(pitch), flat.z * Math.cos(pitch)).normalize();
    }

    public AppetizerAbility() {
        super(ID);
    }

    @Override
    public Kind kind() {
        return Kind.INSTANT;
    }

    @Override
    public int cooldown(AbilityCaster caster) {
        return RyuCombat.cfg().appetizerCooldown;
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        return new AbilityInstance(this, ctx) {
            private boolean cut;
            private final java.util.Set<LivingEntity> swept = new java.util.HashSet<>();

            @Override
            public void start() {
                Anim.play(user, "ryu_appetizer");
                setPhase(0, END);
            }

            @Override
            public void tick() {
                JJKConfig.Ryu cfg = RyuCombat.cfg();
                Motion.set(user, user.getDeltaMovement().multiply(0.3, 1, 0.3));
                for (int at : BLASTS) {
                    if (age != at) continue;
                    if (!RyuCombat.canDischarge(user)) {
                        cut = true;
                        Fx.play(level, "ryu_smoke", user.getEyePosition().add(0, 0.45, 0), Vec3.ZERO, 1f, user.getId());
                        continue;
                    }
                    Vec3 from = RyuCombat.cannon(user), dir = user.getLookAngle().normalize();
                    Vec3 end = RyuCombat.rayEnd(user, from, dir, cfg.appetizerRange);
                    Fx.play(level, "granite_blast", from, end.subtract(from), 0.8f, user.getId());
                    RyuCombat.addHeat(user, cfg.heatAppetizer);
                    List<RyuCombat.RayHit> hits = RyuCombat.ray(user, from, dir, cfg.appetizerRange, 0.35, false);
                    if (!hits.isEmpty()) {
                        LivingEntity t = hits.getFirst().target();
                        HakariCombat.hit(RyuCombat.blast(user, ID, cfg.appetizerBlast, from).knockback(Knockback.HOLD).hitstun(22)
                                .fx("ryu_ray_hit", 0.9f).sparing().build(), t);
                    }
                    if (!RyuCombat.canDischarge(user)) cut = true;
                }
                if (age >= RAY && age < RAY + SWEEP_TICKS) {
                    // The beam: fired from the cannon at the floor in front of him and swept up through everything to
                    // the sky, over ten ticks; whoever it crosses is lifted up and in (pushed away when he is overheated).
                    Vec3 flat = HakariCombat.flat(user);
                    Vec3 from = RyuCombat.cannon(user), dir = sweepDir(flat, age - RAY);
                    if (age == RAY) {
                        Fx.play(level, "appetizer_beam", from, flat.scale(cfg.appetizerRayRange), cut ? 1f : 0f, user.getId());
                        Fx.shake(level, from, 30, 0.7f, 14);
                    }
                    Vec3 base = user.position().add(flat.scale(1));
                    for (RyuCombat.RayHit rh : RyuCombat.ray(user, from, dir, cfg.appetizerRayRange, 1.4, false)) {
                        LivingEntity t = rh.target();
                        if (!swept.add(t) || !Targeting.canTarget(user, t)) continue;
                        if (RyuCombat.finishable(t)) {
                            RyuCombat.execute(user, t, ID, "appetizer_finisher");
                            continue;
                        }
                        Vec3 toward = user.position().subtract(t.position());
                        toward = new Vec3(toward.x, 0, toward.z).normalize();
                        Vec3 kb = cut ? flat.scale(0.9).add(0, 0.7, 0) : toward.scale(0.45).add(0, 1.0, 0);
                        HakariCombat.hit(RyuCombat.blast(user, ID, cfg.appetizerRay, base).tag(AttackTag.OTG).knockback(Knockback.set(kb)).hitstun(26)
                                .status(CombatStatus.LAUNCHED, 26).fx("ryu_ray_hit", 1.2f).wave("ryu.appetizer_ray").build(), t);
                    }
                }
                if (age >= END) finish();
            }

            @Override
            public float movementMultiplier() {
                return 0.1f;
            }
        };
    }
}
