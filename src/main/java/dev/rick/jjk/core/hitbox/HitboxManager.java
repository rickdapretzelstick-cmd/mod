package dev.rick.jjk.core.hitbox;

import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Ticks persistent hitboxes per level. */
public final class HitboxManager {
    private static final Map<ServerLevel, List<ActiveHitbox>> ACTIVE = new IdentityHashMap<>();

    private HitboxManager() {}

    public static void add(ServerLevel level, ActiveHitbox hitbox) {
        ACTIVE.computeIfAbsent(level, l -> new ArrayList<>()).add(hitbox);
    }

    public static void tick(ServerLevel level) {
        List<ActiveHitbox> list = ACTIVE.get(level);
        if (list == null || list.isEmpty()) return;
        // Copy: resolving hits can spawn new hitboxes.
        for (ActiveHitbox h : List.copyOf(list)) if (!h.tick()) list.remove(h);
    }

    public static void clear(ServerLevel level) {
        ACTIVE.remove(level);
    }

    public static void clearAll() {
        ACTIVE.clear();
    }
}
