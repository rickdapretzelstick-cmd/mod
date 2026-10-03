package dev.rick.jjk.core.combat.melee;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.character.JJKCharacter;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatState;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.combat.HitResolver;
import dev.rick.jjk.core.combat.HitResult;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.core.combat.Targeting;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.hitbox.HitShape;
import dev.rick.jjk.core.hitbox.HitboxQuery;
import dev.rick.jjk.core.net.MeleeInputPayload;
import dev.rick.jjk.registry.ModDamageTypes;
import dev.rick.jjk.util.Motion;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Shared melee mechanics. Input picks a move by situation:
 * <ul>
 *   <li>Light: 4-hit chain. The 4th is a finisher: knockback on the ground, uppercut while holding jump,
 *       downslam while airborne.</li>
 *   <li>Sprinting light (chain start): lunging sprint attack that leads into hit 2.</li>
 *   <li>Airborne light: air chain; both fighters hang in the air between hits.</li>
 *   <li>Looking down at a knocked-down target: ground attack (stomp) that pops them back up.</li>
 *   <li>Heavy (hold attack): chargeable guard-breaking strike.</li>
 * </ul>
 */
public final class MeleeSystem {
    private MeleeSystem() {}

    /** Entry point for a melee input from a client. */
    public static void handleInput(LivingEntity user, AbilityCaster caster, int kind, int flags, int targetHint) {
        if (caster.character() == null || !user.isAlive()) return;
        switch (kind) {
            case MeleeInputPayload.LIGHT -> light(user, caster, flags, targetHint);
            case MeleeInputPayload.HEAVY_START -> heavyStart(user, caster);
            case MeleeInputPayload.HEAVY_RELEASE -> heavyRelease(user, caster);
            default -> {}
        }
    }

    private static boolean blocked(LivingEntity user, AbilityCaster caster) {
        CombatState state = Combat.state(user);
        return state.actionsLocked() || state.isGuarding() || caster.isBusy();
    }

    public static void light(LivingEntity user, AbilityCaster caster, int flags, int targetHint) {
        MeleeState m = caster.melee;
        if (blocked(user, caster) || m.heavyCharging) return;
        if (!m.canChain()) {
            // Pressed a little early: remember it and fire as soon as the chain window opens.
            m.bufferedFlags = flags;
            m.bufferedHint = targetHint;
            m.bufferAge = 0;
            return;
        }
        JJKConfig.Melee cfg = JJKConfig.get().melee;
        long now = user.level().getGameTime();
        MeleeMoveset set0 = moveset(caster);
        int last = Math.max(1, set0.chain()) - 1;
        if (now - m.lastAttackTime > cfg.chainResetTicks || m.chainIndex > last) m.chainIndex = 0;

        boolean airborne = Combat.isAirborne(user) && (flags & MeleeInputPayload.FLAG_AIRBORNE) != 0 || !user.onGround() && user.fallDistance > 0.4;
        boolean sprinting = (flags & MeleeInputPayload.FLAG_SPRINT) != 0 || user.isSprinting();
        boolean jumpHeld = (flags & MeleeInputPayload.FLAG_JUMP) != 0;
        boolean lookDown = user.getXRot() > 50 || (flags & MeleeInputPayload.FLAG_LOOK_DOWN) != 0;
        MeleeMoveset set = set0;

        MeleeMove move;
        if (lookDown && !airborne && downedTargetNear(user)) {
            move = stomp(set, cfg);
        } else if (m.chainIndex == 0 && sprinting && !airborne) {
            move = sprintAttack(set, cfg);
            m.chainIndex = 1;
        } else if (m.chainIndex == last) {
            // Movesets without launchers (Shrine) end every chain on the plain finisher.
            move = !set.launchers() ? finisher(set, cfg) : airborne ? downslam(set, cfg) : jumpHeld ? uppercut(set, cfg) : finisher(set, cfg);
            if (move.id().equals("finisher") && set.finisher() != null) {
                MeleeMove own = set.finisher().replace(user, move);
                if (own != null) move = own;
            }
            m.chainIndex = last + 1;
        } else {
            move = airborne ? airLight(set, cfg, m.chainIndex) : groundLight(set, cfg, m.chainIndex);
            m.chainIndex++;
        }
        start(user, m, move, targetHint, set);
    }

    private static void start(LivingEntity user, MeleeState m, MeleeMove move, int targetHint, MeleeMoveset set) {
        m.current = move;
        m.currentAge = 0;
        m.connected = false;
        m.hitThisMove.clear();
        m.targetHint = targetHint;
        m.lastAttackTime = user.level().getGameTime();
        Anim.play(user, move.anim());
        if (user.level() instanceof ServerLevel sl) {
            Fx.play(sl, set.fx("swing"), user.getEyePosition().add(user.getLookAngle().scale(0.8)), user.getLookAngle(), move.id().contains("finisher") || move.id().contains("heavy") ? 1.5f : 1f, user.getId());
            if (set.onSwing() != null) set.onSwing().accept(user);
        }
        magnetize(user, targetHint);
    }

    /** Called for each active frame of the current move. */
    static void activeFrame(LivingEntity user, MeleeState m, MeleeMove move, boolean firstActive) {
        HitShape shape = move.hitbox().apply(user);
        double tol = JJKConfig.get().general.latencyTolerance * 0.5;
        List<LivingEntity> targets = HitboxQuery.targets(user, shape, tol, true);
        // The client saw its crosshair on this entity when attacking; accept it within latency tolerance.
        if (firstActive && m.targetHint >= 0) {
            Entity hinted = user.level().getEntity(m.targetHint);
            if (hinted instanceof LivingEntity lh && !targets.contains(lh) && hintValid(user, lh, move)) targets.add(lh);
        }
        for (LivingEntity target : targets) {
            if (!m.hitThisMove.add(target.getUUID())) continue;
            HitResult r = HitResolver.resolve(move.hit().apply(user, target), target);
            if (r.outcome().contacted()) {
                m.connected = true;
                if (move.onConnect() != null) move.onConnect().accept(user, r);
            }
        }
    }

    private static boolean hintValid(LivingEntity user, LivingEntity target, MeleeMove move) {
        if (!Targeting.canTarget(user, target)) return false;
        JJKConfig cfg = JJKConfig.get();
        var c = dev.rick.jjk.core.ability.Casters.getOrNull(user);
        double range = cfg.melee.lightRange * (c != null ? moveset(c).rangeMultiplier() : 1f);
        double reach = range + cfg.general.latencyTolerance + 0.8;
        if (target.getBoundingBox().distanceToSqr(user.getEyePosition()) > reach * reach) return false;
        Vec3 to = target.getBoundingBox().getCenter().subtract(user.getEyePosition()).normalize();
        return to.dot(user.getLookAngle()) > 0.25 && HitboxQuery.hasLineOfSight(user.level(), user.getEyePosition(), target);
    }

    /** Slight pull toward the target near the crosshair so chains don't whiff from spacing drift. */
    private static void magnetize(LivingEntity user, int targetHint) {
        Entity e = targetHint >= 0 ? user.level().getEntity(targetHint) : null;
        if (!(e instanceof LivingEntity t) || !Targeting.canTarget(user, t)) return;
        Vec3 d = t.position().subtract(user.position());
        double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
        if (horiz < 2.2 || horiz > 5.0) return;
        Vec3 dir = new Vec3(d.x, 0, d.z).normalize().scale(Math.min(0.45, (horiz - 2.0) * 0.25));
        Motion.set(user, new Vec3(dir.x, user.getDeltaMovement().y, dir.z));
    }

    // --- Heavy ---

    static void heavyStart(LivingEntity user, AbilityCaster caster) {
        MeleeState m = caster.melee;
        if (blocked(user, caster) || m.heavyCharging || m.isCommitted()) return;
        if (!caster.isReady(AbilitySlot.HEAVY)) return;
        m.current = null;
        m.heavyCharging = true;
        m.heavyStartTime = user.level().getGameTime();
        Anim.play(user, moveset(caster).anim("heavy_charge"));
        if (user.level() instanceof ServerLevel sl) Fx.play(sl, "heavy_charge", user.position(), Vec3.ZERO, 1f, user.getId());
    }

    public static void heavyRelease(LivingEntity user, AbilityCaster caster) {
        MeleeState m = caster.melee;
        if (!m.heavyCharging) return;
        m.heavyCharging = false;
        if (Combat.state(user).actionsLocked()) return;
        JJKConfig.Melee cfg = JJKConfig.get().melee;
        long held = user.level().getGameTime() - m.heavyStartTime;
        float charge = Mth.clamp((held - cfg.heavyMinCharge) / (float) Math.max(1, cfg.heavyMaxCharge - cfg.heavyMinCharge), 0f, 1f);
        caster.startCooldown(AbilitySlot.HEAVY, cfg.heavyCooldown);
        m.chainIndex = 0;
        MeleeMoveset set = moveset(caster);
        start(user, m, heavy(set, cfg, charge), m.targetHint, set);
    }

    // --- Moves ---

    private static MeleeMoveset moveset(AbilityCaster caster) {
        JJKCharacter c = caster.character();
        return c != null ? c.melee(caster) : new MeleeMoveset("", "", 1f, 1f, 1f);
    }

    private static HitShape frontBox(LivingEntity user, double length, double width, double height) {
        Vec3 look = user.getLookAngle();
        // Clamp pitch so looking at feet or sky still hits what's in front.
        Vec3 dir = new Vec3(look.x, Mth.clamp(look.y, -0.6, 0.6), look.z);
        Vec3 origin = user.position().add(0, user.getBbHeight() * 0.55, 0).subtract(dir.normalize().scale(0.3));
        return HitShape.orientedBox(origin, dir, length + 0.3, width, height);
    }

    private static Hit.Builder base(LivingEntity user, String id, MeleeMoveset set, float damage) {
        Hit.Builder b = Hit.builder(user, id).type(ModDamageTypes.MELEE).damage(damage * set.damageMultiplier()).tag(AttackTag.MELEE)
                .origin(user.getEyePosition());
        if (set.blockable360()) b.tag(AttackTag.BLOCKABLE_360);
        return b;
    }

    private static MeleeMove groundLight(MeleeMoveset set, JJKConfig.Melee cfg, int index) {
        int interval = Math.max(4, Math.round(cfg.lightInterval / set.speedMultiplier()));
        return new MeleeMove("light_" + (index + 1), set.anim("light_" + (index + 1)), 2, 2, 5, interval, 0.55f,
                u -> frontBox(u, cfg.lightRange * set.rangeMultiplier(), cfg.lightWidth, 1.7),
                (u, t) -> base(u, "light", set, cfg.lightDamage)
                        .knockback(Combat.isAirborne(t) ? Knockback.set(horizontalLook(u).scale(0.22).add(0, 0.16, 0))
                                : Knockback.directional(u.getLookAngle(), 0.24 * set.knockbackMultiplier(), 0))
                        .hitstun(cfg.lightHitstun).fx(set.fx("hit_light"), 1f)
                        .status(CombatStatus.LAUNCHED, Combat.isAirborne(t) ? 12 : 0).build(),
                null, (u, r) -> keepClose(u, r.target()));
    }

    private static MeleeMove airLight(MeleeMoveset set, JJKConfig.Melee cfg, int index) {
        return new MeleeMove("air_" + (index + 1), set.anim("air_" + (index + 1)), 2, 2, 5, Math.max(4, cfg.lightInterval), 1f,
                u -> frontBox(u, cfg.lightRange * set.rangeMultiplier(), cfg.lightWidth + 0.3, 2.2),
                (u, t) -> base(u, "air_light", set, cfg.lightDamage)
                        .knockback(Knockback.NONE)
                        .hitstun(cfg.lightHitstun).status(CombatStatus.LAUNCHED, 16).fx(set.fx("hit_light"), 1f).build(),
                u -> Statuses.apply(u, CombatStatus.HOVER, 10),
                (u, r) -> {
                    Statuses.apply(u, CombatStatus.HOVER, 12);
                    Motion.set(u, new Vec3(u.getDeltaMovement().x * 0.3, cfg.airHitHover * 0.4, u.getDeltaMovement().z * 0.3));
                    if (r.connected()) stickInAir(u, r.target(), cfg);
                });
    }

    private static MeleeMove finisher(MeleeMoveset set, JJKConfig.Melee cfg) {
        return new MeleeMove("finisher", set.anim("light_4"), 3, 2, 12, 17, 0.4f,
                u -> frontBox(u, cfg.lightRange * set.rangeMultiplier() + 0.3, cfg.lightWidth + 0.2, 1.8),
                (u, t) -> base(u, "finisher", set, cfg.lightFinisherDamage)
                        .knockback(Knockback.directional(u.getLookAngle(), cfg.finisherKnockback * set.knockbackMultiplier(), 0.38))
                        .hitstun(cfg.lightHitstun + 8).status(CombatStatus.LAUNCHED, 14).fx(set.fx("hit_heavy"), 1.3f).build(),
                null, (u, r) -> shake(u, r, 0.5f));
    }

    private static MeleeMove uppercut(MeleeMoveset set, JJKConfig.Melee cfg) {
        return new MeleeMove("uppercut", set.anim("uppercut"), 3, 3, 10, 15, 0.4f,
                u -> frontBox(u, cfg.lightRange * set.rangeMultiplier(), cfg.lightWidth + 0.2, 2.4),
                (u, t) -> base(u, "uppercut", set, cfg.lightFinisherDamage)
                        .knockback(Knockback.set(horizontalLook(u).scale(0.12).add(0, cfg.uppercutLaunch * set.knockbackMultiplier(), 0)))
                        .hitstun(cfg.lightHitstun + 14).status(CombatStatus.LAUNCHED, 34).fx(set.fx("hit_launch"), 1.3f).build(),
                null, (u, r) -> shake(u, r, 0.45f));
    }

    private static MeleeMove downslam(MeleeMoveset set, JJKConfig.Melee cfg) {
        return new MeleeMove("downslam", set.anim("downslam"), 3, 3, 12, 17, 1f,
                u -> frontBox(u, cfg.lightRange * set.rangeMultiplier() + 0.4, cfg.lightWidth + 0.6, 3.0),
                (u, t) -> base(u, "downslam", set, cfg.lightFinisherDamage)
                        .knockback(Knockback.set(horizontalLook(u).scale(0.25).add(0, -cfg.downslamSpeed, 0)))
                        .hitstun(cfg.lightHitstun + 10).status(CombatStatus.SPIKED, 40).fx(set.fx("hit_slam"), 1.4f).build(),
                u -> Statuses.apply(u, CombatStatus.HOVER, 8),
                (u, r) -> {
                    Statuses.apply(u, CombatStatus.HOVER, 10);
                    shake(u, r, 0.55f);
                });
    }

    private static MeleeMove sprintAttack(MeleeMoveset set, JJKConfig.Melee cfg) {
        return new MeleeMove("sprint", set.anim("sprint"), 3, 5, 9, 12, 1f,
                u -> {
                    Vec3 start = u.position().add(0, u.getBbHeight() * 0.55, 0);
                    return HitShape.capsule(start, start.add(horizontalLook(u).scale(cfg.lightRange + 0.6)), 0.9);
                },
                (u, t) -> base(u, "sprint_attack", set, cfg.sprintAttackDamage)
                        .knockback(Knockback.directional(u.getLookAngle(), 0.55 * set.knockbackMultiplier(), 0.12))
                        .hitstun(cfg.lightHitstun + 4).fx(set.fx("hit_heavy"), 1.1f).build(),
                u -> Motion.set(u, horizontalLook(u).scale(cfg.sprintLunge).add(0, Math.max(0, u.getDeltaMovement().y), 0)),
                (u, r) -> Motion.set(u, u.getDeltaMovement().multiply(0.25, 1, 0.25)));
    }

    private static MeleeMove stomp(MeleeMoveset set, JJKConfig.Melee cfg) {
        return new MeleeMove("stomp", set.anim("stomp"), 3, 3, 9, 12, 0.5f,
                u -> HitShape.sphere(u.position().add(horizontalLook(u).scale(1.4)), 1.9),
                (u, t) -> base(u, "ground_attack", set, cfg.groundAttackDamage).tag(AttackTag.OTG)
                        .knockback(Knockback.set(horizontalLook(u).scale(0.1).add(0, 0.55, 0)))
                        .hitstun(cfg.lightHitstun + 6).status(CombatStatus.LAUNCHED, 18).fx(set.fx("hit_slam"), 1.1f).build(),
                null, (u, r) -> shake(u, r, 0.35f));
    }

    private static MeleeMove heavy(MeleeMoveset set, JJKConfig.Melee cfg, float charge) {
        float damage = Mth.lerp(charge, cfg.heavyMinDamage, cfg.heavyMaxDamage);
        return new MeleeMove(charge >= 1f ? "heavy_full" : "heavy", set.anim("heavy"), 4, 3, 14, 20, 0.3f,
                u -> frontBox(u, cfg.lightRange * set.rangeMultiplier() + 0.6, cfg.lightWidth + 0.5, 2.0),
                (u, t) -> base(u, "heavy", set, damage).tag(AttackTag.HEAVY, AttackTag.GUARD_BREAK).guardDamage(99)
                        .knockback(Knockback.directional(u.getLookAngle(), cfg.heavyKnockback * (0.7 + 0.3 * charge) * set.knockbackMultiplier(), 0.42))
                        .hitstun(cfg.lightHitstun + 12).status(CombatStatus.LAUNCHED, 16).fx(set.fx("hit_heavy"), 1.4f + charge * 0.6f).build(),
                u -> Motion.set(u, horizontalLook(u).scale(0.55).add(0, Math.max(0, u.getDeltaMovement().y), 0)),
                (u, r) -> shake(u, r, 0.6f + charge * 0.4f));
    }

    // --- Helpers ---

    private static Vec3 horizontalLook(LivingEntity u) {
        Vec3 l = u.getLookAngle();
        Vec3 h = new Vec3(l.x, 0, l.z);
        return h.lengthSqr() < 1e-6 ? Vec3.ZERO : h.normalize();
    }

    /**
     * Air combo glue: the target is drawn to a point just in front of the attacker at the attacker's height and
     * given the same gentle lift, so the two hang in the air together between hits.
     */
    private static void stickInAir(LivingEntity user, LivingEntity target, JJKConfig.Melee cfg) {
        Vec3 anchor = user.position().add(horizontalLook(user).scale(1.7 + target.getBbWidth() / 2)).add(0, 0.1, 0);
        Vec3 pull = anchor.subtract(target.position());
        double len = pull.length();
        Vec3 v = len > 3.5 ? pull.scale(0.35 * 3.5 / len) : pull.scale(0.35);
        Motion.set(target, new Vec3(v.x, v.y + cfg.airHitHover * 0.4, v.z));
    }

    /** Keeps a grounded combo target at punching distance instead of drifting away. */
    private static void keepClose(LivingEntity user, LivingEntity target) {
        double d = user.distanceTo(target);
        if (d > 2.6 && user.onGround()) {
            Vec3 toward = target.position().subtract(user.position());
            Motion.set(user, new Vec3(toward.x, 0, toward.z).normalize().scale(Math.min(0.4, (d - 2.2) * 0.3)));
        }
    }

    private static void shake(LivingEntity user, HitResult r, float intensity) {
        if (r.connected() && user.level() instanceof ServerLevel sl) Fx.shake(sl, r.target().position(), 10, intensity, 6);
    }

    private static boolean downedTargetNear(LivingEntity user) {
        return !HitboxQuery.query(user.level(), HitShape.sphere(user.position().add(horizontalLook(user).scale(1.4)), 2.2),
                e -> e != user && Combat.isDowned(e)).isEmpty();
    }

    @Nullable
    public static MeleeMove currentMove(AbilityCaster caster) {
        return caster.melee.current();
    }
}

