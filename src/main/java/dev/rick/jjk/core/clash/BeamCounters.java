package dev.rick.jjk.core.clash;

import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.Casters;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Who can answer whose beam, and how. Each character with an ultimate beam registers its answer here. Any ultimate beam
 * answers any other, whoever fires it: True Love Beam meets Every Last Drop, and also another Yuta's True Love Beam (and
 * Every Last Drop another Ryu's). A clash is told apart by who fires the beams, never by their kits. A character's
 * answer is its Ultimate key during the reaction window, nothing else.
 */
public final class BeamCounters {
    /** One character's answer to an incoming beam. */
    public interface Counter {
        /** Whether this answers a beam of {@code kind} ("tlb", "eld"): every clash beam, its own kind included. */
        default boolean answers(String kind) {
            return true;
        }

        /** The kind of beam it fires. */
        String kind();

        /** What the prompt calls it. */
        String name();

        /** Has the Ultimate it takes ready (meter, cooldown, energy), and nothing stops them firing it. */
        boolean ready(AbilityCaster caster);

        /** Where its beam would come from, firing at {@code at}. */
        Vec3 origin(LivingEntity user, Vec3 at);

        /**
         * Spends what it costs and starts the answering beam, aimed at {@code at}; returns false (spending nothing) if it
         * can't after all.
         */
        boolean fire(AbilityCaster caster, Vec3 at);
    }

    private static final Map<String, Counter> BY_CHARACTER = new HashMap<>();

    private BeamCounters() {}

    public static void register(String characterId, Counter counter) {
        BY_CHARACTER.put(characterId, counter);
    }

    @Nullable
    public static Counter of(LivingEntity e) {
        AbilityCaster c = Casters.getOrNull(e);
        return c == null || c.character() == null ? null : BY_CHARACTER.get(c.character().id);
    }

    /** The beam this sorcerer fires, for drawing it before it exists. */
    static String kindOf(LivingEntity e) {
        Counter c = of(e);
        return c == null ? "tlb" : c.kind();
    }
}
