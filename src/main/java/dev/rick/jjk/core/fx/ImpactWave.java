package dev.rick.jjk.core.fx;

import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.combat.HitResult;
import dev.rick.jjk.core.combat.Knockback;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * The impact-wave system: one description of how a blow's shock reads in the world, played the same on every client.
 *
 * <p>A {@link Profile} is a named, immutable set of parameters (its {@link Tier}, the colours of its {@link Theme},
 * the way its pressure travels, how it meets the ground, the energy motif it carries, radii, speeds, counts, the camera's
 * share). Moves own profiles, not effect code: a hit is given one with {@link Hit.Builder#wave(String)} and
 * {@code HitResolver} fires it at the resolved contact point along the resolved direction, only for blows that really
 * connect (a blow that is blocked shows the stopped version); moves that strike the world rather than a body
 * ({@link #play}) fire one themselves. Everything a client draws comes from the profile and the event's position,
 * direction and strength, so no two clients disagree and no move draws its shock twice. The drawing is
 * {@code client/fx/ImpactWaveFx}; nothing here changes a block.
 *
 * <p>The profiles themselves live in {@link ImpactWaves}.
 */
public final class ImpactWave {
    /** Effect ids on the wire: {@code wave:<profile>}, with {@code #b} appended for a blow that was stopped by a guard. */
    public static final String PREFIX = "wave:";
    public static final String BLOCKED = "#b";

    private ImpactWave() {}

    /** How much is in the blow: sets every default the profile does not override. */
    public enum Tier {
        /** A contact flash, a small pressure ripple, a few sparks. Jabs, flurry hits. */
        LIGHT,
        /** A visible expanding ring, a dust burst, a little grit. Finishers, kicks, launches. */
        MEDIUM,
        /** A real shock: layered rings, the ground answering, debris, the camera feeling it. Slams, heavy blows. */
        HEAVY,
        /** A technique's blow: the shock plus the energy it carries, the camera close. Special moves. */
        SPECIAL,
        /** Cinematic scale: layered shockwaves, a column of dust and debris, a long roll of the ground. Ultimates. */
        ULTIMATE
    }

    /** Where the pressure goes. */
    public enum Direction {
        /** Out from the point in every direction (a blunt blow). */
        RADIAL,
        /** Driven along the line of the blow: a pressure burst leaving the contact behind it (straight punches, dashes). */
        ALONG,
        /** Into the ground: the shock spreads out along the floor (slams, stomps). */
        DOWN,
        /** Up: a column of pressure rising off the point (uppercuts, launchers, things rising out of the ground). */
        UP,
        /** Across: a cut or a swing sweeping through the point sideways to the blow (blades, hooks, kicks). */
        SWEEP
    }

    /** Whether the floor takes part. */
    public enum Ground {
        /** The floor answers only when the contact is within reach of it. */
        AUTO,
        /** Always drawn on the floor below the contact. */
        ALWAYS,
        /** Never. */
        NEVER
    }

    /** The energy motif laid over the shock: a technique's signature, or nothing for plain force. */
    public enum Signature {
        NONE,
        /** Speed lines driven through along the blow: compressed air (Yuji's straights). */
        SPEEDLINES,
        /** Energy arcs thrown off the contact in the theme's colour (cursed energy snapping). */
        BOLTS,
        /** A crescent across the contact: the path of a blade or a shin. */
        CRESCENT,
        /** Jagged spikes bursting out of the point: a discharge going off. */
        SPIKES,
        /** Light winding in to the point, then a crisp shell leaving it: space itself giving (Limitless). */
        SPIRAL,
        /** Heavy dark smoke rolling off the point: mass, not light (Rika, Crushing Blow). */
        SMOKE,
        /** Electric arcs and steam: Cursed Energy Discharge (Ryu). */
        DISCHARGE,
        /** Star flares and chrome glints: luck going off (Hakari). */
        FLARE,
        /** Thin black cuts with white edges crossing the point (Sukuna's slashes). */
        SLASH
    }

    /** The colours a character's shocks are drawn in: the flash's core, the rings, the sparks, the dust. */
    public record Theme(String name, float[] core, float[] energy, float[] accent, float[] dust) {
        public static final float[] WHITE = {1f, 1f, 1f}, GREY = {0.6f, 0.6f, 0.65f}, PALE = {0.75f, 0.9f, 1f};
        public static final Theme NEUTRAL = new Theme("neutral", WHITE, PALE, WHITE, GREY);
        /** Yuji: white contact, the cyan of his cursed energy, grey dust. */
        public static final Theme YUJI = new Theme("yuji", WHITE, new float[] {0.8f, 0.97f, 1f}, new float[] {0.35f, 0.9f, 1f}, GREY);
        /** Sukuna: white and black, the red of his slashes' edges, dark dust. */
        public static final Theme SUKUNA = new Theme("sukuna", WHITE, WHITE, new float[] {1f, 0.06f, 0.1f}, new float[] {0.12f, 0.1f, 0.12f});
        /** Gojo: white-blue, ice, nothing dirty (Limitless never kicks up dust). */
        public static final Theme GOJO = new Theme("gojo", WHITE, new float[] {0.7f, 0.86f, 1f}, new float[] {0.45f, 0.7f, 1f}, new float[] {0.7f, 0.74f, 0.8f});
        /** Hakari: jade and pink, chrome glints, street dust. */
        public static final Theme HAKARI = new Theme("hakari", WHITE, new float[] {0.55f, 1f, 0.75f}, new float[] {1f, 0.25f, 0.63f}, GREY);
        /** Yuta: white steel, the pink of his and Rika's energy, dark smoke. */
        public static final Theme YUTA = new Theme("yuta", WHITE, new float[] {1f, 0.8f, 1f}, new float[] {0.97f, 0.42f, 1f}, new float[] {0.2f, 0.16f, 0.22f});
        /** The steel arm: white, gold sparks, grey. */
        public static final Theme STEEL = new Theme("steel", WHITE, WHITE, new float[] {1f, 0.8f, 0.35f}, GREY);
        /** Rika: her pink over black, heavy dark dust. */
        public static final Theme RIKA = new Theme("rika", new float[] {1f, 0.8f, 1f}, new float[] {0.8f, 0.1f, 0.85f}, new float[] {0.97f, 0.42f, 1f}, new float[] {0.1f, 0.06f, 0.1f});
        /** Ryu: his blue, white-hot, steam. */
        public static final Theme RYU = new Theme("ryu", WHITE, new float[] {0.8f, 0.93f, 1f}, new float[] {0.32f, 0.62f, 1f}, new float[] {0.72f, 0.72f, 0.76f});
        /** Decadence: the same blue under a gold heat. */
        public static final Theme DECADENCE = new Theme("decadence", new float[] {1f, 0.95f, 0.8f}, new float[] {1f, 0.78f, 0.35f}, new float[] {0.32f, 0.62f, 1f}, new float[] {0.72f, 0.72f, 0.76f});
    }

    /**
     * Everything a wave needs. Distances in blocks, times in ticks, counts before particle quality scales them.
     *
     * @param radius    the main ring's full radius
     * @param life      ticks the main ring takes to get there (the expansion speed is radius / life)
     * @param thickness the main ring's band (0: in proportion to its size)
     * @param rings     rings in the layered shock (1-3): each after the first is wider, thinner and a beat later
     * @param flash     size of the contact flash
     * @param lens      size of the shock shell (air displacement), 0 for none
     * @param ink       strength of the dark pressure blot behind the flash, 0 for none
     * @param particles sparks thrown off the contact
     * @param velocity  their speed
     * @param dust      the dust burst (1 = the tier's)
     * @param debris    the grit thrown up (1 = the tier's)
     * @param lift      a column's height (UP) or how far the pressure travels (ALONG)
     * @param delay     ticks before the follow-through (a trailing ring, settling dust), 0 for none
     * @param shake     camera shake at the centre, fading to the edge of shakeRadius
     * @param fov       the FOV kick for the fighters
     * @param victim    the screen feedback of the one hit (0-1)
     * @param once      only one of these per attacker per tick (a slam that hits several bodies shocks the floor once)
     */
    public record Profile(String name, Tier tier, Theme theme, Direction direction, Ground ground, Signature signature,
                          float radius, int life, float thickness, int rings, float flash, float lens, float ink, int particles, float velocity,
                          float dust, float debris, float lift, int delay, float shake, float shakeRadius, float fov, float victim, boolean once) {
        public Builder toBuilder() {
            return new Builder(name, tier, theme).direction(direction).ground(ground).signature(signature).radius(radius).life(life)
                    .thickness(thickness).rings(rings).flash(flash).lens(lens).ink(ink).particles(particles).velocity(velocity).dust(dust)
                    .debris(debris).lift(lift).delay(delay).shake(shake, shakeRadius).fov(fov).victim(victim).once(once);
        }
    }

    /** Builds a profile from its tier's defaults. */
    public static final class Builder {
        private final String name;
        private final Tier tier;
        private final Theme theme;
        private Direction direction = Direction.RADIAL;
        private Ground ground = Ground.AUTO;
        private Signature signature = Signature.NONE;
        private float radius, thickness, flash, lens, ink, velocity, dust, debris, lift, shake, shakeRadius, fov, victim;
        private int life, rings, particles, delay;
        private boolean once;

        public Builder(String name, Tier tier, Theme theme) {
            this.name = name;
            this.tier = tier;
            this.theme = theme;
            switch (tier) {
                case LIGHT -> set(1.0f, 4, 0f, 1, 0.9f, 0f, 0f, 5, 0.35f, 0f, 0f, 0.8f, 0, 0f, 0f, 0f, 0.25f);
                case MEDIUM -> set(1.9f, 6, 0f, 1, 1.4f, 0f, 0f, 9, 0.5f, 1f, 0.6f, 1.4f, 0, 0.25f, 8f, 0.015f, 0.5f);
                case HEAVY -> set(2.8f, 7, 0f, 2, 1.9f, 1.6f, 0.5f, 14, 0.65f, 1.5f, 1.2f, 2.2f, 3, 0.5f, 14f, 0.025f, 0.6f);
                case SPECIAL -> set(3.4f, 8, 0f, 2, 2.3f, 2.2f, 0.7f, 20, 0.75f, 1.8f, 1.5f, 3.0f, 4, 0.7f, 20f, 0.03f, 0.7f);
                case ULTIMATE -> set(5.5f, 11, 0f, 3, 3.2f, 3.6f, 0.9f, 30, 0.9f, 2.6f, 2.2f, 4.5f, 5, 1.0f, 32f, 0.04f, 0.8f);
            }
        }

        private void set(float radius, int life, float thickness, int rings, float flash, float lens, float ink, int particles, float velocity,
                         float dust, float debris, float lift, int delay, float shake, float shakeRadius, float fov, float victim) {
            this.radius = radius;
            this.life = life;
            this.thickness = thickness;
            this.rings = rings;
            this.flash = flash;
            this.lens = lens;
            this.ink = ink;
            this.particles = particles;
            this.velocity = velocity;
            this.dust = dust;
            this.debris = debris;
            this.lift = lift;
            this.delay = delay;
            this.shake = shake;
            this.shakeRadius = shakeRadius;
            this.fov = fov;
            this.victim = victim;
        }

        public Builder direction(Direction d) { direction = d; return this; }
        public Builder ground(Ground g) { ground = g; return this; }
        public Builder signature(Signature s) { signature = s; return this; }
        public Builder radius(float r) { radius = r; return this; }
        public Builder life(int t) { life = Math.max(1, t); return this; }
        public Builder thickness(float t) { thickness = t; return this; }
        public Builder rings(int n) { rings = Math.max(1, Math.min(3, n)); return this; }
        public Builder flash(float f) { flash = f; return this; }
        public Builder lens(float l) { lens = l; return this; }
        public Builder ink(float i) { ink = i; return this; }
        public Builder particles(int n) { particles = Math.max(0, n); return this; }
        public Builder velocity(float v) { velocity = v; return this; }
        public Builder dust(float d) { dust = d; return this; }
        public Builder debris(float d) { debris = d; return this; }
        public Builder lift(float l) { lift = l; return this; }
        public Builder delay(int t) { delay = Math.max(0, t); return this; }
        public Builder shake(float strength, float radius) { shake = strength; shakeRadius = radius; return this; }
        public Builder fov(float f) { fov = f; return this; }
        public Builder victim(float v) { victim = v; return this; }
        public Builder once(boolean o) { once = o; return this; }
        /** No dust, no debris, no ground: a clean blow (Limitless, a blade, a slash). */
        public Builder clean() { dust = 0; debris = 0; ground = Ground.NEVER; return this; }

        public Profile build() {
            return new Profile(name, tier, theme, direction, ground, signature, radius, life, thickness, rings, flash, lens, ink, particles,
                    velocity, dust, debris, lift, delay, shake, shakeRadius, fov, victim, once);
        }

        /** Builds and registers it. */
        public Profile register() {
            return ImpactWave.register(build());
        }
    }

    // --- The registry ---

    private static final Map<String, Profile> PROFILES = new TreeMap<>();

    public static Profile register(Profile p) {
        PROFILES.put(p.name(), p);
        return p;
    }

    /** The profile of that name, or null. The library is loaded on first use. */
    @Nullable
    public static Profile get(String name) {
        ImpactWaves.init();
        return PROFILES.get(name);
    }

    /** Every registered profile, by name. */
    public static Map<String, Profile> all() {
        ImpactWaves.init();
        return Collections.unmodifiableMap(PROFILES);
    }

    /** Parses an effect id ({@code wave:<name>} or {@code wave:<name>#b}); null if it is not a wave. */
    @Nullable
    public static String profileName(String effectId) {
        if (!effectId.startsWith(PREFIX)) return null;
        String n = effectId.substring(PREFIX.length());
        return n.endsWith(BLOCKED) ? n.substring(0, n.length() - BLOCKED.length()) : n;
    }

    public static boolean isBlocked(String effectId) {
        return effectId.endsWith(BLOCKED);
    }

    // --- Firing, from the server ---

    /** (attacker id, profile) -> the tick it last fired, for profiles that fire once per tick. */
    private static final Map<String, Long> RECENT = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
            return size() > 256;
        }
    };
    /** Waves fired so far (tests, debugging). */
    public static int fired;

    /**
     * Plays a wave in the world: at {@code at}, its pressure along {@code dir}, {@code scale} times the profile's
     * strength, {@code entityId} the body it hit (its player gets the victim feedback) or -1.
     */
    public static boolean play(ServerLevel level, String profile, Vec3 at, Vec3 dir, float scale, int entityId) {
        return play(level, profile, at, dir, scale, entityId, -1, false);
    }

    /** The same from a move with no body under it (the floor, the air). */
    public static boolean play(ServerLevel level, String profile, Vec3 at, Vec3 dir, float scale) {
        return play(level, profile, at, dir, scale, -1, -1, false);
    }

    private static boolean play(ServerLevel level, String profile, Vec3 at, Vec3 dir, float scale, int entityId, int attackerId, boolean blocked) {
        Profile p = get(profile);
        if (p == null) {
            dev.rick.jjk.JJK.LOGGER.warn("unknown impact wave '{}'", profile);
            return false;
        }
        if (p.once() && attackerId >= 0) {
            String key = attackerId + "|" + profile;
            long now = level.getGameTime();
            Long last = RECENT.get(key);
            if (last != null && last == now) return false;
            RECENT.put(key, now);
        }
        fired++;
        Fx.play(level, PREFIX + profile + (blocked ? BLOCKED : ""), at, dir, Math.max(0.1f, scale), entityId);
        return true;
    }

    /**
     * The wave a resolved hit carries, fired from the contact. The direction is the way the target was sent (a set
     * knockback), else the line from the blow's origin to the contact; a blow that was blocked plays the stopped version.
     */
    public static void fromHit(ServerLevel level, Hit hit, LivingEntity target, Vec3 hitPoint, HitResult.Outcome outcome) {
        if (hit.wave == null) return;
        if (!outcome.contacted() || outcome == HitResult.Outcome.NEGATED || outcome == HitResult.Outcome.PARRIED) return;
        Vec3 dir = direction(hit, hitPoint);
        play(level, hit.wave, hitPoint, dir, hit.impactScale, target.getId(), hit.attacker.getId(), outcome == HitResult.Outcome.BLOCKED);
    }

    /** The direction a blow's pressure travels: where it sends the target, else where it came from. */
    public static Vec3 direction(Hit hit, Vec3 hitPoint) {
        Knockback kb = hit.knockback;
        if (kb != null && kb.type() == Knockback.Type.SET && kb.vector().lengthSqr() > 0.01) return kb.vector().normalize();
        Vec3 d = hitPoint.subtract(hit.origin);
        if (d.lengthSqr() > 1e-6) return d.normalize();
        return hit.attacker.getLookAngle();
    }

    /** Forgets the once-per-tick memory (tests). */
    public static void reset() {
        RECENT.clear();
        fired = 0;
    }
}
