package dev.rick.jjk.test;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.progression.investigation.CursedRealms;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The last six realms (the Cursed Realms pack: mine, house, pasture, cliff, the Finger Bearer's hall, the hollow): each
 * plan fits its slot, has footing and headroom at the arrival (the cliff's landing, under a clear fall) and at every curse
 * spot, every room is closed in (walls all round and a ceiling), every lantern lights; every incident mapped to a realm opens its own room;
 * and the fallback holds: an unknown realm id is the hollow, a missing plan falls back to the model-backed hollow, and
 * with that missing too to the procedural layout.
 */
public class RealmRoomBTests {
    static final String[] ROOMS = {"mine_realm", "house_realm", "pasture_realm", "cliff_realm", "finger_bearer_realm", "hollow_realm"};

    private static boolean solid(Map<BlockPos, BlockState> b, int x, int y, int z) {
        BlockState s = b.get(new BlockPos(x, y, z));
        return s != null && !s.is(Blocks.LIGHT);
    }

    private static boolean stands(Map<BlockPos, BlockState> b, Vec3 at, int h, int r) {
        int x = (int) Math.floor(at.x), z = (int) Math.floor(at.z), feet = (int) Math.ceil(at.y - 0.01);
        if (!solid(b, x, feet - 1, z)) return false;
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) for (int y = feet; y < feet + h; y++) if (solid(b, x + dx, y, z + dz)) return false;
        return true;
    }

    @GameTest(environment = "jjk-test:realm_rooms_c")
    public void theLastSixStandSealAndLight(GameTestHelper h) {
        for (String id : ROOMS) {
            TrialRooms.Plan p = RealmRooms.plan(id);
            h.assertTrue(p != null, id + ": its plan loads");
            int[] bd = p.bounds();
            h.assertTrue(bd[0] >= -CursedRealms.RADIUS && bd[3] <= CursedRealms.RADIUS + 1 && bd[2] >= -CursedRealms.RADIUS && bd[5] <= CursedRealms.RADIUS + 1
                    && bd[1] >= -20 && bd[4] <= CursedRealms.HEIGHT, id + ": fits its slot " + java.util.Arrays.toString(bd));
            h.assertTrue(p.props().size() == 1 && p.props().getFirst().model().equals("realmb_" + id), id + ": its environment model");
            Map<BlockPos, BlockState> b = p.blocks();
            Vec3 arrival = p.marker("arrival");
            Vec3 landing = p.marker("landing");
            if (id.equals("cliff_realm")) {
                h.assertTrue(landing != null && arrival.y >= landing.y + 10, "cliff: they arrive high over the landing");
                h.assertTrue(stands(b, landing, (int) (arrival.y - landing.y) + 2, 0), "cliff: a clear fall onto real ground");
                h.assertTrue(CursedRealms.layout(id).fromAbove(), "cliff: pulled in from above");
            } else {
                h.assertTrue(landing == null && stands(b, arrival, 3, 0), id + ": footing and headroom at the arrival " + arrival);
                h.assertTrue(!CursedRealms.layout(id).fromAbove(), id + ": set down, not dropped");
            }
            h.assertTrue(!p.spots().isEmpty(), id + ": curse spots");
            for (Vec3 s : p.spots()) h.assertTrue(stands(b, s, 3, id.equals("finger_bearer_realm") ? 2 : 1), id + ": a spot on open ground " + s);
            h.assertTrue(p.lights().length > 0, id + ": its lanterns light it");
            for (int[] l : p.lights()) h.assertTrue(!b.containsKey(new BlockPos(l[0], l[1], l[2])), id + ": a light in the open");
            {
                // Sealed, every one of them: from where they stand, nothing open leads out of the room's box.
                BlockPos start = BlockPos.containing(landing != null ? landing : arrival);
                Set<BlockPos> seen = new HashSet<>(List.of(start));
                ArrayDeque<BlockPos> todo = new ArrayDeque<>(List.of(start));
                while (!todo.isEmpty()) {
                    BlockPos q = todo.pop();
                    for (var d : net.minecraft.core.Direction.values()) {
                        BlockPos n = q.relative(d);
                        if (seen.contains(n) || solid(b, n.getX(), n.getY(), n.getZ())) continue;
                        h.assertTrue(n.getX() >= bd[0] && n.getX() < bd[3] && n.getZ() >= bd[2] && n.getZ() < bd[5] && n.getY() >= bd[1] && n.getY() < bd[4],
                                id + ": sealed (open to the void at " + n.toShortString() + ")");
                        seen.add(n);
                        todo.add(n);
                    }
                }
            }
        }
        // The Finger Bearer takes shape at its hall's seal.
        TrialRooms.Plan fb = RealmRooms.plan("finger_bearer_realm");
        Vec3 seal = fb.marker("seal");
        h.assertTrue(seal != null && Math.abs(seal.x - 0.5) < 1.5 && Math.abs(seal.z - 0.5) < 1.5 && fb.spots().getFirst().equals(seal), "the Finger Bearer at the seal");
        h.succeed();
    }

    @GameTest(environment = "jjk-test:realm_rooms_c")
    public void theFallbackIsBoundedAndSafe(GameTestHelper h) {
        String mine = "data/jjk/realm_rooms/mine_realm.json", hollow = "data/jjk/realm_rooms/hollow_realm.json";
        Vec3 own = CursedRealms.layout("mine_realm").arrival();
        Vec3 hollowArrival = CursedRealms.layout("hollow_realm").arrival();
        h.assertTrue(CursedRealms.layout("no_such_realm") == CursedRealms.layout("hollow_realm"), "an unknown realm id is the hollow");
        try {
            TrialRooms.simulateMissingForTest(mine, true);
            h.assertTrue(CursedRealms.layout("mine_realm").arrival().equals(hollowArrival), "the mine's plan missing: the model-backed hollow stands in");
            TrialRooms.simulateMissingForTest(hollow, true);
            Vec3 procedural = CursedRealms.layout("mine_realm").arrival();
            h.assertTrue(!procedural.equals(own) && !procedural.equals(hollowArrival), "both missing: the procedural mine (" + procedural + ")");
            h.assertTrue(CursedRealms.layout("hollow_realm").arrival() != null && !CursedRealms.layout("hollow_realm").curseSpots().isEmpty(),
                    "the hollow itself missing: its procedural island, never a loop");
        } finally {
            TrialRooms.simulateMissingForTest(mine, false);
            TrialRooms.simulateMissingForTest(hollow, false);
        }
        h.assertTrue(CursedRealms.layout("mine_realm").arrival().equals(own), "and back to its own");
        h.succeed();
    }

    private static Incident incident(GameTestHelper h, String template) {
        ServerLevel level = h.getLevel();
        InvestigationState st = InvestigationState.get(level.getServer());
        InvestigationState.Village v = Investigations.village(level, h.absolutePos(new BlockPos(-40, 1, -40)));
        return Investigations.create(level, v, st, IncidentTemplate.get(template), new Sites.Site(h.absolutePos(new BlockPos(4, 1, 4)), 1, 0),
                level.getGameTime(), RandomSource.create(7));
    }

    @GameTest(maxTicks = 1200, environment = "jjk-test:realm_rooms_d")
    public void everyMappedIncidentOpensItsOwnRoom(GameTestHelper h) {
        JJKConfig.get().general.autoAssignGojo = false;
        JJKConfig.get().progression.enabled = true;
        for (int x = 0; x < 9; x++) for (int z = 0; z < 9; z++) h.setBlock(x, 0, z, Blocks.STONE);
        // Every incident the realms are mapped to (their templates' own "realm"), checked against its room.
        Map<String, String> via = new LinkedHashMap<>();
        for (String t : List.of("old_mine", "deep_mine_maw", "crawler_culvert", "yuji_chain_3", "ryu_chain_3", "hakari_chain_3")) via.put(t, "mine_realm");
        for (String t : List.of("empty_house", "brood_nest", "brood_anchor", "gojo_chain_3", "yuji_chain_2", "yuta_chain_1")) via.put(t, "house_realm");
        for (String t : List.of("livestock", "hound_kennel", "kennel_master", "shrine_anchor")) via.put(t, "pasture_realm");
        for (String t : List.of("cliff_fall", "millpond_toad", "yuta_chain_2")) via.put(t, "cliff_realm");
        for (var e : via.entrySet()) {
            IncidentTemplate t = IncidentTemplate.get(e.getKey());
            h.assertTrue(t != null && e.getValue().equals(t.realm()), e.getKey() + " leads to " + e.getValue() + " (" + (t == null ? null : t.realm()) + ")");
        }
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.CREATIVE);
        p.setPos(h.absoluteVec(new Vec3(4.5, 1, 4.5)));
        InvestigationState st = InvestigationState.get(h.getLevel().getServer());
        List<String> one = List.of("old_mine", "empty_house", "livestock", "cliff_fall");
        visit(h, p, st, one, 0);
    }

    private static void visit(GameTestHelper h, ServerPlayer p, InvestigationState st, List<String> templates, int i) {
        if (i >= templates.size()) {
            p.discard();
            h.succeed();
            return;
        }
        ServerLevel level = h.getLevel();
        Incident in = incident(h, templates.get(i));
        String room = in.def().realm();
        h.assertTrue(CursedRealms.enter(p, in, st), room + ": entered");
        h.runAfterDelay(30, () -> {
            var arena = CursedRealms.arenaOf(st, in.id);
            h.assertTrue(arena != null && arena.layout.equals(room), room + ": its own room");
            ServerLevel realm = CursedRealms.level(level.getServer());
            BlockPos o = arena.origin;
            AABB box = new AABB(o).inflate(CursedRealms.RADIUS, CursedRealms.HEIGHT, CursedRealms.RADIUS);
            h.assertTrue(realm.noCollision(p, p.getBoundingBox()), room + ": not inside anything");
            // Lit: the block light where you stand (the lanterns and the fill), not just the realm's dim ambience.
            // (At the cliff's landing: they are still falling in from above.)
            var planHere = RealmRooms.plan(room);
            BlockPos stand = planHere.marker("landing") != null ? BlockPos.containing(new Vec3(o.getX(), o.getY(), o.getZ()).add(planHere.marker("landing"))).above()
                    : p.blockPosition().above();
            int lit = realm.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, stand);
            h.assertTrue(lit >= 8, room + ": lit where you stand (block light " + lit + ")");
            List<TrialPropEntity> props = realm.getEntitiesOfClass(TrialPropEntity.class, box, x -> true);
            h.assertTrue(props.size() == 1 && props.getFirst().model().equals("realmb_" + room), room + ": its environment model, once");
            List<Mob> curses = realm.getEntitiesOfClass(Mob.class, box, Mob::isAlive);
            h.assertTrue(!curses.isEmpty(), room + ": its curses are there");
            for (Mob m : curses) h.assertTrue(realm.noCollision(m, m.getBoundingBox()), room + ": " + m.getType().toShortString() + " not inside anything");
            CursedRealms.close(level.getServer(), st, arena, false);
            h.assertTrue(realm.getEntitiesOfClass(TrialPropEntity.class, box, x -> true).isEmpty(), room + ": nothing left behind");
            h.assertTrue(realm.getBlockState(o.below()).isAir(), room + ": its blocks are cleared (collision too)");
            visit(h, p, st, templates, i + 1);
        });
    }
}
