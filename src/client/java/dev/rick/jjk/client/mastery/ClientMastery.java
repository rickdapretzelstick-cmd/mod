package dev.rick.jjk.client.mastery;

import dev.rick.jjk.core.net.MasterySyncPayload;
import dev.rick.jjk.progression.mastery.MasteryData;

/** The local player's Mastery as the server last sent it (the screen reads it; the server decides everything). */
public final class ClientMastery {
    private static MasteryData data = MasteryData.EMPTY;
    private static String kit = "";
    private static boolean gated;
    private static int version;

    private ClientMastery() {}

    public static void apply(MasterySyncPayload p) {
        data = p.data();
        kit = p.kit();
        gated = p.gated();
        version++;
    }

    public static MasteryData data() {
        return data;
    }

    /** The kit this player legitimately owns ("" for none): the only technique tree they can develop. */
    public static String kit() {
        return kit;
    }

    public static boolean gated() {
        return gated;
    }

    /** Bumped on every sync, so an open screen knows to refresh. */
    public static int version() {
        return version;
    }

    public static void reset() {
        data = MasteryData.EMPTY;
        kit = "";
        gated = false;
        version++;
    }
}
