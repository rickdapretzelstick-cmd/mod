package dev.rick.jjk.test;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.progression.CursePerception;
import dev.rick.jjk.progression.KitOwnership;
import dev.rick.jjk.progression.ProgressionItems;
import dev.rick.jjk.progression.TechniqueProgression;
import dev.rick.jjk.progression.investigation.CursedRealms;
import dev.rick.jjk.progression.investigation.Incident;
import dev.rick.jjk.progression.investigation.IncidentTemplate;
import dev.rick.jjk.progression.investigation.InvestigationState;
import dev.rick.jjk.progression.investigation.Investigations;
import dev.rick.jjk.progression.investigation.Sites;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Investigation sites and arenas don't outlast or overlap each other, and sorcerers see curses: an incident's overworld
 * site stands (and is never taken down) while it is open, is taken down once it is complete (back to the original
 * terrain, leaving player changes and everything it never touched), also after a reload; new sites keep clear of
 * standing ones; two investigations at once each get their own realm arena, and one closing clears only its own; a kit
 * owner perceives curses without glasses, and only while they own it.
 */
public class SiteTests {
    private static final String ENV = "jjk-test:sites";

    private static ServerPlayer survivor(GameTestHelper h, double x, double z) {
        JJKConfig.get().general.autoAssignGojo = false;
        JJKConfig.get().progression.enabled = true;
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.getAbilities().instabuild = false;
        p.getAbilities().invulnerable = false;
        p.getAbilities().mayfly = false;
        p.setPos(h.absoluteVec(new Vec3(x, 1, z)));
        p.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        return p;
    }

    @GameTest(maxTicks = 400, padding = 24, environment = ENV)
    public void aCompletedSiteIsTakenDownAndNothingElseIs(GameTestHelper h) {
        JJKConfig.get().progression.enabled = true;
        ServerLevel level = h.getLevel();
        for (int x = -2; x < 16; x++) for (int z = -2; z < 16; z++) h.setBlock(x, 0, z, Blocks.STONE);
        // Something nearby the site never touches.
        h.setBlock(14, 1, 14, Blocks.GOLD_BLOCK);
        InvestigationState st = InvestigationState.get(level.getServer());
        InvestigationState.Village v = Investigations.village(level, h.absolutePos(new BlockPos(-90, 1, -90)));
        Incident built = Investigations.create(level, v, st, IncidentTemplate.get("empty_house"), new Sites.Site(h.absolutePos(new BlockPos(6, 1, 6)), 1, 0),
                level.getGameTime(), RandomSource.create(5));
        BlockPos centre = Sites.surfaceForTest(level, built.site);
        net.minecraft.world.level.block.state.BlockState[] groundBefore = {level.getBlockState(centre.below())};
        Investigations.buildForTest(level, built);
        BlockPos bed = built.mark("bed");
        h.assertTrue(bed != null && level.getBlockState(bed).getBlock() instanceof BedBlock, "the house stands, its bed made");
        h.assertTrue(built.siteBlocksLeft() > 50, "what it changed is recorded: " + built.siteBlocksLeft());
        // A player's own block, put in place of one of the house's walls.
        // The house's floor level, from its door (facing +x, three blocks out from its centre).
        BlockPos base = built.mark("door").offset(-3, 0, 0);
        BlockPos wall = base.offset(-3, 0, 1);
        BlockPos otherWall = base.offset(-3, 0, -1);
        h.assertTrue(!level.getBlockState(wall).isAir() && !level.getBlockState(otherWall).isAir(), "its walls stand");
        level.setBlock(wall, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
        Incident[] in = {built};
        h.startSequence()
                // Open: it stays, however long.
                .thenIdle(40)
                .thenExecute(() -> {
                    h.assertTrue(level.getBlockState(bed).getBlock() instanceof BedBlock && !in[0].siteCleared(), "still standing while it is open");
                    // A restart: the record comes back from disk, and completing it takes the site down.
                    in[0] = Investigations.reloadForTest(level.getServer(), in[0]);
                    h.assertTrue(in[0].siteBlocksLeft() > 50, "the record survives a reload");
                    Investigations.completeForTest(level.getServer(), in[0]);
                })
                .thenWaitUntil(() -> h.assertTrue(in[0].siteCleared(), "taken down (" + in[0].siteBlocksLeft() + " left)"))
                .thenExecute(() -> {
                    h.assertTrue(level.getBlockState(bed).isAir(), "the bed is gone");
                    h.assertTrue(level.getBlockState(otherWall).isAir(), "its walls are gone");
                    h.assertTrue(level.getBlockState(base.below()).equals(groundBefore[0]), "the ground under it is back as it was: " + level.getBlockState(base.below()));
                    h.assertTrue(level.getBlockState(wall).is(Blocks.DIAMOND_BLOCK), "the player's own block is kept");
                    h.assertTrue(level.getBlockState(h.absolutePos(new BlockPos(14, 1, 14))).is(Blocks.GOLD_BLOCK), "nothing it never touched is");
                    h.assertTrue(!Investigations.standing(in[0]), "and its ground is free for the next one");
                })
                // It never comes back.
                .thenIdle(40)
                .thenExecute(() -> h.assertTrue(level.getBlockState(bed).isAir(), "it doesn't come back"))
                .thenSucceed();
    }

    @GameTest(environment = ENV)
    public void newSitesKeepClearOfStandingOnes(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        InvestigationState st = InvestigationState.get(level.getServer());
        InvestigationState.Village v = Investigations.village(level, h.absolutePos(new BlockPos(-90, 1, 90)));
        BlockPos at = h.absolutePos(new BlockPos(2, 1, 2)).offset(0, 0, 5000);
        Investigations.create(level, v, st, IncidentTemplate.get("livestock"), new Sites.Site(at, 1, 0), level.getGameTime(), RandomSource.create(1));
        String dim = level.dimension().identifier().toString();
        h.assertTrue(Investigations.crowded(st, dim, at.offset(20, 0, 10)), "an open investigation's ground is taken");
        h.assertTrue(!Investigations.crowded(st, dim, at.offset(200, 0, 0)), "further off is free");
        h.succeed();
    }

    @GameTest(maxTicks = 100, environment = ENV)
    public void twoInvestigationsAtOnceEachHaveTheirOwnArena(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer a = survivor(h, 1, 1), b = survivor(h, 3, 3);
        a.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ProgressionItems.CURSED_GLASSES));
        b.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ProgressionItems.CURSED_GLASSES));
        InvestigationState st = InvestigationState.get(level.getServer());
        InvestigationState.Village v = Investigations.village(level, h.absolutePos(new BlockPos(90, 1, -90)));
        Incident pasture = Investigations.create(level, v, st, IncidentTemplate.get("livestock"), new Sites.Site(h.absolutePos(new BlockPos(2, 1, 2)), 1, 0),
                level.getGameTime(), RandomSource.create(2));
        Incident mine = Investigations.create(level, v, st, IncidentTemplate.get("old_mine"), new Sites.Site(h.absolutePos(new BlockPos(4, 1, 4)), 1, 0),
                level.getGameTime(), RandomSource.create(3));
        CursedRealms.enter(a, pasture, st);
        CursedRealms.enter(b, mine, st);
        var ra = CursedRealms.arenaOf(st, pasture.id);
        var rb = CursedRealms.arenaOf(st, mine.id);
        h.assertTrue(ra != null && rb != null && ra.slot != rb.slot && !ra.origin.equals(rb.origin), "two arenas, two slots");
        ServerLevel realm = CursedRealms.level(level.getServer());
        h.assertTrue(!realm.getBlockState(ra.origin).isAir() && !realm.getBlockState(rb.origin.below()).isAir(), "both built");
        h.assertTrue(ra.inside().contains(a.getUUID()) && rb.inside().contains(b.getUUID()) && !ra.inside().contains(b.getUUID()),
                "each player is in their own");
        CursedRealms.close(level.getServer(), st, ra, true);
        h.assertTrue(realm.getBlockState(ra.origin).isAir(), "the finished one is cleared away, ready for the next");
        h.assertTrue(!realm.getBlockState(rb.origin.below()).isAir() && CursedRealms.arenaOf(st, mine.id) != null && CursedRealms.inRealm(b),
                "the other one is untouched, and its player still in it");
        CursedRealms.close(level.getServer(), st, rb, false);
        a.discard();
        b.discard();
        h.succeed();
    }

    @GameTest(maxTicks = 40, environment = ENV)
    public void aKitOwnerSeesCursesWithoutGlasses(GameTestHelper h) {
        ServerPlayer p = survivor(h, 2, 2);
        KitOwnership own = KitOwnership.get(h.getLevel().getServer());
        own.release("hakari");
        h.assertTrue(!CursePerception.canPerceive(p), "no kit, no glasses: nothing");
        p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ProgressionItems.CURSED_GLASSES));
        h.assertTrue(CursePerception.canPerceive(p), "no kit: the glasses are still how");
        p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        h.assertValueEqual(TechniqueProgression.tryClaimKit(p, "hakari", "test"), KitOwnership.ClaimResult.CLAIMED, "earns Hakari");
        h.assertTrue(CursePerception.canPerceive(p), "a kit owner sees them with their own eyes");
        p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ProgressionItems.CURSED_GLASSES));
        p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        h.assertTrue(CursePerception.canPerceive(p), "taking glasses on and off changes nothing");
        p.setGameMode(GameType.CREATIVE);
        h.assertTrue(CursePerception.canPerceive(p), "in any mode");
        p.setGameMode(GameType.SURVIVAL);
        // The client is told (every screen and renderer reads this).
        TechniqueProgression.sync(p, true);
        TechniqueProgression.release(h.getLevel().getServer(), "hakari");
        h.assertTrue(!CursePerception.canPerceive(p), "a kit taken away takes it with it");
        p.discard();
        h.succeed();
    }
}
