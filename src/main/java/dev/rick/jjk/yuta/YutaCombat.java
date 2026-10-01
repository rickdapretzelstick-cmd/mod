package dev.rick.jjk.yuta;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.combat.HitResult;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.gojo.GojoCombat;
import dev.rick.jjk.hakari.HakariCombat;
import dev.rick.jjk.registry.ModDamageTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Cursed Partners' shared rules: strikes, the katana, Rika's targeting and summoning, and what she copies. */
public final class YutaCombat {
    private YutaCombat() {}

    public static JJKConfig.Yuta cfg() {
        return JJKConfig.get().yuta;
    }

    public static boolean finishable(LivingEntity target) {
        return target.isAlive() && target.getHealth() <= target.getMaxHealth() * cfg().finisherThreshold;
    }

    public static HitResult execute(LivingEntity user, LivingEntity target, String id, String fx) {
        return HakariCombat.execute(user, target, id, fx);
    }

    public static boolean ragdolled(LivingEntity e) {
        return GojoCombat.ragdolled(e);
    }

    /** A strike of his: melee by default; {@code unblockable} skips guard. */
    public static Hit.Builder strike(LivingEntity user, String id, float damage, boolean unblockable) {
        Hit.Builder b = Hit.builder(user, id).type(ModDamageTypes.MELEE).damage(damage).tag(AttackTag.MELEE).origin(user.getEyePosition());
        if (unblockable) b.tag(AttackTag.UNBLOCKABLE);
        return b;
    }

    /**
     * One of Rika's blows: Yuta's attack, coming from where she is. {@code bullet}: it counts as a projectile (JJS
     * "Bullet": Rika Smash, Rika Haymaker), so bullet counters answer it rather than melee ones.
     */
    public static Hit.Builder rika(LivingEntity user, RikaEntity rika, String id, float damage, boolean bullet) {
        return Hit.builder(user, id).type(ModDamageTypes.MELEE).damage(damage).tag(bullet ? AttackTag.PROJECTILE : AttackTag.MELEE, AttackTag.HEAVY)
                .origin(rika.position().add(0, 2.2, 0)).direct(rika);
    }

    /** Swordsmanship: the katana comes out (and stays out for the next 8 seconds). */
    public static void drawKatana(LivingEntity user) {
        Statuses.apply(user, CombatStatus.KATANA, cfg().katanaHolsterTicks);
        YutaState s = YutaState.of(user);
        s.fists = false;
    }

    /** A katana M1: the blade comes out of its holster. */
    public static void katanaSwing(LivingEntity user) {
        drawKatana(user);
    }

    /**
     * Steel Arm: each of the first three M1s is followed by a quick jab of the steel-cased arm (twice as slow if the
     * punch was blocked).
     */
    public static void steelSwing(LivingEntity user) {
        AbilityCaster c = Casters.getOrNull(user);
        if (c == null) return;
        int i = c.melee.chainIndex();
        var cur = c.melee.current();
        if (cur == null || !cur.id().startsWith("light_") && !cur.id().startsWith("air_") || i < 1 || i > 3) return;
        YutaState.of(user).jabAt = user.level().getGameTime() + 5;
    }

    /** The steel jab itself (from the character's tick). */
    static void jab(LivingEntity user) {
        JJKConfig.Melee m = JJKConfig.get().melee;
        LivingEntity t = HakariCombat.firstInFront(user, m.lightRange, m.lightWidth, 1.8);
        YutaState s = YutaState.of(user);
        if (t != null && dev.rick.jjk.core.combat.Combat.isGuarding(t) && !s.jabSlowed) {
            // Blocked: the jab comes twice as slow.
            s.jabSlowed = true;
            s.jabAt = user.level().getGameTime() + 5;
            return;
        }
        s.jabSlowed = false;
        s.jabAt = -1;
        dev.rick.jjk.core.anim.Anim.play(user, "yuta_steel_jab");
        if (user.level() instanceof ServerLevel level) {
            Fx.play(level, "yuta_steel_jab", user.getEyePosition().add(user.getLookAngle().scale(0.8)), user.getLookAngle(), 1f, user.getId());
        }
        if (t == null) return;
        HakariCombat.hit(strike(user, "steel_jab", 0.5f, false).knockback(dev.rick.jjk.core.combat.Knockback.HOLD)
                .hitstun(m.lightHitstun).fx("yuta_steel_hit_light", 0.8f).build(), t);
    }

    // ---------------------------------------------------------------- Rika

    /** Rika, summoned if she isn't out yet. */
    public static RikaEntity summonRika(LivingEntity user) {
        YutaState s = YutaState.of(user);
        RikaEntity r = s.rika();
        if (r == null && user.level() instanceof ServerLevel level) {
            r = RikaEntity.summon(level, user);
            s.rika = r;
            AbilityCaster c = Casters.getOrNull(user);
            r.set(RikaEntity.FULL, c != null && c.isAwakened());
            Fx.play(level, "rika_summon", r.position().add(0, 1.6, 0), Vec3.ZERO, 1f, r.getId());
        }
        return r;
    }

    /**
     * Who Rika fights: the one the user hit last (or aimed at when calling her), else the nearest enemy within her reach.
     */
    @Nullable
    public static LivingEntity rikaTarget(LivingEntity user, @Nullable RikaEntity rika) {
        YutaState s = YutaState.of(user);
        long now = user.level().getGameTime();
        double range = cfg().rikaAttackRange;
        if (s.target != null && s.target.isAlive() && now - s.targetAt < 400 && s.target.distanceTo(user) < range * 1.6) return s.target;
        Vec3 from = rika != null ? rika.position() : user.position();
        LivingEntity best = null;
        double bd = range * range;
        for (LivingEntity e : user.level().getEntitiesOfClass(LivingEntity.class, new AABB(from, from).inflate(range), LivingEntity::isAlive)) {
            if (e == user || e.isSpectator()) continue;
            double d = e.distanceToSqr(from);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    public static void setTarget(LivingEntity user, @Nullable LivingEntity target) {
        if (target == null || target == user) return;
        YutaState s = YutaState.of(user);
        s.target = target;
        s.targetAt = user.level().getGameTime();
        RikaEntity r = s.rika();
        if (r != null) r.setTarget(target);
    }

    /** Rika is busy with a move for {@code ticks} more ticks. */
    public static void busy(LivingEntity user, int ticks) {
        YutaState s = YutaState.of(user);
        s.rikaBusyUntil = Math.max(s.rikaBusyUntil, user.level().getGameTime() + ticks);
        RikaEntity r = s.rika();
        if (r != null) r.set(RikaEntity.BUSY, true);
    }

    /** Her move is over: she is free again (and drifts back to his side unless she was left somewhere). */
    public static void free(LivingEntity user) {
        YutaState s = YutaState.of(user);
        s.rikaBusyUntil = Math.min(s.rikaBusyUntil, user.level().getGameTime());
        RikaEntity r = s.rika();
        if (r != null) r.set(RikaEntity.BUSY, false);
    }

    /** One of her moves was used: her moveset is put away and she comes back to his side. */
    public static void backToYuta(AbilityCaster caster) {
        YutaState s = YutaState.of(caster.owner);
        s.rikaMode = false;
        RikaEntity r = s.rika();
        if (r != null) {
            r.set(RikaEntity.PILOTED, false);
            r.recall();
        }
        caster.markDirty();
    }

    /** Base Rika's moves share one cooldown. */
    public static void shareBaseRikaCooldown(AbilityCaster caster, int ticks) {
        for (AbilitySlot slot : new AbilitySlot[]{AbilitySlot.SKILL_1, AbilitySlot.SKILL_2, AbilitySlot.SKILL_3}) {
            caster.startCooldown(slot, YutaCharacter.RIKA, ticks);
        }
    }

    /** Rika attacked (or killed) someone: their technique is Yuta's to copy. */
    public static void copyFrom(LivingEntity user, LivingEntity victim) {
        String t = Copies.from(victim);
        if (t == null) return;
        YutaState s = YutaState.of(user);
        if (s.learn(t, cfg().copySlots) && user.level() instanceof ServerLevel level) {
            Fx.play(level, "copy_learn", user.position().add(0, 1.2, 0), Vec3.ZERO, 1f, user.getId());
            AbilityCaster c = Casters.getOrNull(user);
            if (c != null) c.markDirty();
            YutaSync.send(user);
        }
    }
}
