package dev.rick.jjk.client.model;

import com.google.gson.JsonParser;
import dev.rick.jjk.JJK;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

/**
 * Blockbench models shipped in {@code assets/<namespace>/models/bb/<name>.bbmodel}, loaded with the resource packs (so a
 * resource pack can replace one, and F3+T reloads them). Looked up by file name: {@code BbModels.get("rika")}.
 */
public final class BbModels {
    private static volatile Map<String, BbModel> models = Map.of();

    private BbModels() {}

    public static void init() {
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(JJK.id("bb_models"), new SimpleReloadListener<Map<String, BbModel>>() {
            @Override
            protected Map<String, BbModel> prepare(PreparableReloadListener.SharedState state) {
                Map<String, BbModel> out = new HashMap<>();
                for (Map.Entry<Identifier, Resource> e : state.resourceManager().listResources("models/bb", id -> id.getPath().endsWith(".bbmodel")).entrySet()) {
                    String path = e.getKey().getPath();
                    String name = path.substring(path.lastIndexOf('/') + 1, path.length() - ".bbmodel".length());
                    try (Reader r = e.getValue().openAsReader()) {
                        out.put(name, BbModel.parse(name, JsonParser.parseReader(r).getAsJsonObject()));
                    } catch (Exception ex) {
                        JJK.LOGGER.warn("[models] {}: {}", e.getKey(), ex.toString());
                    }
                }
                return out;
            }

            @Override
            protected void apply(Map<String, BbModel> loaded, PreparableReloadListener.SharedState state) {
                models = Map.copyOf(loaded);
                JJK.LOGGER.info("[models] {} Blockbench model(s): {}", loaded.size(), loaded.keySet());
            }
        });
    }

    @Nullable
    public static BbModel get(String name) {
        return models.get(name);
    }
}
