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
 * In combat mode (character assigned) left click is always this mod's melee, whatever is held:
 * tap for a light attack, keep holding to charge a heavy, release to throw it.
 */
public final class InputHandler {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(JJK.id("gojo"));
    private static final Map<AbilitySlot, KeyMapping> KEYS = new EnumMap<>(AbilitySlot.class);
    private static final Map<AbilitySlot, Boolean> DOWN = new EnumMap<>(AbilitySlot.class);
    /** Switches between combat mode and Vanilla Minecraft mode. Unbound by default so it can't be hit mid-fight. */
    private static KeyMapping modeKey;
    /** Opens the character select screen. */
    private static KeyMapping characterKey;
    private static KeyMapping masteryKey;
    /** Switches between the innate technique's moveset and the equipped cursed tool's. */
    private static KeyMapping switchKey;

    private static final int HEAVY_HOLD_TICKS = 7;
    private static boolean attackHeld;
    private static int attackHeldTicks;
    private static boolean heavyStarted;

    private InputHandler() {}

    /** This mod's key category (for keys registered elsewhere, like the Prison Realm's outside view). */
    public static KeyMapping.Category category() {
        return CATEGORY;
    }

    public static void init() {
        // Jujutsu Shenanigans' PC controls: M1 attack, 1-4 moves, R special, G awakening, F block, Q dash / ragdoll
        // escape (double-tap W sprint and Space jump are Minecraft's own). In combat mode these keys belong to the mod:
        // the hotbar, drop and offhand-swap actions sharing them are held back (see beforeVanillaKeys); Vanilla mode gives
        // them back.
        bind(AbilitySlot.SKILL_1, "skill_1", InputConstants.KEY_1);
        bind(AbilitySlot.SKILL_2, "skill_2", InputConstants.KEY_2);
        bind(AbilitySlot.SKILL_3, "skill_3", InputConstants.KEY_3);
        bind(AbilitySlot.SKILL_4, "skill_4", InputConstants.KEY_4);
        bind(AbilitySlot.SKILL_5, "skill_5", InputConstants.KEY_R);
        bind(AbilitySlot.ULTIMATE, "ultimate", InputConstants.KEY_G);
        bind(AbilitySlot.GUARD, "guard", InputConstants.KEY_F);
        bind(AbilitySlot.DASH, "dash", InputConstants.KEY_Q);
        characterKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jjk.character_menu", InputConstants.Type.KEYBOARD, InputConstants.KEY_K, CATEGORY));
        masteryKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jjk.mastery", InputConstants.Type.KEYBOARD, InputConstants.KEY_J, CATEGORY));
        switchKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jjk.switch_moveset", InputConstants.Type.KEYBOARD, InputConstants.KEY_X, CATEGORY));
        modeKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jjk.combat_mode", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY));
    }

    private static void bind(AbilitySlot slot, String name, int key) {
        KEYS.put(slot, KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jjk." + name, InputConstants.Type.KEYBOARD, key, CATEGORY)));
        DOWN.put(slot, false);
    }

    /**
     * Start of the client tick, before Minecraft handles its own keys: in combat mode, vanilla actions bound to the same
     * key as one of this mod's (hotbar slots 1-4 under the moves, drop under dash, offhand swap under block) are held
     * back so pressing a move doesn't also change slot or throw the held item.
     */
    public static void beforeVanillaKeys(Minecraft mc) {
        migrateLayout(mc);
        if (mc.player == null || mc.gui.screen() != null || !dev.rick.jjk.client.CombatMode.enabled() || !ClientState.hasCharacter()) return;
        java.util.List<KeyMapping> vanilla = new java.util.ArrayList<>(java.util.List.of(mc.options.keyHotbarSlots));
        vanilla.add(mc.options.keyDrop);
        vanilla.add(mc.options.keySwapOffhand);
        for (KeyMapping v : vanilla) {
            if (v.isUnbound()) continue;
            for (KeyMapping ours : KEYS.values()) {
                if (!ours.isUnbound() && v.same(ours)) {
                    while (v.consumeClick()) {}
                    break;
                }
            }
        }
    }

    private static boolean migrated;
    /** The pre-JJS default keys, per slot: binds still on these move to the JJS layout once. */
    private static final Map<AbilitySlot, Integer> OLD_DEFAULTS = Map.of(AbilitySlot.SKILL_1, InputConstants.KEY_Z,
            AbilitySlot.SKILL_2, InputConstants.KEY_X, AbilitySlot.SKILL_3, InputConstants.KEY_C, AbilitySlot.SKILL_4, InputConstants.KEY_V,
            AbilitySlot.SKILL_5, InputConstants.KEY_B, AbilitySlot.GUARD, InputConstants.KEY_R, AbilitySlot.DASH, InputConstants.KEY_LALT);

    /** Players upgrading from the old layout get the JJS keys, unless they had rebound a key themselves. */
    private static void migrateLayout(Minecraft mc) {
        if (migrated) return;
        migrated = true;
        var cfg = dev.rick.jjk.config.JJKConfig.get().client;
        if (cfg.controlsLayout >= 2) return;
        boolean changed = false;
        for (var e : OLD_DEFAULTS.entrySet()) {
            KeyMapping k = KEYS.get(e.getKey());
            if (k != null && k.saveString().equals(InputConstants.Type.KEYBOARD.getOrCreate(e.getValue()).getName())) {
                k.setKey(k.getDefaultKey());
                changed = true;
            }
        }
        if (changed) {
            KeyMapping.resetMapping();
            mc.options.save();
        }
        cfg.controlsLayout = 2;
        dev.rick.jjk.config.JJKConfig.save();
    }

    /** Called every client tick. */
    public static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) return;
        while (modeKey.consumeClick()) dev.rick.jjk.client.CombatMode.toggle();
        while (characterKey.consumeClick()) {
            // Survival progression decides what the screen may offer (Creative: everything).
            if (mc.gui.screen() == null) dev.rick.jjk.client.ClientProgression.openCharacterSelect(null);
        }
        while (masteryKey.consumeClick()) {
            if (mc.gui.screen() != null) continue;
            if (!dev.rick.jjk.client.mastery.ClientMastery.enabled()) {
                player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("Mastery is off: every cursed tool comes whole.")
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
                continue;
            }
            mc.gui.setScreen(new dev.rick.jjk.client.mastery.MasteryScreen());
        }
        if (!dev.rick.jjk.client.CombatMode.enabled()) {
            // Vanilla Minecraft mode: none of this mod's keys do anything. Swallow their presses so nothing fires later.
            for (KeyMapping k : KEYS.values()) while (k.consumeClick()) {}
            attackHeld = false;
            heavyStarted = false;
            return;
        }
        boolean canAct = mc.gui.screen() == null && ClientState.hasCharacter() && player.isAlive();
        while (switchKey.consumeClick()) {
            if (canAct && ClientState.canSwitchMoveset()) {
                // Keys held across a switch would release onto the other moveset: let them go first.
                releaseAll(player);
                ClientPlayNetworking.send(new dev.rick.jjk.core.net.MovesetSwitchPayload());
            }
        }
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
        // Combat mode means fighting: left click is always the M1, whatever is in hand, and never mines or hits vanilla-style
        // (Vanilla Minecraft mode gives the mouse back).
        return mc.player != null && dev.rick.jjk.client.CombatMode.enabled() && ClientState.hasCharacter() && !mc.player.isSpectator();
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
