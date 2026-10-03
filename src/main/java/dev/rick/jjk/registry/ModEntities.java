package dev.rick.jjk.registry;

import dev.rick.jjk.JJK;
import dev.rick.jjk.entity.BlueEntity;
import dev.rick.jjk.entity.HollowPurpleEntity;
import dev.rick.jjk.entity.RedEntity;
import dev.rick.jjk.entity.TrainingDummy;
import dev.rick.jjk.entity.PachinkoBallEntity;
import dev.rick.jjk.entity.HakariDoorEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
    public static final EntityType<BlueEntity> BLUE = register("blue",
            EntityType.Builder.<BlueEntity>of(BlueEntity::new, MobCategory.MISC).sized(1f, 1f).noSave().noSummon().fireImmune()
                    .clientTrackingRange(10).updateInterval(1).noLootTable());
    public static final EntityType<RedEntity> RED = register("red",
            EntityType.Builder.<RedEntity>of(RedEntity::new, MobCategory.MISC).sized(0.6f, 0.6f).noSave().noSummon().fireImmune()
                    .clientTrackingRange(10).updateInterval(1).noLootTable());
    public static final EntityType<HollowPurpleEntity> HOLLOW_PURPLE = register("hollow_purple",
            EntityType.Builder.<HollowPurpleEntity>of(HollowPurpleEntity::new, MobCategory.MISC).sized(1.5f, 1.5f).noSave().noSummon().fireImmune()
                    .clientTrackingRange(16).updateInterval(1).noLootTable());
    public static final EntityType<PachinkoBallEntity> PACHINKO_BALL = register("pachinko_ball",
            EntityType.Builder.<PachinkoBallEntity>of(PachinkoBallEntity::new, MobCategory.MISC).sized(0.35f, 0.35f).noSave().noSummon().fireImmune()
                    .clientTrackingRange(8).updateInterval(1).noLootTable());
    public static final EntityType<HakariDoorEntity> HAKARI_DOOR = register("hakari_door",
            EntityType.Builder.<HakariDoorEntity>of(HakariDoorEntity::new, MobCategory.MISC).sized(1.2f, 2.6f).noSave().noSummon().fireImmune()
                    .clientTrackingRange(8).updateInterval(1).noLootTable());
    public static final EntityType<dev.rick.jjk.entity.ThrownPropEntity> THROWN_PROP = register("thrown_prop",
            EntityType.Builder.<dev.rick.jjk.entity.ThrownPropEntity>of(dev.rick.jjk.entity.ThrownPropEntity::new, MobCategory.MISC).sized(0.9f, 0.9f).noSave().noSummon().fireImmune()
                    .clientTrackingRange(8).updateInterval(1).noLootTable());
    public static final EntityType<dev.rick.jjk.entity.FireArrowEntity> FIRE_ARROW = register("fire_arrow",
            EntityType.Builder.<dev.rick.jjk.entity.FireArrowEntity>of(dev.rick.jjk.entity.FireArrowEntity::new, MobCategory.MISC).sized(0.6f, 0.6f).noSave().noSummon().fireImmune()
                    .clientTrackingRange(16).updateInterval(1).noLootTable());
    public static final EntityType<dev.rick.jjk.yuta.RikaEntity> RIKA = register("rika",
            EntityType.Builder.<dev.rick.jjk.yuta.RikaEntity>of(dev.rick.jjk.yuta.RikaEntity::new, MobCategory.MISC).sized(1.4f, 3.4f).noSave().noSummon().fireImmune()
                    .clientTrackingRange(12).updateInterval(1).noLootTable());
    public static final EntityType<dev.rick.jjk.yuta.DomainBladeEntity> DOMAIN_BLADE = register("domain_blade",
            EntityType.Builder.<dev.rick.jjk.yuta.DomainBladeEntity>of(dev.rick.jjk.yuta.DomainBladeEntity::new, MobCategory.MISC).sized(0.4f, 1.4f).noSave().noSummon().fireImmune()
                    .clientTrackingRange(10).updateInterval(1).noLootTable());
    /** The Finger Bearer (battle-room curse) and its cursed energy shots. */
    public static final EntityType<dev.rick.jjk.progression.curse.FingerBearerEntity> FINGER_BEARER = register("finger_bearer",
            EntityType.Builder.<dev.rick.jjk.progression.curse.FingerBearerEntity>of(dev.rick.jjk.progression.curse.FingerBearerEntity::new, MobCategory.MONSTER)
                    .sized(1.4f, 3.3f).eyeHeight(2.75f).fireImmune().clientTrackingRange(10).noLootTable());
    public static final EntityType<dev.rick.jjk.progression.curse.CursedEnergyShotEntity> CURSED_ENERGY_SHOT = register("cursed_energy_shot",
            EntityType.Builder.<dev.rick.jjk.progression.curse.CursedEnergyShotEntity>of(dev.rick.jjk.progression.curse.CursedEnergyShotEntity::new, MobCategory.MISC).sized(0.5f, 0.5f).noSave().noSummon().fireImmune()
                    .clientTrackingRange(10).updateInterval(1).noLootTable());
    public static final EntityType<TrainingDummy> TRAINING_DUMMY = register("training_dummy",
            EntityType.Builder.<TrainingDummy>of(TrainingDummy::new, MobCategory.MISC).sized(0.6f, 1.95f).clientTrackingRange(10));

    private ModEntities() {}

    private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JJK.id(name));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
    }

    public static void init() {
        FabricDefaultAttributeRegistry.register(TRAINING_DUMMY, TrainingDummy.createAttributes());
        FabricDefaultAttributeRegistry.register(FINGER_BEARER, dev.rick.jjk.progression.curse.FingerBearerEntity.createAttributes());
    }
}
