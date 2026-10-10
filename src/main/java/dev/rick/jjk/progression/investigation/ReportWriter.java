package dev.rick.jjk.progression.investigation;

import dev.rick.jjk.progression.grade.CurseGrade;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Writes the village's account of an incident: local news, never a quest. The cause is never named, only what people
 * saw; the place is a direction and a rough distance from the village, never coordinates; and how bad it sounds (the
 * closing line, the urgency) follows the grade of what is really behind it.
 */
public final class ReportWriter {
    private static final String[] NAMES = {"Old Maren", "Tobias Reed", "Ilse", "Corwin Hale", "Ada Finch", "Bram", "Wren Ashby", "Jory",
            "Sela Moor", "Edda", "Penrose", "Lio Varga", "Halvard", "Mina Coyle", "Oswin", "Rhea Talbot"};
    private static final String[] ANIMALS = {"sheep", "cows", "goats", "pigs"};
    /** What each grade sounds like, closing the report (Grade 4 is gossip; Special grade is panic). */
    private static final String[] CLOSING = {
            "The elder says it is probably nothing.",
            "Nobody walks that way alone any more.",
            "Two families have already packed up and gone to relatives.",
            "The bell has been rung every night since, and nobody sleeps.",
            "The village is emptying. Those who stay keep their doors barred and their lamps lit."};

    private ReportWriter() {}

    public record Written(String headline, String body) {}

    public static Written write(IncidentTemplate t, CurseGrade grade, BlockPos village, BlockPos site, String landmark, RandomSource r) {
        IncidentTemplate.Report rep = t.reports().get(r.nextInt(t.reports().size()));
        String name = NAMES[r.nextInt(NAMES.length)];
        int pr = r.nextInt(3);
        String they = pr == 0 ? "he" : pr == 1 ? "she" : "they";
        String them = pr == 0 ? "him" : pr == 1 ? "her" : "them";
        String their = pr == 0 ? "his" : pr == 1 ? "her" : "their";
        String body = rep.body() + " " + CLOSING[Mth.clamp(grade.severity, 0, CLOSING.length - 1)];
        return new Written(fill(rep.headline(), name, they, them, their, village, site, landmark, r),
                fill(body, name, they, them, their, village, site, landmark, r));
    }

    private static String fill(String s, String name, String they, String them, String their, BlockPos village, BlockPos site, String landmark, RandomSource r) {
        String dir = direction(village, site);
        String out = s.replace("{name}", name).replace("{they}", they).replace("{them}", them).replace("{their}", their)
                .replace("{dir_cap}", Character.toUpperCase(dir.charAt(0)) + dir.substring(1)).replace("{dir}", dir)
                .replace("{dist}", distance(village, site))
                .replace("{landmark}", landmark).replace("{animal}", ANIMALS[Math.floorMod(name.hashCode(), ANIMALS.length)]);
        return capitalise(out);
    }

    /** A pronoun that opens a sentence ("{they} saw...") starts with a capital, like the rest. */
    static String capitalise(String s) {
        StringBuilder b = new StringBuilder(s);
        boolean start = true;
        for (int i = 0; i < b.length(); i++) {
            char c = b.charAt(i);
            if (start && Character.isLetter(c)) {
                b.setCharAt(i, Character.toUpperCase(c));
                start = false;
            } else if (c == '.' || c == '?' || c == '!') {
                start = i + 1 < b.length() && b.charAt(i + 1) == ' ';
            } else if (c != ' ' && c != '"') {
                start = false;
            }
        }
        return b.toString();
    }

    /** One of eight compass directions from the village to the place (north is -Z). */
    public static String direction(BlockPos from, BlockPos to) {
        double a = Math.toDegrees(Math.atan2(to.getX() - from.getX(), -(to.getZ() - from.getZ())));
        int i = Math.floorMod((int) Math.round(a / 45.0), 8);
        return new String[] {"north", "northeast", "east", "southeast", "south", "southwest", "west", "northwest"}[i];
    }

    /** A villager's sense of the distance. */
    public static String distance(BlockPos from, BlockPos to) {
        double d = Math.sqrt(from.distSqr(to));
        if (d < 140) return "(just past the last houses)";
        if (d < 260) return "(a short walk out)";
        if (d < 380) return "(a good walk out)";
        return "(well out past the fields)";
    }
}
