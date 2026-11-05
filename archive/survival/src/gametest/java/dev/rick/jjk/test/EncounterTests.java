package dev.rick.jjk.test;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.progression.ProgressionItems;
import dev.rick.jjk.progression.curse.CommonCurseEntity;
import dev.rick.jjk.progression.curse.SchoolCrawlerEntity;
import dev.rick.jjk.progression.investigation.CursedBreachEntity;
import dev.rick.jjk.progression.investigation.CursedRealms;
import dev.rick.jjk.progression.investigation.EncounterWatch;
import dev.rick.jjk.progression.investigation.Incident;
import dev.rick.jjk.progression.investigation.IncidentTemplate;
import dev.rick.jjk.progression.investigation.InvestigationState;
import dev.rick.jjk.progression.investigation.Investigations;
import dev.rick.jjk.progression.investigation.Sites;
import dev.rick.jjk.progression.mastery.Mastery;
import dev.rick.jjk.registry.ModEntities;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Realm fights can always be finished, and never trap anyone: the last curse falling releases everyone once (and pays
 * once); a curse that vanishes without dying, or a record of one that never made it into the world, doesn't block the
 * win; two fights at once never count each other's curses; the last curses are revealed after a quiet spell; a curse
 * stuck in terrain or caged where nobody can reach it is brought back (same curse, same health, safe ground away from
 * the players); a stalled fight opens an Emergency Return that must be channelled unhurt and pays nothing; curses'
 * health scales with the players fighting them; and curses turn on whoever hits them and can't be stagger-locked.
 */
public class EncounterTests {
    private static void floor(GameTestHelper h) {
        JJKConfig.get().general.autoAssignGojo = false;
        JJKConfig.get().progression.enabled = true;
        JJKConfig.get().mastery.enabled = true;
        for (int x = 0; x < 10; x++) for (int z = 0; z < 10; z++) h.setBlock(x, 0, z, Blocks.STONE);
    }

    private static ServerPlayer survivor(GameTestHelper h, double x, double z) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.getAbilities().invulnerable = false;
        p.setPos(h.absoluteVec(new Vec3(x, 1, z)));
        p.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ProgressionItems.CURSED_GLASSES));
        return p;
    }

    private static Incident incident(GameTestHelper h, String template, int x, int z) {
        ServerLevel level = h.getLevel();
        InvestigationState st = InvestigationState.get(level.getServer());
        InvestigationState.Village v = Investigations.village(level, h.absolutePos(new BlockPos(-40, 1, -40)));
        return Investigations.create(level, v, st, IncidentTemplate.get(template), new Sites.Site(h.absolutePos(new BlockPos(x, 1, z)), 1, 0),
                level.getGameTime(), RandomSource.create(1));
    }

    /** A player taken straight into an incident's realm. */
    private static InvestigationState.Arena enter(GameTestHelper h, ServerPlayer p, Incident in) {
        MinecraftServer server = h.getLevel().getServer();
        InvestigationState st = InvestigationState.get(server);
        h.assertTrue(CursedRealms.enter(p, in, st), "into the realm");
        InvestigationState.Arena a = EncounterWatch.arenaForTest(server, in.id);
        h.assertTrue(a != null, "its arena is open");
        return a;
    }

    private static List<CommonCurseEntity> curses(MinecraftServer server, Incident in) {
        ServerLevel realm = CursedRealms.level(server);
        List<CommonCurseEntity> out = new ArrayList<>();
        for (UUID id : in.curses()) if (realm.getEntity(id) instanceof CommonCurseEntity c) out.add(c);
        return out;
    }

    /** Every curse on the fight's record is loaded (a new arena's ground takes a moment to load its entities). */
    private static void loaded(GameTestHelper h, MinecraftServer server, Incident in) {
        h.assertTrue(!in.curses().isEmpty() && curses(server, in).size() == in.curses().size(), "the fight's curses are loaded");
    }

    private static void slay(CommonCurseEntity c) {
        ServerLevel level = (ServerLevel) c.level();
        c.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
    }

    /** Leaves one awake curse of the fight (the rest gone without a trace), held still. */
    private static CommonCurseEntity leaveOne(MinecraftServer server, Incident in) {
        List<CommonCurseEntity> all = curses(server, in);
        CommonCurseEntity keep = null;
        for (CommonCurseEntity c : all) if (keep == null && !c.dormant()) keep = c;
        for (CommonCurseEntity c : all) if (c != keep) c.discard();
        keep.setNoAi(true);
        return keep;
    }

    private static void closeIfOpen(GameTestHelper h, Incident in) {
        MinecraftServer server = h.getLevel().getServer();
        InvestigationState.Arena a = EncounterWatch.arenaForTest(server, in.id);
        if (a != null) CursedRealms.close(server, InvestigationState.get(server), a, false);
    }

    private static int incidentsSeen(ServerPlayer p) {
        return Mastery.data(p).incidents().values().stream().mapToInt(Integer::intValue).sum();
    }

    @GameTest(maxTicks = 300, environment = "jjk-test:enc_a")
    public void theLastCurseFallingReleasesEveryoneAndPaysOnce(GameTestHelper h) {
        floor(h);
        MinecraftServer server = h.getLevel().getServer();
        Incident in = incident(h, "old_mine", 5, 5);
        ServerPlayer p = survivor(h, 5.5, 5.5);
        int seen = incidentsSeen(p);
        enter(h, p, in);
        h.startSequence()
                .thenWaitUntil(() -> loaded(h, server, in))
                .thenExecute(() -> {
                    for (CommonCurseEntity c : curses(server, in)) slay(c);
                })
                .thenWaitUntil(() -> h.assertTrue(in.state() == Incident.State.COMPLETE, "the last one falling wins it"))
                .thenExecute(() -> {
                    EncounterWatch.completeAgainForTest(server, in);
                    h.assertTrue(incidentsSeen(p) == seen + 1, "paid once, even when the win is reported twice: " + (incidentsSeen(p) - seen));
                })
                .thenWaitUntil(() -> h.assertTrue(!CursedRealms.inRealm(p), "and a moment later they are home"))
                .thenExecute(() -> h.assertTrue(EncounterWatch.arenaForTest(server, in.id) == null, "the arena is gone"))
                .thenSucceed();
    }

    @GameTest(maxTicks = 300, environment = "jjk-test:enc_b")
    public void cursesThatVanishWithoutDyingDontBlockTheWin(GameTestHelper h) {
        floor(h);
        MinecraftServer server = h.getLevel().getServer();
        Incident in = incident(h, "old_mine", 5, 5);
        ServerPlayer p = survivor(h, 5.5, 5.5);
        enter(h, p, in);
        h.startSequence()
                .thenWaitUntil(() -> loaded(h, server, in))
                // Removed, not killed (a command, a mod, a glitch): no death event ever comes.
                .thenExecute(() -> {
                    for (CommonCurseEntity c : curses(server, in)) c.discard();
                })
                .thenWaitUntil(() -> h.assertTrue(in.state() == Incident.State.COMPLETE, "its records go with them, and the fight is won"))
                .thenWaitUntil(() -> h.assertTrue(!CursedRealms.inRealm(p), "nobody is left behind"))
                .thenSucceed();
    }

    @GameTest(maxTicks = 400, environment = "jjk-test:enc_c")
    public void aRecordOfACurseThatNeverAppearedIsDroppedAsStale(GameTestHelper h) {
        floor(h);
        MinecraftServer server = h.getLevel().getServer();
        Incident in = incident(h, "old_mine", 5, 5);
        ServerPlayer p = survivor(h, 5.5, 5.5);
        enter(h, p, in);
        UUID ghost = UUID.randomUUID();
        // A spawn that failed, or a record that outlived its curse.
        h.startSequence()
                .thenWaitUntil(() -> loaded(h, server, in))
                .thenExecute(() -> {
                    EncounterWatch.trackForTest(in, ghost);
                    for (CommonCurseEntity c : curses(server, in)) slay(c);
                })
                .thenIdle(10)
                .thenExecute(() -> h.assertTrue(in.state() == Incident.State.ACTIVE && in.curses().contains(ghost), "not dropped at once (it might only be unloaded)"))
                .thenWaitUntil(() -> h.assertTrue(in.state() == Incident.State.COMPLETE, "missing from loaded ground check after check: dropped, and won"))
                .thenExecute(() -> h.assertTrue(!in.curses().contains(ghost), "the stale record is gone"))
                .thenWaitUntil(() -> h.assertTrue(!CursedRealms.inRealm(p), "home"))
                .thenSucceed();
    }

    @GameTest(maxTicks = 300, environment = "jjk-test:enc_d")
    public void twoFightsAtOnceNeverCountEachOthersCurses(GameTestHelper h) {
        floor(h);
        MinecraftServer server = h.getLevel().getServer();
        Incident one = incident(h, "old_mine", 3, 3);
        Incident two = incident(h, "old_mine", 7, 7);
        ServerPlayer p = survivor(h, 3.5, 3.5), q = survivor(h, 7.5, 7.5);
        InvestigationState.Arena a = enter(h, p, one), b = enter(h, q, two);
        h.assertTrue(a != b && !a.encounter.equals(b.encounter) && a.origin.distManhattan(b.origin) > 100, "two arenas, two encounters, far apart");
        int theirs = two.curses().size();
        h.startSequence()
                .thenWaitUntil(() -> loaded(h, server, one))
                .thenExecute(() -> {
                    for (CommonCurseEntity c : curses(server, one)) slay(c);
                })
                .thenWaitUntil(() -> h.assertTrue(one.state() == Incident.State.COMPLETE, "the first fight is won"))
                .thenExecute(() -> {
                    h.assertTrue(two.state() == Incident.State.ACTIVE && two.curses().size() == theirs, "the other goes on, its curses all its own");
                    h.assertTrue(CursedRealms.inRealm(q), "and its player is still in it");
                })
                .thenWaitUntil(() -> h.assertTrue(!CursedRealms.inRealm(p), "the winner goes home"))
                .thenExecute(() -> {
                    h.assertTrue(CursedRealms.inRealm(q), "the other player stays");
                    closeIfOpen(h, two);
                })
                .thenSucceed();
    }

    @GameTest(maxTicks = 200, environment = "jjk-test:enc_e")
    public void theLastCursesAreRevealedAfterAQuietSpell(GameTestHelper h) {
        floor(h);
        MinecraftServer server = h.getLevel().getServer();
        Incident in = incident(h, "old_mine", 5, 5);
        ServerPlayer p = survivor(h, 5.5, 5.5);
        InvestigationState.Arena a = enter(h, p, in);
        h.startSequence()
                .thenWaitUntil(() -> loaded(h, server, in))
                .thenExecute(() -> leaveOne(server, in))
                .thenIdle(25)
                .thenExecute(() -> {
                    h.assertTrue(!a.watch().revealed(), "not straight away: the fight may just be catching its breath");
                    EncounterWatch.ageForTest(a, JJKConfig.get().encounters.revealAfter + 5);
                })
                .thenWaitUntil(() -> h.assertTrue(a.watch().revealed(), "a few left and nothing happening: revealed"))
                .thenExecute(() -> {
                    h.assertTrue(EncounterWatch.remainingForTest(a) == 1, "one left");
                    closeIfOpen(h, in);
                })
                .thenSucceed();
    }

    @GameTest(maxTicks = 300, environment = "jjk-test:enc_f")
    public void aCurseStuckInsideTerrainIsBroughtBackWhole(GameTestHelper h) {
        floor(h);
        MinecraftServer server = h.getLevel().getServer();
        Incident in = incident(h, "old_mine", 5, 5);
        ServerPlayer p = survivor(h, 5.5, 5.5);
        InvestigationState.Arena a = enter(h, p, in);
        ServerLevel realm = CursedRealms.level(server);
        CommonCurseEntity[] one = new CommonCurseEntity[1];
        float[] health = new float[1];
        h.startSequence()
                .thenWaitUntil(() -> loaded(h, server, in))
                .thenExecute(() -> {
                    CommonCurseEntity c = one[0] = leaveOne(server, in);
                    // Buried in stone at the arena's edge.
                    BlockPos at = a.origin.offset(18, 3, 18);
                    for (BlockPos q : BlockPos.betweenClosed(at.offset(-2, -1, -2), at.offset(2, 3, 2))) realm.setBlockAndUpdate(q, Blocks.STONE.defaultBlockState());
                    c.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
                    c.hurtServer(realm, realm.damageSources().genericKill(), 3f);
                    health[0] = c.getHealth();
                    h.assertTrue(EncounterWatch.invalidForTest(a, c), "buried");
                })
                .thenWaitUntil(() -> h.assertTrue(!EncounterWatch.invalidForTest(a, one[0]), "brought out of the rock"))
                .thenExecute(() -> {
                    CommonCurseEntity c = one[0];
                    UUID id = c.getUUID();
                    float hp = health[0];
                    h.assertTrue(realm.getEntity(id) == c && c.isAlive(), "the same curse, not a new one");
                    h.assertTrue(Math.abs(c.getHealth() - hp) < 0.01f, "with the health it had");
                    h.assertTrue(in.curses().size() == 1 && in.curses().contains(id), "still the fight's one curse on record");
                    h.assertTrue(c.distanceTo(p) >= JJKConfig.get().encounters.recoverMinDistance - 0.01, "set down well away from the player: " + c.distanceTo(p));
                    h.assertTrue(realm.noCollision(c), "with room to stand");
                    closeIfOpen(h, in);
                })
                .thenSucceed();
    }

    @GameTest(maxTicks = 300, environment = "jjk-test:enc_g")
    public void aCurseCagedWhereNobodyCanReachItIsBroughtBackAfterTheReveal(GameTestHelper h) {
        floor(h);
        MinecraftServer server = h.getLevel().getServer();
        Incident in = incident(h, "old_mine", 5, 5);
        ServerPlayer p = survivor(h, 5.5, 5.5);
        InvestigationState.Arena a = enter(h, p, in);
        ServerLevel realm = CursedRealms.level(server);
        CommonCurseEntity[] one = new CommonCurseEntity[1];
        Vec3[] cage = new Vec3[1];
        h.startSequence()
                .thenWaitUntil(() -> loaded(h, server, in))
                .thenExecute(() -> {
                    CommonCurseEntity c = one[0] = leaveOne(server, in);
                    // A sealed glass cage on a pillar, far from the player: it isn't in a wall, it just can't get out.
                    BlockPos at = a.origin.offset(-16, 6, -16);
                    for (BlockPos q : BlockPos.betweenClosed(at.offset(-2, -1, -2), at.offset(2, 3, 2))) {
                        boolean shell = q.getX() == at.getX() - 2 || q.getX() == at.getX() + 2 || q.getZ() == at.getZ() - 2 || q.getZ() == at.getZ() + 2
                                || q.getY() == at.getY() - 1 || q.getY() == at.getY() + 3;
                        realm.setBlockAndUpdate(q, shell ? Blocks.GLASS.defaultBlockState() : Blocks.AIR.defaultBlockState());
                    }
                    c.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
                    cage[0] = c.position();
                })
                .thenIdle(5)
                .thenExecute(() -> EncounterWatch.ageForTest(a, JJKConfig.get().encounters.revealAfter + 5))
                .thenWaitUntil(() -> h.assertTrue(a.watch().revealed(), "revealed first"))
                .thenIdle(25)
                .thenExecute(() -> EncounterWatch.ageForTest(a, JJKConfig.get().encounters.recoverAfter + JJKConfig.get().encounters.stuckStillTicks + 5))
                .thenWaitUntil(() -> h.assertTrue(one[0].position().distanceTo(cage[0]) > 3, "then, still going nowhere and unable to reach anyone, brought back"))
                .thenExecute(() -> {
                    CommonCurseEntity c = one[0];
                    h.assertTrue(c.isAlive() && in.curses().contains(c.getUUID()), "the same curse, still counted");
                    h.assertTrue(c.distanceTo(p) >= JJKConfig.get().encounters.recoverMinDistance - 0.01, "not on top of the player");
                    closeIfOpen(h, in);
                })
                .thenSucceed();
    }

    @GameTest(maxTicks = 500, environment = "jjk-test:enc_h")
    public void aStalledFightOpensAnEmergencyReturnThatPaysNothing(GameTestHelper h) {
        floor(h);
        MinecraftServer server = h.getLevel().getServer();
        Incident in = incident(h, "old_mine", 5, 5);
        ServerPlayer p = survivor(h, 5.5, 5.5);
        int seen = incidentsSeen(p);
        InvestigationState.Arena a = enter(h, p, in);
        CursedBreachEntity[] exit = new CursedBreachEntity[1];
        var c = JJKConfig.get().encounters;
        h.startSequence()
                .thenWaitUntil(() -> loaded(h, server, in))
                .thenExecute(() -> leaveOne(server, in))
                .thenIdle(5)
                .thenExecute(() -> EncounterWatch.ageForTest(a, c.revealAfter + 5))
                .thenWaitUntil(() -> h.assertTrue(a.watch().revealed(), "revealed"))
                .thenExecute(() -> h.assertTrue(!a.watch().emergency(), "no way out yet: the fight can still be finished"))
                .thenExecute(() -> EncounterWatch.ageForTest(a, c.emergencyAfter + c.recoverAfter))
                .thenWaitUntil(() -> h.assertTrue(a.watch().emergency(), "stalled long after the reveal: the Emergency Return opens"))
                .thenWaitUntil(() -> {
                    exit[0] = EncounterWatch.exitNear(p);
                    h.assertTrue(exit[0] != null && exit[0].breachState() == 2, "a pale breach beside them");
                })
                .thenExecute(() -> EncounterWatch.useExitForTest(p, exit[0]))
                .thenExecute(() -> h.assertTrue(a.watch().channelling(p.getUUID()), "channelling"))
                .thenIdle(10)
                .thenExecute(() -> p.hurtServer((ServerLevel) p.level(), p.level().damageSources().generic(), 1f))
                .thenIdle(2)
                .thenExecute(() -> {
                    h.assertTrue(!a.watch().channelling(p.getUUID()), "a hit breaks the channel");
                    h.assertTrue(a.watch().emergency() && CursedRealms.inRealm(p), "but the way out stays open");
                    EncounterWatch.useExitForTest(p, exit[0]);
                })
                .thenIdle(c.emergencyChannelTicks + 5)
                .thenExecute(() -> {
                    h.assertTrue(!CursedRealms.inRealm(p), "held still and unhurt: home");
                    h.assertTrue(in.state() != Incident.State.COMPLETE, "it is not a win");
                    h.assertTrue(!in.participants().contains(p.getUUID()) && incidentsSeen(p) == seen, "and pays nothing");
                    h.assertTrue(EncounterWatch.arenaForTest(server, in.id) == null && in.state() == Incident.State.OPEN, "nobody left: the fight closes, open to retry");
                })
                .thenSucceed();
    }

    @GameTest(maxTicks = 200, environment = "jjk-test:enc_i")
    public void moreFightersMeanTougherCursesWithinACap(GameTestHelper h) {
        floor(h);
        MinecraftServer server = h.getLevel().getServer();
        Incident in = incident(h, "old_mine", 5, 5);
        ServerPlayer p = survivor(h, 4.5, 5.5), q = survivor(h, 6.5, 5.5);
        enter(h, p, in);
        CommonCurseEntity[] one = new CommonCurseEntity[1];
        float[] alone = new float[1];
        var t = JJKConfig.get().curses;
        h.startSequence()
                .thenWaitUntil(() -> loaded(h, server, in))
                .thenIdle(25)
                .thenExecute(() -> {
                    one[0] = curses(server, in).get(0);
                    alone[0] = one[0].getMaxHealth();
                    enter(h, q, in);
                })
                .thenIdle(25)
                .thenExecute(() -> {
                    CommonCurseEntity c = one[0];
                    double want = Math.min(t.maxPlayerScaling, 1 + t.healthPerExtraPlayer);
                    h.assertTrue(Math.abs(c.playerScale() - want) < 1e-3, "two players: scaled by " + want + " (" + c.playerScale() + ")");
                    h.assertTrue(Math.abs(c.getMaxHealth() - alone[0] * want) < 0.5, "its max health with it: " + c.getMaxHealth() + " vs " + alone[0]);
                    closeIfOpen(h, in);
                })
                .thenSucceed();
    }

    @GameTest(maxTicks = 120, environment = "jjk-test:enc_j")
    public void aCurseTurnsOnWhoeverHitsItAndCantBeStaggerLocked(GameTestHelper h) {
        floor(h);
        ServerLevel level = h.getLevel();
        ServerPlayer p = survivor(h, 5, 1.5);
        // Only cursed means hurt a curse.
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ProgressionItems.SLAUGHTER_DEMON));
        SchoolCrawlerEntity crawler = ModEntities.SCHOOL_CRAWLER.create(level, EntitySpawnReason.MOB_SUMMONED);
        // Mid-floor (a stagger knocks it back a couple of blocks: it must land on the floor, not off the plot).
        Vec3 at = h.absoluteVec(new Vec3(5, 1, 4.5));
        crawler.snapTo(at.x, at.y, at.z, 0f, 0f);
        level.addFreshEntity(crawler);
        // Without glasses it would never pick them out itself...
        p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        crawler.setTarget(null);
        crawler.hurtServer(level, level.damageSources().playerAttack(p), 1f);
        h.assertTrue(crawler.getTarget() == p, "... but hit from out of nowhere, it turns on whoever did it");
        h.startSequence()
                .thenIdle(12)
                .thenExecute(() -> {
                    // Break its poise (it has no armour to soak the blow): it staggers.
                    crawler.setInvulnerableTime(0);
                    crawler.hurtServer(level, level.damageSources().playerAttack(p), crawler.poise() + 1);
                    h.assertTrue(crawler.isStaggered(), "staggered");
                })
                .thenWaitUntil(() -> h.assertTrue(!crawler.isStaggered(), "it comes out of it: action " + crawler.action() + " removed " + crawler.isRemoved()
                        + " alive " + crawler.isAlive() + " frozen " + crawler.isFrozen() + " at " + crawler.position() + " noAi " + crawler.isNoAi()))
                .thenExecute(() -> {
                    float before = crawler.getHealth();
                    crawler.setInvulnerableTime(0);
                    crawler.hurtServer(level, level.damageSources().playerAttack(p), crawler.poise() + 1);
                    h.assertTrue(crawler.getHealth() < before, "the next blow still hurts");
                    h.assertTrue(!crawler.isStaggered(), "but can't break it again straight away");
                    crawler.discard();
                })
                .thenSucceed();
    }
}
