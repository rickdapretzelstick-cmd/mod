package dev.rick.jjk.yuta;

import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Cursed Partners' per-life memory, keyed to the living entity (lost on death, never tied to an account):
 * <ul>
 *   <li>Rika: her entity, whether her moveset is up, who she is fighting, and until when she is busy with a move;</li>
 *   <li>the special's presses (a double press dismisses her; held while recalling, she stays where she is);</li>
 *   <li>Swordsmanship and Steel Arm: whether he fights with the katana or his steel-cased fists;</li>
 *   <li>Second Wind's second try, and Outburst's parry window;</li>
 *   <li>Copy: the techniques Rika has taken (oldest first), the one selected, the wheel and its page, and each
 *       technique's own cooldown;</li>
 *   <li>Authentic Mutual Love: direct katana hits toward Jacob's Ladder.</li>
 * </ul>
 */
public final class YutaState {
    private static final Map<LivingEntity, YutaState> STATES = new WeakHashMap<>();

    @Nullable public RikaEntity rika;
    public boolean rikaMode;
    @Nullable public LivingEntity target;
    public long targetAt;
    public long rikaBusyUntil;
    public long specialPressedAt = -100;
    public long specialHeldFrom = -1;
    /** In True Love: fighting with the steel-cased fists (true) or the katana again (after Energy Ripple). */
    public boolean fists;
    /** Steel Arm's jab, due at this game time (-1: none); slowed once by a guard. */
    public long jabAt = -1;
    public boolean jabSlowed;
    /** Second Wind whiffed once: the next use is its second try (and then its cooldown runs). */
    public boolean secondWindRetry;
    public long secondWindRetryUntil;
    /** Game time Outburst began (its first quarter second parries), or -1. */
    public long outburstAt = -1;
    public final List<String> copied = new ArrayList<>(List.of(Copies.SPEECH));
    public String selected = Copies.SPEECH;
    public boolean wheelOpen;
    public int page;
    public final Map<String, Long> copyReadyAt = new HashMap<>();
    public int ladderHits;
    public boolean ladderUsed;
    /** Marked by his Clairvoyance (until the game time given): their attacks on him are dodged. */
    public final Map<LivingEntity, Long> marked = new java.util.WeakHashMap<>();

    public static YutaState of(LivingEntity e) {
        return STATES.computeIfAbsent(e, k -> new YutaState());
    }

    @Nullable
    public static YutaState get(LivingEntity e) {
        return STATES.get(e);
    }

    public static void clear(LivingEntity e) {
        YutaState s = STATES.remove(e);
        if (s != null && s.rika != null) s.rika.discard();
    }

    /** Rika, if she is out. */
    @Nullable
    public RikaEntity rika() {
        if (rika != null && rika.isRemoved()) rika = null;
        return rika;
    }

    public boolean rikaBusy(long now) {
        return now < rikaBusyUntil;
    }

    /** Clairvoyance marked {@code target}: for as long as the mark lasts, its attacks on him miss. */
    public void markedBy(LivingEntity target) {
        marked.put(target, target.level().getGameTime() + 20L * 10);
    }

    /** Learns a technique (Rika took it): the newest replaces the oldest once the wheel is full. */
    public boolean learn(String technique, int slots) {
        if (copied.contains(technique)) return false;
        copied.add(technique);
        while (copied.size() > slots) {
            String gone = copied.removeFirst();
            if (gone.equals(selected)) selected = copied.getFirst();
        }
        return true;
    }
}
