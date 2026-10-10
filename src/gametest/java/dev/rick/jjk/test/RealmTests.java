package dev.rick.jjk.test;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.net.CompassPayload;
import dev.rick.jjk.progression.ProgressionItems;
import dev.rick.jjk.progression.curse.CommonCurseEntity;
import dev.rick.jjk.progression.investigation.CursedCompass;
import dev.rick.jjk.progression.investigation.CursedRealms;
import dev.rick.jjk.progression.investigation.Incident;
import dev.rick.jjk.progression.investigation.IncidentTemplate;
import dev.rick.jjk.progression.investigation.InvestigationState;
import dev.rick.jjk.progression.investigation.Investigations;
import dev.rick.jjk.progression.investigation.Sites;
import dev.rick.jjk.registry.ModAttachments;
import dev.rick.jjk.registry.ModEntities;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Cursed realms as the only place cursed events are fought: setting one off is a held, dark transition (nothing hurts you
 * meanwhile, the cliff's fall never lands) before the realm; the way back is safe ground near where you were; the pasture
 * fight happens in its own realm, never in the field; an incident's curse found outside its arena is removed; the empty
 * house is set off by lying in its bed; the Cursed Compass reads the investigation chosen at the board.
 */
public class RealmTests {
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

    private static Incident incident(GameTestHelper h, String template, BlockPos site, int dx, int dz) {
        ServerLevel level = h.getLevel();
        InvestigationState st = InvestigationState.get(level.getServer());
        InvestigationState.Village v = Investigations.village(level, h.absolutePos(new BlockPos(-40, 1, -40)));
        return Investigations.create(level, v, st, IncidentTemplate.get(template), new Sites.Site(h.absolutePos(site), dx, dz), level.getGameTime(),
                RandomSource.create(1));
    }

    private static void closeAll(GameTestHelper h, Incident in) {
        InvestigationState st = InvestigationState.get(h.getLevel().getServer());
        var a = CursedRealms.arenaOf(st, in.id);
        if (a != null) CursedRealms.close(h.getLevel().getServer(), st, a, false);
    }

    @GameTest(maxTicks = 200, environment = "jjk-test:realm_a")
    public void settingItOffHoldsYouInTheDarkThenTakesYouIn(GameTestHelper h) {
        floor(h);
        ServerLevel level = h.getLevel();
        Incident in = incident(h, "old_mine", new BlockPos(5, 1, 5), 1, 0);
        ServerPlayer p = survivor(h, 5.5, 1, 5.5);
        Vec3 held = p.position();
        InvestigationState st = InvestigationState.get(level.getServer());
        Investigations.begin(level, p, in, in.def(), st, level.getGameTime());
        h.assertTrue(CursedRealms.pulling(p) && !CursedRealms.inRealm(p), "held, not taken yet");
        int ticks = JJKConfig.get().realms.transitionTicks;
        h.runAfterDelay(ticks / 2, () -> {
            h.assertTrue(CursedRealms.pulling(p) && !CursedRealms.inRealm(p), "still in the transition halfway");
            h.assertTrue(p.position().distanceTo(held) < 0.1, "held still where it caught them");
            float hp = p.getHealth();
            p.hurtServer(level, level.damageSources().generic(), 6f);
            h.assertTrue(p.getHealth() == hp, "nothing hurts them meanwhile");
        });
        h.runAfterDelay(ticks + 4, () -> {
            h.assertTrue(!CursedRealms.pulling(p) && CursedRealms.inRealm(p), "taken in once it's over");
            h.assertTrue(in.state() == Incident.State.ACTIVE, "the incident is under way");
            closeAll(h, in);
            h.assertTrue(!CursedRealms.inRealm(p), "home");
            h.succeed();
        });
    }

    @GameTest(maxTicks = 200, environment = "jjk-test:realm_b")
    public void theCliffsFallNeverLandsAndTheWayBackIsSolidGround(GameTestHelper h) {
        floor(h);
        ServerLevel level = h.getLevel();
        Incident in = incident(h, "cliff_fall", new BlockPos(5, 1, 5), 1, 0);
        // Off the edge, falling.
        ServerPlayer p = survivor(h, 6.6, 6, 5.5);
        p.setOnGround(false);
        p.setDeltaMovement(new Vec3(0, -0.8, 0));
        p.fallDistance = 6;
        double y = p.getY();
        InvestigationState st = InvestigationState.get(level.getServer());
        Investigations.begin(level, p, in, in.def(), st, level.getGameTime());
        float hp = p.getHealth();
        h.runAfterDelay(10, () -> {
            h.assertTrue(Math.abs(p.getY() - y) < 0.1 && p.fallDistance == 0, "hanging where the fall was caught");
            h.assertTrue(p.getHealth() >= hp, "unhurt");
        });
        h.runAfterDelay(JJKConfig.get().realms.transitionTicks + 4, () -> {
            h.assertTrue(CursedRealms.inRealm(p), "into the cliff's realm");
            InvestigationState.Return r = st.returnOf(p.getUUID());
            h.assertTrue(r != null && r.pos().y < y - 1, "the way back is the ground, not the air it was caught in: " + (r == null ? null : r.pos()));
            h.assertTrue(r.pos().distanceTo(Vec3.atBottomCenterOf(in.site)) < 6, "near where it happened");
            closeAll(h, in);
            h.assertTrue(!CursedRealms.inRealm(p) && p.getY() < y - 1, "set down on solid ground");
            h.succeed();
        });
    }

    @GameTest(maxTicks = 100, environment = "jjk-test:realm_c")
    public void thePastureFightHappensInItsRealmNeverInTheField(GameTestHelper h) {
        floor(h);
        ServerLevel level = h.getLevel();
        Incident in = incident(h, "livestock", new BlockPos(5, 1, 5), 1, 0);
        ServerPlayer p = survivor(h, 5.5, 1, 5.5);
        InvestigationState st = InvestigationState.get(level.getServer());
        Investigations.begin(level, p, in, in.def(), st, level.getGameTime());
        CursedRealms.finishPullForTest(p);
        h.assertTrue(CursedRealms.inRealm(p), "pulled into a realm");
        var a = CursedRealms.arenaOf(st, in.id);
        h.assertTrue(a != null && "pasture_realm".equals(a.layout), "the pasture's own realm");
        h.assertTrue(!in.curses().isEmpty(), "its curses wait there");
        h.assertTrue(level.getEntitiesOfClass(CommonCurseEntity.class, new AABB(h.absolutePos(BlockPos.ZERO)).inflate(40), e -> true).isEmpty(),
                "none of them in the field");
        // A second player setting it off joins the same arena: one fight, not two.
        ServerPlayer q = survivor(h, 4.5, 1, 4.5);
        CursedRealms.enter(q, in, st);
        h.assertTrue(CursedRealms.arenaOf(st, in.id) == a && a.inside().contains(q.getUUID()), "the same arena");
        h.assertTrue(st.arenas().size() >= 1 && st.arenas().values().stream().filter(x -> x.incident.equals(in.id)).count() == 1, "never a duplicate");
        closeAll(h, in);
        h.succeed();
    }

    @GameTest(maxTicks = 120, environment = "jjk-test:realm_d")
    public void anIncidentsCurseOutsideItsArenaIsRemoved(GameTestHelper h) {
        floor(h);
        Incident in = incident(h, "livestock", new BlockPos(5, 1, 5), 1, 0);
        CommonCurseEntity c = ModEntities.FLY_HEAD.create(h.getLevel(), EntitySpawnReason.EVENT);
        Vec3 at = h.absoluteVec(new Vec3(5, 1, 5));
        c.snapTo(at.x, at.y, at.z, 0, 0);
        c.bind(in.id, BlockPos.containing(at), 8);
        h.getLevel().addFreshEntity(c);
        h.succeedWhen(() -> h.assertTrue(c.isRemoved(), "a leftover in the overworld goes"));
    }

    @GameTest(maxTicks = 60, environment = "jjk-test:realm_e")
    public void theEmptyHouseIsSetOffByLyingInItsBed(GameTestHelper h) {
        floor(h);
        ServerLevel level = h.getLevel();
        Incident in = incident(h, "empty_house", new BlockPos(5, 1, 5), 1, 0);
        Investigations.buildForTest(level, in);
        BlockPos bed = in.mark("bed"), door = in.mark("door");
        h.assertTrue(bed != null && door != null, "the house has its door and its bed");
        h.assertTrue(level.getBlockState(bed).getBlock() instanceof net.minecraft.world.level.block.BedBlock, "a bed stands there");
        ServerPlayer p = survivor(h, 5.5, 1, 5.5);
        h.assertTrue(!Investigations.use(p, door.above(3)), "using anything else does nothing");
        h.assertTrue(Investigations.use(p, bed), "lying down in it does");
        h.assertTrue(CursedRealms.pulling(p), "and the pull begins");
        CursedRealms.finishPullForTest(p);
        InvestigationState st = InvestigationState.get(level.getServer());
        var a = CursedRealms.arenaOf(st, in.id);
        h.assertTrue(a != null && "house_realm".equals(a.layout), "into the house's realm");
        closeAll(h, in);
        h.succeed();
    }

    @GameTest(maxTicks = 40, environment = "jjk-test:realm_f")
    public void theCompassReadsTheChosenInvestigation(GameTestHelper h) {
        floor(h);
        ServerLevel level = h.getLevel();
        Incident in = incident(h, "old_mine", new BlockPos(5, 1, 5), 1, 0);
        ServerPlayer p = survivor(h, 2.5, 1, 2.5);
        p.removeAttached(ModAttachments.INVESTIGATING);
        h.assertTrue(CursedCompass.reading(p).state() == CompassPayload.DORMANT, "no investigation: dormant");
        CursedCompass.investigate(p, in.id);
        h.assertTrue(in.id.equals(p.getAttached(ModAttachments.INVESTIGATING)), "chosen at the board");
        CompassPayload r = CursedCompass.reading(p);
        BlockPos anchor = CursedCompass.anchor(in);
        h.assertTrue(r.state() == CompassPayload.TRAIL, "in the reported area: it has a trail");
        h.assertTrue(new Vec3(r.x(), r.y(), r.z()).distanceTo(Vec3.atCenterOf(anchor)) < 0.01, "to the exact anchor (the mine's end)");
        h.assertTrue(!anchor.equals(in.site), "which is not where the report says (the mouth)");
        Vec3 far = p.position().add(JJKConfig.get().realms.compassRange + 40, 0, 0);
        Vec3 back = p.position();
        p.setPos(far.x, far.y, far.z);
        CompassPayload f = CursedCompass.reading(p);
        p.setPos(back.x, back.y, back.z);
        h.assertTrue(f.state() == CompassPayload.FAINT && f.x() == 0 && f.z() == 0, "far away it only wanders, and learns nothing");
        CursedCompass.investigate(p, in.id);
        h.assertTrue(p.getAttached(ModAttachments.INVESTIGATING) == null, "choosing it again sets it aside");
        h.succeed();
    }
}
