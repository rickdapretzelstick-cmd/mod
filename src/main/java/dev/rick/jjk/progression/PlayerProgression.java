package dev.rick.jjk.progression;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * A player's own answer to "what have I permanently acquired?": the kits they earned in Survival, which of them is their
 * current Survival kit, and counters for acquisition paths to build on (Cursed Fingers eaten...). Persisted on the player
 * (survives death, logout and dimension changes) and checked against {@link KitOwnership}, the world's authority,
 * whenever they join.
 *
 * <p>Stored as one string attachment, like the character id ({@code ModAttachments.CHARACTER}):
 * {@code current|kit,kit|counter=n,counter=n}. Immutable: every change returns a new value to set back on the player.
 */
public record PlayerProgression(List<String> kits, String kit, Map<String, Integer> counters) {
    public static final PlayerProgression EMPTY = new PlayerProgression(List.of(), "", Map.of());

    public PlayerProgression {
        kits = List.copyOf(new LinkedHashSet<>(kits));
        kit = kit == null ? "" : kit;
        counters = Map.copyOf(counters);
    }

    public boolean owns(String id) {
        return kits.contains(id);
    }

    public int counter(String name) {
        return counters.getOrDefault(name, 0);
    }

    /** Adds a kit; it becomes the current Survival kit when the player had none. */
    public PlayerProgression withKit(String id) {
        List<String> k = new ArrayList<>(kits);
        if (!k.contains(id)) k.add(id);
        return new PlayerProgression(k, kit.isEmpty() ? id : kit, counters);
    }

    public PlayerProgression withoutKit(String id) {
        List<String> k = new ArrayList<>(kits);
        k.remove(id);
        return new PlayerProgression(k, kit.equals(id) ? (k.isEmpty() ? "" : k.get(0)) : kit, counters);
    }

    public PlayerProgression withCurrent(String id) {
        return new PlayerProgression(kits, id, counters);
    }

    public PlayerProgression addCounter(String name, int by) {
        Map<String, Integer> c = new LinkedHashMap<>(counters);
        c.merge(name, by, Integer::sum);
        return new PlayerProgression(kits, kit, c);
    }

    // --- The stored form ---

    public String encode() {
        StringBuilder sb = new StringBuilder(kit).append('|').append(String.join(",", kits)).append('|');
        boolean first = true;
        for (Map.Entry<String, Integer> e : counters.entrySet()) {
            if (!first) sb.append(',');
            sb.append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }
        return sb.toString();
    }

    public static PlayerProgression decode(String s) {
        if (s == null || s.isEmpty()) return EMPTY;
        String[] parts = s.split("\\|", -1);
        String current = parts.length > 0 ? parts[0] : "";
        List<String> kits = new ArrayList<>();
        if (parts.length > 1) for (String k : parts[1].split(",")) if (!k.isBlank()) kits.add(k.trim());
        Map<String, Integer> counters = new LinkedHashMap<>();
        if (parts.length > 2) {
            for (String kv : parts[2].split(",")) {
                int eq = kv.indexOf('=');
                if (eq <= 0) continue;
                try {
                    counters.put(kv.substring(0, eq), Integer.parseInt(kv.substring(eq + 1)));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return new PlayerProgression(kits, current, counters);
    }
}
