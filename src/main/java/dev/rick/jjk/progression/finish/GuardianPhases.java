package dev.rick.jjk.progression.finish;

import dev.rick.jjk.core.clash.ClashCommon;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.fx.Impact;
import dev.rick.jjk.core.net.StoryPayload;
import dev.rick.jjk.progression.contest.StrengthContests;
import dev.rick.jjk.progression.curse.CommonCurseEntity;
import dev.rick.jjk.progression.guardian.GuardianBody;
import dev.rick.jjk.progression.investigation.CursedRealms;
import dev.rick.jjk.progression.investigation.GuardianArena;
import dev.rick.jjk.progression.investigation.Incident;
import dev.rick.jjk.progression.investigation.InvestigationState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A Grade 1 guardian's fight in acts. As it falls past {@link #AT} of its health (two thirds, then under a third) the
 * fight stops for a {@link #BEAT_TICKS} beat: the guardian is held, untouchable, and its challengers are held too; on
 * their screens the camera cuts low on it and pushes in (client.finish.EyecatchCamera) under an anime title card naming
 * the act; and the arena itself changes. At the first beat four pillars grind up out of the floor (cover, and something
 * to fight round); at the second the floor's outer ring breaks and sinks. Then the fight goes on.
 *
 * <p>A guardian's own body ({@link GuardianBody}) can add to its beat (a roar, a transformation) and name its acts.
 * Everything built here is part of the arena, cleared with it when it closes.
 */
public final class GuardianPhases {
    public static final float[] AT = {0.65f, 0.30f};
    public static final int BEAT_TICKS = 50;

    /** A beat under way: the guardian, who is watching it, which act, how far in. */
    static final class Beat {
        final ServerLevel level;
        final UUID curse;
        final List<UUID> players;
        final int phase;
        @Nullable final BlockPos arena;
        int ticks;

        Beat(ServerLevel level, UUID curse, List<UUID> players, int phase, @Nullable BlockPos arena) {
            this.level = level;
            this.curse = curse;
            this.players = players;
            this.phase = phase;
            this.arena = arena;
        }
    }

    private static final Map<UUID, Integer> PASSED = new HashMap<>();
    private static final Map<UUID, Beat> BEATS = new HashMap<>();
    private static final Set<UUID> TEST_GUARDIANS = new HashSet<>();

    private GuardianPhases() {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(GuardianPhases::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            PASSED.clear();
            BEATS.clear();
            TEST_GUARDIANS.clear();
        });
    }

    public static boolean inBeat(Entity curse) {
        return BEATS.containsKey(curse.getUUID());
    }

    /** How many of its acts a guardian has passed (0 to {@link #AT}'s length). */
    public static int passed(Entity curse) {
        return PASSED.getOrDefault(curse.getUUID(), 0);
    }

    private static boolean guardian(CommonCurseEntity c) {
        return TEST_GUARDIANS.contains(c.getUUID()) || StrengthContests.isGuardian(c);
    }

    private static void tick(MinecraftServer server) {
        if (!BEATS.isEmpty()) for (Beat b : List.copyOf(BEATS.values())) step(server, b);
        if (server.getTickCount() % 2 != 0) return;
        // Every guardian someone is fighting, checked against its next act.
        Set<UUID> seen = new HashSet<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!(p.level() instanceof ServerLevel level) || !p.isAlive()) continue;
            for (CommonCurseEntity c : level.getEntitiesOfClass(CommonCurseEntity.class, p.getBoundingBox().inflate(32), e -> e.isAlive())) {
                if (!seen.add(c.getUUID()) || !guardian(c)) continue;
                check(level, c);
            }
        }
    }

    /** Starts its next act's beat if it has fallen past it (and it isn't busy being finished or in a contest). */
    static void check(ServerLevel level, CommonCurseEntity c) {
        int passed = passed(c);
        if (passed >= AT.length || inBeat(c) || c.getHealth() > c.getMaxHealth() * AT[passed]) return;
        if (Finishers.beingFinished(c) || StrengthContests.locked(c)) return;
        PASSED.put(c.getUUID(), passed + 1);
        beat(level, c, passed + 1);
    }

    /** The act's title, and the guardian's name under it. */
    static String title(CommonCurseEntity c, int phase) {
        if (c instanceof GuardianBody g) {
            String own = g.phaseTitle(phase);
            if (own != null && !own.isEmpty()) return own;
        }
        return phase == 1 ? "UNLEASHED" : "LAST STAND";
    }

    static String subtitle(CommonCurseEntity c) {
        Incident in = c.level().getServer() == null ? null : InvestigationState.get(c.level().getServer()).incident(c.incidentId());
        String name = in == null ? null : dev.rick.jjk.progression.investigation.Investigations.guardianName(in);
        return name != null && !name.isEmpty() ? name : c.getName().getString().toLowerCase(java.util.Locale.ROOT);
    }

    static void beat(ServerLevel level, CommonCurseEntity c, int phase) {
        c.holdForBeat(BEAT_TICKS);
        List<UUID> watching = new ArrayList<>();
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, c.getBoundingBox().inflate(28), p -> p.isAlive() && !p.isSpectator())) {
            watching.add(p.getUUID());
            ClashCommon.hold(p);
            ServerPlayNetworking.send(p, new StoryPayload(StoryPayload.CARD_LOW, title(c, phase), subtitle(c), phase == 1 ? 0xE8C060 : 0xE04040, BEAT_TICKS));
            Fx.play(level, "impact_eyecatch", c.position(), new Vec3(c.getId(), BEAT_TICKS * 50, phase), 1f, p.getId());
            Impact.frame(p, phase == 1 ? Impact.INK : Impact.NEGATIVE, 0.9f);
        }
        var arena = CursedRealms.arenaHere(c);
        BlockPos origin = arena != null && GuardianArena.ID.equals(arena.layout) ? arena.origin : null;
        BEATS.put(c.getUUID(), new Beat(level, c.getUUID(), watching, phase, origin));
        Vec3 at = c.position().add(0, c.getBbHeight() * 0.6, 0);
        Fx.sound(level, at, SoundEvents.WARDEN_ROAR, 1.2f, phase == 1 ? 0.8f : 0.6f);
        Fx.shake(level, at, 24, 0.9f, 20);
        Impact.dust(level, c.position(), 1.6f);
        if (c instanceof GuardianBody g) g.phaseBeat(level, phase);
    }

    static void step(MinecraftServer server, Beat b) {
        b.ticks++;
        for (UUID u : b.players) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p != null && p.isAlive()) ClashCommon.hold(p);
        }
        if (b.arena != null) change(b.level, b);
        if (b.ticks >= BEAT_TICKS) BEATS.remove(b.curse);
    }

    // --- The arena changes ---

    /** Where the first act's pillars rise: off the diagonals, so the way between challenger and guardian stays open. */
    static final int[][] PILLARS = {{6, 6}, {-7, 6}, {6, -7}, {-7, -7}};
    static final int PILLAR_HEIGHT = 5;

    private static void change(ServerLevel level, Beat b) {
        BlockPos o = b.arena;
        if (b.phase == 1) {
            // A layer every few ticks, grinding up.
            if (b.ticks % 5 != 0) return;
            int layer = b.ticks / 5 - 1;
            if (layer < 0 || layer > PILLAR_HEIGHT) return;
            for (int[] p : PILLARS) {
                for (int dx = 0; dx <= 1; dx++)
                    for (int dz = 0; dz <= 1; dz++) {
                        BlockPos at = o.offset(p[0] + dx, layer, p[1] + dz);
                        BlockState s = layer == PILLAR_HEIGHT ? Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState()
                                : (layer + dx + dz) % 3 == 0 ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
                        set(level, at, s);
                    }
                if (layer == PILLAR_HEIGHT) set(level, o.offset(p[0], PILLAR_HEIGHT + 1, p[1]), Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT, true));
                Vec3 base = Vec3.atBottomCenterOf(o.offset(p[0] + 1, 0, p[1] + 1));
                if (layer == 0) Impact.crack(level, base, Vec3.ZERO, 1.2f);
                Impact.dust(level, base, 0.8f);
            }
            Fx.sound(level, Vec3.atCenterOf(o), SoundEvents.PISTON_EXTEND, 0.8f, 0.5f);
        } else if (b.ticks == 12) {
            // The outer ring of the floor breaks and sinks a step.
            for (int x = -GuardianArena.RADIUS; x <= GuardianArena.RADIUS; x++)
                for (int z = -GuardianArena.RADIUS; z <= GuardianArena.RADIUS; z++) {
                    double d = Math.sqrt(x * x + z * z);
                    if (d < 7.5 || d > GuardianArena.RADIUS - 0.5 || (x * 31 + z * 17 & 7) < 3) continue;
                    set(level, o.offset(x, -1, z), Blocks.AIR.defaultBlockState());
                    set(level, o.offset(x, -2, z), (x + z) % 3 == 0 ? Blocks.COBBLED_DEEPSLATE.defaultBlockState() : Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState());
                }
            for (int i = 0; i < 10; i++) {
                double a = i * Math.PI * 2 / 10;
                Vec3 at = Vec3.atBottomCenterOf(o).add(Math.cos(a) * 9, -0.9, Math.sin(a) * 9);
                Impact.crack(level, at, new Vec3(Math.cos(a), 0, Math.sin(a)), 1.3f);
                Impact.dust(level, at, 1f);
            }
            Fx.sound(level, Vec3.atCenterOf(o), SoundEvents.GENERIC_EXPLODE.value(), 1f, 0.5f);
            Fx.shake(level, Vec3.atCenterOf(o), 32, 1.1f, 16);
        }
    }

    private static void set(ServerLevel level, BlockPos p, BlockState s) {
        level.setBlock(p, s, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    // --- Tests ---

    /** Treats this curse as a guardian (outside a real contract). */
    public static void trackForTest(CommonCurseEntity c) {
        TEST_GUARDIANS.add(c.getUUID());
    }

    public static void checkForTest(ServerLevel level, CommonCurseEntity c) {
        check(level, c);
    }

    /** Runs a beat in an arena centred on {@code origin} (as if the guardian stood in its guardian arena). */
    public static void beatInArenaForTest(ServerLevel level, CommonCurseEntity c, int phase, BlockPos origin) {
        c.holdForBeat(BEAT_TICKS);
        BEATS.put(c.getUUID(), new Beat(level, c.getUUID(), List.of(), phase, origin));
    }

    public static void stepForTest(MinecraftServer server) {
        if (!BEATS.isEmpty()) for (Beat b : List.copyOf(BEATS.values())) step(server, b);
    }
}
