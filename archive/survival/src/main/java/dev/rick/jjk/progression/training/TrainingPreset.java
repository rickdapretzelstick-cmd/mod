package dev.rick.jjk.progression.training;

import java.util.List;
import java.util.Map;

/**
 * One character's training: what it is called and what its three gauges stand for (left, centre, right, always on
 * the same three keys). The timing itself comes from {@link TrainingSchedule} and the shared settings
 * ({@link dev.rick.jjk.config.JJKConfig.Training}); the scene from {@link TrainingScenes}.
 */
public record TrainingPreset(String kit, String title, List<String> gauges, List<Integer> colors) {
    private static final Map<String, TrainingPreset> ALL = Map.of(
            "yuji", new TrainingPreset("yuji", "EMOTIONAL CONTROL", List.of("EMOTION", "CE FLOW", "COMPOSURE"),
                    List.of(0xFFFF7A6B, 0xFF6FB8FF, 0xFFF4E3B5)),
            "gojo", new TrainingPreset("gojo", "BLUE: PRECISION", List.of("STONE I", "STONE II", "STONE III"),
                    List.of(0xFF5FB4FF, 0xFFA8ECFF, 0xFF6F7CFF)),
            "yuta", new TrainingPreset("yuta", "CURSED PARTNERS", List.of("BLADE", "OUTPUT", "RIKA"),
                    List.of(0xFFE8E8F4, 0xFFB98BFF, 0xFFFF86D8)),
            "ryu", new TrainingPreset("ryu", "OUTPUT STABILIZATION", List.of("LEFT FLOW", "CORE", "RIGHT FLOW"),
                    List.of(0xFFFFB45A, 0xFFFFE27A, 0xFFFF7E45)),
            "hakari", new TrainingPreset("hakari", "FEVER REELS", List.of("REEL 1", "REEL 2", "REEL 3"),
                    List.of(0xFF7CFF8A, 0xFFFFE15A, 0xFFFF6BD0)));

    public static TrainingPreset of(String kit) {
        TrainingPreset p = ALL.get(kit);
        return p != null ? p : ALL.get("yuji");
    }
}
