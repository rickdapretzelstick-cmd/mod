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
import dev.rick.jjk.hakari.HakariCombat;
import dev.rick.jjk.util.Destruction;
import dev.rick.jjk.util.Motion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * "You weren't invited." (JJS True Cannon awakened 4, 20s). He winds up a powerful right jab and lunges at his target,
 * punching them with immense force, sending them flying off to the left: 20, or 40 if held 1.9 seconds. Unblockable.
 * Against a wall, the punch sends its debris flying 100 studs (250 held), carrying along whoever it meets (20 a wall);
 * more walls in its way are torn out and thrown with it. The finisher shatters them with the debris.
 */
public final class NotInvitedAbility extends Ability {
    public static final String ID = "not_invited";

    public NotInvitedAbility() {
        super(ID);
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
    public int cooldown(AbilityCaster caster) {
        return RyuCombat.cfg().invitedCooldown;
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        return new AbilityInstance(this, ctx) {
            private int releasedAt = -1;
            private boolean full;
            @Nullable private LivingEntity target;
            private int punchAt = -1, endAt = -1;

            @Override
            public void start() {
                Anim.play(user, "ryu_invited_windup");
                setPhase(0, RyuCombat.cfg().invitedHoldTicks);
            }

            @Override
            public void tick() {
                JJKConfig.Ryu cfg = RyuCombat.cfg();
                if (endAt >= 0) {
                    if (age >= endAt) finish();
                    return;
                }
                if (releasedAt < 0) {
                    Motion.set(user, user.getDeltaMovement().multiply(0.3, 1, 0.3));
                    if (!full && age >= cfg.invitedHoldTicks) {
                        full = true;
                        Fx.play(level, "ryu_invited_charged", user.position().add(0, 1.2, 0), Vec3.ZERO, 1f, user.getId());
                    }
                    if ((!held && age >= 6) || age >= cfg.invitedHoldTicks + 14) {
                        releasedAt = age;
                        target = HakariCombat.aim(user, 7, null);
                        Anim.play(user, "ryu_invited_lunge");
                    }
                    return;
                }
                int k = age - releasedAt;
                if (punchAt < 0) {
                    // The lunge: at them, or at the wall in front.
                    Vec3 flat = HakariCombat.flat(user);
                    if (target != null && target.isAlive()) HakariCombat.faceTowards(user, target.getEyePosition());
                    BlockPos wall = wallAhead(flat);
                    LivingEntity t = HakariCombat.firstInFront(user, 1.8, 1.5, 2.2);
                    if (t != null || wall != null || k >= 6) {
                        punchAt = age;
                        Anim.play(user, "ryu_invited_punch");
                        if (t != null) punch(t, cfg, full);
                        else if (wall != null) throwWall(wall, flat, cfg, full);
                        else Fx.play(level, "ryu_invited_whiff", user.getEyePosition(), flat, 1f, user.getId());
                        endAt = age + 14;
                        return;
                    }
                    HakariCombat.drive(user, flat, 0.85);
                }
            }

            @Nullable
            private BlockPos wallAhead(Vec3 flat) {
                for (double d = 0.8; d <= 1.9; d += 0.5) {
                    BlockPos p = BlockPos.containing(user.position().add(flat.scale(d)).add(0, 1.0, 0));
                    var st = level.getBlockState(p);
                    if (!st.isAir() && !st.getCollisionShape(level, p).isEmpty()) return p;
                }
                return null;
            }

            private void punch(LivingEntity t, JJKConfig.Ryu cfg, boolean full) {
                float dmg = cfg.invitedDamage * (full ? 2 : 1);
                if (RyuCombat.finishable(t) || t.getHealth() <= dmg) {
                    RyuCombat.execute(user, t, ID, "invited_finisher");
                    return;
                }
                Vec3 flat = HakariCombat.flat(user);
                Vec3 left = new Vec3(flat.z, 0, -flat.x);
                HakariCombat.hit(RyuCombat.strike(user, ID, dmg, true).tag(AttackTag.EXPLOSION)
                        .knockback(Knockback.set(left.scale(full ? 2.4 : 1.8).add(flat.scale(0.3)).add(0, 0.45, 0))).hitstun(40)
                        .status(CombatStatus.LAUNCHED, 40).fx("ryu_invited_hit", full ? 1.8f : 1.4f).build(), t);
                Fx.shake(level, t.position(), 40, full ? 1.4f : 1f, 14);
            }

            private void throwWall(BlockPos wall, Vec3 flat, JJKConfig.Ryu cfg, boolean full) {
                // The wall section he punched comes away whole and flies.
                Destruction.sphere(level, Vec3.atCenterOf(wall), 1.7, 50f, 30, user, null);
                Fx.play(level, "ryu_wall_punch", Vec3.atCenterOf(wall), flat, 1f, user.getId());
                Fx.shake(level, Vec3.atCenterOf(wall), 40, 1.2f, 14);
                caster.addOverlay(new Debris(ability, caster, user, Vec3.atCenterOf(wall), flat, full ? cfg.invitedWallHeldRange : cfg.invitedWallRange,
                        cfg.invitedWallDamage));
            }

            @Override
            public float movementMultiplier() {
                return 0.2f;
            }
        };
    }

    /** Thrown debris: flies on, picking up whoever it meets and tearing out the walls it hits (20 for every wall in it). */
    static final class Debris extends AbilityInstance {
        private Vec3 pos;
        private final Vec3 dir;
        private final double range;
        private double travelled;
        private float damage;
        private int walls = 1;
        private final List<LivingEntity> carried = new ArrayList<>();

        Debris(Ability a, AbilityCaster caster, LivingEntity user, Vec3 from, Vec3 dir, double range, float damage) {
            super(a, new AbilityContext(caster, user, (net.minecraft.server.level.ServerLevel) user.level(), dev.rick.jjk.core.ability.AbilitySlot.SKILL_4,
                    0, 0, null));
            this.pos = from;
            this.dir = dir.normalize();
            this.range = range;
            this.damage = damage;
        }

        @Override
        public boolean exclusive() {
            return false;
        }

        @Override
        public void tick() {
            double step = 1.6;
            pos = pos.add(dir.scale(step));
            travelled += step;
            if (age % 2 == 0) Fx.play(level, "ryu_debris", pos, dir, walls, user.getId());
            // Another wall in its way: torn out and thrown with it.
            BlockPos p = BlockPos.containing(pos);
            var st = level.getBlockState(p);
            if (!st.isAir() && !st.getCollisionShape(level, p).isEmpty()) {
                if (Destruction.sphere(level, pos, 1.6, 50f, 24, user, null) > 0 && walls < 4) {
                    walls++;
                    damage += RyuCombat.cfg().invitedWallDamage * 0.5f;
                    Fx.play(level, "ryu_wall_punch", pos, dir, 0.7f, user.getId());
                } else if (Destruction.budgetExhausted(level) || !Destruction.allowed(level)) {
                    travelled = range;
                }
            }
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos, pos).inflate(1.6), e -> Targeting.canTarget(user, e))) {
                if (!carried.contains(e)) {
                    carried.add(e);
                    if (RyuCombat.finishable(e) || e.getHealth() <= damage) {
                        RyuCombat.execute(user, e, ID, "invited_finisher");
                        continue;
                    }
                    HakariCombat.hit(RyuCombat.strike(user, ID, damage, true).tag(AttackTag.ENVIRONMENTAL).knockback(Knockback.HOLD).hitstun(30)
                            .fx("ryu_invited_hit", 1.2f).build(), e);
                }
            }
            for (LivingEntity e : carried) {
                if (e.isAlive()) Motion.set(e, pos.subtract(e.position()).scale(0.6).add(dir.scale(step * 0.6)));
            }
            if (travelled >= range) {
                Fx.play(level, "ryu_debris_break", pos, dir, walls, user.getId());
                for (LivingEntity e : carried) {
                    if (e.isAlive()) HakariCombat.hit(RyuCombat.strike(user, ID, 0, true).knockback(Knockback.set(dir.scale(0.9).add(0, 0.3, 0)))
                            .hitstun(16).status(CombatStatus.LAUNCHED, 16).build(), e);
                }
                finish();
            }
        }
    }
}
