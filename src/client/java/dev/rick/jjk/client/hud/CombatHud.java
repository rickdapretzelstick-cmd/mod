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
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** Heads-up display for combat: energy, abilities, casts, combos, statuses and full-screen feedback. */
public final class CombatHud {
    private record Meta(String label, int color, boolean ultimate) {}

    /** Display info per ability id. Awakened moves are flagged "ultimate" and drawn with gold, shimmering frames. */
    private static final java.util.Map<String, Meta> META = java.util.Map.ofEntries(
            java.util.Map.entry("blue", new Meta("Blue", 0xFF4F9BFF, false)),
            java.util.Map.entry("red", new Meta("Red", 0xFFFF3B30, false)),
            java.util.Map.entry("infinity", new Meta("Inf", 0xFFCFE8FF, false)),
            java.util.Map.entry("teleport", new Meta("Warp", 0xFF9FE7FF, false)),
            java.util.Map.entry("awaken", new Meta("AWK", 0xFFFFE08A, false)),
            java.util.Map.entry("max_blue", new Meta("MAX", 0xFF4F9BFF, true)),
            java.util.Map.entry("max_red", new Meta("MAX", 0xFFFF3B30, true)),
            java.util.Map.entry("hollow_purple", new Meta("Pur", 0xFFA24DFF, true)),
            java.util.Map.entry("unlimited_void", new Meta("Void", 0xFFFFFFFF, true)),
            java.util.Map.entry("dash", new Meta("Dash", 0xFFB0B0B0, false)),
            java.util.Map.entry("heavy", new Meta("Hvy", 0xFFFFC857, false)));
    private static final AbilitySlot[] SHOWN = {AbilitySlot.SKILL_1, AbilitySlot.SKILL_2, AbilitySlot.SKILL_3, AbilitySlot.SKILL_4,
            AbilitySlot.ULTIMATE, AbilitySlot.HEAVY, AbilitySlot.DASH};
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
        energyBar(g, font, w, h);
        abilityBar(g, font, mc, state, w, h);
        awakeningBar(g, font, mc, w, h);
        awakeningBanner(g, font, mc, w, h, partial);
        castBar(g, font, w, h, partial);
        if (JJKConfig.get().client.showComboCounter) comboCounter(g, font, mc, w, h);
        statusBanner(g, font, state, w, h);
    }

    private static void energyBar(GuiGraphicsExtractor g, Font font, int w, int h) {
        int x = 8, y = h - 30, bw = 112, bh = 6;
        float frac = ClientState.maxEnergy > 0 ? Mth.clamp(ClientState.energy / ClientState.maxEnergy, 0, 1) : 0;
        g.fill(x - 1, y - 1, x + bw + 1, y + bh + 1, 0xC0000000);
        int fillW = Math.round(bw * frac);
        boolean low = frac < 0.2f;
        g.fillGradient(x, y, x + fillW, y + bh, low ? 0xFFFF6060 : 0xFF6FC3FF, low ? 0xFFB02020 : 0xFF2A6BFF);
        for (int i = 1; i < 10; i++) g.fill(x + bw * i / 10, y, x + bw * i / 10 + 1, y + bh, 0x40000000);
        g.text(font, String.format("CE %d / %d", Math.round(ClientState.energy), Math.round(ClientState.maxEnergy)), x, y - 10, 0xFFE0F0FF, true);
        String stance = InputHandler.inStance() ? "◆ COMBAT STANCE" : ClientState.stanceEnabled ? "◇ stance (empty hand)" : "◇ stance off";
        g.text(font, stance, x, y + 9, InputHandler.inStance() ? 0xFF9FE7FF : 0xFF808890, true);
        if (ClientState.flag(CasterSyncPayload.FLAG_NO_COST)) g.text(font, "NO COOLDOWN", x, y - 20, 0xFFFFD060, true);
    }

    private static void abilityBar(GuiGraphicsExtractor g, Font font, Minecraft mc, CombatState state, int w, int h) {
        int size = 18, gap = 10;
        int total = SHOWN.length * (size + gap) - gap;
        int x = w - size - 6, y0 = Math.max(4, h / 2 - total / 2);
        boolean techLocked = state != null && state.techniquesLocked();
        long now = mc.level.getGameTime();
        for (int i = 0; i < SHOWN.length; i++) {
            AbilitySlot slot = SHOWN[i];
            String id = slot == AbilitySlot.HEAVY ? "heavy" : ClientState.abilityIn(slot);
            Meta m = META.getOrDefault(id, new Meta(id.isEmpty() ? "-" : id.substring(0, Math.min(3, id.length())), 0xFF808080, false));
            int y = y0 + i * (size + gap);
            boolean toggledOn = id.equals("infinity") && ClientState.flag(CasterSyncPayload.FLAG_INFINITY);
            boolean casting = ClientState.activeCast.equals(id);
            boolean awakenReady = id.equals("awaken") && ClientState.awakening >= ClientState.awakeningMax;
            int frame = m.ultimate ? shimmer(now, i) : toggledOn || casting ? m.color : awakenReady && (now / 5) % 2 == 0 ? 0xFFFFE08A : 0xFF202428;
            g.fill(x - 1, y - 1, x + size + 1, y + size + 1, frame);
            g.fill(x, y, x + size, y + size, m.ultimate ? 0xE0241438 : 0xE0101418);
            g.fill(x, y, x + size, y + 3, m.color);
            g.centeredText(font, m.label, x + size / 2, y + 6, m.ultimate ? 0xFFFFF0C0 : 0xFFFFFFFF);
            g.centeredText(font, InputHandler.keyLabel(slot), x + size / 2, y + size + 1, 0xFFB8C4D0);
            int cd = ClientState.cooldown(slot);
            int max = ClientState.maxCooldown(slot);
            boolean usesCharges = id.equals("teleport");
            int charges = ClientState.charges(slot);
            boolean blocked = usesCharges ? charges <= 0 : cd > 0;
            if (cd > 0) {
                int covered = Math.round(size * Math.min(1f, cd / (float) max));
                g.fill(x, y + size - covered, x + size, y + size, blocked ? 0xB0000000 : 0x60000000);
                if (blocked) g.centeredText(font, String.format(cd >= 200 ? "%.0f" : "%.1f", cd / 20f), x + size / 2, y + 11, 0xFFFFE08A);
            }
            if (usesCharges) g.text(font, String.valueOf(charges), x + size - 5, y + size - 8, charges > 0 ? 0xFF9FE7FF : 0xFFFF6060, true);
            float cost = meterCost(id);
            if (cost > 0 && ClientState.awakening < cost) g.fill(x, y, x + size, y + size, 0x90000000);
            boolean technique = !id.equals("dash") && !id.equals("heavy") && !id.equals("awaken");
            if (techLocked && technique && !toggledOn) g.fill(x, y, x + size, y + size, 0x90400000);
        }
    }

    private static float meterCost(String id) {
        var a = JJKConfig.get().awakening;
        return switch (id) {
            case "max_blue" -> a.maxBlueCost;
            case "max_red" -> a.maxRedCost;
            case "hollow_purple" -> a.hollowPurpleCost;
            case "unlimited_void" -> a.infiniteVoidCost;
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
    private static void awakeningBar(GuiGraphicsExtractor g, Font font, Minecraft mc, int w, int h) {
        int bw = 182, bh = 5, x = w / 2 - bw / 2, y = h - 52;
        long now = mc.level.getGameTime();
        float frac = Mth.clamp(ClientState.awakening / ClientState.awakeningMax, 0, 1);
        boolean awakened = ClientState.awakened();
        boolean ready = !awakened && frac >= 1f;
        g.fill(x - 2, y - 2, x + bw + 2, y + bh + 2, ready || awakened ? shimmer(now, 0) : 0xFF101018);
        g.fill(x - 1, y - 1, x + bw + 1, y + bh + 1, 0xFF05050A);
        int fill = Math.round(bw * frac);
        if (awakened) {
            g.fillGradient(x, y, x + fill, y + bh, 0xFFFFFFFF, 0xFF7FD4FF);
        } else {
            g.fillGradient(x, y, x + fill, y + bh, 0xFF8A5CFF, 0xFF3E7BFF);
        }
        for (int i = 1; i < 4; i++) g.fill(x + bw * i / 4, y, x + bw * i / 4 + 1, y + bh, 0x50000000);
        String label;
        int color;
        if (awakened) {
            float secs = ClientState.awakening / Math.max(0.01f, JJKConfig.get().awakening.drainPerSecond);
            label = String.format("AWAKENED  %.0fs", secs);
            color = 0xFFEAF8FF;
        } else if (ready && now < ClientState.counterUntilTick) {
            // Someone nearby is opening a domain: the same button answers it.
            label = "COUNTER " + ClientState.counterDomain.toUpperCase(java.util.Locale.ROOT) + "!  [" + InputHandler.keyLabel(AbilitySlot.ULTIMATE) + "]";
            color = (now / 3) % 2 == 0 ? 0xFFFF6A6A : 0xFFFFFFFF;
            float left = (ClientState.counterUntilTick - now) / (float) Math.max(1, ClientState.counterWindow);
            g.fill(x, y + bh + 3, x + Math.round(bw * left), y + bh + 5, 0xFFFF6A6A);
        } else if (ready) {
            label = "AWAKENING READY  [" + InputHandler.keyLabel(AbilitySlot.ULTIMATE) + "]";
            color = (now / 6) % 2 == 0 ? 0xFFFFE08A : 0xFFFFFFFF;
        } else if (ClientState.flag(CasterSyncPayload.FLAG_REFILL_LOCKED)) {
            label = "AWAKENING  (recovering)";
            color = 0xFF8088A0;
        } else {
            label = "AWAKENING  " + Math.round(frac * 100) + "%";
            color = 0xFFC8B8FF;
        }
        g.centeredText(font, label, w / 2, y - 10, color);
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
            g.centeredText(font, "AWAKENED", 0, 0, a | 0xEAF8FF);
        } else {
            pose.scale(1.5f, 1.5f);
            g.centeredText(font, "Awakening ended", 0, 0, a | 0xA0A8C0);
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
                int maxC = max ? cfg.maxRed.maxCharge : cfg.red.maxCharge, minC = max ? cfg.maxRed.minCharge : cfg.red.minCharge;
                String name = max ? "REVERSAL RED: MAX" : "RED";
                frac = Mth.clamp(t / maxC, 0, 1);
                label = frac >= 1 ? name + "  — FULL" : t < minC ? name : name + "  " + Math.round(frac * 100) + "%";
                color = 0xFFFF3B30;
            }
            case "max_blue" -> {
                frac = Mth.clamp(t / cfg.maxBlue.startup, 0, 1);
                label = "LAPSE BLUE: MAX";
                color = 0xFF4F9BFF;
            }
            case "awaken" -> {
                frac = Mth.clamp(t / cfg.awakening.transitionTicks, 0, 1);
                label = "AWAKENING";
                color = 0xFFEAF8FF;
            }
            case "hollow_purple" -> {
                int b = cfg.purple.blueFormTicks, r = b + cfg.purple.redFormTicks, f = r + cfg.purple.fusionTicks;
                frac = Mth.clamp(t / f, 0, 1);
                label = t < b ? "LAPSE: BLUE" : t < r ? "REVERSAL: RED" : t < f ? "HOLLOW TECHNIQUE" : "PURPLE — RELEASE";
                color = t < b ? 0xFF4F9BFF : t < r ? 0xFFFF3B30 : 0xFFA24DFF;
            }
            case "unlimited_void" -> {
                frac = Mth.clamp(t / cfg.domain.startup, 0, 1);
                label = "DOMAIN EXPANSION: INFINITE VOID";
                color = 0xFFFFFFFF;
            }
            case "blue" -> {
                frac = 1;
                label = "BLUE";
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
        int x = 10, y = h / 2 - 30;
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
