package dev.rick.jjk.test;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.character.CharacterService;
import dev.rick.jjk.core.character.Characters;
import dev.rick.jjk.gojo.GojoCharacter;
import dev.rick.jjk.progression.KitOwnership;
import dev.rick.jjk.progression.ProgressionBlocks;
import dev.rick.jjk.progression.ProgressionItems;
import dev.rick.jjk.progression.block.CursedCauldronBlock;
import dev.rick.jjk.progression.prison.PrisonRealm;
import dev.rick.jjk.progression.prison.PrisonRealmEntity;
import dev.rick.jjk.progression.prison.PrisonRealmItem;
import dev.rick.jjk.progression.prison.PrisonRealmState;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The Prison Realm. There is one realm per world, so every test runs in its own batch (environment) and starts from a
 * clean slate (no realm, Gojo unclaimed). Covered:
 * <ol>
 *   <li>forging: one realm a world, the cauldron refuses a second, a destroyed realm frees the world, a stale cube is inert;</li>
 *   <li>sealing: the full sequence lands at the target's feet; dodging it before the restraints fails it and the cube drops;</li>
 *   <li>the sealed realm on the ground: unhurtable, anchored, killed bodies come back, nothing of that releases anyone;</li>
 *   <li>the solo escape: deterministic lock windows, a mistimed lock lashes back and resets the stage, three stages, the core;</li>
 *   <li>escape progress is saved (survives the world data being reloaded);</li>
 *   <li>the rescue from outside: 5 s held next to it; walking away interrupts it;</li>
 *   <li>the cell holds: leaving it any way (another dimension too) puts the captive back; techniques are sealed;</li>
 *   <li>Releases: a genuine capture-and-release is counted and makes nobody Gojo, capture alone isn't, an admin
 *   release doesn't, and a release owed to a captive who was dead is applied once they are back;</li>
 *   <li>no duplicates: no second seal while one runs, the released realm is the same single cube again.</li>
 * </ol>
 */
public class PrisonRealmTests {
    private static void fresh(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        JJKConfig.get().progression.enabled = true;
        JJKConfig.get().mastery.enabled = true;
        JJKConfig.get().progression.infusionTicks = 20;
        PrisonRealm.adminReset(server);
        KitOwnership.get(server).release(GojoCharacter.ID);
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(x, 0, z, Blocks.STONE);
    }

    private static ServerPlayer survivor(GameTestHelper h, double x, double z) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.getAbilities().instabuild = false;
        p.getAbilities().invulnerable = false;
        p.getAbilities().mayfly = false;
        p.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        p.setPos(h.absoluteVec(new Vec3(x, 1, z)));
        return p;
    }

    private static PrisonRealmState st(GameTestHelper h) {
        return PrisonRealm.state(h.getLevel().getServer());
    }

    private static ItemStack forged(GameTestHelper h) {
        UUID id = PrisonRealm.forge(h.getLevel().getServer());
        h.assertTrue(id != null, "forged the world's realm");
        return PrisonRealmItem.create(id);
    }

    /** Seals {@code p} by sneak-using the cube on themselves. */
    private static void sealSelf(GameTestHelper h, ServerPlayer p, ItemStack cube) {
        p.setItemInHand(InteractionHand.MAIN_HAND, cube);
        p.setShiftKeyDown(true);
        h.assertTrue(PrisonRealm.use(p, InteractionHand.MAIN_HAND), "the cube opens on them");
        p.setShiftKeyDown(false);
        h.assertTrue(st(h).phase() == PrisonRealmState.Phase.SEALING, "sealing");
    }

    private static boolean sealed(GameTestHelper h) {
        return st(h).phase() == PrisonRealmState.Phase.SEALED;
    }

    private static boolean inCell(GameTestHelper h, ServerPlayer p) {
        BlockPos c = st(h).cell();
        return c != null && new AABB(c.getX(), c.getY(), c.getZ(), c.getX() + 13, c.getY() + 8, c.getZ() + 13).contains(p.position());
    }

    /** One tick of a perfect escape: every glowing, unbroken lock used, then the core. */
    private static void escapeStep(GameTestHelper h, ServerPlayer p) {
        PrisonRealmState s = st(h);
        if (s.phase() != PrisonRealmState.Phase.SEALED) return;
        if (s.stage() >= PrisonRealm.STAGES) {
            PrisonRealm.useCore(p);
            return;
        }
        long now = h.getLevel().getGameTime();
        for (int i = 0; i < 4; i++) {
            if ((s.broken() & (1 << i)) == 0 && PrisonRealm.lockOpen(now, i, s.stage())) PrisonRealm.useLock(p, PrisonRealm.lockPos(h.getLevel().getServer(), i));
        }
    }

    private static boolean gojo(GameTestHelper h, ServerPlayer p) {
        return KitOwnership.get(h.getLevel().getServer()).isOwner(GojoCharacter.ID, p.getUUID());
    }

    /** Genuine releases counted for a player (the Prison Realm no longer makes anyone Gojo). */
    private static int releases(ServerPlayer p) {
        return dev.rick.jjk.progression.TechniqueProgression.progression(p).counter(PrisonRealm.RELEASES);
    }

    private static void cleanUp(GameTestHelper h, ServerPlayer... players) {
        PrisonRealm.adminReset(h.getLevel().getServer());
        KitOwnership.get(h.getLevel().getServer()).release(GojoCharacter.ID);
        for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(BlockPos.ZERO)).inflate(24),
                e -> e.getItem().is(ProgressionItems.PRISON_REALM))) e.discard();
        for (ServerPlayer p : players) p.discard();
    }

    // 1. One realm a world.
    @GameTest(maxTicks = 160, environment = "jjk-test:prison_a")
    public void onlyOneRealmCanExistUntilItIsDestroyed(GameTestHelper h) {
        fresh(h);
        ServerLevel level = h.getLevel();
        ServerPlayer p = survivor(h, 3, 3);
        BlockPos pot = h.absolutePos(new BlockPos(5, 1, 5));
        level.setBlock(pot, ProgressionBlocks.CURSED_CAULDRON.defaultBlockState().setValue(CursedCauldronBlock.LEVEL, 4), 3);
        // A dormant cube dipped in the full cauldron wakes into the world's Prison Realm.
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ProgressionItems.DORMANT_PRISON_REALM));
        p.gameMode.useItemOn(p, level, p.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pot), Direction.UP, pot, false));
        h.assertTrue(p.getItemInHand(InteractionHand.MAIN_HAND).isEmpty(), "the cauldron took the dormant cube");
        UUID[] id = new UUID[1];
        h.startSequence()
                .thenWaitUntil(() -> {
                    List<ItemEntity> out = level.getEntitiesOfClass(ItemEntity.class, new AABB(pot).inflate(3), e -> e.getItem().is(ProgressionItems.PRISON_REALM));
                    h.assertTrue(!out.isEmpty(), "the Prison Realm rose out");
                    id[0] = PrisonRealmItem.realmId(out.get(0).getItem());
                })
                .thenExecute(() -> {
                    h.assertTrue(id[0] != null && id[0].equals(st(h).realmId()) && st(h).phase() == PrisonRealmState.Phase.ITEM, "it is the world's realm");
                    // A second one: the cauldron refuses it, the world won't forge one.
                    level.setBlock(pot, ProgressionBlocks.CURSED_CAULDRON.defaultBlockState().setValue(CursedCauldronBlock.LEVEL, 4), 3);
                    p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ProgressionItems.DORMANT_PRISON_REALM));
                    p.gameMode.useItemOn(p, level, p.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(pot), Direction.UP, pot, false));
                    h.assertTrue(p.getItemInHand(InteractionHand.MAIN_HAND).is(ProgressionItems.DORMANT_PRISON_REALM), "a second cube is refused while one exists");
                    h.assertTrue(PrisonRealm.forge(level.getServer()) == null, "the world forges no second realm");
                    // A stale cube (another id) is inert.
                    p.setItemInHand(InteractionHand.MAIN_HAND, PrisonRealmItem.create(UUID.randomUUID()));
                    p.setShiftKeyDown(true);
                    h.assertTrue(!PrisonRealm.use(p, InteractionHand.MAIN_HAND), "a copy that isn't the world's realm does nothing");
                    p.setShiftKeyDown(false);
                    // Destroyed (here: as a cactus or an explosion would), the world may forge another.
                    PrisonRealm.itemDestroyed(level, PrisonRealmItem.create(id[0]));
                    h.assertTrue(PrisonRealm.canForge(level.getServer()) && st(h).phase() == PrisonRealmState.Phase.NONE, "destroyed: none exists");
                    UUID next = PrisonRealm.forge(level.getServer());
                    h.assertTrue(next != null && !next.equals(id[0]), "a new realm can be forged");
                    p.setItemInHand(InteractionHand.MAIN_HAND, PrisonRealmItem.create(id[0]));
                    p.setShiftKeyDown(true);
                    h.assertTrue(!PrisonRealm.use(p, InteractionHand.MAIN_HAND), "the destroyed realm's old id is dead");
                    p.setShiftKeyDown(false);
                    cleanUp(h, p);
                })
                .thenSucceed();
    }

    // 2, 4, 8, 9. Sealed, escaped alone: counted, and nobody becomes Gojo (he is earned through his storyline now).
    @GameTest(maxTicks = 1600, environment = "jjk-test:prison_b")
    public void anEscapeIsCountedAndMakesNobodyGojo(GameTestHelper h) {
        fresh(h);
        ServerLevel level = h.getLevel();
        ServerPlayer a = survivor(h, 3.5, 3.5);
        ServerPlayer b = survivor(h, 6.5, 6.5);
        ItemStack cube = forged(h);
        UUID id = PrisonRealmItem.realmId(cube);
        BlockPos[] at = new BlockPos[1];
        h.startSequence()
                .thenExecute(() -> sealSelf(h, a, cube))
                .thenExecute(() -> {
                    h.assertTrue(PrisonRealm.body(level) != null, "the realm lies at their feet, playing its sequence");
                    // While it is sealing nobody can throw it again (there is no second cube to throw, and copies are refused).
                    b.setItemInHand(InteractionHand.MAIN_HAND, PrisonRealmItem.create(id));
                    b.setShiftKeyDown(true);
                    h.assertTrue(!PrisonRealm.use(b, InteractionHand.MAIN_HAND), "no second seal while one is under way");
                    b.setShiftKeyDown(false);
                    b.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                })
                .thenWaitUntil(() -> h.assertTrue(sealed(h), "the seal closed"))
                .thenExecute(() -> {
                    at[0] = st(h).pos();
                    PrisonRealmEntity body = PrisonRealm.body(level);
                    h.assertTrue(body != null && body.realmPhase() == PrisonRealmEntity.SEALED && body.hasGlowingTag(), "closed on the ground, marked");
                    h.assertTrue(at[0].closerToCenterThan(h.absoluteVec(new Vec3(3.5, 1, 3.5)), 1.5), "where they stood");
                    h.assertTrue(inCell(h, a), "the captive is in the cell");
                    h.assertTrue(!gojo(h, a) && !KitOwnership.get(level.getServer()).isClaimed(GojoCharacter.ID), "capture alone grants nothing");
                    // Techniques are sealed inside.
                    CharacterService.assign(a, Characters.get(dev.rick.jjk.yuji.YujiCharacter.ID));
                    h.assertTrue(!Casters.get(a).input(AbilitySlot.SKILL_1, true, 0, 0, null) && "sealed".equals(Casters.get(a).lastRefusal), "no techniques inside");
                    CharacterService.assign(a, null);
                })
                .thenWaitUntil(() -> {
                    escapeStep(h, a);
                    h.assertTrue(st(h).phase() == PrisonRealmState.Phase.ITEM, "escaped (stage " + st(h).stage() + ", seals " + Integer.bitCount(st(h).broken()) + ")");
                })
                .thenExecute(() -> {
                    h.assertTrue(!gojo(h, a) && !KitOwnership.get(level.getServer()).isClaimed(GojoCharacter.ID), "a genuine escape makes nobody Gojo");
                    h.assertTrue(releases(a) == 1, "it is counted");
                    h.assertTrue(a.position().distanceTo(Vec3.atBottomCenterOf(at[0])) < 5, "they stand beside the realm");
                    h.assertTrue(PrisonRealm.body(level) == null, "the realm's body is gone");
                    List<ItemEntity> cubes = level.getEntitiesOfClass(ItemEntity.class, new AABB(at[0]).inflate(4), e -> e.getItem().is(ProgressionItems.PRISON_REALM));
                    h.assertTrue(cubes.size() == 1 && id.equals(PrisonRealmItem.realmId(cubes.get(0).getItem())), "the same single realm is back on the ground");
                    cubes.get(0).discard();
                })
                // Someone else, later: sealed and escaped the same way.
                .thenExecute(() -> sealSelf(h, b, PrisonRealmItem.create(id)))
                .thenWaitUntil(() -> h.assertTrue(sealed(h), "the second seal closed"))
                .thenWaitUntil(() -> {
                    escapeStep(h, b);
                    h.assertTrue(st(h).phase() == PrisonRealmState.Phase.ITEM, "the second captive escaped");
                })
                .thenExecute(() -> {
                    h.assertTrue(!gojo(h, b) && releases(b) == 1 && !KitOwnership.get(level.getServer()).isClaimed(GojoCharacter.ID), "the later captive is let out and counted");
                    cleanUp(h, a, b);
                })
                .thenSucceed();
    }

    // 4, 5. A mistimed lock lashes back; progress is saved with the world.
    @GameTest(maxTicks = 700, environment = "jjk-test:prison_c")
    public void mistimingResetsTheStageAndProgressIsSaved(GameTestHelper h) {
        fresh(h);
        ServerPlayer a = survivor(h, 3.5, 3.5);
        ItemStack cube = forged(h);
        MinecraftServer server = h.getLevel().getServer();
        float[] hp = new float[1];
        h.startSequence()
                .thenExecute(() -> sealSelf(h, a, cube))
                .thenWaitUntil(() -> h.assertTrue(sealed(h), "sealed"))
                // Two seals broken properly.
                .thenWaitUntil(() -> {
                    long now = h.getLevel().getGameTime();
                    for (int i = 0; i < 2; i++) {
                        if ((st(h).broken() & (1 << i)) == 0 && PrisonRealm.lockOpen(now, i, 0)) PrisonRealm.useLock(a, PrisonRealm.lockPos(server, i));
                    }
                    h.assertTrue(st(h).broken() == 3, "two seals broken (" + st(h).broken() + ")");
                })
                // Then one used while it is dark.
                .thenWaitUntil(() -> h.assertTrue(!PrisonRealm.lockOpen(h.getLevel().getGameTime(), 3, 0)
                        && !PrisonRealm.lockOpen(h.getLevel().getGameTime() - 1, 3, 0) && !PrisonRealm.lockOpen(h.getLevel().getGameTime() - 2, 3, 0), "lock 3 dark"))
                .thenExecute(() -> {
                    hp[0] = a.getHealth();
                    PrisonRealm.useLock(a, PrisonRealm.lockPos(server, 3));
                    h.assertTrue(st(h).broken() == 0 && st(h).stage() == 0, "mistimed: this stage's seals re-form");
                    h.assertTrue(a.getHealth() < hp[0], "and it lashes back");
                })
                // A whole stage, then the world's data reloaded: the stage is still broken.
                .thenWaitUntil(() -> {
                    escapeStep(h, a);
                    h.assertTrue(st(h).stage() == 1, "stage one broken");
                })
                .thenExecute(() -> {
                    PrisonRealmState.unload();
                    PrisonRealmState again = PrisonRealm.state(server);
                    h.assertTrue(again.phase() == PrisonRealmState.Phase.SEALED && again.stage() == 1 && a.getUUID().equals(again.captive()),
                            "escape progress and the captive are saved (" + again.phase() + " stage " + again.stage() + ")");
                    // An admin release grants nothing.
                    h.assertTrue(PrisonRealm.adminFree(server), "admin free");
                })
                .thenWaitUntil(() -> h.assertTrue(st(h).phase() == PrisonRealmState.Phase.ITEM, "freed"))
                .thenExecute(() -> {
                    h.assertTrue(!gojo(h, a) && !KitOwnership.get(server).isClaimed(GojoCharacter.ID), "an admin release grants nothing");
                    h.assertTrue(!inCell(h, a), "out of the cell");
                    cleanUp(h, a);
                })
                .thenSucceed();
    }

    // 6, 8. Rescued from outside.
    @GameTest(maxTicks = 700, environment = "jjk-test:prison_d")
    public void aRescueFromOutsideReleasesAndGrants(GameTestHelper h) {
        fresh(h);
        ServerLevel level = h.getLevel();
        ServerPlayer captive = survivor(h, 2.5, 2.5);
        ServerPlayer friend = survivor(h, 5.5, 5.5);
        ItemStack cube = forged(h);
        int[] held = new int[1];
        h.startSequence()
                .thenExecute(() -> sealSelf(h, captive, cube))
                .thenWaitUntil(() -> h.assertTrue(sealed(h), "sealed"))
                .thenExecute(() -> {
                    PrisonRealmEntity body = PrisonRealm.body(level);
                    Vec3 next = body.position().add(1.6, 0, 0);
                    friend.teleportTo(level, next.x, next.y, next.z, Set.of(), 90f, 20f, false);
                    friend.setShiftKeyDown(true);
                })
                // A start, then they walk off: interrupted, the progress gone.
                .thenWaitUntil(() -> {
                    PrisonRealm.interact(friend, PrisonRealm.body(level));
                    h.assertTrue(PrisonRealm.body(level).rescue() >= 20, "opening (" + PrisonRealm.body(level).rescue() + "%)");
                })
                .thenExecute(() -> {
                    Vec3 away = PrisonRealm.body(level).position().add(7, 0, 0);
                    friend.teleportTo(level, away.x, away.y, away.z, Set.of(), 90f, 20f, false);
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    h.assertTrue(PrisonRealm.body(level).rescue() == 0 && sealed(h), "walking off interrupts it");
                    Vec3 next = PrisonRealm.body(level).position().add(1.6, 0, 0);
                    friend.teleportTo(level, next.x, next.y, next.z, Set.of(), 90f, 20f, false);
                })
                // Held for the full five seconds (use is repeated while held, as a real client does).
                .thenWaitUntil(() -> {
                    if (held[0]++ % 4 == 0 && PrisonRealm.body(level) != null) PrisonRealm.interact(friend, PrisonRealm.body(level));
                    h.assertTrue(st(h).phase() == PrisonRealmState.Phase.ITEM, "released by the rescue");
                })
                .thenExecute(() -> {
                    h.assertTrue(held[0] >= PrisonRealm.RESCUE_TICKS, "it took the whole five seconds (" + held[0] + " ticks)");
                    h.assertTrue(!gojo(h, captive) && releases(captive) == 1 && releases(friend) == 0, "the rescued captive's release is counted (the rescuer's isn't); nobody is Gojo");
                    h.assertTrue(!inCell(h, captive), "out");
                    friend.setShiftKeyDown(false);
                    cleanUp(h, captive, friend);
                })
                .thenSucceed();
    }

    // 3. The grounded realm can't be broken, moved or taken, and nothing of that releases anyone.
    @GameTest(maxTicks = 600, environment = "jjk-test:prison_e")
    public void theGroundedRealmCantBeBrokenMovedOrTaken(GameTestHelper h) {
        fresh(h);
        ServerLevel level = h.getLevel();
        ServerPlayer captive = survivor(h, 3.5, 3.5);
        ServerPlayer other = survivor(h, 6.5, 6.5);
        ItemStack cube = forged(h);
        UUID[] first = new UUID[1];
        h.startSequence()
                .thenExecute(() -> sealSelf(h, captive, cube))
                .thenWaitUntil(() -> h.assertTrue(sealed(h), "sealed"))
                .thenExecute(() -> {
                    PrisonRealmEntity body = PrisonRealm.body(level);
                    first[0] = body.getUUID();
                    h.assertTrue(!body.hurtServer(level, level.damageSources().playerAttack(other), 1000f), "it can't be hurt");
                    h.assertTrue(body.skipAttackInteraction(other), "hitting it does nothing");
                    other.attack(body);
                    body.setPos(body.getX() + 5, body.getY() + 3, body.getZ());
                })
                .thenWaitUntil(() -> h.assertTrue(PrisonRealm.body(level).position().distanceTo(Vec3.atBottomCenterOf(st(h).pos())) < 0.01,
                        "it is anchored where it lies (on its next tick)"))
                .thenExecute(() -> {
                    PrisonRealmEntity body = PrisonRealm.body(level);
                    h.assertTrue(sealed(h) && inCell(h, captive), "nobody was released");
                    // Killed outright (a command): its body comes back; the seal holds.
                    body.kill(level);
                })
                .thenWaitUntil(() -> {
                    PrisonRealmEntity body = PrisonRealm.body(level);
                    h.assertTrue(body != null && !body.getUUID().equals(first[0]), "a lost body is raised again");
                })
                .thenExecute(() -> {
                    h.assertTrue(sealed(h) && inCell(h, captive) && !KitOwnership.get(level.getServer()).isClaimed(GojoCharacter.ID),
                            "still sealed, nothing granted");
                    h.assertTrue(level.getEntitiesOfClass(PrisonRealmEntity.class, new AABB(st(h).pos()).inflate(8)).size() == 1, "one body, no duplicates");
                    cleanUp(h, captive, other);
                })
                .thenSucceed();
    }

    // 7. The cell holds, whatever the captive does.
    @GameTest(maxTicks = 500, environment = "jjk-test:prison_f")
    public void theCellHoldsItsCaptiveAnywhere(GameTestHelper h) {
        fresh(h);
        ServerLevel level = h.getLevel();
        ServerPlayer captive = survivor(h, 3.5, 3.5);
        ItemStack cube = forged(h);
        h.startSequence()
                .thenExecute(() -> sealSelf(h, captive, cube))
                .thenWaitUntil(() -> h.assertTrue(sealed(h), "sealed"))
                .thenExecute(() -> {
                    Vec3 out = h.absoluteVec(new Vec3(6, 1, 6));
                    captive.teleportTo(level, out.x, out.y, out.z, Set.of(), 0f, 0f, false);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    h.assertTrue(inCell(h, captive), "teleported out: straight back in");
                    ServerLevel nether = level.getServer().getLevel(Level.NETHER);
                    captive.teleportTo(nether, 0.5, 70, 0.5, Set.of(), 0f, 0f, false);
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    h.assertTrue(captive.level() == level && inCell(h, captive), "another dimension: straight back in");
                    // Nothing in the cell can be broken, even in Creative.
                    BlockPos lock = PrisonRealm.lockPos(level.getServer(), 0);
                    captive.setGameMode(GameType.CREATIVE);
                    captive.gameMode.destroyBlock(lock);
                    h.assertTrue(level.getBlockState(lock).is(ProgressionBlocks.SEAL_LOCK), "the cell can't be broken");
                    captive.setGameMode(GameType.SURVIVAL);
                    cleanUp(h, captive);
                })
                .thenSucceed();
    }

    // 8. Dead (or offline) at the moment of a genuine release: it is applied, with the claim, once they are back.
    @GameTest(maxTicks = 500, environment = "jjk-test:prison_g")
    public void aReleaseOwedToADeadCaptiveIsAppliedWhenTheyAreBack(GameTestHelper h) {
        fresh(h);
        ServerLevel level = h.getLevel();
        ServerPlayer captive = survivor(h, 3.5, 3.5);
        ItemStack cube = forged(h);
        h.startSequence()
                .thenExecute(() -> sealSelf(h, captive, cube))
                .thenWaitUntil(() -> h.assertTrue(sealed(h), "sealed"))
                .thenExecute(() -> {
                    captive.setHealth(0f);
                    h.assertTrue(!captive.isAlive(), "dead in the cell");
                    h.assertTrue(PrisonRealm.release(level.getServer(), PrisonRealm.Release.RESCUE), "a rescue releases them meanwhile");
                })
                .thenWaitUntil(() -> h.assertTrue(st(h).phase() == PrisonRealmState.Phase.ITEM && st(h).pending().size() == 1, "the release is owed"))
                .thenExecute(() -> {
                    h.assertTrue(releases(captive) == 0, "nothing yet while they are dead");
                    // Back (respawned / rejoined).
                    captive.setHealth(captive.getMaxHealth());
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    h.assertTrue(st(h).pending().isEmpty(), "applied once");
                    h.assertTrue(releases(captive) == 1 && !gojo(h, captive), "counted once they are back (and still not Gojo)");
                    h.assertTrue(!inCell(h, captive), "and they are out");
                    cleanUp(h, captive);
                })
                .thenSucceed();
    }

    // 2. Getting away before the restraints reach out fails the seal; the cube drops.
    @GameTest(maxTicks = 300, environment = "jjk-test:prison_h")
    public void dodgingTheSealFailsItAndTheCubeDrops(GameTestHelper h) {
        fresh(h);
        ServerLevel level = h.getLevel();
        ServerPlayer user = survivor(h, 1.5, 3.5);
        ServerPlayer target = survivor(h, 5.5, 3.5);
        ItemStack cube = forged(h);
        UUID id = PrisonRealmItem.realmId(cube);
        h.startSequence()
                .thenExecute(() -> {
                    Vec3 u = user.position();
                    // Facing the target.
                    user.teleportTo(level, u.x, u.y, u.z, Set.of(), -90f, 10f, false);
                    user.setItemInHand(InteractionHand.MAIN_HAND, cube);
                    h.assertTrue(PrisonRealm.use(user, InteractionHand.MAIN_HAND), "thrown at the player in front");
                    h.assertTrue(target.getUUID().equals(st(h).captive()), "it is opening on the target");
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    Vec3 far = target.position().add(0, 0, 0);
                    Vec3 away = h.absoluteVec(new Vec3(5.5, 1, 3.5)).add(10, 0, 0);
                    target.teleportTo(level, away.x, far.y, away.z, Set.of(), 0f, 0f, false);
                })
                .thenWaitUntil(() -> h.assertTrue(st(h).phase() == PrisonRealmState.Phase.ITEM, "the seal failed"))
                .thenExecute(() -> {
                    h.assertTrue(PrisonRealm.body(level) == null, "no realm on the ground");
                    List<ItemEntity> cubes = level.getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(BlockPos.ZERO)).inflate(16),
                            e -> e.getItem().is(ProgressionItems.PRISON_REALM));
                    h.assertTrue(cubes.size() == 1 && id.equals(PrisonRealmItem.realmId(cubes.get(0).getItem())), "the cube dropped where it lay");
                    h.assertTrue(!KitOwnership.get(level.getServer()).isClaimed(GojoCharacter.ID), "nothing granted");
                    cleanUp(h, user, target);
                })
                .thenSucceed();
    }

    // 4, 6, 8. Sealed by someone else: no way out from inside; only a rescue lets them out (and they are the first: Gojo).
    @GameTest(maxTicks = 1000, environment = "jjk-test:prison_i")
    public void aPlayerSealedBySomeoneElseIsTrappedUntilRescued(GameTestHelper h) {
        fresh(h);
        ServerLevel level = h.getLevel();
        ServerPlayer user = survivor(h, 1.5, 3.5);
        ServerPlayer target = survivor(h, 5.5, 3.5);
        ItemStack cube = forged(h);
        int[] held = new int[1];
        h.startSequence()
                .thenExecute(() -> {
                    Vec3 u = user.position();
                    user.teleportTo(level, u.x, u.y, u.z, Set.of(), -90f, 10f, false);
                    user.setItemInHand(InteractionHand.MAIN_HAND, cube);
                    h.assertTrue(PrisonRealm.use(user, InteractionHand.MAIN_HAND), "thrown at the player in front");
                })
                .thenWaitUntil(() -> h.assertTrue(sealed(h) && target.getUUID().equals(st(h).captive()), "the target is sealed"))
                .thenExecute(() -> {
                    h.assertTrue(inCell(h, target) && !inCell(h, user), "the target is the one inside");
                    h.assertTrue(!st(h).selfSealed(), "sealed by someone else");
                    BlockPos lock = PrisonRealm.lockPos(level.getServer(), 0);
                    h.assertTrue(!level.getBlockState(lock).is(ProgressionBlocks.SEAL_LOCK), "their cell has no seal locks to break");
                })
                // Trying to break out anyway (every lock position, the core, for a good while): nothing.
                .thenExecute(() -> {
                    for (int t = 0; t < 4; t++) {
                        PrisonRealm.useLock(target, PrisonRealm.lockPos(level.getServer(), t));
                        PrisonRealm.useCore(target);
                    }
                })
                .thenIdle(120)
                .thenExecute(() -> {
                    h.assertTrue(sealed(h) && st(h).stage() == 0 && inCell(h, target), "no way out from inside");
                    Vec3 next = PrisonRealm.body(level).position().add(1.6, 0, 0);
                    user.teleportTo(level, next.x, next.y, next.z, Set.of(), 90f, 20f, false);
                    user.setShiftKeyDown(true);
                })
                .thenWaitUntil(() -> {
                    if (held[0]++ % 4 == 0 && PrisonRealm.body(level) != null) PrisonRealm.interact(user, PrisonRealm.body(level));
                    h.assertTrue(st(h).phase() == PrisonRealmState.Phase.ITEM, "opened from outside");
                })
                .thenExecute(() -> {
                    h.assertTrue(!gojo(h, target) && releases(target) == 1 && releases(user) == 0, "the rescued captive is counted; nobody is Gojo");
                    user.setShiftKeyDown(false);
                    cleanUp(h, user, target);
                })
                .thenSucceed();
    }

    // Mobs can be sealed too (never Gojo, no escape of their own); a rescue lets them out; a captive that vanishes frees the realm.
    @GameTest(maxTicks = 900, environment = "jjk-test:prison_j")
    public void mobsCanBeSealedAndLetOut(GameTestHelper h) {
        fresh(h);
        ServerLevel level = h.getLevel();
        ServerPlayer user = survivor(h, 1.5, 3.5);
        var cow = h.spawn(net.minecraft.world.entity.EntityTypes.COW, new Vec3(5.5, 1, 3.5));
        cow.setNoAi(true);
        ItemStack cube = forged(h);
        int[] held = new int[1];
        h.startSequence()
                .thenExecute(() -> {
                    Vec3 u = user.position();
                    user.teleportTo(level, u.x, u.y, u.z, Set.of(), -90f, 10f, false);
                    user.setItemInHand(InteractionHand.MAIN_HAND, cube);
                    h.assertTrue(PrisonRealm.use(user, InteractionHand.MAIN_HAND), "thrown at the cow");
                    h.assertTrue(cow.getUUID().equals(st(h).captive()), "it opens on the cow");
                })
                .thenWaitUntil(() -> h.assertTrue(sealed(h), "the cow is sealed"))
                .thenExecute(() -> {
                    BlockPos c = st(h).cell();
                    h.assertTrue(new AABB(c.getX(), c.getY(), c.getZ(), c.getX() + 13, c.getY() + 8, c.getZ() + 13).contains(cow.position()), "in the cell");
                    Vec3 out = h.absoluteVec(new Vec3(6, 1, 6));
                    cow.teleportTo(out.x, out.y, out.z);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    BlockPos c = st(h).cell();
                    h.assertTrue(new AABB(c.getX(), c.getY(), c.getZ(), c.getX() + 13, c.getY() + 8, c.getZ() + 13).contains(cow.position()), "held: put back");
                    Vec3 next = PrisonRealm.body(level).position().add(1.6, 0, 0);
                    user.teleportTo(level, next.x, next.y, next.z, Set.of(), 90f, 20f, false);
                    user.setShiftKeyDown(true);
                })
                .thenWaitUntil(() -> {
                    if (held[0]++ % 4 == 0 && PrisonRealm.body(level) != null) PrisonRealm.interact(user, PrisonRealm.body(level));
                    h.assertTrue(st(h).phase() == PrisonRealmState.Phase.ITEM, "let out by a rescue");
                })
                .thenExecute(() -> {
                    h.assertTrue(cow.isAlive() && cow.position().distanceTo(h.absoluteVec(new Vec3(5.5, 1, 3.5))) < 8, "the cow is back beside the realm");
                    h.assertTrue(!KitOwnership.get(level.getServer()).isClaimed(GojoCharacter.ID), "a mob never claims anything (nor does its rescuer)");
                    user.setShiftKeyDown(false);
                    // Sealed again, then it dies inside: the realm frees itself.
                    user.setItemInHand(InteractionHand.MAIN_HAND, PrisonRealmItem.create(st(h).realmId()));
                    Vec3 u = h.absoluteVec(new Vec3(1.5, 1, 3.5));
                    user.teleportTo(level, u.x, u.y, u.z, Set.of(), -90f, 10f, false);
                    cow.teleportTo(h.absoluteVec(new Vec3(5.5, 1, 3.5)).x, h.absoluteVec(new Vec3(5.5, 1, 3.5)).y, h.absoluteVec(new Vec3(5.5, 1, 3.5)).z);
                    for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(BlockPos.ZERO)).inflate(24),
                            e -> e.getItem().is(ProgressionItems.PRISON_REALM))) e.discard();
                    h.assertTrue(PrisonRealm.use(user, InteractionHand.MAIN_HAND), "thrown at the cow again");
                })
                .thenWaitUntil(() -> h.assertTrue(sealed(h), "sealed again"))
                .thenExecute(() -> cow.kill(level))
                .thenWaitUntil(() -> h.assertTrue(st(h).phase() == PrisonRealmState.Phase.ITEM, "a captive that died inside frees the realm"))
                .thenExecute(() -> cleanUp(h, user))
                .thenSucceed();
    }
}
