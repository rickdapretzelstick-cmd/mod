package dev.rick.jjk.client.render;

import dev.rick.jjk.yuta.TrueLoveBeamProfile;
import net.minecraft.world.phys.Vec3;

/**
 * A sustained beam as a client sees it: drawn from the server's own numbers (the shape it hits with), keyed by its caster
 * so the server can cut it short, hold it (a clash) or let it punch through, and told every few ticks where it is
 * striking. Its age counts from the first frame it is drawn (the packet's own tick can be a couple of ticks stale).
 */
public final class ClientBeam {
    public final Vec3 origin;
    public Vec3 dir;
    public final double length;
    public final double half;
    public final int grow;
    public int collapse;
    /** Ticks it holds at full size (raised while a clash holds it, or when it wins one). */
    public int hold;
    public final long start;
    public final boolean quick;
    /** A clash has it: it doesn't run out until released. */
    public boolean held;
    /** Where it strikes (updated by the server's pulses). */
    public Vec3 strike;
    /** When the last surge set off down it (game ticks). */
    public long surgeAt = -1000;
    private float seen = Float.NaN;

    public ClientBeam(Vec3 origin, Vec3 dir, double length, double half, int grow, int collapse, int hold, long start, boolean quick) {
        this.origin = origin;
        this.dir = dir.normalize();
        this.length = length;
        this.half = half;
        this.grow = grow;
        this.collapse = collapse;
        this.hold = hold;
        this.start = start;
        this.quick = quick;
        this.strike = origin.add(this.dir.scale(length));
    }

    /** Its age in ticks at {@code time}; {@code drawing} marks the first drawn frame. */
    public float age(float time, boolean drawing) {
        if (Float.isNaN(seen)) {
            if (!drawing) return 0;
            seen = time;
        }
        return time - Math.max(start, Math.min(seen, start + 4));
    }

    /** How long it is drawn in all: held, it never runs out. */
    public int life() {
        return held ? Integer.MAX_VALUE / 2 : hold + collapse;
    }

    public double front(float t) {
        return TrueLoveBeamProfile.front(length, grow, t);
    }

    public float scale(float t) {
        return held ? (t < 0 ? 0 : Math.min(1f, 0.6f + 0.2f * t)) : TrueLoveBeamProfile.scale(t, hold, collapse);
    }

    /** Collapses it over the next few ticks instead of holding. */
    public void stop(long now) {
        held = false;
        int at = (int) age(now, false);
        collapse = Math.min(4, collapse);
        hold = Math.max(0, Math.min(hold, at));
    }
}
