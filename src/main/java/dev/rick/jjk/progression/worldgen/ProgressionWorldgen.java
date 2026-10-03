package dev.rick.jjk.progression.worldgen;

import dev.rick.jjk.JJK;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.Feature;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * World generation for the Survival progression. Both features run in the last decoration step, after vanilla has placed
 * its structures (Nether fossils, mansions, igloos), and are added to vanilla's biomes through Fabric's biome API (their
 * feature/placed feature JSON is in data/jjk/worldgen).
 */
public final class ProgressionWorldgen {
    // 26.3: a feature type is its codec (data/jjk/worldgen/feature/*.json names it by "type").
    public static final MapCodec<? extends Feature> CURSED_SOUL_SAND = Registry.register(BuiltInRegistries.FEATURE_TYPE, JJK.id("cursed_soul_sand"),
            CursedSoulSandFeature.CODEC);
    public static final MapCodec<? extends Feature> CURSED_BATTLE_ROOM = Registry.register(BuiltInRegistries.FEATURE_TYPE, JJK.id("cursed_battle_room"),
            CursedBattleRoomFeature.CODEC);

    private ProgressionWorldgen() {}

    private static ResourceKey<PlacedFeature> placed(String name) {
        return ResourceKey.create(Registries.PLACED_FEATURE, JJK.id(name));
    }

    public static void init() {
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(Biomes.SOUL_SAND_VALLEY), GenerationStep.Decoration.TOP_LAYER_MODIFICATION,
                placed("cursed_soul_sand"));
        // Every Overworld biome: the feature only acts where a mansion or igloo starts, whichever biomes those end up in.
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.TOP_LAYER_MODIFICATION,
                placed("cursed_battle_room"));
    }
}
