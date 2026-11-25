package dev.rick.jjk.battle.game;

import dev.rick.jjk.JJK;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/** The turn-based battle's network messages: the state and each beat go down, the player's choice goes up. */
public final class BattlePayloads {
    private BattlePayloads() {}

    public static final int CHOOSING = 0, PLAYING = 1, DONE = 2, CLOSED = 3;
    /** Per-side numbers in {@link State#stats}: hp, ce, meter, control, stance, slow, burn, zone, domain. */
    public static final int STATS = 9;

    /**
     * Server → client: the battle as this player sees it.
     *
     * @param side   which side the receiving player is (0 or 1), or -1 for a spectator
     * @param picked whether the receiving player has locked in this turn
     */
    public record State(int session, int side, int phase, int timer, int turn, int range, int winner, boolean picked,
                        String char0, String char1, int entity0, int entity1, List<Integer> stats) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(JJK.id("battle_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.session); buf.writeVarInt(p.side + 1); buf.writeVarInt(p.phase); buf.writeVarInt(p.timer);
            buf.writeVarInt(p.turn); buf.writeVarInt(p.range); buf.writeVarInt(p.winner + 1); buf.writeBoolean(p.picked);
            buf.writeUtf(p.char0, 32); buf.writeUtf(p.char1, 32); buf.writeVarInt(p.entity0); buf.writeVarInt(p.entity1);
            buf.writeVarInt(p.stats.size());
            for (int v : p.stats) buf.writeVarInt(v);
        }, buf -> {
            int session = buf.readVarInt(), side = buf.readVarInt() - 1, phase = buf.readVarInt(), timer = buf.readVarInt();
            int turn = buf.readVarInt(), range = buf.readVarInt(), winner = buf.readVarInt() - 1;
            boolean picked = buf.readBoolean();
            String c0 = buf.readUtf(32), c1 = buf.readUtf(32);
            int e0 = buf.readVarInt(), e1 = buf.readVarInt();
            int n = Math.min(buf.readVarInt(), 64);
            List<Integer> stats = new ArrayList<>(n);
            for (int i = 0; i < n; i++) stats.add(buf.readVarInt());
            return new State(session, side, phase, timer, turn, range, winner, picked, c0, c1, e0, e1, stats);
        });

        public int stat(int side, int which) {
            int i = side * STATS + which;
            return i < stats.size() ? stats.get(i) : 0;
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Server → client: a beat starts playing. {@code length} is how long its scene runs and {@code contact} the tick
     * the hit lands (the client's camera and title cards key off them).
     */
    public record BeatStart(int session, int index, int count, int actor, String move, int kind, int damage, int link,
                            boolean blackFlash, String note, int hp0, int hp1, int length, int contact, boolean control) implements CustomPacketPayload {
        public static final Type<BeatStart> TYPE = new Type<>(JJK.id("battle_beat"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BeatStart> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.session); buf.writeVarInt(p.index); buf.writeVarInt(p.count); buf.writeVarInt(p.actor);
            buf.writeUtf(p.move, 64); buf.writeVarInt(p.kind); buf.writeVarInt(p.damage); buf.writeVarInt(p.link);
            buf.writeBoolean(p.blackFlash); buf.writeUtf(p.note, 64); buf.writeVarInt(p.hp0); buf.writeVarInt(p.hp1);
            buf.writeVarInt(p.length); buf.writeVarInt(p.contact); buf.writeBoolean(p.control);
        }, buf -> new BeatStart(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(64),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readUtf(64), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client → server: the player locks in a plan (or forfeits). */
    public record Choice(int session, List<String> chain, int boost, boolean forfeit) implements CustomPacketPayload {
        public static final Type<Choice> TYPE = new Type<>(JJK.id("battle_choice"));
        private static final StreamCodec<ByteBuf, List<String>> CHAIN = ByteBufCodecs.stringUtf8(64).apply(ByteBufCodecs.list(4));
        public static final StreamCodec<RegistryFriendlyByteBuf, Choice> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.session); CHAIN.encode(buf, p.chain); buf.writeVarInt(p.boost); buf.writeBoolean(p.forfeit);
        }, buf -> new Choice(buf.readVarInt(), CHAIN.decode(buf), buf.readVarInt(), buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
