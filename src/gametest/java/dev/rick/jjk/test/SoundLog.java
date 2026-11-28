package dev.rick.jjk.test;

/**
 * Every sound the game plays while a recording runs (see {@code SoundLogMixin}): one line per sound, {@code game_time id
 * x y z relative volume pitch}, so {@code tools/make_showcase.py} can mix the real sound at its exact frame.
 */
public final class SoundLog {
    public static volatile boolean recording;
    public static final StringBuilder LINES = new StringBuilder();

    private SoundLog() {}

    public static synchronized void add(String line) {
        LINES.append(line).append('\n');
    }

    public static synchronized String take() {
        String s = LINES.toString();
        LINES.setLength(0);
        return s;
    }
}
