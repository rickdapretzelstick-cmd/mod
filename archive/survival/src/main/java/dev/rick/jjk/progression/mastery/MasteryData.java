package dev.rick.jjk.progression.mastery;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * A player's Mastery record (an attachment: survives death, logout, restarts and dimension changes; the server owns it).
 *
 * @param points     unspent Mastery per tree id ("technique/gojo", "tool/slaughter_demon")
 * @param bought     nodes bought per tree id
 * @param earned     Mastery ever earned per tree id (for the screen and future promotion trials)
 * @param fatigue    the anti-grind counter per curse grade ("GRADE_4"...): exorcisms of that grade recently, decaying
 * @param fatigueAt  game time the fatigue was last decayed
 * @param exorcised  curses exorcised per grade (the record future grade trials read)
 * @param incidents  investigations completed per grade
 * @param grade      the sorcerer grade proven so far ({@link SorcererGrade})
 */
public record MasteryData(Map<String, Integer> points, Map<String, List<String>> bought, Map<String, Integer> earned,
                          Map<String, Integer> fatigue, long fatigueAt, Map<String, Integer> exorcised, Map<String, Integer> incidents,
                          String grade) {
    public static final MasteryData EMPTY = new MasteryData(Map.of(), Map.of(), Map.of(), Map.of(), 0L, Map.of(), Map.of(), SorcererGrade.UNRANKED.name());

    public static final Codec<MasteryData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("points", Map.of()).forGetter(MasteryData::points),
            Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf()).optionalFieldOf("bought", Map.of()).forGetter(MasteryData::bought),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("earned", Map.of()).forGetter(MasteryData::earned),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("fatigue", Map.of()).forGetter(MasteryData::fatigue),
            Codec.LONG.optionalFieldOf("fatigue_at", 0L).forGetter(MasteryData::fatigueAt),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("exorcised", Map.of()).forGetter(MasteryData::exorcised),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("incidents", Map.of()).forGetter(MasteryData::incidents),
            Codec.STRING.optionalFieldOf("grade", SorcererGrade.UNRANKED.name()).forGetter(MasteryData::grade)
    ).apply(i, MasteryData::new));

    public MasteryData {
        points = Map.copyOf(points);
        Map<String, List<String>> b = new LinkedHashMap<>();
        bought.forEach((k, v) -> b.put(k, List.copyOf(new LinkedHashSet<>(v))));
        bought = Map.copyOf(b);
        earned = Map.copyOf(earned);
        fatigue = Map.copyOf(fatigue);
        exorcised = Map.copyOf(exorcised);
        incidents = Map.copyOf(incidents);
        grade = grade == null ? SorcererGrade.UNRANKED.name() : grade;
    }

    public int points(String tree) {
        return points.getOrDefault(tree, 0);
    }

    public boolean has(String tree, String node) {
        List<String> l = bought.get(tree);
        return l != null && l.contains(node);
    }

    public List<String> bought(String tree) {
        return bought.getOrDefault(tree, List.of());
    }

    public MasteryData withPoints(String tree, int value) {
        Map<String, Integer> p = new LinkedHashMap<>(points);
        p.put(tree, Math.max(0, value));
        return new MasteryData(p, bought, earned, fatigue, fatigueAt, exorcised, incidents, grade);
    }

    public MasteryData earn(String tree, int amount) {
        Map<String, Integer> p = new LinkedHashMap<>(points);
        p.merge(tree, amount, Integer::sum);
        Map<String, Integer> e = new LinkedHashMap<>(earned);
        e.merge(tree, amount, Integer::sum);
        return new MasteryData(p, bought, e, fatigue, fatigueAt, exorcised, incidents, grade);
    }

    public MasteryData buy(String tree, String node, int cost) {
        Map<String, Integer> p = new LinkedHashMap<>(points);
        p.put(tree, Math.max(0, points(tree) - cost));
        Map<String, List<String>> b = new LinkedHashMap<>(bought);
        List<String> l = new ArrayList<>(bought(tree));
        l.add(node);
        b.put(tree, l);
        return new MasteryData(p, b, earned, fatigue, fatigueAt, exorcised, incidents, grade);
    }

    /** Forgets a tree's purchases and refunds them (admin). */
    public MasteryData respec(String tree, int refund) {
        Map<String, Integer> p = new LinkedHashMap<>(points);
        p.merge(tree, refund, Integer::sum);
        Map<String, List<String>> b = new LinkedHashMap<>(bought);
        b.remove(tree);
        return new MasteryData(p, b, earned, fatigue, fatigueAt, exorcised, incidents, grade);
    }

    public MasteryData withFatigue(Map<String, Integer> f, long at) {
        return new MasteryData(points, bought, earned, f, at, exorcised, incidents, grade);
    }

    public MasteryData recordExorcism(String gradeName, boolean incident) {
        Map<String, Integer> x = new LinkedHashMap<>(exorcised);
        x.merge(gradeName, 1, Integer::sum);
        Map<String, Integer> in = new LinkedHashMap<>(incidents);
        if (incident) in.merge(gradeName, 1, Integer::sum);
        return new MasteryData(points, bought, earned, fatigue, fatigueAt, x, in, grade);
    }

    /** An investigation seen through to the end (its grade). */
    public MasteryData recordIncident(String gradeName) {
        Map<String, Integer> in = new LinkedHashMap<>(incidents);
        in.merge(gradeName, 1, Integer::sum);
        return new MasteryData(points, bought, earned, fatigue, fatigueAt, exorcised, in, grade);
    }

    public MasteryData withGrade(SorcererGrade g) {
        return new MasteryData(points, bought, earned, fatigue, fatigueAt, exorcised, incidents, g.name());
    }
}
