package dev.rick.jjk.progression.story;

import dev.rick.jjk.progression.ProgressionItems;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The five character storylines. Adding a character's storyline is one entry here, four incident templates
 * ({@code data/jjk/incidents/<kit>_chain_<n>.json}), its three items and one {@link PersonalTrials.Trial}.
 */
public final class CharacterStories {
    public static final CharacterStory YUJI = new CharacterStory("yuji", "Yuji Itadori", chain("yuji"),
            () -> ProgressionItems.YUJI_ESSENCE, () -> ProgressionItems.HUMAN_EARTHWORM_VHS, () -> ProgressionItems.INFUSED_HUMAN_EARTHWORM_VHS,
            CharacterStory.Activation.PLAY, 0xFF5A3A);
    public static final CharacterStory GOJO = new CharacterStory("gojo", "Satoru Gojo", chain("gojo"),
            () -> ProgressionItems.GOJO_ESSENCE, () -> ProgressionItems.BLINDFOLD, () -> ProgressionItems.INFUSED_BLINDFOLD,
            CharacterStory.Activation.WEAR, 0x6EC8FF);
    public static final CharacterStory YUTA = new CharacterStory("yuta", "Yuta Okkotsu", chain("yuta"),
            () -> ProgressionItems.YUTA_ESSENCE, () -> ProgressionItems.CURSED_RING, () -> ProgressionItems.INFUSED_CURSED_RING,
            CharacterStory.Activation.USE, 0xFF8AD6);
    public static final CharacterStory RYU = new CharacterStory("ryu", "Ryu Ishigori", chain("ryu"),
            () -> ProgressionItems.RYU_ESSENCE, () -> ProgressionItems.COMB, () -> ProgressionItems.INFUSED_COMB,
            CharacterStory.Activation.USE, 0xFFD04A);
    public static final CharacterStory HAKARI = new CharacterStory("hakari", "Kinji Hakari", chain("hakari"),
            () -> ProgressionItems.HAKARI_ESSENCE, () -> ProgressionItems.SCRATCH_OFF_TICKET, () -> ProgressionItems.INFUSED_SCRATCH_OFF_TICKET,
            CharacterStory.Activation.USE, 0x6AFFD8);

    private static final Map<String, CharacterStory> BY_KIT = new LinkedHashMap<>();

    static {
        for (CharacterStory s : List.of(YUJI, GOJO, YUTA, RYU, HAKARI)) BY_KIT.put(s.kit(), s);
    }

    private CharacterStories() {}

    private static List<String> chain(String kit) {
        return List.of(kit + "_chain_1", kit + "_chain_2", kit + "_chain_3", kit + "_chain_4");
    }

    public static List<CharacterStory> all() {
        return List.copyOf(BY_KIT.values());
    }

    @Nullable
    public static CharacterStory byKit(String kit) {
        return BY_KIT.get(kit);
    }

    /** The storyline an incident template belongs to, or null (an ordinary incident). */
    @Nullable
    public static CharacterStory ofTemplate(String template) {
        for (CharacterStory s : BY_KIT.values()) if (s.stageOf(template) > 0) return s;
        return null;
    }

    @Nullable
    public static CharacterStory byObject(net.minecraft.world.item.ItemStack stack) {
        for (CharacterStory s : BY_KIT.values()) if (stack.is(s.object().get())) return s;
        return null;
    }
}
