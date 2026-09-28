package dev.rick.jjk.entity;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.combat.HitResolver;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.core.combat.Targeting;
import dev.rick.jjk.core.defense.DefenseResult;
import dev.rick.jjk.core.defense.Defenses;
import dev.rick.jjk.core.defense.IncomingAttack;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.hitbox.HitShape;
import dev.rick.jjk.core.hitbox.HitboxQuery;
import dev.rick.jjk.registry.ModDamageTypes;
import dev.rick.jjk.registry.ModEntities;
import dev.rick.jjk.util.Destruction;
import dev.rick.jjk.util.Motion;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Lapse: Blue. A point of attraction: everything nearby is dragged toward the core, harder the closer it is,
 * and held there. When it expires it implodes, leaving victims briefly stunned and bunched up in the air.
 * While the caster holds the key the point follows their crosshair.
 */
public class BlueEntity extends TechniqueEntity {
    public static final int ACTIVE = 0, COLLAPSING = 1;
    private int lifetime;
    private int collapseAge = -1;
    @Nullable private Vec3 steerTarget;
    private float power = 1f;
    private Params params = Params.normal();

    /** Tuning for one Blue. Normal Blue and Max Blue are the same technique at different scales. */
    public record Params(double pullRadius, double pullStrength, int duration, float tickDamage, int tickDamageInterval, float collapseDamage,
                         int collapseStun, double blockPullRadius, boolean pullsBlocks, float power, boolean ultimate) {
        public static Params normal() {
            JJKConfig.Blue c = JJKConfig.get().blue;
            return new Params(c.pullRadius, c.pullStrength, c.duration, c.tickDamage, c.tickDamageInterval, c.collapseDamage, c.collapseStun,
                    c.blockPullRadius, c.pullsBlocks, 1f, false);
        }

        public static Params max() {
            JJKConfig.MaxBlue c = JJKConfig.get().maxBlue;
            JJKConfig.Blue b = JJKConfig.get().blue;
            return new Params(c.pullRadius, c.pullStrength, c.duration, c.tickDamage, b.tickDamageInterval, c.collapseDamage, c.collapseStun,
                    c.blockPullRadius, b.pullsBlocks, c.power, true);
        }
    }

    public BlueEntity(EntityType<? extends BlueEntity> type, Level level) {
        super(type, level);
    }

    public static BlueEntity spawn(ServerLevel level, LivingEntity owner, Vec3 pos, float power) {
        return spawn(level, owner, pos, Params.normal());
    }

    public static BlueEntity spawn(ServerLevel level, LivingEntity owner, Vec3 pos, Params params) {
        float power = params.power();
        BlueEntity e = new BlueEntity(ModEntities.BLUE, level);
        e.setOwner(owner);
        e.setPos(pos.x, pos.y, pos.z);
        e.power = power;
        e.params = params;
        e.lifetime = params.duration();
        e.setScale(power);
        level.addFreshEntity(e);
        Fx.play(level, params.ultimate() ? "max_blue_spawn" : "blue_spawn", pos, Vec3.ZERO, power, e.getId());
        return e;
    }

    /** Steer toward a point this tick (while the caster holds the key). */
    public void steer(Vec3 target) {
        this.steerTarget = target;
    }

    /** Extends the lifetime while being steered, up to the configured maximum. */
    public void extend(int ticks) {
        lifetime += ticks;
    }

    public boolean isCollapsing() {
        return phase() == COLLAPSING;
    }

    /** Blue near a point (for Red amplification). */
    @Nullable
    public static BlueEntity findNear(ServerLevel level, Vec3 pos, double radius) {
        List<BlueEntity> list = level.getEntitiesOfClass(BlueEntity.class, new AABB(pos, pos).inflate(radius), b -> !b.isCollapsing());
        BlueEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (BlueEntity b : list) {
            double d = b.position().distanceToSqr(pos);
            if (d < bestD) {
                best = b;
                bestD = d;
            }
        }
        return best;
    }

    /** Consumed by a technique (e.g. Red detonating inside it). */
    public void consume() {
        discard();
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        if (ownerLost()) {
            discard();
            return;
        }
        if (isCollapsing()) {
            if (++collapseAge >= 6) discard();
            return;
        }
        JJKConfig.Blue cfg = JJKConfig.get().blue;
        if (steerTarget != null) {
            Vec3 d = steerTarget.subtract(position());
            double len = d.length();
            if (len > 0.05) {
                Vec3 step = d.scale(Math.min(1, 0.9 / Math.max(len, 0.9)) * Math.min(len, 1.6));
                setPos(getX() + step.x, getY() + step.y, getZ() + step.z);
            }
            steerTarget = null;
        }
        if (!level.isLoaded(blockPosition())) {
            discard();
            return;
        }
        pull(level, cfg);
        if (params.pullsBlocks() && tickCount % 3 == 0) tearBlocks(level, cfg);
        if (tickCount >= lifetime) collapse(level, cfg);
    }

    private void pull(ServerLevel level, JJKConfig.Blue cfg) {
        Vec3 core = position();
        double radius = params.pullRadius();
        long now = level.getGameTime();
        boolean damageTick = tickCount % Math.max(1, params.tickDamageInterval()) == 0;
        for (Entity e : level.getEntities(this, new AABB(core, core).inflate(radius), e -> e != owner && !(e instanceof TechniqueEntity))) {
            Vec3 c = e.getBoundingBox().getCenter();
            double d = c.distanceTo(core);
            if (d > radius) continue;
            double closeness = 1 - d / radius;
            Vec3 dir = d < 1e-3 ? Vec3.ZERO : core.subtract(c).scale(1 / d);
            if (e instanceof LivingEntity living) {
                if (!Targeting.canTarget(owner, living)) continue;
                // Infinity and other defenses get a say before anyone is dragged.
                IncomingAttack atk = new IncomingAttack(living, owner, this, core, Set.of(AttackTag.TECHNIQUE, AttackTag.LIMITLESS, AttackTag.AREA),
                        0.2f, null, null);
                if (Defenses.resolve(atk).kind() == DefenseResult.Kind.NEGATE) continue;
                double speed = params.pullStrength() * (0.35 + 0.9 * closeness);
                Vec3 v;
                if (d < 1.1) {
                    // At the core: held in place, slowly orbiting.
                    Vec3 tangent = new Vec3(-dir.z, 0, dir.x).scale(0.05);
                    v = dir.scale(d * 0.4).add(tangent);
                } else {
                    v = living.getDeltaMovement().scale(0.35).add(dir.scale(Math.min(speed, d * 0.5)));
                }
                Motion.set(living, v);
                Statuses.apply(living, CombatStatus.PULLED, 4);
                Combat.state(living).setPull(core, speed, now);
                if (damageTick && params.tickDamage() > 0) {
                    HitResolver.resolve(Hit.builder(owner, params.ultimate() ? "max_blue" : "blue").direct(this).type(ModDamageTypes.BLUE)
                            .damage(params.tickDamage()).tag(params.ultimate() ? AttackTag.ULTIMATE : AttackTag.LIMITLESS)
                            .tag(AttackTag.TECHNIQUE, AttackTag.LIMITLESS, AttackTag.AREA).origin(core).hitstun(8)
                            .knockback(Knockback.NONE).noComboScaling().fx("blue_hit", 0.6f).build(), living);
                }
            } else if (e instanceof ItemEntity || e instanceof Projectile || e instanceof net.minecraft.world.entity.item.FallingBlockEntity) {
                Motion.set(e, e.getDeltaMovement().scale(0.5).add(dir.scale(Math.min(0.45 + closeness, d * 0.5))));
            }
        }
    }

    private void tearBlocks(ServerLevel level, JJKConfig.Blue cfg) {
        if (!Destruction.allowed(level)) return;
        double r = params.blockPullRadius();
        int torn = 0;
        BlockPos c = blockPosition();
        int ri = (int) Math.ceil(r);
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-ri, -ri, -ri), c.offset(ri, ri, ri))) {
            if (torn >= (params.ultimate() ? 18 : 6)) break;
            if (p.distToCenterSqr(position()) > r * r) continue;
            BlockState s = level.getBlockState(p);
            if (s.isAir() || !loose(level, p, s)) continue;
            BlockPos at = p.immutable();
            int stateId = Block.getId(s);
            if (Destruction.destroy(level, at, 2f, this)) {
                torn++;
                Fx.play(level, "blue_debris", Vec3.atCenterOf(at), position().subtract(Vec3.atCenterOf(at)), 1f, stateId);
            }
        }
    }

    /** Light, loose terrain that Blue can rip up: plants, leaves, snow, sand, gravel, dirt surfaces. */
    private static boolean loose(ServerLevel level, BlockPos pos, BlockState s) {
        if (s.is(BlockTags.LEAVES) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS) || s.is(BlockTags.SAND) || s.canBeReplaced()
                || s.is(BlockTags.SNOW) || s.is(BlockTags.CROPS)) return true;
        float h = s.getDestroySpeed(level, pos);
        return h >= 0 && h <= 0.6f;
    }

    /** Implosion: stuns and bunches up everything near the core. */
    public void collapse(ServerLevel level, JJKConfig.Blue cfg) {
        if (isCollapsing()) return;
        setPhase(COLLAPSING);
        collapseAge = 0;
        Vec3 core = position();
        Fx.play(level, params.ultimate() ? "max_blue_collapse" : "blue_collapse", core, Vec3.ZERO, power, getId());
        Fx.shake(level, core, 16 * power, 0.5f * power, 8);
        Hit hit = Hit.builder(owner, "blue_collapse").direct(this).type(ModDamageTypes.BLUE).damage(params.collapseDamage())
                .tag(AttackTag.TECHNIQUE, AttackTag.LIMITLESS, AttackTag.AREA).tag(params.ultimate() ? AttackTag.ULTIMATE : AttackTag.AREA)
                .origin(core).hitstun(params.collapseStun())
                .knockback(Knockback.toward(core, 0.4)).status(CombatStatus.LAUNCHED, 20).fx("blue_hit", 1.2f).build();
        HitResolver.resolveAll(hit, HitboxQuery.targets(owner, HitShape.sphere(core, params.pullRadius() * 0.55), 0, false));
    }
}

