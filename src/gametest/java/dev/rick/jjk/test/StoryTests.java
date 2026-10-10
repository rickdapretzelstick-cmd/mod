package dev.rick.jjk.test;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.progression.KitOwnership;
import dev.rick.jjk.progression.ProgressionItems;
import dev.rick.jjk.progression.TechniqueProgression;
import dev.rick.jjk.progression.block.CauldronInfusions;
import dev.rick.jjk.progression.investigation.CursedRealms;
import dev.rick.jjk.progression.investigation.Incident;
import dev.rick.jjk.progression.investigation.IncidentTemplate;
import dev.rick.jjk.progression.investigation.InvestigationState;
import dev.rick.jjk.progression.investigation.Investigations;
import dev.rick.jjk.progression.investigation.Sites;
import dev.rick.jjk.progression.investigation.StoryChains;
import dev.rick.jjk.progression.mastery.Mastery;
import dev.rick.jjk.progression.story.CharacterStories;
import dev.rick.jjk.progression.story.CharacterStory;
import dev.rick.jjk.progression.story.PersonalTrials;
import dev.rick.jjk.progression.story.RelicForging;
import dev.rick.jjk.progression.story.RelicItem;
import dev.rick.jjk.progression.story.UniqueRelics;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The character storylines: every storyline's data is whole (four events with real places, triggers and realms, its
 * Essence, its object and relic, its cauldron infusion); a village's storyline is a fixed function of the village and the
 * world; completing its events moves the village on, and the finale owes everyone who took part its Essence; one relic
 * per character in the whole world (atomic forging, destruction frees only an unbound relic, a keeper's re-forge retires
 * the lost one); the cauldron forges the one relic and gives a loser back its dormant object; a relic needs the world's
 * live token; a completed trial claims the base kit and never the Awakening; a test relic or Creative claims nothing.
 * World-wide registries are shared, so each test uses its own character.
 */
public class StoryTests {
    private static final String ENV = "jjk-test:story";

    private static ServerPlayer survivor(GameTestHelper h, double x, double z) {
        JJKConfig.get().general.autoAssignGojo = false;
        JJKConfig.get().progression.enabled = true;
        JJKConfig.get().mastery.enabled = true;
        for (int i = 0; i < 6; i++) for (int k = 0; k < 6; k++) h.setBlock(i, 0, k, Blocks.STONE);
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.getAbilities().instabuild = false;
        p.getAbilities().invulnerable = false;
        p.getAbilities().mayfly = false;
        p.setPos(h.absoluteVec(new Vec3(x, 1, z)));
        p.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        return p;
    }

    private static MinecraftServer server(GameTestHelper h) {
        return h.getLevel().getServer();
    }

    private static void freeKit(GameTestHelper h, String kit) {
        KitOwnership.get(server(h)).release(kit);
        UniqueRelics.get(server(h)).reset(RelicItem.key(kit));
    }

    @GameTest(environment = ENV)
    public void everyStorylineIsWhole(GameTestHelper h) {
        h.assertTrue(CharacterStories.all().size() == 5, "five storylines");
        for (CharacterStory s : CharacterStories.all()) {
            h.assertTrue(s.chain().size() == 4, s.kit() + ": four events");
            for (String id : s.chain()) {
                IncidentTemplate t = IncidentTemplate.get(id);
                h.assertTrue(t != null, s.kit() + ": template " + id + " loads");
                h.assertTrue(Sites.exists(t.site()) && CursedRealms.hasLayout(t.realm()), id + ": a real place and its own realm");
                h.assertTrue(!t.curses().isEmpty() && t.reports().size() >= 2, id + ": curses and reports");
                h.assertTrue(CharacterStories.ofTemplate(id) == s && s.stageOf(id) == s.chain().indexOf(id) + 1, id + ": belongs to its storyline");
            }
            h.assertTrue(s.essence().get() != s.object().get() && s.relic().get() instanceof RelicItem r && r.kit().equals(s.kit()), s.kit() + ": its items");
            h.assertTrue(CauldronInfusions.indexOf(new ItemStack(s.object().get())) >= 5, s.kit() + ": its object is infused in the cauldron");
            h.assertTrue(CauldronInfusions.indexOf(new ItemStack(s.relic().get())) < 0, s.kit() + ": its relic is not infused again");
        }
        // The finales are the hardest events, and the first reports are recognisable without naming anyone.
        for (CharacterStory s : CharacterStories.all()) {
            IncidentTemplate fin = IncidentTemplate.get(s.chain().get(3));
            h.assertTrue(fin.grade().rank() >= IncidentTemplate.get(s.chain().get(0)).grade().rank(), s.kit() + ": the finale is at least as hard");
            for (IncidentTemplate.Report r : IncidentTemplate.get(s.chain().get(0)).reports()) {
                String all = (r.headline() + " " + r.body()).toLowerCase(java.util.Locale.ROOT);
                for (CharacterStory other : CharacterStories.all()) h.assertTrue(!all.contains(other.kit()), "a report never names " + other.kit());
            }
        }
        h.succeed();
    }

    @GameTest(environment = ENV)
    public void aVillagesStorylineIsFixedByTheVillageAndTheWorld(GameTestHelper h) {
        Map<String, Integer> seen = new HashMap<>();
        RandomSource r = RandomSource.create(9);
        int n = 4000;
        for (int i = 0; i < n; i++) {
            long bell = BlockPos.asLong(r.nextInt(60000) - 30000, 64, r.nextInt(60000) - 30000);
            String a = StoryChains.storyFor(1234L, bell);
            h.assertTrue(a.equals(StoryChains.storyFor(1234L, bell)), "the same village always holds the same storyline");
            seen.merge(a, 1, Integer::sum);
        }
        for (CharacterStory s : CharacterStories.all()) h.assertTrue(seen.getOrDefault(s.kit(), 0) > n / 20, s.kit() + " villages exist: " + seen);
        int none = seen.getOrDefault("", 0);
        h.assertTrue(none > n / 4 && none < n / 2, "most villages hold a storyline, not all: " + seen);
        h.succeed();
    }

    @GameTest(maxTicks = 60, environment = ENV)
    public void completingEventsMovesTheVillageOnAndTheFinaleOwesTheEssence(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer p = survivor(h, 2, 2);
        InvestigationState st = InvestigationState.get(server(h));
        InvestigationState.Village v = Investigations.village(level, h.absolutePos(new BlockPos(-60, 1, -60)));
        StoryChains.setStory(st, v, "gojo", 1);
        Incident first = Investigations.create(level, v, st, IncidentTemplate.get("gojo_chain_1"),
                new Sites.Site(h.absolutePos(new BlockPos(3, 1, 3)), 1, 0), level.getGameTime(), RandomSource.create(1));
        String headline = first.headline.toLowerCase(java.util.Locale.ROOT);
        h.assertTrue(StoryChains.isChain(first) && (headline.contains("distance") || headline.contains("watchtower")),
                "the first report is the watchtower: " + first.headline);
        // It never goes stale, and it is pinned first on the board.
        h.assertTrue(Investigations.notes(v, st, level.getGameTime()).get(0).headline().equals(first.headline), "pinned first");
        Investigations.participateForTest(first, p);
        Investigations.completeForTest(server(h), first);
        h.assertTrue(v.storyStage() == 2, "the village moves on to the next event: " + v.storyStage());
        h.assertTrue(!first.rewardsPending().contains(p.getUUID()), "only the finale gives anything");
        StoryChains.setStory(st, v, "gojo", 4);
        Incident fin = Investigations.create(level, v, st, IncidentTemplate.get("gojo_chain_4"),
                new Sites.Site(h.absolutePos(new BlockPos(3, 1, 3)), 1, 0), level.getGameTime(), RandomSource.create(2));
        Investigations.participateForTest(fin, p);
        Investigations.completeForTest(server(h), fin);
        h.assertTrue(v.storyStage() == StoryChains.DONE, "the storyline is complete");
        h.assertTrue(fin.rewardsPending().contains(p.getUUID()), "the Essence is owed");
        StoryChains.deliver(p, st);
        h.assertTrue(p.getInventory().countItem(ProgressionItems.GOJO_ESSENCE) == 1, "delivered: the Gojo Essence");
        StoryChains.deliver(p, st);
        h.assertTrue(p.getInventory().countItem(ProgressionItems.GOJO_ESSENCE) == 1, "exactly once");
        p.discard();
        h.succeed();
    }

    @GameTest(environment = ENV)
    public void oneRelicPerWorldAndRecoveryNeverMakesTwo(GameTestHelper h) {
        UniqueRelics reg = UniqueRelics.get(server(h));
        String key = "relic:test_registry";
        reg.reset(key);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        UUID t1 = reg.forge(key, a, "A");
        h.assertTrue(t1 != null && reg.isLive(key, t1), "the first forge wins");
        h.assertTrue(reg.forge(key, b, "B") == null, "a second never does");
        h.assertTrue(reg.reforge(key, b, "B") == null, "only its keeper may re-forge it");
        UUID t2 = reg.reforge(key, a, "A");
        h.assertTrue(t2 != null && !t2.equals(t1) && reg.isLive(key, t2) && !reg.isLive(key, t1), "the keeper's re-forge retires the lost one");
        h.assertTrue(!reg.destroyed(key, t1), "destroying a retired copy frees nothing");
        h.assertTrue(reg.destroyed(key, t2) && !reg.exists(key), "destroying the live, unbound relic frees it");
        UUID t3 = reg.forge(key, b, "B");
        reg.bind(key, b);
        h.assertTrue(!reg.destroyed(key, t3) && reg.isLive(key, t3), "a bound relic is never freed by losing it");
        reg.reset(key);
        h.succeed();
    }

    @GameTest(environment = ENV)
    public void theCauldronForgesTheOneRelicAndGivesALoserItsObjectBack(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        freeKit(h, "gojo");
        ServerPlayer a = survivor(h, 1, 1);
        ServerPlayer b = survivor(h, 4, 4);
        BlockPos c1 = h.absolutePos(new BlockPos(1, 1, 4)), c2 = h.absolutePos(new BlockPos(4, 1, 1));
        h.assertTrue(RelicForging.refuse(level, CharacterStories.GOJO, a) == null && RelicForging.refuse(level, CharacterStories.GOJO, b) == null,
                "both cauldrons take a Blindfold while the world has none");
        RelicForging.offered(level, c1, a);
        RelicForging.offered(level, c2, b);
        ItemStack won = RelicForging.make(level, c1, CharacterStories.GOJO);
        ItemStack lost = RelicForging.make(level, c2, CharacterStories.GOJO);
        h.assertTrue(won.is(ProgressionItems.INFUSED_BLINDFOLD) && UniqueRelics.get(server(h)).isLive(RelicItem.key("gojo"), RelicItem.token(won)),
                "exactly one Infused Blindfold");
        h.assertTrue(lost.is(ProgressionItems.BLINDFOLD), "the other cauldron gives its Blindfold back");
        h.assertTrue(RelicForging.refuse(level, CharacterStories.GOJO, b) != null, "and refuses another while the relic exists");
        freeKit(h, "gojo");
        a.discard();
        b.discard();
        h.succeed();
    }

    @GameTest(environment = ENV)
    public void aRelicNeedsTheWorldsLiveToken(GameTestHelper h) {
        freeKit(h, "yuta");
        ServerPlayer p = survivor(h, 2, 2);
        ItemStack fake = RelicItem.create(ProgressionItems.INFUSED_CURSED_RING, UUID.randomUUID());
        String why = PersonalTrials.whyBlocked(p, fake);
        h.assertTrue(why != null && !why.isEmpty(), "a copy with a stale token is cold: " + why);
        UUID live = UniqueRelics.get(server(h)).forge(RelicItem.key("yuta"), p.getUUID(), "p");
        h.assertTrue(PersonalTrials.whyBlocked(p, RelicItem.create(ProgressionItems.INFUSED_CURSED_RING, live)) == null, "the live one opens its trial");
        TechniqueProgression.tryClaimKit(p, "yuta", "test");
        h.assertTrue(PersonalTrials.whyBlocked(p, RelicItem.create(ProgressionItems.INFUSED_CURSED_RING, live)) != null, "its owner has nothing more to find in it");
        freeKit(h, "yuta");
        p.discard();
        h.succeed();
    }

    @GameTest(maxTicks = 100, environment = ENV)
    public void aCompletedTrialClaimsTheBaseKitButNeverTheAwakening(GameTestHelper h) {
        freeKit(h, "yuji");
        ServerPlayer p = survivor(h, 2, 2);
        PersonalTrials.startNow(p, CharacterStories.YUJI, false);
        CursedRealms.finishPullForTest(p);
        h.assertTrue(PersonalTrials.inTrial(p) && CursedRealms.inRealm(p), "in the theater");
        h.assertTrue(PersonalTrials.cursedFists(p), "the body is the weapon");
        h.assertTrue(PersonalTrials.completeNow(p), "completed");
        h.assertTrue(KitOwnership.get(server(h)).isOwner("yuji", p.getUUID()), "the world's Yuji");
        h.assertTrue(Mastery.unlocked(p, "divergent_fist.black_flash"), "the base kit is whole");
        h.assertTrue(!Mastery.unlocked(p, "yuji.awakening"), "but not the Awakening");
        InvestigationState st = InvestigationState.get(server(h));
        var arena = CursedRealms.arenaOf(st, CursedRealms.TRIAL + "yuji:" + p.getUUID());
        h.assertTrue(arena != null, "its arena");
        CursedRealms.close(server(h), st, arena, true);
        h.assertTrue(!PersonalTrials.inTrial(p) && !CursedRealms.inRealm(p), "home again");
        freeKit(h, "yuji");
        p.discard();
        h.succeed();
    }

    @GameTest(maxTicks = 100, environment = ENV)
    public void aTestRelicOrCreativeClaimsNothing(GameTestHelper h) {
        freeKit(h, "ryu");
        ServerPlayer p = survivor(h, 2, 2);
        PersonalTrials.startNow(p, CharacterStories.RYU, true);
        CursedRealms.finishPullForTest(p);
        h.assertTrue(PersonalTrials.completeNow(p), "completed");
        h.assertTrue(!KitOwnership.get(server(h)).isClaimed("ryu"), "a test relic in Survival is only an echo");
        InvestigationState st = InvestigationState.get(server(h));
        var arena = CursedRealms.arenaOf(st, CursedRealms.TRIAL + "ryu:" + p.getUUID());
        if (arena != null) CursedRealms.close(server(h), st, arena, false);
        // Creative: the real thing, but it claims nothing either (the kit is only tried out).
        p.setGameMode(GameType.CREATIVE);
        PersonalTrials.startNow(p, CharacterStories.RYU, false);
        CursedRealms.finishPullForTest(p);
        h.assertTrue(PersonalTrials.completeNow(p), "completed in Creative");
        h.assertTrue(!KitOwnership.get(server(h)).isClaimed("ryu"), "Creative never claims");
        arena = CursedRealms.arenaOf(st, CursedRealms.TRIAL + "ryu:" + p.getUUID());
        if (arena != null) CursedRealms.close(server(h), st, arena, false);
        freeKit(h, "ryu");
        p.discard();
        h.succeed();
    }

    @GameTest(maxTicks = 100, environment = ENV)
    public void takingTheBlindfoldOffTooSoonFailsGojosTrial(GameTestHelper h) {
        freeKit(h, "gojo");
        ServerPlayer p = survivor(h, 2, 2);
        UUID live = UniqueRelics.get(server(h)).forge(RelicItem.key("gojo"), p.getUUID(), "p");
        p.setItemSlot(EquipmentSlot.HEAD, RelicItem.create(ProgressionItems.INFUSED_BLINDFOLD, live));
        PersonalTrials.startNow(p, CharacterStories.GOJO, false);
        CursedRealms.finishPullForTest(p);
        h.assertTrue(PersonalTrials.inTrial(p), "in the trial, blindfolded");
        p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        h.succeedWhen(() -> {
            h.assertTrue(!PersonalTrials.inTrial(p), "taking it off ends the trial");
            h.assertTrue(!KitOwnership.get(server(h)).isClaimed("gojo"), "and claims nothing");
            freeKit(h, "gojo");
            p.discard();
        });
    }
}
