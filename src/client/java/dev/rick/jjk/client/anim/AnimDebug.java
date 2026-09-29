package dev.rick.jjk.client.anim;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.rick.jjk.client.anim.rig.Bone;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * The animation debugger: pause, step, slow down and replay a clip on one entity (yourself, or whatever you target),
 * with a readout of exactly where playback is and a skeleton overlay. Driven by {@code /jjkanim} and its control panel
 * ({@code /jjkanim ui}); nothing here changes gameplay.
 */
public final class AnimDebug {
    /** The entity being debugged; {@link Integer#MIN_VALUE} means the local player. */
    public static int target = Integer.MIN_VALUE;
    public static boolean enabled;
    public static boolean paused;
    public static float speed = 1;
    public static boolean hud = true, skeleton, names = true, axes;
    /** Frames to step on the target's next update (negative steps back). */
    private static int pendingFrames;
    @Nullable private static String lastClip;
    private static boolean openUi;

    private AnimDebug() {}

    public static int targetId() {
        Minecraft mc = Minecraft.getInstance();
        if (target == Integer.MIN_VALUE) return mc.player == null ? Integer.MIN_VALUE : mc.player.getId();
        return target;
    }

    static boolean affects(int id) {
        return enabled && id == targetId();
    }

    public static boolean wantsSkeleton(int id) {
        return enabled && skeleton && id == targetId();
    }

    /** The scrub distance in ms owed to the target, in its top clip's frames. */
    static float takeStep(int id, AnimPlayer p) {
        if (pendingFrames == 0) return 0;
        AnimPlayer.Instance top = p.top();
        float fps = top == null ? 50 : top.clip.fps;
        float ms = pendingFrames * 1000f / fps;
        pendingFrames = 0;
        return ms;
    }

    // ---------------------------------------------------------------- controls

    public static void play(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        enabled = true;
        lastClip = name;
        ClientAnimations.play(targetId(), name, 1f, mc.level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
    }

    public static void restart() {
        AnimPlayer p = ClientAnimations.player(targetId());
        AnimPlayer.Instance top = p == null ? null : p.top();
        String name = top != null ? top.clip.name : lastClip;
        if (name != null) play(name);
    }

    public static void togglePause() {
        enabled = true;
        paused = !paused;
    }

    public static void step(int frames) {
        enabled = true;
        paused = true;
        pendingFrames += frames;
    }

    public static void setSpeed(float s) {
        enabled = true;
        speed = s;
    }

    /** Targets the entity under the crosshair (or yourself when looking at nothing). */
    public static String retarget() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.hitResult instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity le) {
            target = le.getId();
            return le.getName().getString();
        }
        target = Integer.MIN_VALUE;
        return "yourself";
    }

    // ---------------------------------------------------------------- HUD

    public static void renderHud(GuiGraphicsExtractor g) {
        if (!enabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(targetId());
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        if (skeleton && e instanceof LivingEntity le) drawSkeleton(g, mc, le, partial);
        if (!hud) return;
        Font font = mc.font;
        int x = 38, y = 92, lh = 10;
        AnimPlayer p = ClientAnimations.player(targetId());
        List<AnimPlayer.Instance> list = p == null ? List.of() : p.ordered();
        int rows = 4 + Math.max(1, list.size()) * 6;
        g.fill(x - 3, y - 3, x + 250, y + rows * lh + 2, 0xB0101018);
        String who = e == null ? "?" : e.getName().getString();
        g.text(font, "ANIM DEBUG  " + who + (paused ? "  [PAUSED]" : "") + String.format(Locale.ROOT, "  debug x%.2f", speed), x, y, 0xFFFFD060, true);
        y += lh;
        g.text(font, "/jjkanim ui  pause  step [n]  back [n]  speed <x>  restart", x, y, 0xFF808890, false);
        y += lh + 2;
        if (list.isEmpty()) {
            g.text(font, "no clip playing (vanilla pose)", x, y, 0xFFB0B0B0, false);
            return;
        }
        for (AnimPlayer.Instance i : list) {
            Clip c = i.clip;
            float t = i.sampleTime();
            float frameMs = 1000f / c.fps;
            int frame = (int) Math.floor(t / frameMs + 1e-3), frames = Math.round(c.duration / frameMs);
            int key = c.keyIndexAt(t);
            float w = i.weight();
            String blend = i.stopping ? "fading out" : (c.blendIn > 0 && i.time < c.blendIn ? "blending in" : "full");
            Clip.Marker m = c.markerAt(t);
            g.text(font, c.group + "/" + c.name, x, y, i == p.top() ? 0xFF80E0FF : 0xFFC0C0C0, true);
            y += lh;
            g.text(font, String.format(Locale.ROOT, "time %4.0f / %.0f ms   frame %d / %d @ %.0f fps", t, c.duration, frame, frames, c.fps), x + 6, y, 0xFFFFFFFF, false);
            y += lh;
            g.text(font, String.format(Locale.ROOT, "speed x%.2f   key %d / %d (%.0f ms)%s", i.speed * (affects(targetId()) ? speed : 1),
                    key + 1, c.keyTimes.length, c.keyTimes.length == 0 ? 0 : c.keyTimes[key], m == null ? "" : "   phase: " + m.label()), x + 6, y, 0xFFFFFFFF, false);
            y += lh;
            g.text(font, String.format(Locale.ROOT, "blend %s  %.0f%%   in %.0f / out %.0f ms", blend, w * 100, c.blendIn, c.blendOut), x + 6, y, 0xFFFFFFFF, false);
            y += lh;
            String flags = (c.loop ? String.format(Locale.ROOT, "loop %.0f-%.0f  ", c.loopStart, c.loopEnd) : "") + (c.hold ? "hold  " : "")
                    + (c.interruptible ? (c.interruptTo < 0 && c.interruptFrom <= 0 ? "interruptible" : String.format(Locale.ROOT, "interrupt %.0f-%.0f", c.interruptFrom, c.interruptTo)) : "uninterruptible");
            g.text(font, "priority " + c.priority + "   layer " + c.layer, x + 6, y, 0xFFFFFFFF, false);
            y += lh;
            g.text(font, flags, x + 6, y, 0xFFA0A0A0, false);
            y += lh + 2;
        }
    }

    private static void drawSkeleton(GuiGraphicsExtractor g, Minecraft mc, LivingEntity le, float partial) {
        Vec3[] bones = HandPos.bones(le, partial);
        if (bones == null) return;
        int[][] s = new int[Bone.COUNT][];
        for (Bone b : Bone.ALL) s[b.ordinal()] = screen(mc, bones[b.ordinal()]);
        for (Bone b : Bone.ALL) {
            int[] a = s[b.ordinal()];
            if (a == null) continue;
            if (b.parent != null && s[b.parent.ordinal()] != null) line(g, s[b.parent.ordinal()], a, 0xFFFFFFFF);
        }
        if (axes) {
            for (Bone b : Bone.ALL) {
                int[] o = s[b.ordinal()];
                Vec3[] ax = HandPos.axes(le, partial, b, 0.12f);
                if (o == null || ax == null) continue;
                int[] colors = {0xFFFF4040, 0xFF40FF40, 0xFF4080FF};
                for (int k = 0; k < 3; k++) {
                    int[] tip = screen(mc, bones[b.ordinal()].add(ax[k]));
                    if (tip != null) line(g, o, tip, colors[k]);
                }
            }
        }
        for (Bone b : Bone.ALL) {
            int[] a = s[b.ordinal()];
            if (a == null) continue;
            g.fill(a[0] - 2, a[1] - 2, a[0] + 2, a[1] + 2, 0xFFFFD040);
            if (names) g.text(mc.font, b.id, a[0] + 3, a[1] - 3, 0xFFFFE8A0, true);
        }
    }

    /** GUI coordinates of a world point, or null behind the camera. */
    @Nullable
    private static int[] screen(Minecraft mc, Vec3 world) {
        Vec3 cam = mc.gameRenderer.mainCamera().position();
        Vec3 look = Vec3.directionFromRotation(mc.gameRenderer.mainCamera().xRot(), mc.gameRenderer.mainCamera().yRot());
        if (world.subtract(cam).dot(look) <= 0.05) return null;
        Vec3 ndc = mc.gameRenderer.projectPointToScreen(world);
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        return new int[]{(int) ((ndc.x + 1) / 2 * w), (int) ((1 - ndc.y) / 2 * h)};
    }

    private static void line(GuiGraphicsExtractor g, int[] a, int[] b, int color) {
        int dx = b[0] - a[0], dy = b[1] - a[1];
        int n = Math.max(Math.abs(dx), Math.abs(dy));
        if (n > 2000) return;
        for (int i = 0; i <= n; i++) {
            int x = a[0] + (n == 0 ? 0 : dx * i / n), y = a[1] + (n == 0 ? 0 : dy * i / n);
            g.fill(x, y, x + 1, y + 1, color);
        }
    }

    // ---------------------------------------------------------------- command

    public static void init() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> dispatcher.register(command()));
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (openUi && mc.gui.screen() == null) {
                openUi = false;
                mc.gui.setScreen(new AnimDebugScreen());
            }
        });
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> command() {
        return ClientCommands.literal("jjkanim")
                .then(ClientCommands.literal("play").then(ClientCommands.argument("clip", StringArgumentType.greedyString())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(AnimLibrary.names(), b))
                        .executes(c -> {
                            String name = StringArgumentType.getString(c, "clip");
                            if (AnimLibrary.get(name) == null) {
                                c.getSource().sendError(Component.literal("No clip named " + name));
                                return 0;
                            }
                            paused = false;
                            play(name);
                            return 1;
                        })))
                .then(ClientCommands.literal("stop").executes(c -> {
                    AnimPlayer p = ClientAnimations.player(targetId());
                    if (p != null) p.stopAll();
                    return 1;
                }))
                .then(ClientCommands.literal("pause").executes(c -> {
                    togglePause();
                    c.getSource().sendFeedback(Component.literal(paused ? "Paused" : "Playing"));
                    return 1;
                }))
                .then(ClientCommands.literal("restart").executes(c -> {
                    restart();
                    return 1;
                }))
                .then(ClientCommands.literal("step").executes(c -> {
                    step(1);
                    return 1;
                }).then(ClientCommands.argument("frames", IntegerArgumentType.integer(1, 1000)).executes(c -> {
                    step(IntegerArgumentType.getInteger(c, "frames"));
                    return 1;
                })))
                .then(ClientCommands.literal("back").executes(c -> {
                    step(-1);
                    return 1;
                }).then(ClientCommands.argument("frames", IntegerArgumentType.integer(1, 1000)).executes(c -> {
                    step(-IntegerArgumentType.getInteger(c, "frames"));
                    return 1;
                })))
                .then(ClientCommands.literal("speed").then(ClientCommands.argument("x", FloatArgumentType.floatArg(0.01f, 10f)).executes(c -> {
                    setSpeed(FloatArgumentType.getFloat(c, "x"));
                    return 1;
                })))
                .then(ClientCommands.literal("skeleton").executes(c -> {
                    enabled = true;
                    skeleton = !skeleton;
                    return 1;
                }))
                .then(ClientCommands.literal("axes").executes(c -> {
                    enabled = true;
                    skeleton = true;
                    axes = !axes;
                    return 1;
                }))
                .then(ClientCommands.literal("names").executes(c -> {
                    names = !names;
                    return 1;
                }))
                .then(ClientCommands.literal("hud").executes(c -> {
                    enabled = true;
                    hud = !hud;
                    return 1;
                }))
                .then(ClientCommands.literal("target").executes(c -> {
                    enabled = true;
                    c.getSource().sendFeedback(Component.literal("Debugging " + retarget()));
                    return 1;
                }))
                .then(ClientCommands.literal("off").executes(c -> {
                    enabled = false;
                    paused = false;
                    speed = 1;
                    return 1;
                }))
                .then(ClientCommands.literal("list").executes(c -> {
                    c.getSource().sendFeedback(Component.literal(String.join(", ", AnimLibrary.names())));
                    return 1;
                }))
                .then(ClientCommands.literal("reload").executes(c -> {
                    List<String> errors = AnimLibrary.reload(c.getSource().getClient().getResourceManager());
                    c.getSource().sendFeedback(Component.literal("Reloaded " + AnimLibrary.names().size() + " clips, " + errors.size() + " errors"));
                    for (String err : errors) c.getSource().sendError(Component.literal(err));
                    return 1;
                }))
                .then(ClientCommands.literal("ui").executes(c -> {
                    enabled = true;
                    // Opened on the next tick, once the chat screen has closed.
                    openUi = true;
                    return 1;
                }))
                .executes(c -> {
                    enabled = !enabled;
                    c.getSource().sendFeedback(Component.literal("Animation debugger " + (enabled ? "on" : "off")));
                    return 1;
                });
    }
}
