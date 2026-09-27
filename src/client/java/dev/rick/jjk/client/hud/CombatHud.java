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
    private record SlotInfo(AbilitySlot slot, String label, int color, boolean technique) {}

    private static final SlotInfo[] SLOTS = {
            new SlotInfo(AbilitySlot.SKILL_1, "Blue", 0xFF4F9BFF, true),
            new SlotInfo(AbilitySlot.SKILL_2, "Red", 0xFFFF3B30, true),
            new SlotInfo(AbilitySlot.SKILL_3, "Purple", 0xFFA24DFF, true),
            new SlotInfo(AbilitySlot.SKILL_4, "Warp", 0xFF9FE7FF, true),
            new SlotInfo(AbilitySlot.SKILL_5, "Infinity", 0xFFCFE8FF, true),
            new SlotInfo(AbilitySlot.ULTIMATE, "Domain", 0xFFFFFFFF, true),
            new SlotInfo(AbilitySlot.HEAVY, "Heavy", 0xFFFFC857, false),
            new SlotInfo(AbilitySlot.DASH, "Dash", 0xFFB0B0B0, false),
    };
    private static final RandomSource RNG = RandomSource.create();

    private CombatHud() {}

    public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        int w = g.guiWidth(), h = g.guiHeight();
        float partial = delta.getGameTimeDeltaPartialTick(false);
        CombatState state = Combat.stateOrNull(mc.player);

        overlays(g, mc, state, w, h, partial);
        if (!JJKConfig.get().client.showHud || !ClientState.hasCharacter()) return;
        Font font = mc.font;
        energyBar(g, font, w, h);
        abilityBar(g, font, mc, state, w, h);
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
        if (ClientState.flag(CasterSyncPayload.FLAG_NO_COST)) g.text(font, "NO COOLDOWN", x + 70, y - 10, 0xFFFFD060, true);
    }

    private static void abilityBar(GuiGraphicsExtractor g, Font font, Minecraft mc, CombatState state, int w, int h) {
        int size = 24, gap = 3;
        int total = SLOTS.length * (size + gap) - gap;
        int x0 = w - total - 8, y = h - size - 22;
        boolean techLocked = state != null && state.techniquesLocked();
        for (int i = 0; i < SLOTS.length; i++) {
            SlotInfo s = SLOTS[i];
            int x = x0 + i * (size + gap);
            boolean toggledOn = s.slot == AbilitySlot.SKILL_5 && ClientState.flag(CasterSyncPayload.FLAG_INFINITY);
            boolean casting = isCastingSlot(s.slot);
            g.fill(x - 1, y - 1, x + size + 1, y + size + 1, toggledOn || casting ? s.color : 0xFF202428);
            g.fill(x, y, x + size, y + size, 0xE0101418);
            g.fill(x, y, x + size, y + 3, s.color);
            g.centeredText(font, s.label.length() > 5 ? s.label.substring(0, 4) : s.label, x + size / 2, y + 7, 0xFFFFFFFF);
            String key = InputHandler.keyLabel(s.slot);
            g.centeredText(font, key, x + size / 2, y + size + 3, 0xFFB8C4D0);

            int cd = ClientState.cooldown(s.slot);
            int max = ClientState.maxCooldown(s.slot);
            int charges = ClientState.charges(s.slot);
            boolean usesCharges = s.slot == AbilitySlot.SKILL_4;
            boolean blocked = usesCharges ? charges <= 0 : cd > 0;
            if (cd > 0) {
                int covered = Math.round(size * Math.min(1f, cd / (float) max));
                g.fill(x, y + size - covered, x + size, y + size, blocked ? 0xB0000000 : 0x60000000);
                if (blocked) g.centeredText(font, String.format(cd >= 200 ? "%.0f" : "%.1f", cd / 20f), x + size / 2, y + 15, 0xFFFFE08A);
            }
            if (usesCharges) g.text(font, String.valueOf(charges), x + size - 6, y + size - 9, charges > 0 ? 0xFF9FE7FF : 0xFFFF6060, true);
            if (techLocked && s.technique && !(s.slot == AbilitySlot.SKILL_5 && toggledOn)) {
                g.fill(x, y, x + size, y + size, 0x90400000);
            }
        }
    }

    private static boolean isCastingSlot(AbilitySlot slot) {
        String c = ClientState.activeCast;
        return switch (slot) {
            case SKILL_1 -> c.equals("blue");
            case SKILL_2 -> c.equals("red");
            case SKILL_3 -> c.equals(HollowPurpleAbility.ID);
            case ULTIMATE -> c.equals("unlimited_void");
            case GUARD -> c.equals("guard");
            default -> false;
        };
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
            case "red" -> {
                frac = Mth.clamp(t / cfg.red.maxCharge, 0, 1);
                label = frac >= 1 ? "RED  — MAX" : t < cfg.red.minCharge ? "RED" : "RED  " + Math.round(frac * 100) + "%";
                color = 0xFFFF3B30;
            }
            case "hollow_purple" -> {
                int b = cfg.purple.blueFormTicks, r = b + cfg.purple.redFormTicks, f = r + cfg.purple.fusionTicks;
                frac = Mth.clamp(t / f, 0, 1);
                label = t < b ? "LAPSE: BLUE" : t < r ? "REVERSAL: RED" : t < f ? "HOLLOW TECHNIQUE" : "PURPLE — RELEASE";
                color = t < b ? 0xFF4F9BFF : t < r ? 0xFFFF3B30 : 0xFFA24DFF;
            }
            case "unlimited_void" -> {
                frac = Mth.clamp(t / cfg.domain.startup, 0, 1);
                label = "DOMAIN EXPANSION";
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
        int x = w - 70, y = h / 2 - 30;
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
        float fa = ScreenEffects.flashAlpha();
        if (fa > 0.01f) g.fill(0, 0, w, h, (Math.round(fa * 255) << 24) | ScreenEffects.flashColor());
    }

    private static String randomGlyphs() {
        StringBuilder sb = new StringBuilder();
        String pool = "01∞∆Ωλ∑∫≈≠±÷∂π";
        for (int i = 0; i < 6 + RNG.nextInt(10); i++) sb.append(pool.charAt(RNG.nextInt(pool.length())));
        return sb.toString();
    }
}
