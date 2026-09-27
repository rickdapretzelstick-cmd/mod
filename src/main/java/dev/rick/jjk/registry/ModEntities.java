package dev.rick.jjk.registry;

import dev.rick.jjk.JJK;
import dev.rick.jjk.entity.BlueEntity;
import dev.rick.jjk.entity.HollowPurpleEntity;
import dev.rick.jjk.entity.RedEntity;
import dev.rick.jjk.entity.TrainingDummy;
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
    public static final EntityType<TrainingDummy> TRAINING_DUMMY = register("training_dummy",
            EntityType.Builder.<TrainingDummy>of(TrainingDummy::new, MobCategory.MISC).sized(0.6f, 1.95f).clientTrackingRange(10));

    private ModEntities() {}

    private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JJK.id(name));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
    }

    public static void init() {
        FabricDefaultAttributeRegistry.register(TRAINING_DUMMY, TrainingDummy.createAttributes());
    }
}
