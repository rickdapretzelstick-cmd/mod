package dev.rick.jjk;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JJK implements ModInitializer {
    public static final String MOD_ID = "jjk";
    public static final Logger LOGGER = LoggerFactory.getLogger("Jujutsu");

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        Bootstrap.init();
    }
}
