package dev.rick.jjk.progression.story;

import net.minecraft.world.item.Item;

import java.util.List;
import java.util.function.Supplier;

/**
 * One character's way into the world: the village storyline that ends in their Essence, the object the Essence is
 * crafted into, its infused (world-unique) form, how that relic is used, and the personal trial it opens.
 *
 * <pre>
 * village storyline (chain[0..3]) → Essence → object (crafted) → full cauldron → infused relic → personal trial → base kit
 * </pre>
 *
 * @param kit        the character id the trial grants (base kit only; never the Awakening)
 * @param name       how the storyline names the character in logs and admin output
 * @param chain      the village storyline's incident templates, in order (discovery, escalation, revelation, finale)
 * @param essence    the storyline's reward
 * @param object     the dormant character object (crafted from the Essence)
 * @param relic      the object infused: the world's one functional relic
 * @param activation how the relic opens the personal trial
 * @param color      the storyline's colour (title cards, essence motes)
 */
public record CharacterStory(String kit, String name, List<String> chain, Supplier<Item> essence, Supplier<Item> object, Supplier<Item> relic,
                             Activation activation, int color) {
    public enum Activation {
        /** Played on something (the VHS on a jukebox). */
        PLAY,
        /** Worn on the head for a few seconds (the Blindfold). */
        WEAR,
        /** Simply used (the Ring, the Comb, the Scratch-Off). */
        USE
    }

    /** The relic's key in {@link UniqueRelics}. */
    public String relicKey() {
        return RelicItem.key(kit);
    }

    /** Its stage (1-4) of an incident template in this storyline, or 0. */
    public int stageOf(String template) {
        int i = chain.indexOf(template);
        return i < 0 ? 0 : i + 1;
    }
}
