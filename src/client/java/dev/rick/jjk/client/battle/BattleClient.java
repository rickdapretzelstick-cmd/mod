package dev.rick.jjk.client.battle;

import com.mojang.blaze3d.platform.InputConstants;
import dev.rick.jjk.JJK;
import dev.rick.jjk.battle.BattleKit;
import dev.rick.jjk.battle.BattleKits;
import dev.rick.jjk.battle.BattleMove;
import dev.rick.jjk.battle.BattleMove.Range;
import dev.rick.jjk.battle.BattleMove.Stance;
import dev.rick.jjk.battle.BattleState;
import dev.rick.jjk.battle.Beat;
import dev.rick.jjk.battle.Fighter;
import dev.rick.jjk.battle.game.BattlePayloads;
import dev.rick.jjk.client.camera.CameraDirector;
import dev.rick.jjk.client.camera.Frame;
import dev.rick.jjk.client.camera.Shots;
import dev.rick.jjk.client.cinematic.CinematicPanels;
import dev.rick.jjk.client.hud.CharacterTheme;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The client half of a turn-based battle: what the server last said, the battle HUD (both fighters' bars, the range,
 * title cards, damage numbers, letterbox), the battle camera, and opening the menu when it's time to choose.
 */
public final class BattleClient {
    // GLFW key codes.
    private static final int K_B = 66;
    @Nullable static BattlePayloads.State state;
    @Nullable static BattlePayloads.BeatStart beat;
    static int ticks, beatAt, endAt;
    /** The player closed the menu with Esc this turn (B brings it back). */
    static boolean hidden;
    /** HP as the fight has shown it so far (the state jumps ahead to the end of the turn as soon as it resolves). */
    static final int[] shownHp = {Fighter.MAX_HP, Fighter.MAX_HP};
    static final float[] drainHp = {Fighter.MAX_HP, Fighter.MAX_HP};
    static int lastTurn = -1;
    /** The control sequence playing: who has control, when it started, and which side of the line the camera is on. */
    static int seqActor = -1, seqStart, seqSide = 1;
    /** A move the menu is hovering (the camera and your own fighter preview it), or null. */
    @Nullable static String hovering;

    /** The move playing, written small in the bottom letterbox bar (no shouting). */
    static String captionText = "";
    static int captionColor = 0xFFFFFFFF, captionAt;

    private static void caption(String text, int color) {
        captionText = text;
        captionColor = color;
        captionAt = ticks;
    }

    private record Popup(String text, int color, float scale, int born, int life, float x, float y, float rise) {}

    private static final List<Popup> POPUPS = new ArrayList<>();

    private BattleClient() {}

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(BattlePayloads.State.TYPE, (p, ctx) -> onState(p));
        ClientPlayNetworking.registerGlobalReceiver(BattlePayloads.BeatStart.TYPE, (p, ctx) -> onBeat(p));
        ClientTickEvents.END_CLIENT_TICK.register(BattleClient::tick);
        HudElementRegistry.addLast(JJK.id("battle_hud"), (g, delta) -> hud(g));
        CameraDirector.register("battle", 470, new CameraDirector.Stage() {
            @Override
            public double @Nullable [] apply(Vec3 pos, float yaw, float pitch) {
                return camera();
            }

            @Override
            public boolean last() {
                return true;
            }
        });
        CameraDirector.wantsThirdPerson(BattleClient::active);
    }

    public static boolean active() {
        return state != null && state.phase() != BattlePayloads.CLOSED;
    }

    @Nullable
    public static BattlePayloads.State state() {
        return state;
    }

    /** The current battle as an engine state, for the menu's rules checks (identical rules on both sides). */
    @Nullable
    static BattleState engineState() {
        BattlePayloads.State s = state;
        if (s == null) return null;
        BattleKit k0 = BattleKits.get(s.char0()), k1 = BattleKits.get(s.char1());
        if (k0 == null || k1 == null) return null;
        BattleState b = new BattleState(k0, k1, 0);
        b.range = Range.values()[Mth.clamp(s.range(), 0, 2)];
        b.turn = s.turn();
        for (int i = 0; i < 2; i++) {
            Fighter f = b.f[i];
            f.hp = s.stat(i, 0); f.ce = s.stat(i, 1); f.meter = s.stat(i, 2); f.control = s.stat(i, 3);
            f.stance = Stance.values()[Mth.clamp(s.stat(i, 4), 0, Stance.values().length - 1)];
            f.slowTurns = s.stat(i, 5); f.burnTurns = s.stat(i, 6); f.zoneTurns = s.stat(i, 7); f.domainTurns = s.stat(i, 8);
            if (i < 2 && f.domainTurns > 0 && k(i).character.equals("yuji")) f.strikeBoostTurns = f.domainTurns;
        }
        return b;
    }

    private static BattleKit k(int side) {
        BattlePayloads.State s = state;
        BattleKit k = s == null ? null : BattleKits.get(side == 0 ? s.char0() : s.char1());
        return k == null ? BattleKits.YUJI : k;
    }

    static int mySide() {
        return state == null ? 0 : Math.max(0, state.side());
    }

    // ------------------------------------------------------------------------------------------------ network

    private static void onState(BattlePayloads.State p) {
        Minecraft mc = Minecraft.getInstance();
        if (p.phase() == BattlePayloads.CLOSED) {
            state = null;
            beat = null;
            POPUPS.clear();
            if (mc.gui.screen() instanceof BattleScreen) mc.gui.setScreen(null);
            return;
        }
        boolean fresh = state == null || state.session() != p.session();
        state = p;
        if (fresh) {
            for (int i = 0; i < 2; i++) shownHp[i] = p.stat(i, 0);
            drainHp[0] = shownHp[0];
            drainHp[1] = shownHp[1];
            popup(k(0).name.toUpperCase() + "  ·  " + k(1).name.toUpperCase(), 0xFFFFFFFF, 1.6f, 50, 0.5f, 0.42f, 0);
        }
        if (p.phase() == BattlePayloads.CHOOSING) {
            beat = null;
            for (int i = 0; i < 2; i++) shownHp[i] = p.stat(i, 0);
            if (p.turn() != lastTurn) {
                hidden = false;
                lastTurn = p.turn();
            }
        } else if (p.phase() == BattlePayloads.DONE) {
            endAt = ticks;
            if (mc.gui.screen() instanceof BattleScreen) mc.gui.setScreen(null);
            int w = p.winner();
            String text = w == 2 ? "DRAW" : p.side() < 0 ? k(w).name.toUpperCase() + " WINS" : w == p.side() ? "VICTORY" : "DEFEAT";
            popup(text, w == p.side() ? 0xFFFFD040 : 0xFFE04040, 3.2f, 80, 0.5f, 0.40f, 0);
        }
        if (p.phase() != BattlePayloads.CHOOSING && mc.gui.screen() instanceof BattleScreen bs) bs.lockedOrDone();
    }

    private static void onBeat(BattlePayloads.BeatStart b) {
        beat = b;
        beatAt = ticks;
        Beat.Kind kind = Beat.Kind.values()[Mth.clamp(b.kind(), 0, Beat.Kind.values().length - 1)];
        // A fighter taking control turns everything they do into one take (the camera follows from here).
        if (b.control() && seqActor != b.actor()) {
            seqActor = b.actor();
            seqStart = ticks;
            seqSide = b.actor() == mySide() ? 1 : -1;
        } else if (!b.control()) {
            seqActor = -1;
        }
        if (kind == Beat.Kind.REVERSAL) seqSide = -seqSide;
        int accent = CharacterTheme.of(k(b.actor()).character).accent();
        BattleMove m = kind == Beat.Kind.FINISHER ? k(b.actor()).finisher : k(b.actor()).move(b.move());
        // The move's name, small in the letterbox: the picture does the talking.
        if (m != null && (m.category.attack() || kind == Beat.Kind.FINISHER)) caption(m.name, accent);
        if (kind == Beat.Kind.DOMAIN || kind == Beat.Kind.DOMAIN_CLASH) popup(k(b.actor()).domain.name.toUpperCase(), accent, 2.4f, 50, 0.5f, 0.36f, 0);
    }

    /** At the moment of contact: a small damage number, and a title card only for the big moments. */
    private static void onContact(BattlePayloads.BeatStart b) {
        Beat.Kind kind = Beat.Kind.values()[Mth.clamp(b.kind(), 0, Beat.Kind.values().length - 1)];
        shownHp[0] = b.hp0();
        shownHp[1] = b.hp1();
        int target = kind == Beat.Kind.PARRY ? b.actor() : 1 - b.actor();
        float x = target == mySide() ? 0.36f : 0.64f;
        if (b.damage() > 0 && kind != Beat.Kind.KO) {
            int color = b.blackFlash() ? 0xFFFF3040 : kind == Beat.Kind.BLOCK ? 0xFF8090A8 : kind == Beat.Kind.PARRY ? 0xFFFFE070 : 0xFFFFFFFF;
            popup(String.valueOf(b.damage()), color, b.blackFlash() ? 1.5f : 1.0f, 20, x, 0.46f, 0.04f);
        }
        if (b.blackFlash()) popup("BLACK FLASH", 0xFFFF2030, 2.6f, 36, 0.5f, 0.33f, 0);
        else if (!b.note().isEmpty() && kind != Beat.Kind.DOMAIN) popup(b.note().toUpperCase(), 0xFFFFD040, 2.2f, 40, 0.5f, 0.30f, 0);
    }

    private static void popup(String text, int color, float scale, int life, float x, float y, float rise) {
        // One big title card at a time, and never the same words twice on screen.
        POPUPS.removeIf(p -> p.text.equals(text) || scale >= 2f && p.scale >= 2f && Math.abs(p.y - y) < 0.14f);
        POPUPS.add(new Popup(text, color, scale, ticks, life, x, y, rise));
    }

    // ------------------------------------------------------------------------------------------------ tick

    private static void tick(Minecraft mc) {
        ticks++;
        for (int i = 0; i < 2; i++) drainHp[i] += (shownHp[i] - drainHp[i]) * (drainHp[i] > shownHp[i] ? 0.08f : 1f);
        BattlePayloads.BeatStart b = beat;
        if (b != null && ticks - beatAt == b.contact()) onContact(b);
        BattlePayloads.State s = state;
        if (s == null || mc.player == null) return;
        if (s.side() >= 0 && s.phase() == BattlePayloads.CHOOSING && !s.picked() && mc.gui.screen() == null) {
            if (hidden && InputConstants.isKeyDown(K_B)) hidden = false;
            if (!hidden) mc.gui.setScreen(new BattleScreen());
        }
    }

    // ------------------------------------------------------------------------------------------------ camera

    @Nullable private static Vec3 camPos, camLook;
    private static int lastShot = -1;

    @Nullable
    private static Entity body(int side) {
        Minecraft mc = Minecraft.getInstance();
        BattlePayloads.State s = state;
        if (mc.level == null || s == null) return null;
        return mc.level.getEntity(side == 0 ? s.entity0() : s.entity1());
    }

    private static double @Nullable [] camera() {
        if (!active()) {
            camPos = null;
            return null;
        }
        Entity e0 = body(0), e1 = body(1);
        if (e0 == null || e1 == null) return null;
        int me = mySide();
        Entity mine = me == 0 ? e0 : e1, theirs = me == 0 ? e1 : e0;
        Vec3 p0 = e0.position(), p1 = e1.position();
        // Stay on one side of the line between them (the 180-degree rule): the player's side reads left to right.
        int side = me == 0 ? 1 : -1;
        float t = ticks + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Frame f;
        int shot;
        BattlePayloads.State s = state;
        BattlePayloads.BeatStart b = beat;
        if (s.phase() == BattlePayloads.DONE) {
            Entity w = s.winner() == 1 ? e1 : e0;
            shot = 90;
            f = Shots.orbit(w.position().add(0, 1.1, 0), 4.2, 0.6, (t - endAt) * 0.025);
        } else if (s.phase() == BattlePayloads.PLAYING && b != null) {
            Beat.Kind kind = Beat.Kind.values()[Mth.clamp(b.kind(), 0, Beat.Kind.values().length - 1)];
            Entity att = b.actor() == 0 ? e0 : e1, tgt = b.actor() == 0 ? e1 : e0;
            Vec3 pa = att.position().add(0, 1.2, 0), pt = tgt.position().add(0, 1.2, 0);
            float bt = t - beatAt;
            BattleMove m = k(b.actor()).move(b.move());
            boolean big = b.blackFlash() || !b.note().isEmpty() || (m != null && m.weight == BattleMove.Weight.HEAVY && m.category.attack());
            if (b.control() && kind != Beat.Kind.KO) {
                // One take for the whole sequence: orbit with the action (on the controller's side of the line), rise
                // with juggles, push in on every contact; one hard cut to a low hero shot for the finisher's impact.
                float st = t - seqStart, local = bt - b.contact();
                Vec3 mid = pa.lerp(pt, 0.5);
                if (kind == Beat.Kind.FINISHER && local > 0) {
                    shot = 5000 + b.index();
                    // Low and side-on to the impact, drifting back as the target crashes.
                    Frame two = Shots.twoShot(att.position().add(0, 0.6, 0), tgt.position().add(0, 0.4, 0), side * seqSide, -0.4 + local * 0.04, -0.2);
                    f = two;
                } else {
                    shot = 4000 + seqStart + (seqSide > 0 ? 0 : 1);
                    double push = Math.max(0, 1 - Math.abs(local) / 5.0) * 0.9;
                    double radius = Math.max(3.0, att.position().distanceTo(tgt.position()) * 0.6 + 3.2) - push;
                    Vec3 n = Shots.perpendicular(Shots.flatDir(p0, p1), side * seqSide);
                    Vec3 dir = Shots.rotate(n, (st * 0.03) * seqSide - 0.35 * seqSide);
                    double rise = Math.max(0, tgt.position().y - att.position().y) * 0.6;
                    f = Shots.place(mid.add(0, rise * 0.5, 0), mid.add(dir.scale(radius)).add(0, 0.6 + rise + st * 0.01, 0));
                }
            } else switch (kind) {
                case HIT, PIERCE, TRADE, CLASH, BLOCK, PARRY, WHIFF -> {
                    if (bt < b.contact() * 0.6f && b.link() == 0) {
                        shot = b.index() * 10;
                        f = Shots.overShoulder(att.position().add(0, 1.5, 0), pt, side * (b.actor() == me ? 1 : -1), 3.6);
                    } else if (bt < b.contact() + 3) {
                        shot = b.index() * 10 + 1;
                        Frame two = Shots.twoShot(p0.add(0, 1.0, 0), p1.add(0, 1.0, 0), side, 0.6, 0.5);
                        // A slow push-in toward the hit.
                        f = two.lerp(new Frame(two.look().lerp(two.pos(), 0.75), two.look()), Shots.ease(bt / Math.max(1, b.contact() + 3)) * 0.35);
                    } else if (big) {
                        shot = b.index() * 10 + 2;
                        f = Shots.lowHero(tgt.position(), att.position(), 3.2, 1.8);
                    } else {
                        shot = b.index() * 10 + 3;
                        f = Shots.twoShot(p0.add(0, 1.0, 0), p1.add(0, 1.0, 0), side, 1.8, 1.0);
                    }
                }
                case DOMAIN, DOMAIN_CLASH -> {
                    shot = b.index() * 10 + 4;
                    f = Shots.orbit(pa, 3.5 + bt * 0.06, 0.4 + bt * 0.03, bt * 0.035 + (side > 0 ? 0 : Math.PI));
                }
                case KO -> {
                    shot = b.index() * 10 + 5;
                    f = Shots.orbit(pt, 5, 1.0, bt * 0.02);
                }
                case BURN, STUNNED -> {
                    shot = b.index() * 10 + 6;
                    f = Shots.lowHero(tgt.position(), att.position(), 3.5, 1.8);
                }
                default -> {
                    shot = b.index() * 10 + 7;
                    f = Shots.twoShot(p0.add(0, 1.0, 0), p1.add(0, 1.0, 0), side, 2.2, 1.0);
                }
            }
        } else {
            // Choosing: a slow drift round the two of them, or over your shoulder when the menu previews a move.
            if (hovering != null) {
                shot = -2;
                f = Shots.overShoulder(mine.position().add(0, 1.5, 0), theirs.position().add(0, 1.2, 0), side, 2.8);
            } else {
                shot = -3;
                Vec3 mid = p0.lerp(p1, 0.5).add(0, 1.0, 0);
                Vec3 n = Shots.perpendicular(Shots.flatDir(p0, p1), side);
                double a = Math.sin(t * 0.012) * 0.45;
                double r = Math.max(4.5, p0.distanceTo(p1) * 0.85 + 3.5);
                Vec3 dir = Shots.rotate(n, a);
                f = Shots.place(mid, mid.add(dir.scale(r)).add(0, 1.4 + Math.sin(t * 0.02) * 0.3, 0));
            }
        }
        boolean cut = shot != lastShot && (s.phase() == BattlePayloads.PLAYING || camPos == null);
        lastShot = shot;
        if (camPos == null || cut || CameraDirector.reducedMotion()) {
            camPos = f.pos();
            camLook = f.look();
        } else {
            camPos = camPos.lerp(f.pos(), 0.12);
            camLook = camLook.lerp(f.look(), 0.2);
        }
        return new Frame(camPos, camLook).array();
    }

    // ------------------------------------------------------------------------------------------------ HUD

    private static void hud(GuiGraphicsExtractor g) {
        BattlePayloads.State s = state;
        if (s == null || s.phase() == BattlePayloads.CLOSED) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        boolean playing = s.phase() != BattlePayloads.CHOOSING;
        BattlePayloads.BeatStart cb = beat;
        // The bars close in while someone has control of the fight.
        int bar = !playing ? 0 : cb != null && cb.control() && s.phase() == BattlePayloads.PLAYING ? Math.max(18, h / 9) : Math.max(12, h / 14);
        if (playing) {
            g.fill(0, 0, w, bar, 0xF0000000);
            g.fill(0, h - bar, w, h, 0xF0000000);
            float age = ticks - captionAt;
            if (!captionText.isEmpty() && age < 40) {
                float alpha = Mth.clamp(age / 4f, 0, 1) * Mth.clamp((40 - age) / 8f, 0, 1);
                CinematicPanels.labelCentered(g, font, captionText, w / 2f, h - bar / 2f - 3, 0.75f, captionColor, alpha);
            }
        }
        int me = mySide();
        panel(g, font, me, 6, 3, false, w);
        panel(g, font, 1 - me, w - 6, 3, true, w);
        if (!playing) rangeMeter(g, font, s, w / 2f, 3);
        if (s.phase() == BattlePayloads.CHOOSING && s.side() >= 0 && !(mc.gui.screen() instanceof BattleScreen)) {
            String msg = s.picked() ? "Locked in — waiting for the opponent…" : "B: battle menu";
            CinematicPanels.labelCentered(g, font, msg, w / 2f, h - 12, 0.6f, 0xFFFFFFFF, 0.6f + 0.4f * (float) Math.sin(ticks * 0.2));
        }
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        for (Iterator<Popup> it = POPUPS.iterator(); it.hasNext(); ) {
            Popup p = it.next();
            float age = ticks - p.born + partial;
            if (age > p.life) {
                it.remove();
                continue;
            }
            float in = Mth.clamp(age / 3f, 0, 1), out = Mth.clamp((p.life - age) / 5f, 0, 1);
            float pop = 1 + (1 - in) * 0.6f;
            CinematicPanels.labelCentered(g, font, p.text, w * p.x, h * (p.y - p.rise * age / p.life), p.scale * pop, p.color, Math.min(in, out));
        }
    }

    /** One fighter's bars, slim and in the corner: name and HP, a thin HP bar, cursed energy and the domain meter under it. */
    private static void panel(GuiGraphicsExtractor g, Font font, int side, int x, int y, boolean right, int screenW) {
        BattlePayloads.State s = state;
        BattleKit kit = k(side);
        CharacterTheme theme = CharacterTheme.of(kit.character);
        int pw = Math.max(90, Math.round(screenW * 0.26f));
        int x0 = right ? x - pw : x;
        int hp = shownHp[side];
        CinematicPanels.label(g, font, kit.name.toUpperCase(), right ? x0 + pw : x0, y, 0.65f, theme.accent(), 1f, right);
        CinematicPanels.label(g, font, String.valueOf(hp), right ? x0 : x0 + pw, y, 0.65f, 0xFFFFFFFF, 1f, !right);
        bar(g, x0, y + 8, pw, 3, drainHp[side] / Fighter.MAX_HP, 0xFFFFFFFF, hp / (float) Fighter.MAX_HP,
                hp > 50 ? 0xFF50E070 : hp > 20 ? 0xFFF0C030 : 0xFFE04040, right);
        bar(g, x0, y + 12, pw, 1, 0, 0, s.stat(side, 1) / (float) Fighter.MAX_CE, theme.ceBottom(), right);
        boolean full = s.stat(side, 2) >= Fighter.MAX_METER;
        bar(g, x0, y + 14, pw / 2, 1, 0, 0, s.stat(side, 2) / (float) Fighter.MAX_METER,
                full ? (ticks / 4 % 2 == 0 ? 0xFFFFFFFF : 0xFFFFD040) : 0xFFB070FF, right);
        StringBuilder tags = new StringBuilder();
        // While a turn plays, the state already shows how it ends: keep stances and statuses until it has.
        boolean showTags = s.phase() != BattlePayloads.PLAYING;
        int control = s.stat(side, 3);
        if (control > 0) tags.append("CONTROL ").append("●".repeat(control)).append("○".repeat(Fighter.MAX_CONTROL - control)).append(' ');
        int stance = s.stat(side, 4);
        if (stance > 0) tags.append(Stance.values()[Math.min(stance, 3)].name()).append(' ');
        if (s.stat(side, 5) > 0) tags.append("SLOW ");
        if (s.stat(side, 6) > 0) tags.append("BURN ");
        if (s.stat(side, 7) > 0) tags.append("ZONE ");
        if (full) tags.append("DOMAIN READY");
        if (!tags.isEmpty() && showTags) CinematicPanels.label(g, font, tags.toString().trim(), right ? x0 + pw : x0, y + 17, 0.5f, 0xFFFFE070, 1f, right);
    }

    private static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, float back, int backColor, float k, int color, boolean right) {
        g.fill(x, y, x + w, y + h, 0xFF202028);
        int bw = Math.round(w * Mth.clamp(back, 0, 1)), fw = Math.round(w * Mth.clamp(k, 0, 1));
        if (bw > 0) g.fill(right ? x + w - bw : x, y, right ? x + w : x + bw, y + h, backColor);
        if (fw > 0) g.fill(right ? x + w - fw : x, y, right ? x + w : x + fw, y + h, color);
    }

    private static void rangeMeter(GuiGraphicsExtractor g, Font font, BattlePayloads.State s, float cx, int y) {
        String[] names = {"CLOSE", "MID", "FAR"};
        CinematicPanels.labelCentered(g, font, names[Mth.clamp(s.range(), 0, 2)], cx, y, 0.6f, 0xFFFFFFFF, 1f);
        for (int i = 0; i < 3; i++) g.fill(Math.round(cx) - 7 + i * 5, y + 8, Math.round(cx) - 4 + i * 5, y + 9, s.range() == i ? 0xFFFFFFFF : 0x60FFFFFF);
    }
}
