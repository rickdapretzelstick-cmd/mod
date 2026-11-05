package dev.rick.jjk.progression.training;

import net.minecraft.network.RegistryFriendlyByteBuf;

/**
 * One skill check in a training schedule, in milliseconds from the start of the timed challenge. Its cue (the arc
 * appearing, the needle's new speed) comes at {@code cueMs}; the needle is centred on the arc at {@code atMs}; the GOOD
 * window is {@code goodMs} wide around it, the PERFECT window {@code perfectMs}. A hold check is pressed in that window
 * and released in a second one centred on {@code releaseMs}. {@code periodMs} is how long the gauge's needle takes to
 * go once round from the cue on; {@code mod} marks what the check announces (a disturbance, the aligned finale...).
 */
public record TrainingCheck(int id, int gauge, boolean hold, int cueMs, int atMs, int releaseMs, int goodMs, int perfectMs, int periodMs, int mod) {
    /** What a check announces. FAST/SLOW are disturbances to its approach speed (announced at the cue). */
    public static final int PLAIN = 0, FAST = 1, SLOW = 2, ALIGNED = 3, FINALE = 4;
    /** Results. HOLDING: the press of a hold check landed; the release decides it. */
    public static final int PERFECT = 0, GOOD = 1, MISS = 2, HOLDING = 3;

    public int windowStart() {
        return atMs - goodMs / 2;
    }

    public int windowEnd() {
        return atMs + (goodMs - goodMs / 2);
    }

    public int releaseStart() {
        return releaseMs - goodMs / 2;
    }

    public int releaseEnd() {
        return releaseMs + (goodMs - goodMs / 2);
    }

    /** When the check is over at the latest (its window, or its release window). */
    public int endMs() {
        return hold ? releaseEnd() : windowEnd();
    }

    /** PERFECT, GOOD or MISS for an input at {@code t} against a window centred on {@code centre}. */
    public int judge(int centre, double t) {
        double d = Math.abs(t - centre);
        if (d <= perfectMs / 2.0) return PERFECT;
        if (d <= goodMs / 2.0) return GOOD;
        return MISS;
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(id);
        buf.writeByte(gauge);
        buf.writeBoolean(hold);
        buf.writeVarInt(cueMs);
        buf.writeVarInt(atMs);
        buf.writeVarInt(releaseMs);
        buf.writeVarInt(goodMs);
        buf.writeVarInt(perfectMs);
        buf.writeVarInt(periodMs);
        buf.writeByte(mod);
    }

    public static TrainingCheck read(RegistryFriendlyByteBuf buf) {
        return new TrainingCheck(buf.readVarInt(), buf.readByte(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readByte());
    }
}
