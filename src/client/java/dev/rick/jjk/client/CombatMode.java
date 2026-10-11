package dev.rick.jjk.client;

import dev.rick.jjk.client.input.InputHandler;
import dev.rick.jjk.config.JJKConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Combat mode versus Vanilla Minecraft mode. In vanilla mode the mod stays out of the way completely: no custom HUD, and
 * none of its ability keys, melee or clash inputs are sent, so building and mining can't set off a technique. The choice
 * is stored in the client config ({@code client.combatMode}) and survives respawns, rejoins and restarts.
 */
public final class CombatMode {
    private CombatMode() {}

    public static boolean enabled() {
        return JJKConfig.get().client.combatMode;
    }

    public static void set(boolean on) {
        JJKConfig.Client cfg = JJKConfig.get().client;
        if (cfg.combatMode == on) return;
        cfg.combatMode = on;
        JJKConfig.save();
        Minecraft mc = Minecraft.getInstance();
        // Let go of anything being held so a charge doesn't keep going after the switch.
        InputHandler.releaseAll(mc.player);
        // Off, a drawn cursed tool is put away (and comes back out when it's on again).
        sync();
        if (mc.player != null) {
            mc.player.sendOverlayMessage(on
                    ? Component.literal("JJK Combat Mode: ON").withStyle(ChatFormatting.AQUA)
                    : Component.literal("Vanilla Minecraft Mode: JJK abilities and HUD off").withStyle(ChatFormatting.GRAY));
        }
    }

    /** Tells the server whether Combat Mode is on (a drawn cursed tool is holstered while it's off). */
    public static void sync() {
        if (net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(dev.rick.jjk.core.net.CombatModePayload.TYPE)) {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new dev.rick.jjk.core.net.CombatModePayload(enabled()));
        }
    }

    public static void toggle() {
        set(!enabled());
    }
}
