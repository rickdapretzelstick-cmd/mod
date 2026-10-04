package dev.rick.jjk.progression.investigation;

import dev.rick.jjk.JJK;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.net.FxPayload;
import dev.rick.jjk.progression.curse.CommonCurseEntity;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cursed realms: pocket spaces a curse makes of the place it haunts, built as arenas in one shared void dimension
 * ({@code jjk:cursed_realm}). Each arena takes a slot (its own patch of the void, far from the others), is built from a
 * {@link Layout} when the first player is pulled in, and is cleared back to empty void when it closes: no new dimension
 * per incident, nothing left behind.
 *
 * <ul>
 *   <li><b>Entry</b>: whoever sets off the incident ({@link Investigations}) is pulled in; anyone setting it off while it
 *   is open joins the same arena. Where they came from is saved.</li>
 *   <li><b>Victory</b>: the last curse falls; a few seconds later everyone inside is sent back where they came from.</li>
 *   <li><b>Death</b>: the player respawns as usual (the realm is not a respawn point); the arena carries on for the rest.</li>
 *   <li><b>Abandoned</b>: nobody inside for a minute (everyone died, fled, logged out), or the server restarted: the arena
 *   is cleared, its curses go, and the incident is open again to retry.</li>
 *   <li><b>Safety</b>: falling out of the arena puts you back on its ground; a player who logs in inside the realm without
 *   an arena to be in is sent home.</li>
 * </ul>
 */
public final class CursedRealms {
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, JJK.id("cursed_realm"));
    /** Slots are this far apart in the void. */
    static final int SPACING = 512;
    static final int BASE_Y = 80;
    /** Half-width and height of the box an arena may use (and that is cleared). */
    static final int RADIUS = 26, HEIGHT = 40;
    static final int WIN_DELAY = 60, ABANDON_AFTER = 1200;

    /** How an arena looks, where players land and where its curses wait. */
    public interface Layout {
        void build(ServerLevel level, BlockPos origin, RandomSource random);

        /** Where players arrive, relative to the origin. */
        Vec3 arrival();

        /** Where the curses appear, relative to the origin. */
        List<Vec3> curseSpots();

        /** Pulled in from above (and so given a slow fall), or set down. */
        default boolean fromAbove() {
            return false;
        }

        /** Arena radius for leashing its curses. */
        default int radius() {
            return 14;
        }

        String entryLine();
    }

    private static final Map<String, Layout> LAYOUTS = Map.of("cliff_realm", new CliffRealm(), "mine_realm", new MineRealm());

    private CursedRealms() {}

    public static boolean hasLayout(String id) {
        return LAYOUTS.containsKey(id);
    }

    /**
     * Where arenas are built when the world has no realm dimension (a world whose dimensions were fixed before the mod,
     * or a test server): far out over the overworld, high in the sky, still one slot per arena and cleared after.
     */
    static final int FALLBACK_X = 12_000_000, FALLBACK_Z = 12_000_000, FALLBACK_Y = 272;

    /** The level arenas live in: the realm dimension, or the overworld's far sky if the world has none. */
    public static ServerLevel level(MinecraftServer server) {
        ServerLevel l = server.getLevel(DIMENSION);
        return l != null ? l : server.overworld();
    }

    private static boolean dedicated(MinecraftServer server) {
        return server.getLevel(DIMENSION) != null;
    }

    /** Whether an entity is in a cursed realm (the dimension, or the overworld fallback's region). */
    public static boolean inRealm(Entity e) {
        if (e.level().dimension() == DIMENSION) return true;
        MinecraftServer s = e.level().getServer();
        return s != null && !dedicated(s) && e.level().dimension() == Level.OVERWORLD && e.getX() > FALLBACK_X - 4000 && e.getZ() > FALLBACK_Z - 4000;
    }

    static BlockPos origin(MinecraftServer server, int slot) {
        return dedicated(server) ? new BlockPos(slot * SPACING, BASE_Y, 0) : new BlockPos(FALLBACK_X + slot * SPACING, FALLBACK_Y, FALLBACK_Z);
    }

    @Nullable
    public static InvestigationState.Arena arenaOf(InvestigationState st, String incident) {
        for (InvestigationState.Arena a : st.arenas.values()) if (a.incident.equals(incident)) return a;
        return null;
    }

    @Nullable
    static InvestigationState.Arena arenaAt(InvestigationState st, Vec3 pos) {
        for (InvestigationState.Arena a : st.arenas.values()) {
            if (Math.abs(pos.x - a.origin.getX()) <= RADIUS + 8 && Math.abs(pos.z - a.origin.getZ()) <= RADIUS + 8) return a;
        }
        return null;
    }

    // --- Entering ---

    /** Pulls a player into the incident's realm (building it if it isn't open yet). False if there is no realm to go to. */
    static boolean enter(ServerPlayer p, Incident in, InvestigationState st) {
        IncidentTemplate t = in.def();
        MinecraftServer server = p.level().getServer();
        ServerLevel realm = level(server);
        if (t == null || !t.usesRealm()) return false;
        Layout layout = LAYOUTS.get(t.realm());
        InvestigationState.Arena a = arenaOf(st, in.id);
        if (a == null) {
            int slot = 0;
            while (st.arenas.containsKey(slot)) slot++;
            a = new InvestigationState.Arena(slot, in.id, t.realm(), origin(server, slot));
            if (!dedicated(server)) JJK.LOGGER.warn("[investigations] this world has no {} dimension: the realm is built in the overworld's far sky", DIMENSION.identifier());
            a.openedAt = server.overworld().getGameTime();
            st.arenas.put(slot, a);
            clear(realm, a.origin);
            layout.build(realm, a.origin, RandomSource.create(in.id.hashCode()));
            Investigations.spawnCurses(realm, in, a.origin, layout.curseSpots(), layout.radius());
            in.state = Incident.State.ACTIVE;
            in.changedAt = server.overworld().getGameTime();
            JJK.LOGGER.info("[investigations] realm {} opened for incident {} ({})", slot, in.id, t.realm());
        }
        if (!inRealm(p)) st.returns.put(p.getUUID(), new InvestigationState.Return(p.level().dimension().identifier().toString(), p.position(), p.getYRot()));
        in.participants.add(p.getUUID());
        a.inside.add(p.getUUID());
        a.emptyTicks = 0;
        st.markDirty();
        Vec3 at = Vec3.atBottomCenterOf(a.origin).add(layout.arrival());
        // The pull: a black flash where they were, then the realm.
        Fx.play((ServerLevel) p.level(), "curse_realm_pull", p.position().add(0, 1, 0), Vec3.ZERO, 1f);
        p.teleport(new TeleportTransition(realm, at, Vec3.ZERO, p.getYRot(), 10f, Set.<Relative>of(), TeleportTransition.DO_NOTHING));
        p.fallDistance = 0;
        p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 70, 0, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 90, 0, false, false));
        if (layout.fromAbove()) p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0, false, false));
        realm.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_HEARTBEAT, net.minecraft.sounds.SoundSource.AMBIENT, 2f, 0.6f);
        realm.playSound(null, at.x, at.y, at.z, SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD.value(), net.minecraft.sounds.SoundSource.AMBIENT, 1.5f, 0.7f);
        ServerPlayNetworking.send(p, new FxPayload("curse_realm_pull", at.add(0, 1, 0), Vec3.ZERO, 1f, -1));
        p.sendOverlayMessage(Component.literal(layout.entryLine()).withStyle(net.minecraft.ChatFormatting.DARK_RED, net.minecraft.ChatFormatting.ITALIC));
        return true;
    }

    // --- Running ---

    static void tick(MinecraftServer server, InvestigationState st) {
        ServerLevel realm = level(server);
        if (st.arenas.isEmpty()) return;
        for (InvestigationState.Arena a : List.copyOf(st.arenas.values())) {
            Incident in = st.incidents.get(a.incident);
            Layout layout = LAYOUTS.get(a.layout);
            if (in == null || layout == null) {
                close(server, st, a, false);
                continue;
            }
            // Who is actually inside (alive, in the realm, within this arena).
            a.inside.removeIf(id -> {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                return p == null || !p.isAlive() || !inRealm(p) || arenaAt(st, p.position()) != a;
            });
            for (ServerPlayer p : realm.players()) {
                if (inRealm(p) && arenaAt(st, p.position()) == a && p.isAlive()) {
                    a.inside.add(p.getUUID());
                    keepInside(p, a, layout);
                }
            }
            if (in.state == Incident.State.COMPLETE) {
                if (a.wonTicks < 0) a.wonTicks = 0;
                if (++a.wonTicks >= WIN_DELAY) close(server, st, a, true);
                continue;
            }
            if (a.inside.isEmpty()) {
                if (++a.emptyTicks >= ABANDON_AFTER) close(server, st, a, false);
            } else {
                a.emptyTicks = 0;
            }
        }
    }

    /** Fell off the edge of the realm: back onto its ground, a little hurt, never killed by it. */
    private static void keepInside(ServerPlayer p, InvestigationState.Arena a, Layout layout) {
        Vec3 o = Vec3.atBottomCenterOf(a.origin);
        if (p.getY() < o.y - 18 || Math.abs(p.getX() - o.x) > RADIUS + 4 || Math.abs(p.getZ() - o.z) > RADIUS + 4) {
            Vec3 at = o.add(layout.arrival());
            p.teleportTo(at.x, at.y, at.z);
            p.setDeltaMovement(Vec3.ZERO);
            p.fallDistance = 0;
            if (p.getHealth() > 3) p.hurtServer((ServerLevel) p.level(), p.damageSources().fellOutOfWorld(), 2f);
            p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0, false, false));
        }
    }

    /** Ends an arena: everyone inside goes home, its curses vanish (if any are left), its blocks are cleared. */
    public static void close(MinecraftServer server, InvestigationState st, InvestigationState.Arena a, boolean won) {
        ServerLevel realm = level(server);
        Incident in = st.incidents.get(a.incident);
        for (ServerPlayer p : List.copyOf(realm.players())) {
            if (inRealm(p) && arenaAt(st, p.position()) == a) sendHome(p, st);
        }
        clear(realm, a.origin);
        st.arenas.remove(a.slot);
        if (!won && in != null && in.state == Incident.State.ACTIVE) {
            // Nobody finished it: the place goes quiet again, and can be tried again.
            in.state = Incident.State.OPEN;
            in.curses.clear();
            in.changedAt = server.overworld().getGameTime();
        }
        st.markDirty();
        JJK.LOGGER.info("[investigations] realm {} closed ({})", a.slot, won ? "won" : "abandoned");
    }

    /** Sends a player back where they came from (or to the world spawn if that's lost). */
    public static void sendHome(ServerPlayer p, InvestigationState st) {
        MinecraftServer server = p.level().getServer();
        InvestigationState.Return r = st.returns.remove(p.getUUID());
        st.markDirty();
        ServerLevel to = null;
        Vec3 at = null;
        float yaw = p.getYRot();
        if (r != null) {
            to = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(r.dimension())));
            at = r.pos();
            yaw = r.yaw();
        }
        if (to == null || at == null) {
            TeleportTransition t = p.findRespawnPositionAndUseSpawnBlock(false, TeleportTransition.DO_NOTHING);
            p.teleport(t);
        } else {
            p.teleport(new TeleportTransition(to, at, Vec3.ZERO, yaw, p.getXRot(), Set.<Relative>of(), TeleportTransition.DO_NOTHING));
        }
        p.fallDistance = 0;
        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0, false, false));
    }

    /** Clears an arena's box back to void, and anything left standing in it. */
    static void clear(ServerLevel realm, BlockPos origin) {
        AABB box = new AABB(origin.getX() - RADIUS, origin.getY() - 20, origin.getZ() - RADIUS, origin.getX() + RADIUS + 1, origin.getY() + HEIGHT, origin.getZ() + RADIUS + 1);
        for (int cx = (origin.getX() - RADIUS) >> 4; cx <= (origin.getX() + RADIUS) >> 4; cx++) {
            for (int cz = (origin.getZ() - RADIUS) >> 4; cz <= (origin.getZ() + RADIUS) >> 4; cz++) realm.getChunk(cx, cz);
        }
        for (Entity e : realm.getEntities((Entity) null, box, e -> !(e instanceof ServerPlayer))) e.discard();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int y = origin.getY() - 20; y < origin.getY() + HEIGHT; y++) {
            for (int x = origin.getX() - RADIUS; x <= origin.getX() + RADIUS; x++) {
                for (int z = origin.getZ() - RADIUS; z <= origin.getZ() + RADIUS; z++) {
                    m.set(x, y, z);
                    if (!realm.getBlockState(m).isAir()) realm.setBlock(m, air, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
        }
    }

    // --- Layouts ---

    private static void set(ServerLevel l, BlockPos p, BlockState s) {
        l.setBlock(p, s, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    /**
     * The cliff, from the other side: the edge they jumped from, but broken off and hanging in a dark red void. Ragged
     * stone, a cracked face rising on one side, dead brush, a few cold soul lanterns. They fall into it from above.
     */
    static final class CliffRealm implements Layout {
        @Override
        public void build(ServerLevel l, BlockPos o, RandomSource r) {
            for (int x = -15; x <= 15; x++) {
                for (int z = -15; z <= 15; z++) {
                    double d = Math.sqrt(x * x + z * z) + r.nextDouble() * 2.2;
                    if (d > 14.5) continue;
                    int depth = (int) Math.max(1, 6 - d / 2.5) + r.nextInt(2);
                    for (int y = 0; y < depth; y++) {
                        BlockState s = y == 0 ? (r.nextInt(7) == 0 ? Blocks.TUFF.defaultBlockState() : Blocks.DEEPSLATE.defaultBlockState())
                                : Blocks.BLACKSTONE.defaultBlockState();
                        set(l, o.offset(x, -y, z), s);
                    }
                    if (r.nextInt(18) == 0 && d < 12) set(l, o.offset(x, 1, z), Blocks.DEAD_BUSH.defaultBlockState());
                }
            }
            // The cliff face: a jagged wall along the north side.
            for (int x = -12; x <= 12; x++) {
                int h = 6 + r.nextInt(6) - Math.abs(x) / 3;
                for (int y = 1; y <= h; y++) for (int z = -14; z <= -12 + (y < 3 ? 1 : 0); z++) {
                    if (Math.sqrt(x * x + z * z) <= 14.5) set(l, o.offset(x, y, z), y % 4 == 0 ? Blocks.COBBLED_DEEPSLATE.defaultBlockState() : Blocks.DEEPSLATE.defaultBlockState());
                }
            }
            // Cold light.
            for (int[] c : new int[][] {{8, 6}, {-9, 4}, {3, -9}, {-4, 10}}) {
                set(l, o.offset(c[0], 1, c[1]), Blocks.COBBLED_DEEPSLATE_WALL.defaultBlockState());
                set(l, o.offset(c[0], 2, c[1]), Blocks.SOUL_LANTERN.defaultBlockState());
            }
        }

        @Override
        public Vec3 arrival() {
            return new Vec3(0, 14, 4);
        }

        @Override
        public List<Vec3> curseSpots() {
            List<Vec3> out = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                double a = Mth.TWO_PI * i / 6;
                out.add(new Vec3(Math.cos(a) * 7, 1.2, Math.sin(a) * 7 - 2));
            }
            return out;
        }

        @Override
        public boolean fromAbove() {
            return true;
        }

        @Override
        public String entryLine() {
            return "You never hit the ground. The air is thick, and wrong.";
        }
    }

    /** The mine, as the curse remembers it: a low cavern of wet rock, rails that go nowhere, webs. */
    static final class MineRealm implements Layout {
        @Override
        public void build(ServerLevel l, BlockPos o, RandomSource r) {
            int hx = 12, hz = 9, h = 7;
            for (int x = -hx - 1; x <= hx + 1; x++) {
                for (int z = -hz - 1; z <= hz + 1; z++) {
                    for (int y = -1; y <= h; y++) {
                        boolean shell = x == -hx - 1 || x == hx + 1 || z == -hz - 1 || z == hz + 1 || y == -1 || y == h;
                        // Uneven walls and ceiling.
                        boolean rough = !shell && (y >= h - 1 - r.nextInt(2)) && r.nextInt(3) == 0;
                        if (shell || rough) set(l, o.offset(x, y, z), r.nextInt(5) == 0 ? Blocks.TUFF.defaultBlockState() : Blocks.DEEPSLATE.defaultBlockState());
                    }
                }
            }
            for (int x = -hx; x <= hx; x++) set(l, o.offset(x, 0, 0), Blocks.RAIL.defaultBlockState());
            for (int i = 0; i < 6; i++) {
                int x = -hx + 2 + r.nextInt(2 * hx - 3), z = (r.nextBoolean() ? 1 : -1) * (3 + r.nextInt(hz - 3));
                for (int y = 0; y < h; y++) set(l, o.offset(x, y, z), Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState());
            }
            for (int i = 0; i < 10; i++) set(l, o.offset(-hx + r.nextInt(2 * hx), h - 1, -hz + r.nextInt(2 * hz)), Blocks.COBWEB.defaultBlockState());
            for (int[] c : new int[][] {{-hx, -hz}, {-hx, hz}, {hx, -hz}, {hx, hz}, {0, -hz}, {0, hz}}) {
                set(l, o.offset(c[0], 3, c[1]), Blocks.SOUL_LANTERN.defaultBlockState());
                set(l, o.offset(c[0], 4, c[1]), Blocks.IRON_CHAIN.defaultBlockState());
            }
        }

        @Override
        public Vec3 arrival() {
            return new Vec3(-10, 0.1, 0);
        }

        @Override
        public List<Vec3> curseSpots() {
            return List.of(new Vec3(8, 0.2, 3), new Vec3(9, 0.2, -3), new Vec3(6, 0.2, 0), new Vec3(4, 0.2, 5), new Vec3(4, 0.2, -5));
        }

        @Override
        public int radius() {
            return 13;
        }

        @Override
        public String entryLine() {
            return "The tunnel behind you is gone. Something is scraping in the dark.";
        }
    }
}
