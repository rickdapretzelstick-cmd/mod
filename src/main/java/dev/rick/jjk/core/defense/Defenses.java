package dev.rick.jjk.core.defense;

import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Registry of defense layers and the resolution pipeline. */
public final class Defenses {
    private static final List<DefenseLayer> LAYERS = new ArrayList<>();

    private Defenses() {}

    public static synchronized void register(DefenseLayer layer) {
        LAYERS.removeIf(l -> l.id().equals(layer.id()));
        LAYERS.add(layer);
        LAYERS.sort(Comparator.comparingInt(DefenseLayer::priority).reversed());
    }

    public static List<DefenseLayer> layers() {
        return LAYERS;
    }

    /** True when any defense is currently up (e.g. "is the target protected by a defensive technique?"). */
    public static boolean isProtected(LivingEntity entity) {
        for (DefenseLayer l : LAYERS) if (l.isActive(entity)) return true;
        return false;
    }

    public static boolean isActive(LivingEntity entity, String id) {
        for (DefenseLayer l : LAYERS) if (l.id().equals(id) && l.isActive(entity)) return true;
        return false;
    }

    /**
     * Runs the attack through every active layer. Returns the first decisive result, or a REDUCE/PASS
     * carrying the combined damage scale of any reducing layers.
     */
    public static DefenseResult resolve(IncomingAttack attack) {
        LivingEntity defender = attack.target;
        float scale = 1f;
        String reason = "";
        for (DefenseLayer layer : LAYERS) {
            if (!layer.isActive(defender)) continue;
            DefenseResult r = layer.intercept(defender, attack);
            switch (r.kind()) {
                case PASS -> {}
                case REDUCE -> {
                    scale *= r.damageScale();
                    reason = r.reason();
                    layer.afterIntercept(defender, attack, r);
                }
                default -> {
                    layer.afterIntercept(defender, attack, r);
                    if (scale != 1f) return new DefenseResult(r.kind(), r.damageScale() * scale, r.reason());
                    return r;
                }
            }
        }
        return scale == 1f ? DefenseResult.PASS : DefenseResult.reduce(scale, reason);
    }
}
