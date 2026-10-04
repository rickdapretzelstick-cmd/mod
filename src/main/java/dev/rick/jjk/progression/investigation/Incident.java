package dev.rick.jjk.progression.investigation;

import dev.rick.jjk.progression.grade.CurseGrade;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * One incident in the world: a {@link IncidentTemplate} made real at a place near a village. Saved with the
 * {@link InvestigationState}. Its life: {@link State#OPEN} (reported, waiting for someone to find it), {@link State#ACTIVE}
 * (triggered: its curses are out, in the world or in its realm), {@link State#COMPLETE} (every curse exorcised) or
 * {@link State#EXPIRED} (left alone too long: the village stops talking about it).
 */
public final class Incident {
    public enum State { OPEN, ACTIVE, COMPLETE, EXPIRED }

    public final String id;
    public final String template;
    public final CurseGrade grade;
    public final String dimension;
    /** The village bell it was reported at. */
    public final BlockPos village;
    /** Where it happened: the cliff edge, the pasture, the mine mouth. */
    public final BlockPos site;
    /** The way out from the site (a cliff's drop, a tunnel's direction), as a unit step. */
    public final int dirX, dirZ;
    public final long createdAt;
    public final String headline;
    public final String body;
    State state = State.OPEN;
    long changedAt;
    /** The physical traces at the site have been placed (once its chunk was loaded). */
    boolean featureBuilt;
    /** Its living curses (ACTIVE). */
    final List<UUID> curses = new ArrayList<>();
    /** Everyone who took part (entered its realm or fought at the site). */
    final Set<UUID> participants = new LinkedHashSet<>();
    /** The residue trail leading to the site (for perceiving players). */
    final List<BlockPos> trail = new ArrayList<>();

    public Incident(String id, String template, CurseGrade grade, String dimension, BlockPos village, BlockPos site, int dirX, int dirZ,
                    long createdAt, String headline, String body) {
        this.id = id;
        this.template = template;
        this.grade = grade;
        this.dimension = dimension;
        this.village = village.immutable();
        this.site = site.immutable();
        this.dirX = dirX;
        this.dirZ = dirZ;
        this.createdAt = createdAt;
        this.changedAt = createdAt;
        this.headline = headline;
        this.body = body;
    }

    public State state() {
        return state;
    }

    public long changedAt() {
        return changedAt;
    }

    public List<UUID> curses() {
        return List.copyOf(curses);
    }

    public Set<UUID> participants() {
        return Set.copyOf(participants);
    }

    public List<BlockPos> trail() {
        return List.copyOf(trail);
    }

    @Nullable
    public IncidentTemplate def() {
        return IncidentTemplate.get(template);
    }

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("Id", id);
        t.putString("Template", template);
        t.putString("Grade", grade.name());
        t.putString("Dimension", dimension);
        t.putLong("Village", village.asLong());
        t.putLong("Site", site.asLong());
        t.putInt("DirX", dirX);
        t.putInt("DirZ", dirZ);
        t.putLong("Created", createdAt);
        t.putLong("Changed", changedAt);
        t.putString("Headline", headline);
        t.putString("Body", body);
        t.putString("State", state.name());
        t.putBoolean("Feature", featureBuilt);
        ListTag c = new ListTag();
        for (UUID u : curses) c.add(StringTag.valueOf(u.toString()));
        t.put("Curses", c);
        ListTag p = new ListTag();
        for (UUID u : participants) p.add(StringTag.valueOf(u.toString()));
        t.put("Participants", p);
        ListTag tr = new ListTag();
        for (BlockPos b : trail) tr.add(LongTag.valueOf(b.asLong()));
        t.put("Trail", tr);
        return t;
    }

    @Nullable
    static Incident load(CompoundTag t) {
        try {
            Incident i = new Incident(t.getStringOr("Id", ""), t.getStringOr("Template", ""), CurseGrade.valueOf(t.getStringOr("Grade", "GRADE_4")),
                    t.getStringOr("Dimension", "minecraft:overworld"), BlockPos.of(t.getLongOr("Village", 0L)), BlockPos.of(t.getLongOr("Site", 0L)),
                    t.getIntOr("DirX", 0), t.getIntOr("DirZ", 0), t.getLongOr("Created", 0L), t.getStringOr("Headline", ""), t.getStringOr("Body", ""));
            if (i.id.isEmpty()) return null;
            i.changedAt = t.getLongOr("Changed", i.createdAt);
            i.state = State.valueOf(t.getStringOr("State", "OPEN"));
            i.featureBuilt = t.getBooleanOr("Feature", false);
            for (Tag x : t.getListOrEmpty("Curses")) uuid(x, i.curses);
            List<UUID> ps = new ArrayList<>();
            for (Tag x : t.getListOrEmpty("Participants")) uuid(x, ps);
            i.participants.addAll(ps);
            for (Tag x : t.getListOrEmpty("Trail")) if (x instanceof LongTag l) i.trail.add(BlockPos.of(l.longValue()));
            return i;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void uuid(Tag x, List<UUID> into) {
        if (x instanceof StringTag s) {
            try {
                into.add(UUID.fromString(s.value()));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }
}
