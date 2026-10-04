package dev.rick.jjk.core.combat;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.defense.DefenseResult;
import dev.rick.jjk.core.defense.Defenses;
import dev.rick.jjk.core.defense.IncomingAttack;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.net.ComboPayload;
import dev.rick.jjk.registry.ModDamageTypes;
import dev.rick.jjk.util.Motion;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The single pipeline every attack in the mod goes through:
 * targeting rules → evasion/downed checks → defense layers → damage → knockback → hitstun/statuses → combo → feedback.
 */
public final class HitResolver {
    private HitResolver() {}

    public static List<HitResult> resolveAll(Hit hit, Collection<? extends LivingEntity> targets) {
        List<HitResult> results = new ArrayList<>(targets.size());
        for (LivingEntity t : targets) results.add(resolve(hit, t));
        return results;
    }

    public static HitResult resolve(Hit hit, LivingEntity target) {
        if (!(target.level() instanceof ServerLevel level) || !Targeting.canTarget(hit.attacker, target)) {
            return finish(new HitResult(hit, target, HitResult.Outcome.INVALID, 0, 0));
        }
        JJKConfig cfg = JJKConfig.get();
        CombatState state = Combat.state(target);
        boolean sureHit = hit.has(AttackTag.SURE_HIT);
        Vec3 hitPoint = hitPoint(target, hit.direct != null ? hit.direct.position() : hit.origin);

        if (!sureHit && state.isEvading()) {
            Fx.play(level, "evade", target.position().add(0, target.getBbHeight() / 2, 0));
            return finish(new HitResult(hit, target, HitResult.Outcome.WHIFF, 0, 0));
        }
        if (!sureHit && hit.has(AttackTag.PROJECTILE) && !hit.has(AttackTag.MELEE) && state.isProjectileImmune()) {
            return finish(new HitResult(hit, target, HitResult.Outcome.WHIFF, 0, 0));
        }
        if (!sureHit && hit.has(AttackTag.MELEE) && !hit.has(AttackTag.OTG) && (state.isMeleeImmune() || state.isDowned())) {
            return finish(new HitResult(hit, target, HitResult.Outcome.WHIFF, 0, 0));
        }

        long time = level.getGameTime();
        int window = cfg.general.comboWindow;
        int prior = state.peekCombo(hit.attacker.getUUID(), time, window);
        float damage = hit.damage * cfg.general.damageMultiplier;
        if (hit.comboScaling) damage *= Math.max(cfg.general.comboMinDamageScale, 1f - cfg.general.comboDamageDecay * prior);
        if (!sureHit && state.has(CombatStatus.OVERLOAD)) damage *= cfg.domain.overloadedDamageScale;
        damage *= masteryDamage(hit.attacker);

        DefenseResult defense = Defenses.resolve(IncomingAttack.of(hit, target, damage));
        HitResult.Outcome outcome = switch (defense.kind()) {
            case NEGATE -> HitResult.Outcome.NEGATED;
            case BLOCK -> HitResult.Outcome.BLOCKED;
            case PARRY -> HitResult.Outcome.PARRIED;
            case BREAK -> HitResult.Outcome.GUARD_BROKEN;
            default -> HitResult.Outcome.HIT;
        };
        if (outcome == HitResult.Outcome.NEGATED || outcome == HitResult.Outcome.PARRIED) {
            return finish(new HitResult(hit, target, outcome, 0, prior));
        }
        damage *= defense.damageScale();
        // True Cannon's Decadence: critical, his Awakening meter takes the blow first.
        var tc = dev.rick.jjk.core.ability.Casters.getOrNull(target);
        if (tc != null && damage > 0) damage = dev.rick.jjk.ryu.RyuCharacter.absorb(tc, damage);

        float dealt = 0;
        if (damage > 0) {
            DamageSource src = ModDamageTypes.source(level, hit.damageType, hit.direct != null ? hit.direct : hit.attacker, hit.attacker);
            if (target.isInvulnerableTo(level, src)) return finish(new HitResult(hit, target, HitResult.Outcome.INVALID, 0, prior));
            float before = target.getHealth() + target.getAbsorptionAmount();
            target.hurtServer(level, src, damage);
            dealt = Math.max(0, before - (target.getHealth() + target.getAbsorptionAmount()));
            if (target.isDeadOrDying()) dealt = Math.max(dealt, damage);
        }

        if (outcome == HitResult.Outcome.BLOCKED) {
            Vec3 push = hit.knockback.compute(target, cfg.general.knockbackMultiplier);
            if (push != null) {
                Motion.set(target, new Vec3(push.x * 0.25, Math.min(push.y, 0.1) * 0.25, push.z * 0.25));
            }
            return finish(new HitResult(hit, target, outcome, dealt, prior));
        }

        Vec3 kb = hit.knockback.compute(target, cfg.general.knockbackMultiplier);
        if (kb != null) {
            Motion.set(target, kb);
        }

        int count = state.registerComboHit(hit.attacker.getUUID(), time, window, dealt);
        int hitstun = hit.hitstun;
        if (count > cfg.general.comboHitstunDecayStart) hitstun = Math.round(hitstun * 0.4f);
        if (hitstun > 0) Statuses.apply(target, CombatStatus.HITSTUN, hitstun);
        for (Hit.StatusApplication s : hit.statuses) Statuses.apply(target, s.status(), s.ticks());
        // A fresh hit on someone lying down picks them up; they no longer count as downed.
        if (hit.has(AttackTag.OTG) && kb != null && kb.y > 0.2) state.remove(CombatStatus.KNOCKDOWN);

        Vec3 dir = hitPoint.subtract(hit.origin);
        Fx.play(level, hit.impactFx, hitPoint, dir.lengthSqr() > 1e-6 ? dir.normalize() : Vec3.ZERO, hit.impactScale, target.getId());

        if (hit.attacker instanceof ServerPlayer sp) {
            ServerPlayNetworking.send(sp, new ComboPayload(target.getId(), count, state.comboDamage(), outcome.ordinal()));
        }
        if (target.isDeadOrDying() && hit.has(AttackTag.ULTIMATE)) finisher(level, hit, target, hitPoint);
        return finish(new HitResult(hit, target, outcome, dealt, count));
    }

    /**
     * Kill presentation reserved for ultimate-level attacks: an impact frame for everyone nearby, a burst at the body,
     * and the body sent flying. Normal kills stay fast.
     */
    private static void finisher(ServerLevel level, Hit hit, LivingEntity target, Vec3 at) {
        Vec3 dir = at.subtract(hit.origin);
        dir = dir.lengthSqr() < 1e-4 ? hit.attacker.getLookAngle() : dir.normalize();
        Fx.play(level, "finisher", at, dir, 1f, target.getId());
        Fx.shake(level, at, 40, 1.2f, 18);
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(at) < 48 * 48) Fx.camera(p, dev.rick.jjk.core.net.CameraPayload.IMPACT, 1f, 6, 0);
        }
        Motion.set(target, dir.scale(2.2).add(0, 0.9, 0));
    }

    private static HitResult finish(HitResult result) {
        if (result.hit().onResolved != null) result.hit().onResolved.accept(result);
        for (CombatEvents.HitResolved l : CombatEvents.HIT_RESOLVED) l.onResolved(result);
        return result;
    }

    /** Point on the target's body closest to where the attack came from, nudged toward its center. */
    public static Vec3 hitPoint(LivingEntity target, Vec3 from) {
        AABB box = target.getBoundingBox();
        Vec3 c = box.getCenter();
        Vec3 closest = new Vec3(Math.max(box.minX, Math.min(from.x, box.maxX)),
                Math.max(box.minY, Math.min(from.y, box.maxY)),
                Math.max(box.minZ, Math.min(from.z, box.maxZ)));
        return closest.lerp(c, 0.35);
    }

    /** Technique Mastery's damage bonus on the move this hit belongs to ({@code <ability>.damage}); 1 for anyone else. */
    private static float masteryDamage(@org.jetbrains.annotations.Nullable net.minecraft.world.entity.LivingEntity attacker) {
        if (!(attacker instanceof net.minecraft.server.level.ServerPlayer)) return 1f;
        var c = dev.rick.jjk.core.ability.Casters.getOrNull(attacker);
        String id = c == null ? null : c.creditedAbility();
        return id == null ? 1f : (float) dev.rick.jjk.progression.mastery.Mastery.param(attacker, id + ".damage");
    }
}
