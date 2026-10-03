package dev.rick.jjk.hakari;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.registry.ModDamageTypes;
import dev.rick.jjk.util.Motion;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Jackpot 4 — Energy Surge. A surging dash that hits whoever it passes through, then Hakari's cursed energy bursts
 * and he is simply somewhere else — above his target — and comes down on them with a kick. Dash → hit → vanish →
 * reappear → aerial kick. The disappearance is a burst of his energy (he's untouchable for the instant he's gone), not a
 * blink.
 */
public final class EnergySurgeAbility extends Ability {
    public static final String ID = "energy_surge";

    public EnergySurgeAbility() {
        super(ID);
    }

    @Override
    public Kind kind() {
        return Kind.INSTANT;
    }

    @Override
    public int cooldown(AbilityCaster caster) {
        return JJKConfig.get().hakari.surgeCooldown;
    }

    private static boolean free(AbilityContext ctx, Vec3 feet) {
        LivingEntity u = ctx.user();
        AABB box = u.getDimensions(u.getPose()).makeBoundingBox(feet);
        return ctx.level().isLoaded(net.minecraft.core.BlockPos.containing(feet)) && ctx.level().noCollision(u, box);
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        LivingEntity aimed = HakariCombat.aim(ctx.user(), 18, ctx.targetHint());
        return new AbilityInstance(this, ctx) {
            private LivingEntity target = aimed;
            private int vanishAt = -1, appearAt = -1, kickAt = -1;
            private Vec3 dashDir;

            @Override
            public void start() {
                Anim.play(user, "surge_dash");
                setPhase(0, 30);
                dashDir = target != null ? target.position().subtract(user.position()) : HakariCombat.flat(user);
                dashDir = new Vec3(dashDir.x, 0, dashDir.z).normalize();
                Fx.play(level, "surge_dash", user.position().add(0, 1, 0), dashDir, 1f, user.getId());
            }

            @Override
            public void tick() {
                JJKConfig.Hakari cfg = JJKConfig.get().hakari;
                if (vanishAt < 0) {
                    HakariCombat.drive(user, dashDir, 1.2);
                    Hit pass = Hit.builder(user, ID).type(ModDamageTypes.MELEE).damage(cfg.surgeDashDamage).tag(AttackTag.MELEE, AttackTag.TECHNIQUE, AttackTag.UNBLOCKABLE)
                            .origin(user.getEyePosition()).knockback(Knockback.directional(new Vec3(0, 1, 0), 0.5, 0.55)).hitstun(26)
                            .status(CombatStatus.LAUNCHED, 20).fx("surge_hit", 1f).build();
                    for (LivingEntity t : HakariCombat.front(user, 1.6, 1.8, 2.2)) {
                        if (HakariCombat.hit(pass, t).connected() && (target == null || !target.isAlive())) target = t;
                    }
                    if (age >= cfg.surgeDashTicks) {
                        vanishAt = age;
                        Motion.set(user, Vec3.ZERO);
                        Statuses.apply(user, CombatStatus.EVADING, 6);
                        user.setInvisible(true);
                        Fx.play(level, "surge_vanish", user.position().add(0, 1, 0), dashDir, 1f, user.getId());
                        setPhase(1, 4);
                    }
                    return;
                }
                if (appearAt < 0 && age >= vanishAt + 3) {
                    appearAt = age;
                    // Reappear above and a little behind the target (or ahead, if nobody was hit), as long as it's free.
                    Vec3 spot;
                    if (target != null && target.isAlive()) {
                        Vec3 back = target.position().subtract(user.position());
                        back = new Vec3(back.x, 0, back.z).lengthSqr() < 1e-4 ? dashDir : new Vec3(back.x, 0, back.z).normalize();
                        spot = target.position().add(back.scale(0.8)).add(0, 3.2, 0);
                    } else {
                        spot = user.position().add(dashDir.scale(3)).add(0, 3, 0);
                    }
                    for (int i = 0; i < 4 && !free(ctx, spot); i++) spot = spot.add(0, -0.8, 0);
                    if (!free(ctx, spot)) spot = user.position().add(0, 0.2, 0);
                    // Height the teleport gives is never fall damage (only falling below where they left counts).
                    dev.rick.jjk.core.combat.LaunchHeight.displaced(user, user.getY());
                    user.teleportTo(spot.x, spot.y, spot.z);
                    if (target != null) HakariCombat.faceTowards(user, target.getBoundingBox().getCenter());
                    user.setInvisible(false);
                    Motion.set(user, new Vec3(0, 0.1, 0));
                    Fx.play(level, "surge_appear", spot.add(0, 1, 0), dashDir, 1f, user.getId());
                    Anim.play(user, "surge_kick");
                    setPhase(2, 8);
                    kickAt = age + 4;
                    return;
                }
                if (age == kickAt) {
                    // The aerial kick: straight down onto them.
                    Motion.set(user, new Vec3(0, -1.1, 0));
                    Hit kick = Hit.builder(user, ID).type(ModDamageTypes.MELEE).damage(cfg.surgeKickDamage).tag(AttackTag.MELEE, AttackTag.HEAVY, AttackTag.OTG, AttackTag.UNBLOCKABLE)
                            .origin(user.getEyePosition()).knockback(Knockback.set(new Vec3(0, -1.2, 0))).hitstun(28).status(CombatStatus.SPIKED, 16)
                            .fx("surge_kick_hit", 1.3f).build();
                    Vec3 below = user.position().add(0, -1.2, 0);
                    var shape = dev.rick.jjk.core.hitbox.HitShape.capsule(user.position().add(0, 0.5, 0), below.add(0, -2.2, 0), 1.3);
                    HakariCombat.hitAll(kick, dev.rick.jjk.core.hitbox.HitboxQuery.targets(user, shape, 0.3, false));
                    Fx.play(level, "surge_kick", user.position(), new Vec3(0, -1, 0), 1f, user.getId());
                    Fx.shake(level, user.position(), 18, 0.8f, 10);
                }
                if (kickAt >= 0 && age >= kickAt + 8) finish();
            }

            @Override
            public void end() {
                user.setInvisible(false);
            }
        };
    }
}
