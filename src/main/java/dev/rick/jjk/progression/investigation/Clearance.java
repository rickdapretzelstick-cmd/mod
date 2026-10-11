package dev.rick.jjk.progression.investigation;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.progression.grade.CurseGrade;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * News clearance: per player, server-side and persisted with the investigations. It decides which contracts a player
 * may take, separately from which character (if any) they own. Everyone starts with Grade 4 access; two distinct
 * completed contracts of their current grade open the next ({@link JJKConfig.Clearance}). A contract counts once per
 * player (its unique id), a failed attempt changes nothing, and nobody can be carried past their own clearance: it is
 * checked when a contract is taken up and again at its breach.
 *
 * <p>Grade 1 contracts are the essence guardians, solo challenges: reaching Grade 1 is the last step, and completing a
 * Grade 1 contract advances nothing further.
 */
public final class Clearance {
    private Clearance() {}

    /** One player's clearance: the best grade they may take (4 = Grade 4 ... 1 = Grade 1) and what they've completed. */
    static final class Record {
        int access;
        final Map<Integer, Set<String>> done = new LinkedHashMap<>();
        boolean migrated;

        Record(int access) {
            this.access = access;
        }

        Set<String> done(int grade) {
            return done.computeIfAbsent(grade, k -> new LinkedHashSet<>());
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("Access", access);
            t.putBoolean("Migrated", migrated);
            CompoundTag d = new CompoundTag();
            done.forEach((g, ids) -> {
                ListTag l = new ListTag();
                for (String s : ids) l.add(StringTag.valueOf(s));
                d.put(Integer.toString(g), l);
            });
            t.put("Done", d);
            return t;
        }

        static Record load(CompoundTag t) {
            Record r = new Record(t.getIntOr("Access", JJKConfig.get().clearance.startGrade));
            r.migrated = t.getBooleanOr("Migrated", false);
            CompoundTag d = t.getCompoundOrEmpty("Done");
            for (String k : d.keySet()) {
                try {
                    int g = Integer.parseInt(k);
                    for (Tag x : d.getListOrEmpty(k)) if (x instanceof StringTag s) r.done(g).add(s.value());
                } catch (NumberFormatException ignored) {
                }
            }
            return r;
        }
    }

    /** A grade as a clearance number: Grade 4 → 4 ... Grade 1 → 1 (special grade → 0, never open). */
    public static int number(CurseGrade g) {
        return switch (g) {
            case GRADE_4 -> 4;
            case GRADE_3 -> 3;
            case GRADE_2 -> 2;
            case GRADE_1 -> 1;
            default -> 0;
        };
    }

    public static String name(int grade) {
        return "Grade " + grade;
    }

    static Record of(InvestigationState st, ServerPlayer p) {
        Record r = st.clearance.get(p.getUUID());
        if (r == null) {
            r = new Record(Math.max(1, Math.min(4, JJKConfig.get().clearance.startGrade)));
            st.clearance.put(p.getUUID(), r);
            st.markDirty();
        }
        if (!r.migrated) migrate(st, p, r);
        return r;
    }

    /**
     * Earlier versions kept a count of investigations completed per grade (in the player's Mastery record): reliable
     * records, so they count, up to what each step needs. Nothing is guessed from equipment or characters.
     */
    private static void migrate(InvestigationState st, ServerPlayer p, Record r) {
        r.migrated = true;
        int need = Math.max(1, JJKConfig.get().clearance.contractsToAdvance);
        try {
            Map<String, Integer> old = dev.rick.jjk.progression.mastery.Mastery.data(p).incidents();
            for (int g = 4; g >= 2; g--) {
                int n = Math.min(need, old.getOrDefault("GRADE_" + g, 0));
                for (int i = 0; i < n; i++) r.done(g).add("legacy-" + g + "-" + i);
            }
        } catch (RuntimeException ignored) {
        }
        settle(r);
        st.markDirty();
    }

    /** Opens every grade the record has earned (one step per grade's worth of contracts). */
    private static boolean settle(Record r) {
        int need = Math.max(1, JJKConfig.get().clearance.contractsToAdvance);
        boolean moved = false;
        while (r.access > 1 && r.done(r.access).size() >= need) {
            r.access--;
            moved = true;
        }
        return moved;
    }

    // --- Queries ---

    /** The best grade this player may take (4 ... 1). */
    public static int access(ServerPlayer p) {
        return of(InvestigationState.get(p.level().getServer()), p).access;
    }

    /** Distinct contracts of a grade this player has completed. */
    public static int completed(ServerPlayer p, int grade) {
        return of(InvestigationState.get(p.level().getServer()), p).done(grade).size();
    }

    public static boolean allows(ServerPlayer p, CurseGrade grade) {
        int n = number(grade);
        return n > 0 && n >= access(p);
    }

    public static boolean allows(ServerPlayer p, Incident in) {
        return allows(p, in.grade);
    }

    /** "Grade 3 clearance" (or why it can't be taken at all). */
    public static String requirement(Incident in) {
        int n = number(in.grade);
        return n <= 0 ? "Not open to anyone" : name(n) + " clearance";
    }

    /** Toward the next grade: "1 / 2 Grade 4 contracts toward Grade 3", or that the top is reached. */
    public static String progress(ServerPlayer p) {
        Record r = of(InvestigationState.get(p.level().getServer()), p);
        int need = Math.max(1, JJKConfig.get().clearance.contractsToAdvance);
        if (r.access <= 1) return "Grade 1 clearance: essence guardians are open to you";
        return r.done(r.access).size() + " / " + need + " " + name(r.access) + " contracts toward " + name(r.access - 1);
    }

    /** Why this player may not take this contract (null if they may). */
    @org.jetbrains.annotations.Nullable
    public static String refusal(ServerPlayer p, Incident in) {
        if (!allows(p, in)) {
            return "This contract needs " + requirement(in) + ". You have " + name(access(p)) + " clearance (" + progress(p) + ").";
        }
        if (in.grade == CurseGrade.GRADE_1 && in.challenger != null && !in.challenger.equals(p.getUUID())) {
            return "Someone else has already taken this solo challenge.";
        }
        return null;
    }

    // --- Credit ---

    /** A completed contract, credited to an eligible participant once. True if it counted (first time). */
    static boolean award(ServerPlayer p, Incident in) {
        InvestigationState st = InvestigationState.get(p.level().getServer());
        Record r = of(st, p);
        int g = number(in.grade);
        if (g <= 0 || g < r.access) return false;
        if (!r.done(g).add(in.id)) return false;
        st.markDirty();
        int before = r.access;
        if (settle(r)) {
            p.sendSystemMessage(Component.literal("Clearance raised: " + name(r.access) + ". Harder contracts will reach you now.")
                    .withStyle(ChatFormatting.GOLD));
        } else if (before > 1 && g == before) {
            p.sendSystemMessage(Component.literal("Contract credited (" + progress(p) + ").").withStyle(ChatFormatting.GRAY));
        }
        return true;
    }

    /** Credit for someone offline when the contract completed (their record is updated; they'll see it next time). */
    static void awardOffline(InvestigationState st, UUID id, Incident in) {
        Record r = st.clearance.computeIfAbsent(id, k -> new Record(Math.max(1, Math.min(4, JJKConfig.get().clearance.startGrade))));
        int g = number(in.grade);
        if (g <= 0 || g < r.access) return;
        if (r.done(g).add(in.id)) {
            settle(r);
            st.markDirty();
        }
    }

    // --- Admin and tests ---

    public static void set(ServerPlayer p, int access) {
        InvestigationState st = InvestigationState.get(p.level().getServer());
        Record r = of(st, p);
        r.access = Math.max(1, Math.min(4, access));
        st.markDirty();
    }

    /** Test hook: forgets a player's record entirely (the next look migrates it afresh). */
    public static void forgetForTest(ServerPlayer p) {
        InvestigationState.get(p.level().getServer()).clearance.remove(p.getUUID());
    }

    public static void reset(ServerPlayer p) {
        InvestigationState st = InvestigationState.get(p.level().getServer());
        Record r = new Record(Math.max(1, Math.min(4, JJKConfig.get().clearance.startGrade)));
        r.migrated = true;
        st.clearance.put(p.getUUID(), r);
        st.markDirty();
    }

    static void saveAll(Map<UUID, Record> all, CompoundTag into) {
        CompoundTag c = new CompoundTag();
        all.forEach((u, r) -> c.put(u.toString(), r.save()));
        into.put("Clearance", c);
    }

    static void loadAll(CompoundTag from, Map<UUID, Record> into) {
        CompoundTag c = from.getCompoundOrEmpty("Clearance");
        for (String k : c.keySet()) {
            try {
                into.put(UUID.fromString(k), Record.load(c.getCompoundOrEmpty(k)));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }
}
