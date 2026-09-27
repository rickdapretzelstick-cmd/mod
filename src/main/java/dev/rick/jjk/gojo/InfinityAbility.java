package dev.rick.jjk.gojo;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatState;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.core.combat.Targeting;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.util.Motion;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Infinity toggle and its field. The field is the "things slow down as they approach" half of Infinity:
 * projectiles decelerate and hang in the air, walking entities can't close the last gap.
 * The interception half (hits that do arrive are stopped) is {@link InfinityDefense}.
 */
public final class InfinityAbility extends Ability {
    public static final String ID = "infinity";
    /** Projectiles currently held by an Infinity field: projectile UUID → ticks held. */
    private final Map<UUID, Hold> held = new HashMap<>();

    private static final class Hold {
        final Vec3 pos;
        int ticks;

        Hold(Vec3 pos) {
            this.pos = pos;
        }
    }

    public InfinityAbility() {
        super(ID);
    }

    @Override
    public Kind kind() {
        return Kind.TOGGLE;
    }

    @Override
    public int cooldown(AbilityCaster caster) {
        return JJKConfig.get().infinity.toggleCooldown;
    }

    @Override
    public boolean isToggled(AbilityCaster caster) {
        return caster.toggled(ID);
    }

    @Override
    public @Nullable String checkActivation(AbilityContext ctx) {
        return JJKConfig.get().infinity.enabled ? null : "disabled";
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        turnOn(ctx.caster(), true);
        return null;
    }

    public void turnOn(AbilityCaster caster, boolean announce) {
        caster.setToggle(ID, true);
        Statuses.apply(caster.owner, CombatStatus.INFINITY, 60);
        if (announce && caster.owner.level() instanceof ServerLevel sl) {
            Anim.play(caster.owner, "infinity_on");
            Fx.play(sl, "infinity_on", caster.owner.position().add(0, 1, 0), Vec3.ZERO, 1f, caster.owner.getId());
        }
    }

    @Override
    public void toggleOff(AbilityCaster caster, String reason) {
        if (!caster.toggled(ID)) return;
        caster.setToggle(ID, false);
        Statuses.remove(caster.owner, CombatStatus.INFINITY);
        boolean collapse = reason.equals("energy") || reason.equals("burnout") || reason.equals("overwhelmed");
        JJKConfig.Infinity cfg = JJKConfig.get().infinity;
        caster.startCooldown(AbilitySlot.SKILL_5, collapse ? cfg.collapseCooldown : cfg.toggleCooldown);
        if (caster.owner.level() instanceof ServerLevel sl) {
            Fx.play(sl, collapse ? "infinity_collapse" : "infinity_off", caster.owner.position().add(0, 1, 0), Vec3.ZERO, 1f, caster.owner.getId());
        }
    }

    /** Per-tick upkeep and field behaviour; called from Gojo's passive tick. */
    public void tickField(AbilityCaster caster) {
        LivingEntity owner = caster.owner;
        if (!caster.toggled(ID) || !(owner.level() instanceof ServerLevel level)) return;
        JJKConfig.Infinity cfg = JJKConfig.get().infinity;
        CombatState state = Combat.state(owner);
        if (state.techniquesLocked()) {
            toggleOff(caster, "burnout");
            return;
        }
        if (!caster.drain(cfg.upkeepPerSecond / 20f)) {
            toggleOff(caster, "energy");
            return;
        }
        if (state.get(CombatStatus.INFINITY) < 20) state.set(CombatStatus.INFINITY, 60);

        Vec3 center = owner.getBoundingBox().getCenter();
        double r = cfg.radius;
        AABB area = owner.getBoundingBox().inflate(r + 3);
        for (Entity e : level.getEntities(owner, area, e -> e instanceof Projectile || (cfg.repelWalkingEntities && e instanceof LivingEntity))) {
            if (e instanceof Projectile p) {
                if (p.getOwner() == owner) continue;
                slowProjectile(caster, owner, p, center, cfg);
            } else if (e instanceof LivingEntity living && Targeting.canTarget(owner, living)) {
                holdBack(owner, living, center, cfg);
            }
        }
        if (owner.tickCount % 40 == 0) held.keySet().removeIf(id -> level.getEntity(id) == null);
    }

    private void slowProjectile(AbilityCaster caster, LivingEntity owner, Projectile p, Vec3 center, JJKConfig.Infinity cfg) {
        Vec3 pos = p.position();
        double d = pos.distanceTo(center) - owner.getBbWidth() / 2;
        Hold hold = held.get(p.getUUID());
        if (hold != null) {
            hold.ticks++;
            if (hold.ticks < cfg.projectileHangTicks) {
                p.setPos(hold.pos.x, hold.pos.y, hold.pos.z);
                Motion.set(p, Vec3.ZERO);
            } else if (hold.ticks == cfg.projectileHangTicks) {
                // Let go: it falls harmlessly.
                Motion.set(p, new Vec3(0, -0.05, 0));
            }
            return;
        }
        Vec3 v = p.getDeltaMovement();
        Vec3 toward = center.subtract(pos).normalize();
        double approach = v.dot(toward);
        // Where will it be next tick? Fast projectiles are caught before they can skip past the boundary.
        double next = pos.add(v).distanceTo(center) - owner.getBbWidth() / 2;
        if (approach <= 0 || Math.min(d, next) > cfg.radius) return;
        double f = Math.max(0, (Math.min(d, next) - cfg.stopDistance) / Math.max(0.1, cfg.radius - cfg.stopDistance));
        if (f < 0.25 || next < cfg.stopDistance) {
            // Stopped at the boundary: it hangs in space.
            Vec3 stopAt = center.add(pos.subtract(center).normalize().scale(cfg.stopDistance + owner.getBbWidth() / 2 + 0.2));
            p.setPos(stopAt.x, stopAt.y, stopAt.z);
            Motion.set(p, Vec3.ZERO);
            held.put(p.getUUID(), new Hold(stopAt));
            caster.drain(cfg.costPerBlockedProjectile);
            if (owner.level() instanceof ServerLevel sl) Fx.play(sl, "infinity_hold", stopAt, toward.reverse(), 0.8f, owner.getId());
        } else {
            // Asymptotic approach: the closer it gets, the slower it goes.
            Motion.set(p, v.scale(0.35 + 0.5 * f));
        }
    }

    private static void holdBack(LivingEntity owner, LivingEntity other, Vec3 center, JJKConfig.Infinity cfg) {
        Vec3 offset = other.getBoundingBox().getCenter().subtract(center);
        Vec3 flat = new Vec3(offset.x, 0, offset.z);
        double gap = flat.length() - (owner.getBbWidth() + other.getBbWidth()) / 2;
        if (gap > cfg.stopDistance || flat.lengthSqr() < 1e-4) return;
        Vec3 out = flat.normalize();
        Vec3 v = other.getDeltaMovement();
        double inward = -v.dot(out);
        Combat.state(other).apply(CombatStatus.INFINITY_SLOWED, 6);
        // Cancel inward motion and nudge back to the boundary; nothing gets closer than stopDistance.
        if (inward > 0 || gap < cfg.stopDistance * 0.6) {
            Vec3 corrected = v.add(out.scale(Math.max(0, inward))).add(out.scale((cfg.stopDistance - gap) * 0.25));
            if (other.tickCount % 2 == 0 || !(other instanceof net.minecraft.world.entity.player.Player)) Motion.set(other, corrected);
        }
    }
}
