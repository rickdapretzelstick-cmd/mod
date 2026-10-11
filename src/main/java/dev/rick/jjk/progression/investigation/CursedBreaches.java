package dev.rick.jjk.progression.investigation;

import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.progression.CursedEncounters;
import dev.rick.jjk.progression.curse.FingerBearerEncounter;
import dev.rick.jjk.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Cursed Breaches: every cursed realm is entered the same way. A report says roughly where; the Cursed Compass finds the
 * place; at the place (the cliff's lip, the end of the mine, inside the empty house, out in the lodge's trees, at the
 * theater's projector, over a battle room's seal) hangs a breach; use it and the place takes you. What the place looks
 * like, and what realm is on the other side, is the incident's; how you go in never changes.
 *
 * <p>A site raises its breach whenever someone is near and the incident is open (or under way: others can follow those
 * already inside into the same arena); the breach closes itself the moment its incident is over or gone.
 */
public final class CursedBreaches {
    private CursedBreaches() {}

    /** Where an incident's breach hangs: the place its story points at. */
    public static BlockPos anchor(Incident in) {
        BlockPos b = in.mark("breach");
        if (b != null) return b;
        IncidentTemplate t = in.def();
        if (t != null && "hillside".equals(t.site())) return Sites.mineEnd(in);
        BlockPos a = in.mark("anomaly");
        if (a != null) return a;
        BlockPos o = in.mark("object");
        if (o != null) return o.above();
        return in.site;
    }

    /** Makes sure there is a breach for {@code target} at {@code at} (a block position: it hangs half a block up in it). */
    public static void ensure(ServerLevel level, String target, BlockPos at) {
        if (!level.isLoaded(at)) return;
        List<CursedBreachEntity> near = level.getEntitiesOfClass(CursedBreachEntity.class, new AABB(at).inflate(4), e -> target.equals(e.target));
        if (!near.isEmpty()) {
            // Never two for one place (a reload racing a respawn).
            for (int i = 1; i < near.size(); i++) near.get(i).discard();
            return;
        }
        CursedBreachEntity e = ModEntities.CURSED_BREACH.create(level, EntitySpawnReason.EVENT);
        if (e == null) return;
        e.target = target;
        Vec3 c = Vec3.atBottomCenterOf(at).add(0, 0.25, 0);
        e.snapTo(c.x, c.y, c.z, 0, 0);
        level.addFreshEntity(e);
    }

    /** Whether a breach still leads anywhere, and if someone is already through it. -1: closed for good. */
    static int status(ServerLevel level, CursedBreachEntity e) {
        var server = level.getServer();
        if (e.target.startsWith(CursedRealms.ROOM)) {
            CursedEncounters.Room room = CursedEncounters.byKey(server, e.target.substring(CursedRealms.ROOM.length()));
            if (room == null || room.state == CursedEncounters.State.CLEARED) return -1;
            return CursedRealms.arenaOf(InvestigationState.get(server), e.target) != null ? 1 : 0;
        }
        InvestigationState st = InvestigationState.get(server);
        Incident in = st.incidents().get(e.target);
        if (in == null || !in.dimension.equals(level.dimension().identifier().toString())) return -1;
        if (in.state() == Incident.State.OPEN) return 0;
        return in.state() == Incident.State.ACTIVE ? 1 : -1;
    }

    static void tick(ServerLevel level, CursedBreachEntity e) {
        if (e.tickCount % 20 == 1) {
            int s = status(level, e);
            if (s < 0) {
                Fx.play(level, "breach_close", e.position().add(0, 0.9, 0), Vec3.ZERO, 1f, e.getId());
                e.discard();
                return;
            }
            e.setBreachState(s);
        }
        // A low drone now and then, and a pulse.
        if (e.tickCount % 70 == 13) {
            level.playSound(null, e.getX(), e.getY() + 0.9, e.getZ(), SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD.value(), SoundSource.AMBIENT, 0.6f, 0.55f);
        }
        if (e.tickCount % 40 == 0) level.playSound(null, e.getX(), e.getY() + 0.9, e.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.AMBIENT, 0.5f, 0.5f);
    }

    /** Someone reaches into a breach. */
    static void use(ServerPlayer p, CursedBreachEntity e) {
        if (!(p.level() instanceof ServerLevel level) || p.isSpectator() || CursedRealms.pulling(p) || CursedRealms.inRealm(p)) return;
        if (p.position().distanceToSqr(e.position()) > 6 * 6) return;
        int s = status(level, e);
        if (s < 0) {
            e.discard();
            return;
        }
        Fx.play(level, "breach_use", e.position().add(0, 0.9, 0), Vec3.ZERO, 1f, e.getId());
        level.playSound(null, e.getX(), e.getY() + 0.9, e.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.AMBIENT, 0.8f, 0.6f);
        if (e.target.startsWith(CursedRealms.ROOM)) {
            FingerBearerEncounter.pullIntoRealm(p, e.target.substring(CursedRealms.ROOM.length()));
            return;
        }
        InvestigationState st = InvestigationState.get(level.getServer());
        Incident in = st.incidents().get(e.target);
        if (in == null || in.def() == null) return;
        p.sendOverlayMessage(Component.literal(s == 1 ? "Someone is already on the other side." : "The air gives way under your hand.")
                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        Investigations.begin(level, p, in, in.def(), st, level.getServer().overworld().getGameTime());
    }

    /** Test hook: the breach for a target near a spot, or null. */
    public static CursedBreachEntity find(ServerLevel level, String target, BlockPos near) {
        return level.getEntitiesOfClass(CursedBreachEntity.class, new AABB(near).inflate(4), e -> target.equals(e.target)).stream().findFirst().orElse(null);
    }

    /** Test hook: use a breach as a player would. */
    public static void useForTest(ServerPlayer p, CursedBreachEntity e) {
        use(p, e);
    }
}
