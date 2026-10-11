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
 * <p>The guardians themselves are separate work: each is an entity registered as {@code jjk:<id>} that extends
 * {@link CommonCurseEntity} (so the contract system can bind, track and complete it like any curse) and implements
 * {@link GuardianBody} (to take its variant and to report its defeat to the seal capture). Until a body's entity
 * exists, its contracts use a stand-in existing curse raised to Grade 1, so the whole flow already works end to end.
 */
public final class Guardians {
    /** One guardian body: its entity id, how many visual variants it has, and the stand-in used until it exists. */
    public record Body(String id, String name, int variants, EntityType<? extends CommonCurseEntity> standIn) {}

    public static final List<Body> BODIES = List.of(
            new Body("guardian_claw_hunter", "the skeletal claw hunter", 3, ModEntities.GRAVE_HOUND),
            new Body("guardian_pale_stalker", "the pale-haired stalker", 3, ModEntities.FOREST_STALKER),
            new Body("guardian_cyclops_bruiser", "the heavy cyclops bruiser", 3, ModEntities.SCHOOL_MAW),
            new Body("guardian_veiled_reacher", "the veiled long-armed curse", 3, ModEntities.VEILED_BRIDE));

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

    /**
     * The entity type to raise for a body: its own (once registered as {@code jjk:<id>} and a {@link CommonCurseEntity}),
     * else its stand-in.
     */
    @SuppressWarnings("unchecked")
    public static EntityType<? extends CommonCurseEntity> type(String id) {
        Body b = body(id);
        if (b == null) return BODIES.get(0).standIn();
        var own = BuiltInRegistries.ENTITY_TYPE.getOptional(JJK.id(id));
        if (own.isPresent()) {
            EntityType<?> t = own.get();
            // Only a curse the contract system can track counts as the real body.
            try {
                if (CommonCurseEntity.class.isAssignableFrom(t.getBaseClass())) return (EntityType<? extends CommonCurseEntity>) t;
            } catch (RuntimeException ignored) {
            }
        }
        return b.standIn();
    }

    /** Whether a body's real entity is in this build (not its stand-in). */
    public static boolean present(String id) {
        return type(id) != (body(id) != null ? body(id).standIn() : null);
    }
}
