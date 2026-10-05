package dev.rick.jjk.progression.investigation;

import dev.rick.jjk.JJK;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.progression.ProgressionBlocks;
import dev.rick.jjk.progression.tool.rifle.RifleClaims;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The hunting lodge's reward: the Cursed Rifle on the gun rack.
 *
 * <ul>
 *   <li>When the lodge's curse is exorcised, everyone who took part is <b>owed</b> a rifle ({@link Incident#rewardsPending},
 *   saved with the incident, apart from its completion), and the rack's seal breaks: the rifle rests on it, visible.</li>
 *   <li>Each of them takes their own, deliberately, from the rack. A full inventory leaves the claim open; being sent home
 *   late, dying, logging out or a restart changes nothing: the claim waits on the rack. Nobody else can take it.</li>
 *   <li>A participant who has taken theirs and no longer carries it (lost, destroyed, left somewhere) can <b>recover</b> it
 *   here: a new rifle, and the old one goes cold ({@link RifleClaims}), so there are never two.</li>
 * </ul>
 */
public final class LodgeRewards {
    public static final String REWARD = "cursed_rifle";

    private LodgeRewards() {}

    static boolean givesRifle(Incident in) {
        IncidentTemplate t = in.def();
        return t != null && REWARD.equals(t.reward());
    }

    /** The incident whose rack is at {@code pos}. */
    @Nullable
    static Incident incidentAt(InvestigationState st, ServerLevel level, BlockPos pos) {
        String dim = level.dimension().identifier().toString();
        for (Incident in : st.incidents.values()) if (in.dimension.equals(dim) && pos.equals(in.mark("rack"))) return in;
        return null;
    }

    /** The lodge's curse is gone: its participants are owed a rifle, and the rack unseals. */
    static void completed(MinecraftServer server, Incident in, InvestigationState st) {
        if (!givesRifle(in)) return;
        for (UUID u : in.participants) if (!in.rewardsClaimed.contains(u)) in.rewardsPending.add(u);
        st.markDirty();
        st.flush();
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(in.dimension)));
        BlockPos rack = in.mark("rack");
        if (level != null && rack != null && level.isLoaded(rack)) {
            refresh(level, in);
            Vec3 at = Vec3.atCenterOf(rack);
            Fx.play(level, "rack_unseal", at, Vec3.ZERO, 1f);
            Fx.sound(level, at, SoundEvents.CHAIN_BREAK, 1.4f, 0.7f);
            Fx.sound(level, at, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2f, 0.6f);
        }
        for (UUID u : in.rewardsPending) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p != null) p.sendSystemMessage(Component.literal("Back at the lodge, the seal on the gun rack has broken.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }

    /** The rack shows what it holds: sealed until the curse is gone, the rifle while anyone is owed one, then empty. */
    static void refresh(ServerLevel level, Incident in) {
        BlockPos pos = in.mark("rack");
        if (pos == null || !level.isLoaded(pos)) return;
        BlockState s = level.getBlockState(pos);
        if (!s.is(ProgressionBlocks.GUN_RACK)) return;
        GunRackBlock.Rack want = in.state != Incident.State.COMPLETE ? GunRackBlock.Rack.SEALED
                : !in.rewardsPending.isEmpty() ? GunRackBlock.Rack.OPEN : GunRackBlock.Rack.EMPTY;
        if (s.getValue(GunRackBlock.RACK) != want) level.setBlock(pos, s.setValue(GunRackBlock.RACK, want), 3);
    }

    /** A player uses the rack. */
    public static void useRack(ServerPlayer p, ServerLevel level, BlockPos pos) {
        InvestigationState st = InvestigationState.get(level.getServer());
        Incident in = incidentAt(st, level, pos);
        if (in == null || !givesRifle(in)) {
            say(p, "An old gun rack, its pegs empty.", ChatFormatting.GRAY);
            return;
        }
        refresh(level, in);
        if (in.state != Incident.State.COMPLETE) {
            say(p, "The rack is bound shut with rusted chain and a curling paper seal. It's cold to the touch.", ChatFormatting.GRAY);
            Fx.sound(level, Vec3.atCenterOf(pos), SoundEvents.CHAIN_HIT, 0.8f, 0.6f);
            return;
        }
        UUID u = p.getUUID();
        boolean owed = in.rewardsPending.contains(u);
        boolean recover = !owed && in.rewardsClaimed.contains(u) && !RifleClaims.carriesLive(p);
        if (!owed && !recover) {
            if (in.rewardsClaimed.contains(u)) say(p, "Your rifle is already with you.", ChatFormatting.GRAY);
            else if (in.rewardsPending.isEmpty()) say(p, "The pegs are empty now.", ChatFormatting.GRAY);
            else say(p, "It isn't yours to take. It wouldn't answer you.", ChatFormatting.GRAY);
            return;
        }
        int slot = p.getInventory().getFreeSlot();
        if (slot < 0) {
            say(p, "You've no room to carry it. It'll wait for you here.", ChatFormatting.GRAY);
            return;
        }
        // The claim is moved first and saved with the new rifle's id (RifleClaims.issue saves): a crash after this
        // leaves them able to recover, never holding two.
        in.rewardsPending.remove(u);
        in.rewardsClaimed.add(u);
        ItemStack rifle = RifleClaims.issue(p);
        p.getInventory().setItem(slot, rifle);
        st.save();
        Vec3 at = Vec3.atCenterOf(pos);
        Fx.play(level, "rack_take", at, Vec3.ZERO, 1f);
        Fx.sound(level, at, SoundEvents.ARMOR_EQUIP_IRON.value(), 1.2f, 0.7f);
        Fx.sound(level, at, SoundEvents.AMETHYST_BLOCK_CHIME, 1.4f, 0.5f);
        refresh(level, in);
        JJK.LOGGER.info("[investigations] {} {} a Cursed Rifle at incident {}", p.getName().getString(), recover ? "recovered" : "claimed", in.id);
        say(p, recover ? "The rack remembers you. The rifle you left behind won't answer any more; this one will."
                : "You lift the Cursed Rifle from the rack. It's heavier than it looks, and warm.", ChatFormatting.DARK_PURPLE);
    }

    private static void say(ServerPlayer p, String line, ChatFormatting colour) {
        p.sendOverlayMessage(Component.literal(line).withStyle(colour, ChatFormatting.ITALIC));
    }

    /** Keeps the racks near a player looking right (a rack's chunk may have been unloaded when its curse fell). */
    static void visit(ServerLevel level, ServerPlayer p, InvestigationState st) {
        String dim = level.dimension().identifier().toString();
        for (Incident in : st.incidents.values()) {
            BlockPos rack = in.mark("rack");
            if (rack == null || !in.dimension.equals(dim) || p.blockPosition().distSqr(rack) > 48 * 48) continue;
            refresh(level, in);
        }
    }
}
