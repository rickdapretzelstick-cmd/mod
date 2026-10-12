package dev.rick.jjk.progression.guardian;

import dev.rick.jjk.JJK;
import dev.rick.jjk.progression.curse.CommonCurseEntity;
import dev.rick.jjk.registry.ModEntities;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Grade 1 essence guardians a Grade 1 News contract can bring: four bodies, three cosmetic variants each (variants
 * share their body's balance). A contract picks one body and one variant when it is generated and keeps both for every
 * retry ({@code Incident#guardian}, {@code Incident#variant}).
 *
 * <p>Each body is its own entity, registered as {@code jjk:<id>}: a {@link CommonCurseEntity} (so the contract system
 * binds, tracks and completes it like any curse) that implements {@link GuardianBody} (it takes its variant and reports
 * its defeat to the seal capture). It fights with the moves of the curse that stood in for it before it had a body (it
 * extends that curse), drawn with the Blockbench pack's models and its own clips.
 */
public final class Guardians {
    /** One guardian body: its entity id, how many visual variants it has, its entity, and the curse whose moves it has. */
    public record Body(String id, String name, int variants, EntityType<? extends CommonCurseEntity> type, EntityType<? extends CommonCurseEntity> standIn) {}

    public static final List<Body> BODIES = List.of(
            new Body("guardian_claw_hunter", "the skeletal claw hunter", 3, ModEntities.GUARDIAN_CLAW_HUNTER, ModEntities.GRAVE_HOUND),
            new Body("guardian_pale_stalker", "the pale-haired stalker", 3, ModEntities.GUARDIAN_PALE_STALKER, ModEntities.FOREST_STALKER),
            new Body("guardian_cyclops_bruiser", "the heavy cyclops bruiser", 3, ModEntities.GUARDIAN_CYCLOPS_BRUISER, ModEntities.SCHOOL_MAW),
            new Body("guardian_veiled_reacher", "the veiled long-armed curse", 3, ModEntities.GUARDIAN_VEILED_REACHER, ModEntities.VEILED_BRIDE));

    private Guardians() {}

    @Nullable
    public static Body body(String id) {
        for (Body b : BODIES) if (b.id().equals(id)) return b;
        return null;
    }

    /** A body and variant for a new contract (every body is in the pool). */
    public static Body pick(RandomSource r) {
        return BODIES.get(r.nextInt(BODIES.size()));
    }

    /** The entity type to raise for a body (an unknown id, from an old save, gets the first body). */
    public static EntityType<? extends CommonCurseEntity> type(String id) {
        Body b = body(id);
        return (b != null ? b : BODIES.get(0)).type();
    }

    /** Whether a body has its own entity, registered as {@code jjk:<id>} (not a stand-in). */
    public static boolean present(String id) {
        Body b = body(id);
        return b != null && b.type() != b.standIn() && BuiltInRegistries.ENTITY_TYPE.getOptional(JJK.id(id)).orElse(null) == b.type();
    }
}
