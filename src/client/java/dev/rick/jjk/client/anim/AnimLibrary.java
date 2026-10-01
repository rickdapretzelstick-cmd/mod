package dev.rick.jjk.client.anim;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.rick.jjk.JJK;
import dev.rick.jjk.client.anim.rig.Bone;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Every animation clip and reusable pose, loaded from {@code assets/<namespace>/animations/**.json} and reloaded with
 * resource packs (F3+T or {@code /jjkanim reload}). Clips live in a folder per character ({@code animations/gojo/},
 * {@code animations/hakari/}...) and poses in {@code animations/poses/}. Gameplay only ever names a clip.
 */
public final class AnimLibrary {
    private static volatile Registry current = new Registry();

    /**
     * A reusable partial pose: per bone (by its canonical name), any of rotation (degrees), position (pixels) and scale.
     * Player bones are canonicalised to {@link Bone#id}; another rig's bones keep the names its model gives them.
     */
    public record Pose(Map<String, float[][]> bones) {}

    /** The rig player clips animate; any other value names a model's own skeleton (e.g. "rika"). */
    public static final String PLAYER_RIG = "player";

    private record Registry(Map<String, Clip> clips, Map<String, Pose> poses, List<String> errors) {
        Registry() {
            this(new HashMap<>(), new HashMap<>(), new ArrayList<>());
        }
    }

    private AnimLibrary() {}

    public static void init() {
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(JJK.id("animations"), new SimpleReloadListener<Registry>() {
            @Override
            protected Registry prepare(PreparableReloadListener.SharedState state) {
                return load(state.resourceManager());
            }

            @Override
            protected void apply(Registry r, PreparableReloadListener.SharedState state) {
                install(r);
            }
        });
    }

    /** Reloads straight from the current resources (the debugger's reload; no full resource reload needed). */
    public static List<String> reload(ResourceManager rm) {
        Registry r = load(rm);
        install(r);
        return r.errors;
    }

    private static void install(Registry r) {
        current = r;
        for (String e : r.errors) JJK.LOGGER.warn("[animations] {}", e);
        JJK.LOGGER.info("[animations] {} clips, {} poses", r.clips.values().stream().distinct().count(), r.poses.values().stream().distinct().count());
    }

    @Nullable
    public static Clip get(String name) {
        return current.clips.get(name);
    }

    @Nullable
    public static Pose pose(String name) {
        return current.poses.get(name);
    }

    /** Every clip's short name, sorted. */
    public static List<String> names() {
        List<String> out = new ArrayList<>();
        for (var e : current.clips.entrySet()) if (!e.getKey().contains("/")) out.add(e.getKey());
        out.sort(null);
        return out;
    }

    public static List<String> errors() {
        return current.errors;
    }

    /** Compiles one clip from JSON text against the loaded poses (tools and tests); throws on bad input. */
    public static Clip parse(String name, String group, String json) {
        Registry r = new Registry(new HashMap<>(), current.poses, new ArrayList<>());
        return compile(name, group, JsonParser.parseString(json).getAsJsonObject(), r);
    }

    // ---------------------------------------------------------------- loading

    private static Registry load(ResourceManager rm) {
        Registry r = new Registry();
        Map<Identifier, Resource> found = new TreeMap<>(rm.listResources("animations", id -> id.getPath().endsWith(".json")));
        Map<String, JsonObject> poseJson = new LinkedHashMap<>();
        Map<String, JsonObject> clipJson = new LinkedHashMap<>();
        Map<String, String> clipGroup = new HashMap<>();
        for (var e : found.entrySet()) {
            String path = e.getKey().getPath();
            String[] seg = path.substring("animations/".length(), path.length() - ".json".length()).split("/");
            String base = seg[seg.length - 1];
            String group = seg.length > 1 ? seg[0] : "misc";
            JsonElement root;
            try (Reader reader = e.getValue().openAsReader()) {
                root = JsonParser.parseReader(reader);
            } catch (Exception ex) {
                r.errors.add(e.getKey() + ": " + ex.getMessage());
                continue;
            }
            if (!root.isJsonObject()) {
                r.errors.add(e.getKey() + ": not an object");
                continue;
            }
            JsonObject o = root.getAsJsonObject();
            if (group.equals("poses")) {
                if (o.has("poses")) {
                    for (var p : o.getAsJsonObject("poses").entrySet()) poseJson.put(p.getKey(), p.getValue().getAsJsonObject());
                } else {
                    poseJson.put(str(o, "name", base), o);
                }
                continue;
            }
            List<JsonObject> clips = new ArrayList<>();
            if (o.has("clips")) for (JsonElement c : o.getAsJsonArray("clips")) clips.add(c.getAsJsonObject());
            else clips.add(o);
            for (JsonObject c : clips) {
                String name = str(c, "name", base);
                clipJson.put(name, c);
                clipGroup.put(name, group);
            }
        }

        // Poses first (they may extend each other), then clips (which may mirror each other).
        Map<String, Pose> poses = new HashMap<>();
        for (String name : poseJson.keySet()) resolvePose(name, poseJson, poses, new HashSet<>(), r.errors);
        for (var e : poses.entrySet()) {
            r.poses.put(e.getKey(), e.getValue());
            r.poses.put("poses/" + e.getKey(), e.getValue());
        }
        Map<String, Clip> clips = new HashMap<>();
        for (var e : clipJson.entrySet()) {
            if (e.getValue().has("mirrorOf")) continue;
            try {
                clips.put(e.getKey(), compile(e.getKey(), clipGroup.get(e.getKey()), e.getValue(), r));
            } catch (Exception ex) {
                r.errors.add("clip " + e.getKey() + ": " + ex.getMessage());
            }
        }
        for (var e : clipJson.entrySet()) {
            if (!e.getValue().has("mirrorOf")) continue;
            Clip src = clips.get(e.getValue().get("mirrorOf").getAsString());
            if (src == null) r.errors.add("clip " + e.getKey() + ": mirrorOf unknown clip");
            else clips.put(e.getKey(), src.mirrored(e.getKey()));
        }
        for (var e : clips.entrySet()) {
            r.clips.put(e.getKey(), e.getValue());
            r.clips.put(e.getValue().group + "/" + e.getKey(), e.getValue());
        }
        return r;
    }

    @Nullable
    private static Pose resolvePose(String name, Map<String, JsonObject> json, Map<String, Pose> done, Set<String> visiting, List<String> errors) {
        if (done.containsKey(name)) return done.get(name);
        JsonObject o = json.get(name);
        if (o == null || !visiting.add(name)) {
            errors.add("pose " + name + (o == null ? ": unknown" : ": extends itself"));
            return null;
        }
        Map<String, float[][]> bones = new LinkedHashMap<>();
        String rig = str(o, "rig", PLAYER_RIG);
        if (o.has("extends")) {
            for (String parent : strings(o.get("extends"))) {
                Pose p = resolvePose(stripMirror(parent), json, done, visiting, errors);
                if (p != null) merge(bones, isMirror(parent) ? mirror(p.bones, rig) : p.bones);
            }
        }
        try {
            merge(bones, bones(o.getAsJsonObject("bones"), null, rig));
        } catch (Exception ex) {
            errors.add("pose " + name + ": " + ex.getMessage());
        }
        Pose pose = new Pose(bones);
        done.put(name, pose);
        return pose;
    }

    private record Keyed(float time, float[] value, Easing ease) {}

    private static Clip compile(String name, String group, JsonObject o, Registry r) {
        float fps = num(o, "fps", 50);
        float frameMs = 1000f / fps;
        // Keys may use the reference's own frame numbers: refStart is the reference frame the clip begins on.
        float refStart = num(o, "refStart", 0);
        String rig = str(o, "rig", PLAYER_RIG);
        // Per bone (canonical name), per channel.
        Map<String, List<List<Keyed>>> keyed = new LinkedHashMap<>();
        List<Clip.Marker> markers = new ArrayList<>();
        List<Float> keyTimes = new ArrayList<>();
        float last = 0;
        JsonArray keys = o.getAsJsonArray("keys");
        if (keys == null) throw new IllegalArgumentException("no keys");
        for (JsonElement ke : keys) {
            JsonObject k = ke.getAsJsonObject();
            float t = k.has("f") && !k.has("t") ? (k.get("f").getAsFloat() - refStart) * frameMs : time(k, "t", "f", frameMs, Float.NaN);
            if (Float.isNaN(t)) throw new IllegalArgumentException("a key has neither t (ms) nor f (frame)");
            last = Math.max(last, t);
            keyTimes.add(t);
            Easing keyEase = ease(k, Easing.EASE_IN_OUT);
            Map<String, float[][]> pose = new LinkedHashMap<>();
            if (k.has("pose")) {
                for (String p : strings(k.get("pose"))) {
                    Pose def = r.poses.get(stripMirror(p));
                    if (def == null) r.errors.add("clip " + name + ": unknown pose " + p);
                    else merge(pose, isMirror(p) ? mirror(def.bones, rig) : def.bones);
                }
            }
            Map<String, Easing> boneEase = new HashMap<>();
            if (k.has("bones")) merge(pose, bones(k.getAsJsonObject("bones"), boneEase, rig));
            for (var e : pose.entrySet()) {
                Easing ease = boneEase.getOrDefault(e.getKey(), keyEase);
                List<List<Keyed>> ch = keyed.computeIfAbsent(e.getKey(), x -> List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>()));
                for (int c = 0; c < 3; c++) {
                    float[] v = e.getValue()[c];
                    if (v != null) ch.get(c).add(new Keyed(t, v, ease));
                }
            }
            if (k.has("marker")) markers.add(new Clip.Marker(t, k.get("marker").getAsString()));
        }
        if (o.has("markers")) {
            for (JsonElement me : o.getAsJsonArray("markers")) {
                JsonObject m = me.getAsJsonObject();
                float mt = m.has("f") && !m.has("t") ? (m.get("f").getAsFloat() - refStart) * frameMs : time(m, "t", "f", frameMs, 0);
                markers.add(new Clip.Marker(mt, str(m, "label", "?")));
            }
        }
        markers.sort((a, b) -> Float.compare(a.time(), b.time()));

        Track[][] tracks = new Track[Bone.COUNT][3];
        Map<String, Track[]> named = new LinkedHashMap<>();
        boolean player = rig.equals(PLAYER_RIG);
        for (var be : keyed.entrySet()) {
            Track[] dst = player ? tracks[Bone.byName(be.getKey()).ordinal()] : named.computeIfAbsent(be.getKey(), x -> new Track[3]);
            for (int c = 0; c < 3; c++) {
                List<Keyed> list = be.getValue().get(c);
                if (list.isEmpty()) continue;
                list.sort((x, y) -> Float.compare(x.time, y.time));
                // A later key at the same time replaces an earlier one.
                List<Keyed> dedup = new ArrayList<>();
                for (Keyed kk : list) {
                    if (!dedup.isEmpty() && Math.abs(dedup.getLast().time - kk.time) < 1e-3f) dedup.set(dedup.size() - 1, kk);
                    else dedup.add(kk);
                }
                float[] times = new float[dedup.size()];
                float[][] values = new float[dedup.size()][];
                Easing[] eases = new Easing[dedup.size()];
                for (int i = 0; i < dedup.size(); i++) {
                    times[i] = dedup.get(i).time;
                    values[i] = c == Clip.ROT ? toRadians(dedup.get(i).value) : dedup.get(i).value;
                    eases[i] = dedup.get(i).ease;
                }
                dst[c] = new Track(times, values, eases);
            }
        }
        float duration = o.has("endFrame") ? (o.get("endFrame").getAsFloat() - refStart) * frameMs : time(o, "duration", "frames", frameMs, last);
        float loopStart = o.has("loopStartFrame") ? (o.get("loopStartFrame").getAsFloat() - refStart) * frameMs : time(o, "loopStart", "-", frameMs, 0);
        float loopEnd = o.has("loopEndFrame") ? (o.get("loopEndFrame").getAsFloat() - refStart) * frameMs : time(o, "loopEnd", "-", frameMs, duration);
        float iFrom = 0, iTo = -1;
        JsonArray window = o.has("interruptWindow") ? o.getAsJsonArray("interruptWindow") : null;
        if (window != null && window.size() == 2) {
            iFrom = window.get(0).getAsFloat();
            iTo = window.get(1).getAsFloat();
        } else if (o.has("interruptWindowFrames")) {
            JsonArray w = o.getAsJsonArray("interruptWindowFrames");
            iFrom = w.get(0).getAsFloat() * frameMs;
            iTo = w.get(1).getAsFloat() * frameMs;
        }
        float[] kt = new float[keyTimes.size()];
        keyTimes.sort(null);
        for (int i = 0; i < kt.length; i++) kt[i] = keyTimes.get(i);
        Clip clip = new Clip(name, group, duration, fps, bool(o, "hold", false), bool(o, "loop", false), loopStart, loopEnd,
                num(o, "blendIn", 75), num(o, "blendOut", 150), blendEase(o),
                enumOf(Priority.class, str(o, "priority", "SPECIAL")), enumOf(Layer.class, str(o, "layer", "BASE")),
                bool(o, "interruptible", true), iFrom, iTo, num(o, "speed", 1), num(o, "look", 0), num(o, "tremble", 0),
                kt, List.copyOf(markers), tracks);
        clip.stopAfter = num(o, "stopAfter", 0);
        clip.rig = rig;
        clip.named = named;
        return clip;
    }

    /**
     * Parses a {@code bones} object: per bone, [x, y, z] (a rotation) or {rot, pos, scale, ease}. Player bone names (and
     * their aliases) are checked and canonicalised; another rig's names are taken as they are.
     */
    private static Map<String, float[][]> bones(@Nullable JsonObject o, @Nullable Map<String, Easing> eases, String rig) {
        Map<String, float[][]> out = new LinkedHashMap<>();
        if (o == null) return out;
        for (var e : o.entrySet()) {
            String b = e.getKey();
            if (rig.equals(PLAYER_RIG)) {
                Bone bone = Bone.byName(e.getKey());
                if (bone == null) throw new IllegalArgumentException("unknown bone " + e.getKey());
                b = bone.id;
            }
            float[][] v = new float[3][];
            JsonElement j = e.getValue();
            if (j.isJsonArray()) {
                v[Clip.ROT] = vec(j);
            } else {
                JsonObject bo = j.getAsJsonObject();
                if (bo.has("rot")) v[Clip.ROT] = vec(bo.get("rot"));
                if (bo.has("pos")) v[Clip.POS] = vec(bo.get("pos"));
                if (bo.has("scale")) {
                    JsonElement s = bo.get("scale");
                    v[Clip.SCALE] = s.isJsonPrimitive() ? new float[]{s.getAsFloat(), s.getAsFloat(), s.getAsFloat()} : vec(s);
                }
                Easing ease = ease(bo, null);
                if (ease != null && eases != null) eases.put(b, ease);
            }
            out.put(b, v);
        }
        return out;
    }

    private static void merge(Map<String, float[][]> into, Map<String, float[][]> from) {
        for (var e : from.entrySet()) {
            float[][] dst = into.computeIfAbsent(e.getKey(), k -> new float[3][]);
            for (int c = 0; c < 3; c++) if (e.getValue()[c] != null) dst[c] = e.getValue()[c];
        }
    }

    /** The other side's bone: the player's own pairs, or a model's right_/left_ names swapped. */
    static String mirrorName(String bone, String rig) {
        if (rig.equals(PLAYER_RIG)) {
            Bone b = Bone.byName(bone);
            return b == null ? bone : b.mirror().id;
        }
        if (bone.startsWith("right")) return "left" + bone.substring(5);
        if (bone.startsWith("left")) return "right" + bone.substring(4);
        if (bone.endsWith("_right")) return bone.substring(0, bone.length() - 6) + "_left";
        if (bone.endsWith("_left")) return bone.substring(0, bone.length() - 5) + "_right";
        return bone;
    }

    static Map<String, float[][]> mirror(Map<String, float[][]> bones, String rig) {
        Map<String, float[][]> out = new LinkedHashMap<>();
        for (var e : bones.entrySet()) {
            float[][] s = e.getValue(), d = new float[3][];
            if (s[Clip.ROT] != null) d[Clip.ROT] = new float[]{s[Clip.ROT][0], -s[Clip.ROT][1], -s[Clip.ROT][2]};
            if (s[Clip.POS] != null) d[Clip.POS] = new float[]{-s[Clip.POS][0], s[Clip.POS][1], s[Clip.POS][2]};
            d[Clip.SCALE] = s[Clip.SCALE];
            out.put(mirrorName(e.getKey(), rig), d);
        }
        return out;
    }

    private static boolean isMirror(String ref) {
        return ref.endsWith("@mirror");
    }

    private static String stripMirror(String ref) {
        String s = isMirror(ref) ? ref.substring(0, ref.length() - "@mirror".length()) : ref;
        return s.startsWith("poses/") ? s.substring("poses/".length()) : s;
    }

    private static float[] vec(JsonElement e) {
        JsonArray a = e.getAsJsonArray();
        if (a.size() != 3) throw new IllegalArgumentException("expected [x, y, z], got " + a);
        return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
    }

    private static float[] toRadians(float[] deg) {
        float k = (float) (Math.PI / 180);
        return new float[]{deg[0] * k, deg[1] * k, deg[2] * k};
    }

    private static List<String> strings(JsonElement e) {
        List<String> out = new ArrayList<>();
        if (e.isJsonArray()) for (JsonElement s : e.getAsJsonArray()) out.add(s.getAsString());
        else out.add(e.getAsString());
        return out;
    }

    private static float time(JsonObject o, String msKey, String frameKey, float frameMs, float def) {
        if (o.has(msKey)) return o.get(msKey).getAsFloat();
        if (o.has(frameKey)) return o.get(frameKey).getAsFloat() * frameMs;
        return def;
    }

    private static Easing blendEase(JsonObject o) {
        if (!o.has("blendEase")) return Easing.LINEAR;
        Easing e = Easing.byName(o.get("blendEase").getAsString());
        if (e == null) throw new IllegalArgumentException("unknown blendEase " + o.get("blendEase"));
        return e;
    }

    @Nullable
    private static Easing ease(JsonObject o, @Nullable Easing def) {
        if (!o.has("ease")) return def;
        Easing e = Easing.byName(o.get("ease").getAsString());
        if (e == null) throw new IllegalArgumentException("unknown ease " + o.get("ease"));
        return e;
    }

    private static <E extends Enum<E>> E enumOf(Class<E> type, String s) {
        return Enum.valueOf(type, s.toUpperCase(Locale.ROOT));
    }

    private static String str(JsonObject o, String k, String def) {
        return o.has(k) ? o.get(k).getAsString() : def;
    }

    private static float num(JsonObject o, String k, float def) {
        return o.has(k) ? o.get(k).getAsFloat() : def;
    }

    private static boolean bool(JsonObject o, String k, boolean def) {
        return o.has(k) ? o.get(k).getAsBoolean() : def;
    }
}
