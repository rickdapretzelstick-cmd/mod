package dev.rick.jjk.test;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.combat.CombatEvents;
import dev.rick.jjk.core.combat.HitResult;
import dev.rick.jjk.core.combat.Targeting;
import dev.rick.jjk.progression.CurseAggro;
import dev.rick.jjk.progression.CursePerception;
import dev.rick.jjk.progression.CursedEncounters;
import dev.rick.jjk.progression.ProgressionBlocks;
import dev.rick.jjk.progression.ProgressionItems;
import dev.rick.jjk.progression.curse.CursedEnergyShotEntity;
import dev.rick.jjk.progression.curse.FingerBearerEntity;
import dev.rick.jjk.progression.curse.FingerBearerEntity.Move;
import dev.rick.jjk.registry.ModEntities;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The Finger Bearer encounter: it rises in a battle room when someone enters, stays hidden from and harmless to anyone
 * who can't perceive it (unless they started the fight), each of its five attacks winds up visibly, lands once, can be
 * avoided, and recovers; walls stop its shots, rush and bursts; its defeat clears the room for good and leaves one
 * Cursed Finger; a lost spirit is raised again; death cancels what it had in flight; it lets go of a target who leaves
 * the room. Its own batch: the room registry and the seal ticks are world-wide.
 */
public class FingerBearerTests {
    private static final String ENV = "jjk-test:finger_bearer";
    /** Every resolved hit from one of its attacks, by target. */
    private static final Map<UUID, List<HitResult>> HITS = new ConcurrentHashMap<>();

    static {
        CombatEvents.HIT_RESOLVED.add(r -> {
            if (r.hit().source.startsWith("fb_")) HITS.computeIfAbsent(r.target().getUUID(), k -> new CopyOnWriteArrayList<>()).add(r);
        });
    }

    /** Hits from {@code source} that reached {@code p} (anything but refused or dodged). */
    private static long landed(ServerPlayer p, String source) {
        return HITS.getOrDefault(p.getUUID(), List.of()).stream()
                .filter(r -> r.hit().source.equals(source) && r.outcome() != HitResult.Outcome.INVALID && r.outcome() != HitResult.Outcome.WHIFF).count();
    }

    private static long anyLanded(ServerPlayer p) {
        return HITS.getOrDefault(p.getUUID(), List.of()).stream()
                .filter(r -> r.outcome() != HitResult.Outcome.INVALID && r.outcome() != HitResult.Outcome.WHIFF).count();
    }

    private static void setup(GameTestHelper h) {
        JJKConfig cfg = JJKConfig.get();
        cfg.general.autoAssignGojo = false;
        cfg.progression.enabled = true;
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(x, 0, z, Blocks.STONE);
    }

    private static ServerPlayer survivor(GameTestHelper h, double x, double z, boolean glasses) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.getAbilities().instabuild = false;
        p.getAbilities().invulnerable = false;
        p.getAbilities().mayfly = false;
        p.setPos(h.absoluteVec(new Vec3(x, 1, z)));
        // A mock player's "client" never reports it has loaded, which leaves it invulnerable: as a real player once in.
        p.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        if (glasses) p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ProgressionItems.CURSED_GLASSES));
        HITS.remove(p.getUUID());
        return p;
    }

    private static void move(GameTestHelper h, ServerPlayer p, double x, double z) {
        Vec3 a = h.absoluteVec(new Vec3(x, 1, z));
        p.teleportTo(a.x, a.y, a.z);
    }

    /** A bearer that makes no moves of its own (the test starts them), facing +x. */
    private static FingerBearerEntity bearer(GameTestHelper h, double x, double z) {
        FingerBearerEntity fb = ModEntities.FINGER_BEARER.create(h.getLevel(), EntitySpawnReason.MOB_SUMMONED);
        Vec3 at = h.absoluteVec(new Vec3(x, 1, z));
        fb.snapTo(at.x, at.y, at.z, -90f, 0f);
        fb.setHome(BlockPos.containing(at).below(), 12);
        h.getLevel().addFreshEntity(fb);
        fb.setRest(1_000_000);
        return fb;
    }

    private static void wallAtX(GameTestHelper h, int x) {
        for (int z = 0; z < 8; z++) for (int y = 1; y <= 4; y++) h.setBlock(x, y, z, Blocks.STONE);
    }

    private static List<FingerBearerEntity> bearers(GameTestHelper h) {
        return h.getLevel().getEntitiesOfClass(FingerBearerEntity.class, new AABB(h.absolutePos(BlockPos.ZERO)).inflate(16), e -> true);
    }

    private static BlockPos seal(GameTestHelper h, int x, int z) {
        BlockPos seal = h.absolutePos(new BlockPos(x, 0, z));
        h.getLevel().setBlock(seal, ProgressionBlocks.CURSED_SEAL.defaultBlockState(), 3);
        h.getLevel().scheduleTick(seal, ProgressionBlocks.CURSED_SEAL, 2);
        return seal;
    }

    private static CursedEncounters.Room room(GameTestHelper h, BlockPos seal) {
        ServerLevel level = h.getLevel();
        return CursedEncounters.find(level.getServer(), level.dimension().identifier().toString(), seal);
    }

    // --- Spawning in the battle room ---

    /** The room's realm arena, if it is open. */
    private static dev.rick.jjk.progression.investigation.InvestigationState.Arena arena(GameTestHelper h, CursedEncounters.Room room) {
        var st = dev.rick.jjk.progression.investigation.InvestigationState.get(h.getLevel().getServer());
        return dev.rick.jjk.progression.investigation.CursedRealms.arenaOf(st, dev.rick.jjk.progression.investigation.CursedRealms.ROOM + CursedEncounters.keyOf(room));
    }

    /** Finger Bearers in a room's realm arena. */
    private static List<FingerBearerEntity> realmBearers(GameTestHelper h, dev.rick.jjk.progression.investigation.InvestigationState.Arena a) {
        ServerLevel realm = dev.rick.jjk.progression.investigation.CursedRealms.level(h.getLevel().getServer());
        // By the whole entity list, not a box query: the far-off arena's chunk may not be entity-accessible to a mock player.
        List<FingerBearerEntity> out = new java.util.ArrayList<>();
        AABB box = new AABB(a.origin).inflate(20);
        for (net.minecraft.world.entity.Entity e : realm.getAllEntities()) {
            if (e instanceof FingerBearerEntity fb && !fb.isRemoved() && box.contains(fb.position())) out.add(fb);
        }
        return out;
    }

    @GameTest(maxTicks = 300, padding = 16, environment = ENV)
    public void enteringTheRoomPullsYouIntoItsRealmWhereItRises(GameTestHelper h) {
        setup(h);
        BlockPos seal = seal(h, 4, 4);
        h.runAfterDelay(20, () -> {
            CursedEncounters.Room r = room(h, seal);
            h.assertTrue(r != null && r.state == CursedEncounters.State.DORMANT, "registered and waiting");
            h.assertTrue(bearers(h).isEmpty(), "nothing rises in an empty room");
            ServerPlayer p = survivor(h, 1, 1, false);
            h.succeedWhen(() -> {
                CursedEncounters.Room room = room(h, seal);
                h.assertTrue(dev.rick.jjk.progression.investigation.CursedRealms.inRealm(p), "taken into the room's realm");
                var a = arena(h, room);
                h.assertTrue(a != null && a.inside().contains(p.getUUID()), "in its arena");
                h.assertTrue(room.state == CursedEncounters.State.ACTIVE && room.spirit != null, "the room is active with its spirit");
                List<FingerBearerEntity> found = realmBearers(h, a);
                h.assertTrue(found.size() == 1, "exactly one Finger Bearer, in the realm: " + found.size() + " near " + a.origin);
                h.assertTrue(bearers(h).isEmpty(), "and none in the room itself");
                FingerBearerEntity fb = found.getFirst();
                h.assertTrue(fb.getUUID().equals(room.spirit) && a.origin.equals(fb.home()), "it is the room's spirit, tied to the arena");
                h.assertTrue(CursePerception.requiresPerception(fb), "hidden from those who can't perceive curses");
                CursedEncounters.unload();
                CursedEncounters.Room again = room(h, seal);
                h.assertTrue(again.state == CursedEncounters.State.ACTIVE && fb.getUUID().equals(again.spirit), "spirit and state saved");
                var st = dev.rick.jjk.progression.investigation.InvestigationState.get(h.getLevel().getServer());
                dev.rick.jjk.progression.investigation.CursedRealms.close(h.getLevel().getServer(), st, a, false);
                h.assertTrue(!dev.rick.jjk.progression.investigation.CursedRealms.inRealm(p), "home when it closes");
                h.assertTrue(room(h, seal).state == CursedEncounters.State.DORMANT && room(h, seal).spirit == null, "an abandoned fight leaves the room waiting");
                p.discard();
            });
        });
    }

    @GameTest(maxTicks = 500, padding = 16, environment = ENV)
    public void aSpiritLostWithoutDyingIsRaisedAgain(GameTestHelper h) {
        setup(h);
        BlockPos seal = seal(h, 4, 4);
        ServerPlayer p = survivor(h, 1, 1, false);
        UUID[] first = new UUID[1];
        h.succeedWhen(() -> {
            CursedEncounters.Room room = room(h, seal);
            h.assertTrue(room != null && room.spirit != null, "spawned");
            var a = arena(h, room);
            h.assertTrue(a != null, "in its realm");
            if (first[0] == null) {
                first[0] = room.spirit;
                realmBearers(h, a).forEach(e -> e.discard());
                h.fail("removed; waiting for the realm to notice");
            }
            h.assertTrue(!room.spirit.equals(first[0]), "a new spirit was raised");
            h.assertTrue(realmBearers(h, a).size() == 1, "one spirit, not two: " + realmBearers(h, a).size());
            var st = dev.rick.jjk.progression.investigation.InvestigationState.get(h.getLevel().getServer());
            dev.rick.jjk.progression.investigation.CursedRealms.close(h.getLevel().getServer(), st, a, false);
            p.discard();
        });
    }

    // --- Perception ---

    @GameTest(maxTicks = 160, padding = 16, environment = ENV)
    public void unseenItNeitherTargetsNorHurtsThoseWhoCantPerceiveIt(GameTestHelper h) {
        setup(h);
        FingerBearerEntity fb = bearer(h, 1.5, 4);
        fb.setRest(0);
        ServerPlayer p = survivor(h, 3.5, 4, false);
        h.assertTrue(!Targeting.canTarget(fb, p), "its hits refuse someone who can't perceive it");
        h.runAfterDelay(40, () -> {
            h.assertTrue(fb.getTarget() == null, "it never takes them as a target");
            // Force its widest attack and a projectile at them anyway: neither may hurt them.
            fb.setRest(1_000_000);
            fb.forceMove(Move.BURST, p);
            h.runAfterDelay(FingerBearerEntity.BURST_END + 2, () -> {
                CursedEnergyShotEntity.fire(h.getLevel(), fb, fb.position().add(0, 2, 0), p.position().add(0, 1, 0).subtract(fb.position().add(0, 2, 0)),
                        CursedEnergyShotEntity.Kind.BLAST);
                h.runAfterDelay(15, () -> {
                    h.assertTrue(anyLanded(p) == 0 && p.getHealth() == p.getMaxHealth(), "burst, blast and splash pass through them");
                    // With the glasses it sees them, and they it: the fight starts.
                    p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ProgressionItems.CURSED_GLASSES));
                    h.runAfterDelay(25, () -> {
                        h.assertTrue(fb.getTarget() == p, "perceived, it takes them");
                        h.assertTrue(CurseAggro.isHostileTo(fb, p), "and is hostile");
                        // Taking the glasses off now doesn't end the fight (by design: no cheesing it).
                        p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                        h.runAfterDelay(20, () -> {
                            h.assertTrue(fb.getTarget() == p, "still hunting them without the glasses");
                            fb.discard();
                            p.discard();
                            h.succeed();
                        });
                    });
                });
            });
        });
    }

    @GameTest(maxTicks = 200, padding = 16, environment = ENV)
    public void itLetsGoOfATargetWhoLeavesTheRoom(GameTestHelper h) {
        setup(h);
        FingerBearerEntity fb = bearer(h, 2, 4);
        fb.setHome(h.absolutePos(new BlockPos(2, 0, 4)), 2);
        ServerPlayer p = survivor(h, 4.5, 4, true);
        h.runAfterDelay(25, () -> {
            h.assertTrue(fb.getTarget() == p, "takes the perceiving player in its room");
            move(h, p, 7.5, 7.5);
            h.runAfterDelay(5, () -> {
                h.assertTrue(fb.getTarget() == null, "lets go once they are out of the room");
                fb.discard();
                p.discard();
                h.succeed();
            });
        });
    }

    // --- The five attacks ---

    @GameTest(maxTicks = 80, padding = 16, environment = ENV)
    public void energyShotLandsOnceAfterAVisibleWindup(GameTestHelper h) {
        setup(h);
        FingerBearerEntity fb = bearer(h, 0.8, 3.5);
        ServerPlayer p = survivor(h, 7.2, 3.5, true);
        fb.forceMove(Move.SHOT, p);
        h.runAfterDelay(FingerBearerEntity.SHOT_FIRE - 1, () -> h.assertTrue(
                h.getLevel().getEntitiesOfClass(CursedEnergyShotEntity.class, fb.getBoundingBox().inflate(12)).isEmpty(), "nothing leaves its hand during the windup"));
        h.runAfterDelay(FingerBearerEntity.SHOT_FIRE + 14, () -> {
            h.assertValueEqual(landed(p, "fb_shot"), 1L, "the shot lands once");
            h.assertTrue(p.getHealth() < p.getMaxHealth() && p.getHealth() > p.getMaxHealth() - 8, "it hurts, never much");
            h.assertTrue(fb.move() == Move.NONE && fb.cooldown(Move.SHOT) > 0, "recovered, and on cooldown");
            fb.discard();
            p.discard();
            h.succeed();
        });
    }

    @GameTest(maxTicks = 80, padding = 16, environment = ENV)
    public void energyShotCanBeSidestepped(GameTestHelper h) {
        setup(h);
        FingerBearerEntity fb = bearer(h, 0.8, 1.5);
        ServerPlayer p = survivor(h, 7.2, 1.5, true);
        fb.forceMove(Move.SHOT, p);
        // A step aside just after it leaves the hand: the shot keeps its line.
        h.runAfterDelay(FingerBearerEntity.SHOT_FIRE + 1, () -> move(h, p, 7.2, 5.5));
        h.runAfterDelay(FingerBearerEntity.SHOT_FIRE + 20, () -> {
            h.assertValueEqual(landed(p, "fb_shot"), 0L, "dodged");
            h.assertTrue(p.getHealth() == p.getMaxHealth(), "unhurt");
            fb.discard();
            p.discard();
            h.succeed();
        });
    }

    @GameTest(maxTicks = 160, padding = 16, environment = ENV)
    public void chargedBlastThatMissesLeavesItOpenLonger(GameTestHelper h) {
        setup(h);
        FingerBearerEntity fb = bearer(h, 0.8, 1.5);
        ServerPlayer p = survivor(h, 7.2, 1.5, true);
        fb.forceMove(Move.BLAST, p);
        h.runAfterDelay(FingerBearerEntity.BLAST_FIRE - 2, () -> h.assertTrue(fb.move() == Move.BLAST
                && h.getLevel().getEntitiesOfClass(CursedEnergyShotEntity.class, fb.getBoundingBox().inflate(12)).isEmpty(), "still charging, two seconds in"));
        h.runAfterDelay(FingerBearerEntity.BLAST_FIRE + 1, () -> move(h, p, 7.2, 6.5));
        h.runAfterDelay(FingerBearerEntity.BLAST_LANDED_END + 2, () -> h.assertTrue(fb.move() == Move.BLAST, "a miss: still winded"));
        h.runAfterDelay(FingerBearerEntity.BLAST_END + 2, () -> {
            h.assertTrue(fb.move() == Move.NONE && fb.cooldown(Move.BLAST) > 0, "recovered, on cooldown");
            h.assertValueEqual(anyLanded(p), 0L, "nothing reached the player who stepped away");
            fb.discard();
            p.discard();
            h.succeed();
        });
    }

    @GameTest(maxTicks = 160, padding = 16, environment = ENV)
    public void chargedBlastThatLandsHitsOnceAndRecoversSooner(GameTestHelper h) {
        setup(h);
        FingerBearerEntity fb = bearer(h, 0.8, 3.5);
        ServerPlayer p = survivor(h, 7.2, 3.5, true);
        fb.forceMove(Move.BLAST, p);
        h.runAfterDelay(FingerBearerEntity.BLAST_LANDED_END + 2, () -> {
            h.assertValueEqual(landed(p, "fb_blast"), 1L, "the blast lands once");
            h.assertValueEqual(landed(p, "fb_blast_splash"), 0L, "its splash doesn't hit the one it struck again");
            h.assertTrue(p.getHealth() > p.getMaxHealth() - 14, "heavy, not lethal");
            h.assertTrue(fb.move() == Move.NONE, "a hit: the short recovery");
            fb.discard();
            p.discard();
            h.succeed();
        });
    }

    @GameTest(maxTicks = 80, padding = 16, environment = ENV)
    public void pointBlankBurstWarnsThenHitsOnlyThoseInsideAndInSight(GameTestHelper h) {
        setup(h);
        wallAtX(h, 1);
        FingerBearerEntity fb = bearer(h, 3.5, 3.5);
        ServerPlayer close = survivor(h, 5.5, 3.5, true);
        ServerPlayer behindWall = survivor(h, 0.5, 3.5, true);
        fb.forceMove(Move.BURST, close);
        h.runAfterDelay(FingerBearerEntity.BURST_FIRE - 1, () -> h.assertValueEqual(anyLanded(close), 0L, "nothing during the warning"));
        h.runAfterDelay(FingerBearerEntity.BURST_FIRE + 2, () -> {
            h.assertValueEqual(landed(close, "fb_burst"), 1L, "the burst throws back the one beside it, once");
            h.assertValueEqual(anyLanded(behindWall), 0L, "not through a wall");
            fb.discard();
            close.discard();
            behindWall.discard();
            h.succeed();
        });
    }

    @GameTest(maxTicks = 120, padding = 16, environment = ENV)
    public void brutalRushStrikesTheFirstInLineThenFollowsWithTheSmash(GameTestHelper h) {
        setup(h);
        FingerBearerEntity fb = bearer(h, 0.8, 3.5);
        ServerPlayer p = survivor(h, 7.2, 3.5, true);
        fb.forceMove(Move.RUSH, p);
        h.runAfterDelay(FingerBearerEntity.RUSH_WINDUP - 1, () -> h.assertTrue(fb.rushPhase() == 0, "a visible crouch before it goes"));
        h.succeedWhen(() -> {
            h.assertValueEqual(landed(p, "fb_rush"), 1L, "the blow lands once");
            h.assertTrue(fb.move() == Move.SMASH, "a connected rush is followed by the smash");
            h.assertTrue(fb.moveTick() < FingerBearerEntity.SMASH_HIT - 8, "with its windup still ahead: time to get away");
            fb.discard();
            p.discard();
        });
    }

    @GameTest(maxTicks = 120, padding = 16, environment = ENV)
    public void rushIntoAWallStaggersItAndHitsNobodyBehind(GameTestHelper h) {
        setup(h);
        wallAtX(h, 4);
        FingerBearerEntity fb = bearer(h, 0.8, 3.5);
        ServerPlayer p = survivor(h, 6.5, 3.5, true);
        fb.forceMove(Move.RUSH, p);
        boolean[] staggered = new boolean[1];
        h.onEachTick(() -> {
            if (fb.move() == Move.RUSH && fb.rushPhase() == 3) staggered[0] = true;
        });
        h.succeedWhen(() -> {
            h.assertTrue(staggered[0], "it staggered off the wall");
            h.assertTrue(fb.move() == Move.NONE, "and recovered");
            h.assertValueEqual(anyLanded(p), 0L, "nobody behind the wall was touched");
            h.assertTrue(fb.position().x < h.absoluteVec(new Vec3(4, 0, 0)).x, "it didn't pass through");
            fb.discard();
            p.discard();
        });
    }

    @GameTest(maxTicks = 80, padding = 16, environment = ENV)
    public void heavySmashLandsOnItsMarkedSpotOnly(GameTestHelper h) {
        setup(h);
        FingerBearerEntity fb = bearer(h, 2, 3.5);
        ServerPlayer stays = survivor(h, 2 + FingerBearerEntity.SMASH_REACH, 3.5, true);
        ServerPlayer steps = survivor(h, 2 + FingerBearerEntity.SMASH_REACH, 4.2, true);
        fb.forceMove(Move.SMASH, stays);
        h.runAfterDelay(8, () -> move(h, steps, 2 + FingerBearerEntity.SMASH_REACH, 7.6));
        h.runAfterDelay(FingerBearerEntity.SMASH_HIT - 1, () -> h.assertValueEqual(anyLanded(stays), 0L, "a long, open windup"));
        h.runAfterDelay(FingerBearerEntity.SMASH_HIT + 2, () -> {
            h.assertValueEqual(landed(stays, "fb_smash"), 1L, "it lands once on whoever stayed on the mark");
            h.assertValueEqual(anyLanded(steps), 0L, "stepping off the mark avoids it");
            h.assertTrue(stays.getHealth() > 6, "heavy, not a one-shot");
            fb.discard();
            stays.discard();
            steps.discard();
            h.succeed();
        });
    }

    // --- Death ---

    @GameTest(maxTicks = 100, padding = 16, environment = ENV)
    public void deathCancelsItsAttackAndTheShotInFlight(GameTestHelper h) {
        setup(h);
        FingerBearerEntity fb = bearer(h, 0.8, 3.5);
        ServerPlayer p = survivor(h, 7.2, 3.5, true);
        fb.forceMove(Move.BLAST, p);
        h.runAfterDelay(FingerBearerEntity.BLAST_FIRE + 1, () -> {
            h.assertTrue(!h.getLevel().getEntitiesOfClass(CursedEnergyShotEntity.class, fb.getBoundingBox().inflate(12)).isEmpty(), "the blast is in flight");
            fb.hurtServer(h.getLevel(), h.getLevel().damageSources().genericKill(), 10_000f);
            h.runAfterDelay(2, () -> {
                h.assertTrue(h.getLevel().getEntitiesOfClass(CursedEnergyShotEntity.class, fb.getBoundingBox().inflate(16)).isEmpty(), "its energy dies with it");
                h.assertTrue(fb.move() == Move.NONE, "no move survives its death");
                h.runAfterDelay(10, () -> {
                    h.assertValueEqual(anyLanded(p), 0L, "nothing landed after it died");
                    p.discard();
                    h.succeed();
                });
            });
        });
    }

    @GameTest(maxTicks = 400, padding = 16, environment = ENV)
    public void defeatInItsRealmClearsTheRoomForGoodAndGivesTheKillerExactlyOneFinger(GameTestHelper h) {
        setup(h);
        BlockPos seal = seal(h, 4, 4);
        ServerPlayer p = survivor(h, 1, 1, true);
        Vec3 start = p.position();
        boolean[] killed = new boolean[1];
        long[] at = new long[1];
        dev.rick.jjk.progression.investigation.InvestigationState.Arena[] arena = new dev.rick.jjk.progression.investigation.InvestigationState.Arena[1];
        h.succeedWhen(() -> {
            CursedEncounters.Room room = room(h, seal);
            h.assertTrue(room != null && room.state != CursedEncounters.State.DORMANT, "the spirit has risen");
            if (!killed[0]) {
                arena[0] = arena(h, room);
                h.assertTrue(arena[0] != null && !realmBearers(h, arena[0]).isEmpty(), "in its realm: " + (arena[0] == null ? "no arena" : realmBearers(h, arena[0]).size()));
                FingerBearerEntity fb = realmBearers(h, arena[0]).getFirst();
                // Two last blows in the same tick: still one defeat, one finger.
                fb.hurtServer((ServerLevel) fb.level(), fb.level().damageSources().playerAttack(p), 10_000f);
                fb.die(fb.level().damageSources().playerAttack(p));
                killed[0] = true;
                at[0] = h.getTick();
                h.fail("killed; waiting");
            }
            h.assertTrue(room.state == CursedEncounters.State.CLEARED, "the room is cleared");
            int fingers = 0;
            for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                if (p.getInventory().getItem(i).is(ProgressionItems.CURSED_FINGER)) fingers += p.getInventory().getItem(i).getCount();
            }
            h.assertTrue(fingers == 1, "exactly one Cursed Finger, in the killer's hands: " + fingers);
            h.assertTrue(!dev.rick.jjk.progression.investigation.CursedRealms.inRealm(p) && p.position().distanceTo(start) < 3, "home, where they stood in the room");
            var st = dev.rick.jjk.progression.investigation.InvestigationState.get(h.getLevel().getServer());
            h.assertTrue(dev.rick.jjk.progression.investigation.CursedRealms.arenaOf(st, arena[0].incident) == null, "the realm is gone");
            h.assertTrue(h.getTick() - at[0] > 2 * 40 + 10, "waiting for two more seal checks");
            h.assertTrue(bearers(h).isEmpty() && !dev.rick.jjk.progression.investigation.CursedRealms.inRealm(p), "a cleared room never takes anyone again");
            CursedEncounters.unload();
            h.assertTrue(room(h, seal).state == CursedEncounters.State.CLEARED, "cleared is saved");
            p.discard();
        });
    }

    // --- The Grade 1 wall: immunity, reactions, area denial, the second phase ---

    @GameTest(maxTicks = 40, padding = 16, environment = ENV)
    public void ordinaryWeaponsDontTouchItACursedToolDoes(GameTestHelper h) {
        setup(h);
        ServerPlayer p = survivor(h, 2, 3, true);
        FingerBearerEntity fb = bearer(h, 4, 3);
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.NETHERITE_SWORD));
        fb.hurtServer(h.getLevel(), h.getLevel().damageSources().playerAttack(p), 20f);
        h.assertTrue(fb.getHealth() == fb.getMaxHealth(), "a netherite sword does nothing");
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ProgressionItems.CURSED_CLEAVER));
        fb.setInvulnerableTime(0);
        fb.hurtServer(h.getLevel(), h.getLevel().damageSources().playerAttack(p), 20f);
        h.assertTrue(fb.getHealth() < fb.getMaxHealth(), "a cursed tool does");
        h.assertTrue(fb.curseGrade() == dev.rick.jjk.progression.grade.CurseGrade.GRADE_1, "Grade 1");
        h.succeed();
    }

    @GameTest(maxTicks = 80, padding = 16, environment = ENV)
    public void atHalfHealthItEnragesAndThrowsEveryoneBack(GameTestHelper h) {
        setup(h);
        ServerPlayer p = survivor(h, 3, 3, true);
        FingerBearerEntity fb = bearer(h, 5, 3);
        fb.setHealth(fb.getMaxHealth() * 0.45f);
        h.succeedWhen(() -> {
            h.assertTrue(fb.enraged(), "enraged");
            h.assertTrue(landed(p, "fb_enrage") >= 1, "the roar throws them back");
        });
    }

    @GameTest(maxTicks = 120, padding = 16, environment = ENV)
    public void theLeapLandsOnTheMarkedSpot(GameTestHelper h) {
        setup(h);
        ServerPlayer stays = survivor(h, 6.5, 2.5, true);
        ServerPlayer leaves = survivor(h, 6.5, 4.5, true);
        FingerBearerEntity fb = bearer(h, 1, 4);
        fb.forceMove(Move.LEAP, stays);
        h.runAfterDelay(4, () -> move(h, leaves, 1, 7.5));
        h.succeedWhen(() -> {
            stays.setHealth(stays.getMaxHealth());
            h.assertTrue(landed(stays, "fb_leap") == 1, "it comes down on whoever stayed (move " + fb.move() + " t" + fb.moveTick()
                    + " at " + h.relativeVec(fb.position()) + ", target at " + h.relativeVec(stays.position()) + ")");
            h.assertTrue(landed(leaves, "fb_leap") == 0, "not on whoever got out of the ring");
        });
    }

    @GameTest(maxTicks = 60, padding = 16, environment = ENV)
    public void lingeringBehindItEarnsABackhand(GameTestHelper h) {
        setup(h);
        ServerPlayer p = survivor(h, 2, 3, true);
        FingerBearerEntity fb = bearer(h, 4, 3);
        fb.forceMove(Move.SPIN, p);
        h.succeedWhen(() -> h.assertTrue(landed(p, "fb_spin") == 1, "spun on"));
    }

    @GameTest(maxTicks = 200, padding = 16, environment = ENV)
    public void aBlastLeavesAPoolThatHurtsWhoeverStandsInIt(GameTestHelper h) {
        setup(h);
        ServerPlayer p = survivor(h, 7.2, 3.5, true);
        FingerBearerEntity fb = bearer(h, 0.8, 3.5);
        fb.forceMove(Move.BLAST, p);
        h.succeedWhen(() -> {
            p.setHealth(p.getMaxHealth());
            if (fb.pools() > 0) move(h, p, 7.2, 3.5);
            h.assertTrue(fb.pools() >= 1, "a pool lingers where it burst (move " + fb.move() + " t" + fb.moveTick() + ", target " + fb.getTarget() + ")");
            h.assertTrue(landed(p, "fb_pool") >= 1, "standing in it hurts");
        });
    }
}
