package dev.rick.jjk.progression.tool;

import dev.rick.jjk.progression.mastery.MasteryTree;

/**
 * One cursed tool: a weapon carrying cursed energy, the first way an ordinary person can exorcise a curse. Its
 * {@link #id} names its Mastery tree ({@code tool/<id>}, data in {@code data/jjk/mastery/tool/<id>.json}) and the prefix
 * of the parameters that tree changes ({@code tool.<id>.damage}...). Its {@link ToolBehavior} is what the tree's
 * unlocks do in a fight.
 *
 * @param style        how it fights (decides nothing by itself: it's for the screen and future tools' defaults)
 * @param damage       its swing's attack damage (on top of the player's base 1)
 * @param attackSpeed  attacks per second
 */
public record CursedToolDefinition(String id, String displayName, Style style, float damage, float attackSpeed, String description, Rarity rarity) {
    public enum Style { FAST, HEAVY, RANGED, SPECIAL }

    /**
     * How many can exist. {@link #COMMON}: anyone can make or find one, as often as they like. {@link #RARE}: found, not
     * made (its source decides how often). {@link #UNIQUE}: one in the whole world, ever live at once (like a technique).
     */
    public enum Rarity { COMMON, RARE, UNIQUE }

    public CursedToolDefinition(String id, String displayName, Style style, float damage, float attackSpeed, String description) {
        this(id, displayName, style, damage, attackSpeed, description, Rarity.COMMON);
    }

    public boolean unique() {
        return rarity == Rarity.UNIQUE;
    }

    public String treeId() {
        return MasteryTree.toolId(id);
    }

    /** "tool.slaughter_demon.damage" */
    public String paramKey(String name) {
        return "tool." + id + "." + name;
    }

    /** "tool.slaughter_demon.flurry" */
    public String unlockKey(String name) {
        return "tool." + id + "." + name;
    }
}
