package dev.rick.jjk.client.hud;

import java.util.HashMap;
import java.util.Map;

/**
 * A character's HUD identity. The HUD framework draws the same pieces for everyone (the CE bar, the ability column, the
 * awakened-state bar) and asks the current character's theme for colours and wording, so each character keeps their
 * own visual language without the framework knowing anything about them. Register a theme per character id.
 */
public record CharacterTheme(
        /** CE fill, top and bottom of the gradient, and the rim of the bar. */
        int ceTop, int ceBottom, int ceRim,
        /** The awakened-state name ("AWAKENED", "JACKPOT") and what the bar says while filling / when full. */
        String awakenedName, String meterName, String readyText,
        /** Meter colours while filling, and the awakened timer's colours. */
        int meterFrom, int meterTo, int timerFrom, int timerTo,
        /** Whether the awakened timer is presented big (a major HUD element) rather than as the ordinary bar. */
        boolean bigTimer,
        /** Accent for frames and highlights. */
        int accent) {

    private static final Map<String, CharacterTheme> THEMES = new HashMap<>();

    /** Gojo: cold blues and white, the six eyes. */
    public static final CharacterTheme GOJO = register("gojo", new CharacterTheme(0xFFA8ECFF, 0xFF2455E0, 0xFF2A3A55,
            "AWAKENED", "AWAKENING", "AWAKENING READY", 0xFF8A5CFF, 0xFF3E7BFF, 0xFFFFFFFF, 0xFF7FD4FF, false, 0xFF7FD4FF));
    /** Hakari: casino black, hot pink and gold; Jackpot runs green. */
    public static final CharacterTheme HAKARI = register("hakari", new CharacterTheme(0xFFFFB0DC, 0xFFD01A78, 0xFF55203C,
            "JACKPOT", "GAMBLE", "IDLE DEATH GAMBLE READY", 0xFFFF3FA0, 0xFFF0C040, 0xFFB8FFD8, 0xFF22D47A, true, 0xFFFF3FA0));

    /** Yuji: cursed-energy cyan on black; the King of Curses runs blood red. */
    public static final CharacterTheme YUJI = register("yuji", new CharacterTheme(0xFFA8F4FF, 0xFF1C9AD8, 0xFF20343E,
            "KING OF CURSES", "AWAKENING", "KING OF CURSES READY", 0xFF5CE6FF, 0xFFE01020, 0xFFFF6A6A, 0xFFB00818, false, 0xFFE01020));

    public static CharacterTheme register(String characterId, CharacterTheme theme) {
        THEMES.put(characterId, theme);
        return theme;
    }

    public static CharacterTheme of(String characterId) {
        return THEMES.getOrDefault(characterId, GOJO);
    }
}
