package dev.rick.jjk.progression.guardian;

import dev.rick.jjk.core.fx.Fx;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * What a defeated Grade 1 guardian leaves: a slowly turning knot of its cursed energy where it fell. It is not hostile,
 * can't be hurt, and blocks nothing. Its challenger uses an Exorcism Seal on it to begin containing it
 * ({@link EssenceCapture}). It belongs to its contract ({@code contract}); the contract remembers it, so a remnant left
 * behind is raised again when its owner comes back for it.
 */
public class GuardianRemnantEntity extends Entity {
    private static final EntityDataAccessor<Boolean> CONTAINING = SynchedEntityData.defineId(GuardianRemnantEntity.class, EntityDataSerializers.BOOLEAN);
    private String contract = "";

    public GuardianRemnantEntity(EntityType<?> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(CONTAINING, false);
    }

    public String contract() {
        return contract;
    }

    public void setContract(String id) {
        contract = id;
    }

    public boolean containing() {
        return entityData.get(CONTAINING);
    }

    public void setContaining(boolean on) {
        entityData.set(CONTAINING, on);
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        if (!level().isClientSide() && level() instanceof ServerLevel l) {
            // A slowly turning knot of dark energy: a ring of motes, a dark core, brighter while it is being contained.
            double a = tickCount * (containing() ? 0.35 : 0.12);
            for (int i = 0; i < 3; i++) {
                double t = a + i * Math.PI * 2 / 3;
                l.sendParticles(new net.minecraft.core.particles.DustParticleOptions(containing() ? 0xD9B8FF : 0x6A2FB0, 1.1f), getX() + Math.cos(t) * 0.6,
                        getY() + 0.8 + Math.sin(t * 1.7) * 0.25, getZ() + Math.sin(t) * 0.6, 1, 0, 0, 0, 0);
            }
            if (tickCount % 3 == 0) l.sendParticles(net.minecraft.core.particles.ParticleTypes.SQUID_INK, getX(), getY() + 0.8, getZ(), 1, 0.15, 0.2, 0.15, 0.005);
            if (tickCount % 6 == 0) l.sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, getX(), getY() + 0.8, getZ(), 2, 0.25, 0.35, 0.25, 0.02);
            if (tickCount % 50 == 0) Fx.sound(l, position(), SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.3f, 0.5f);
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 at) {
        if (!(player instanceof ServerPlayer sp) || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        return EssenceCapture.begin(sp, this) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        contract = in.getStringOr("JjkContract", "");
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        out.putString("JjkContract", contract);
    }
}
