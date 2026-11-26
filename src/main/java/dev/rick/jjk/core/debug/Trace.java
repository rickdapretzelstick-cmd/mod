package dev.rick.jjk.core.debug;

import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * A tap on what the server does to fighters, for reading a fight as text instead of watching it: casts beginning,
 * changing phase and ending, every clip started, hits landing. Nothing listens in play ({@link #sink} is null and each
 * call is one read); the animation trace ({@code client/debug/AnimTrace}) and the move timing audit plug in here.
 */
public final class Trace {
    /** Receives the events. {@code gameTime} is the server tick. */
    public interface Sink {
        void event(long gameTime, int entityId, String kind, String detail);
    }

    @Nullable public static volatile Sink sink;

    private Trace() {}

    public static boolean on() {
        return sink != null;
    }

    public static void event(@Nullable Entity e, String kind, String detail) {
        Sink s = sink;
        if (s != null && e != null) s.event(e.level().getGameTime(), e.getId(), kind, detail);
    }
}
