package dev.rick.jjk.progression.mastery;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One Mastery tree: a technique's ("technique/gojo") or a cursed tool's ("tool/slaughter_demon"). Defined entirely in
 * data ({@code data/jjk/mastery/<kind>/<name>.json}, listed in {@code data/jjk/mastery/index.json}): its nodes, what
 * each costs, what it needs first, where it sits, and what it changes. Code never special-cases a character or a tool
 * to draw or buy a node; abilities only ask {@link Mastery} for the parameters and unlocks the nodes grant.
 *
 * @param id     "technique/gojo", "tool/cursed_cleaver"
 * @param kind   TECHNIQUE or TOOL
 * @param owner  the kit id or the cursed tool id this tree develops
 * @param title  shown at the top of the screen
 * @param lanes  column headings, left to right (usually one per move, plus core/awakening)
 */
public record MasteryTree(String id, Kind kind, String owner, String title, List<String> lanes, List<MasteryNode> nodes) {
    public enum Kind { TECHNIQUE, TOOL }

    public MasteryTree {
        lanes = List.copyOf(lanes);
        nodes = List.copyOf(nodes);
    }

    public Map<String, MasteryNode> byId() {
        Map<String, MasteryNode> m = new LinkedHashMap<>();
        for (MasteryNode n : nodes) m.put(n.id(), n);
        return m;
    }

    public MasteryNode node(String id) {
        for (MasteryNode n : nodes) if (n.id().equals(id)) return n;
        return null;
    }

    /** Tree id for a kit's technique. */
    public static String techniqueId(String kit) {
        return "technique/" + kit;
    }

    /** Tree id for a cursed tool. */
    public static String toolId(String tool) {
        return "tool/" + tool;
    }
}
