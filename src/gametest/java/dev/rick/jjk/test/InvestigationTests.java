package dev.rick.jjk.test;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.net.NewsBoardPayload;
import dev.rick.jjk.progression.ProgressionBlocks;
import dev.rick.jjk.progression.ProgressionItems;
import dev.rick.jjk.progression.grade.CurseGrade;
import dev.rick.jjk.progression.investigation.CursedRealms;
import dev.rick.jjk.progression.investigation.Incident;
import dev.rick.jjk.progression.investigation.IncidentTemplate;
import dev.rick.jjk.progression.investigation.InvestigationState;
import dev.rick.jjk.progression.investigation.Investigations;
import dev.rick.jjk.progression.investigation.ReportWriter;
import dev.rick.jjk.progression.investigation.Sites;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Investigations: reports read like local news (no curse, no grade, no coordinates) and their wording follows the
 * grade; the board goes up by the bell and shows the village's notices, then a follow-up once it's over; each trigger
 * fires only on the right action at the right spot; the cliff incident pulls the player into a cursed realm where its
 * curses wait, and exorcising them completes it, sends everyone home and clears the arena; a realm nobody stays in
 * closes and the incident can be tried again. One environment each (the investigation state is world-wide).
 */
public class InvestigationTests {
    private static void floor(GameTestHelper h) {
        JJKConfig.get().general.autoAssignGojo = false;
        JJKConfig.get().progression.enabled = true;
        for (int x = 0; x < 10; x++) for (int z = 0; z < 10; z++) h.setBlock(x, 0, z, Blocks.STONE);
    }

    private static ServerPlayer survivor(GameTestHelper h, double x, double y, double z) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.getAbilities().invulnerable = false;
        p.setPos(h.absoluteVec(new Vec3(x, y, z)));
        p.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ProgressionItems.CURSED_GLASSES));
        return p;
    }

    /** An incident of {@code template} at a spot in the test area, reported at a "bell" a little way off. */
    private static Incident incident(GameTestHelper h, String template, BlockPos site, int dx, int dz) {
        ServerLevel level = h.getLevel();
        InvestigationState st = InvestigationState.get(level.getServer());
        InvestigationState.Village v = Investigations.village(level, h.absolutePos(new BlockPos(-40, 1, -40)));
        return Investigations.create(level, v, st, IncidentTemplate.get(template), new Sites.Site(h.absolutePos(site), dx, dz), level.getGameTime(),
                RandomSource.create(1));
    }

    @GameTest(environment = "jjk-test:inv_a")
    public void reportsReadLikeLocalNews(GameTestHelper h) {
        RandomSource r = RandomSource.create(5);
        BlockPos village = BlockPos.ZERO;
        for (IncidentTemplate t : IncidentTemplate.all()) {
            for (CurseGrade g : CurseGrade.values()) {
                for (int i = 0; i < 6; i++) {
                    BlockPos site = new BlockPos(r.nextInt(600) - 300, 70, r.nextInt(600) - 300);
                    ReportWriter.Written w = ReportWriter.write(t, g, village, site, Sites.landmark(t.site()), r);
                    String all = (w.headline() + " " + w.body()).toLowerCase(Locale.ROOT);
                    for (String banned : List.of("curse", "grade", "exorcis", "sorcerer", "quest", "{", "}", "kill", "!")) {
                        h.assertTrue(!all.contains(banned), t.id() + " report mentions '" + banned + "': " + all);
                    }
                    h.assertTrue(all.contains(ReportWriter.direction(village, site)), "says roughly where: " + all);
                    h.assertTrue(!all.matches(".*-?\\d{2,}.*"), "no coordinates: " + all);
                }
            }
        }
        // The wording grows more urgent with the grade.
        IncidentTemplate t = IncidentTemplate.get("livestock");
        String mild = ReportWriter.write(t, CurseGrade.GRADE_4, village, new BlockPos(100, 70, 0), "x", RandomSource.create(2)).body();
        String dire = ReportWriter.write(t, CurseGrade.GRADE_1, village, new BlockPos(100, 70, 0), "x", RandomSource.create(2)).body();
        h.assertTrue(!mild.equals(dire) && dire.contains("bell"), "a worse curse sounds worse");
        h.assertTrue(ReportWriter.direction(BlockPos.ZERO, new BlockPos(0, 0, -50)).equals("north")
                && ReportWriter.direction(BlockPos.ZERO, new BlockPos(50, 0, -50)).equals("northeast")
                && ReportWriter.direction(BlockPos.ZERO, new BlockPos(-50, 0, 50)).equals("southwest"), "compass");
        h.succeed();
    }

    @GameTest(environment = "jjk-test:inv_b")
    public void theBoardGoesUpByTheBellAndShowsTheNotices(GameTestHelper h) {
        floor(h);
        ServerLevel level = h.getLevel();
        BlockPos bell = h.absolutePos(new BlockPos(5, 1, 5));
        BlockPos board = Investigations.placeBoard(level, bell);
        h.assertTrue(board != null && level.getBlockState(board).is(ProgressionBlocks.NEWS_BOARD), "a board by the bell");
        h.assertTrue(board.distManhattan(bell) <= 5, "close to it");
        var facing = level.getBlockState(board).getValue(HorizontalDirectionalBlock.FACING);
        h.assertTrue(board.relative(facing).distSqr(bell) < board.distSqr(bell), "facing the bell");
        InvestigationState st = InvestigationState.get(level.getServer());
        InvestigationState.Village v = Investigations.village(level, bell);
        Incident in = Investigations.create(level, v, st, IncidentTemplate.get("livestock"),
                new Sites.Site(h.absolutePos(new BlockPos(2, 1, 2)), 1, 0), level.getGameTime(), RandomSource.create(3));
        List<NewsBoardPayload.Note> notes = Investigations.notes(v, st, level.getGameTime());
        h.assertTrue(notes.stream().anyMatch(n -> n.headline().equals(in.headline) && n.status() == 0), "the report is pinned up");
        h.succeed();
    }

    @GameTest(maxTicks = 20, environment = "jjk-test:inv_c")
    public void triggersFireOnlyOnTheRightAction(GameTestHelper h) {
        floor(h);
        Incident cliff = incident(h, "cliff_fall", new BlockPos(5, 1, 5), 1, 0);
        IncidentTemplate t = cliff.def();
        ServerPlayer p = survivor(h, 5.5, 1, 5.5);
        p.setOnGround(true);
        p.setDeltaMovement(Vec3.ZERO);
        h.assertTrue(!Investigations.fired(p, cliff, t), "standing at the edge does nothing");
        // Off the edge, falling, past where it happened.
        Vec3 at = h.absoluteVec(new Vec3(6.6, -1.5, 5.5));
        p.setPos(at.x, at.y, at.z);
        p.setOnGround(false);
        p.setDeltaMovement(new Vec3(0, -0.6, 0));
        h.assertTrue(Investigations.fired(p, cliff, t), "jumping from it does");
        Vec3 far = h.absoluteVec(new Vec3(5.5, -1.5, 15));
        p.setPos(far.x, far.y, far.z);
        h.assertTrue(!Investigations.fired(p, cliff, t), "falling somewhere else doesn't");
        Incident pasture = incident(h, "livestock", new BlockPos(5, 1, 5), 1, 0);
        Vec3 near = h.absoluteVec(new Vec3(8, 1, 8));
        p.setPos(near.x, near.y, near.z);
        h.assertTrue(Investigations.fired(p, pasture, pasture.def()), "walking into the pasture");
        h.succeed();
    }

    @GameTest(maxTicks = 400, environment = "jjk-test:inv_d")
    public void cliffFallPullsIntoARealmAndExorcisingItBringsThemHome(GameTestHelper h) {
        floor(h);
        ServerLevel level = h.getLevel();
        Incident in = incident(h, "cliff_fall", new BlockPos(5, 1, 5), 1, 0);
        ServerPlayer p = survivor(h, 6.5, 1, 5.5);
        Vec3 start = p.position();
        InvestigationState st = InvestigationState.get(level.getServer());
        Investigations.begin(level, p, in, in.def(), st, level.getGameTime());
        h.assertTrue(CursedRealms.inRealm(p), "pulled into the realm");
        h.assertTrue(in.state() == Incident.State.ACTIVE, "the incident is under way");
        var arena = CursedRealms.arenaOf(st, in.id);
        h.assertTrue(arena != null && arena.inside().contains(p.getUUID()), "in its arena");
        int expected = 0;
        for (var cs : in.def().curses()) expected += cs.min();
        h.assertTrue(in.curses().size() >= expected, "its curses wait there: " + in.curses().size());
        BlockPos origin = arena.origin;
        ServerLevel realm = CursedRealms.level(level.getServer());
        h.assertTrue(!realm.getBlockState(origin).isAir(), "the arena was built");
        boolean[] killed = new boolean[1];
        h.succeedWhen(() -> {
            if (!killed[0]) {
                killed[0] = true;
                for (UUID id : in.curses()) {
                    Entity e = Investigations.curseEntity(level.getServer(), id);
                    if (e instanceof LivingEntity le) le.hurtServer((ServerLevel) le.level(), le.damageSources().genericKill(), 10_000f);
                }
                h.fail("exorcising");
            }
            h.assertTrue(in.state() == Incident.State.COMPLETE, "complete once the last falls");
            h.assertTrue(!CursedRealms.inRealm(p) && p.level().dimension() == Level.OVERWORLD, "sent home");
            h.assertTrue(p.position().distanceTo(start) < 3, "where they came from");
            h.assertTrue(CursedRealms.arenaOf(st, in.id) == null && realm.getBlockState(origin).isAir(), "the arena is gone, void again");
            h.assertTrue(dev.rick.jjk.progression.mastery.Mastery.data(p).incidents().getOrDefault(in.grade.name(), 0) == 1, "recorded");
        });
    }

    @GameTest(maxTicks = 1500, environment = "jjk-test:inv_e")
    public void aRealmNobodyStaysInClosesAndTheIncidentCanBeTriedAgain(GameTestHelper h) {
        floor(h);
        ServerLevel level = h.getLevel();
        Incident in = incident(h, "old_mine", new BlockPos(5, 1, 5), 1, 0);
        ServerPlayer p = survivor(h, 5.5, 1, 5.5);
        InvestigationState st = InvestigationState.get(level.getServer());
        Investigations.begin(level, p, in, in.def(), st, level.getGameTime());
        h.assertTrue(CursedRealms.inRealm(p), "in");
        CursedRealms.sendHome(p, st);
        h.assertTrue(!CursedRealms.inRealm(p), "fled");
        h.succeedWhen(() -> {
            h.assertTrue(CursedRealms.arenaOf(st, in.id) == null, "the empty realm closes");
            h.assertTrue(in.state() == Incident.State.OPEN && in.curses().isEmpty(), "and the incident is open to try again");
        });
    }
}
