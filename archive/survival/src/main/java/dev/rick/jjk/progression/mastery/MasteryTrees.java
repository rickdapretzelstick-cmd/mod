package dev.rick.jjk.progression.mastery;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.rick.jjk.JJK;
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
 * Every Mastery tree, read from the mod's data ({@code data/jjk/mastery/index.json} lists the files). Both sides load the
 * same files, so the client draws exactly what the server enforces. Adding a character's tree or a cursed tool's tree is
 * one JSON file and a line in the index.
 */
public final class MasteryTrees {
    private static final Map<String, MasteryTree> TREES = new LinkedHashMap<>();
    private static boolean loaded;

    private MasteryTrees() {}

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        JsonObject index = read("data/jjk/mastery/index.json");
        if (index == null) {
            JJK.LOGGER.error("[mastery] no data/jjk/mastery/index.json");
            return;
        }
        for (JsonElement e : index.getAsJsonArray("trees")) {
            String file = e.getAsString();
            JsonObject o = read("data/jjk/mastery/" + file + ".json");
            if (o == null) {
                JJK.LOGGER.error("[mastery] missing tree {}", file);
                continue;
            }
            try {
                MasteryTree t = parse(o);
                validate(t);
                TREES.put(t.id(), t);
            } catch (RuntimeException ex) {
                JJK.LOGGER.error("[mastery] bad tree {}: {}", file, ex.getMessage());
            }
        }
        JJK.LOGGER.info("[mastery] {} trees: {}", TREES.size(), TREES.keySet());
    }

    @Nullable
    public static MasteryTree get(String id) {
        load();
        return TREES.get(id);
    }

    public static Collection<MasteryTree> all() {
        load();
        return TREES.values();
    }

    @Nullable
    private static JsonObject read(String path) {
        try (InputStream in = MasteryTrees.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) return null;
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            JJK.LOGGER.error("[mastery] could not read {}", path, e);
            return null;
        }
    }

    static MasteryTree parse(JsonObject o) {
        List<String> lanes = new ArrayList<>();
        for (JsonElement l : o.getAsJsonArray("lanes")) lanes.add(l.getAsString());
        List<MasteryNode> nodes = new ArrayList<>();
        for (JsonElement ne : o.getAsJsonArray("nodes")) {
            JsonObject n = ne.getAsJsonObject();
            List<String> req = new ArrayList<>();
            if (n.has("requires")) for (JsonElement r : n.getAsJsonArray("requires")) req.add(r.getAsString());
            List<MasteryNode.Effect> fx = new ArrayList<>();
            if (n.has("effects")) {
                for (JsonElement fe : n.getAsJsonArray("effects")) {
                    JsonObject f = fe.getAsJsonObject();
                    if (f.has("unlock")) fx.add(new MasteryNode.Effect(MasteryNode.Effect.Op.UNLOCK, f.get("unlock").getAsString(), 1));
                    else if (f.has("mul")) fx.add(new MasteryNode.Effect(MasteryNode.Effect.Op.MUL, f.get("param").getAsString(), f.get("mul").getAsDouble()));
                    else if (f.has("add")) fx.add(new MasteryNode.Effect(MasteryNode.Effect.Op.ADD, f.get("param").getAsString(), f.get("add").getAsDouble()));
                }
            }
            nodes.add(new MasteryNode(n.get("id").getAsString(), n.get("name").getAsString(), str(n, "description"),
                    MasteryNode.Tier.valueOf(str(n, "tier").isEmpty() ? "SMALL" : str(n, "tier")), n.get("cost").getAsInt(),
                    n.has("lane") ? n.get("lane").getAsInt() : 0, n.has("row") ? n.get("row").getAsInt() : 0, str(n, "move"), req, fx));
        }
        return new MasteryTree(o.get("id").getAsString(), MasteryTree.Kind.valueOf(o.get("kind").getAsString()), o.get("owner").getAsString(),
                o.get("title").getAsString(), lanes, nodes);
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) ? o.get(k).getAsString() : "";
    }

    /** Prerequisites name real nodes and sit above their dependents; every node is reachable. */
    static void validate(MasteryTree t) {
        Map<String, MasteryNode> by = t.byId();
        if (by.size() != t.nodes().size()) throw new IllegalArgumentException("duplicate node ids");
        java.util.Set<Long> cells = new java.util.HashSet<>();
        for (MasteryNode n : t.nodes()) {
            if (!cells.add(((long) n.lane() << 32) | n.row())) throw new IllegalArgumentException(n.id() + ": two nodes in one place");
        }
        for (MasteryNode n : t.nodes()) {
            if (n.cost() < 0) throw new IllegalArgumentException(n.id() + ": negative cost");
            if (n.lane() < 0 || n.lane() >= Math.max(1, t.lanes().size())) throw new IllegalArgumentException(n.id() + ": lane out of range");
            for (String r : n.requires()) {
                MasteryNode p = by.get(r);
                if (p == null) throw new IllegalArgumentException(n.id() + " requires unknown " + r);
                if (p.row() >= n.row()) throw new IllegalArgumentException(n.id() + " requires " + r + " on the same row or below");
            }
        }
    }

    /** Test hook: parses and validates a tree from JSON text. */
    public static MasteryTree parseForTest(String json) {
        MasteryTree t = parse(JsonParser.parseString(json).getAsJsonObject());
        validate(t);
        return t;
    }
}
