package dev.rick.jjk.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import dev.rick.jjk.JJK;
import dev.rick.jjk.client.ClientState;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.Targeting;
import dev.rick.jjk.core.net.AbilityInputPayload;
import dev.rick.jjk.core.net.MeleeInputPayload;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;
import java.util.Map;

/**
 * Keybinds and input translation. Ability keys send press and release (hold abilities charge/steer while held).
 * In combat stance (character assigned, empty main hand) left click becomes this mod's melee:
 * tap for a light attack, keep holding to charge a heavy, release to throw it.
 */
public final class InputHandler {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(JJK.id("gojo"));
    private static final Map<AbilitySlot, KeyMapping> KEYS = new EnumMap<>(AbilitySlot.class);
    private static final Map<AbilitySlot, Boolean> DOWN = new EnumMap<>(AbilitySlot.class);
    private static KeyMapping stanceKey;
    /** Switches between combat mode and Vanilla Minecraft mode. Unbound by default so it can't be hit mid-fight. */
    private static KeyMapping modeKey;
    /** Opens the character select screen. */
    private static KeyMapping characterKey;

    private static final int HEAVY_HOLD_TICKS = 7;
    private static boolean attackHeld;
    private static int attackHeldTicks;
    private static boolean heavyStarted;

    private InputHandler() {}

    public static void init() {
        bind(AbilitySlot.SKILL_1, "skill_1", InputConstants.KEY_Z);
        bind(AbilitySlot.SKILL_2, "skill_2", InputConstants.KEY_X);
        bind(AbilitySlot.SKILL_3, "skill_3", InputConstants.KEY_C);
        bind(AbilitySlot.SKILL_4, "skill_4", InputConstants.KEY_V);
        bind(AbilitySlot.SKILL_5, "skill_5", InputConstants.KEY_B);
        bind(AbilitySlot.ULTIMATE, "ultimate", InputConstants.KEY_G);
        bind(AbilitySlot.GUARD, "guard", InputConstants.KEY_R);
        bind(AbilitySlot.DASH, "dash", InputConstants.KEY_LALT);
        stanceKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jjk.stance", InputConstants.Type.KEYBOARD, InputConstants.KEY_GRAVE, CATEGORY));
        characterKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jjk.character_menu", InputConstants.Type.KEYBOARD, InputConstants.KEY_K, CATEGORY));
        modeKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jjk.combat_mode", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY));
    }

    private static void bind(AbilitySlot slot, String name, int key) {
        KEYS.put(slot, KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jjk." + name, InputConstants.Type.KEYBOARD, key, CATEGORY)));
        DOWN.put(slot, false);
    }

    /** Called every client tick. */
    public static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) return;
        while (modeKey.consumeClick()) dev.rick.jjk.client.CombatMode.toggle();
        while (characterKey.consumeClick()) {
            if (mc.gui.screen() == null) mc.gui.setScreen(new dev.rick.jjk.client.hud.CharacterSelectScreen(null));
        }
        if (!dev.rick.jjk.client.CombatMode.enabled()) {
            // Vanilla Minecraft mode: none of this mod's keys do anything. Swallow their presses so nothing fires later.
            while (stanceKey.consumeClick()) {}
            for (KeyMapping k : KEYS.values()) while (k.consumeClick()) {}
            attackHeld = false;
            heavyStarted = false;
            return;
        }
        while (stanceKey.consumeClick()) ClientState.stanceEnabled = !ClientState.stanceEnabled;
        boolean canAct = mc.gui.screen() == null && ClientState.hasCharacter() && player.isAlive();
        for (Map.Entry<AbilitySlot, KeyMapping> e : KEYS.entrySet()) {
            AbilitySlot slot = e.getKey();
            boolean down = canAct && e.getValue().isDown();
            boolean was = DOWN.get(slot);
            boolean clicked = false;
            while (e.getValue().consumeClick()) clicked = true;
            // Hakari's Rhythm: the Special key is a beat button while the dance runs.
            if (slot == AbilitySlot.SKILL_5 && dev.rick.jjk.client.hud.RhythmClient.active()) {
                if ((down && !was) || (clicked && !down)) dev.rick.jjk.client.hud.RhythmClient.press();
                DOWN.put(slot, down);
                continue;
            }
            if (down != was) {
                DOWN.put(slot, down);
                sendAbility(player, slot, down);
            } else if (clicked && !down && canAct) {
                // A tap shorter than one tick: pressed and released between polls. Still counts.
                sendAbility(player, slot, true);
                sendAbility(player, slot, false);
            }
        }
        tickAttack(mc, player);
    }

    private static void sendAbility(LocalPlayer player, AbilitySlot slot, boolean pressed) {
        Vec2 move = player.input.getMoveVector();
        int hint = pressed ? targetHint(player, slot == AbilitySlot.SKILL_4 ? 32 : 20) : -1;
        ClientPlayNetworking.send(new AbilityInputPayload(slot.ordinal(), pressed, move.y, move.x, hint));
    }

    /** True when left click should perform this mod's melee. */
    public static boolean inStance() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && dev.rick.jjk.client.CombatMode.enabled() && ClientState.hasCharacter() && ClientState.stanceEnabled && mc.player.getMainHandItem().isEmpty()
                && !mc.player.isSpectator();
    }

    /** Called from the attack-key mixin instead of vanilla's attack/mine. */
    public static void onAttackPressed() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || Combat.actionsLocked(p)) return;
        ClientPlayNetworking.send(new MeleeInputPayload(MeleeInputPayload.LIGHT, flags(mc, p), targetHint(p, 5)));
        attackHeld = true;
        attackHeldTicks = 0;
        heavyStarted = false;
    }

    private static void tickAttack(Minecraft mc, LocalPlayer p) {
        if (!attackHeld) return;
        boolean down = mc.gui.screen() == null && mc.options.keyAttack.isDown() && inStance();
        if (down) {
            attackHeldTicks++;
            if (!heavyStarted && attackHeldTicks >= HEAVY_HOLD_TICKS) {
                heavyStarted = true;
                ClientPlayNetworking.send(new MeleeInputPayload(MeleeInputPayload.HEAVY_START, flags(mc, p), targetHint(p, 6)));
            }
        } else {
            if (heavyStarted) ClientPlayNetworking.send(new MeleeInputPayload(MeleeInputPayload.HEAVY_RELEASE, flags(mc, p), targetHint(p, 6)));
            attackHeld = false;
            heavyStarted = false;
        }
    }

    private static int flags(Minecraft mc, LocalPlayer p) {
        int f = 0;
        if (mc.options.keyJump.isDown()) f |= MeleeInputPayload.FLAG_JUMP;
        if (p.isSprinting()) f |= MeleeInputPayload.FLAG_SPRINT;
        if (!p.onGround() && !p.isInWater()) f |= MeleeInputPayload.FLAG_AIRBORNE;
        if (p.getXRot() > 50) f |= MeleeInputPayload.FLAG_LOOK_DOWN;
        return f;
    }

    /** The living entity under (or very near) the crosshair within range, or -1. */
    public static int targetHint(LocalPlayer p, double range) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.crosshairPickEntity instanceof LivingEntity le && Targeting.canTarget(p, le)) return le.getId();
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        Entity best = null;
        double bestScore = Math.cos(Math.toRadians(8));
        AABB area = p.getBoundingBox().expandTowards(look.scale(range)).inflate(2);
        for (Entity e : mc.level.getEntities(p, area, e -> e instanceof LivingEntity le && Targeting.canTarget(p, le))) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double d = to.length();
            if (d > range || d < 1e-3) continue;
            double dot = to.dot(look) / d;
            if (dot > bestScore) {
                bestScore = dot;
                best = e;
            }
        }
        return best != null ? best.getId() : -1;
    }

    /** The key currently bound to a slot, as the player set it in Controls (short enough for a HUD badge). */
    public static String keyLabel(AbilitySlot slot) {
        if (slot == AbilitySlot.HEAVY) return "Hold";
        KeyMapping k = KEYS.get(slot);
        if (k == null) return "";
        if (k.isUnbound()) return "-";
        String s = k.getTranslatedKeyMessage().getString();
        s = s.replace("Left ", "L").replace("Right ", "R");
        return s.length() > 4 ? s.substring(0, 4) : s;
    }

    /** Releases held keys when the world is left or a screen opens mid-hold. */
    public static void releaseAll(LocalPlayer player) {
        for (AbilitySlot slot : AbilitySlot.values()) {
            if (Boolean.TRUE.equals(DOWN.get(slot))) {
                DOWN.put(slot, false);
                if (player != null) sendAbility(player, slot, false);
            }
        }
        attackHeld = false;
        heavyStarted = false;
    }
}
