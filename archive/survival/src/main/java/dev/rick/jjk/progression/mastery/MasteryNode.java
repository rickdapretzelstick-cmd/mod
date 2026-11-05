package dev.rick.jjk.progression.mastery;

import java.util.List;

/**
 * One node of a Mastery tree.
 *
 * @param id          unique within its tree
 * @param name        shown on the node
 * @param description what it does, in plain words
 * @param tier        SMALL (a number gets better), MECHANICAL (a new interaction, an R variant), MAJOR (a new state or a
 *                    big extension), AWAKENING (the character's awakened form: the tree's milestone)
 * @param cost        Mastery it costs
 * @param lane        the column it is drawn in (an index into {@link MasteryTree#lanes()})
 * @param row         its row from the top (0 = root); deeper nodes need more before them
 * @param move        the move it develops, for the description panel ("" for a general node)
 * @param requires    every node that must be bought first
 * @param effects     what owning it changes
 */
public record MasteryNode(String id, String name, String description, Tier tier, int cost, int lane, int row, String move,
                          List<String> requires, List<Effect> effects) {
    public enum Tier { SMALL, MECHANICAL, MAJOR, AWAKENING }

    public MasteryNode {
        requires = List.copyOf(requires);
        effects = List.copyOf(effects);
    }

    /**
     * One change: {@code unlock} turns a gated behaviour on (an R variant, a follow-up, the awakening); {@code mul}
     * multiplies a parameter (range, radius, cooldown, damage...); {@code add} adds to one.
     */
    public record Effect(Op op, String key, double value) {
        public enum Op { UNLOCK, MUL, ADD }
    }
}
