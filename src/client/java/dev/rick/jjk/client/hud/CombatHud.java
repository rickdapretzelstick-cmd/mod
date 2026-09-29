package dev.rick.jjk.client.hud;

import dev.rick.jjk.client.ClientState;
import dev.rick.jjk.client.fx.ScreenEffects;
import dev.rick.jjk.client.input.InputHandler;
import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatState;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.net.CasterSyncPayload;
import dev.rick.jjk.gojo.HollowPurpleAbility;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import dev.rick.jjk.JJK;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** Heads-up display for combat: energy, abilities, casts, combos, statuses and full-screen feedback. */
public final class CombatHud {
    /** How each ability is presented: its pixel-art icon, display name and colour. Awakened moves get gold frames. */
    private record Meta(String name, int color, boolean ultimate, Identifier icon) {}

    private static Meta meta(String name, int color, boolean ultimate, String icon) {
        return new Meta(name, color, ultimate, JJK.id("textures/gui/ability/" + icon + ".png"));
    }

    /** Presentation per ability id. Infinity keeps its entry for when it returns to the moveset. */
    private static final java.util.Map<String, Meta> META = java.util.Map.ofEntries(
            java.util.Map.entry("blue", meta("Lapse Blue", 0xFF4F9BFF, false, "blue")),
            java.util.Map.entry("red", meta("Reversal Red", 0xFFFF3B30, false, "red")),
            java.util.Map.entry("rapid_punches", meta("Rapid Punches", 0xFFCFE8FF, false, "rapid_punches")),
            java.util.Map.entry("twofold_kick", meta("Twofold Kick", 0xFF8FC8FF, false, "twofold_kick")),
            java.util.Map.entry("infinity", meta("Infinity", 0xFFCFE8FF, false, "infinity")),
            java.util.Map.entry("teleport", meta("Limitless", 0xFF9FE7FF, false, "teleport")),
            java.util.Map.entry("awaken", meta("Six Eyes", 0xFFFFE08A, false, "awaken")),
            java.util.Map.entry("max_blue", meta("Lapse Blue MAX", 0xFF4F9BFF, true, "max_blue")),
            java.util.Map.entry("max_red", meta("Reversal Red MAX", 0xFFFF3B30, true, "max_red")),
            java.util.Map.entry("hollow_purple", meta("Hollow Purple", 0xFFA24DFF, true, "hollow_purple")),
            java.util.Map.entry("unlimited_void", meta("Infinite Void", 0xFFE8F0FF, true, "unlimited_void")),
            java.util.Map.entry("dash", meta("Dash", 0xFFB0B8C8, false, "dash")),
            java.util.Map.entry("guard", meta("Guard", 0xFFB0B8C8, false, "guard")),
            // Hakari: base kit, domain, and the Jackpot kit (gold frames like other awakened moves).
            java.util.Map.entry("reserve_balls", meta("Reserve Balls", 0xFFD8DEE8, false, "reserve_balls")),
            java.util.Map.entry("shutter_doors", meta("Shutter Doors", 0xFFB8C0CC, false, "shutter_doors")),
            java.util.Map.entry("rough_energy", meta("Rough Energy", 0xFF5CFFA8, false, "rough_energy")),
            java.util.Map.entry("fever_breaker", meta("Fever Breaker", 0xFFFF3FA0, false, "fever_breaker")),
            java.util.Map.entry("door_guard", meta("Door Guard", 0xFFE8B840, false, "door_guard")),
            java.util.Map.entry("idle_death_gamble", meta("Idle Death Gamble", 0xFFFF3FA0, false, "idle_death_gamble")),
            java.util.Map.entry("lucky_volley", meta("Lucky Volley", 0xFF5CFFA8, true, "lucky_volley")),
            java.util.Map.entry("lucky_rushdown", meta("Lucky Rushdown", 0xFF5CFFA8, true, "lucky_rushdown")),
            java.util.Map.entry("overwhelming_luck", meta("Overwhelming Luck", 0xFF5CFFA8, true, "overwhelming_luck")),
            java.util.Map.entry("energy_surge", meta("Energy Surge", 0xFF5CFFA8, true, "energy_surge")),
            java.util.Map.entry("rhythm", meta("Rhythm", 0xFFF0C040, true, "rhythm")));
    /** The technique column (empty slots are skipped), then the movement/defence pair under it. */
    private static final AbilitySlot[] TECHNIQUES = {AbilitySlot.SKILL_1, AbilitySlot.SKILL_2, AbilitySlot.SKILL_3, AbilitySlot.SKILL_4,
            AbilitySlot.SKILL_5, AbilitySlot.ULTIMATE};
    private static final AbilitySlot[] UTILITY = {AbilitySlot.GUARD, AbilitySlot.DASH};
    private static final RandomSource RNG = RandomSource.create();

    private CombatHud() {}

    public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int w = g.guiWidth(), h = g.guiHeight();
        float partial = delta.getGameTimeDeltaPartialTick(false);
        CombatState state = Combat.stateOrNull(mc.player);

        overlays(g, mc, state, w, h, partial);
        // The clash screen takes over while duelling.
        if (dev.rick.jjk.client.clash.ClashClient.playing() || dev.rick.jjk.client.cinematic.DomainCinematic.fullscreen()) return;
        if (!JJKConfig.get().client.showHud || !ClientState.hasCharacter()) return;
        Font font = mc.font;
        ceBar(g, font, mc, w, h);
        abilityHud(g, font, mc, state, w, h, partial);
        awakeningBar(g, font, mc, w, h);
        awakeningBanner(g, font, mc, w, h, partial);
        castBar(g, font, w, h, partial);
        if (JJKConfig.get().client.showComboCounter) comboCounter(g, font, mc, w, h);
        statusBanner(g, font, state, w, h);
    }

    // --- CE: a slim vertical bar hugging the left edge ---

    /** CE as drawn (eases toward the real value), the "just spent" ghost that trails behind it, and regen detection. */
    private static float shownCe = -1, trailCe = -1, lastEnergy = -1;
    private static long lastFrameNanos, regenUntilNanos;

    private static void ceBar(GuiGraphicsExtractor g, Font font, Minecraft mc, int w, int h) {
        float max = Math.max(1, ClientState.maxEnergy);
        float target = Mth.clamp(ClientState.energy / max, 0, 1);
        long nanos = System.nanoTime();
        float dt = lastFrameNanos == 0 ? 0 : Math.min(0.1f, (nanos - lastFrameNanos) / 1e9f);
        lastFrameNanos = nanos;
        if (shownCe < 0) shownCe = trailCe = target;
        shownCe += (target - shownCe) * (1 - (float) Math.exp(-dt * 14));
        // What was just spent lingers as a pale ghost, then drains away.
        if (shownCe >= trailCe) trailCe = shownCe;
        else trailCe += (shownCe - trailCe) * (1 - (float) Math.exp(-dt * 3.5f));
        if (lastEnergy >= 0 && ClientState.energy > lastEnergy + 0.001f) regenUntilNanos = nanos + 600_000_000L;
        lastEnergy = ClientState.energy;
        boolean regenerating = nanos < regenUntilNanos && target < 1;
        boolean noCost = ClientState.flag(CasterSyncPayload.FLAG_NO_COST);
        boolean empty = !noCost && ClientState.energy < 1;
        boolean low = !empty && target < 0.25f;
        boolean full = target >= 0.999f;
        float time = (mc.level.getGameTime() % 100000) + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);

        // Anchored to the left edge and centred vertically; its length follows the screen height.
        int bw = 5, bh = Mth.clamp(Math.round(h * 0.4f), 60, 160);
        int x = 7, y0 = h / 2 - bh / 2 - 8, y1 = y0 + bh;
        // Frame: a dark shell with a thin coloured rim (red and pulsing when empty).
        CharacterTheme theme = CharacterTheme.of(ClientState.character);
        int rim = empty ? pulse(0xFFFF4040, 0xFF501010, time, 0.25f) : low ? 0xFF803030 : full ? theme.accent() : theme.ceRim();
        g.fill(x - 2, y0 - 2, x + bw + 2, y1 + 2, 0xB0000000);
        outlineRect(g, x - 1, y0 - 1, x + bw + 1, y1 + 1, rim);
        g.fill(x, y0, x + bw, y1, 0xFF090C12);
        int top = y1 - Math.round(bh * shownCe);
        int ghostTop = y1 - Math.round(bh * trailCe);
        if (ghostTop < top) g.fill(x, ghostTop, x + bw, top, 0x90E8F0FF);
        if (top < y1) {
            int hi = low ? pulse(0xFFFF8A7A, 0xFFE05050, time, 0.18f) : theme.ceTop();
            int lo = low ? 0xFFA01818 : theme.ceBottom();
            g.fillGradient(x, top, x + bw, y1, hi, lo);
            // A soft inner highlight down the left edge gives the fill some depth.
            g.fill(x, top, x + 1, y1, low ? 0x40FFFFFF : 0x50FFFFFF);
            // The surface line.
            g.fill(x, top, x + bw, top + 1, 0xFFFFFFFF);
            if (regenerating) {
                // Regenerating: a faint glint travelling up through the fill.
                int span = y1 - top;
                if (span > 4) {
                    int gy = y1 - Math.round(((time * 2.2f) % span));
                    g.fill(x, gy - 1, x + bw, gy + 1, 0x60FFFFFF);
                }
            }
        }
        // Quarter marks.
        for (int i = 1; i < 4; i++) g.fill(x, y0 + bh * i / 4, x + bw, y0 + bh * i / 4 + 1, 0x70000000);
        if (full) g.fill(x - 1, y0 - 3, x + bw + 1, y0 - 2, (theme.accent() & 0xFFFFFF) | 0x80000000);

        // Label above, a small readout below.
        smallText(g, font, "CE", x + bw / 2f, y0 - 11, 0.75f, empty ? 0xFFFF6A6A : ClientState.awakened() && theme.bigTimer() ? 0xFFB8FFD8 : 0xFFB8D8FF, true);
        // Jackpot: cursed energy is unlimited.
        if (ClientState.awakened() && theme.bigTimer()) smallText(g, font, "∞", x + bw / 2f, y0 + bh / 2f - 3, 0.9f, 0xFF0A2A18, true);
        int ty = y1 + 5;
        boolean unlimited = ClientState.awakened() && theme.bigTimer();
        if (JJKConfig.get().client.showCeNumbers && !unlimited) {
            smallText(g, font, String.format(java.util.Locale.ROOT, "%,d", Math.round(ClientState.energy)), x - 2, ty, 0.6f,
                    empty ? 0xFFFF6A6A : low ? 0xFFFFA090 : 0xFFE0F0FF, false);
            smallText(g, font, String.format(java.util.Locale.ROOT, "/%,d", Math.round(ClientState.maxEnergy)), x - 2, ty + 7, 0.6f, 0xFF7888A0, false);
            ty += 16;
        }
        if (empty) {
            smallText(g, font, "EMPTY", x - 2, ty, 0.6f, pulse(0xFFFF5050, 0xFF803030, time, 0.25f), false);
            ty += 8;
        }
        if (noCost) {
            smallText(g, font, "NO COST", x - 2, ty, 0.6f, 0xFFFFD060, false);
            ty += 8;
        }
    }

    // --- Abilities: pixel-art icons down the right edge ---

    private static final long[] readyFlash = new long[AbilitySlot.values().length];
    private static final int[] lastCooldown = new int[AbilitySlot.values().length];

    private static void abilityHud(GuiGraphicsExtractor g, Font font, Minecraft mc, CombatState state, int w, int h, float partial) {
        java.util.List<AbilitySlot> techniques = new java.util.ArrayList<>();
        for (AbilitySlot s : TECHNIQUES) if (!ClientState.abilityIn(s).isEmpty()) techniques.add(s);
        int size = 22, small = 18, gap = 5, split = 9;
        int total = techniques.size() * (size + gap) - gap + split + UTILITY.length * (small + 3) - 3;
        if (total > h - 90) gap = 2;
        total = techniques.size() * (size + gap) - gap + split + UTILITY.length * (small + 3) - 3;
        int right = w - 6;
        int y = Math.max(20, h / 2 - total / 2 - 8);
        long now = mc.level.getGameTime();
        float since = now - ClientState.awakenedChangedTick + partial;
        boolean justChanged = ClientState.awakenedChangedTick != 0 && since < 70;
        for (int i = 0; i < techniques.size(); i++) {
            slot(g, font, mc, state, techniques.get(i), right - size, y, size, now, partial, justChanged ? since - i * 2.5f : -1, justChanged ? since : -1);
            y += size + gap;
        }
        y += split - gap;
        g.fill(right - size + 3, y - split / 2 - 1, right - 3, y - split / 2, 0x40FFFFFF);
        for (AbilitySlot s : UTILITY) {
            if (ClientState.abilityIn(s).isEmpty()) continue;
            slot(g, font, mc, state, s, right - small, y, small, now, partial, -1, -1);
            y += small + 3;
        }
    }

    /**
     * One ability: its icon in a frame whose look carries the state (ready, cooldown with timer, not enough CE or
     * Awakening, locked, casting, awakened), with the bound key in a badge beside it.
     *
     * @param flip  ticks into this slot's awakening transition (flips in with the new icon), or negative
     * @param named ticks since the moveset changed (names are shown briefly then), or negative
     */
    private static void slot(GuiGraphicsExtractor g, Font font, Minecraft mc, CombatState state, AbilitySlot slot, int x, int y, int size,
                             long now, float partial, float flip, float named) {
        String id = ClientState.abilityIn(slot);
        Meta m = META.getOrDefault(id, meta(id, 0xFF808080, false, "dash"));
        JJKConfig cfg = JJKConfig.get();
        boolean noCost = ClientState.flag(CasterSyncPayload.FLAG_NO_COST);
        int cd = ClientState.cooldown(slot), max = Math.max(1, ClientState.maxCooldown(slot));
        boolean cooling = cd > 0;
        if (lastCooldown[slot.ordinal()] > 0 && cd == 0) readyFlash[slot.ordinal()] = now;
        lastCooldown[slot.ordinal()] = cd;
        boolean lackCe = !noCost && ClientState.energy < ceCost(id, cfg);
        boolean lackMeter = meterCost(id) > 0 && ClientState.awakening < meterCost(id) - 0.01f;
        boolean technique = !id.equals("dash") && !id.equals("guard") && !id.equals("awaken") && !id.equals("door_guard");
        boolean locked = state != null && state.techniquesLocked() && technique;
        boolean casting = ClientState.activeCast.equals(id);
        boolean opensUp = id.equals("awaken") || id.equals("idle_death_gamble");
        boolean awakenReady = opensUp && !ClientState.awakened() && ClientState.awakening >= ClientState.awakeningMax;
        boolean counter = awakenReady && now < ClientState.counterUntilTick;
        boolean ready = !cooling && !lackCe && !lackMeter && !locked;
        float time = now + partial;

        var pose = g.pose();
        pose.pushMatrix();
        if (flip >= 0 && flip < 8) {
            // Awakening swapped the moveset: each icon flips over into its new form, one after another.
            float t = Mth.clamp(flip / 8f, 0, 1);
            pose.translate(x + size / 2f, y + size / 2f);
            pose.scale(Math.max(0.05f, t), 1f);
            pose.translate(-(x + size / 2f), -(y + size / 2f));
        } else if (flip < 0 && named >= 0) {
            pose.translate(0, 0);
        }
        int frame;
        if (counter) frame = pulse(0xFFFF6A6A, 0xFFFFFFFF, time, 0.35f);
        else if (casting) frame = pulse(0xFFFFFFFF, m.color, time, 0.4f);
        else if (awakenReady) frame = pulse(0xFFFFE08A, 0xFFFFFFFF, time, 0.2f);
        else if (locked) frame = 0xFF3A3A44;
        else if (lackCe || lackMeter) frame = 0xFF8A2A2A;
        else if (cooling) frame = 0xFF2A2F38;
        else if (m.ultimate) frame = shimmer(now, slot.ordinal());
        else frame = m.color;
        // Shadowed tile, rim, inner bevel.
        g.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0xA0000000);
        outlineRect(g, x - 1, y - 1, x + size + 1, y + size + 1, frame);
        g.fillGradient(x, y, x + size, y + size, m.ultimate ? 0xE82A1A40 : 0xE81A2030, m.ultimate ? 0xE8120A20 : 0xE80A0E16);
        g.fill(x, y, x + size, y + 1, 0x30FFFFFF);
        int tint = locked ? 0xFF46464E : cooling ? 0xFF585E6A : lackCe || lackMeter ? 0xFF9A6060 : 0xFFFFFFFF;
        int off = (size - 16) / 2;
        g.blit(RenderPipelines.GUI_TEXTURED, m.icon, x + off, y + off, 0, 0, 16, 16, 16, 16, 16, 16, tint);
        // Cooldown: a shade that recedes upward, with the time left.
        if (cooling) {
            int covered = Math.round(size * Math.min(1f, cd / (float) max));
            g.fill(x, y, x + size, y + covered, 0x88000000);
            smallText(g, font, String.format(cd >= 200 ? "%.0f" : "%.1f", cd / 20f), x + size / 2f, y + size / 2f - 3, 0.75f, 0xFFFFE08A, true);
        }
        // Not enough of a resource: a strip along the bottom in the colour of what's missing.
        if (!cooling && (lackCe || lackMeter)) {
            g.fill(x, y + size - 2, x + size, y + size, lackCe ? 0xFFFF4A4A : 0xFFB08AFF);
            smallText(g, font, lackCe ? "CE" : "AWK", x + size / 2f, y + size - 8, 0.5f, lackCe ? 0xFFFF8A8A : 0xFFD8C8FF, true);
        }
        if (locked) padlock(g, x + size - 7, y + size - 8);
        // Just came off cooldown: a quick white pop.
        long flash = now - readyFlash[slot.ordinal()];
        if (ready && flash < 6) g.fill(x, y, x + size, y + size, (Math.round((1 - (flash + partial) / 6f) * 150) << 24) | 0xFFFFFF);
        pose.popMatrix();

        // The key, in a badge to the left of the icon (whatever the player has bound it to).
        String key = InputHandler.keyLabel(slot);
        if (!key.isEmpty()) {
            int kw = Math.round(font.width(key) * 0.75f) + 6;
            int kx = x - kw - 3, ky = y + size / 2 - 5;
            g.fill(kx, ky, kx + kw, ky + 10, 0xB0000000);
            outlineRect(g, kx, ky, kx + kw, ky + 10, ready ? 0x60FFFFFF : 0x30FFFFFF);
            smallText(g, font, key, kx + kw / 2f, ky + 2, 0.75f, ready ? 0xFFE8EEF8 : 0xFF8890A0, true);
            // Right after Awakening starts or ends, name the new moves so the changed kit is obvious.
            if (named >= 0 && named < 70) {
                float a = named < 6 ? named / 6f : named > 55 ? 1 - (named - 55) / 15f : 1f;
                int ac = Math.round(Mth.clamp(a, 0, 1) * 255) << 24;
                String name = m.name.toUpperCase(java.util.Locale.ROOT);
                int nw = Math.round(font.width(name) * 0.75f);
                smallText(g, font, name, kx - 4 - nw / 2f, ky + 2, 0.75f, ac | (m.ultimate ? 0xFFE8A0 : 0xE8EEF8), true);
            }
        }
    }

    private static float ceCost(String id, JJKConfig cfg) {
        return switch (id) {
            case "blue" -> cfg.blue.cost;
            case "red" -> cfg.red.cost;
            case "teleport" -> cfg.teleport.cost;
            case "rapid_punches" -> cfg.gojo.punchesCost;
            case "twofold_kick" -> cfg.gojo.twofoldCost;
            case "hollow_purple" -> cfg.purple.cost;
            case "unlimited_void" -> cfg.domain.cost;
            case "reserve_balls" -> cfg.hakari.ballsCost;
            case "shutter_doors" -> cfg.hakari.shutterCost;
            case "rough_energy" -> cfg.hakari.roughCost;
            case "fever_breaker" -> cfg.hakari.feverCost;
            case "idle_death_gamble" -> cfg.hakari.domainCost;
            default -> 0;
        };
    }

    private static void padlock(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x + 1, y, x + 5, y + 1, 0xFFB8C0CC);
        g.fill(x + 1, y, x + 2, y + 3, 0xFFB8C0CC);
        g.fill(x + 4, y, x + 5, y + 3, 0xFFB8C0CC);
        g.fill(x, y + 3, x + 6, y + 7, 0xFFD8DEE8);
        g.fill(x + 2, y + 4, x + 4, y + 6, 0xFF404048);
    }

    private static void outlineRect(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int c) {
        g.fill(x0, y0, x1, y0 + 1, c);
        g.fill(x0, y1 - 1, x1, y1, c);
        g.fill(x0, y0, x0 + 1, y1, c);
        g.fill(x1 - 1, y0, x1, y1, c);
    }

    /** Text at a smaller scale; centred on x or left-aligned. */
    private static void smallText(GuiGraphicsExtractor g, Font font, String text, float x, float y, float scale, int color, boolean centered) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        if (centered) g.centeredText(font, text, 0, 0, color);
        else g.text(font, text, 0, 0, color, true);
        pose.popMatrix();
    }

    /** Blends between two colours on a slow sine, for pulsing states. */
    private static int pulse(int a, int b, float time, float speed) {
        float t = (float) Math.sin(time * speed) * 0.5f + 0.5f;
        int ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255, br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
        return 0xFF000000 | Math.round(ar + (br - ar) * t) << 16 | Math.round(ag + (bg - ag) * t) << 8 | Math.round(ab + (bb - ab) * t);
    }

    private static float meterCost(String id) {
        var a = JJKConfig.get().awakening;
        return switch (id) {
            case "idle_death_gamble" -> a.max;
            default -> 0;
        };
    }

    /** Gold/white frame that shimmers, marking awakened moves. */
    private static int shimmer(long now, int i) {
        float t = (float) Math.sin(now * 0.25 + i * 0.9) * 0.5f + 0.5f;
        int r = 255, gC = (int) (190 + 60 * t), b = (int) (70 + 170 * t);
        return 0xFF000000 | r << 16 | gC << 8 | b;
    }

    /** The Awakening meter: builds through combat, then becomes the timer once awakened. */
    /** Seconds of the awakened state left (the meter is its timer, drained at the character's own rate). */
    private static float awakenedSecondsLeft() {
        var ch = dev.rick.jjk.core.character.Characters.get(ClientState.character);
        float drain = ch != null ? ch.awakeningDrainPerSecond() : JJKConfig.get().awakening.drainPerSecond;
        return ClientState.awakening / Math.max(0.01f, drain);
    }

    /** The Awakening meter: builds through combat, then becomes the timer once awakened. Worded and coloured per character. */
    private static void awakeningBar(GuiGraphicsExtractor g, Font font, Minecraft mc, int w, int h) {
        CharacterTheme theme = CharacterTheme.of(ClientState.character);
        boolean awakened = ClientState.awakened();
        long now = mc.level.getGameTime();
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        if (awakened && theme.bigTimer()) {
            jackpotTimer(g, font, theme, w, h, now + partial);
            return;
        }
        int bw = 182, bh = 5, x = w / 2 - bw / 2, y = h - 52;
        float frac = Mth.clamp(ClientState.awakening / ClientState.awakeningMax, 0, 1);
        boolean ready = !awakened && frac >= 1f;
        g.fill(x - 2, y - 2, x + bw + 2, y + bh + 2, ready || awakened ? shimmer(now, 0) : 0xFF101018);
        g.fill(x - 1, y - 1, x + bw + 1, y + bh + 1, 0xFF05050A);
        int fill = Math.round(bw * frac);
        if (awakened) {
            g.fillGradient(x, y, x + fill, y + bh, theme.timerFrom(), theme.timerTo());
        } else {
            g.fillGradient(x, y, x + fill, y + bh, theme.meterFrom(), theme.meterTo());
        }
        for (int i = 1; i < 4; i++) g.fill(x + bw * i / 4, y, x + bw * i / 4 + 1, y + bh, 0x50000000);
        String label;
        int color;
        String key = "  [" + InputHandler.keyLabel(AbilitySlot.ULTIMATE) + "]";
        if (awakened) {
            label = String.format("%s  %.0fs", theme.awakenedName(), awakenedSecondsLeft());
            color = 0xFFEAF8FF;
        } else if (ready && now < ClientState.counterUntilTick) {
            // Someone nearby is opening a domain: the same button answers it.
            label = "COUNTER " + ClientState.counterDomain.toUpperCase(java.util.Locale.ROOT) + "!" + key;
            color = (now / 3) % 2 == 0 ? 0xFFFF6A6A : 0xFFFFFFFF;
            float left = (ClientState.counterUntilTick - now) / (float) Math.max(1, ClientState.counterWindow);
            g.fill(x, y + bh + 3, x + Math.round(bw * left), y + bh + 5, 0xFFFF6A6A);
        } else if (ready) {
            label = theme.readyText() + key;
            color = (now / 6) % 2 == 0 ? 0xFFFFE08A : 0xFFFFFFFF;
        } else if (ClientState.flag(CasterSyncPayload.FLAG_REFILL_LOCKED)) {
            label = theme.meterName() + "  (recovering)";
            color = 0xFF8088A0;
        } else {
            label = theme.meterName() + "  " + Math.round(frac * 100) + "%";
            color = 0xFFC8B8FF;
        }
        g.centeredText(font, label, w / 2, y - 10, color);
    }

    /**
     * The Jackpot timer: a major element at the top of the screen while it runs — big lettering that cycles like
     * casino lights, the time left, and a wide bar that drains (flashing red in the last few seconds).
     */
    private static void jackpotTimer(GuiGraphicsExtractor g, Font font, CharacterTheme theme, int w, int h, float time) {
        float secs = awakenedSecondsLeft();
        float frac = Mth.clamp(ClientState.awakening / ClientState.awakeningMax, 0, 1);
        boolean ending = secs < 6;
        int bw = Math.min(260, w - 120), bh = 7, x = w / 2 - bw / 2, y = 26;
        g.fill(x - 3, y - 3, x + bw + 3, y + bh + 3, 0xC0000000);
        int frame = ending ? pulse(0xFFFF4040, 0xFFFFFFFF, time, 0.6f) : rainbow(time, 0);
        outlineRect(g, x - 2, y - 2, x + bw + 2, y + bh + 2, frame);
        int fill = Math.round(bw * frac);
        g.fillGradient(x, y, x + fill, y + bh, theme.timerFrom(), theme.timerTo());
        // Lights chasing along the bar.
        for (int i = 0; i < bw; i += 12) {
            boolean on = ((int) (time / 2) + i / 12) % 3 == 0;
            if (i < fill && on) g.fill(x + i, y + 1, x + i + 3, y + bh - 1, 0x90FFFFFF);
        }
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(w / 2f, 6);
        float pop = 2.0f + 0.08f * Mth.sin(time * 0.5f);
        pose.scale(pop, pop);
        String word = theme.awakenedName();
        int tw = font.width(word);
        // Each letter its own casino light.
        int cx = -tw / 2;
        for (int i = 0; i < word.length(); i++) {
            String ch = String.valueOf(word.charAt(i));
            g.text(font, ch, cx, 0, rainbow(time, i), true);
            cx += font.width(ch);
        }
        pose.popMatrix();
        String t = String.format("%d:%02d", (int) secs / 60, (int) secs % 60);
        g.centeredText(font, t, w / 2, y + bh + 5, ending ? pulse(0xFFFF6060, 0xFFFFFFFF, time, 0.6f) : 0xFFE8FFF0);
    }

    /** Casino lights: a hue that walks through pink, gold and green. */
    private static int rainbow(float time, int i) {
        int[] cols = {0xFFFF3FA0, 0xFFF0C040, 0xFF5CFFA8, 0xFFFFFFFF, 0xFF7FD4FF};
        return cols[(int) Math.floorMod((int) (time / 3) + i, cols.length)];
    }

    /** Big announcement when entering Awakening, and a quieter one when it ends. */
    private static void awakeningBanner(GuiGraphicsExtractor g, Font font, Minecraft mc, int w, int h, float partial) {
        float age = mc.level.getGameTime() - ClientState.awakenedChangedTick + partial;
        if (ClientState.awakenedChangedTick == 0 || age > 50) return;
        float alpha = age < 5 ? age / 5 : age > 35 ? 1 - (age - 35) / 15 : 1;
        int a = Math.round(Mth.clamp(alpha, 0, 1) * 255) << 24;
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(w / 2f, h / 2f - 60);
        if (ClientState.awakened()) {
            float sc = 3.2f + Math.max(0, 1 - age / 6f);
            pose.scale(sc, sc);
            g.centeredText(font, CharacterTheme.of(ClientState.character).awakenedName(), 0, 0, a | 0xEAF8FF);
        } else {
            pose.scale(1.5f, 1.5f);
            String ended = CharacterTheme.of(ClientState.character).awakenedName();
            g.centeredText(font, ended.charAt(0) + ended.substring(1).toLowerCase(java.util.Locale.ROOT) + " ended", 0, 0, a | 0xA0A8C0);
        }
        pose.popMatrix();
    }

    private static void castBar(GuiGraphicsExtractor g, Font font, int w, int h, float partial) {
        String cast = ClientState.activeCast;
        if (cast.isEmpty() || cast.equals("guard")) return;
        JJKConfig cfg = JJKConfig.get();
        float t = ClientState.castTicks + partial;
        String label;
        float frac;
        int color;
        switch (cast) {
            case "red", "max_red" -> {
                boolean max = cast.equals("max_red");
                frac = Mth.clamp(t / (max ? cfg.maxRed.charge : cfg.red.windup), 0, 1);
                label = max ? "REVERSAL RED MAX" : "REVERSAL RED";
                color = 0xFFFF3B30;
            }
            case "max_blue" -> {
                frac = Mth.clamp(t / cfg.maxBlue.startup, 0, 1);
                label = "LAPSE BLUE MAX";
                color = 0xFF4F9BFF;
            }
            case "hollow_purple" -> {
                int b = cfg.purple.blueFormTicks, r = b + cfg.purple.redFormTicks, f = r + cfg.purple.fusionTicks;
                frac = Mth.clamp(t / f, 0, 1);
                label = t < b ? "LAPSE: BLUE" : t < r ? "REVERSAL: RED" : "HOLLOW PURPLE";
                color = t < b ? 0xFF4F9BFF : t < r ? 0xFFFF3B30 : 0xFFA24DFF;
            }
            case "unlimited_void" -> {
                frac = Mth.clamp(t / cfg.domain.startup, 0, 1);
                label = "DOMAIN EXPANSION: INFINITE VOID";
                color = 0xFFFFFFFF;
            }
            case "idle_death_gamble" -> {
                frac = Mth.clamp(t / cfg.hakari.domainStartup, 0, 1);
                label = "DOMAIN EXPANSION: IDLE DEATH GAMBLE";
                color = 0xFFFF7FC0;
            }
            case "rough_energy" -> {
                frac = Mth.clamp(t / cfg.hakari.roughWindup, 0, 1);
                label = "ROUGH ENERGY";
                color = 0xFF5CFFA8;
            }
            case "blue" -> {
                frac = Mth.clamp(t / cfg.gojo.blueWindup, 0, 1);
                label = "LAPSE BLUE";
                color = 0xFF4F9BFF;
            }
            default -> {
                return;
            }
        }
        int bw = 90, x = w / 2 - bw / 2, y = h / 2 + 22;
        g.fill(x - 1, y - 1, x + bw + 1, y + 4, 0xA0000000);
        g.fill(x, y, x + Math.round(bw * frac), y + 3, color);
        g.centeredText(font, label, w / 2, y + 7, color);
    }

    private static void comboCounter(GuiGraphicsExtractor g, Font font, Minecraft mc, int w, int h) {
        if (ClientState.comboCount < 2) return;
        long age = mc.level.getGameTime() - ClientState.comboTime;
        int window = JJKConfig.get().general.comboWindow;
        if (age > window + 20) return;
        float alpha = age > window ? 1f - (age - window) / 20f : 1f;
        int a = Math.round(alpha * 255) << 24;
        int x = 26, y = h / 2 - 30;
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        float pop = Math.max(0, 1f - age / 4f) * 0.35f;
        pose.scale(2.2f + pop, 2.2f + pop);
        g.text(font, String.valueOf(ClientState.comboCount), 0, 0, a | 0xFFFFFF, true);
        pose.popMatrix();
        g.text(font, "HITS", x + 2 + font.width(String.valueOf(ClientState.comboCount)) * 2 + 6, y + 8, a | 0xFFD060, true);
        g.text(font, String.format("%.1f dmg", ClientState.comboDamage), x, y + 22, a | 0xC0C8D0, true);
    }

    private static void statusBanner(GuiGraphicsExtractor g, Font font, CombatState state, int w, int h) {
        if (state == null) return;
        String text = null;
        int color = 0xFFFFFFFF;
        if (state.has(CombatStatus.OVERLOAD)) {
            text = "INFORMATION OVERLOAD";
        } else if (state.has(CombatStatus.GUARD_BROKEN)) {
            text = "GUARD BROKEN";
            color = 0xFFFF8060;
        } else if (state.isDowned()) {
            text = "KNOCKED DOWN";
            color = 0xFFFFB060;
        } else if (state.has(CombatStatus.HITSTUN)) {
            text = "STUNNED";
            color = 0xFFFF6060;
        } else if (state.has(CombatStatus.BURNOUT)) {
            text = String.format("TECHNIQUE BURNOUT %.0fs", Math.ceil(state.get(CombatStatus.BURNOUT) / 20f));
            color = 0xFFFF9050;
        }
        if (text != null) g.centeredText(font, text, w / 2, h / 2 - 40, color);
    }

    private static void overlays(GuiGraphicsExtractor g, Minecraft mc, CombatState state, int w, int h, float partial) {
        JJKConfig.Client cfg = JJKConfig.get().client;
        boolean insideDomain = false;
        if (mc.player != null) {
            for (ClientState.Domain d : ClientState.DOMAINS.values()) {
                if (d.center != null && mc.player.position().distanceTo(d.center) < d.radius) insideDomain = true;
            }
        }
        if (insideDomain) {
            // Vignette: the edges of vision sink into the void.
            g.fillGradient(0, 0, w, h / 4, 0x90000008, 0x00000000);
            g.fillGradient(0, h - h / 4, w, h, 0x00000000, 0x90000008);
        }
        if (ClientState.awakened()) {
            // Awakened: a faint cold glow at the edges of vision.
            int pulse = 0x18 + (int) (0x10 * Math.sin((mc.level.getGameTime() + partial) * 0.15));
            g.fillGradient(0, 0, w, h / 6, (pulse << 24) | 0x9FD8FF, 0x009FD8FF);
            g.fillGradient(0, h - h / 6, w, h, 0x009FD8FF, (pulse << 24) | 0x9FD8FF);
        }
        if (cfg.overloadOverlay && state != null && state.has(CombatStatus.OVERLOAD)) {
            // Too much information: flickering white noise and streaks across the screen.
            int intensity = 40 + RNG.nextInt(60);
            g.fill(0, 0, w, h, (intensity << 24) | 0xFFFFFF);
            for (int i = 0; i < 40; i++) {
                int y = RNG.nextInt(h), x = RNG.nextInt(w), len = 20 + RNG.nextInt(w / 2);
                g.fill(x, y, Math.min(w, x + len), y + 1, ((80 + RNG.nextInt(150)) << 24) | 0xFFFFFF);
            }
            for (int i = 0; i < 12; i++) {
                g.text(mc.font, randomGlyphs(), RNG.nextInt(w), RNG.nextInt(h), 0x80FFFFFF, false);
            }
        }
        int impact = ScreenEffects.impactColor();
        float fa = ScreenEffects.flashAlpha();
        if (dev.rick.jjk.client.clash.ClashFocus.active()) {
            // Mid-clash the lanes must stay readable: flashes glow in from the edges of the screen instead.
            if (impact != 0) edgeFlash(g, w, h, impact & 0xFFFFFF, (impact >>> 24) / 255f);
            if (fa > 0.01f) edgeFlash(g, w, h, ScreenEffects.flashColor(), fa);
            return;
        }
        if (impact != 0) g.fill(0, 0, w, h, impact);
        if (fa > 0.01f) g.fill(0, 0, w, h, (Math.round(fa * 255) << 24) | ScreenEffects.flashColor());
    }

    /** A flash confined to a frame around the edges of the screen, fading to nothing toward the middle. */
    private static void edgeFlash(GuiGraphicsExtractor g, int w, int h, int rgb, float alpha) {
        int a = Math.round(Math.min(1, alpha * 1.2f) * 255) << 24;
        int band = h / 7, side = w / 9;
        g.fillGradient(0, 0, w, band, a | rgb, rgb);
        g.fillGradient(0, h - band, w, h, rgb, a | rgb);
        // Sides, as strips (fillGradient runs top to bottom).
        for (int i = 0; i < 8; i++) {
            int sa = Math.round((a >>> 24) * (1 - i / 8f)) << 24;
            g.fill(side * i / 8, band, side * (i + 1) / 8, h - band, sa | rgb);
            g.fill(w - side * (i + 1) / 8, band, w - side * i / 8, h - band, sa | rgb);
        }
    }

    private static String randomGlyphs() {
        StringBuilder sb = new StringBuilder();
        String pool = "01∞∆Ωλ∑∫≈≠±÷∂π";
        for (int i = 0; i < 6 + RNG.nextInt(10); i++) sb.append(pool.charAt(RNG.nextInt(pool.length())));
        return sb.toString();
    }
}
