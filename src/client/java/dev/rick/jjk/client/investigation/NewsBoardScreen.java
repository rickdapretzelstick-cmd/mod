package dev.rick.jjk.client.investigation;

import dev.rick.jjk.core.net.NewsBoardPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A village news board, read: notices pinned to weathered boards. Each is just a piece of paper with what people are
 * saying; click one to read it in full. Nothing is highlighted and nothing says what to do about it.
 */
public class NewsBoardScreen extends Screen {
    private NewsBoardPayload board;
    private int open = -1;
    private int hovered = -1;

    public NewsBoardScreen(NewsBoardPayload board) {
        super(Component.literal(board.village()));
        this.board = board;
    }

    public static void show(NewsBoardPayload p) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        mc.gui.setScreen(new NewsBoardScreen(p));
    }

    private int cols() {
        return Math.max(1, Math.min(3, board.notes().size()));
    }

    private int noteW() {
        return Math.min(150, (boardW() - 24) / cols() - 10);
    }

    private int noteH() {
        return Math.min(110, (boardH() - 50) / 2 - 10);
    }

    private int boardW() {
        return Math.min(520, width - 30);
    }

    private int boardH() {
        return Math.min(300, height - 30);
    }

    private int boardX() {
        return width / 2 - boardW() / 2;
    }

    private int boardY() {
        return height / 2 - boardH() / 2;
    }

    private int[] noteRect(int i) {
        int c = i % cols(), r = i / cols();
        int rowCount = Math.min(cols(), board.notes().size() - r * cols());
        int totalW = rowCount * (noteW() + 10) - 10;
        int x = boardX() + boardW() / 2 - totalW / 2 + c * (noteW() + 10);
        int y = boardY() + 40 + r * (noteH() + 12);
        // A slight, fixed scatter: pinned by hand.
        int jx = (int) (Math.sin(i * 12.9898) * 4), jy = (int) (Math.cos(i * 78.233) * 3);
        return new int[] {x + jx, y + jy, x + jx + noteW(), y + jy + noteH()};
    }

    private int noteAt(double mx, double my) {
        for (int i = 0; i < board.notes().size(); i++) {
            int[] r = noteRect(i);
            if (mx >= r[0] && mx < r[2] && my >= r[1] && my < r[3]) return i;
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (open >= 0) {
            int[] b = investigateButton();
            NewsBoardPayload.Note n = open < board.notes().size() ? board.notes().get(open) : null;
            if (n != null && canInvestigate(n) && event.x() >= b[0] && event.x() < b[2] && event.y() >= b[1] && event.y() < b[3]) {
                investigate(open);
                return true;
            }
            open = -1;
            return true;
        }
        int i = noteAt(event.x(), event.y());
        if (i >= 0) {
            open = i;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        g.fill(0, 0, width, height, 0xA0000000);
        int x0 = boardX(), y0 = boardY(), x1 = x0 + boardW(), y1 = y0 + boardH();
        // The board: dark frame, weathered planks.
        g.fill(x0 - 6, y0 - 6, x1 + 6, y1 + 6, 0xFF3A2A1C);
        for (int y = y0, k = 0; y < y1; y += 18, k++) {
            g.fill(x0, y, x1, Math.min(y1, y + 17), (k % 2 == 0) ? 0xFF6E5A44 : 0xFF66533F);
        }
        g.centeredText(font, Component.literal(board.village() + " — Notices"), width / 2, y0 + 12, 0xFFEADFC8);
        List<NewsBoardPayload.Note> notes = board.notes();
        if (notes.isEmpty()) {
            g.centeredText(font, Component.literal("Nothing pinned up but an old harvest-fair poster."), width / 2, y0 + boardH() / 2, 0xFFD8CCB0);
            super.extractRenderState(g, mouseX, mouseY, a);
            return;
        }
        hovered = open >= 0 ? -1 : noteAt(mouseX, mouseY);
        for (int i = 0; i < notes.size(); i++) note(g, i, notes.get(i));
        if (open >= 0 && open < notes.size()) reading(g, notes.get(open));
        super.extractRenderState(g, mouseX, mouseY, a);
    }

    private static int paper(NewsBoardPayload.Note n) {
        return n.status() == 2 ? 0xFFE4E8E2 : 0xFFEDE3C8;
    }

    private void note(GuiGraphicsExtractor g, int i, NewsBoardPayload.Note n) {
        int[] r = noteRect(i);
        int lift = hovered == i ? 2 : 0;
        g.fill(r[0] + 2, r[1] + 3, r[2] + 2, r[3] + 3, 0x50000000);
        g.fill(r[0], r[1] - lift, r[2], r[3] - lift, paper(n));
        // The pin.
        int pc = (r[0] + r[2]) / 2;
        g.fill(pc - 2, r[1] - lift - 1, pc + 2, r[1] - lift + 3, 0xFF9A2A2A);
        if (n.tracked()) g.fill(r[2] - 10, r[1] - lift, r[2] - 4, r[1] - lift + 12, 0xFF902020);
        int x = r[0] + 6, y = r[1] - lift + 7, w = r[2] - r[0] - 12;
        y = text(g, n.headline(), x, y, w, 0.8f, 0xFF2A2018, 3);
        y += 2;
        String when = n.status() == 2 ? "Posted " + ago(n.daysAgo()) : "Posted " + ago(n.daysAgo()) + (n.status() == 1 ? " · Folk say someone went to look." : "");
        y = text(g, when, x, y, w, 0.6f, 0xFF6A5E50, 2);
        y += 2;
        int maxLines = Math.max(1, (r[3] - lift - 6 - y) / 7);
        text(g, n.body(), x, y, w, 0.62f, 0xFF3A3028, maxLines);
    }

    private static String ago(int days) {
        return days <= 0 ? "today" : days == 1 ? "yesterday" : days + " days ago";
    }

    private int[] readingRect() {
        int w = Math.min(320, width - 40), h = Math.min(240, height - 40);
        int x0 = width / 2 - w / 2, y0 = height / 2 - h / 2;
        return new int[] {x0, y0, x0 + w, y0 + h};
    }

    private int[] investigateButton() {
        int[] r = readingRect();
        int bw = 120;
        return new int[] {(r[0] + r[2]) / 2 - bw / 2, r[3] - 34, (r[0] + r[2]) / 2 + bw / 2, r[3] - 20};
    }

    private static boolean canInvestigate(NewsBoardPayload.Note n) {
        return !n.id().isEmpty() && n.status() != 2;
    }

    /** Marks (or unmarks) a report as the reader's investigation: the server decides; the board shows it at once. */
    public void investigate(int i) {
        NewsBoardPayload.Note n = board.notes().get(i);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new dev.rick.jjk.core.net.InvestigatePayload(n.id()));
        boolean now = !n.tracked();
        List<NewsBoardPayload.Note> notes = new java.util.ArrayList<>();
        for (NewsBoardPayload.Note x : board.notes()) {
            notes.add(new NewsBoardPayload.Note(x.id(), x.headline(), x.body(), x.status(), x.daysAgo(), x.place(), x == n ? now : now ? false : x.tracked()));
        }
        board = new NewsBoardPayload(board.village(), notes);
    }

    /** The report, taken down and read: a filed document. What was reported, where, and when; nothing about what to do. */
    private void reading(GuiGraphicsExtractor g, NewsBoardPayload.Note n) {
        int[] r = readingRect();
        int x0 = r[0], y0 = r[1], w = r[2] - r[0], h = r[3] - r[1];
        g.fill(0, 0, width, height, 0x80000000);
        g.fill(x0 + 3, y0 + 4, x0 + w + 3, y0 + h + 4, 0x60000000);
        g.fill(x0, y0, x0 + w, y0 + h, paper(n));
        // Ruled like a clerk's form: a header rule, a margin line, a stamp box.
        g.fill(x0 + 8, y0 + 8, x0 + w - 8, y0 + 9, 0xFFB8A888);
        g.fill(x0 + 24, y0 + 9, x0 + 25, y0 + h - 8, 0x40B05040);
        int y = text(g, "VILLAGE REPORT", x0 + 32, y0 + 14, w - 44, 0.6f, 0xFF8A6E50, 1) + 2;
        y = text(g, n.headline(), x0 + 32, y, w - 44, 1.0f, 0xFF2A2018, 3) + 4;
        if (!n.place().isEmpty()) y = text(g, "Reported Location: " + n.place(), x0 + 32, y, w - 44, 0.7f, 0xFF5A4A3A, 2);
        y = text(g, "Filed: " + ago(n.daysAgo()), x0 + 32, y, w - 44, 0.7f, 0xFF5A4A3A, 1) + 3;
        g.fill(x0 + 32, y, x0 + w - 12, y + 1, 0xFFC8B898);
        y += 5;
        int bodyLines = Math.max(1, (y0 + h - 40 - y) / 8);
        text(g, n.body(), x0 + 32, y, w - 44, 0.8f, 0xFF3A3028, bodyLines);
        if (n.tracked()) {
            // A rubber stamp in the corner.
            g.fill(x0 + w - 86, y0 + 14, x0 + w - 14, y0 + 30, 0x30902020);
            text(g, "INVESTIGATING", x0 + w - 82, y0 + 19, 70, 0.6f, 0xFF902020, 1);
        }
        if (canInvestigate(n)) {
            int[] b = investigateButton();
            g.fill(b[0], b[1], b[2], b[3], 0xFF4A3A2A);
            g.fill(b[0] + 1, b[1] + 1, b[2] - 1, b[3] - 1, n.tracked() ? 0xFF6A4040 : 0xFF5A4A3A);
            g.centeredText(font, Component.literal(n.tracked() ? "Set it aside" : "Investigate"), (b[0] + b[2]) / 2, b[1] + 3, 0xFFEADFC8);
        }
        g.centeredText(font, Component.literal("(click elsewhere to put it back)"), width / 2, y0 + h - 12, 0xFF8A7E70);
    }

    /** Wrapped, scaled text; returns the y after it. */
    private int text(GuiGraphicsExtractor g, String s, int x, int y, int w, float scale, int color, int maxLines) {
        var pose = g.pose();
        int lines = 0;
        int lh = Math.round(9 * scale) + 1;
        for (FormattedCharSequence l : font.split(Component.literal(s), Mth.floor(w / scale))) {
            if (lines++ >= maxLines) break;
            pose.pushMatrix();
            pose.translate(x, y);
            pose.scale(scale, scale);
            g.text(font, l, 0, 0, color, false);
            pose.popMatrix();
            y += lh;
        }
        return y;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** For tests: the note being read, or -1. */
    public int reading() {
        return open;
    }

    public void read(@Nullable Integer i) {
        open = i == null ? -1 : i;
    }

    public NewsBoardPayload board() {
        return board;
    }
}
