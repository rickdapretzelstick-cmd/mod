package dev.rick.jjk.progression.investigation;

import dev.rick.jjk.progression.training.TrialPropEntity;
import dev.rick.jjk.progression.training.TrialRooms;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The cursed realm environments of the "Cursed Realm Blockbench Pack", each a realm arena's layout
 * ({@code tools/install_realm_rooms.py}, {@code data/jjk/realm_rooms/<id>.json}): Gojo's fractured infinity
 * ({@code distance_realm}), Yuji's last screening ({@code theater_realm}), Yuta's courtyard ({@code chapel_realm}), Ryu's
 * impact basin ({@code crater_realm}), Hakari's fight club ({@code fight_club_realm}), the rifle's watching woods
 * ({@code forest_realm}), and a Grade 1 guardian's arena per guardian body ({@link GuardianArena#idFor}).
 *
 * <p>A room is its block plan (closed in and lit, with its own light colour) and its props: one prop model drawn in room
 * coordinates, and in the courtyard the mod's own Rika on her terrace (a display of her model, not an entity of hers).
 * The props are never saved: an arena that outlives a restart gets them back on its next tick ({@link #ensureProps}), and
 * closing the arena clears them with everything else in its box.
 */
public final class RealmRooms {
    private RealmRooms() {}

    @Nullable
    public static TrialRooms.Plan plan(String id) {
        return TrialRooms.cached("data/jjk/realm_rooms/" + id + ".json", id);
    }

    /** A realm layout built from its room plan (if the plan can't be read, the fallback layout stands in). */
    public static final class Layout implements CursedRealms.Layout {
        private final String id;
        private final CursedRealms.Layout fallback;

        public Layout(String id, CursedRealms.Layout fallback) {
            this.id = id;
            this.fallback = fallback;
        }

        @Nullable
        TrialRooms.Plan plan() {
            return RealmRooms.plan(id);
        }

        /** Room-local (from the origin's corner) to the layouts' frame (from the origin block's bottom centre). */
        private static Vec3 local(Vec3 v) {
            return v.subtract(0.5, 0, 0.5);
        }

        @Override
        public void build(ServerLevel level, BlockPos origin, RandomSource random) {
            TrialRooms.Plan p = plan();
            if (p == null) {
                fallback.build(level, origin, random);
                return;
            }
            TrialRooms.build(level, origin, p);
            TrialRooms.spawnProps(level, origin, p);
        }

        @Override
        public Vec3 arrival() {
            TrialRooms.Plan p = plan();
            return p == null ? fallback.arrival() : local(p.marker("arrival"));
        }

        @Override
        public List<Vec3> curseSpots() {
            TrialRooms.Plan p = plan();
            if (p == null) return fallback.curseSpots();
            List<Vec3> out = new ArrayList<>();
            for (Vec3 s : p.spots()) out.add(local(s));
            return out;
        }

        /** Arrivals face into the room (its entrance is toward +Z). */
        @Override
        public float arrivalYaw() {
            return plan() == null ? fallback.arrivalYaw() : 180f;
        }

        @Override
        public int radius() {
            TrialRooms.Plan p = plan();
            if (p == null) return fallback.radius();
            int[] b = p.bounds();
            return Math.max(Math.max(-b[0], b[3]), Math.max(-b[2], b[5])) - 1;
        }

        @Override
        public Vec3 center() {
            TrialRooms.Plan p = plan();
            if (p == null) return fallback.center();
            Vec3 c = p.marker("combat_center") != null ? p.marker("combat_center") : p.marker("encounter_center");
            return c == null ? Vec3.ZERO : local(c);
        }

        @Override
        public String entryLine() {
            TrialRooms.Plan p = plan();
            return p == null || p.line().isEmpty() ? fallback.entryLine() : p.line();
        }

        /** The props back, if the arena outlived them (a restart: they are never saved). */
        void ensureProps(ServerLevel level, InvestigationState.Arena a) {
            TrialRooms.Plan p = plan();
            if (p == null || p.props().isEmpty()) return;
            AABB box = new AABB(a.origin).inflate(CursedRealms.RADIUS, CursedRealms.HEIGHT, CursedRealms.RADIUS);
            if (!level.getEntitiesOfClass(TrialPropEntity.class, box, e -> !e.isRemoved()).isEmpty()) return;
            TrialRooms.spawnProps(level, a.origin, p);
        }
    }
}
