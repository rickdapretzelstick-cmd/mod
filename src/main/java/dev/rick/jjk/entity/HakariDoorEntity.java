package dev.rick.jjk.entity;

import dev.rick.jjk.registry.ModEntities;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A door Hakari summons: the steel shutters of Shutter Doors, or the lacquered gamble door of Door Guard. Purely a
 * manifestation: the technique that owns it moves it every tick and does all the hitting. It disappears on its own
 * if the technique stops refreshing it.
 */
public class HakariDoorEntity extends TechniqueEntity {
    public static final int SHUTTER = 0, GUARD = 1;
    private static final EntityDataAccessor<Float> OPEN = SynchedEntityData.defineId(HakariDoorEntity.class, EntityDataSerializers.FLOAT);
    private int life;
    private int maxLife = 40;

    public HakariDoorEntity(EntityType<? extends HakariDoorEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OPEN, 0f);
    }

    public static HakariDoorEntity spawn(ServerLevel level, LivingEntity owner, int kind, Vec3 pos, float yaw, int maxLife) {
        HakariDoorEntity e = new HakariDoorEntity(ModEntities.HAKARI_DOOR, level);
        e.setOwner(owner);
        e.setPhase(kind);
        e.maxLife = maxLife;
        e.place(pos, yaw);
        level.addFreshEntity(e);
        return e;
    }

    /** Moves the door (the owning technique calls this every tick). */
    public void place(Vec3 pos, float yaw) {
        setPos(pos.x, pos.y, pos.z);
        setYRot(yaw);
        setYHeadRot(yaw);
        life = 0;
    }

    public int kind() {
        return phase();
    }

    /** 0 = shut, 1 = swung open (Door Guard's counter). */
    public float open() {
        return entityData.get(OPEN);
    }

    public void setOpen(float v) {
        entityData.set(OPEN, v);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;
        if (ownerLost() || ++life > maxLife) discard();
    }
}
