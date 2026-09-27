package dev.rick.jjk.core.character;

import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

public final class Characters {
    private static final Map<String, JJKCharacter> REGISTRY = new LinkedHashMap<>();

    private Characters() {}

    public static <T extends JJKCharacter> T register(T c) {
        REGISTRY.put(c.id, c);
        return c;
    }

    @Nullable
    public static JJKCharacter get(String id) {
        return REGISTRY.get(id);
    }

    public static Iterable<String> ids() {
        return REGISTRY.keySet();
    }
}
