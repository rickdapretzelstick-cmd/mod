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
    /** The Prison Realm in the world (saved: it lies where it sealed someone). */
    public static final EntityType<dev.rick.jjk.progression.prison.PrisonRealmEntity> PRISON_REALM = register("prison_realm",
            EntityType.Builder.<dev.rick.jjk.progression.prison.PrisonRealmEntity>of(dev.rick.jjk.progression.prison.PrisonRealmEntity::new, MobCategory.MISC)
                    .sized(0.3f, 0.3f).noSummon().fireImmune().clientTrackingRange(16).updateInterval(2).noLootTable());
    /** A Cursed Breach: the way into a cursed realm, where an incident is anchored (never saved: the site raises it). */
    public static final EntityType<dev.rick.jjk.progression.investigation.CursedBreachEntity> CURSED_BREACH = register("cursed_breach",
            EntityType.Builder.<dev.rick.jjk.progression.investigation.CursedBreachEntity>of(dev.rick.jjk.progression.investigation.CursedBreachEntity::new, MobCategory.MISC)
                    .sized(1.1f, 1.9f).noSave().noSummon().fireImmune().clientTrackingRange(10).updateInterval(10).noLootTable());
    /** The common curses: graded cursed spirits found through investigations. */
    public static final EntityType<dev.rick.jjk.progression.curse.FlyHeadEntity> FLY_HEAD = register("fly_head",
            EntityType.Builder.<dev.rick.jjk.progression.curse.FlyHeadEntity>of(dev.rick.jjk.progression.curse.FlyHeadEntity::new, MobCategory.MONSTER)
                    .sized(0.65f, 0.85f).eyeHeight(0.5f).clientTrackingRange(10).noLootTable());
    public static final EntityType<dev.rick.jjk.progression.curse.SchoolCrawlerEntity> SCHOOL_CRAWLER = register("school_crawler",
            EntityType.Builder.<dev.rick.jjk.progression.curse.SchoolCrawlerEntity>of(dev.rick.jjk.progression.curse.SchoolCrawlerEntity::new, MobCategory.MONSTER)
                    .sized(1.2f, 1.2f).eyeHeight(0.8f).clientTrackingRange(10).noLootTable());
    public static final EntityType<dev.rick.jjk.progression.curse.SchoolMawEntity> SCHOOL_MAW = register("school_maw",
            EntityType.Builder.<dev.rick.jjk.progression.curse.SchoolMawEntity>of(dev.rick.jjk.progression.curse.SchoolMawEntity::new, MobCategory.MONSTER)
                    .sized(1.15f, 1.55f).eyeHeight(1.2f).clientTrackingRange(10).noLootTable());
    /** The hunting lodge's curse (a placeholder model: the School Crawler's). */
    public static final EntityType<dev.rick.jjk.progression.curse.ForestStalkerEntity> FOREST_STALKER = register("forest_stalker",
            EntityType.Builder.<dev.rick.jjk.progression.curse.ForestStalkerEntity>of(dev.rick.jjk.progression.curse.ForestStalkerEntity::new, MobCategory.MONSTER)
                    .sized(1.2f, 1.2f).eyeHeight(0.8f).clientTrackingRange(10).noLootTable());
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
        FabricDefaultAttributeRegistry.register(FLY_HEAD, dev.rick.jjk.progression.curse.FlyHeadEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(SCHOOL_CRAWLER, dev.rick.jjk.progression.curse.SchoolCrawlerEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(SCHOOL_MAW, dev.rick.jjk.progression.curse.SchoolMawEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(FOREST_STALKER, dev.rick.jjk.progression.curse.ForestStalkerEntity.createAttributes());
    }
}
