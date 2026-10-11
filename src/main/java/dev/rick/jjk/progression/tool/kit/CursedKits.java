package dev.rick.jjk.progression.tool.kit;

import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.character.JJKCharacter;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.progression.tool.CursedToolDefinition;
import dev.rick.jjk.progression.tool.CursedToolItem;
import dev.rick.jjk.registry.ModAttachments;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cursed tools as equippable movesets. Each cursed tool with a kit registers it here by its id; whatever is in a
 * player's Cursed Item slot gives them that kit ({@link AbilityCaster#setToolKit}), beside any technique they have. With
 * both, a key switches which one the moves come from; with no technique, the tool's is simply theirs.
 *
 * <p>Death drops the equipped tool with the rest of the inventory (keepInventory keeps it).
 */
public final class CursedKits {
    private static final Map<String, CursedToolKit> KITS = new LinkedHashMap<>();

    private CursedKits() {}

    public static void register(CursedToolKit kit) {
        KITS.put(kit.def.id(), kit);
    }

    @Nullable
    public static CursedToolKit kit(String toolId) {
        return KITS.get(toolId);
    }

    public static java.util.Collection<CursedToolKit> all() {
        return KITS.values();
    }

    /** The kit of the tool in a player's Cursed Item slot, or null. */
    @Nullable
    public static CursedToolKit equipped(LivingEntity e) {
        if (!(e instanceof net.minecraft.world.entity.player.Player p)) return null;
        ItemStack s = CursedSlot.get(p);
        return s.getItem() instanceof CursedToolItem t ? KITS.get(t.definition().id()) : null;
    }

    /**
     * The cursed tool a hit landing now belongs to, when it comes from a tool's moveset (not a tool in the hand: that
     * is the damage source's own weapon): the tool kit's move that is running or just ran, or a basic attack while the
     * tool's moveset is the one in use.
     */
    @Nullable
    public static CursedToolDefinition credited(LivingEntity attacker) {
        AbilityCaster c = Casters.getOrNull(attacker);
        if (c == null || !(c.toolKit() instanceof CursedToolKit k)) return null;
        String a = c.creditedAbility();
        if (a != null) return k.has(a) ? k.def : null;
        return c.usingTool() ? k.def : null;
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(CursedKits::tick);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof ServerPlayer p) || !(p.level() instanceof ServerLevel level)) return;
            ItemStack s = CursedSlot.get(p);
            if (s.isEmpty() || level.getGameRules().get(GameRules.KEEP_INVENTORY)) return;
            CursedSlot.set(p, ItemStack.EMPTY);
            p.spawnAtLocation(level, s);
        });
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> COMBAT_OFF.remove(h.player.getUUID()));
        ServerPlayerEvents.COPY_FROM.register((old, now, alive) -> {
            ItemStack s = CursedSlot.get(old);
            if (!s.isEmpty()) CursedSlot.set(now, s.copy());
        });
    }

    private static void tick(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) update(p);
    }

    /** Keeps a player's caster in step with their Cursed Item slot. */
    public static void update(ServerPlayer p) {
        AbilityCaster c = Casters.get(p);
        CursedToolKit want = equipped(p);
        JJKCharacter had = c.toolKit();
        // Drawn or holstered: the player's choice (X), drawn at first for someone with no technique beside it; never
        // drawn while their Combat Mode is off (it comes back out when Combat Mode does).
        Boolean chosen = p.getAttached(ModAttachments.TOOL_MOVESET);
        boolean wants = chosen != null ? chosen : c.character() == null;
        c.setToolSelected(want != null && wants && !COMBAT_OFF.contains(p.getUUID()));
        boolean drawn = want != null && c.toolSelected();
        Boolean was = p.getAttached(ModAttachments.TOOL_DRAWN);
        if (was == null || was != drawn) {
            p.setAttached(ModAttachments.TOOL_DRAWN, drawn);
            if (p.level() instanceof ServerLevel level && want != null) {
                // The draw or the holster: a quick clip and the sound of it.
                String where = want.grip().holster == GripProfile.Holster.BACK ? "back" : "hip";
                dev.rick.jjk.core.anim.Anim.play(p, (drawn ? "kit_draw_" : "kit_holster_") + where);
                Fx.sound(level, p.position(), drawn ? SoundEvents.ARMOR_EQUIP_IRON.value() : SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.6f, drawn ? 1.2f : 0.9f);
            }
        }
        if (had == want) return;
        c.setToolKit(want);
        if (want != null && p.level() instanceof ServerLevel level) {
            Fx.sound(level, p.position(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 0.8f, 0.7f);
            String how = c.character() == null ? "drawn (X holsters it)" : "X draws it";
            p.sendOverlayMessage(Component.literal(want.def.displayName() + " equipped: " + how + ".").withStyle(ChatFormatting.DARK_PURPLE));
        }
    }

    private static String want(ServerPlayer p) {
        CursedToolKit k = equipped(p);
        return k == null ? "" : k.def.displayName();
    }

    /** Players whose JJK Combat Mode is off (their client said so): no tool drawn. */
    private static final java.util.Set<java.util.UUID> COMBAT_OFF = new java.util.HashSet<>();

    /** A player's client turned Combat Mode on or off. */
    public static void setCombatMode(ServerPlayer p, boolean on) {
        boolean changed = on ? COMBAT_OFF.remove(p.getUUID()) : COMBAT_OFF.add(p.getUUID());
        if (changed) update(p);
    }

    public static boolean combatModeOff(ServerPlayer p) {
        return COMBAT_OFF.contains(p.getUUID());
    }

    /** A player asks to draw or holster their cursed tool (switching from or to their technique, if they have one). */
    public static boolean switchMoveset(ServerPlayer p) {
        AbilityCaster c = Casters.get(p);
        update(p);
        if (COMBAT_OFF.contains(p.getUUID())) return false;
        if (!c.switchMoveset()) return false;
        p.setAttached(ModAttachments.TOOL_MOVESET, c.toolSelected());
        if (p.level() instanceof ServerLevel level) {
            Fx.play(level, "kit_switch", p.position().add(0, 1, 0), p.getLookAngle(), c.usingTool() ? 1f : 0f, p.getId());
            Fx.sound(level, p.position(), c.usingTool() ? SoundEvents.ARMOR_EQUIP_IRON.value() : SoundEvents.AMETHYST_BLOCK_CHIME, 0.7f, 1.3f);
        }
        JJKCharacter k = c.activeKit();
        p.sendOverlayMessage(Component.literal(k == null ? want(p) + " holstered" : k.displayName()).withStyle(c.usingTool() ? ChatFormatting.DARK_PURPLE : ChatFormatting.AQUA));
        return true;
    }
}
