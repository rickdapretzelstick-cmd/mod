package dev.rick.jjk.progression.training;

import dev.rick.jjk.JJK;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** The training's packets. The server decides everything; the client only shows it and reports key timings. */
public final class TrainingNet {
    private TrainingNet() {}

    /** Attempt states, as both sides number them. */
    public static final int INTRO = 0, ACTIVE = 1, SUCCESS = 2, FAILURE = 3, SYNC_LOST = 4, ENDED = 5;
    /** Client actions. PRESS/RELEASE are key edges on a gauge; the rest answer the session. */
    public static final int PRESS = TrainingEvaluator.PRESS, RELEASE = TrainingEvaluator.RELEASE, READY = 2, RETRY = 3, LEAVE = 4, CANCEL = 5;

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Input.TYPE, Input.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Answer.TYPE, Answer.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Offer.TYPE, Offer.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Start.TYPE, Start.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(State.TYPE, State.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Judge.TYPE, Judge.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Input.TYPE, (p, ctx) -> {
            // The same skill check drives a seal's containment (progression.guardian.EssenceCapture).
            if (!dev.rick.jjk.progression.guardian.EssenceCapture.input(ctx.player(), p)) TrainingSessions.input(ctx.player(), p);
        });
        ServerPlayNetworking.registerGlobalReceiver(Answer.TYPE, (p, ctx) -> TrainingSessions.answer(ctx.player(), p.kit(), p.accept()));
    }

    private static void vec(RegistryFriendlyByteBuf buf, Vec3 v) {
        buf.writeDouble(v.x);
        buf.writeDouble(v.y);
        buf.writeDouble(v.z);
    }

    private static Vec3 vec(RegistryFriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    /** Server → client: the relic offers its training ("Begin Training", or "Practice Again" once it is yours). */
    public record Offer(String kit, String name, boolean practice, int color, String line) implements CustomPacketPayload {
        public static final Type<Offer> TYPE = new Type<>(JJK.id("training_offer"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Offer> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeUtf(p.kit, 32);
            buf.writeUtf(p.name, 64);
            buf.writeBoolean(p.practice);
            buf.writeInt(p.color);
            buf.writeUtf(p.line, 256);
        }, buf -> new Offer(buf.readUtf(32), buf.readUtf(64), buf.readBoolean(), buf.readInt(), buf.readUtf(256)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client → server: the answer to an offer. */
    public record Answer(String kit, boolean accept) implements CustomPacketPayload {
        public static final Type<Answer> TYPE = new Type<>(JJK.id("training_answer"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Answer> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeUtf(p.kit, 32);
            buf.writeBoolean(p.accept);
        }, buf -> new Answer(buf.readUtf(32), buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Server → participant: an attempt begins (its intro starts now). Everything the client needs to show it: the
     * character, the fixed camera, the gauges, and the whole schedule (generated from {@code seed} on the server).
     */
    public record Start(int session, int attempt, String kit, String title, boolean practice, long seed, int introMs, int durationMs, int phase2AtMs,
                        int phase3AtMs, int maxMisses, Vec3 camera, Vec3 look, List<String> labels, List<Integer> colors, List<TrainingCheck> checks)
            implements CustomPacketPayload {
        public static final Type<Start> TYPE = new Type<>(JJK.id("training_start"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Start> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.session);
            buf.writeVarInt(p.attempt);
            buf.writeUtf(p.kit, 32);
            buf.writeUtf(p.title, 64);
            buf.writeBoolean(p.practice);
            buf.writeLong(p.seed);
            buf.writeVarInt(p.introMs);
            buf.writeVarInt(p.durationMs);
            buf.writeVarInt(p.phase2AtMs);
            buf.writeVarInt(p.phase3AtMs);
            buf.writeVarInt(p.maxMisses);
            vec(buf, p.camera);
            vec(buf, p.look);
            buf.writeVarInt(p.labels.size());
            for (String s : p.labels) buf.writeUtf(s, 32);
            buf.writeVarInt(p.colors.size());
            for (int c : p.colors) buf.writeInt(c);
            buf.writeVarInt(p.checks.size());
            for (TrainingCheck c : p.checks) c.write(buf);
        }, buf -> {
            int session = buf.readVarInt(), attempt = buf.readVarInt();
            String kit = buf.readUtf(32), title = buf.readUtf(64);
            boolean practice = buf.readBoolean();
            long seed = buf.readLong();
            int intro = buf.readVarInt(), dur = buf.readVarInt(), p2 = buf.readVarInt(), p3 = buf.readVarInt(), max = buf.readVarInt();
            Vec3 cam = vec(buf), look = vec(buf);
            int nl = Math.min(8, buf.readVarInt());
            List<String> labels = new ArrayList<>();
            for (int i = 0; i < nl; i++) labels.add(buf.readUtf(32));
            int nc = Math.min(8, buf.readVarInt());
            List<Integer> colors = new ArrayList<>();
            for (int i = 0; i < nc; i++) colors.add(buf.readInt());
            int n = Math.min(512, buf.readVarInt());
            List<TrainingCheck> checks = new ArrayList<>();
            for (int i = 0; i < n; i++) checks.add(TrainingCheck.read(buf));
            return new Start(session, attempt, kit, title, practice, seed, intro, dur, p2, p3, max, cam, look, labels, colors, checks);
        });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server → participant: the attempt's state (on every change, and every second as a heartbeat). */
    public record State(int session, int attempt, int state, int misses, int clockMs, int stateTicks) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(JJK.id("training_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.session);
            buf.writeVarInt(p.attempt);
            buf.writeByte(p.state);
            buf.writeByte(p.misses);
            buf.writeInt(p.clockMs);
            buf.writeVarInt(p.stateTicks);
        }, buf -> new State(buf.readVarInt(), buf.readVarInt(), buf.readByte(), buf.readByte(), buf.readInt(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server → participant: the verdict on one check (it replaces the client's own prediction). */
    public record Judge(int session, int attempt, int check, int result, int misses) implements CustomPacketPayload {
        public static final Type<Judge> TYPE = new Type<>(JJK.id("training_judge"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Judge> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.session);
            buf.writeVarInt(p.attempt);
            buf.writeVarInt(p.check);
            buf.writeByte(p.result);
            buf.writeByte(p.misses);
        }, buf -> new Judge(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readByte(), buf.readByte()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Client → server: a key edge on a gauge (PRESS/RELEASE, for {@code check}, at {@code clientMs} of the challenge
     * on the client's clock), or an answer to the session (READY, RETRY, LEAVE, CANCEL). {@code seq} rises with every
     * input of an attempt, so a repeat is recognised.
     */
    public record Input(int session, int attempt, int seq, int action, int gauge, int check, int clientMs) implements CustomPacketPayload {
        public static final Type<Input> TYPE = new Type<>(JJK.id("training_input"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Input> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.session);
            buf.writeVarInt(p.attempt);
            buf.writeVarInt(p.seq);
            buf.writeByte(p.action);
            buf.writeByte(p.gauge);
            buf.writeVarInt(p.check + 1);
            buf.writeInt(p.clientMs);
        }, buf -> new Input(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readByte(), buf.readByte(), buf.readVarInt() - 1, buf.readInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
