package dev.rick.jjk.yuta;

import dev.rick.jjk.entity.TechniqueEntity;
import dev.rick.jjk.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * One of Authentic Mutual Love's katanas: it falls from the sky and sticks point-first into the platform, carrying one
 * of the techniques (its {@link #phase()} is the technique's index in {@link DomainTechnique#values()}). Only the
 * domain's owner can take it up.
 */
public class DomainBladeEntity extends TechniqueEntity {
    private int domainId = -1;
    private boolean landed;

    public DomainBladeEntity(EntityType<? extends DomainBladeEntity> type, Level level) {
        super(type, level);
    }

    /** Drops a blade from high above {@code at}. */
    public static DomainBladeEntity drop(ServerLevel level, LivingEntity owner, Vec3 at, DomainTechnique technique, int domainId, double fall) {
        DomainBladeEntity b = new DomainBladeEntity(ModEntities.DOMAIN_BLADE, level);
        b.setOwner(owner);
        b.setPhase(technique.ordinal());
        b.domainId = domainId;
        b.setPos(at.x, at.y + fall, at.z);
        b.setYRot(level.getRandom().nextFloat() * 360f);
        level.addFreshEntity(b);
        return b;
    }

    public DomainTechnique technique() {
        DomainTechnique[] all = DomainTechnique.values();
        return all[Math.floorMod(phase(), all.length)];
    }

    public int domainId() {
        return domainId;
    }

    public boolean landed() {
        return landed;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;
        if (ownerLost()) {
            discard();
            return;
        }
        if (!landed) {
            // Falling point-first until it bites into the floor.
            BlockPos below = BlockPos.containing(getX(), getY() - 0.05, getZ());
            if (!level().getBlockState(below).isAir() || getY() < level().getMinY()) {
                landed = true;
                setPos(getX(), below.getY() + 1.0, getZ());
                setDeltaMovement(Vec3.ZERO);
                if (level() instanceof ServerLevel sl) {
                    dev.rick.jjk.core.fx.Fx.play(sl, "blade_land", position(), Vec3.ZERO, 1f, getId());
                }
            } else {
                double vy = Math.max(-1.6, getDeltaMovement().y - 0.12);
                setDeltaMovement(0, vy, 0);
                setPos(getX(), getY() + vy, getZ());
            }
        }
        if (tickCount > 20 * 120) discard();
    }
}
