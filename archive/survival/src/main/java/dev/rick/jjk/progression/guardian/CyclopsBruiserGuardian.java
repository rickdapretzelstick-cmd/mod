package dev.rick.jjk.progression.guardian;

import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.progression.curse.SchoolMawEntity;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A Grade 1 guardian body: the heavy cyclops bruiser ({@code jjk:guardian_cyclops_bruiser}). Heavy and one-eyed: it brings both fists down (the maw's bite) or hooks what it has reeled in (the quick bite), throws its tongue from a gaping mouth (the lash) and slams the ground. Its phase beat is a chest-beating roar.
 *
 * <p>It fights as the curse it stood in for until it had a body of its own ({@link SchoolMawEntity}: the same moves on the
 * same ticks), drawn with its own model and clips ({@code guardian_cyclops_bruiser_<variant>}, the clips shared by its three looks).
 */
public class CyclopsBruiserGuardian extends SchoolMawEntity implements GuardianBody {
    private static final EntityDataAccessor<Byte> VARIANT = SynchedEntityData.defineId(CyclopsBruiserGuardian.class, EntityDataSerializers.BYTE);
    public static final String KIND = "guardian_cyclops_bruiser";

    public CyclopsBruiserGuardian(EntityType<? extends CyclopsBruiserGuardian> type, Level level) {
        super(type, level);
    }

    @Override
    public String curseKind() {
        return KIND;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(VARIANT, (byte) 0);
    }

    @Override
    public void setGuardianVariant(int variant) {
        entityData.set(VARIANT, (byte) Math.floorMod(variant, 3));
    }

    @Override
    public int guardianVariant() {
        return entityData.get(VARIANT);
    }

    @Override
    public void phaseBeat(ServerLevel level, int phase) {
        Anim.playOn(this, KIND + "_unleash");
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putInt("GuardianVariant", guardianVariant());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        setGuardianVariant(in.getIntOr("GuardianVariant", 0));
    }
}
