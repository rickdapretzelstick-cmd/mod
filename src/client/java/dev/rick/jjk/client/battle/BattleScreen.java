package dev.rick.jjk.client.battle;

import dev.rick.jjk.battle.BattleEngine;
import dev.rick.jjk.battle.BattleKit;
import dev.rick.jjk.battle.BattleKits;
import dev.rick.jjk.battle.BattleMove;
import dev.rick.jjk.battle.BattleMove.Category;
import dev.rick.jjk.battle.BattleMove.Range;
import dev.rick.jjk.battle.BattleState;
import dev.rick.jjk.battle.Fighter;
import dev.rick.jjk.battle.Plan;
import dev.rick.jjk.battle.game.BattlePayloads;
import dev.rick.jjk.client.anim.ClientAnimations;
import dev.rick.jjk.client.cinematic.CinematicPanels;
import dev.rick.jjk.client.hud.CharacterTheme;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The battle menu: one panel along the bottom of the screen, the fight still visible above it.
 *
 * <ul>
 *   <li>Four big move cards (keys 1-4): category, speed, damage, cost, one line, and why not when greyed out.</li>
 *   <li>Picking a card starts a combo strip; the cards then light up the moves that can follow (LINK). Backspace
 *       takes the last one off.</li>
 *   <li>One row of small buttons for the shared choices: guard (Q), Focus (F), Close In (W), Back Off (S), Domain (R).</li>
 *   <li>Boost (- / +) with a live initiative preview, the timer, and Lock in (Enter).</li>
 * </ul>
 * Hovering a move previews its wind-up on your own fighter (only on your screen) and moves the camera over your shoulder.
 */
public class BattleScreen extends Screen {
    // GLFW key codes.
    private static final int K_ESCAPE = 256;
    private static final int K_1 = 49;
    private static final int K_4 = 52;
    private static final int K_Q = 81;
    private static final int K_F = 70;
    private static final int K_W = 87;
    private static final int K_S = 83;
    private static final int K_R = 82;
    private static final int K_MINUS = 45;
    private static final int K_KP_SUBTRACT = 333;
    private static final int K_EQUAL = 61;
    private static final int K_KP_ADD = 334;
    private static final int K_BACKSPACE = 259;
    private static final int K_ENTER = 257;
    private static final int K_KP_ENTER = 335;
    private static final int K_SPACE = 32;
    private static final int K_B = 66;
    private final List<String> chain = new ArrayList<>();
    private int boost;
    private boolean sent;
    @Nullable private String hover, previewing;
    private final List<Hit> hits = new ArrayList<>();
    private long openedAt;

    private record Hit(int x0, int y0, int x1, int y1, Runnable click, @Nullable String move) {}

    /** Your own fighter's wind-up for each move, played locally while the card is hovered. */
    private static final Map<String, String> PREVIEW = Map.ofEntries(
            Map.entry("divergent_fist", "divergent_windup"), Map.entry("manji_kick", "manji_stance"),
            Map.entry("cursed_strikes", "cursed_strikes_ready"), Map.entry("crushing_blow", "crushing_blow_charge"),
            Map.entry("blue", "blue_cast"), Map.entry("twofold_kick", "twofold_1"), Map.entry("red", "red_charge"),
            Map.entry("hollow_purple", "purple_blue"), Map.entry("focus", "heavy_charge"));

    public BattleScreen() {
        super(Component.literal("Battle"));
    }

    @Override
    protected void init() {
        openedAt = System.currentTimeMillis();
    }

    /** The server moved on (both locked in, or the battle ended). */
    void lockedOrDone() {
        sent = true;
        onClose();
    }

    @Nullable
    private BattleState st() {
        return BattleClient.engineState();
    }

    private int side() {
        return BattleClient.mySide();
    }

    private BattleKit kit(BattleState s) {
        return s.f[side()].kit;
    }

    // ------------------------------------------------------------------------------------------------ choosing

    private void pickCard(BattleState s, BattleMove m) {
        if (!chain.isEmpty() && canLink(s, m)) {
            chain.add(m.id);
            return;
        }
        if (BattleEngine.whyNot(s, side(), m) == null) {
            chain.clear();
            chain.add(m.id);
            if (BattleEngine.validate(s, side(), plan()) != null) boost = 0;
        }
    }

    private void pickShared(BattleState s, BattleMove m) {
        if (BattleEngine.whyNot(s, side(), m) != null) return;
        chain.clear();
        chain.add(m.id);
        boost = 0;
    }

    /** Whether {@code m} can go on the end of the combo strip right now. */
    private boolean canLink(BattleState s, BattleMove m) {
        if (chain.isEmpty() || chain.size() >= BattleEngine.MAX_CHAIN) return false;
        List<String> next = new ArrayList<>(chain);
        next.add(m.id);
        return BattleEngine.validate(s, side(), new Plan(next, boost)) == null;
    }

    private Plan plan() {
        return new Plan(chain, boost);
    }

    private void lockIn() {
        BattleState s = st();
        BattlePayloads.State p = BattleClient.state();
        if (s == null || p == null || sent) return;
        if (chain.isEmpty()) chain.add(kit(s).guard.id);
        if (BattleEngine.validate(s, side(), plan()) != null) return;
        ClientPlayNetworking.send(new BattlePayloads.Choice(p.session(), List.copyOf(chain), boost, false));
        sent = true;
        onClose();
    }

    private void forfeit() {
        BattlePayloads.State p = BattleClient.state();
        if (p != null) ClientPlayNetworking.send(new BattlePayloads.Choice(p.session(), List.of(), 0, true));
        sent = true;
        onClose();
    }

    private void setBoost(BattleState s, int b) {
        int old = boost;
        boost = Mth.clamp(b, 0, BattleEngine.MAX_BOOST);
        if (!chain.isEmpty() && BattleEngine.validate(s, side(), plan()) != null) boost = old;
        if (chain.isEmpty() && boost * BattleEngine.BOOST_COST > s.f[side()].ce) boost = old;
    }

    /** The initiative this plan's opener would come out at, with the reasons. */
    private String initiativeText(BattleState s) {
        if (chain.isEmpty()) return "";
        BattleMove m = kit(s).move(chain.getFirst());
        if (m == null || !m.category.attack() && m.category != Category.MOVE) {
            return m == null ? "" : switch (m.category) {
                case GUARD -> "Instant";
                case FOCUS -> "Goes last";
                case DOMAIN -> "Opens first";
                default -> "";
            };
        }
        Fighter f = s.f[side()];
        int speed = m.category == Category.MOVE ? 3 : m.weight.speed;
        StringBuilder b = new StringBuilder("Speed " + speed);
        int total = speed;
        if (f.momentum > 0) { b.append(" +").append(f.momentum).append(" momentum"); total += f.momentum; }
        if (boost > 0) { b.append(" +").append(boost).append(" boost"); total += boost; }
        if (f.slowTurns > 0) { b.append(" -2 slowed"); total -= 2; }
        if (f.stance == BattleMove.Stance.DOWN) { b.append(" -1 down"); total -= 1; }
        if (f.strikeBoostTurns > 0 && m.category == Category.STRIKE) { b.append(" +1 shrine"); total += 1; }
        return b.append(" = ").append(total).toString();
    }

    // ------------------------------------------------------------------------------------------------ input

    @Override
    public boolean keyPressed(KeyEvent event) {
        BattleState s = st();
        int key = event.key();
        if (key == K_ESCAPE) {
            BattleClient.hidden = true;
            onClose();
            return true;
        }
        if (s == null) return super.keyPressed(event);
        BattleKit k = kit(s);
        if (key >= K_1 && key <= K_4 && key - K_1 < k.moves.size()) {
            pickCard(s, k.moves.get(key - K_1));
            return true;
        }
        switch (key) {
            case K_Q -> pickShared(s, k.guard);
            case K_F -> pickShared(s, BattleKits.FOCUS);
            case K_W -> pickShared(s, BattleKits.CLOSE_IN);
            case K_S -> pickShared(s, BattleKits.BACK_OFF);
            case K_R -> pickShared(s, k.domain);
            case K_MINUS, K_KP_SUBTRACT -> setBoost(s, boost - 1);
            case K_EQUAL, K_KP_ADD -> setBoost(s, boost + 1);
            case K_BACKSPACE -> { if (!chain.isEmpty()) chain.removeLast(); }
            case K_ENTER, K_KP_ENTER, K_SPACE -> lockIn();
            default -> { return super.keyPressed(event); }
        }
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        for (Hit h : hits) {
            if (event.x() >= h.x0 && event.x() < h.x1 && event.y() >= h.y0 && event.y() < h.y1) {
                h.click.run();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        // No blur or dim: the fight stays in view.
    }

    @Override
    public void onClose() {
        setPreview(null);
        BattleClient.hovering = null;
        super.onClose();
    }

    // ------------------------------------------------------------------------------------------------ drawing

    /** One clickable chip of the bar. */
    private record Chip(String label, @Nullable BattleMove move, Runnable click, boolean enabled, boolean lit, int accent, int dots, String tip) {}

    private static final float TS = 0.7f;
    private static final int BAR_H = 16;

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        hits.clear();
        BattleState s = st();
        BattlePayloads.State p = BattleClient.state();
        if (s == null || p == null) return;
        BattleKit k = kit(s);
        CharacterTheme theme = CharacterTheme.of(k.character);
        float in = Mth.clamp((System.currentTimeMillis() - openedAt) / 160f, 0, 1);
        int y = height - BAR_H - 4 + Math.round((1 - in) * 20);

        // The chips: four moves, the shared keys, boost, timer, lock in.
        List<Chip> chips = new ArrayList<>();
        for (int i = 0; i < k.moves.size(); i++) {
            BattleMove m = k.moves.get(i);
            String why = BattleEngine.whyNot(s, side(), m);
            boolean link = canLink(s, m);
            int accent = m.category == Category.STRIKE ? 0xFFFF9050 : 0xFF70B0FF;
            String tip = m.name + " · " + (m.category == Category.STRIKE ? "Strike" : "Technique") + " · " + m.power + " dmg · " + m.cost + " CE"
                    + (link ? " · LINK" : why != null ? " · " + why : "") + " — " + m.line;
            chips.add(new Chip((i + 1) + " " + m.name, m, () -> pickCard(s, m), why == null || link, link || chain.contains(m.id), accent, m.weight.speed, tip));
        }
        record Key(String key, String shortName, BattleMove m) {}
        for (Key key : List.of(new Key("Q", "Guard", k.guard), new Key("F", "Focus", BattleKits.FOCUS), new Key("W", "In", BattleKits.CLOSE_IN),
                new Key("S", "Back", BattleKits.BACK_OFF), new Key("R", "Domain", k.domain))) {
            String why = BattleEngine.whyNot(s, side(), key.m);
            boolean picked = chain.size() == 1 && chain.getFirst().equals(key.m.id);
            boolean domainReady = key.m.category == Category.DOMAIN && why == null;
            if (key.m.category == Category.DOMAIN && !domainReady) continue; // only shows when the meter is full
            chips.add(new Chip(key.key + " " + key.shortName, key.m, () -> pickShared(s, key.m), why == null, picked || domainReady,
                    domainReady ? theme.accent() : 0xFF9090A0, 0, key.m.name + (why != null ? " · " + why : "") + " — " + key.m.line));
        }

        // Layout: measure, then centre the row.
        int gap = 3, pad = 5;
        int[] w = new int[chips.size()];
        int total = 0;
        for (int i = 0; i < chips.size(); i++) total += (w[i] = Math.round(font.width(chips.get(i).label) * TS) + pad * 2) + gap;
        int tail = 96; // boost, timer, lock in
        if (total + tail > width - 8) {
            // Narrow screen: first words only, single letters for the shared keys.
            total = 0;
            for (int i = 0; i < chips.size(); i++) {
                Chip c = chips.get(i);
                String l = c.label;
                if (c.move != null && k.moves.contains(c.move)) l = l.split(" ")[0] + " " + c.move.name.replace("Lapse: ", "").replace("Reversal: ", "").split(" ")[0];
                else l = l.split(" ")[0];
                chips.set(i, new Chip(l, c.move, c.click, c.enabled, c.lit, c.accent, c.dots, c.tip));
                total += (w[i] = Math.round(font.width(l) * TS) + pad * 2) + gap;
            }
        }
        int x = Math.max(4, width / 2 - (total + tail) / 2);
        String tip = null;
        for (int i = 0; i < chips.size(); i++) {
            Chip c = chips.get(i);
            boolean over = mouseX >= x && mouseX < x + w[i] && mouseY >= y && mouseY < y + BAR_H;
            if (over) {
                tip = c.tip;
                BattleClient.hovering = c.move == null ? null : c.move.id;
            }
            int bg = c.lit ? (theme.ceBottom() & 0xFFFFFF) | 0xD0000000 : over ? 0xD0303040 : 0xB0101018;
            g.fill(x, y, x + w[i], y + BAR_H, bg);
            g.fill(x, y + BAR_H - 1, x + w[i], y + BAR_H, c.enabled ? c.accent : 0x40FFFFFF);
            if (c.lit && c.move != null && canLink(s, c.move)) g.fill(x, y, x + w[i], y + 1, System.currentTimeMillis() / 300 % 2 == 0 ? 0xFFFFD040 : 0xFFFFFFFF);
            text(g, c.label, x + pad, y + 5, TS, c.enabled ? 0xFFFFFFFF : 0xFF5A5A66);
            for (int d = 0; d < c.dots; d++) g.fill(x + w[i] - 3 - d * 3, y + 2, x + w[i] - 2 - d * 3, y + 3, c.enabled ? 0xFFFFE070 : 0xFF505058);
            hits.add(new Hit(x, y, x + w[i], y + BAR_H, c.click, c.move == null ? null : c.move.id));
            x += w[i] + gap;
        }
        if (tip == null) BattleClient.hovering = null;

        // Boost (◆ per level), the timer, lock in.
        x += 4;
        int bx = x;
        g.fill(bx, y, bx + 30, y + BAR_H, 0xB0101018);
        StringBuilder flames = new StringBuilder();
        for (int i = 0; i < BattleEngine.MAX_BOOST; i++) flames.append(i < boost ? "◆" : "◇");
        text(g, flames.toString(), bx + 4, y + 5, TS, boost > 0 ? 0xFFFF8030 : 0xFF606070);
        hits.add(new Hit(bx, y, bx + 30, y + BAR_H, () -> setBoost(s, boost >= BattleEngine.MAX_BOOST ? 0 : boost + 1), null));
        boolean overBoost = mouseX >= bx && mouseX < bx + 30 && mouseY >= y && mouseY < y + BAR_H;
        if (overBoost) tip = "Boost: +1 speed per level, " + BattleEngine.BOOST_COST + " CE each (click or -/+)";
        x = bx + 33;
        int secs = (p.timer() + 19) / 20;
        boolean ok = chain.isEmpty() || BattleEngine.validate(s, side(), plan()) == null;
        int lw = 58;
        boolean overLock = mouseX >= x && mouseX < x + lw && mouseY >= y && mouseY < y + BAR_H;
        g.fill(x, y, x + lw, y + BAR_H, overLock ? (theme.ceBottom() & 0xFFFFFF) | 0xE0000000 : 0xC0181820);
        g.fill(x, y + BAR_H - 1, x + lw, y + BAR_H, ok ? theme.accent() : 0x40FFFFFF);
        text(g, (ok ? "GO ⏎ " : "— ") + secs + "s", x + 6, y + 5, TS, secs <= 5 ? 0xFFFF6060 : 0xFFFFFFFF);
        hits.add(new Hit(x, y, x + lw, y + BAR_H, this::lockIn, null));

        // One line above: the combo and its speed, or what's under the mouse.
        String line;
        if (tip != null) line = tip;
        else if (chain.isEmpty()) line = "";
        else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < chain.size(); i++) {
                BattleMove m = k.move(chain.get(i));
                if (i > 0) sb.append(" → ");
                sb.append(m == null ? chain.get(i) : m.name);
            }
            boolean more = false;
            if (chain.size() < BattleEngine.MAX_CHAIN) for (BattleMove m : k.moves) more |= canLink(s, m);
            if (more) sb.append(" → +");
            String ini = initiativeText(s);
            line = sb + (ini.isEmpty() ? "" : "   ·   " + ini);
        }
        if (!line.isEmpty()) {
            int lw2 = Math.min(width - 8, Math.round(font.width(line) * 0.62f) + 10);
            g.fill(width / 2 - lw2 / 2, y - 12, width / 2 + lw2 / 2, y - 2, 0x90000000);
            var lines = font.split(Component.literal(line), Math.round((width - 18) / 0.62f));
            if (!lines.isEmpty()) {
                var pose = g.pose();
                pose.pushMatrix();
                pose.translate(width / 2f - Math.min(width - 18, font.width(lines.getFirst()) * 0.62f) / 2, y - 10);
                pose.scale(0.62f, 0.62f);
                g.text(font, lines.getFirst(), 0, 0, tip != null ? 0xFFD0D0E0 : 0xFFFFE070, false);
                pose.popMatrix();
            }
        }
        setPreview(BattleClient.hovering);
    }

    private void text(GuiGraphicsExtractor g, String s, int x, int y, float scale, int color) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        g.text(font, Component.literal(s), 0, 0, color, false);
        pose.popMatrix();
    }

    /** Plays (or lets go of) the hovered move's wind-up on your own fighter, on your screen only. */
    private void setPreview(@Nullable String move) {
        if (java.util.Objects.equals(move, previewing)) return;
        previewing = move;
        BattlePayloads.State p = BattleClient.state();
        if (p == null || minecraft == null || minecraft.level == null) return;
        int id = side() == 0 ? p.entity0() : p.entity1();
        String clip = move == null ? null : PREVIEW.get(move);
        if (move != null && clip == null) {
            BattleState s = st();
            if (s != null && kit(s).guard.id.equals(move)) clip = kit(s).character.equals("gojo") ? "guard_gojo" : "guard";
        }
        ClientAnimations.play(id, clip == null ? "" : clip, 1f, minecraft.level.getGameTime());
    }
}
