package dev.rick.jjk.progression.investigation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.rick.jjk.JJK;
import dev.rick.jjk.progression.grade.CurseGrade;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One kind of incident, as data ({@code data/jjk/incidents/<id>.json}, listed in {@code index.json}): what is behind it
 * (its curses and their grade), where it can happen ({@link Sites}), what sets it off ({@link Trigger}), whether the
 * fight happens in a cursed realm ({@link CursedRealms} layout id, or none for the open world), how long a residue trail
 * it leaves, and the reports a village writes about it. A new incident is a new JSON file; a new kind of place, trigger
 * or realm is one entry in the matching registry.
 */
public record IncidentTemplate(String id, int weight, CurseGrade grade, String site, Trigger trigger, String realm,
                               List<CurseSpawn> curses, int trail, List<Report> reports, String reward) {
    /**
     * How an incident is entered once a player has found it. One way only: its Cursed Breach, used ({@link CursedBreaches}).
     * Older templates' triggers (a jump, a bed, a doorway at night...) all read as this.
     */
    public enum Trigger { BREACH }

    /** Curses of one kind it brings: how many, and their grade if it isn't the incident's. */
    public record CurseSpawn(String kind, int min, int max, @Nullable CurseGrade grade) {}

    /** A report as a village would write it: {@code {name}}, {@code {dir}}, {@code {dist}}, {@code {animal}}, pronouns. */
    public record Report(String headline, String body) {}

    private static final Map<String, IncidentTemplate> ALL = new LinkedHashMap<>();
    private static boolean loaded;

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        JsonObject index = read("data/jjk/incidents/index.json");
        if (index == null) {
            JJK.LOGGER.error("[investigations] no data/jjk/incidents/index.json");
            return;
        }
        for (JsonElement e : index.getAsJsonArray("incidents")) {
            JsonObject o = read("data/jjk/incidents/" + e.getAsString() + ".json");
            if (o == null) {
                JJK.LOGGER.error("[investigations] missing incident {}", e.getAsString());
                continue;
            }
            try {
                IncidentTemplate t = parse(o);
                ALL.put(t.id(), t);
            } catch (RuntimeException ex) {
                JJK.LOGGER.error("[investigations] bad incident {}: {}", e.getAsString(), ex.toString());
            }
        }
        JJK.LOGGER.info("[investigations] {} incident templates: {}", ALL.size(), ALL.keySet());
    }

    static IncidentTemplate parse(JsonObject o) {
        List<CurseSpawn> curses = new ArrayList<>();
        for (JsonElement ce : o.getAsJsonArray("curses")) {
            JsonObject c = ce.getAsJsonObject();
            curses.add(new CurseSpawn(c.get("kind").getAsString(), c.get("min").getAsInt(), Math.max(c.get("min").getAsInt(), c.get("max").getAsInt()),
                    c.has("grade") ? CurseGrade.valueOf(c.get("grade").getAsString()) : null));
        }
        List<Report> reports = new ArrayList<>();
        for (JsonElement re : o.getAsJsonArray("reports")) {
            JsonObject r = re.getAsJsonObject();
            reports.add(new Report(r.get("headline").getAsString(), r.get("body").getAsString()));
        }
        if (reports.isEmpty()) throw new IllegalArgumentException("no reports");
        String site = o.get("site").getAsString();
        if (!Sites.exists(site)) throw new IllegalArgumentException("unknown site " + site);
        String realm = o.has("realm") ? o.get("realm").getAsString() : "";
        if (!realm.isEmpty() && !CursedRealms.hasLayout(realm)) throw new IllegalArgumentException("unknown realm " + realm);
        for (CurseSpawn c : curses) if (!CurseKinds.exists(c.kind())) throw new IllegalArgumentException("unknown curse " + c.kind());
        return new IncidentTemplate(o.get("id").getAsString(), o.has("weight") ? o.get("weight").getAsInt() : 1,
                CurseGrade.valueOf(o.get("grade").getAsString()), site, Trigger.BREACH,
                realm, curses, o.has("trail") ? o.get("trail").getAsInt() : 16, reports, o.has("reward") ? o.get("reward").getAsString() : "");
    }

    @Nullable
    private static JsonObject read(String path) {
        try (InputStream in = IncidentTemplate.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) return null;
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            JJK.LOGGER.error("[investigations] could not read {}", path, e);
            return null;
        }
    }

    @Nullable
    public static IncidentTemplate get(String id) {
        load();
        return ALL.get(id);
    }

    public static Collection<IncidentTemplate> all() {
        load();
        return ALL.values();
    }

    public boolean usesRealm() {
        return !realm.isEmpty();
    }
}
