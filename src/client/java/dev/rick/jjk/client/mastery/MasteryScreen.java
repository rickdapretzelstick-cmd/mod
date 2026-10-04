package dev.rick.jjk.client.mastery;

import dev.rick.jjk.client.cinematic.CinematicPanels;
import dev.rick.jjk.client.hud.CharacterTheme;
import dev.rick.jjk.core.character.Characters;
import dev.rick.jjk.core.character.JJKCharacter;
import dev.rick.jjk.core.net.MasteryPurchasePayload;
import dev.rick.jjk.progression.mastery.MasteryData;
import dev.rick.jjk.progression.mastery.MasteryNode;
import dev.rick.jjk.progression.mastery.MasteryTree;
import dev.rick.jjk.progression.mastery.MasteryTrees;
import dev.rick.jjk.progression.mastery.SorcererGrade;
import dev.rick.jjk.progression.tool.CursedToolItem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Mastery screen (default key J). Not a character select: it shows only what this player can develop.
 * <ul>
 *   <li>the <b>Technique</b> tab, for the kit they legitimately own (nothing for a borrowed Creative kit);</li>
 *   <li>the <b>Cursed Tool</b> tab, for the cursed tool in their hand.</li>
 * </ul>
 * The tree is drawn from its definition: lanes are columns, rows are tiers, prerequisite lines join them. Nodes show
 * their state (owned, available, locked), their tier by shape (small, mechanical, major, the Awakening) and, picked, their
 * full card on the right with a Develop button. The server checks every purchase again; this screen only asks.
 */
public class MasteryScreen extends Screen {
    private static final int ROW_H_MAX = 46, ROW_H_MIN = 30;
    private static final int TOOL_ACCENT = 0xFF9A5CFF;

    private final List<MasteryTree> tabs = new ArrayList<>();
    private int tab;
    @Nullable private String selected;
    @Nullable private String hovered;
    private double scroll;
    private int seenVersion = -1;
    private long openedAt;
    /** A purchase sent and not yet answered: its node, and when (the sync that follows clears it). */
    @Nullable private String pending;

    public MasteryScreen() {
        super(Component.literal("Mastery"));
    }

    @Override
    protected void init() {
        tabs.clear();
        String kit = ClientMastery.kit();
        if (!kit.isEmpty()) {
            MasteryTree t = MasteryTrees.get(MasteryTree.techniqueId(kit));
            if (t != null) tabs.add(t);
        }
        if (minecraft.player != null && minecraft.player.getMainHandItem().getItem() instanceof CursedToolItem tool) {
            MasteryTree t = MasteryTrees.get(tool.definition().treeId());
            if (t != null) tabs.add(t);
        }
        tab = Mth.clamp(tab, 0, Math.max(0, tabs.size() - 1));
        openedAt = System.currentTimeMillis();
    }

    @Nullable
    private MasteryTree tree() {
        return tabs.isEmpty() ? null : tabs.get(tab);
    }

    private int accent(MasteryTree t) {
        return t.kind() == MasteryTree.Kind.TOOL ? TOOL_ACCENT : CharacterTheme.of(t.owner()).accent();
    }

    // --- Layout ---

    private int canvasX0() {
        return 12;
    }

    private int panelW() {
        return Mth.clamp(width * 3 / 10, 120, 170);
    }

    private int canvasX1() {
        return width - panelW() - 18;
    }

    private float laneW(MasteryTree t) {
        return (canvasX1() - canvasX0()) / (float) Math.max(1, t.lanes().size());
    }

    /** Row spacing: the whole tree fits when it can, never cramped below {@link #ROW_H_MIN} (then it scrolls). */
    private int rowH(MasteryTree t) {
        return Mth.clamp((canvasY1() - canvasY0() - 44) / Math.max(1, rows(t) - 1), ROW_H_MIN, ROW_H_MAX);
    }

    /** Node scale with the row spacing (small screens get smaller nodes rather than overlapping ones). */
    private float k(MasteryTree t) {
        return Math.max(0.65f, rowH(t) / (float) ROW_H_MAX);
    }

    private int canvasY0() {
        return 52;
    }

    private int canvasY1() {
        return height - 12;
    }

    private int size(MasteryTree t, MasteryNode n) {
        int base = switch (n.tier()) {
            case SMALL -> 18;
            case MECHANICAL -> 22;
            case MAJOR -> 26;
            case AWAKENING -> 32;
        };
        return Math.max(10, Math.round(base * k(t)));
    }

    private int nodeX(MasteryTree t, MasteryNode n) {
        int lanes = Math.max(1, t.lanes().size());
        float w = (canvasX1() - canvasX0()) / (float) lanes;
        return Math.round(canvasX0() + w * (n.lane() + 0.5f));
    }

    private int nodeY(MasteryTree t, MasteryNode n) {
        return (int) Math.round(canvasY0() + 28 + n.row() * rowH(t) - scroll);
    }

    private int rows(MasteryTree t) {
        int r = 0;
        for (MasteryNode n : t.nodes()) r = Math.max(r, n.row());
        return r + 1;
    }

    // --- State ---

    private enum State { OWNED, AVAILABLE, LOCKED }

    private static State state(MasteryTree t, MasteryNode n, MasteryData d) {
        if (d.has(t.id(), n.id())) return State.OWNED;
        for (String r : n.requires()) if (!d.has(t.id(), r)) return State.LOCKED;
        return State.AVAILABLE;
    }

    private static int cost(MasteryNode n) {
        return (int) Math.max(0, Math.round(n.cost() * dev.rick.jjk.config.JJKConfig.get().mastery.costMultiplier));
    }

    @Nullable
    private MasteryNode nodeAt(MasteryTree t, double mx, double my) {
        if (mx < canvasX0() || mx > canvasX1() || my < canvasY0() || my > canvasY1()) return null;
        for (MasteryNode n : t.nodes()) {
            int s = size(t, n) / 2 + 2, x = nodeX(t, n), y = nodeY(t, n);
            if (Math.abs(mx - x) <= s && Math.abs(my - y) <= s) return n;
        }
        return null;
    }

    // --- Input ---

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        // Tabs.
        for (int i = 0; i < tabs.size(); i++) {
            int[] r = tabRect(i);
            if (mx >= r[0] && mx < r[2] && my >= r[1] && my < r[3]) {
                tab = i;
                selected = null;
                scroll = 0;
                return true;
            }
        }
        MasteryTree t = tree();
        if (t != null) {
            MasteryNode n = nodeAt(t, mx, my);
            if (n != null) {
                selected = n.id();
                if (doubleClick) develop(t, n);
                return true;
            }
            int[] b = developRect();
            MasteryNode sel = selected == null ? null : t.node(selected);
            if (sel != null && mx >= b[0] && mx < b[2] && my >= b[1] && my < b[3]) {
                develop(t, sel);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void develop(MasteryTree t, MasteryNode n) {
        MasteryData d = ClientMastery.data();
        if (state(t, n, d) != State.AVAILABLE || d.points(t.id()) < cost(n) || pending != null) return;
        pending = n.id();
        ClientPlayNetworking.send(new MasteryPurchasePayload(t.id(), n.id()));
    }

    @Override
    public boolean mouseScrolled(double x, double y, double h, double v) {
        MasteryTree t = tree();
        if (t == null) return false;
        double max = Math.max(0, (rows(t) - 1) * rowH(t) + 60 - (canvasY1() - canvasY0()));
        scroll = Mth.clamp(scroll - v * 18, 0, max);
        return true;
    }

    private int[] tabRect(int i) {
        int w = Math.min(150, (canvasX1() - 12) / Math.max(1, tabs.size()) - 6), x = 12 + i * (w + 6);
        return new int[] {x, 30, x + w, 46};
    }

    private int[] developRect() {
        int x0 = width - panelW() - 6;
        return new int[] {x0 + 10, height - 40, x0 + panelW() - 10, height - 22};
    }

    // --- Drawing ---

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        if (seenVersion != ClientMastery.version()) {
            seenVersion = ClientMastery.version();
            pending = null;
        }
        g.fillGradient(0, 0, width, height, 0xE0060609, 0xF00C0C14);
        MasteryData d = ClientMastery.data();
        CinematicPanels.label(g, font, "MASTERY", 12, 10, 1.5f, 0xFFFFFFFF, 1f, false);
        CinematicPanels.label(g, font, SorcererGrade.of(d.grade()).display.toUpperCase(Locale.ROOT), width - 12, 12, 0.9f, 0xFFB0B0C0, 1f, true);
        MasteryTree t = tree();
        if (t == null) {
            empty(g);
            super.extractRenderState(g, mouseX, mouseY, a);
            return;
        }
        for (int i = 0; i < tabs.size(); i++) tab(g, i, i == tab, mouseX, mouseY);
        int accent = accent(t);
        // Mastery available, above the canvas on the right of the tabs.
        String pts = d.points(t.id()) + " Mastery";
        CinematicPanels.label(g, font, pts, width - 12, 34, 1.0f, accent, 1f, true);
        // Canvas: lanes as faint columns with their names.
        g.fill(canvasX0() - 4, canvasY0() - 2, canvasX1() + 4, canvasY1(), 0x60000000);
        g.outline(canvasX0() - 4, canvasY0() - 2, canvasX1() - canvasX0() + 8, canvasY1() - canvasY0() + 2, 0xFF22222C);
        int lanes = Math.max(1, t.lanes().size());
        float lw = (canvasX1() - canvasX0()) / (float) lanes;
        for (int i = 0; i < lanes; i++) {
            int cx = Math.round(canvasX0() + lw * (i + 0.5f));
            if (i > 0) g.fill(Math.round(canvasX0() + lw * i), canvasY0(), Math.round(canvasX0() + lw * i) + 1, canvasY1(), 0x18FFFFFF);
            CinematicPanels.labelCentered(g, font, fit(t.lanes().get(i).toUpperCase(Locale.ROOT), lw - 10, 0.65f), cx, canvasY0() + 4, 0.65f, 0xFF8A8A9A, 1f);
        }
        MasteryNode hover = nodeAt(t, mouseX, mouseY);
        hovered = hover == null ? null : hover.id();
        g.enableScissor(canvasX0() - 3, canvasY0() + 14, canvasX1() + 3, canvasY1() - 1);
        // Prerequisite lines first, under the nodes.
        for (MasteryNode n : t.nodes()) {
            for (String r : n.requires()) {
                MasteryNode p = t.node(r);
                if (p == null) continue;
                boolean lit = d.has(t.id(), p.id());
                boolean done = lit && d.has(t.id(), n.id());
                link(g, nodeX(t, p), nodeY(t, p) + size(t, p) / 2, nodeX(t, n), nodeY(t, n) - size(t, n) / 2,
                        done ? accent : lit ? (accent & 0xFFFFFF) | 0x90000000 : 0x60FFFFFF);
            }
        }
        float time = (System.currentTimeMillis() - openedAt) / 1000f;
        for (MasteryNode n : t.nodes()) node(g, t, n, d, accent, time);
        g.disableScissor();
        // More of the tree below (or above): a quiet hint at the edge.
        double max = Math.max(0, (rows(t) - 1) * rowH(t) + 60 - (canvasY1() - canvasY0()));
        if (scroll < max - 1) CinematicPanels.labelCentered(g, font, "▼ scroll", (canvasX0() + canvasX1()) / 2f, canvasY1() - 9, 0.6f, 0xFF9A9AAA, 1f);
        if (scroll > 1) CinematicPanels.labelCentered(g, font, "▲", (canvasX0() + canvasX1()) / 2f, canvasY0() + 14, 0.6f, 0xFF9A9AAA, 1f);
        panel(g, t, d, accent, mouseX, mouseY);
        super.extractRenderState(g, mouseX, mouseY, a);
    }

    private void empty(GuiGraphicsExtractor g) {
        int cx = width / 2, cy = height / 2 - 20;
        CinematicPanels.labelCentered(g, font, "Nothing to develop yet.", cx, cy, 1.2f, 0xFFE0E0E8, 1f);
        List<FormattedCharSequence> lines = font.split(Component.literal(
                "Mastery grows from exorcising curses. Hold a cursed tool to see its tree; a cursed technique that is truly "
                        + "your own has a tree of its own. Curses can't be hurt by ordinary weapons."), Math.min(320, width - 40));
        int y = cy + 18;
        for (var l : lines) {
            g.centeredText(font, l, cx, y, 0xFF9A9AAA);
            y += 10;
        }
    }

    private void tab(GuiGraphicsExtractor g, int i, boolean on, int mx, int my) {
        MasteryTree t = tabs.get(i);
        int[] r = tabRect(i);
        boolean hover = mx >= r[0] && mx < r[2] && my >= r[1] && my < r[3];
        int accent = accent(t);
        g.fill(r[0], r[1], r[2], r[3], on ? (accent & 0xFFFFFF) | 0x60000000 : hover ? 0x40FFFFFF : 0x30000000);
        g.fill(r[0], r[3] - 2, r[2], r[3], on ? accent : 0xFF30303A);
        String kind = t.kind() == MasteryTree.Kind.TOOL ? "CURSED TOOL" : "TECHNIQUE";
        String name = t.kind() == MasteryTree.Kind.TOOL ? t.title() : kitName(t.owner());
        CinematicPanels.label(g, font, fit(kind + " · " + name, r[2] - r[0] - 10, 0.75f), r[0] + 6, r[1] + 4, 0.75f, on ? 0xFFFFFFFF : 0xFFA0A0B0, 1f, false);
    }

    private static String kitName(String kit) {
        JJKCharacter c = Characters.get(kit);
        return c == null ? kit : c.displayName();
    }

    /** A prerequisite line: down from the parent, across, down into the child. */
    private static void link(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color) {
        int mid = (y0 + y1) / 2;
        g.fill(x0, y0, x0 + 1, mid + 1, color);
        g.fill(Math.min(x0, x1), mid, Math.max(x0, x1) + 1, mid + 1, color);
        g.fill(x1, mid, x1 + 1, y1, color);
    }

    private void node(GuiGraphicsExtractor g, MasteryTree t, MasteryNode n, MasteryData d, int accent, float time) {
        State st = state(t, n, d);
        int s = size(t, n), h = s / 2, x = nodeX(t, n), y = nodeY(t, n);
        boolean afford = d.points(t.id()) >= cost(n);
        boolean sel = n.id().equals(selected), hov = n.id().equals(hovered);
        int frame = switch (st) {
            case OWNED -> accent;
            case AVAILABLE -> afford ? 0xFFFFFFFF : 0xFF9A9AA8;
            case LOCKED -> 0xFF3A3A44;
        };
        boolean big = n.tier() == MasteryNode.Tier.MAJOR || n.tier() == MasteryNode.Tier.AWAKENING;
        // The Awakening glows; a milestone has a gold outer ring.
        if (n.tier() == MasteryNode.Tier.AWAKENING) {
            float pulse = 0.5f + 0.5f * Mth.sin(time * 3f);
            int glow = (int) (40 + 60 * pulse) << 24 | (accent & 0xFFFFFF);
            g.fill(x - h - 5, y - h - 5, x + h + 5, y + h + 5, glow);
        }
        if (big) g.fill(x - h - 3, y - h - 3, x + h + 3, y + h + 3, st == State.LOCKED ? 0xFF4A4030 : 0xFFD8B45A);
        g.fill(x - h - 1, y - h - 1, x + h + 1, y + h + 1, sel || hov ? 0xFFFFFFFF : frame);
        int body = switch (st) {
            case OWNED -> (accent & 0xFFFFFF) | 0xC0000000;
            case AVAILABLE -> 0xFF1C1C26;
            case LOCKED -> 0xFF0E0E12;
        };
        g.fill(x - h, y - h, x + h, y + h, body);
        // The tier's mark in the middle: a dot, a diamond (a new mechanic), a star-like cross, the Awakening's eye.
        int mark = st == State.LOCKED ? 0xFF50505A : st == State.OWNED ? 0xFFFFFFFF : accent;
        switch (n.tier()) {
            case SMALL -> g.fill(x - 2, y - 2, x + 2, y + 2, mark);
            case MECHANICAL -> {
                for (int i = 0; i < 5; i++) g.fill(x - i, y - 4 + i, x + i + 1, y - 3 + i, mark);
                for (int i = 0; i < 4; i++) g.fill(x - 3 + i, y + 1 + i, x + 4 - i, y + 2 + i, mark);
            }
            case MAJOR -> {
                g.fill(x - 6, y - 1, x + 7, y + 1, mark);
                g.fill(x - 1, y - 6, x + 1, y + 7, mark);
            }
            case AWAKENING -> {
                g.fill(x - 8, y - 2, x + 9, y + 2, mark);
                g.fill(x - 5, y - 4, x + 6, y + 4, mark);
                g.fill(x - 2, y - 2, x + 3, y + 2, 0xFF000000);
            }
        }
        // Name under it; the cost for anything not owned.
        String name = n.name();
        int labelColor = st == State.LOCKED ? 0xFF6A6A76 : 0xFFE0E0EA;
        CinematicPanels.labelCentered(g, font, fit(name, laneW(t) - 4, 0.6f), x, y + h + 3, 0.6f, labelColor, 1f);
        if (st != State.OWNED) {
            CinematicPanels.labelCentered(g, font, String.valueOf(cost(n)), x, y + h + 9, 0.55f,
                    st == State.AVAILABLE && afford ? accent : 0xFF707080, 1f);
        }
        if (n.id().equals(pending)) CinematicPanels.labelCentered(g, font, "…", x, y - h - 9, 0.8f, 0xFFFFFFFF, 1f);
    }

    /** {@code s}, shortened with an ellipsis until it fits {@code width} GUI pixels at {@code scale}. */
    private String fit(String s, float width, float scale) {
        if (font.width(s) * scale <= width) return s;
        String out = s;
        while (out.length() > 1 && font.width(out + "…") * scale > width) out = out.substring(0, out.length() - 1);
        return out.trim() + "…";
    }

    private void panel(GuiGraphicsExtractor g, MasteryTree t, MasteryData d, int accent, int mx, int my) {
        int x0 = width - panelW() - 6, y0 = canvasY0() - 2, x1 = width - 6, y1 = height - 12;
        g.fill(x0, y0, x1, y1, 0xB0000000);
        g.fill(x0, y0, x0 + 2, y1, accent);
        String id = hovered != null ? hovered : selected;
        MasteryNode n = id == null ? null : t.node(id);
        int x = x0 + 10, y = y0 + 8, w = panelW() - 18;
        if (n == null) {
            CinematicPanels.label(g, font, t.title(), x, y, 0.9f, 0xFFFFFFFF, 1f, false);
            y += 14;
            int owned = d.bought(t.id()).size();
            y = wrap(g, owned + " of " + t.nodes().size() + " developed. " + d.earned().getOrDefault(t.id(), 0)
                    + " Mastery earned here in all.", x, y, w, 0xFFB0B0BE);
            y += 6;
            wrap(g, "Pick a node to read it. Double-click (or Develop) to buy one whose prerequisites you own.", x, y, w, 0xFF80808E);
            return;
        }
        State st = state(t, n, d);
        y = wrap(g, n.name(), x, y, w, 0xFFFFFFFF, 1.0f) + 2;
        String tier = switch (n.tier()) {
            case SMALL -> "Upgrade";
            case MECHANICAL -> "New mechanic";
            case MAJOR -> "Milestone";
            case AWAKENING -> "Awakening";
        };
        CinematicPanels.label(g, font, tier.toUpperCase(Locale.ROOT) + (n.move().isEmpty() ? "" : " · " + pretty(n.move())), x, y, 0.65f, accent, 1f, false);
        y += 11;
        y = wrap(g, n.description(), x, y, w, 0xFFD0D0DA) + 4;
        if (!n.requires().isEmpty()) {
            List<String> names = new ArrayList<>();
            for (String r : n.requires()) {
                MasteryNode p = t.node(r);
                names.add((d.has(t.id(), r) ? "✔ " : "✘ ") + (p == null ? r : p.name()));
            }
            y = wrap(g, "Needs: " + String.join(", ", names), x, y, w, 0xFF9A9AAA) + 4;
        }
        int c = cost(n);
        String status = switch (st) {
            case OWNED -> "Developed.";
            case LOCKED -> "Locked: its prerequisites come first.";
            case AVAILABLE -> d.points(t.id()) >= c ? "Available: " + c + " Mastery." : "Needs " + c + " Mastery (you have " + d.points(t.id()) + ").";
        };
        wrap(g, status, x, y, w, st == State.OWNED ? accent : 0xFFE0E0E8);
        if (n.id().equals(selected) && st == State.AVAILABLE) {
            boolean can = d.points(t.id()) >= c && pending == null;
            int[] b = developRect();
            boolean hover = mx >= b[0] && mx < b[2] && my >= b[1] && my < b[3];
            g.fill(b[0], b[1], b[2], b[3], can ? (hover ? accent : (accent & 0xFFFFFF) | 0xA0000000) : 0x40FFFFFF);
            CinematicPanels.labelCentered(g, font, pending != null ? "…" : "DEVELOP (" + c + ")", (b[0] + b[2]) / 2f, b[1] + 5, 0.8f,
                    can ? 0xFFFFFFFF : 0xFF808088, 1f);
        }
    }

    private int wrap(GuiGraphicsExtractor g, String text, int x, int y, int w, int color) {
        return wrap(g, text, x, y, w, color, 0.75f);
    }

    private int wrap(GuiGraphicsExtractor g, String text, int x, int y, int w, int color, float sc) {
        var pose = g.pose();
        for (FormattedCharSequence l : font.split(Component.literal(text), (int) (w / sc))) {
            pose.pushMatrix();
            pose.translate(x, y);
            pose.scale(sc, sc);
            g.text(font, l, 0, 0, color, false);
            pose.popMatrix();
            y += Math.round(10 * sc) + 1;
        }
        return y;
    }

    static String pretty(String id) {
        StringBuilder sb = new StringBuilder();
        for (String part : id.split("_")) {
            if (part.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** For tests: the tab titles shown, in order. */
    public List<String> tabIds() {
        List<String> out = new ArrayList<>();
        for (MasteryTree t : tabs) out.add(t.id());
        return out;
    }

    /** For tests: where a node is drawn (GUI coordinates) on the open tab, or null. */
    public int @Nullable [] nodeCenter(String nodeId) {
        MasteryTree t = tree();
        MasteryNode n = t == null ? null : t.node(nodeId);
        return n == null ? null : new int[] {nodeX(t, n), nodeY(t, n)};
    }

    public void selectTab(int i) {
        tab = Mth.clamp(i, 0, Math.max(0, tabs.size() - 1));
        selected = null;
    }

    public void select(@Nullable String nodeId) {
        selected = nodeId;
    }
}
