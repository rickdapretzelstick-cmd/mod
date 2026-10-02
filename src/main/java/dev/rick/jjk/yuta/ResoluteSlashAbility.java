package dev.rick.jjk.yuta;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.HitResult;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.gojo.TeleportAbility;
import dev.rick.jjk.hakari.HakariCombat;
import dev.rick.jjk.util.Aim;
import dev.rick.jjk.util.Motion;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Resolute Slash (JJS, 15s). Aiming at a spot within 25 studs, Yuta vanishes while winding up a long swing, then
 * reappears there to finish it with a slash at the target's neck (12). Unblockable; can't bypass ragdoll. The finisher
 * cuts through the head.
 *
 * <p>Resolute Black Flash (use again right as he reappears): he vanishes a second time and comes back with a heavy blow
 * amplified by a Black Flash (12), with melee i-frames, bypassing ragdoll. Its finisher launches them away in sparks.
 */
public final class ResoluteSlashAbility extends Ability {
    public static final String ID = "resolute_slash";

    public ResoluteSlashAbility() {
        super(ID);
    }

    @Override
    public Kind kind() {
        return Kind.INSTANT;
    }

    @Override
    public float cost(AbilityCaster caster) {
        return YutaCombat.cfg().resoluteCost;
    }

    @Override
    public int cooldown(AbilityCaster caster) {
        return YutaCombat.cfg().resoluteCooldown;
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        YutaCombat.drawKatana(ctx.user());
        Aim.Target aim = Aim.point(ctx.user(), YutaCombat.cfg().resoluteRange, 10, ctx.targetHint());
        return new Instance(this, ctx, aim);
    }

    public static final class Instance extends AbilityInstance {
        @Nullable private final LivingEntity target;
        private final Vec3 spot;
        /** Tick he reappears (the slash follows), then the Black Flash's own vanish and blow. */
        private int appearAt;
        private int slashAt;
        private boolean slashed;
        private int flashFrom = -1;
        private int flashAt = -1;
        private int endAt = -1;

        Instance(Ability ability, AbilityContext ctx, Aim.Target aim) {
            super(ability, ctx);
            target = aim.entity();
            spot = aim.point();
        }

        /** Pressed again: right as he reappears, the Resolute Black Flash. */
        public boolean againPress() {
            int w = YutaCombat.cfg().resoluteAgainWindow;
            if (flashFrom >= 0 || endAt >= 0 && slashed && age > slashAt + w) return false;
            if (age < appearAt - 2 || age > appearAt + w) return false;
            flashFrom = age;
            flashAt = age + YutaCombat.cfg().resoluteVanishTicks;
            vanish();
            Statuses.apply(user, CombatStatus.MELEE_ARMOR, flashAt - age + 4);
            Anim.play(user, "yuta_resolute_flash_windup");
            setPhase(2, flashAt - age);
            Fx.play(level, "resolute_flash_ready", user.position().add(0, 1, 0), HakariCombat.flat(user), 1f, user.getId());
            endAt = -1;
            return true;
        }

        @Override
        public void start() {
            JJKConfig.Yuta cfg = YutaCombat.cfg();
            appearAt = cfg.resoluteVanishTicks;
            slashAt = appearAt + 3;
            Anim.play(user, "yuta_resolute_windup");
            setPhase(0, appearAt);
            Fx.play(level, "resolute_vanish", user.position().add(0, 1, 0), HakariCombat.flat(user), 1f, user.getId());
        }

        private void vanish() {
            user.setInvisible(true);
            Statuses.apply(user, CombatStatus.HOVER, 4);
            Fx.play(level, "resolute_vanish", user.position().add(0, 1, 0), HakariCombat.flat(user), 1f, user.getId());
        }

        private void appear() {
            user.setInvisible(false);
            Vec3 dest = destination();
            if (dest != null) {
                Vec3 face = target != null && target.isAlive() ? target.getBoundingBox().getCenter() : dest.add(HakariCombat.flat(user).scale(2)).add(0, 1.2, 0);
                Vec3 look = face.subtract(dest.add(0, user.getEyeHeight(), 0));
                float yaw = (float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90f;
                user.teleportTo(level, dest.x, dest.y, dest.z, Set.of(), yaw, user.getXRot(), false);
                user.resetFallDistance();
            }
            Motion.set(user, Vec3.ZERO);
            Fx.play(level, "resolute_appear", user.position().add(0, 1, 0), HakariCombat.flat(user), 1f, user.getId());
        }

        /** In front of the target (his side of them), or the aimed spot; null to stay put. */
        @Nullable
        private Vec3 destination() {
            if (target != null && target.isAlive() && target.distanceTo(user) <= YutaCombat.cfg().resoluteRange + 4) {
                Vec3 v = TeleportAbility.aroundFront(user, target, 0);
                if (v != null) return v;
            }
            Vec3 feet = new Vec3(spot.x, spot.y - user.getBbHeight() * 0.5, spot.z);
            for (double up : new double[] {0, 0.6, 1.2, -0.6}) {
                Vec3 f = feet.add(0, up, 0);
                AABB box = user.getDimensions(user.getPose()).makeBoundingBox(f);
                if (level.isLoaded(BlockPos.containing(f)) && level.noCollision(user, box)) return f;
            }
            return null;
        }

        @Override
        public void tick() {
            JJKConfig.Yuta cfg = YutaCombat.cfg();
            if (flashFrom >= 0) {
                flash(cfg);
                return;
            }
            if (endAt >= 0) {
                if (age >= endAt) finish();
                return;
            }
            if (age < appearAt) {
                Motion.set(user, new Vec3(0, Math.min(0, user.getDeltaMovement().y) * 0.3, 0));
                if (age == 3) vanish();
                return;
            }
            if (age == appearAt) {
                appear();
                Anim.play(user, "yuta_resolute_slash");
                setPhase(1, slashAt - age + cfg.resoluteAgainWindow);
                return;
            }
            if (age == slashAt && !slashed) {
                slashed = true;
                slash(cfg);
                endAt = Math.max(age + 10, appearAt + cfg.resoluteAgainWindow + 1);
            }
        }

        /** The slash at the neck: unblockable, can't reach someone lying on the ground. */
        private void slash(JJKConfig.Yuta cfg) {
            Fx.play(level, "resolute_slash", user.position().add(0, 1.5, 0), HakariCombat.flat(user), 1f, user.getId());
            LivingEntity t = HakariCombat.firstInFront(user, 2.6, 2.0, 2.4);
            if (t == null) return;
            if (YutaCombat.finishable(t) && !YutaCombat.ragdolled(t)) {
                YutaCombat.execute(user, t, ID, "resolute_finisher");
                return;
            }
            YutaCombat.setTarget(user, t);
            HakariCombat.hit(YutaCombat.strike(user, ID, cfg.resoluteDamage, true)
                    .knockback(Knockback.directional(HakariCombat.flat(user), 0.6, 0.25)).hitstun(22).fx("resolute_hit", 1.1f).build(), t);
        }

        private void flash(JJKConfig.Yuta cfg) {
            if (endAt >= 0) {
                if (age >= endAt) finish();
                return;
            }
            Motion.set(user, new Vec3(0, Math.min(0, user.getDeltaMovement().y) * 0.3, 0));
            if (age < flashAt) return;
            appear();
            Anim.play(user, "yuta_resolute_flash");
            LivingEntity t = target != null && target.isAlive() && target.distanceTo(user) < 3.4 ? target : HakariCombat.firstInFront(user, 2.6, 2.0, 2.4);
            Fx.play(level, "resolute_black_flash", user.position().add(0, 1.2, 0).add(HakariCombat.flat(user).scale(1.2)), HakariCombat.flat(user), 1f, user.getId());
            Fx.shake(level, user.position(), 24, 0.9f, 10);
            endAt = age + 14;
            if (t == null) return;
            YutaCombat.setTarget(user, t);
            if (YutaCombat.finishable(t)) {
                // Amped up: sparks, and they are launched away.
                YutaCombat.execute(user, t, ID, "resolute_flash_finisher");
                Motion.set(t, HakariCombat.flat(user).scale(3.0).add(0, 1.0, 0));
                return;
            }
            HitResult r = HakariCombat.hit(YutaCombat.strike(user, ID, cfg.resoluteBlackFlashDamage, true).tag(AttackTag.OTG, AttackTag.HEAVY)
                    .knockback(Knockback.set(HakariCombat.flat(user).scale(2.2).add(0, 0.55, 0))).hitstun(30)
                    .status(CombatStatus.LAUNCHED, 30).noComboScaling().fx("yuta_impact", 1.3f).build(), t);
            if (r.connected()) Fx.flash(level, t.position(), 20, 0xA0000000, 6);
        }

        @Override
        public float movementMultiplier() {
            return 0f;
        }

        @Override
        public void end() {
            user.setInvisible(false);
        }
    }
}
