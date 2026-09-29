package dev.rick.jjk.yuji;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.hitbox.HitShape;
import dev.rick.jjk.core.hitbox.HitboxQuery;
import dev.rick.jjk.hakari.HakariCombat;
import dev.rick.jjk.util.Aim;
import dev.rick.jjk.util.Destruction;
import dev.rick.jjk.util.Motion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Dismantle (JJS King of Curses, 13s). He swings his arm and the primary blade of Shrine unleashes a barrage of slashes on
 * whoever he's facing within 30 studs: 17.5, only 10 through a guard (360 blockable), and a guarding target can't be
 * finished by it. The finisher takes the head and limbs.
 * <ul>
 *   <li>Airborne: a jump, a short hover and a flip, then one long slash down the line (unblockable, an explosion); its
 *       finisher cuts them in half.</li>
 *   <li>World Cutting Slash: Rush during the wind-up, then Open, then Cleave. He chants "SCALE OF THE DRAGON",
 *       "RECOIL", "TWIN METEORS" (uninterruptible), then, with total i-frames, swings a massive horizontal slash that cuts
 *       the world itself: 80 damage, less the more people it hits. Needs Cleave off cooldown; puts Open on its full
 *       cooldown, doubles Dismantle's, and puts Rush and Cleave on theirs. Started in the air, it all happens mid-air
 *       with 360° aim.</li>
 * </ul>
 */
public final class DismantleAbility extends Ability {
    public static final String ID = "dismantle";
    public static final String[] CHANT = {"", "SCALE OF\nTHE DRAGON...", "RECOIL...", "TWIN\nMETEORS."};

    public DismantleAbility() {
        super(ID);
    }

    @Override
    public Kind kind() {
        return Kind.INSTANT;
    }

    @Override
    public int cooldown(AbilityCaster caster) {
        return YujiCombat.cfg().dismantleCooldown;
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        return new Instance(this, ctx);
    }

    public static final class Instance extends AbilityInstance {
        private final boolean air;
        @Nullable private final Entity hint;
        @Nullable private LivingEntity target;
        /** World Cutting Slash: how far the chant has got (0 = plain Dismantle). */
        private int chant;
        private int chantAt;
        private int slashAt = -1;
        private int barrageAt = -1;
        private int slashes;
        private int endAt = -1;

        Instance(Ability ability, AbilityContext ctx) {
            super(ability, ctx);
            this.air = Combat.isAirborne(ctx.user());
            this.hint = ctx.targetHint();
        }

        private int windup() {
            return air ? YujiCombat.cfg().dismantleAirWindup : YujiCombat.cfg().dismantleWindup;
        }

        /** The next press of the chant. Returns whether it was taken. */
        public boolean chant(int stage) {
            if (stage != chant + 1 || barrageAt >= 0 || slashAt >= 0 || endAt >= 0) return false;
            if (stage == 1 && age >= windup()) return false;
            chant = stage;
            chantAt = age;
            Anim.play(user, "wcs_chant_" + stage);
            setPhase(10 + stage, YujiCombat.cfg().worldSlashChantWindow);
            // The line, in a speech panel beside him.
            Fx.play(level, "wcs_chant", user.position().add(0, 1.8, 0), Vec3.ZERO, stage, user.getId());
            if (stage == 3) slashAt = age + YujiCombat.cfg().worldSlashLineTicks;
            return true;
        }

        @Override
        public boolean uninterruptible() {
            return chant > 0;
        }

        @Override
        public void start() {
            Anim.play(user, air ? "dismantle_air" : "dismantle_windup");
            setPhase(0, windup());
            Fx.play(level, air ? "dismantle_air_start" : "dismantle_windup", user.position().add(0, 1.2, 0), user.getLookAngle(), 1f, user.getId());
            if (air) Motion.set(user, new Vec3(0, 0.55, 0));
        }

        @Override
        public void tick() {
            JJKConfig.Yuji cfg = YujiCombat.cfg();
            if (endAt >= 0) {
                if (age >= endAt) finish();
                return;
            }
            if (air || chant > 0 && air) Statuses.apply(user, CombatStatus.HOVER, 3);
            if (chant > 0) {
                Motion.set(user, new Vec3(0, air ? 0 : Math.min(0, user.getDeltaMovement().y), 0));
                if (slashAt >= 0) {
                    if (age == slashAt) worldSlash(cfg);
                    return;
                }
                // Waiting on the next line: it falls apart if it doesn't come, and the Dismantle goes off after all.
                if (age - chantAt > cfg.worldSlashChantWindow) {
                    chant = 0;
                    fire(cfg);
                }
                return;
            }
            if (barrageAt >= 0) {
                barrage(cfg);
                return;
            }
            if (age < windup()) {
                if (!air) Motion.set(user, new Vec3(0, Math.min(0, user.getDeltaMovement().y), 0));
                else if (age > 6) Motion.set(user, user.getDeltaMovement().multiply(0.4, 0.2, 0.4));
                return;
            }
            fire(cfg);
        }

        private void fire(JJKConfig.Yuji cfg) {
            if (air) {
                airSlash(cfg);
                return;
            }
            Anim.play(user, "dismantle_swing");
            target = Aim.target(user, cfg.dismantleRange, 25, hint);
            Vec3 at = target != null ? target.getBoundingBox().getCenter() : user.getEyePosition().add(user.getLookAngle().scale(cfg.dismantleRange * 0.6));
            Fx.play(level, "dismantle_swing", user.getEyePosition(), at.subtract(user.getEyePosition()), 1f, user.getId());
            barrageAt = age;
            setPhase(1, 12);
        }

        /** The barrage on the target: a slash every other tick. */
        private void barrage(JJKConfig.Yuji cfg) {
            int t = age - barrageAt;
            if (target == null || !target.isAlive()) {
                if (t >= 10) endAt = age + 4;
                return;
            }
            if (t % 2 == 0 && slashes < cfg.dismantleSlashes) {
                slashes++;
                Vec3 at = target.getBoundingBox().getCenter();
                boolean guarding = Combat.isGuarding(target);
                boolean last = slashes == cfg.dismantleSlashes;
                if (last && !guarding && YujiCombat.finishable(target)) {
                    Fx.play(level, "dismantle_finisher", at, HakariCombat.flat(user), 1.4f, user.getId());
                    YujiCombat.execute(user, target, ID, "dismantle_dismember");
                    endAt = age + 8;
                    return;
                }
                float per = (guarding ? cfg.dismantleBlockedDamage / Math.max(0.05f, JJKConfig.get().guard.blockedTechniqueDamageScale) : cfg.dismantleDamage) / cfg.dismantleSlashes;
                HakariCombat.hit(YujiCombat.slash(user, ID, per).tag(AttackTag.BLOCKABLE_360)
                        .knockback(last ? Knockback.directional(HakariCombat.flat(user), 0.7, 0.25) : Knockback.HOLD)
                        .hitstun(last ? 22 : 10).fx("dismantle_hit", 1f).build(), target);
                Fx.play(level, "dismantle_slash", at, HakariCombat.flat(user), slashes, user.getId());
            }
            if (slashes >= cfg.dismantleSlashes) endAt = age + 6;
        }

        /** Airborne: one long slash down the line. */
        private void airSlash(JJKConfig.Yuji cfg) {
            Anim.play(user, "dismantle_air_swing");
            Vec3 dir = user.getLookAngle();
            if (dir.y > -0.25) dir = new Vec3(dir.x, -0.35, dir.z).normalize();
            Vec3 from = user.getEyePosition(), to = from.add(dir.scale(cfg.dismantleAirLength));
            Fx.play(level, "dismantle_line", from, dir, (float) cfg.dismantleAirLength, user.getId());
            Fx.shake(level, to, 24, 0.8f, 10);
            List<LivingEntity> hit = HitboxQuery.targets(user, HitShape.capsule(from, to, 1.3), 0.3, false);
            for (LivingEntity t : hit) {
                if (YujiCombat.finishable(t)) {
                    YujiCombat.execute(user, t, ID, "dismantle_halve");
                    continue;
                }
                HakariCombat.hit(YujiCombat.slash(user, ID, cfg.dismantleAirDamage).tag(AttackTag.UNBLOCKABLE, AttackTag.EXPLOSION)
                        .knockback(Knockback.directional(dir, 0.9, 0.3)).hitstun(26).status(CombatStatus.LAUNCHED, 22)
                        .fx("dismantle_hit", 1.5f).build(), t);
            }
            cut(from, to, -1, 0, 60);
            endAt = age + 12;
        }

        private void worldSlash(JJKConfig.Yuji cfg) {
            Anim.play(user, "wcs_swing");
            Statuses.apply(user, CombatStatus.EVADING, 14);
            Vec3 look = user.getLookAngle();
            Vec3 f = air ? look : HakariCombat.flat(user);
            Vec3 from = user.getEyePosition().subtract(0, 0.4, 0);
            Fx.play(level, "world_slash", from, f, (float) cfg.worldSlashLength, user.getId());
            Fx.flash(level, user.position(), 64, 0xC0000000, 6);
            Fx.shake(level, user.position(), 64, 1.4f, 20);
            Vec3 center = from.add(f.scale(cfg.worldSlashLength / 2));
            List<LivingEntity> hit = HitboxQuery.targets(user, HitShape.orientedBox(from, f, cfg.worldSlashLength, cfg.worldSlashWidth, 5), 0.3, false);
            // Cutting the world itself: the more it cuts through, the less each one takes.
            float damage = cfg.worldSlashDamage / (float) Math.sqrt(Math.max(1, hit.size()));
            for (LivingEntity t : hit) {
                if (YujiCombat.finishable(t) || t.getHealth() <= damage) {
                    YujiCombat.execute(user, t, ID, "world_slash_halve");
                    continue;
                }
                HakariCombat.hit(YujiCombat.slash(user, ID, damage).tag(AttackTag.UNBLOCKABLE, AttackTag.EXPLOSION, AttackTag.ULTIMATE)
                        .noComboScaling().knockback(Knockback.directional(f, 1.2, 0.4)).hitstun(34).status(CombatStatus.LAUNCHED, 30)
                        .fx("world_slash_hit", 1.6f).build(), t);
            }
            Vec3 side = new Vec3(-f.z, 0, f.x).normalize();
            // The cut runs through everything at chest height (walls, trees, buildings), not the ground.
            cut(center.subtract(side.scale(cfg.worldSlashWidth / 2)), center.add(side.scale(cfg.worldSlashWidth / 2)), 0, 1, 200);
            // Everything the chant used goes on cooldown; Dismantle's twice over.
            for (AbilitySlot s : AbilitySlot.values()) {
                Ability a = caster.ability(s);
                if (a instanceof OpenAbility || a instanceof RushAbility || a instanceof CleaveAbility) caster.startCooldown(s, a.cooldown(caster));
                if (a == ability) caster.startCooldown(s, a.cooldown(caster) * 2);
            }
            chant = 0;
            endAt = age + 16;
        }

        /** A line of cut blocks (restored later like any destruction). */
        private void cut(Vec3 a, Vec3 b, int low, int height, int limit) {
            if (!Destruction.allowed(level)) return;
            int n = 0;
            double len = a.distanceTo(b);
            for (double d = 0; d <= len && n < limit; d += 0.8) {
                Vec3 p = a.lerp(b, d / Math.max(1e-3, len));
                for (int y = low; y <= height; y++) if (Destruction.destroy(level, BlockPos.containing(p.add(0, y, 0)), 5f, user, "jjk:dismantle")) n++;
            }
        }

        @Override
        public float movementMultiplier() {
            return chant > 0 ? 0f : 0.35f;
        }
    }
}
