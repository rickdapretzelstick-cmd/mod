package dev.rick.jjk.progression.investigation;

import dev.rick.jjk.JJK;
import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.net.FxPayload;
import dev.rick.jjk.core.net.NewsBoardPayload;
import dev.rick.jjk.progression.CursePerception;
import dev.rick.jjk.progression.ProgressionBlocks;
import dev.rick.jjk.progression.curse.CommonCurseEntity;
import dev.rick.jjk.progression.grade.CurseGrade;
import dev.rick.jjk.progression.mastery.Mastery;
import dev.rick.jjk.progression.mastery.MasteryTree;
import dev.rick.jjk.progression.mastery.MasteryTrees;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Investigations: the loop that sends a sorcerer out into the world.
 *
 * <ol>
 *   <li>A village a player walks into gets a <b>news board</b> by its bell, and the village has things to report:
 *   incidents generated from {@link IncidentTemplate}s at real places around it ({@link Sites}), written up as local
 *   news ({@link ReportWriter}).</li>
 *   <li>The report says roughly where; the place has its physical traces, and <b>cursed residue</b> (only for those who
 *   perceive curses) leads the last stretch to it. Nothing marks it on a map or a HUD.</li>
 *   <li>At the place, the incident's <b>trigger</b> sets it off: jumping from the cliff edge, going down the mine,
 *   walking into the pasture. Its curses appear, in the open world or in a {@link CursedRealms cursed realm}.</li>
 *   <li>When the last of them is exorcised the incident is <b>complete</b>: everyone who took part gets the
 *   investigation bonus (on top of the exorcisms' own Mastery), and the board posts a follow-up.</li>
 * </ol>
 * Left alone for three days, a report goes stale ({@link Incident.State#EXPIRED}) and something else gets reported.
 */
public final class Investigations {
    private static final int STALE_TICKS = 72000;
    /** Per incident, per player, per tree: Mastery paid out by its curses (for where the bonus goes). */
    private static final Map<String, Map<UUID, Map<String, Integer>>> EARNED = new HashMap<>();

    private Investigations() {}

    public static void init() {
        IncidentTemplate.load();
        LodgeScope.init();
        ServerTickEvents.END_SERVER_TICK.register(Investigations::tick);
        ServerLifecycleEvents.SERVER_STARTED.register(Investigations::onStart);
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> InvestigationState.get(s).save());
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            InvestigationState.unload();
            EARNED.clear();
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof CommonCurseEntity c && !c.incidentId().isEmpty() && entity.level().getServer() != null) {
                curseFell(entity.level().getServer(), c);
            }
            if (entity instanceof CursedAnchorEntity a && !a.incidentId().isEmpty() && entity.level().getServer() != null) {
                MinecraftServer server = entity.level().getServer();
                InvestigationState st = InvestigationState.get(server);
                Incident in = st.incidents.get(a.incidentId());
                if (in != null && in.state == Incident.State.ACTIVE) {
                    objectiveMet(server, in, st, source.getEntity() instanceof ServerPlayer p ? p : null);
                }
            }
        });
        // Participation: who dealt a contract's curses how much (credit needs real part in the fight, not the last hit).
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
            String incident = entity instanceof CommonCurseEntity c ? c.incidentId() : entity instanceof CursedAnchorEntity a ? a.incidentId() : "";
            if (incident.isEmpty() || taken <= 0 || entity.level().getServer() == null) return;
            if (!(source.getEntity() instanceof ServerPlayer p)) return;
            Incident in = InvestigationState.get(entity.level().getServer()).incidents.get(incident);
            if (in == null || in.state != Incident.State.ACTIVE) return;
            in.damage.merge(p.getUUID(), Math.min(taken, entity.getMaxHealth()), Float::sum);
        });
        // No building in a guardian's arena: no towers, no walls, no safe ledges.
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!(player instanceof ServerPlayer sp) || !(player.getItemInHand(hand).getItem() instanceof net.minecraft.world.item.BlockItem)) return net.minecraft.world.InteractionResult.PASS;
            if (!CursedRealms.inRealm(sp)) return net.minecraft.world.InteractionResult.PASS;
            InvestigationState.Arena a = CursedRealms.arenaAtPos(sp.level().getServer(), sp.position());
            if (a != null && GuardianArena.is(a.layout)) {
                sp.sendOverlayMessage(Component.literal("Nothing you build here would hold.").withStyle(net.minecraft.ChatFormatting.GRAY));
                return net.minecraft.world.InteractionResult.FAIL;
            }
            return net.minecraft.world.InteractionResult.PASS;
        });
        EncounterWatch.init();
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> rejoin(handler.player));
        // Logging out mid-pull: set down somewhere safe, not left hanging where the pull caught them.
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> CursedRealms.cancelPull(handler.player));
        // Nothing hurts someone being pulled in (the cliff's fall never lands).
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof ServerPlayer sp && CursedRealms.pulling(sp)));
        ServerPlayerEvents.AFTER_RESPAWN.register((old, now, alive) -> {
            // Died in a realm: they respawn at home as usual; the realm forgets them.
            if (!CursedRealms.inRealm(now)) {
                InvestigationState st = InvestigationState.get(now.level().getServer());
                if (st.returns.remove(now.getUUID()) != null) st.markDirty();
            }
        });
    }

    /** A restart ends every realm fight in progress (its arena is cleared; the incident can be retried). */
    private static void onStart(MinecraftServer server) {
        InvestigationState st = InvestigationState.get(server);
        for (InvestigationState.Arena a : List.copyOf(st.arenas.values())) CursedRealms.close(server, st, a, false);
        st.flush();
    }

    /** Logged in inside the realm with no arena to be in (it closed while they were away): home. */
    private static void rejoin(ServerPlayer p) {
        if (!CursedRealms.inRealm(p)) return;
        InvestigationState st = InvestigationState.get(p.level().getServer());
        InvestigationState.Arena a = CursedRealms.arenaAt(st, p.position());
        if (a == null) CursedRealms.sendHome(p, st);
        else a.inside.add(p.getUUID());
    }

    // --- The loop ---

    private static void tick(MinecraftServer server) {
        // No loose drops in an open arena, whatever else is switched on.
        CursedRealms.sweepDrops(server, InvestigationState.get(server));
        if (!JJKConfig.get().progression.enabled) return;
        InvestigationState st = InvestigationState.get(server);
        long now = server.overworld().getGameTime();
        CursedRealms.tick(server, st);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!(p.level() instanceof ServerLevel level) || CursedRealms.inRealm(p) || p.isSpectator()) continue;
            int phase = (int) ((now + p.getId()) % 100);
            if (phase == 0 && JJKConfig.get().mastery.newsBoards) visitVillages(level, p, st, now);
            if (phase % 5 == 0) triggers(level, p, st, now);
            if (phase == 50) LodgeRewards.visit(level, p, st);
            if (phase == 25) StoryChains.deliver(p, st);
            if (phase % 10 == 0 && CursePerception.canPerceive(p)) residue(level, p, st);
        }
        if (now % 200 == 0) expire(st, now);
        if (now % 4 == 0) takeDownSites(server, st);
        if (now % 40 == 7) deliverClaims(server, st);
        if (now % 100 == 0) st.flush();
    }

    /** Villages near a player: register them, put up their board, keep a few reports on it. */
    private static void visitVillages(ServerLevel level, ServerPlayer p, InvestigationState st, long now) {
        PoiManager poi = level.getPoiManager();
        Optional<BlockPos> bell = poi.findClosest(h -> h.is(PoiTypes.MEETING), p.blockPosition(), 64, PoiManager.Occupancy.ANY);
        if (bell.isEmpty()) return;
        String dim = level.dimension().identifier().toString();
        InvestigationState.Village v = st.villages.get(bell.get().asLong());
        if (v == null) {
            v = new InvestigationState.Village(dim, bell.get());
            st.villages.put(v.bell.asLong(), v);
            st.markDirty();
        }
        if (JJKConfig.get().progression.villageStorylines) StoryChains.decide(level, v, st);
        if (v.board == null && v.boardTries < 6 && level.isLoaded(v.bell)) {
            v.boardTries++;
            v.board = placeNews(level, v.bell);
            st.markDirty();
        }
        stock(level, v, st, now, p);
        refreshBoard(level, v, st, now);
    }

    static void stock(ServerLevel level, InvestigationState.Village v, InvestigationState st, long now) {
        stock(level, v, st, now, null);
    }

    /** Keeps the village's board stocked with reports (one new one at a time, a little apart). */
    static void stock(ServerLevel level, InvestigationState.Village v, InvestigationState st, long now, @Nullable ServerPlayer reader) {
        // (Old worlds only) the village's character storyline first: its current event is always on the board.
        if (JJKConfig.get().progression.villageStorylines) StoryChains.stock(level, v, st, now);
        int open = 0;
        for (String id : v.incidents) {
            Incident i = st.incidents.get(id);
            if (i != null && StoryChains.isChain(i)) continue;
            if (i != null && (i.state == Incident.State.OPEN || i.state == Incident.State.ACTIVE)) open++;
        }
        if (open >= JJKConfig.get().mastery.reportsPerBoard) return;
        if (open > 0 && now - v.lastGenerated < 2400) return;
        Incident made = generate(level, v, st, now, null, reader);
        if (made != null) JJK.LOGGER.info("[investigations] reported {} ({}) near {}: {}", made.id, made.template, v.bell.toShortString(), made.headline);
    }

    /** Makes one incident for a village: a template that has a place to happen around it. */
    @Nullable
    public static Incident generate(ServerLevel level, InvestigationState.Village v, InvestigationState st, long now, @Nullable String only) {
        return generate(level, v, st, now, only, null);
    }

    /**
     * Makes one incident for a village. With a reader (the player whose visit stocks the board), it is shaped by their
     * News clearance: mostly their own grade, sometimes an easier one, now and then one grade above (shown as locked, a
     * goal). Grade 1 guardian contracts appear only for readers cleared for them, one at a time per board.
     */
    @Nullable
    public static Incident generate(ServerLevel level, InvestigationState.Village v, InvestigationState st, long now, @Nullable String only,
                                    @Nullable ServerPlayer reader) {
        RandomSource r = RandomSource.create(now ^ v.bell.asLong() * 31 + v.incidents.size());
        List<IncidentTemplate> pool = new ArrayList<>();
        int target = only == null ? targetGrade(reader, v, st, r) : 0;
        for (IncidentTemplate t : IncidentTemplate.all()) {
            // A storyline's events only ever come from its own village's storyline (StoryChains), never at random.
            if (only == null ? dev.rick.jjk.progression.story.CharacterStories.ofTemplate(t.id()) != null : !t.id().equals(only)) continue;
            if (only == null && t.weight() <= 0) continue;
            if (only == null && target > 0 && Clearance.number(t.grade()) != target) continue;
            if (only == null && target == 0 && t.grade() == CurseGrade.GRADE_1) continue;
            for (int k = 0; k < Math.max(1, t.weight()); k++) pool.add(t);
        }
        JJKConfig.MasteryRules cfg = JJKConfig.get().mastery;
        v.lastGenerated = now;
        for (int attempt = 0; attempt < 8 && !pool.isEmpty(); attempt++) {
            IncidentTemplate t = pool.get(r.nextInt(pool.size()));
            Sites.Site s = Sites.find(t.site(), level, v.bell, r, cfg.incidentMinDistance, cfg.incidentMaxDistance);
            if (s == null) {
                pool.removeIf(x -> x == t);
                continue;
            }
            // Never on top of another investigation's site that is still standing (another player may be on it).
            if (crowded(st, level.dimension().identifier().toString(), s.pos())) continue;
            return create(level, v, st, t, s, now, r);
        }
        st.markDirty();
        return null;
    }

    /** The grade a new report on this board should be, for this reader (0 = any but Grade 1). */
    static int targetGrade(@Nullable ServerPlayer reader, InvestigationState.Village v, InvestigationState st, RandomSource r) {
        if (reader == null) return 0;
        int access = Clearance.access(reader);
        float roll = r.nextFloat();
        int target;
        if (access > 1 && roll < JJKConfig.get().clearance.aboveClearanceChance) target = access - 1;
        else if (roll < 0.75f || access >= 4) target = access;
        else target = access + 1 + r.nextInt(Math.max(1, 4 - access));
        target = Math.max(1, Math.min(4, target));
        // A guardian only for someone cleared for it, and one open guardian contract per board at a time.
        if (target == 1 && access > 1) target = 2;
        if (target == 1) {
            for (String id : v.incidents) {
                Incident i = st.incidents.get(id);
                if (i != null && i.grade == CurseGrade.GRADE_1 && (i.state == Incident.State.OPEN || i.state == Incident.State.ACTIVE)) return 2;
            }
        }
        return target;
    }

    /** Sites this close together could overlap (a lodge's grounds reach about thirty blocks out). */
    static final int SITE_SPACING = 64;

    /** Whether a new site at {@code pos} would overlap the site of an incident still standing there. */
    public static boolean crowded(InvestigationState st, String dimension, BlockPos pos) {
        for (Incident o : st.incidents.values()) {
            if (o.dimension.equals(dimension) && standing(o) && o.site.distSqr(pos) < (double) SITE_SPACING * SITE_SPACING) return true;
        }
        return false;
    }

    /** Whether an incident still holds its ground: reported and not over, or built and not taken down (or abandoned). */
    public static boolean standing(Incident o) {
        return o.state == Incident.State.OPEN || o.state == Incident.State.ACTIVE || (o.featureBuilt && !o.siteCleared);
    }

    /** Puts a specific incident at a specific place (generation, and tests). */
    public static Incident create(ServerLevel level, InvestigationState.Village v, InvestigationState st, IncidentTemplate t, Sites.Site s, long now, RandomSource r) {
        ReportWriter.Written w = ReportWriter.write(t, t.grade(), v.bell, s.pos(), Sites.describe(t.site(), level, v.bell, s.pos()), r);
        Incident in = new Incident(st.newId(), t.id(), t.grade(), level.dimension().identifier().toString(), v.bell, s.pos(), s.dirX(), s.dirZ(), now,
                w.headline(), w.body());
        if (t.objective() == IncidentTemplate.Objective.GUARDIAN) {
            // One body and one look, chosen now and kept for every retry.
            var body = dev.rick.jjk.progression.guardian.Guardians.pick(r);
            in.guardian = body.id();
            in.variant = r.nextInt(Math.max(1, body.variants()));
        }
        st.incidents.put(in.id, in);
        v.incidents.add(in.id);
        st.markDirty();
        return in;
    }

    /** Test/admin hook: the village record for a bell, made if needed. */
    public static InvestigationState.Village village(ServerLevel level, BlockPos bell) {
        InvestigationState st = InvestigationState.get(level.getServer());
        return st.villages.computeIfAbsent(bell.asLong(), k -> new InvestigationState.Village(level.dimension().identifier().toString(), bell));
    }

    /**
     * The village's news: its news house by the bell (in the village's style, the board inside), or where there is no room
     * for one, a board in the open by the bell.
     */
    @Nullable
    public static BlockPos placeNews(ServerLevel level, BlockPos bell) {
        BlockPos inHouse = NewsHouse.placeNear(level, bell);
        return inHouse != null ? inHouse : placeBoard(level, bell);
    }

    /** Pins up as many notices on the board as the village has news (0-4): the board's look follows its reports. */
    public static void refreshBoard(ServerLevel level, InvestigationState.Village v, InvestigationState st, long now) {
        if (v.board == null || !level.isLoaded(v.board)) return;
        BlockState s = level.getBlockState(v.board);
        if (!s.is(ProgressionBlocks.NEWS_BOARD)) return;
        int n = Math.min(4, notes(v, st, now).size());
        if (s.getValue(NewsBoardBlock.NOTICES) != n) level.setBlock(v.board, s.setValue(NewsBoardBlock.NOTICES, n), 3);
    }

    /** A board by the bell: on solid ground, in the open, facing the bell. */
    @Nullable
    public static BlockPos placeBoard(ServerLevel level, BlockPos bell) {
        for (int r = 2; r <= 4; r++) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                for (int dy = -2; dy <= 1; dy++) {
                    BlockPos at = bell.relative(d, r).above(dy);
                    BlockState here = level.getBlockState(at), above = level.getBlockState(at.above());
                    if (!here.canBeReplaced() || !above.canBeReplaced() || !level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)) continue;
                    if (!here.getFluidState().isEmpty()) continue;
                    level.setBlock(at, ProgressionBlocks.NEWS_BOARD.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, d.getOpposite()), 3);
                    JJK.LOGGER.info("[investigations] news board put up at {}", at.toShortString());
                    return at.immutable();
                }
            }
        }
        return null;
    }

    private static void expire(InvestigationState st, long now) {
        for (Incident i : st.incidents.values()) {
            // A storyline's event waits as long as it takes: it is the village's own story.
            if (i.state == Incident.State.OPEN && now - i.createdAt > STALE_TICKS && !StoryChains.isChain(i)) {
                i.state = Incident.State.EXPIRED;
                i.changedAt = now;
                st.markDirty();
            }
        }
    }

    // --- At the site ---

    private static void triggers(ServerLevel level, ServerPlayer p, InvestigationState st, long now) {
        String dim = level.dimension().identifier().toString();
        for (Incident in : List.copyOf(st.incidents.values())) {
            if (in.state != Incident.State.OPEN || !in.dimension.equals(dim)) continue;
            double d2 = p.position().distanceToSqr(Vec3.atBottomCenterOf(in.site));
            if (d2 > 96 * 96) continue;
            IncidentTemplate t = in.def();
            if (t == null) continue;
            if (!in.featureBuilt && areaLoaded(level, in.site, 30) && level.isLoaded(Sites.mineEnd(in))) {
                in.featureBuilt = true;
                buildSite(level, in, t);
                layTrail(level, in, t.trail());
                st.markDirty();
            }
            if (in.featureBuilt && !in.marks.isEmpty()) lodgeClues(level, p, in, now);
        }
        // Every open (or under-way) incident whose site is built has its breach: the one way in.
        for (Incident in : List.copyOf(st.incidents.values())) {
            if ((in.state != Incident.State.OPEN && in.state != Incident.State.ACTIVE && !capturePending(in)) || !in.featureBuilt || !in.dimension.equals(dim)) continue;
            if (p.position().distanceToSqr(Vec3.atBottomCenterOf(in.site)) > 64 * 64) continue;
            CursedBreaches.ensure(level, in.id, CursedBreaches.anchor(in));
        }
    }

    /** The ground round a site is all loaded (its traces reach this far out). */
    static boolean areaLoaded(ServerLevel level, BlockPos c, int r) {
        for (int dx = -r; dx <= r; dx += r) for (int dz = -r; dz <= r; dz += r) if (!level.isLoaded(c.offset(dx, 0, dz))) return false;
        return true;
    }

    /**
     * Sets an incident off for a player (they used its {@link CursedBreachEntity breach}): the place takes them. Every cursed-event fight happens in a cursed realm (an
     * incident whose template names no realm, or one this build doesn't have, uses the plain hollow one): the player is
     * held where they stand for a moment while the world goes dark ({@link CursedRealms#pull}), then arrives.
     */
    public static void begin(ServerLevel level, ServerPlayer p, Incident in, IncidentTemplate t, InvestigationState st, long now) {
        if (CursedRealms.pulling(p) || CursedRealms.inRealm(p)) return;
        CursedRealms.pull(p, CursedRealms.safeReturn(level, p, in), () -> {
            InvestigationState s2 = InvestigationState.get(level.getServer());
            Incident live = s2.incidents.get(in.id);
            if (live != null && (live.state == Incident.State.OPEN || live.state == Incident.State.ACTIVE || capturePending(live))) CursedRealms.enter(p, live, s2);
        });
    }

    /**
     * Spawns an incident's curses around {@code origin}, bound to it. Three or more come in waves: the kinds are mixed,
     * about three in five manifest at once, and the rest wait unseen ({@link CommonCurseEntity#setDormant}) to come in
     * as reinforcements once the first wave is nearly gone ({@link #curseFell}).
     */
    static void spawnCurses(ServerLevel level, Incident in, BlockPos origin, List<Vec3> spots, int radius) {
        IncidentTemplate t = in.def();
        if (t == null) return;
        RandomSource r = RandomSource.create(in.id.hashCode() * 7L + level.getGameTime());
        in.curses.clear();
        List<CommonCurseEntity> all = new java.util.ArrayList<>();
        if (t.objective() == IncidentTemplate.Objective.GUARDIAN) {
            spawnGuardian(level, in, origin, spots, radius);
            return;
        }
        for (IncidentTemplate.CurseSpawn cs : t.curses()) {
            EntityType<? extends CommonCurseEntity> type = CurseKinds.type(cs.kind());
            if (type == null) continue;
            int count = cs.min() + (cs.max() > cs.min() ? r.nextInt(cs.max() - cs.min() + 1) : 0);
            for (int i = 0; i < count; i++) {
                CommonCurseEntity c = type.create(level, EntitySpawnReason.EVENT);
                if (c == null) continue;
                c.setGrade(cs.grade() != null ? cs.grade() : in.grade);
                all.add(c);
            }
        }
        // Mixed: a wave is never all one kind when the incident has more than one.
        for (int i = all.size() - 1; i > 0; i--) java.util.Collections.swap(all, i, r.nextInt(i + 1));
        int firstWave = firstWave(all.size());
        in.totalHealth = 0;
        for (CommonCurseEntity c : all) in.totalHealth += c.getMaxHealth();
        for (int n = 0; n < all.size(); n++) {
            CommonCurseEntity c = all.get(n);
            Vec3 at = Vec3.atBottomCenterOf(origin).add(spots.get(n % spots.size())).add(r.nextDouble() - 0.5, 0, r.nextDouble() - 0.5);
            c.setPos(at.x, at.y, at.z);
            c.setYRot(r.nextFloat() * 360f);
            c.bind(in.id, origin, radius);
            if (n >= firstWave) c.setDormant(true);
            // Only a curse that is really there counts toward the fight (a refused spawn would leave it unwinnable).
            if (level.addFreshEntity(c)) in.curses.add(c.getUUID());
            else JJK.LOGGER.warn("[encounters] incident {}: a {} could not be spawned; it is not counted", in.id, c.curseKind());
        }
        if (in.curses.isEmpty() && !all.isEmpty()) JJK.LOGGER.warn("[encounters] incident {}: none of its curses could be spawned", in.id);
        in.objectiveTarget = null;
        in.objectiveBy = null;
        if (t.objective() == IncidentTemplate.Objective.DESIGNATED && !all.isEmpty()) {
            // The designated curse: of the contract's first kind, in the first wave, marked so it can be told apart.
            String kind = t.curses().get(0).kind();
            CommonCurseEntity target = null;
            for (int n = 0; n < Math.min(firstWave, all.size()); n++) {
                if (all.get(n).curseKind().equals(kind)) {
                    target = all.get(n);
                    break;
                }
            }
            if (target == null) target = all.get(0);
            target.setDormant(false);
            target.setGlowingTag(true);
            in.objectiveTarget = target.getUUID();
        } else if (t.objective() == IncidentTemplate.Objective.ANCHOR) {
            CursedAnchorEntity a = dev.rick.jjk.registry.ModEntities.CURSED_ANCHOR.create(level, EntitySpawnReason.EVENT);
            if (a != null) {
                Vec3 at = Vec3.atBottomCenterOf(origin).add(spots.isEmpty() ? new Vec3(0, 0, 6) : spots.get(0));
                double hp = switch (in.grade) {
                    case GRADE_4 -> 40;
                    case GRADE_3 -> 70;
                    default -> 110;
                };
                a.setup(in.id, at, hp);
                level.addFreshEntity(a);
                in.objectiveTarget = a.getUUID();
                in.totalHealth += (float) hp;
            }
        }
        dev.rick.jjk.core.fx.Fx.play(level, "fb_manifest", Vec3.atBottomCenterOf(origin).add(0, 1, 0), Vec3.ZERO, 1f);
    }

    /** A Grade 1 contract's guardian: its chosen body (or its stand-in), its chosen look, alone in the arena. */
    static void spawnGuardian(ServerLevel level, Incident in, BlockPos origin, List<Vec3> spots, int radius) {
        if (in.guardian.isEmpty()) {
            var body = dev.rick.jjk.progression.guardian.Guardians.pick(level.getRandom());
            in.guardian = body.id();
            in.variant = level.getRandom().nextInt(Math.max(1, body.variants()));
        }
        in.curses.clear();
        in.objectiveTarget = null;
        in.objectiveBy = null;
        CommonCurseEntity g = dev.rick.jjk.progression.guardian.Guardians.type(in.guardian).create(level, EntitySpawnReason.EVENT);
        if (g == null) return;
        g.setGrade(CurseGrade.GRADE_1);
        if (g instanceof dev.rick.jjk.progression.guardian.GuardianBody b) b.setGuardianVariant(in.variant);
        Vec3 at = Vec3.atBottomCenterOf(origin).add(spots.isEmpty() ? new Vec3(0, 0, 7) : spots.get(0));
        g.setPos(at.x, at.y, at.z);
        g.setYRot(180f);
        g.bind(in.id, origin, radius);
        level.addFreshEntity(g);
        in.curses.add(g.getUUID());
        in.objectiveTarget = g.getUUID();
        in.totalHealth = g.getMaxHealth();
        dev.rick.jjk.core.fx.Fx.play(level, "fb_manifest", at.add(0, 1, 0), Vec3.ZERO, 1.6f);
    }

    // --- A Grade 1 guardian's essence (see progression.guardian.EssenceCapture) ---

    /** Whether a contract's guardian has fallen and its remnant still waits for a seal. */
    public static boolean capturePending(Incident in) {
        return in.capture == Incident.CAPTURE_PENDING;
    }

    @Nullable
    public static Incident incident(MinecraftServer server, String id) {
        return InvestigationState.get(server).incidents.get(id);
    }

    /** The guardian is down: what's left of it stays where it fell, waiting for its challenger's seal. */
    static void guardianFell(MinecraftServer server, Incident in, InvestigationState st, @Nullable Vec3 at) {
        if (in.capture != Incident.CAPTURE_NONE) return;
        in.capture = Incident.CAPTURE_PENDING;
        InvestigationState.Arena a = CursedRealms.arenaOf(st, in.id);
        if (a != null && at != null) {
            Vec3 o = Vec3.atBottomCenterOf(a.origin);
            // Kept inside the arena (a guardian knocked into a wall leaves its remnant a step in).
            double r = Math.max(2, CursedRealms.arenaRadius(a) - 2);
            Vec3 d = at.subtract(o);
            double flat = Math.sqrt(d.x * d.x + d.z * d.z);
            double k = flat > r ? r / flat : 1;
            in.remnantX = d.x * k;
            in.remnantY = Math.max(0, Math.min(3, d.y));
            in.remnantZ = d.z * k;
        } else {
            in.remnantX = 0;
            in.remnantY = 0;
            in.remnantZ = 6;
        }
        st.markDirty();
        st.save();
        ServerPlayer c = in.challenger == null ? null : server.getPlayerList().getPlayer(in.challenger);
        if (c != null) {
            boolean seal = c.getInventory().contains(x -> x.is(dev.rick.jjk.progression.ProgressionItems.EXORCISM_SEAL));
            c.sendSystemMessage(Component.literal(seal
                    ? "It is down. What is left of it still writhes where it fell: use an Exorcism Seal on it to contain its essence."
                    : "It is down. What is left of it will wait where it fell. You have no Exorcism Seal: leave by the breach, make one, and come back the same way.")
                    .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
        }
        JJK.LOGGER.info("[guardians] contract {}: guardian down, its remnant waits for a seal", in.id);
    }

    /** Where a contract's remnant lies in its arena. */
    public static Vec3 remnantAt(InvestigationState.Arena a, Incident in) {
        return Vec3.atBottomCenterOf(a.origin).add(in.remnantX, in.remnantY, in.remnantZ);
    }

    /** Why this player can't contain this contract's essence now, or null. */
    @Nullable
    public static String captureRefusal(@Nullable Incident in, ServerPlayer p) {
        if (in == null || in.capture == Incident.CAPTURE_DONE) return "Its essence is already sealed.";
        if (in.capture != Incident.CAPTURE_PENDING) return "There is nothing here to contain yet.";
        if (in.challenger != null && !in.challenger.equals(p.getUUID())) return "This remnant answers only to the one who defeated it.";
        return null;
    }

    /** How a contract's guardian is named on its essence. */
    public static String guardianName(@Nullable Incident in) {
        var body = in == null ? null : dev.rick.jjk.progression.guardian.Guardians.body(in.guardian);
        return body == null ? "a Grade 1 curse" : body.name();
    }

    /**
     * The essence is sealed: once, ever. It goes into its challenger's inventory, or, if that is full, waits for them as an
     * owner-bound claim. Saved at once.
     */
    public static void markCaptured(MinecraftServer server, Incident in, net.minecraft.world.item.ItemStack essence, ServerPlayer p) {
        if (in.capture != Incident.CAPTURE_PENDING) return;
        in.capture = Incident.CAPTURE_DONE;
        if (!p.getInventory().add(essence) || !essence.isEmpty()) {
            in.claimOwed = true;
            p.sendSystemMessage(Component.literal("Your inventory is full. The Sealed Grade 1 Essence is held for you: make room and it will come to you.")
                    .withStyle(net.minecraft.ChatFormatting.GOLD));
        } else {
            p.sendSystemMessage(Component.literal("Sealed. A Grade 1 essence is yours: bind it into a character's ordinary vessel at a cauldron.")
                    .withStyle(net.minecraft.ChatFormatting.GOLD));
        }
        InvestigationState st = InvestigationState.get(server);
        st.markDirty();
        st.save();
    }

    /** Owed essences reach their challengers as soon as they have room (checked now and then; nobody else can take them). */
    static void deliverClaims(MinecraftServer server, InvestigationState st) {
        for (Incident in : st.incidents.values()) {
            if (!in.claimOwed || in.challenger == null) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(in.challenger);
            if (p == null || !p.isAlive()) continue;
            net.minecraft.world.item.ItemStack e = dev.rick.jjk.progression.story.Vessels.sealed(in.id, guardianName(in));
            if (p.getInventory().getFreeSlot() < 0) continue;
            if (p.getInventory().add(e) && e.isEmpty()) {
                in.claimOwed = false;
                st.markDirty();
                st.save();
                p.sendSystemMessage(Component.literal("The Sealed Grade 1 Essence held for you is in your inventory.").withStyle(net.minecraft.ChatFormatting.GOLD));
            }
        }
    }

    /** The objective is met: what's left of the contract's curses scatters, and it completes. */
    static void objectiveMet(MinecraftServer server, Incident in, InvestigationState st, @Nullable ServerPlayer by) {
        if (by != null) {
            in.objectiveBy = by.getUUID();
            in.participants.add(by.getUUID());
        }
        for (UUID id : List.copyOf(in.curses)) {
            Entity e = curseEntity(server, id);
            if (e != null && e.isAlive()) {
                if (e.level() instanceof ServerLevel l) dev.rick.jjk.core.fx.Fx.play(l, "curse_scatter", e.position().add(0, 1, 0), Vec3.ZERO, 1f);
                e.discard();
            }
        }
        in.curses.clear();
        if (in.state == Incident.State.ACTIVE) complete(server, in, st);
    }

    /** How many of {@code total} curses manifest at the start (the rest are reinforcements). */
    public static int firstWave(int total) {
        return total < 3 ? total : Math.max(2, Math.round(total * 0.6f));
    }

    /** One of an incident's curses has been exorcised; the last one completes it. */
    static void curseFell(MinecraftServer server, CommonCurseEntity c) {
        InvestigationState st = InvestigationState.get(server);
        Incident in = st.incidents.get(c.incidentId());
        if (in == null) return;
        in.curses.remove(c.getUUID());
        ServerPlayer killer = c.getKillCredit() instanceof ServerPlayer p ? p : null;
        if (killer != null) in.participants.add(killer.getUUID());
        st.markDirty();
        IncidentTemplate t = in.def();
        IncidentTemplate.Objective obj = t == null ? IncidentTemplate.Objective.INFESTATION : t.objective();
        if (in.state != Incident.State.ACTIVE) return;
        if ((obj == IncidentTemplate.Objective.DESIGNATED || obj == IncidentTemplate.Objective.GUARDIAN) && c.getUUID().equals(in.objectiveTarget)) {
            if (obj == IncidentTemplate.Objective.GUARDIAN) guardianFell(server, in, st, c.position());
            objectiveMet(server, in, st, killer);
            return;
        }
        if (obj == IncidentTemplate.Objective.ANCHOR) {
            // The guards falling isn't the objective; the anchor is.
            if (c.level() instanceof ServerLevel level) reinforce(level, in);
            return;
        }
        if (in.curses.isEmpty()) {
            if (killer != null) in.objectiveBy = killer.getUUID();
            complete(server, in, st);
        }
        else if (c.level() instanceof ServerLevel level) reinforce(level, in);
    }

    /**
     * A contract's curse records changed without a kill (the encounter watch dropped a stale or departed record):
     * whether it is now done depends on its objective. A designated curse or guardian that is gone counts as its objective
     * met; an anchor contract is done only when its anchor is; an infestation when no record is left.
     */
    static void recordsChanged(MinecraftServer server, Incident in, InvestigationState st, @Nullable UUID lost) {
        if (in.state != Incident.State.ACTIVE) return;
        IncidentTemplate t = in.def();
        IncidentTemplate.Objective obj = t == null ? IncidentTemplate.Objective.INFESTATION : t.objective();
        switch (obj) {
            case DESIGNATED, GUARDIAN -> {
                if (in.objectiveTarget != null && (in.objectiveTarget.equals(lost) || !in.curses.contains(in.objectiveTarget))) {
                    if (obj == IncidentTemplate.Objective.GUARDIAN) guardianFell(server, in, st, null);
                    objectiveMet(server, in, st, null);
                    return;
                }
            }
            case ANCHOR -> {
                Entity anchor = in.objectiveTarget == null ? null : curseEntity(server, in.objectiveTarget);
                if (in.objectiveTarget == null || in.objectiveTarget.equals(lost)) {
                    objectiveMet(server, in, st, null);
                    return;
                }
                if (anchor == null || !anchor.isAlive()) return;
            }
            default -> {
                if (in.curses.isEmpty()) {
                    complete(server, in, st);
                    return;
                }
            }
        }
        reinforce(CursedRealms.level(server), in);
    }

    /** With at most one of the first wave left standing, the waiting curses come in. */
    static void reinforce(ServerLevel level, Incident in) {
        List<CommonCurseEntity> waiting = new java.util.ArrayList<>();
        int awake = 0;
        for (UUID id : in.curses) {
            if (!(level.getEntity(id) instanceof CommonCurseEntity e) || !e.isAlive()) continue;
            if (e.dormant()) waiting.add(e);
            else awake++;
        }
        if (waiting.isEmpty() || awake > (waiting.size() >= 2 ? 1 : 0)) return;
        for (CommonCurseEntity e : waiting) e.manifest(level);
        dev.rick.jjk.core.fx.Fx.sound(level, waiting.get(0).position(), net.minecraft.sounds.SoundEvents.WARDEN_ROAR, 0.6f, 1.5f);
    }

    /** Everyone who took part gets the investigation bonus; the village hears it's over. */
    static void complete(MinecraftServer server, Incident in, InvestigationState st) {
        // Once: a duplicate (two curses' last records dropped together, a death and a stale check at once) pays nothing.
        if (in.state == Incident.State.COMPLETE) return;
        in.state = Incident.State.COMPLETE;
        // Anyone who left by Emergency Return abandoned the fight: no completion rewards for them.
        InvestigationState.Arena arena = CursedRealms.arenaOf(st, in.id);
        if (arena != null) in.participants.removeAll(arena.forfeited);
        in.changedAt = server.overworld().getGameTime();
        st.markDirty();
        CurseGrade g = in.grade;
        int bonus = (int) Math.round(g.incidentBonus * JJKConfig.get().mastery.rewardMultiplier);
        Map<UUID, Map<String, Integer>> earned = EARNED.remove(in.id);
        for (UUID id : in.participants) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) continue;
            var d = Mastery.data(p).recordIncident(g.name());
            Mastery.set(p, d);
            String tree = topTree(p, earned == null ? null : earned.get(id));
            if (tree != null && Mastery.enabled()) {
                Mastery.award(p, tree, bonus);
                MasteryTree t = MasteryTrees.get(tree);
                p.sendSystemMessage(Component.literal("It's over. Whatever was there won't trouble anyone again. (+" + bonus + " "
                        + (t == null ? tree : t.title()) + " Mastery for the investigation)").withStyle(net.minecraft.ChatFormatting.GRAY));
            } else {
                p.sendSystemMessage(Component.literal("It's over. Whatever was there won't trouble anyone again.").withStyle(net.minecraft.ChatFormatting.GRAY));
            }
        }
        creditClearance(server, in, st);
        LodgeRewards.completed(server, in, st);
        StoryChains.completed(server, in, st);
        JJK.LOGGER.info("[investigations] incident {} complete ({} participants)", in.id, in.participants.size());
    }

    /**
     * News clearance for a completed contract: once to each participant who took a real part (a meaningful share of the
     * damage, or the objective itself) and was cleared for its grade. Offline participants are credited too.
     */
    static void creditClearance(MinecraftServer server, Incident in, InvestigationState st) {
        JJKConfig.Clearance cfg = JJKConfig.get().clearance;
        java.util.Set<UUID> who = new java.util.LinkedHashSet<>(in.participants);
        who.addAll(in.damage.keySet());
        float total = Math.max(1f, in.totalHealth);
        for (UUID id : who) {
            float d = in.damage.getOrDefault(id, 0f);
            boolean meaningful = d >= cfg.minDamage || d >= total * cfg.minDamageShare || id.equals(in.objectiveBy);
            if (!meaningful) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) Clearance.award(p, in);
            else Clearance.awardOffline(st, id, in);
        }
    }

    /** The tree a participant earned most in during this incident (one they may develop). */
    @Nullable
    private static String topTree(ServerPlayer p, @Nullable Map<String, Integer> earned) {
        if (earned == null) return null;
        String best = null;
        int most = 0;
        for (Map.Entry<String, Integer> e : earned.entrySet()) {
            MasteryTree t = MasteryTrees.get(e.getKey());
            if (t == null || !Mastery.mayDevelop(p, t)) continue;
            if (e.getValue() > most) {
                most = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }

    /** {@link dev.rick.jjk.progression.mastery.CurseRewards} reports what an incident's curse paid to whom. */
    public static void credit(UUID player, String incident, String tree, int amount) {
        EARNED.computeIfAbsent(incident, k -> new HashMap<>()).computeIfAbsent(player, k -> new LinkedHashMap<>()).merge(tree, amount, Integer::sum);
    }

    // --- Clues ---

    /** Clue bits on {@link Incident#clues} (per player, persisted): what each has found at a lodge. */
    public static final int CLUE_STAND = 1, CLUE_MARKS = 2, CLUE_TRACKS = 4, CLUE_GUNSHOT = 8, CLUE_SCOPE = 16, CLUE_ANOMALY = 32;

    /** Records a clue for a player (once); its line, if any, goes to the overlay the first time. True if it was new. */
    public static boolean clue(ServerPlayer p, Incident in, int bit, @Nullable String line) {
        int had = in.clues.getOrDefault(p.getUUID(), 0);
        if ((had & bit) != 0) return false;
        in.clues.put(p.getUUID(), had | bit);
        InvestigationState.get(p.level().getServer()).markDirty();
        if (line != null) p.sendOverlayMessage(Component.literal(line).withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.ITALIC));
        return true;
    }

    /** A lodge's traces, noticed by walking up to them, and its gunshots with nobody there. */
    private static void lodgeClues(ServerLevel level, ServerPlayer p, Incident in, long now) {
        if (in.marks.isEmpty()) return;
        BlockPos me = p.blockPosition();
        for (String k : new String[] {"stand_a", "stand_b"}) {
            BlockPos b = in.mark(k);
            if (b != null && me.distSqr(b) <= 5 * 5) {
                clue(p, in, CLUE_STAND, "A hunting stand, its rungs worn smooth. Someone sat up here a long time, watching the same stretch of woods.");
            }
        }
        BlockPos m = in.mark("marks");
        if (m != null && me.distSqr(m) <= 4 * 4) clue(p, in, CLUE_MARKS, "The bark is scored and blackened, chest high. Shot at, more than once, from the lodge.");
        BlockPos t = in.mark("tracks");
        if (t != null && me.distSqr(t) <= 3 * 3) clue(p, in, CLUE_TRACKS, "The tracks just stop. No turn, no scuffle. Whoever made them didn't walk any further.");
        // A distant shot now and then, from the trees beyond the window; far likelier after dark.
        BlockPos a = in.mark("anomaly");
        if (a == null) return;
        double d2 = p.position().distanceToSqr(Vec3.atCenterOf(a));
        if (d2 > 90 * 90) return;
        boolean night = level.isDarkOutside();
        RandomSource r = level.getRandom();
        if (r.nextInt(night ? 60 : 400) != 0) return;
        Vec3 from = Vec3.atCenterOf(a).add(r.nextGaussian() * 6, 2, r.nextGaussian() * 6);
        Vec3 toward = from.subtract(p.getEyePosition());
        // Played at the player (pointing the right way) so it carries as far as it should; it's only theirs to hear.
        Vec3 at = p.getEyePosition().add(toward.normalize().scale(Math.min(14, toward.length())));
        hear(p, at, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(), 0.35f, 1.9f);
        clue(p, in, CLUE_GUNSHOT, "A gunshot, out past the lodge. Then nothing at all.");
    }

    /** A sound only this player hears. */
    public static void hear(ServerPlayer p, Vec3 at, net.minecraft.sounds.SoundEvent s, float volume, float pitch) {
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(net.minecraft.core.Holder.direct(s),
                net.minecraft.sounds.SoundSource.AMBIENT, at.x, at.y, at.z, volume, pitch, p.getRandom().nextLong()));
    }

    private static void layTrail(ServerLevel level, Incident in, int length) {
        in.trail.clear();
        Vec3 site = Vec3.atBottomCenterOf(in.site), toVillage = Vec3.atBottomCenterOf(in.village).subtract(site);
        if (toVillage.lengthSqr() < 1) return;
        Vec3 step = new Vec3(toVillage.x, 0, toVillage.z).normalize().scale(3);
        RandomSource r = RandomSource.create(in.id.hashCode());
        // Starts a little back from the edge (it walked to it), wanders slightly.
        for (int i = 1; i <= length; i++) {
            Vec3 at = site.add(step.scale(i)).add(r.nextGaussian() * 0.8, 0, r.nextGaussian() * 0.8);
            BlockPos b = Sites.surface(level, BlockPos.containing(at));
            in.trail.add(b);
        }
    }

    /** Faint residue at the site and along its trail, sent only to this (perceiving) player. */
    private static void residue(ServerLevel level, ServerPlayer p, InvestigationState st) {
        String dim = level.dimension().identifier().toString();
        int sent = 0;
        for (Incident in : st.incidents.values()) {
            if (in.state != Incident.State.OPEN || !in.dimension.equals(dim) || !in.featureBuilt) continue;
            if (p.position().distanceToSqr(Vec3.atBottomCenterOf(in.site)) > 80 * 80) continue;
            List<BlockPos> pts = new ArrayList<>(in.trail);
            pts.add(in.site);
            // A lodge's clues carry residue too (never the anomaly: only the scope shows that).
            for (String k : new String[] {"stand_a", "stand_b", "marks", "tracks"}) if (in.mark(k) != null) pts.add(in.mark(k));
            for (BlockPos b : pts) {
                if (sent >= 10) return;
                if (p.blockPosition().distSqr(b) > 28 * 28) continue;
                ServerPlayNetworking.send(p, new FxPayload("curse_residue", Vec3.atBottomCenterOf(b).add(0, 0.15, 0), Vec3.ZERO, 1f, -1));
                sent++;
            }
        }
    }

    // --- The board ---

    /** A player reads a news board: the village's current notices. */
    public static void readBoard(ServerPlayer p, ServerLevel level, BlockPos board) {
        InvestigationState st = InvestigationState.get(level.getServer());
        InvestigationState.Village v = null;
        double best = Double.MAX_VALUE;
        for (InvestigationState.Village x : st.villages.values()) {
            if (!x.dimension.equals(level.dimension().identifier().toString())) continue;
            double d = x.bell.distSqr(board);
            if (board.equals(x.board)) {
                v = x;
                break;
            }
            if (d < best && d <= 64 * 64) {
                best = d;
                v = x;
            }
        }
        if (v == null) {
            // A board put up by hand away from any known village: it adopts the nearest bell, or itself.
            Optional<BlockPos> bell = level.getPoiManager().findClosest(h -> h.is(PoiTypes.MEETING), board, 64, PoiManager.Occupancy.ANY);
            v = village(level, bell.orElse(board));
            if (v.board == null) v.board = board.immutable();
        }
        long now = level.getServer().overworld().getGameTime();
        if (JJKConfig.get().progression.villageStorylines) StoryChains.decide(level, v, st);
        stock(level, v, st, now, p);
        refreshBoard(level, v, st, now);
        st.flush();
        ServerPlayNetworking.send(p, new NewsBoardPayload(villageName(v), notes(v, st, now, p.getAttached(dev.rick.jjk.registry.ModAttachments.INVESTIGATING), p)));
    }

    public static List<NewsBoardPayload.Note> notes(InvestigationState.Village v, InvestigationState st, long now) {
        return notes(v, st, now, null);
    }

    /** The board's notices, marking the one {@code tracked} (the reader's investigation). */
    public static List<NewsBoardPayload.Note> notes(InvestigationState.Village v, InvestigationState st, long now, @Nullable String tracked) {
        return notes(v, st, now, tracked, null);
    }

    /**
     * The contract sheet under a notice, for its reader: threat grade, objective, the clearance it needs and theirs (with
     * progress to the next grade), and for a Grade 1 guardian a reminder that it is solo and to bring an Exorcism Seal.
     */
    public static String brief(Incident i, @Nullable ServerPlayer reader) {
        IncidentTemplate t = i.def();
        StringBuilder b = new StringBuilder();
        b.append("Threat: ").append(i.grade.display).append("  ·  Objective: ").append(t == null ? "Investigate" : t.objective().text);
        b.append("\nRequires: ").append(Clearance.requirement(i));
        if (reader != null) {
            boolean ok = Clearance.allows(reader, i);
            b.append(ok ? "  ·  Cleared" : "  ·  LOCKED").append(" (you: ").append(Clearance.name(Clearance.access(reader))).append(")");
            b.append("\n").append(Clearance.progress(reader));
        }
        if (i.grade == CurseGrade.GRADE_1) {
            var body = dev.rick.jjk.progression.guardian.Guardians.body(i.guardian);
            b.append("\nSolo challenge").append(body == null ? "" : ": " + body.name()).append(". Bring an Exorcism Seal: its essence can only be captured with one.");
        }
        return b.toString();
    }

    public static List<NewsBoardPayload.Note> notes(InvestigationState.Village v, InvestigationState st, long now, @Nullable String tracked,
                                                    @Nullable ServerPlayer reader) {
        List<NewsBoardPayload.Note> out = new ArrayList<>();
        List<String> ids = new ArrayList<>(v.incidents);
        java.util.Collections.reverse(ids);
        // The village's storyline is what everyone is talking about: its report is pinned first.
        ids.sort(java.util.Comparator.comparingInt(id -> st.incidents.get(id) != null && StoryChains.isChain(st.incidents.get(id)) ? 0 : 1));
        for (String id : ids) {
            Incident i = st.incidents.get(id);
            if (i == null || out.size() >= 6) continue;
            int days = (int) Math.max(0, (now - i.createdAt) / 24000);
            switch (i.state) {
                case OPEN, ACTIVE -> out.add(new NewsBoardPayload.Note(i.id, i.headline, i.body, i.state == Incident.State.OPEN ? 0 : 1, days,
                        place(v, i), i.id.equals(tracked), brief(i, reader)));
                case COMPLETE -> {
                    if (now - i.changedAt <= 48000) {
                        out.add(new NewsBoardPayload.Note("Update: " + i.headline, "Since the last notice there has been nothing more of it. "
                                + "Whoever looked into it, the village is grateful. People are walking that way again.", 2, (int) ((now - i.changedAt) / 24000)));
                    }
                }
                default -> {}
            }
        }
        return out;
    }

    /**
     * Roughly where a report places it: a direction and a distance rounded to the nearest fifty blocks from the village
     * ("about 150 blocks northeast of Ashford"). Never closer than that: finding the exact place is the compass's job.
     */
    public static String place(InvestigationState.Village v, Incident i) {
        int dx = i.site.getX() - v.bell.getX(), dz = i.site.getZ() - v.bell.getZ();
        double d = Math.sqrt((double) dx * dx + (double) dz * dz);
        if (d < 40) return "In " + villageName(v) + " itself";
        long rounded = Math.max(50, Math.round(d / 50.0) * 50);
        return "About " + rounded + " blocks " + ReportWriter.direction(v.bell, i.site) + " of " + villageName(v);
    }

    private static String villageName(InvestigationState.Village v) {
        String[] a = {"Ash", "Bell", "Cold", "Elder", "Fern", "Hollow", "Mill", "Oak", "Stone", "Thorn", "Wil", "Marsh"};
        String[] b = {"ford", "brook", "field", "wick", "stead", "ham", "combe", "ridge", "mere", "dale"};
        long h = v.bell.asLong() * 0x9E3779B97F4A7C15L;
        return a[(int) Math.floorMod(h, a.length)] + b[(int) Math.floorMod(h >>> 17, b.length)];
    }

    // --- Admin ---

    /** Sets an incident off for a player right now, wherever they are (admin/testing). */
    public static boolean force(ServerPlayer p, String incidentId) {
        InvestigationState st = InvestigationState.get(p.level().getServer());
        Incident in = st.incidents.get(incidentId);
        if (in == null || in.state != Incident.State.OPEN) return false;
        IncidentTemplate t = in.def();
        if (t == null) return false;
        ServerLevel level = p.level().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(in.dimension)));
        if (level == null) return false;
        begin(level, p, in, t, st, level.getGameTime());
        return true;
    }

    /** Test/admin hook: completes an incident now, exactly as its last curse falling would. */
    /** Test hook: a contract's guardian has fallen (its remnant waits at its default spot). */
    public static void guardianFellForTest(MinecraftServer server, Incident in) {
        guardianFell(server, in, InvestigationState.get(server), null);
    }

    /** Test hook: deliver owed essences now. */
    public static void deliverClaimsForTest(MinecraftServer server) {
        deliverClaims(server, InvestigationState.get(server));
    }

    public static void completeForTest(MinecraftServer server, Incident in) {
        InvestigationState st = InvestigationState.get(server);
        in.state = Incident.State.ACTIVE;
        in.curses.clear();
        complete(server, in, st);
    }

    /** Test hook: an incident as it comes back from disk (a restart), swapped in for the live one. */
    public static Incident reloadForTest(MinecraftServer server, Incident in) {
        InvestigationState st = InvestigationState.get(server);
        Incident back = Incident.load(in.save());
        st.incidents.put(back.id, back);
        return back;
    }

    /** Test hook: someone took part in an incident (entered its realm, fought at it). */
    /** Test hook: records damage a player dealt this contract's curses. */
    public static void damageForTest(Incident in, ServerPlayer p, float amount) {
        in.participants.add(p.getUUID());
        in.damage.merge(p.getUUID(), amount, Float::sum);
    }

    /** Test hook: makes a player a solo contract's challenger (as entering its breach does). */
    public static void challengeForTest(Incident in, ServerPlayer p) {
        in.challenger = p.getUUID();
    }

    /** Test hook: the grade a board would pick for a reader with this seed. */
    public static int targetGradeForTest(ServerPlayer reader, InvestigationState.Village v, long seed) {
        return targetGrade(reader, v, InvestigationState.get(reader.level().getServer()), RandomSource.create(seed));
    }

    /** Test hook: the designated curse, anchor or guardian of a running contract. */
    @Nullable
    public static UUID objectiveTargetForTest(Incident in) {
        return in.objectiveTarget;
    }

    public static void participateForTest(Incident in, ServerPlayer p) {
        in.participants.add(p.getUUID());
    }

    /** Test hook: builds an incident's site now (as the first visit does once its ground is loaded). */
    public static void buildForTest(ServerLevel level, Incident in) {
        IncidentTemplate t = in.def();
        if (t == null || in.featureBuilt) return;
        in.featureBuilt = true;
        buildSite(level, in, t);
        InvestigationState.get(level.getServer()).markDirty();
    }

    /** Builds an incident's site, recording what it changed so it can be taken down once the incident is over. */
    static void buildSite(ServerLevel level, Incident in, IncidentTemplate t) {
        in.footprint = new ArrayList<>(SiteFootprint.record(level, () -> Sites.build(t.site(), level, in)));
        in.footprintRaw = null;
    }

    /**
     * Completed incidents' sites come down, a batch at a time, wherever their ground is loaded (a restart or an unloaded
     * chunk only pauses it). A lodge waits until nobody is still owed its rifle (the rack holds it).
     */
    private static void takeDownSites(MinecraftServer server, InvestigationState st) {
        for (Incident in : st.incidents.values()) {
            if (in.state != Incident.State.COMPLETE || in.siteCleared || !in.featureBuilt) continue;
            if (LodgeRewards.givesRifle(in) && !in.rewardsPending.isEmpty()) continue;
            // A guardian's remnant still waiting for its seal: its breach (and the way back to it) stays.
            if (capturePending(in)) continue;
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(in.dimension)));
            if (level == null) continue;
            if (in.footprint == null) {
                if (in.footprintRaw == null) {
                    // Built before sites recorded their footprint: nothing known to take down.
                    in.siteCleared = true;
                    st.markDirty();
                    continue;
                }
                in.footprint = SiteFootprint.load(level, in.footprintRaw);
                in.footprintRaw = null;
            }
            if (!level.isLoaded(in.site)) continue;
            int before = in.footprint.size();
            // Nobody is shut in the ground as it comes back: whoever is down in it (or whose way back from its realm
            // leads into it) goes back to the village that reported it.
            for (ServerPlayer p : SiteFootprint.trapped(level, in.footprint, false)) backToVillage(p, level, in);
            repointReturns(st, level, in);
            boolean done = SiteFootprint.demolish(level, in.footprint);
            for (ServerPlayer p : SiteFootprint.trapped(level, in.footprint, true)) backToVillage(p, level, in);
            if (done) {
                in.siteCleared = true;
                in.footprint = null;
                JJK.LOGGER.info("[investigations] the site of incident {} ({}) has been taken down", in.id, in.template);
            }
            if (done || in.footprint == null || in.footprint.size() != before) st.markDirty();
        }
    }

    /** Where someone sent back to an incident's village stands: on open ground near its bell. */
    static Vec3 villageSpot(ServerLevel level, Incident in) {
        level.getChunk(in.village);
        Vec3 at = dev.rick.jjk.core.world.SafeGround.standNear(level, in.village, 2, 12);
        return at != null ? at : Vec3.atBottomCenterOf(Sites.surface(level, in.village));
    }

    /** Sends a player who would be shut in an incident's site, as it comes down, back to the village that reported it. */
    static void backToVillage(ServerPlayer p, ServerLevel level, Incident in) {
        Vec3 at = villageSpot(level, in);
        dev.rick.jjk.core.world.SafeGround.put(p, at);
        p.sendOverlayMessage(Component.literal("The ground closes over the site. You make your way back to the village.").withStyle(net.minecraft.ChatFormatting.GRAY));
        JJK.LOGGER.info("[investigations] {} was down in incident {}'s site as it came down: sent back to its village at {}", p.getName().getString(), in.id, BlockPos.containing(at).toShortString());
    }

    /** A way back from a realm that leads into ground the site is about to fill in leads to the village instead. */
    private static void repointReturns(InvestigationState st, ServerLevel level, Incident in) {
        if (st.returns.isEmpty() || in.footprint == null) return;
        String dim = level.dimension().identifier().toString();
        Vec3 village = null;
        for (var e : st.returns.entrySet()) {
            InvestigationState.Return r = e.getValue();
            if (!dim.equals(r.dimension()) || r.pos().distanceToSqr(Vec3.atCenterOf(in.site)) > 64 * 64) continue;
            if (!SiteFootprint.carved(level, in.footprint, BlockPos.containing(r.pos()))) continue;
            if (village == null) village = villageSpot(level, in);
            e.setValue(new InvestigationState.Return(r.dimension(), village, r.yaw()));
            st.markDirty();
        }
    }

    @Nullable
    public static Entity curseEntity(MinecraftServer server, UUID id) {
        for (ServerLevel l : server.getAllLevels()) {
            Entity e = l.getEntity(id);
            if (e != null) return e;
        }
        return null;
    }
}
