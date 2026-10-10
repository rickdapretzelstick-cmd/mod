package dev.rick.jjk.test;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.character.CharacterService;
import dev.rick.jjk.core.character.Characters;
import dev.rick.jjk.progression.ProgressionItems;
import dev.rick.jjk.progression.investigation.InvestigationState;
import dev.rick.jjk.progression.tool.CursedToolDefinition;
import dev.rick.jjk.progression.tool.CursedTools;
import dev.rick.jjk.progression.tool.kit.CursedKits;
import dev.rick.jjk.progression.tool.kit.CursedSlot;
import dev.rick.jjk.progression.tool.kit.SlaughterDemonKit;
import dev.rick.jjk.progression.tool.rifle.RifleClaims;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Cursed tools as equipped movesets: the Cursed Item slot takes only cursed tools and gives their kit; with no technique
 * the tool's moves are simply yours, with one the switch key swaps them (never mid-move, never twice in a moment); each
 * moveset keeps its own cooldowns across switches; learned moves wait on the tool's own Mastery; tool moves credit the
 * tool; only the rifle is one-per-world.
 */
public class CursedKitTests {
    private static ServerPlayer survivor(GameTestHelper h) {
        JJKConfig.get().general.autoAssignGojo = false;
        JJKConfig.get().progression.enabled = true;
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        p.setPos(h.absoluteVec(new Vec3(1, 1, 1)));
        return p;
    }

    @GameTest(environment = "jjk-test:kit_a")
    public void theSlotTakesOnlyCursedToolsAndGivesTheirKit(GameTestHelper h) {
        ServerPlayer p = survivor(h);
        AbilityCaster c = Casters.get(p);
        h.assertTrue(!CursedSlot.fits(new ItemStack(Items.IRON_SWORD)) && !CursedSlot.fits(new ItemStack(ProgressionItems.CURSED_COMPASS)),
                "ordinary items don't fit");
        h.assertTrue(CursedSlot.fits(new ItemStack(ProgressionItems.SLAUGHTER_DEMON)), "a cursed tool does");
        CursedSlot.set(p, new ItemStack(ProgressionItems.SLAUGHTER_DEMON));
        CursedKits.update(p);
        h.assertTrue(c.toolKit() instanceof SlaughterDemonKit, "its kit is equipped");
        h.assertTrue(c.character() == null && c.usingTool(), "with no technique, the tool's moves are yours");
        h.assertTrue("sd_quickstep".equals(c.ability(AbilitySlot.SKILL_1).id) && "sd_thousand_cuts".equals(c.ability(AbilitySlot.ULTIMATE).id),
                "1 is Quickstep, G is Thousand Cuts");
        CursedSlot.set(p, ItemStack.EMPTY);
        CursedKits.update(p);
        h.assertTrue(c.toolKit() == null && !c.armed(), "taken off, it's gone");
        h.succeed();
    }

    @GameTest(maxTicks = 80, environment = "jjk-test:kit_b")
    public void theSwitchSwapsMovesetsOnlyFromAFreeMomentAndKeepsEachOnesCooldowns(GameTestHelper h) {
        ServerPlayer p = survivor(h);
        CharacterService.assign(p, Characters.get("gojo"));
        AbilityCaster c = Casters.get(p);
        CursedSlot.set(p, new ItemStack(ProgressionItems.SLAUGHTER_DEMON));
        CursedKits.update(p);
        String techniqueOne = c.ability(AbilitySlot.SKILL_1).id;
        h.assertTrue(!c.usingTool() && !techniqueOne.startsWith("sd_"), "the technique is in use first");
        h.assertTrue(CursedKits.switchMoveset(p) && c.usingTool(), "switched to the tool");
        h.assertTrue("sd_quickstep".equals(c.ability(AbilitySlot.SKILL_1).id), "1 is now the tool's");
        h.assertTrue(!CursedKits.switchMoveset(p) && c.usingTool(), "not twice in a moment");
        // Quickstep: a cooldown in the tool's moveset only.
        c.input(AbilitySlot.SKILL_5, true, 1, 0, null);
        c.input(AbilitySlot.SKILL_5, false, 1, 0, null);
        // A mock player isn't ticked by the server: its caster is ticked here, as the server would each tick.
        for (int i = 0; i < 40 && c.isBusy(); i++) c.tick();
        for (int i = 0; i < JJKConfig.get().cursedTools.switchLockTicks; i++) c.tick();
        int toolCd = c.cooldown(AbilitySlot.SKILL_5);
        h.assertTrue(toolCd > 20, "Draw Cut (R) is cooling down: " + toolCd);
        String why = c.switchBlocked();
        h.assertTrue(CursedKits.switchMoveset(p) && !c.usingTool(), "back to the technique: " + why);
        h.assertTrue(c.cooldown(AbilitySlot.SKILL_5) == 0, "the technique's R was never touched");
        for (int i = 0; i < JJKConfig.get().cursedTools.switchLockTicks + 2; i++) c.tick();
        h.assertTrue(CursedKits.switchMoveset(p) && c.usingTool(), "and to the tool again");
        int now = c.cooldown(AbilitySlot.SKILL_5);
        h.assertTrue(now > 0 && now < toolCd, "Draw Cut's cooldown kept running meanwhile, never reset: " + now + " of " + toolCd);
        h.succeed();
    }

    @GameTest(environment = "jjk-test:kit_c")
    public void learnedMovesWaitOnTheToolsOwnMastery(GameTestHelper h) {
        ServerPlayer p = survivor(h);
        AbilityCaster c = Casters.get(p);
        c.setNoCost(true);
        CursedSlot.set(p, new ItemStack(ProgressionItems.SLAUGHTER_DEMON));
        CursedKits.update(p);
        c.input(AbilitySlot.SKILL_2, true, 0, 0, null);
        c.input(AbilitySlot.SKILL_2, false, 0, 0, null);
        h.assertTrue(!c.isCasting("sd_flurry") && c.cooldown(AbilitySlot.SKILL_2) == 0, "Flurry isn't learned yet");
        dev.rick.jjk.progression.mastery.Mastery.award(p, "tool/slaughter_demon", 200);
        dev.rick.jjk.progression.mastery.Mastery.purchase(p, "tool/slaughter_demon", "keen_edge");
        var r = dev.rick.jjk.progression.mastery.Mastery.purchase(p, "tool/slaughter_demon", "flurry");
        h.assertTrue(r == dev.rick.jjk.progression.mastery.Mastery.Result.OK, "bought Flurry in the tool's tree: " + r);
        c.input(AbilitySlot.SKILL_2, true, 0, 0, null);
        c.input(AbilitySlot.SKILL_2, false, 0, 0, null);
        h.assertTrue(c.isCasting("sd_flurry") || c.cooldown(AbilitySlot.SKILL_2) > 0, "once learned, it works");
        h.assertTrue(dev.rick.jjk.progression.mastery.Mastery.data(p).points(dev.rick.jjk.progression.mastery.MasteryTree.techniqueId("gojo")) == 0,
                "and the technique's tree is untouched");
        h.succeed();
    }

    @GameTest(environment = "jjk-test:kit_d")
    public void toolMovesCreditTheTool(GameTestHelper h) {
        ServerPlayer p = survivor(h);
        AbilityCaster c = Casters.get(p);
        CursedSlot.set(p, new ItemStack(ProgressionItems.CURSED_CLEAVER));
        CursedKits.update(p);
        CursedToolDefinition d = CursedKits.credited(p);
        h.assertTrue(d == CursedTools.CURSED_CLEAVER, "a basic attack in the tool's moveset is the Cleaver's");
        h.assertTrue("tool/cursed_cleaver".equals(d.treeId()), "booked to its own tree");
        CharacterService.assign(p, Characters.get("gojo"));
        h.assertTrue(!c.usingTool() && CursedKits.credited(p) == null, "the technique's moves are not the tool's");
        h.succeed();
    }

    @GameTest(environment = "jjk-test:kit_e")
    public void onlyTheRifleIsOnePerWorld(GameTestHelper h) {
        h.assertTrue(CursedTools.CURSED_RIFLE.unique(), "the rifle is unique");
        h.assertTrue(!CursedTools.SLAUGHTER_DEMON.unique() && !CursedTools.CURSED_CLEAVER.unique(), "the starter tools are common");
        ServerPlayer a = survivor(h), b = survivor(h);
        InvestigationState st = InvestigationState.get(h.getLevel().getServer());
        InvestigationState.Claims.clearForTest(st);
        h.assertTrue(RifleClaims.bearer(h.getLevel().getServer()) == null, "nobody bears it yet");
        ItemStack first = RifleClaims.issue(a);
        h.assertTrue(a.getUUID().equals(RifleClaims.bearer(h.getLevel().getServer())), "A bears it");
        ItemStack second = RifleClaims.issue(b);
        h.assertTrue(RifleClaims.inertReason(a, first) != null && RifleClaims.inertReason(b, second) == null, "issuing it again retires the first: never two live");
        InvestigationState.Claims.clearForTest(st);
        h.succeed();
    }
}
