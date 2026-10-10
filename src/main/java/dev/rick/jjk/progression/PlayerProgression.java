package dev.rick.jjk.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * A player's own answer to "what have I permanently acquired?": the kits they earned in Survival, which of them is their
 * current Survival kit, the kit they last picked in Creative to test with (put away when they leave Creative, never
 * ownership), and free-form progression flags and counters for acquisition paths to build on (a cursed
 * object eaten, a room cleared...). Persisted on the player (survives death, logout and dimension changes) and always
 * checked against {@link KitOwnership}, the world's authority, when they join.
 *
 * <p>Immutable: every change returns a new value to set back on the player.
 */
public record PlayerProgression(List<String> kits, String kit, List<String> flags, Map<String, Integer> counters, String testKit) {
    public static final PlayerProgression EMPTY = new PlayerProgression(List.of(), "", List.of(), Map.of(), "");

    public static final Codec<PlayerProgression> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.listOf().optionalFieldOf("kits", List.of()).forGetter(PlayerProgression::kits),
            Codec.STRING.optionalFieldOf("kit", "").forGetter(PlayerProgression::kit),
            Codec.STRING.listOf().optionalFieldOf("flags", List.of()).forGetter(PlayerProgression::flags),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("counters", Map.of()).forGetter(PlayerProgression::counters),
            Codec.STRING.optionalFieldOf("test_kit", "").forGetter(PlayerProgression::testKit)
    ).apply(i, PlayerProgression::new));

    public PlayerProgression {
        kits = List.copyOf(new LinkedHashSet<>(kits));
        kit = kit == null ? "" : kit;
        flags = List.copyOf(new LinkedHashSet<>(flags));
        counters = Map.copyOf(counters);
        testKit = testKit == null ? "" : testKit;
    }

    public boolean owns(String id) {
        return kits.contains(id);
    }

    public boolean hasFlag(String flag) {
        return flags.contains(flag);
    }

    public int counter(String name) {
        return counters.getOrDefault(name, 0);
    }

    /** Adds a kit; it becomes the current Survival kit when the player had none. */
    public PlayerProgression withKit(String id) {
        List<String> k = new ArrayList<>(kits);
        if (!k.contains(id)) k.add(id);
        return new PlayerProgression(k, kit.isEmpty() ? id : kit, flags, counters, testKit);
    }

    public PlayerProgression withoutKit(String id) {
        List<String> k = new ArrayList<>(kits);
        k.remove(id);
        return new PlayerProgression(k, kit.equals(id) ? (k.isEmpty() ? "" : k.get(0)) : kit, flags, counters, testKit);
    }

    public PlayerProgression withCurrent(String id) {
        return new PlayerProgression(kits, id, flags, counters, testKit);
    }

    /** The kit last picked in Creative for testing ("" = none): never ownership, see {@link TechniqueProgression}. */
    public PlayerProgression withTestKit(String id) {
        return new PlayerProgression(kits, kit, flags, counters, id == null ? "" : id);
    }

    public PlayerProgression withFlag(String flag) {
        List<String> f = new ArrayList<>(flags);
        f.add(flag);
        return new PlayerProgression(kits, kit, f, counters, testKit);
    }

    public PlayerProgression withoutFlag(String flag) {
        List<String> f = new ArrayList<>(flags);
        f.remove(flag);
        return new PlayerProgression(kits, kit, f, counters, testKit);
    }

    public PlayerProgression addCounter(String name, int by) {
        Map<String, Integer> c = new LinkedHashMap<>(counters);
        c.merge(name, by, Integer::sum);
        return new PlayerProgression(kits, kit, flags, c, testKit);
    }
}
