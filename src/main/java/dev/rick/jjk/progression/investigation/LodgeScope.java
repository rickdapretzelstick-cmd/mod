package dev.rick.jjk.progression.investigation;

import dev.rick.jjk.core.net.FxPayload;
import dev.rick.jjk.core.net.ScopeViewPayload;
import dev.rick.jjk.progression.CursePerception;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The lodge's mounted scope. Put your eye to it (use it) and you see the woods out of the window, magnified. Through
 * it, and only for someone who perceives curses (the existing rule: Cursed Glasses), something out there doesn't belong:
 * hold the scope on that spot and it resolves into a crooked trail and a figure standing on it, with a sound. Use the
 * scope again then, deliberately, to follow it into the cursed realm. Nothing happens from touching it, looking away
 * lets the image fade, and stepping back from the sill (or sneaking) takes your eye off it.
 *
 * <p>Without perception the scope still works as a scope: the spot only "doesn't sit right", which says something is
 * missing without inventing a new rule.
 */
public final class LodgeScope {
    /** Ticks of holding the anomaly in view for it to resolve; how close (degrees) the aim must be. */
    public static final int HOLD_TICKS = 40;
    public static final double AIM_DEGREES = 5.0;

    static final class Looking {
        final String incident;
        final BlockPos scope;
        /** The scope's front lens: where the view through it is from (in front of the scope's own body). */
        Vec3 eye = Vec3.ZERO;
        int hold;
        boolean revealed;
        int hintCooldown;

        Looking(String incident, BlockPos scope) {
            this.incident = incident;
            this.scope = scope;
        }
    }

    private static final Map<UUID, Looking> LOOKING = new HashMap<>();

    private LodgeScope() {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(LodgeScope::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> LOOKING.remove(h.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> LOOKING.clear());
    }

    @Nullable
    static Incident incidentAt(InvestigationState st, ServerLevel level, BlockPos scope) {
        String dim = level.dimension().identifier().toString();
        for (Incident in : st.incidents.values()) {
            if (in.dimension.equals(dim) && scope.equals(in.mark("scope"))) return in;
        }
        return null;
    }

    /** The scope used. */
    static void use(ServerPlayer p, ServerLevel level, BlockPos pos, Direction facing) {
        InvestigationState st = InvestigationState.get(level.getServer());
        Looking l = LOOKING.get(p.getUUID());
        if (l != null && l.scope.equals(pos)) {
            if (l.revealed) {
                // What it showed: where the trail ends, the air is torn (the incident's breach is out there).
                stop(p);
                p.sendOverlayMessage(Component.literal("Where the trail ends, out in the trees, the air itself is torn open.")
                        .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
            } else {
                stop(p);
            }
            return;
        }
        Incident in = incidentAt(st, level, pos);
        if (in == null || in.state == Incident.State.COMPLETE || in.state == Incident.State.EXPIRED) {
            p.sendOverlayMessage(Component.literal(in != null && in.state == Incident.State.COMPLETE
                    ? "Just the woods, and the path the hunter used to take." : "Through the scope: only trees.").withStyle(ChatFormatting.GRAY));
            return;
        }
        Looking look = new Looking(in.id, pos.immutable());
        look.eye = Vec3.atCenterOf(pos).add(facing.getStepX() * 0.55, 0.22, facing.getStepZ() * 0.55);
        LOOKING.put(p.getUUID(), look);
        Investigations.clue(p, in, Investigations.CLUE_SCOPE, null);
        send(p, look, 0f, false);
        p.sendOverlayMessage(Component.literal("You put your eye to the scope.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    static void stop(ServerPlayer p) {
        if (LOOKING.remove(p.getUUID()) != null) ServerPlayNetworking.send(p, new ScopeViewPayload(false, 0f, false));
    }

    public static boolean looking(ServerPlayer p) {
        return LOOKING.containsKey(p.getUUID());
    }

    public static boolean revealed(ServerPlayer p) {
        Looking l = LOOKING.get(p.getUUID());
        return l != null && l.revealed;
    }

    private static void tick(MinecraftServer server) {
        if (LOOKING.isEmpty()) return;
        InvestigationState st = InvestigationState.get(server);
        for (Map.Entry<UUID, Looking> e : List.copyOf(LOOKING.entrySet())) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            Looking l = e.getValue();
            Incident in = st.incidents.get(l.incident);
            if (p == null || in == null || !(p.level() instanceof ServerLevel level) || in.state != Incident.State.OPEN) {
                LOOKING.remove(e.getKey());
                if (p != null) ServerPlayNetworking.send(p, new ScopeViewPayload(false, 0f, false));
                continue;
            }
            // Stepping back from the sill, or sneaking, takes your eye off it.
            if (p.position().distanceTo(Vec3.atCenterOf(l.scope)) > 2.6 || p.isShiftKeyDown() || !p.isAlive()) {
                stop(p);
                continue;
            }
            tickLook(level, p, l, in);
        }
    }

    /** One tick of looking through the scope. */
    static void tickLook(ServerLevel level, ServerPlayer p, Looking l, Incident in) {
        BlockPos anomaly = in.mark("anomaly");
        if (anomaly == null) return;
        Vec3 to = Vec3.atCenterOf(anomaly).add(0, 1, 0).subtract(l.eye.lengthSqr() > 0 ? l.eye : p.getEyePosition());
        double angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, to.normalize().dot(p.getLookAngle())))));
        boolean onIt = angle <= AIM_DEGREES;
        if (l.hintCooldown > 0) l.hintCooldown--;
        if (l.revealed) {
            if (level.getGameTime() % 4 == 0) send(p, "lodge_anomaly", anomaly, 1f);
            return;
        }
        if (!CursePerception.canPerceive(p)) {
            // The scope can't show what the eye can't see: a hint that something is missing, nothing more.
            if (onIt && l.hintCooldown == 0) {
                p.sendOverlayMessage(Component.literal("Something out there doesn't sit right, but your eye slides off it.")
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
                l.hintCooldown = 80;
            }
            return;
        }
        l.hold = onIt ? l.hold + 1 : Math.max(0, l.hold - 2);
        float progress = l.hold / (float) HOLD_TICKS;
        if (l.hold > 0 && level.getGameTime() % 3 == 0) send(p, "lodge_anomaly", anomaly, progress);
        if (level.getGameTime() % 5 == 0) send(p, l, progress, false);
        if (l.hold >= HOLD_TICKS) {
            l.revealed = true;
            send(p, "lodge_reveal", anomaly, 1f);
            Investigations.hear(p, p.getEyePosition(), SoundEvents.WARDEN_HEARTBEAT, 1.4f, 0.6f);
            Investigations.hear(p, p.getEyePosition(), SoundEvents.SOUL_ESCAPE.value(), 1.4f, 0.5f);
            send(p, l, 1f, true);
            Investigations.clue(p, in, Investigations.CLUE_ANOMALY, null);
            p.sendOverlayMessage(Component.literal("A crooked trail through the trees, and something standing on it, where the air is torn.")
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
        }
    }

    private static void send(ServerPlayer p, Looking l, float progress, boolean revealed) {
        ServerPlayNetworking.send(p, new ScopeViewPayload(true, progress, revealed, l.eye.x, l.eye.y, l.eye.z));
    }

    private static void send(ServerPlayer p, String id, BlockPos at, float s) {
        ServerPlayNetworking.send(p, new FxPayload(id, Vec3.atBottomCenterOf(at), Vec3.ZERO, s, -1));
    }

    /** Test hook: look through a scope for a tick as the server would (bypasses distance). */
    public static void tickForTest(ServerPlayer p) {
        Looking l = LOOKING.get(p.getUUID());
        InvestigationState st = InvestigationState.get(p.level().getServer());
        Incident in = l == null ? null : st.incidents.get(l.incident);
        if (in != null) tickLook((ServerLevel) p.level(), p, l, in);
    }

    public static void stopForTest(ServerPlayer p) {
        stop(p);
    }

    public static void useForTest(ServerPlayer p, BlockPos pos) {
        var st = p.level().getBlockState(pos);
        use(p, (ServerLevel) p.level(), pos, st.hasProperty(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING)
                ? st.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING) : Direction.NORTH);
    }
}
