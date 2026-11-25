package dev.rick.jjk.client.battle;

import dev.rick.jjk.battle.BattleEngine;
import dev.rick.jjk.battle.BattleKit;
import dev.rick.jjk.battle.BattleKits;
import dev.rick.jjk.battle.BattleMove;
import dev.rick.jjk.battle.BattleMove.Category;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * The battle menu, laid out like a classic monster-battle menu: a text box on the left ("What will Yuji do?") and four
 * options on the right. Each option opens its own section of up to four choices, so it stays sectioned and small:
 *
 * <ul>
 *   <li><b>FIGHT</b>: the four moves. A move that can chain opens <b>COMBO</b> (the follow-ups, or FINISH).</li>
 *   <li><b>DEFEND</b>: the character's guard, Focus.</li>
 *   <li><b>MOVE</b>: Close In, Back Off.</li>
 *   <li><b>CURSED</b>: Domain Expansion (when the meter is full), Boost, Forfeit.</li>
 * </ul>
 * Keys 1-4 pick (top left, top right, bottom left, bottom right), Esc or Backspace goes back (Esc on the main page hides
 * the menu; B brings it back). Hovering a move previews its wind-up on your own fighter and moves the camera over your
 * shoulder (on your screen only).
 */
public class BattleScreen extends Screen {
    // GLFW key codes.
    private static final int K_ESCAPE = 256, K_1 = 49, K_4 = 52, K_BACKSPACE = 259, K_ENTER = 257, K_KP_ENTER = 335, K_SPACE = 32;
    private static final float TS = 0.72f;

    private enum Page { ROOT, FIGHT, COMBO, DEFEND, MOVE, CURSED }

    /** One of the four slots on a page. */
    private record Option(String label, boolean enabled, int accent, int dots, String tip, Runnable action, @Nullable String move) {}

    private Page page = Page.ROOT;
    private final List<String> chain = new ArrayList<>();
    private int boost;
    private boolean sent, confirmForfeit;
    @Nullable private String previewing;
    private int hoveredSlot = -1;
    private long openedAt;
    private final int[][] slotBox = new int[4][];

    /** Your own fighter's wind-up for each move, played locally while it is hovered. */
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

    // ------------------------------------------------------------------------------------------------ pages

    private Option[] options(BattleState s) {
        BattleKit k = kit(s);
        CharacterTheme theme = CharacterTheme.of(k.character);
        Option[] o = new Option[4];
        switch (page) {
            case ROOT -> {
                boolean canMove = BattleEngine.whyNot(s, side(), BattleKits.CLOSE_IN) == null || BattleEngine.whyNot(s, side(), BattleKits.BACK_OFF) == null;
                boolean domain = BattleEngine.whyNot(s, side(), k.domain) == null;
                o[0] = new Option("FIGHT", true, 0xFFFF7050, 0, "Attack with one of your four moves.", () -> go(Page.FIGHT), null);
                o[1] = new Option("DEFEND", true, 0xFF60C0FF, 0, "Guard against strikes, or Focus to gather cursed energy.", () -> go(Page.DEFEND), null);
                o[2] = new Option("MOVE", canMove, 0xFF70E090, 0, "Close the distance or back off.", () -> go(Page.MOVE), null);
                o[3] = new Option(domain ? "CURSED ★" : "CURSED", true, domain ? theme.accent() : 0xFFC080FF, 0,
                        domain ? "Your Domain Expansion is ready!" : "Boost your next attack, or forfeit.", () -> go(Page.CURSED), null);
            }
            case FIGHT -> {
                for (int i = 0; i < 4 && i < k.moves.size(); i++) {
                    BattleMove m = k.moves.get(i);
                    String why = BattleEngine.whyNot(s, side(), m);
                    o[i] = moveOption(m, why == null, why, () -> pickMove(s, m));
                }
            }
            case COMBO -> {
                int i = 0;
                for (BattleMove m : k.moves) {
                    if (i >= 3) break;
                    if (canLink(s, m)) o[i++] = moveOption(m, true, null, () -> addLink(s, m));
                }
                o[3] = new Option("FINISH", true, 0xFFFFD040, 0, "Lock in the combo as it is.", this::lockIn, null);
            }
            case DEFEND -> {
                o[0] = shared(s, k.guard, 0xFF60C0FF);
                o[1] = shared(s, BattleKits.FOCUS, 0xFF80D0FF);
                o[3] = back();
            }
            case MOVE -> {
                o[0] = shared(s, BattleKits.CLOSE_IN, 0xFF70E090);
                o[1] = shared(s, BattleKits.BACK_OFF, 0xFF70E090);
                o[3] = back();
            }
            case CURSED -> {
                String why = BattleEngine.whyNot(s, side(), k.domain);
                o[0] = new Option(why == null ? "DOMAIN" : "Domain", why == null, theme.accent(), 0,
                        k.domain.name + (why != null ? "  ·  " + why : "") + "\n" + k.domain.line, () -> lockShared(s, k.domain), k.domain.id);
                StringBuilder flames = new StringBuilder("Boost ");
                for (int i = 0; i < BattleEngine.MAX_BOOST; i++) flames.append(i < boost ? "◆" : "◇");
                o[1] = new Option(flames.toString(), true, 0xFFFF8030, 0,
                        "Boost your next attack\n+1 speed per level, " + BattleEngine.BOOST_COST + " CE each. Pick to change.",
                        () -> boost = boost >= BattleEngine.MAX_BOOST ? 0 : boost + 1, null);
                o[2] = new Option(confirmForfeit ? "Sure?" : "Forfeit", true, 0xFFE05050, 0,
                        confirmForfeit ? "Pick again to give up the battle." : "Give up the battle.", this::forfeitStep, null);
                o[3] = back();
            }
        }
        return o;
    }

    private Option moveOption(BattleMove m, boolean enabled, @Nullable String why, Runnable action) {
        int accent = m.category == Category.STRIKE ? 0xFFFF9050 : 0xFF70B0FF;
        String tip = m.name + "  ·  " + (m.category == Category.STRIKE ? "Strike" : "Technique") + "  ·  " + m.power + " dmg  ·  "
                + m.cost + " CE" + (m.pierce ? "  ·  unblockable" : "") + (why != null ? "  ·  " + why : "") + "\n" + m.line;
        return new Option(m.name, enabled, accent, m.weight.speed, tip, action, m.id);
    }

    private Option shared(BattleState s, BattleMove m, int accent) {
        String why = BattleEngine.whyNot(s, side(), m);
        return new Option(m.name, why == null, accent, 0, m.name + (why != null ? "  ·  " + why : "") + "\n" + m.line, () -> lockShared(s, m), m.id);
    }

    private Option back() {
        return new Option("BACK", true, 0xFF9090A0, 0, "Back to the main options.", () -> go(Page.ROOT), null);
    }

    private void go(Page p) {
        page = p;
        confirmForfeit = false;
        if (p == Page.ROOT || p == Page.FIGHT) chain.clear();
    }

    private void goBack() {
        switch (page) {
            case ROOT -> {
                BattleClient.hidden = true;
                onClose();
            }
            case COMBO -> {
                if (!chain.isEmpty()) chain.removeLast();
                if (chain.isEmpty()) go(Page.FIGHT);
            }
            default -> go(Page.ROOT);
        }
    }

    // ------------------------------------------------------------------------------------------------ choosing

    private void pickMove(BattleState s, BattleMove m) {
        if (BattleEngine.whyNot(s, side(), m) != null) return;
        chain.clear();
        chain.add(m.id);
        if (hasLinks(s)) page = Page.COMBO;
        else lockIn();
    }

    private void addLink(BattleState s, BattleMove m) {
        if (!canLink(s, m)) return;
        chain.add(m.id);
        if (!hasLinks(s)) lockIn();
    }

    private boolean hasLinks(BattleState s) {
        for (BattleMove m : kit(s).moves) if (canLink(s, m)) return true;
        return false;
    }

    /** Whether {@code m} can go on the end of the combo right now (affordable with no boost). */
    private boolean canLink(BattleState s, BattleMove m) {
        if (chain.isEmpty() || chain.size() >= BattleEngine.maxChain(s.f[side()])) return false;
        List<String> next = new ArrayList<>(chain);
        next.add(m.id);
        return BattleEngine.validate(s, side(), new Plan(next, 0)) == null;
    }

    private void lockShared(BattleState s, BattleMove m) {
        if (BattleEngine.whyNot(s, side(), m) != null) return;
        chain.clear();
        chain.add(m.id);
        lockIn();
    }

    private void lockIn() {
        BattleState s = st();
        BattlePayloads.State p = BattleClient.state();
        if (s == null || p == null || sent) return;
        if (chain.isEmpty()) chain.add(kit(s).guard.id);
        BattleMove first = kit(s).move(chain.getFirst());
        int b = first != null && first.category.attack() ? boost : 0;
        // Drop boost levels the plan can't afford rather than refuse it.
        while (b > 0 && BattleEngine.validate(s, side(), new Plan(chain, b)) != null) b--;
        if (BattleEngine.validate(s, side(), new Plan(chain, b)) != null) return;
        ClientPlayNetworking.send(new BattlePayloads.Choice(p.session(), List.copyOf(chain), b, false));
        sent = true;
        onClose();
    }

    private void forfeitStep() {
        if (!confirmForfeit) {
            confirmForfeit = true;
            return;
        }
        BattlePayloads.State p = BattleClient.state();
        if (p != null) ClientPlayNetworking.send(new BattlePayloads.Choice(p.session(), List.of(), 0, true));
        sent = true;
        onClose();
    }

    /** The initiative the current pick would come out at, with the reasons. */
    private String initiativeText(BattleState s) {
        if (chain.isEmpty()) return "";
        BattleMove m = kit(s).move(chain.getFirst());
        if (m == null || !m.category.attack()) return "";
        Fighter f = s.f[side()];
        int total = m.weight.speed;
        StringBuilder b = new StringBuilder("Speed " + m.weight.speed);
        if (f.control > 0) { b.append(" +1 control"); total += 1; }
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
        if (s == null) return super.keyPressed(event);
        if (key == K_ESCAPE || key == K_BACKSPACE) {
            goBack();
            return true;
        }
        if (key >= K_1 && key <= K_4) {
            choose(s, key - K_1);
            return true;
        }
        if ((key == K_ENTER || key == K_KP_ENTER || key == K_SPACE) && page == Page.COMBO) {
            lockIn();
            return true;
        }
        return super.keyPressed(event);
    }

    private void choose(BattleState s, int slot) {
        Option o = options(s)[slot];
        if (o != null && o.enabled) o.action.run();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        BattleState s = st();
        if (s != null) {
            for (int i = 0; i < 4; i++) {
                int[] b = slotBox[i];
                if (b != null && event.x() >= b[0] && event.x() < b[2] && event.y() >= b[1] && event.y() < b[3]) {
                    choose(s, i);
                    return true;
                }
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

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        Arrays.fill(slotBox, null);
        BattleState s = st();
        BattlePayloads.State p = BattleClient.state();
        if (s == null || p == null) return;
        BattleKit k = kit(s);
        CharacterTheme theme = CharacterTheme.of(k.character);
        Option[] opts = options(s);

        float in = Mth.clamp((System.currentTimeMillis() - openedAt) / 160f, 0, 1);
        int boxH = 30, y = height - boxH - 3 + Math.round((1 - in) * 24);
        int split = Math.round(width * 0.5f);
        int lx0 = 4, lx1 = split - 2, rx0 = split + 2, rx1 = width - 4;

        // Options (right): a 2x2 grid.
        hoveredSlot = -1;
        int cellW = (rx1 - rx0 - 3) / 2, cellH = (boxH - 3) / 2;
        frame(g, rx0, y, rx1, y + boxH, theme.accent());
        for (int i = 0; i < 4; i++) {
            int cx = rx0 + 1 + (i % 2) * (cellW + 1), cy = y + 1 + (i / 2) * (cellH + 1);
            Option o = opts[i];
            if (o == null) continue;
            boolean over = mouseX >= cx && mouseX < cx + cellW && mouseY >= cy && mouseY < cy + cellH;
            if (over) hoveredSlot = i;
            slotBox[i] = new int[] {cx, cy, cx + cellW, cy + cellH};
            g.fill(cx, cy, cx + cellW, cy + cellH, over && o.enabled ? (theme.ceBottom() & 0xFFFFFF) | 0xD0000000 : 0xC0101018);
            g.fill(cx, cy + cellH - 1, cx + cellW, cy + cellH, o.enabled ? o.accent : 0x30FFFFFF);
            String label = (over && o.enabled ? "▶ " : "") + o.label;
            text(g, String.valueOf(i + 1), cx + 3, cy + 4, 0.55f, 0xFF7070A0);
            text(g, label, cx + 10, cy + 3, TS, o.enabled ? 0xFFFFFFFF : 0xFF55555F);
            for (int d = 0; d < o.dots; d++) g.fill(cx + cellW - 4 - d * 3, cy + 3, cx + cellW - 2 - d * 3, cy + 5, o.enabled ? 0xFFFFE070 : 0xFF45454D);
        }

        // Text box (left): the prompt, or what's under the mouse.
        frame(g, lx0, y, lx1, y + boxH, 0xFF505060);
        int secs = (p.timer() + 19) / 20;
        CinematicPanels.label(g, font, secs + "s", lx1 - 3, y + 3, 0.65f, secs <= 5 ? 0xFFFF6060 : 0xFFB0B0C0, 1f, true);
        String head, sub;
        Option hov = hoveredSlot >= 0 ? opts[hoveredSlot] : null;
        if (hov != null) {
            String[] parts = hov.tip.split("\n", 2);
            head = parts[0];
            sub = parts.length > 1 ? parts[1] : "";
        } else {
            head = switch (page) {
                case ROOT -> "What will " + k.name + " do?";
                case FIGHT -> "Which move?";
                case COMBO -> comboText(k) + "  →  ?";
                case DEFEND -> "Defend how?";
                case MOVE -> "Move where?";
                case CURSED -> "Cursed energy: " + s.f[side()].ce;
            };
            String ini = initiativeText(s);
            Fighter me = s.f[side()];
            String limit = me.control >= Fighter.MAX_CONTROL ? "Full control: a full combo ends in your finisher!"
                    : me.control >= 1 ? "Control: combos up to 3 moves." : "Take control (win cleanly) for longer combos.";
            sub = page == Page.COMBO ? limit + (ini.isEmpty() ? "" : "   " + ini)
                    : page == Page.ROOT ? (boost > 0 ? "Boost set for your next attack.  " : "") + "Esc: hide menu"
                    : page == Page.FIGHT && Arrays.stream(opts).noneMatch(o -> o != null && o.enabled)
                    ? "Nothing reaches from here — use MOVE to close in.  Esc: back"
                    : "Esc: back";
        }
        wrap(g, head, lx0 + 5, y + 4, lx1 - lx0 - 30, 0.68f, 0xFFFFFFFF, 1);
        wrap(g, sub, lx0 + 5, y + 15, lx1 - lx0 - 10, 0.55f, 0xFFB8B8C8, 2);

        String hoveredMove = hov != null && hov.move != null ? hov.move : null;
        setPreview(hoveredMove);
        BattleClient.hovering = hoveredMove;
    }

    private String comboText(BattleKit k) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chain.size(); i++) {
            BattleMove m = k.move(chain.get(i));
            if (i > 0) sb.append(" → ");
            sb.append(m == null ? chain.get(i) : m.name);
        }
        return sb.toString();
    }

    private static void frame(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color) {
        g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, (color & 0xFFFFFF) | 0xB0000000);
        g.fill(x0, y0, x1, y1, 0xD8080810);
    }

    private void text(GuiGraphicsExtractor g, String s, int x, int y, float scale, int color) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        g.text(font, Component.literal(s), 0, 0, color, false);
        pose.popMatrix();
    }

    /** Text wrapped to {@code width} (GUI pixels), at most {@code maxLines} lines. */
    private void wrap(GuiGraphicsExtractor g, String s, int x, int y, int width, float scale, int color, int maxLines) {
        if (s.isEmpty()) return;
        var lines = font.split(Component.literal(s), Math.max(10, Math.round(width / scale)));
        for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
            var pose = g.pose();
            pose.pushMatrix();
            pose.translate(x, y + i * Math.round(9 * scale + 1));
            pose.scale(scale, scale);
            g.text(font, lines.get(i), 0, 0, color, false);
            pose.popMatrix();
        }
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
