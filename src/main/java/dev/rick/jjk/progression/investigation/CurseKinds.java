package dev.rick.jjk.progression.investigation;

import dev.rick.jjk.progression.curse.CommonCurseEntity;
import dev.rick.jjk.registry.ModEntities;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/** The curse kinds an incident can name, by the id its JSON uses. */
public final class CurseKinds {
    private static final Map<String, EntityType<? extends CommonCurseEntity>> KINDS = Map.of(
            "fly_head", ModEntities.FLY_HEAD,
            "school_crawler", ModEntities.SCHOOL_CRAWLER,
            "school_maw", ModEntities.SCHOOL_MAW,
            "forest_stalker", ModEntities.FOREST_STALKER);

    private CurseKinds() {}

    public static boolean exists(String kind) {
        return KINDS.containsKey(kind);
    }

    @Nullable
    public static EntityType<? extends CommonCurseEntity> type(String kind) {
        return KINDS.get(kind);
    }
}
