package dev.rick.jjk.progression.guardian;

import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.progression.curse.VeiledBrideEntity;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A Grade 1 guardian body: the veiled long-armed curse ({@code jjk:guardian_veiled_reacher}). Veiled, with arms far too long: it flickers close, grasps with both hands, and wails. Its phase beat is a wail with its arms flung wide.
 *
 * <p>It fights as the curse it stood in for until it had a body of its own ({@link VeiledBrideEntity}: the same moves on the
 * same ticks), drawn with its own model and clips ({@code guardian_veiled_reacher_<variant>}, the clips shared by its three looks).
 */
public class VeiledReacherGuardian extends VeiledBrideEntity implements GuardianBody {
    private static final EntityDataAccessor<Byte> VARIANT = SynchedEntityData.defineId(VeiledReacherGuardian.class, EntityDataSerializers.BYTE);
    public static final String KIND = "guardian_veiled_reacher";

    public VeiledReacherGuardian(EntityType<? extends VeiledReacherGuardian> type, Level level) {
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
