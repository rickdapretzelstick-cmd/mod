package dev.rick.jjk.progression.investigation;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.net.CompassPayload;
import dev.rick.jjk.progression.ProgressionItems;
import dev.rick.jjk.registry.ModAttachments;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Cursed Compass: a compass steeped in cursed energy. A news board only says roughly where something happened; the
 * compass, carried by someone investigating it (chosen at the board), finds the exact place. Far from the reported
 * area it only wanders; once there it settles on the source, and grows restless close to it. It never says what sets
 * the incident off: it points at the place, nothing more.
 *
 * <p>The source's position is only sent to the client inside {@link JJKConfig.Realms#compassRange} of it (the board's
 * "roughly where" still has to be walked to).
 */
public final class CursedCompass {
    private CursedCompass() {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(CursedCompass::tick);
    }

    /** A player picks an incident at a news board as their investigation (or picks it again to drop it). */
    public static void investigate(ServerPlayer p, String id) {
        InvestigationState st = InvestigationState.get(p.level().getServer());
        String current = p.getAttached(ModAttachments.INVESTIGATING);
        if (id.isEmpty() || id.equals(current)) {
            p.removeAttached(ModAttachments.INVESTIGATING);
            p.sendOverlayMessage(Component.literal("You set the report aside.").withStyle(ChatFormatting.GRAY));
            return;
        }
        Incident in = st.incidents().get(id);
        if (in == null || (in.state() != Incident.State.OPEN && in.state() != Incident.State.ACTIVE)) {
            p.sendOverlayMessage(Component.literal("That report is out of date.").withStyle(ChatFormatting.GRAY));
            return;
        }
        p.setAttached(ModAttachments.INVESTIGATING, id);
        p.sendOverlayMessage(Component.literal("Investigating: " + in.headline).withStyle(ChatFormatting.DARK_PURPLE));
    }

    /** The incident a player is investigating, if it is still going. */
    @Nullable
    public static Incident tracked(ServerPlayer p) {
        String id = p.getAttached(ModAttachments.INVESTIGATING);
        if (id == null) return null;
        return InvestigationState.get(p.level().getServer()).incidents().get(id);
    }

    /** Exactly where an incident is anchored: what its trigger is at (the mine's end, the lodge's anomaly), else its site. */
    public static BlockPos anchor(Incident in) {
        IncidentTemplate t = in.def();
        if (t != null && t.trigger() == IncidentTemplate.Trigger.DESCEND) return Sites.mineEnd(in);
        BlockPos a = in.mark("anomaly");
        return a != null ? a : in.site;
    }

    /** What a player's compass can feel right now. */
    public static CompassPayload reading(ServerPlayer p) {
        String id = p.getAttached(ModAttachments.INVESTIGATING);
        if (id == null) return new CompassPayload(CompassPayload.DORMANT, 0, 0, 0);
        Incident in = InvestigationState.get(p.level().getServer()).incidents().get(id);
        if (in == null || in.state() == Incident.State.COMPLETE || in.state() == Incident.State.EXPIRED) {
            return new CompassPayload(CompassPayload.GONE, 0, 0, 0);
        }
        if (!in.dimension.equals(p.level().dimension().identifier().toString())) return new CompassPayload(CompassPayload.FAINT, 0, 0, 0);
        Vec3 at = Vec3.atCenterOf(anchor(in));
        double range = JJKConfig.get().realms.compassRange;
        if (p.position().distanceToSqr(at) > range * range) return new CompassPayload(CompassPayload.FAINT, 0, 0, 0);
        return new CompassPayload(CompassPayload.TRAIL, at.x, at.y, at.z);
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 10 != 0) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!carrying(p)) continue;
            ServerPlayNetworking.send(p, reading(p));
        }
    }

    private static boolean carrying(ServerPlayer p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(ProgressionItems.CURSED_COMPASS)) return true;
        return false;
    }
}
