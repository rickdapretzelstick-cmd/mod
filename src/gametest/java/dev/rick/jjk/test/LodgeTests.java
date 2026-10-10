package dev.rick.jjk.test;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.progression.ProgressionBlocks;
import dev.rick.jjk.progression.ProgressionItems;
import dev.rick.jjk.progression.curse.ForestStalkerEntity;
import dev.rick.jjk.progression.investigation.CursedRealms;
import dev.rick.jjk.progression.investigation.GunRackBlock;
import dev.rick.jjk.progression.investigation.Incident;
import dev.rick.jjk.progression.investigation.IncidentTemplate;
import dev.rick.jjk.progression.investigation.InvestigationState;
import dev.rick.jjk.progression.investigation.Investigations;
import dev.rick.jjk.progression.investigation.LodgeRewards;
import dev.rick.jjk.progression.investigation.LodgeScope;
import dev.rick.jjk.progression.investigation.Sites;
import dev.rick.jjk.progression.tool.rifle.RifleClaims;
import dev.rick.jjk.progression.tool.rifle.RifleServer;
import dev.rick.jjk.registry.ModEntities;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * The hunting lodge, end to end: the lodge is built with its rack (sealed), its scope and its traces; the scope shows
 * the anomaly only to someone who perceives curses, and only a deliberate second use goes in; the realm holds the
 * stalker, whose lunge is telegraphed and, dodged, leaves it exposed; defeating it owes every participant a rifle, kept
 * through a full inventory and a restart, claimable once each, never by anyone else, and recoverable without duplication.
 * One environment each (the investigation state is world-wide).
 */
public class LodgeTests {
    private static void floor(GameTestHelper h) {
        JJKConfig.get().general.autoAssignGojo = false;
        JJKConfig.get().progression.enabled = true;
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(x, 0, z, Blocks.STONE);
    }

    private static ServerPlayer player(GameTestHelper h, Vec3 at, boolean glasses) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.getAbilities().invulnerable = false;
        p.setPos(at.x, at.y, at.z);
        p.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        if (glasses) p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ProgressionItems.CURSED_GLASSES));
        return p;
    }

    /** A lodge incident in the test area (facing +X: its window looks east), built at once. */
    private static Incident lodge(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        InvestigationState st = InvestigationState.get(level.getServer());
        InvestigationState.Village v = Investigations.village(level, h.absolutePos(new BlockPos(-60, 1, 4)));
        Incident in = Investigations.create(level, v, st, IncidentTemplate.get("hunting_lodge"), new Sites.Site(h.absolutePos(new BlockPos(4, 1, 4)), 1, 0),
                level.getGameTime(), RandomSource.create(7));
        Investigations.buildForTest(level, in);
        return in;
    }

    private static void face(LivingEntity e, Vec3 target) {
        RifleTests.face(e, target);
    }

    private static int rifles(ServerPlayer p) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (RifleServer.isRifle(p.getInventory().getItem(i))) n++;
        return n;
    }

    @GameTest(maxTicks = 20, padding = 36, environment = "jjk-test:lodge_a")
    public void theLodgeIsBuiltWithItsRackScopeAndTraces(GameTestHelper h) {
        floor(h);
        Incident in = lodge(h);
        ServerLevel level = h.getLevel();
        for (String k : new String[] {"scope", "rack", "anomaly", "stand_a", "stand_b", "marks", "tracks"}) h.assertTrue(in.mark(k) != null, "marked: " + k);
        h.assertTrue(level.getBlockState(in.mark("scope")).is(ProgressionBlocks.MOUNTED_SCOPE), "the scope on the sill");
        var rack = level.getBlockState(in.mark("rack"));
        h.assertTrue(rack.is(ProgressionBlocks.GUN_RACK) && rack.getValue(GunRackBlock.RACK) == GunRackBlock.Rack.SEALED, "the rack, sealed");
        h.assertTrue(in.mark("anomaly").distSqr(in.site) > 20 * 20, "what the scope shows is well out in the trees");
        // The report reads as news and says where (the lodge's own wording, a direction).
        String all = (in.headline + " " + in.body).toLowerCase(java.util.Locale.ROOT);
        h.assertTrue(all.contains("lodge") && !all.contains("curse") && !all.contains("grade"), "news, not a quest: " + all);
        h.succeed();
    }

    @GameTest(maxTicks = 260, padding = 36, environment = "jjk-test:lodge_b")
    public void theScopeShowsTheAnomalyOnlyToThoseWhoPerceiveAndItsBreachGoesIn(GameTestHelper h) {
        floor(h);
        Incident in = lodge(h);
        BlockPos scope = in.mark("scope");
        Direction out = Direction.getApproximateNearest(in.dirX, 0, in.dirZ);
        Vec3 stand = Vec3.atBottomCenterOf(scope.relative(out.getOpposite()));
        ServerPlayer p = player(h, stand, false);
        Vec3 anomaly = Vec3.atCenterOf(in.mark("anomaly")).add(0, 1, 0);
        face(p, anomaly);
        LodgeScope.useForTest(p, scope);
        h.assertTrue(LodgeScope.looking(p), "an eye to the scope");
        h.assertTrue((in.clues(p.getUUID()) & Investigations.CLUE_SCOPE) != 0, "a clue noted");
        long[] withGlasses = {-1};
        h.onEachTick(() -> {
            face(p, anomaly);
            long t = h.getTick();
            if (withGlasses[0] < 0 && t >= 70) {
                h.assertTrue(!LodgeScope.revealed(p), "without perceiving curses, it never resolves");
                p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ProgressionItems.CURSED_GLASSES));
                withGlasses[0] = t;
            }
        });
        boolean[] went = new boolean[1];
        h.succeedWhen(() -> {
            if (!went[0]) {
                h.assertTrue(withGlasses[0] >= 0 && LodgeScope.revealed(p), "with the glasses, held on it, it resolves");
                h.assertTrue(h.getTick() - withGlasses[0] >= LodgeScope.HOLD_TICKS - 1, "only after holding it there");
                h.assertTrue(in.state() == Incident.State.OPEN, "seeing it changes nothing by itself");
                went[0] = true;
                LodgeScope.useForTest(p, scope);
                h.assertTrue(!CursedRealms.pulling(p), "the scope only shows the way: it takes nobody in");
                // Out where the trail ends, the breach: the one way in.
                BlockPos anchor = dev.rick.jjk.progression.investigation.CursedBreaches.anchor(in);
                h.assertTrue(anchor.equals(in.mark("anomaly")), "the breach is where the scope showed the figure");
                dev.rick.jjk.progression.investigation.CursedBreaches.ensure(h.getLevel(), in.id, anchor);
                var breach = dev.rick.jjk.progression.investigation.CursedBreaches.find(h.getLevel(), in.id, anchor);
                h.assertTrue(breach != null, "a breach hangs there");
                p.setPos(breach.position().add(1, 0, 0));
                dev.rick.jjk.progression.investigation.CursedBreaches.useForTest(p, breach);
                h.assertTrue(CursedRealms.pulling(p), "using it begins the pull");
            }
            h.assertTrue(CursedRealms.inRealm(p) && in.state() == Incident.State.ACTIVE, "through the breach");
            InvestigationState st = InvestigationState.get(h.getLevel().getServer());
            var arena = CursedRealms.arenaOf(st, in.id);
            h.assertTrue(arena != null && "forest_realm".equals(arena.layout), "into the distorted forest");
            Entity curse = in.curses().isEmpty() ? null : Investigations.curseEntity(h.getLevel().getServer(), in.curses().get(0));
            int stalkers = 0;
            for (Entity e : CursedRealms.level(h.getLevel().getServer()).getAllEntities()) if (e instanceof ForestStalkerEntity && !e.isRemoved()) stalkers++;
            h.assertTrue(curse instanceof ForestStalkerEntity, "the stalker waits there (" + in.curses().size() + " curses, " + stalkers + " stalkers loaded)");
            CursedRealms.close(h.getLevel().getServer(), st, arena, false);
        });
    }

    @GameTest(maxTicks = 400, padding = 36, environment = "jjk-test:lodge_c")
    public void defeatingItOwesEachParticipantTheirOwnRifle(GameTestHelper h) {
        floor(h);
        ServerLevel level = h.getLevel();
        Incident in = lodge(h);
        String id = in.id;
        BlockPos rack = in.mark("rack");
        ServerPlayer a = player(h, h.absoluteVec(new Vec3(4.5, 1, 4.5)), true);
        ServerPlayer b = player(h, h.absoluteVec(new Vec3(3.5, 1, 4.5)), true);
        Vec3 home = a.position();
        InvestigationState st = InvestigationState.get(level.getServer());
        CursedRealms.enter(a, in, st);
        h.assertTrue(CursedRealms.inRealm(a), "A went in");
        boolean[] checked = new boolean[1];
        h.succeedWhen(() -> {
            Incident now = InvestigationState.get(level.getServer()).incident(id);
            if (now != null && now.state() != Incident.State.COMPLETE) {
                // Exorcise whatever is still there (a curse whose chunk isn't ready yet is found on a later tick).
                for (UUID u : now.curses()) {
                    Entity e = Investigations.curseEntity(level.getServer(), u);
                    if (e instanceof LivingEntity le && le.isAlive()) le.hurtServer((ServerLevel) le.level(), le.damageSources().genericKill(), 10_000f);
                }
                h.fail("exorcising");
            }
            h.assertTrue(!CursedRealms.inRealm(a) && a.level().dimension() == Level.OVERWORLD && a.position().distanceTo(home) < 3, "A is back at the lodge");
            if (!checked[0]) {
                checked[0] = true;
                InvestigationState s = InvestigationState.get(level.getServer());
                Incident i = s.incident(id);
                h.assertTrue(i.state() == Incident.State.COMPLETE, "complete");
                h.assertTrue(i.rewardsPending().contains(a.getUUID()) && !i.rewardsPending().contains(b.getUUID()), "A is owed a rifle, B (who never went) isn't");
                h.assertTrue(level.getBlockState(rack).getValue(GunRackBlock.RACK) == GunRackBlock.Rack.OPEN, "the seal is broken: the rifle on the rack");
                // A restart keeps what's owed.
                s.markDirtyForTest();
                s.flush();
                InvestigationState.unload();
                i = InvestigationState.get(level.getServer()).incident(id);
                h.assertTrue(i != null && i.rewardsPending().contains(a.getUUID()) && i.state() == Incident.State.COMPLETE, "owed through a restart");
                // Not B's to take.
                LodgeRewards.useRack(b, level, rack);
                h.assertTrue(rifles(b) == 0, "nobody else can take it");
                // A full inventory leaves it waiting.
                for (int k = 0; k < 36; k++) a.getInventory().setItem(k, new ItemStack(Items.COBBLESTONE));
                LodgeRewards.useRack(a, level, rack);
                h.assertTrue(rifles(a) == 0 && i.rewardsPending().contains(a.getUUID()), "no room: still owed");
                a.getInventory().setItem(5, ItemStack.EMPTY);
                // Tests share one world: nobody else's rifle claim counts here (the rifle is one per world).
                InvestigationState.Claims.clearForTest(InvestigationState.get(level.getServer()));
                LodgeRewards.useRack(a, level, rack);
                h.assertTrue(rifles(a) == 1 && !i.rewardsPending().contains(a.getUUID()) && i.rewardsClaimed().contains(a.getUUID()), "claimed, once");
                h.assertTrue(level.getBlockState(rack).getValue(GunRackBlock.RACK) == GunRackBlock.Rack.EMPTY, "the rack is empty now");
                LodgeRewards.useRack(a, level, rack);
                h.assertTrue(rifles(a) == 1, "a second use gives nothing more");
                // Lost it: the rack gives a new one, and the old one goes cold. Never two.
                ItemStack lost = a.getInventory().getItem(5).copy();
                a.getInventory().setItem(5, ItemStack.EMPTY);
                LodgeRewards.useRack(a, level, rack);
                h.assertTrue(rifles(a) == 1, "recovered");
                h.assertTrue(RifleClaims.inertReason(a, lost) != null, "the lost one no longer works");
                h.assertTrue(RifleClaims.inertReason(a, a.getInventory().getItem(5)) == null, "the recovered one does");
            }
        });
    }

    @GameTest(maxTicks = 200, environment = "jjk-test:lodge_d")
    public void theStalkerTelegraphsItsLungeAndIsExposedWhenDodged(GameTestHelper h) {
        floor(h);
        ServerPlayer p = player(h, h.absoluteVec(new Vec3(1.5, 1, 1.5)), true);
        ForestStalkerEntity s = h.spawn(ModEntities.FOREST_STALKER, new Vec3(1.5, 1, 6.5));
        s.setPersistenceRequired();
        float hp = p.getHealth();
        s.forceTelegraphForTest(p);
        h.assertTrue(s.telegraphing(), "it shows itself and holds still first");
        boolean[] dodged = new boolean[1];
        h.onEachTick(() -> {
            if (!dodged[0] && s.actionTicksForTest() >= ForestStalkerEntity.LOCK_AT + 2 && s.telegraphing()) {
                // Sidestep after it has locked its line.
                Vec3 to = h.absoluteVec(new Vec3(5.5, 1, 1.5));
                p.teleportTo(to.x, to.y, to.z);
                dodged[0] = true;
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(dodged[0] && s.exposed(), "dodged: it overshoots and lies exposed");
            h.assertTrue(p.getHealth() == hp, "and the lunge never touched them");
            s.discard();
        });
    }
}
