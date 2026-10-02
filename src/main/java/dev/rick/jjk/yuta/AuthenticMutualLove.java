package dev.rick.jjk.yuta;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.combat.Targeting;
import dev.rick.jjk.core.domain.DomainDefinition;
import dev.rick.jjk.core.domain.DomainInstance;
import dev.rick.jjk.core.domain.DomainManager;
import dev.rick.jjk.core.domain.structure.StructureSpec;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Domain Expansion: Authentic Mutual Love. A pale stone platform under a black sky, grave crosses rising from it and
 * knots of rope (their love) circling overhead; Rika looms in the dark. Blades rain down, but only four land within
 * reach, each carrying a random technique: Shrine, Thin Ice Breaker, Clairvoyance, Cursed Speech or Shikigami. Standing
 * by one, Yuta takes it up, runs about 55 studs and swings, and the technique goes off; another blade falls after every
 * pickup. They give no Awakening progress. No lethal sure hit; it breaks at once if no enemy is inside. 45 seconds.
 */
public final class AuthenticMutualLove implements DomainDefinition {
    public static final AuthenticMutualLove INSTANCE = new AuthenticMutualLove();
    public static final String ID = "authentic_mutual_love";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public int clashColor() {
        return 0xFFF76BFF;
    }

    @Override
    public double radius(LivingEntity owner) {
        return JJKConfig.get().yuta.domainRadius;
    }

    @Override
    public int duration(LivingEntity owner) {
        return JJKConfig.get().yuta.domainDuration;
    }

    @Override
    public String displayName() {
        return "Authentic Mutual Love";
    }

    @Override
    public int formingTicks() {
        return JJKConfig.get().yuta.domainFormationTicks;
    }

    @Override
    public StructureSpec structure(LivingEntity owner) {
        JJKConfig.Domain cfg = JJKConfig.get().domain;
        return new StructureSpec(JJKConfig.get().yuta.domainRadius, Math.max(1, cfg.shellThickness), ModBlocks.AML_BARRIER.defaultBlockState(),
                ModBlocks.AML_FLOOR.defaultBlockState(), cfg.clearInterior);
    }

    @Override
    public boolean closedBarrier() {
        return true;
    }

    /** No lethal sure hit: the katanas are the domain's weapon. */
    @Override
    public void applySureHit(DomainInstance domain, LivingEntity target, int ticksInside) {}

    @Override
    public void onActivated(DomainInstance domain) {
        YutaState s = YutaState.of(domain.owner);
        s.ladderHits = 0;
        s.ladderUsed = false;
        // Blades rain down; four land within reach.
        for (int i = 0; i < JJKConfig.get().yuta.domainBlades; i++) dropBlade(domain, 12 + i * 4);
        YutaSync.send(domain.owner);
    }

    @Override
    public void onTick(DomainInstance domain) {
        LivingEntity owner = domain.owner;
        if (domain.phase() != DomainInstance.Phase.ACTIVE) return;
        // Nobody to fight: the domain breaks at once.
        if (domain.age() > 10 && enemies(domain).isEmpty()) {
            DomainManager.cancel(domain, DomainInstance.EndReason.CANCELLED);
            return;
        }
        if (domain.age() % 4 == 0 && domain.level.getRandom().nextFloat() < 0.5f) {
            // Blades that land out of reach, for the look of it.
            Fx.play(domain.level, "blade_rain", domain.center.add(0, domain.radius * 0.7, 0), Vec3.ZERO, (float) domain.radius, owner.getId());
        }
        // Jacob's Ladder ready: a gold light keeps rising round him until he uses it.
        if (AuthenticMutualLoveAbility.ladderReady(owner) && domain.age() % 8 == 0) {
            Fx.play(domain.level, "ladder_ready_glow", owner.position(), Vec3.ZERO, 1f, owner.getId());
        }
        AbilityCaster c = Casters.getOrNull(owner);
        if (c == null || c.isBusy() || dev.rick.jjk.core.combat.Combat.actionsLocked(owner)) return;
        double reach = JJKConfig.get().yuta.bladePickupRange;
        for (DomainBladeEntity b : owner.level().getEntitiesOfClass(DomainBladeEntity.class, owner.getBoundingBox().inflate(reach + 0.5),
                b -> b.landed() && b.owner() == owner && !b.isRemoved())) {
            // Standing by it: he takes it up and runs.
            DomainTechnique t = b.technique();
            b.discard();
            YutaState.of(owner).fists = true;
            c.begin(new BladeRun(c, owner, t));
            dropBlade(domain, 30);
            break;
        }
    }

    /** One more blade falling from the sky to a free spot near the owner. */
    static void dropBlade(DomainInstance domain, double fall) {
        RandomSource r = domain.level.getRandom();
        LivingEntity owner = domain.owner;
        for (int tries = 0; tries < 12; tries++) {
            double a = r.nextDouble() * Mth.TWO_PI;
            double d = 2.5 + r.nextDouble() * Math.min(7, domain.radius * 0.45);
            Vec3 at = new Vec3(domain.center.x + Math.cos(a) * d, domain.center.y, domain.center.z + Math.sin(a) * d);
            if (!domain.contains(at)) continue;
            BlockPos floor = BlockPos.containing(at).below();
            if (domain.level.getBlockState(floor).isAir()) continue;
            DomainTechnique t = DomainTechnique.values()[r.nextInt(DomainTechnique.values().length)];
            DomainBladeEntity.drop(domain.level, owner, new Vec3(at.x, floor.getY() + 1, at.z), t, domain.id, Math.min(fall, domain.radius * 0.8));
            return;
        }
    }

    static List<LivingEntity> enemies(DomainInstance domain) {
        double r = domain.radius;
        return domain.level.getEntitiesOfClass(LivingEntity.class, new AABB(domain.center, domain.center).inflate(r),
                e -> e != domain.owner && e.isAlive() && !e.isSpectator() && domain.contains(e) && Targeting.canTarget(domain.owner, e));
    }

    @Override
    public void onCollapse(DomainInstance domain, DomainInstance.EndReason reason) {
        for (DomainBladeEntity b : domain.level.getEntitiesOfClass(DomainBladeEntity.class, new AABB(domain.center, domain.center).inflate(domain.radius + 4),
                b -> b.domainId() == domain.id)) {
            b.discard();
        }
        YutaState s = YutaState.get(domain.owner);
        if (s != null) s.ladderHits = 0;
        YutaSync.send(domain.owner);
    }
}
