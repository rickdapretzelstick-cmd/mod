package dev.rick.jjk.test;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.progression.investigation.CursedRealms;
import dev.rick.jjk.progression.investigation.GuardianArena;
import dev.rick.jjk.progression.investigation.Incident;
import dev.rick.jjk.progression.investigation.IncidentTemplate;
import dev.rick.jjk.progression.investigation.InvestigationState;
import dev.rick.jjk.progression.investigation.Investigations;
import dev.rick.jjk.progression.investigation.RealmRooms;
import dev.rick.jjk.progression.investigation.Sites;
import dev.rick.jjk.progression.training.TrialPropEntity;
import dev.rick.jjk.progression.training.TrialRooms;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The cursed realm environments (the Cursed Realm Blockbench pack, {@link RealmRooms}): every room's plan fits its arena's
 * cleared box, is closed in, has footing and headroom where players arrive and where curses wait, room for a guardian to
 * stand and for its phase beats to change the floor; and every destination is reached through its real entry
 * ({@link CursedRealms#enter} for its incident) into the right room, with its props (and, in Yuta's courtyard, the mod's
 * own Rika on her terrace), its curses on the ground, nothing left behind when it closes, and nothing doubled on the
 * next visit.
 */
public class RealmRoomTests {
    private static final String[] ROOMS = {"distance_realm", "theater_realm", "chapel_realm", "crater_realm", "fight_club_realm", "forest_realm",
            "guardian_claw_hunter", "guardian_pale_stalker", "guardian_cyclops_bruiser", "guardian_veiled_reacher"};

    private static Map<BlockPos, net.minecraft.world.level.block.state.BlockState> blocks(TrialRooms.Plan p) {
        return p.blocks();
    }

    private static boolean solid(Map<BlockPos, net.minecraft.world.level.block.state.BlockState> b, int x, int y, int z) {
        var s = b.get(new BlockPos(x, y, z));
        return s != null && !s.is(Blocks.LIGHT) && !s.is(Blocks.LANTERN);
    }

    /** Footing under a spot (a block or a slab) and {@code h} blocks of air above it, {@code r} round it. */
    private static boolean stands(Map<BlockPos, net.minecraft.world.level.block.state.BlockState> b, Vec3 at, int h, int r) {
        int x = (int) Math.floor(at.x), z = (int) Math.floor(at.z);
        int feet = (int) Math.ceil(at.y - 0.01);
        boolean footing = solid(b, x, feet - 1, z) || (at.y - Math.floor(at.y) > 0.4 && solid(b, x, (int) Math.floor(at.y), z));
        if (!footing) return false;
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) for (int y = feet; y < feet + h; y++) if (solid(b, x + dx, y, z + dz)) return false;
        return true;
    }

    @GameTest(environment = "jjk-test:realm_rooms_a")
    public void everyRealmRoomFitsStandsAndIsClosedIn(GameTestHelper h) {
        for (String id : ROOMS) {
            TrialRooms.Plan p = RealmRooms.plan(id);
            h.assertTrue(p != null, id + ": its plan loads");
            int[] bd = p.bounds();
            h.assertTrue(bd[0] >= -CursedRealms.RADIUS && bd[3] <= CursedRealms.RADIUS + 1 && bd[2] >= -CursedRealms.RADIUS && bd[5] <= CursedRealms.RADIUS + 1
                    && bd[1] >= -20 && bd[4] <= CursedRealms.HEIGHT, id + ": fits the arena's cleared box " + java.util.Arrays.toString(bd));
            var b = blocks(p);
            Vec3 arrival = p.marker("arrival");
            h.assertTrue(stands(b, arrival, 3, 0), id + ": footing and headroom where players arrive " + arrival);
            h.assertTrue(!p.spots().isEmpty(), id + ": somewhere for its curses");
            for (Vec3 s : p.spots()) h.assertTrue(stands(b, s, 3, 0), id + ": a curse spot on open ground " + s);
            // Closed in: a roof over the arrival and a wall every way from it.
            int ax = (int) Math.floor(arrival.x), az = (int) Math.floor(arrival.z), ay = (int) Math.ceil(arrival.y);
            boolean roof = false;
            for (int y = ay + 2; y < bd[4]; y++) roof |= solid(b, ax, y, az);
            h.assertTrue(roof, id + ": roofed over");
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                boolean wall = false;
                for (int k = 1; k < 60 && !wall; k++) wall = solid(b, ax + d[0] * k, ay + 1, az + d[1] * k);
                h.assertTrue(wall, id + ": walled in toward " + d[0] + "," + d[1]);
            }
            if (id.startsWith("guardian_")) {
                // The guardian has room to stand; its phase beats' pillars rise from open floor round the fight's centre.
                Vec3 g = p.marker("guardian_spawn");
                h.assertTrue(stands(b, g, 4, 1), id + ": room for the guardian at its spawn " + g);
                for (int i = 0; i < 4; i++) {
                    Vec3 pil = p.marker("pillar_" + i);
                    h.assertTrue(pil != null, id + ": a spot for phase pillar " + i);
                    int px = (int) Math.floor(pil.x), pz = (int) Math.floor(pil.z);
                    for (int dx = 0; dx <= 1; dx++) for (int dz = 0; dz <= 1; dz++) {
                        h.assertTrue(solid(b, px + dx, -1, pz + dz) && !solid(b, px + dx, 0, pz + dz) && !solid(b, px + dx, 3, pz + dz),
                                id + ": phase pillar " + i + " rises from open floor");
                    }
                }
            }
            if (id.equals("chapel_realm")) {
                var rika = p.props().stream().filter(pr -> pr.kind() == TrialPropEntity.RIKA).toList();
                h.assertTrue(rika.size() == 1 && rika.get(0).model().equals("rika") && rika.get(0).at() != null, "chapel: the mod's own Rika on her terrace");
            }
        }
        // No sculpted stand-in for Rika in the courtyard's model (its terrace is left empty for the mod's own).
        try (var in = RealmRoomTests.class.getClassLoader().getResourceAsStream("assets/jjk/models/bb/realm_chapel_realm.bbmodel")) {
            String m = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).toLowerCase(java.util.Locale.ROOT);
            h.assertTrue(!m.contains("relief") && !m.contains("sculpt") && !m.contains("silhouette"), "no Rika sculpture in the courtyard's model");
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
        h.succeed();
    }

    private static Incident incident(GameTestHelper h, String template, int seed) {
        ServerLevel level = h.getLevel();
        InvestigationState st = InvestigationState.get(level.getServer());
        InvestigationState.Village v = Investigations.village(level, h.absolutePos(new BlockPos(-40, 1, -40)));
        return Investigations.create(level, v, st, IncidentTemplate.get(template), new Sites.Site(h.absolutePos(new BlockPos(4, 1, 4)), 1, 0),
                level.getGameTime(), RandomSource.create(seed));
    }

    @GameTest(maxTicks = 900, environment = "jjk-test:realm_rooms_b")
    public void everyDestinationOpensItsOwnRoomAndClosesClean(GameTestHelper h) {
        JJKConfig.get().general.autoAssignGojo = false;
        JJKConfig.get().progression.enabled = true;
        for (int x = 0; x < 9; x++) for (int z = 0; z < 9; z++) h.setBlock(x, 0, z, Blocks.STONE);
        ServerLevel level = h.getLevel();
        InvestigationState st = InvestigationState.get(level.getServer());
        // The templates that lead to each room (the storyline realms through their own events' templates: only the
        // templates, the storylines themselves stay as configured).
        Map<String, String> via = new HashMap<>();
        via.put("chapel_realm", "jilted_bride");
        via.put("crater_realm", "stalker_crater");
        via.put("forest_realm", "hunting_lodge");
        via.put("distance_realm", "gojo_chain_1");
        via.put("theater_realm", "yuji_chain_1");
        via.put("fight_club_realm", "hakari_chain_1");
        // Each guardian body's own arena, through Grade 1 contracts (the body is the contract's).
        Map<String, Incident> guardians = new HashMap<>();
        for (int seed = 0; seed < 80 && guardians.size() < 4; seed++) {
            Incident in = incident(h, "guardian_cliff", seed);
            guardians.putIfAbsent(in.guardian(), in);
        }
        h.assertTrue(guardians.size() == 4, "contracts for all four guardians (" + guardians.keySet() + ")");
        Map<String, Incident> all = new java.util.LinkedHashMap<>();
        for (var e : via.entrySet()) all.put(e.getKey(), incident(h, e.getValue(), 1));
        for (var e : guardians.entrySet()) all.put(GuardianArena.idFor(e.getKey()), e.getValue());
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.CREATIVE);
        p.setPos(h.absoluteVec(new Vec3(4.5, 1, 4.5)));
        visit(h, p, st, new java.util.ArrayList<>(all.entrySet()), 0, 0);
    }

    /** One visit at a time: enter, give the room a second to load in, look it over, close it; twice per room. */
    private static void visit(GameTestHelper h, ServerPlayer p, InvestigationState st, List<Map.Entry<String, Incident>> all, int index, int visit) {
        if (index >= all.size()) {
            p.discard();
            h.succeed();
            return;
        }
        ServerLevel level = h.getLevel();
        String layout = all.get(index).getKey();
        Incident in = all.get(index).getValue();
        String room = layout.startsWith(GuardianArena.ID + "_") ? layout.substring(GuardianArena.ID.length() + 1) : layout;
        TrialRooms.Plan plan = RealmRooms.plan(room);
        h.assertTrue(CursedRealms.enter(p, in, st), layout + ": entered");
        h.runAfterDelay(20, () -> {
            var arena = CursedRealms.arenaOf(st, in.id);
            h.assertTrue(arena != null && arena.layout.equals(layout), layout + ": its own room (" + (arena == null ? null : arena.layout) + ")");
            ServerLevel realm = CursedRealms.level(level.getServer());
            BlockPos o = arena.origin;
            AABB box = new AABB(o).inflate(CursedRealms.RADIUS, CursedRealms.HEIGHT, CursedRealms.RADIUS);
            Vec3 arrive = new Vec3(o.getX(), o.getY(), o.getZ()).add(plan.marker("arrival"));
            h.assertTrue(p.position().distanceTo(arrive) < 1.0 && realm.noCollision(p, p.getBoundingBox()), layout + ": set down at its arrival, in the clear");
            List<TrialPropEntity> props = realm.getEntitiesOfClass(TrialPropEntity.class, box, x -> true);
            h.assertTrue(props.size() == plan.props().size(), layout + ": its props once each (" + props.size() + ")");
            if (room.equals("chapel_realm")) h.assertTrue(props.stream().anyMatch(x -> x.kind() == TrialPropEntity.RIKA), "chapel: Rika on her terrace");
            h.assertTrue(realm.getEntitiesOfClass(dev.rick.jjk.yuta.RikaEntity.class, box, x -> true).isEmpty(), layout + ": no Rika entity raised");
            // Its curses (or its guardian) are on the ground in the room.
            List<Mob> curses = realm.getEntitiesOfClass(Mob.class, box, x -> x.isAlive());
            h.assertTrue(!curses.isEmpty(), layout + ": its curses are there");
            for (Mob m : curses) h.assertTrue(realm.noCollision(m, m.getBoundingBox()), layout + ": " + m.getType().toShortString() + " not inside anything");
            // The room's light colour, not the realm's crimson, inside it.
            // (Only where arenas live in the realm dimension: a world without it builds them in the overworld's sky, untinted.)
            boolean dim = realm.dimension() == CursedRealms.DIMENSION;
            var here = realm.getBiome(o.offset(0, 2, 0));
            h.assertTrue(!dim || here.unwrapKey().map(k -> k.identifier().getPath().equals("realm_" + room)).orElse(false),
                    layout + ": its own light colour (" + here.unwrapKey().map(k -> k.identifier().toString()).orElse("?") + " in " + realm.dimension().identifier() + ")");
            CursedRealms.close(level.getServer(), st, arena, false);
            h.assertTrue(realm.getEntitiesOfClass(TrialPropEntity.class, box, x -> true).isEmpty(), layout + ": no props left behind");
            h.assertTrue(realm.getEntitiesOfClass(Mob.class, box, x -> x.isAlive()).isEmpty(), layout + ": no curses left behind");
            h.assertTrue(realm.getBlockState(o.below()).isAir(), layout + ": its blocks are cleared");
            h.assertTrue(!dim || realm.getBiome(o.offset(0, 2, 0)).unwrapKey().map(k -> k.identifier().getPath().equals("the_void")).orElse(false),
                    layout + ": the realm's own void again");
            if (visit == 0) visit(h, p, st, all, index, 1);
            else visit(h, p, st, all, index + 1, 0);
        });
    }
}
