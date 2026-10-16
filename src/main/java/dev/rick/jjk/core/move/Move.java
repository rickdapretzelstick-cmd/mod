package dev.rick.jjk.core.move;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A move made in the move editor (tools/move_editor): its animation, and a timeline of events -- hitboxes, effects,
 * sounds, camera, motion, statuses on the user, grabs and logic -- run by {@link MoveAbility}. Times are milliseconds in
 * the file (the editor's), ticks here (50 ms each). Offsets and velocities are (right, up, forward) from the user's
 * facing, in blocks and blocks/tick. The format is documented in tools/move_editor/FORMAT.md.
 */
public record Move(String id, String name, int ticks, float cost, int cooldown, boolean technique, boolean uninterruptible,
                   float movement, boolean airOk, String clipJson, boolean holds, String useClip, List<PlayClip> clips, List<Hitbox> hitboxes, List<Effect> effects,
                   List<Sound> sounds, List<Camera> cameras, List<Motion> motions, List<SelfStatus> statuses, List<Grab> grabs,
                   List<Logic> logic) {

    public static final int FORMAT_VERSION = 1;

    public record Hitbox(String id, int start, int end, String shape, Vec3 offset, Vec3 size, double radius, boolean aim,
                         float damage, Vec3 knockback, int hitstun, String status, int statusTicks, List<String> tags,
                         int rehit, String impactFx, float impactScale, boolean blockable, boolean grab) {}

    public record Effect(int t, String fx, Vec3 offset, Vec3 dir, float scale, boolean onTarget) {}

    /** Plays one of the mod's existing clips at t (on top of, or instead of, the move's own). */
    public record PlayClip(int t, String clip, float speed) {}

    public record Sound(int t, String sound, float volume, float pitch) {}

    public record Camera(int t, String kind, float intensity, int duration, double radius, int color) {}

    public record Motion(int t, String mode, Vec3 velocity, int duration) {}

    public record SelfStatus(int start, int end, String status) {}

    public record Grab(int start, int end, String from, Vec3 hold, Vec3 release, float releaseDamage, String releaseFx) {}

    /** At t (or for every tick of [start, end] with a press), if the condition holds, do the action. */
    public record Logic(int start, int end, String when, String from, String action, String move) {}

    static int ticks(double ms) {
        return (int) Math.round(ms / 50.0);
    }

    public static Move parse(JsonObject o) {
        String id = str(o, "id", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        if (id.isEmpty()) throw new IllegalArgumentException("a move needs an id");
        int ticks = Math.max(1, ticks(num(o, "duration", 1000)));
        JsonObject anim = o.has("animation") && o.get("animation").isJsonObject() ? o.getAsJsonObject("animation").deepCopy() : new JsonObject();
        anim.addProperty("name", clipName(id));
        if (!anim.has("duration")) anim.addProperty("duration", num(o, "duration", 1000));
        if (!anim.has("keys")) anim.add("keys", new JsonArray());
        boolean holds = anim.has("hold") && anim.get("hold").getAsBoolean();
        List<Hitbox> hb = new ArrayList<>();
        List<Effect> fx = new ArrayList<>();
        List<Sound> snd = new ArrayList<>();
        List<Camera> cam = new ArrayList<>();
        List<Motion> mot = new ArrayList<>();
        List<SelfStatus> st = new ArrayList<>();
        List<Grab> gr = new ArrayList<>();
        List<Logic> lg = new ArrayList<>();
        List<PlayClip> pc = new ArrayList<>();
        JsonArray events = o.has("events") ? o.getAsJsonArray("events") : new JsonArray();
        int n = 0;
        for (JsonElement el : events) {
            JsonObject e = el.getAsJsonObject();
            n++;
            int t = ticks(num(e, "t", num(e, "start", 0)));
            int start = ticks(num(e, "start", num(e, "t", 0)));
            int end = Math.max(start, ticks(num(e, "end", num(e, "start", num(e, "t", 0)))));
            switch (str(e, "type", "")) {
                case "hitbox" -> {
                    JsonObject kb = e.has("knockback") && e.get("knockback").isJsonObject() ? e.getAsJsonObject("knockback") : new JsonObject();
                    List<String> tags = new ArrayList<>();
                    if (e.has("tags")) for (JsonElement x : e.getAsJsonArray("tags")) tags.add(x.getAsString());
                    hb.add(new Hitbox(str(e, "id", "hitbox" + n), start, end, str(e, "shape", "box"), vec(e, "offset", new Vec3(0, 1, 1)),
                            vec(e, "size", new Vec3(1.2, 1.4, 1.6)), num(e, "radius", 1), bool(e, "aim", false), (float) num(e, "damage", 5),
                            new Vec3(num(kb, "right", 0), num(kb, "up", 0.15), num(kb, "forward", 0.5)), (int) num(e, "hitstun", 10),
                            str(e, "status", ""), (int) num(e, "statusTicks", 0), tags, (int) num(e, "rehit", 0),
                            str(e, "impactFx", "hit_light"), (float) num(e, "impactScale", 1), bool(e, "blockable", true), bool(e, "grab", false)));
                }
                case "effect" -> fx.add(new Effect(t, str(e, "fx", "swing"), vec(e, "offset", new Vec3(0, 1, 1)), vec(e, "dir", new Vec3(0, 0, 1)),
                        (float) num(e, "scale", 1), bool(e, "onTarget", false)));
                case "anim" -> pc.add(new PlayClip(t, str(e, "clip", ""), (float) num(e, "speed", 1)));
                case "sound" -> snd.add(new Sound(t, str(e, "sound", ""), (float) num(e, "volume", 1), (float) num(e, "pitch", 1)));
                case "camera" -> cam.add(new Camera(t, str(e, "kind", "shake"), (float) num(e, "intensity", 0.5), (int) num(e, "duration", 8),
                        num(e, "radius", 16), (int) Long.parseLong(str(e, "color", "ffffffff").replace("#", ""), 16)));
                case "motion" -> mot.add(new Motion(t, str(e, "mode", "add"), vec(e, "velocity", Vec3.ZERO),
                        Math.max(1, ticks(num(e, "end", num(e, "t", 0))) - t + 1)));
                case "status" -> st.add(new SelfStatus(start, end, str(e, "status", "melee_armor")));
                case "grab" -> gr.add(new Grab(start, end, str(e, "from", ""), vec(e, "hold", new Vec3(0, 0.2, 1.2)), vec(e, "release", new Vec3(0, 0.3, 0.8)),
                        (float) num(e, "releaseDamage", 0), str(e, "releaseFx", "")));
                case "logic" -> lg.add(new Logic(start, end, str(e, "when", "miss"), str(e, "from", ""), str(e, "action", "end"), str(e, "move", "")));
                default -> {}
            }
        }
        return new Move(id, str(o, "name", id), ticks, (float) num(o, "cost", 0), ticks(num(o, "cooldown", 0) * 1000), bool(o, "technique", false),
                bool(o, "uninterruptible", false), (float) num(o, "movement", 0.4), bool(o, "air", true), anim.toString(), holds, str(anim, "use", ""), pc,
                hb, fx, snd, cam, mot, st, gr, lg);
    }

    /** The name the move's own clip is registered under on clients. */
    public static String clipName(String id) {
        return "move_" + id;
    }

    private static String str(JsonObject o, String k, String def) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : def;
    }

    private static double num(JsonObject o, String k, double def) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsDouble() : def;
    }

    private static boolean bool(JsonObject o, String k, boolean def) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsBoolean() : def;
    }

    private static Vec3 vec(JsonObject o, String k, Vec3 def) {
        if (!o.has(k) || !o.get(k).isJsonArray()) return def;
        JsonArray a = o.getAsJsonArray(k);
        return new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
    }
}
