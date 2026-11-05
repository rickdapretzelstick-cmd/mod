package dev.rick.jjk.progression.investigation;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.progression.CursedEncounters;
import dev.rick.jjk.progression.ProgressionBlocks;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The places the mod builds can't be broken down for their blocks, by a technique or by hand:
 * <ul>
 *   <li>every cursed realm arena (the incident realms, the guardian arenas, the battle-room realms, the training rooms);</li>
 *   <li>an incident's overworld site while it stands (only the site's own blocks: what it put there and still is);</li>
 *   <li>a village's news house (rebuilt from its board, so houses built before this version are covered too);</li>
 *   <li>a cursed battle room's shell under its mansion or igloo.</li>
 * </ul>
 * Techniques ({@link dev.rick.jjk.util.Destruction}), players breaking blocks (survival and adventure; creative still can,
 * for admins), explosions and pistons all ask here. The game itself is not stopped: closing an arena, taking a site
 * down and every other cleanup set blocks directly and never come through these checks. Switch:
 * {@code general.protectModStructures}.
 */
public final class ProtectedPlaces {
    /** Per incident: its site's own blocks (position to the state the site built there). Rebuilt when the footprint changes. */
    private static final Map<Incident, Index> SITES = new IdentityHashMap<>();

    private record Index(List<SiteFootprint.Change> from, int size, Long2ObjectOpenHashMap<BlockState> built, int[] box) {}

    private ProtectedPlaces() {}

    public static void init() {
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> {
            if (!(level instanceof ServerLevel sl) || player.isCreative() || player.isSpectator()) return true;
            if (!isProtected(sl, pos, state)) return true;
            if (player instanceof ServerPlayer sp) {
                sp.sendOverlayMessage(Component.literal("Cursed energy holds this place together. It won't break.").withStyle(ChatFormatting.DARK_PURPLE));
                sl.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.BLOCKS, 0.6f, 0.6f);
            }
            return false;
        });
    }

    public static boolean enabled() {
        return JJKConfig.get().general.protectModStructures;
    }

    public static boolean isProtected(ServerLevel level, BlockPos pos) {
        return isProtected(level, pos, level.getBlockState(pos));
    }

    /** Whether the block at {@code pos} (now {@code state}) belongs to a place the mod built that may not be broken. */
    public static boolean isProtected(ServerLevel level, BlockPos pos, BlockState state) {
        if (!enabled() || state.isAir()) return false;
        if (CursedRealms.inArena(level, pos)) return true;
        var server = level.getServer();
        if (server == null) return false;
        InvestigationState st = InvestigationState.get(server);
        String dim = level.dimension().identifier().toString();
        if (siteBlock(level, st, dim, pos, state)) return true;
        if (newsHouse(level, st, dim, pos)) return true;
        return battleRoom(server, dim, pos);
    }

    private static boolean siteBlock(ServerLevel level, InvestigationState st, String dim, BlockPos pos, BlockState state) {
        if (SITES.size() > 256) SITES.clear();
        for (Incident in : st.incidents.values()) {
            if (!in.dimension.equals(dim) || in.footprint == null && in.footprintRaw == null) continue;
            if (in.footprint == null) {
                // Decoded the same way taking the site down decodes it (it needs the world's block registry).
                in.footprint = SiteFootprint.load(level, in.footprintRaw);
                in.footprintRaw = null;
            }
            Index idx = SITES.get(in);
            if (idx == null || idx.from != in.footprint || idx.size != in.footprint.size()) {
                idx = index(in.footprint);
                SITES.put(in, idx);
            }
            int[] b = idx.box;
            if (pos.getX() < b[0] || pos.getX() > b[3] || pos.getY() < b[1] || pos.getY() > b[4] || pos.getZ() < b[2] || pos.getZ() > b[5]) continue;
            BlockState built = idx.built.get(pos.asLong());
            // The site's own block, still as it built it (anything a player has put there since is theirs).
            if (built != null && built == state && !built.isAir()) return true;
        }
        return false;
    }

    private static Index index(List<SiteFootprint.Change> fp) {
        Long2ObjectOpenHashMap<BlockState> built = new Long2ObjectOpenHashMap<>();
        int[] box = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (SiteFootprint.Change c : fp) {
            built.put(c.pos(), c.built());
            int x = BlockPos.getX(c.pos()), y = BlockPos.getY(c.pos()), z = BlockPos.getZ(c.pos());
            box[0] = Math.min(box[0], x);
            box[1] = Math.min(box[1], y);
            box[2] = Math.min(box[2], z);
            box[3] = Math.max(box[3], x);
            box[4] = Math.max(box[4], y);
            box[5] = Math.max(box[5], z);
        }
        return new Index(fp, fp.size(), built, box);
    }

    /** A news house: the board hangs on its back wall facing the door, so the house's whole box follows from the board. */
    private static boolean newsHouse(ServerLevel level, InvestigationState st, String dim, BlockPos pos) {
        for (InvestigationState.Village v : st.villages.values()) {
            BlockPos board = v.board;
            if (board == null || !v.dimension.equals(dim)) continue;
            if (Math.abs(pos.getX() - board.getX()) > 12 || Math.abs(pos.getZ() - board.getZ()) > 12 || Math.abs(pos.getY() - board.getY()) > 12) continue;
            if (!level.isLoaded(board)) continue;
            BlockState bs = level.getBlockState(board);
            if (!bs.is(ProgressionBlocks.NEWS_BOARD)) continue;
            Direction out = bs.getValue(HorizontalDirectionalBlock.FACING);
            BlockPos ground = board.relative(out, NewsHouse.DEPTH - 2).below();
            // A board standing in the open (no room for a house) has no house round it.
            if (level.getBlockState(NewsHouse.at(ground, out, -NewsHouse.HX, 1, 0)).isAir()
                    || level.getBlockState(NewsHouse.at(ground, out, 0, 1, NewsHouse.DEPTH - 1)).isAir()) continue;
            for (int x = -NewsHouse.HX - 1; x <= NewsHouse.HX + 1; x++) {
                for (int z = -1; z <= NewsHouse.DEPTH; z++) {
                    BlockPos col = NewsHouse.at(ground, out, x, 0, z);
                    if (col.getX() == pos.getX() && col.getZ() == pos.getZ()) {
                        int dy = pos.getY() - ground.getY();
                        if (dy >= -4 && dy <= NewsHouse.WALL + NewsHouse.HX + 1) return true;
                    }
                }
            }
        }
        return false;
    }

    /** A cursed battle room's shell: its seal sits in the middle of the floor; walls 7 out, the ceiling 8 up. */
    private static boolean battleRoom(net.minecraft.server.MinecraftServer server, String dim, BlockPos pos) {
        for (CursedEncounters.Room room : CursedEncounters.rooms(server)) {
            if (!room.dimension.equals(dim)) continue;
            BlockPos s = room.seal;
            if (Math.abs(pos.getX() - s.getX()) <= 7 && Math.abs(pos.getZ() - s.getZ()) <= 7 && pos.getY() >= s.getY() - 1 && pos.getY() <= s.getY() + 8) return true;
        }
        return false;
    }
}
