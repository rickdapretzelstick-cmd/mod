package dev.rick.jjk.entity;

import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.combat.HitResolver;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.hitbox.HitShape;
import dev.rick.jjk.core.hitbox.HitboxQuery;
import dev.rick.jjk.registry.ModDamageTypes;
import dev.rick.jjk.registry.ModEntities;
import dev.rick.jjk.yuji.CombatInstinctsAbility;
import dev.rick.jjk.yuji.YujiCombat;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * A prop Combat Instincts punched across the battlefield: the block itself tumbling through the air (drawn with its own
 * model), breaking on the first thing it meets. Unblockable, 15 damage, a bullet.
 */
public class ThrownPropEntity extends TechniqueEntity {
    private static final EntityDataAccessor<Integer> BLOCK = SynchedEntityData.defineId(ThrownPropEntity.class, EntityDataSerializers.INT);
    private Vec3 velocity = Vec3.ZERO;
    private int life;

    public ThrownPropEntity(EntityType<? extends ThrownPropEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(BLOCK, Block.getId(Blocks.BARREL.defaultBlockState()));
    }

    public BlockState blockState() {
        return Block.stateById(entityData.get(BLOCK));
    }

    public static ThrownPropEntity fire(ServerLevel level, LivingEntity owner, Vec3 from, Vec3 dir, BlockState state) {
        ThrownPropEntity e = new ThrownPropEntity(ModEntities.THROWN_PROP, level);
        e.setOwner(owner);
        e.entityData.set(BLOCK, Block.getId(state));
        e.setPos(from.x, from.y, from.z);
        e.velocity = dir.normalize().scale(YujiCombat.cfg().throwableSpeed);
        e.setDeltaMovement(e.velocity);
        level.addFreshEntity(e);
        return e;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        if (ownerLost() || ++life > 80) {
            discard();
            return;
        }
        velocity = velocity.add(0, -0.03, 0);
        Vec3 from = position(), to = from.add(velocity);
        if (!level.isPositionEntityTicking(BlockPos.containing(to))) {
            discard();
            return;
        }
        List<LivingEntity> hits = HitboxQuery.targets(owner, HitShape.capsule(from, to, 0.7), 0.2, false);
        BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (!hits.isEmpty()) {
            LivingEntity t = hits.getFirst();
            HitResolver.resolve(Hit.builder(owner, CombatInstinctsAbility.ID).direct(this).type(ModDamageTypes.TECHNIQUE)
                    .damage(YujiCombat.cfg().throwableDamage).tag(AttackTag.PROJECTILE, AttackTag.UNBLOCKABLE).origin(from)
                    .knockback(Knockback.directional(velocity.normalize(), 1.1, 0.35)).hitstun(24).status(CombatStatus.LAUNCHED, 20)
                    .fx("hit_heavy", 1.3f).build(), t);
            smash(level, t.getBoundingBox().getCenter());
            return;
        }
        if (block.getType() != HitResult.Type.MISS) {
            smash(level, block.getLocation());
            return;
        }
        setPos(to.x, to.y, to.z);
        setDeltaMovement(velocity);
    }

    private void smash(ServerLevel level, Vec3 at) {
        Fx.play(level, "prop_smash", at, velocity.normalize(), Block.getId(blockState()), owner != null ? owner.getId() : -1);
        discard();
    }
}
