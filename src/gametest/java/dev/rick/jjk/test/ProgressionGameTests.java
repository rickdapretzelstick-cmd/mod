package dev.rick.jjk.test;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.character.CharacterService;
import dev.rick.jjk.core.character.Characters;
import dev.rick.jjk.progression.CurseAggro;
import dev.rick.jjk.progression.CursePerception;
import dev.rick.jjk.progression.CursedEncounters;
import dev.rick.jjk.progression.CursedFingerAcquisition;
import dev.rick.jjk.progression.KitOwnership;
import dev.rick.jjk.progression.PlayerProgression;
import dev.rick.jjk.progression.ProgressionBlocks;
import dev.rick.jjk.progression.ProgressionItems;
import dev.rick.jjk.progression.TechniqueProgression;
import dev.rick.jjk.progression.block.CursedCauldronBlock;
import dev.rick.jjk.progression.worldgen.CursedSoulSandFeature;
import dev.rick.jjk.registry.ModAttachments;
import dev.rick.jjk.yuji.YujiCharacter;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Survival progression: Survival starts with no kit and the select screen can't hand one out, Creative is a sandbox
 * whose picks are never ownership, each kit has one owner per world (kept across a reload, checked against player data),
 * the Cursed Finger (first eater becomes Yuji, a later eater dies, the owner eating another is harmless), Cursed Soul Sand
 * forming under bone blocks, the bottle → brewing → cauldron → Cursed Glasses chain, curse perception and curse
 * hostility. Kit ownership is world-wide, so these run as their own batch and each test claims different kits and
 * releases them afterwards.
 */
public class ProgressionGameTests {
    private static final String ENV = "jjk-test:progression";

    private static void applyConfig() {
        JJKConfig cfg = JJKConfig.get();
        cfg.general.autoAssignGojo = false;
        cfg.progression.enabled = true;
        cfg.progression.infusionTicks = 20;
    }

    private static ServerPlayer survivor(GameTestHelper h, double x, double z) {
        applyConfig();
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        // The mock player keeps creative abilities whatever its mode; a real Survival player has none.
        p.getAbilities().instabuild = false;
        p.getAbilities().invulnerable = false;
        p.getAbilities().mayfly = false;
        p.setPos(h.absoluteVec(new Vec3(x, 1, z)));
        return p;
    }

    private static void floor(GameTestHelper h, int size) {
        for (int x = 0; x < size; x++) for (int z = 0; z < size; z++) h.setBlock(x, 0, z, Blocks.STONE);
    }

    private static KitOwnership ownership(GameTestHelper h) {
        return KitOwnership.get(h.getLevel().getServer());
    }

    @GameTest(maxTicks = 40, environment = ENV)
    public void kitsHaveOneOwnerPerWorldAndSurviveAReload(GameTestHelper h) {
        ServerPlayer a = survivor(h, 1, 1);
        ServerPlayer b = survivor(h, 2, 2);
        ownership(h).release(dev.rick.jjk.gojo.GojoCharacter.ID);
        h.assertValueEqual(TechniqueProgression.tryClaimKit(a, "gojo", "test"), KitOwnership.ClaimResult.CLAIMED, "first claim");
        h.assertValueEqual(TechniqueProgression.tryClaimKit(b, "gojo", "test"), KitOwnership.ClaimResult.TAKEN, "second player refused");
        h.assertValueEqual(TechniqueProgression.tryClaimKit(a, "gojo", "test"), KitOwnership.ClaimResult.ALREADY_OWNER, "owner again");
        h.assertTrue(Casters.get(a).character() == Characters.get("gojo"), "the Survival owner plays the kit at once");
        // A reload reads the registry back from disk.
        KitOwnership.unload();
        h.assertTrue(ownership(h).isOwner("gojo", a.getUUID()), "ownership kept across a reload");
        // Copied player data can't make a second owner: the world's record wins.
        b.setAttached(ModAttachments.PROGRESSION, PlayerProgression.EMPTY.withKit("gojo"));
        h.assertTrue(!TechniqueProgression.validate(b).owns("gojo"), "a forged kit is dropped on validation");
        // Administration: transfer and release.
        h.assertTrue(TechniqueProgression.transfer(h.getLevel().getServer(), "gojo", b), "transfer");
        h.assertTrue(ownership(h).isOwner("gojo", b.getUUID()) && !TechniqueProgression.validate(a).owns("gojo"), "transferred away from A");
        TechniqueProgression.release(h.getLevel().getServer(), "gojo");
        h.assertTrue(!ownership(h).isClaimed("gojo"), "released");
        a.discard();
        b.discard();
        h.succeed();
    }

    @GameTest(maxTicks = 60, environment = ENV)
    public void survivalStartsWithNothingAndACreativePickIsKeptForTesting(GameTestHelper h) {
        ServerPlayer p = survivor(h, 1, 1);
        ownership(h).release("ryu");
        ownership(h).release("hakari");
        TechniqueProgression.restore(p);
        h.assertTrue(Casters.get(p).character() == null, "a new Survival player has no kit");
        h.assertValueEqual(CharacterService.select(p, "ryu"), TechniqueProgression.NOT_AWAKENED, "the select screen can't hand one out");
        p.setGameMode(GameType.CREATIVE);
        h.assertTrue(CharacterService.select(p, "ryu") == null && Casters.get(p).character() == Characters.get("ryu"), "Creative picks freely");
        h.assertTrue(!ownership(h).isClaimed("ryu"), "a Creative pick claims nothing");
        // (A tick or two in Creative first: a real player can't switch there and back within one tick.)
        h.startSequence()
                .thenIdle(2)
                .thenExecute(() -> p.setGameMode(GameType.SURVIVAL))
                .thenIdle(3)
                .thenExecute(() -> {
                    h.assertTrue(Casters.get(p).character() == Characters.get("ryu"), "back in Survival the Creative pick is kept to test with");
                    h.assertTrue(!ownership(h).isClaimed("ryu") && !TechniqueProgression.progression(p).owns("ryu"), "and it is still not theirs");
                    h.assertValueEqual(CharacterService.select(p, "gojo"), TechniqueProgression.NOT_AWAKENED, "Survival alone still can't swap");
                    // Through a respawn or a relog (restore runs on both) it stays too.
                    TechniqueProgression.restore(p);
                    h.assertTrue(Casters.get(p).character() == Characters.get("ryu"), "kept through a respawn/relog");
                    // Earning a kit replaces it at once.
                    h.assertValueEqual(TechniqueProgression.tryClaimKit(p, "hakari", "test"), KitOwnership.ClaimResult.CLAIMED, "earns Hakari");
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    h.assertTrue(Casters.get(p).character() == Characters.get("hakari"), "the earned kit replaces the test kit");
                    h.assertTrue(TechniqueProgression.progression(p).testKit().isEmpty(), "the test kit is put away");
                    // Back to Creative, picking nothing: the next Survival trip has only their own kit.
                    p.setGameMode(GameType.CREATIVE);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    h.assertTrue(CharacterService.select(p, "ryu") == null, "Creative test pick");
                    p.setGameMode(GameType.SURVIVAL);
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    h.assertTrue(Casters.get(p).character() == Characters.get("ryu"), "testing Ryu in Survival again");
                    h.assertTrue(CharacterService.select(p, "hakari") == null && Casters.get(p).character() == Characters.get("hakari"),
                            "choosing their own kit in Survival works");
                    h.assertTrue(TechniqueProgression.progression(p).testKit().isEmpty(), "and puts the test kit away");
                    ownership(h).release("hakari");
                    p.discard();
                })
                .thenSucceed();
    }

    @GameTest(maxTicks = 60, environment = ENV)
    public void cursedFingerMakesTheFirstEaterYujiAndKillsTheNext(GameTestHelper h) {
        ServerPlayer a = survivor(h, 1, 1);
        ServerPlayer b = survivor(h, 3, 3);
        ownership(h).release(YujiCharacter.ID);
        h.assertValueEqual(TechniqueProgression.acquire(a, CursedFingerAcquisition.INSTANCE), KitOwnership.ClaimResult.CLAIMED, "A claims Yuji");
        h.assertValueEqual(TechniqueProgression.acquire(b, CursedFingerAcquisition.INSTANCE), KitOwnership.ClaimResult.TAKEN, "B finds Yuji taken");
        h.assertValueEqual(TechniqueProgression.acquire(a, CursedFingerAcquisition.INSTANCE), KitOwnership.ClaimResult.ALREADY_OWNER, "A again: their own case");
        h.runAfterDelay(3, () -> {
            h.assertTrue(a.isAlive() && Casters.get(a).character() == Characters.get(YujiCharacter.ID), "A survives as Yuji");
            h.assertTrue(TechniqueProgression.progression(a).counter(CursedFingerAcquisition.FINGERS_EATEN) == 2, "A's fingers are counted");
            h.assertTrue(!b.isAlive(), "B is consumed by the finger");
            h.assertTrue(ownership(h).isOwner(YujiCharacter.ID, a.getUUID()), "Yuji still belongs to A");
            ownership(h).release(YujiCharacter.ID);
            a.discard();
            b.discard();
            h.succeed();
        });
    }

    @GameTest(maxTicks = 20, environment = ENV)
    public void cursedSoulSandFormsOnlyUnderBoneBlocks(GameTestHelper h) {
        applyConfig();
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) h.setBlock(x, 1, z, Blocks.AIR);
        h.setBlock(2, 1, 2, Blocks.SOUL_SAND);
        h.setBlock(2, 2, 2, Blocks.BONE_BLOCK);
        h.setBlock(5, 1, 5, Blocks.SOUL_SAND);
        int n = CursedSoulSandFeature.decorate(h.getLevel(), h.getLevel().getRandom(), o.getX(), o.getZ(), o.getY(), o.getY() + 4);
        h.assertTrue(n == 1, "exactly the one spot under a bone block turned");
        h.assertTrue(h.getBlockState(new BlockPos(2, 1, 2)).is(ProgressionBlocks.CURSED_SOUL_SAND), "under the bone block");
        h.assertTrue(h.getBlockState(new BlockPos(5, 1, 5)).is(Blocks.SOUL_SAND), "open soul sand stays plain");
        h.succeed();
    }

    @GameTest(maxTicks = 120, environment = ENV)
    public void soulToCursedGlassesThroughBrewingAndTheCauldron(GameTestHelper h) {
        ServerPlayer p = survivor(h, 1, 1);
        floor(h, 6);
        ServerLevel level = h.getLevel();
        // Extraction: a glass bottle on Cursed Soul Sand.
        BlockPos sand = h.absolutePos(new BlockPos(1, 1, 3));
        level.setBlock(sand, ProgressionBlocks.CURSED_SOUL_SAND.defaultBlockState(), 3);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE));
        level.getBlockState(sand).useItemOn(p.getItemInHand(InteractionHand.MAIN_HAND), level, p, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(sand), Direction.UP, sand, false));
        h.assertTrue(level.getBlockState(sand).is(Blocks.SOUL_SAND), "the sand is plain again");
        h.assertTrue(p.getItemInHand(InteractionHand.MAIN_HAND).is(ProgressionItems.SOUL_IN_A_BOTTLE), "Soul in a Bottle");
        // (Brewing Soul in a Bottle with a Ghast Tear runs on a real brewing stand: soulInABottleBrewsIntoCursedEnergy.)
        // The cauldron: four bottles, 1/4 to 4/4.
        BlockPos pot = h.absolutePos(new BlockPos(3, 1, 3));
        level.setBlock(pot, Blocks.CAULDRON.defaultBlockState(), 3);
        for (int i = 1; i <= 4; i++) {
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ProgressionItems.CURSED_ENERGY_BOTTLE));
            // Through the player's real use path (a click: Fabric's use-block event, then the block).
            p.gameMode.useItemOn(p, level, p.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pot), Direction.UP, pot, false));
            h.assertTrue(level.getBlockState(pot).is(ProgressionBlocks.CURSED_CAULDRON)
                    && level.getBlockState(pot).getValue(CursedCauldronBlock.LEVEL) == i, "level " + i + "/4");
        }
        // Normal glasses perceive nothing; thrown into the full cauldron they come out cursed.
        p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ProgressionItems.GLASSES));
        h.assertTrue(!CursePerception.canPerceive(p), "plain glasses show nothing");
        // The plain glasses, used on the full cauldron (they must be wearable before: they were worn just now).
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ProgressionItems.GLASSES));
        p.gameMode.useItemOn(p, level, p.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pot), Direction.UP, pot, false));
        h.assertTrue(p.getItemInHand(InteractionHand.MAIN_HAND).isEmpty(), "the cauldron took the glasses");
        h.succeedWhen(() -> {
            h.assertTrue(level.getBlockState(pot).is(Blocks.CAULDRON), "the energy is spent (" + level.getBlockState(pot) + ", glasses at "
                    + level.getEntitiesOfClass(ItemEntity.class, new AABB(pot).inflate(3)).stream().map(e -> e.getItem() + "@" + e.position().subtract(Vec3.atLowerCornerOf(pot))).toList()
                    + ", scheduled " + level.getBlockTicks().hasScheduledTick(pot, ProgressionBlocks.CURSED_CAULDRON) + ")");
            List<ItemEntity> out = level.getEntitiesOfClass(ItemEntity.class, new AABB(pot).inflate(3), e -> e.getItem().is(ProgressionItems.CURSED_GLASSES));
            h.assertTrue(!out.isEmpty(), "Cursed Glasses rose out");
            p.setItemSlot(EquipmentSlot.HEAD, out.get(0).getItem().copy());
            h.assertTrue(CursePerception.canPerceive(p), "Cursed Glasses grant perception");
        });
    }

    /** A real brewing stand, fuelled: Soul in a Bottle and a Ghast Tear brew into Cursed Energy in a Bottle (a 26.3 data recipe). */
    @GameTest(maxTicks = 520, environment = ENV)
    public void soulInABottleBrewsIntoCursedEnergy(GameTestHelper h) {
        applyConfig();
        BlockPos at = h.absolutePos(new BlockPos(2, 1, 2));
        h.getLevel().setBlock(at, Blocks.BREWING_STAND.defaultBlockState(), 3);
        var stand = (net.minecraft.world.level.block.entity.BrewingStandBlockEntity) h.getLevel().getBlockEntity(at);
        h.assertTrue(stand != null, "the stand");
        h.assertTrue(stand.canPlaceItem(0, new ItemStack(ProgressionItems.SOUL_IN_A_BOTTLE)), "its bottle slot takes a Soul in a Bottle");
        stand.setItem(0, new ItemStack(ProgressionItems.SOUL_IN_A_BOTTLE));
        stand.setItem(3, new ItemStack(Items.GHAST_TEAR));
        stand.setItem(4, new ItemStack(Items.BLAZE_POWDER));
        h.succeedWhen(() -> {
            h.assertTrue(stand.getItem(0).is(ProgressionItems.CURSED_ENERGY_BOTTLE), "Cursed Energy in a Bottle (" + stand.getItem(0) + ")");
            h.assertTrue(stand.getItem(3).isEmpty(), "the tear was used");
        });
    }

    /**
     * Husks stand in for curses here: the test datapack tags them jjk:requires_curse_perception. The game's mock player
     * always reports itself as creative, and 26.3's Mob.setTarget refuses creative targets on its own, so this checks the
     * rules the setTarget guard applies ({@link CurseAggro#mayTarget}, {@link CurseAggro#onTargeted}) rather than vanilla
     * targeting.
     */
    @GameTest(maxTicks = 40, environment = ENV)
    public void unseenCursesDontStartFightsButDontForgetThem(GameTestHelper h) {
        ServerPlayer p = survivor(h, 2, 2);
        floor(h, 8);
        Mob curse = h.spawn(net.minecraft.world.entity.EntityTypes.HUSK, new Vec3(5, 1, 5));
        h.assertTrue(CursePerception.requiresPerception(curse), "the test curse needs perception");
        h.assertTrue(!CurseAggro.mayTarget(curse, p), "an unseen curse can't start a fight with someone who can't perceive it");
        p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ProgressionItems.CURSED_GLASSES));
        h.assertTrue(CurseAggro.mayTarget(curse, p), "perceived, it may take them");
        CurseAggro.onTargeted(curse, p);
        h.assertTrue(CurseAggro.isHostileTo(curse, p), "taking them makes it hostile");
        // Glasses off mid-fight: it keeps going (no cheesing the curse by hiding from it).
        p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        h.assertTrue(CurseAggro.mayTarget(curse, p), "taking the glasses off doesn't calm it");
        CurseAggro.calm(curse);
        h.assertTrue(!CurseAggro.mayTarget(curse, p), "calmed, it can't take an unperceiving player again");
        curse.discard();
        p.discard();
        h.succeed();
    }

    @GameTest(maxTicks = 100, environment = ENV)
    public void battleRoomSealRegistersItsEncounter(GameTestHelper h) {
        applyConfig();
        BlockPos seal = h.absolutePos(new BlockPos(2, 1, 2));
        h.getLevel().setBlock(seal, ProgressionBlocks.CURSED_SEAL.defaultBlockState(), 3);
        h.getLevel().scheduleTick(seal, ProgressionBlocks.CURSED_SEAL, 2);
        h.succeedWhen(() -> h.assertTrue(CursedEncounters.rooms(h.getLevel().getServer()).stream()
                .anyMatch(r -> r.seal.equals(seal) && r.encounter.equals(CursedEncounters.FINGER_BEARER) && r.state == CursedEncounters.State.DORMANT),
                "the room is registered, waiting for its Finger Bearer"));
    }
}
