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
        Boolean chosen = p.getAttached(ModAttachments.TOOL_MOVESET);
        c.setToolSelected(chosen != null && chosen);
        if (had == want) return;
        c.setToolKit(want);
        if (want != null && p.level() instanceof ServerLevel level) {
            Fx.sound(level, p.position(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 0.8f, 0.7f);
            String how = c.character() == null ? "its moves are yours" : "switch movesets to use it";
            p.sendOverlayMessage(Component.literal(want.def.displayName() + " equipped: " + how + ".").withStyle(ChatFormatting.DARK_PURPLE));
        }
    }

    /** A player asks to switch between their technique and their cursed tool's moveset. */
    public static boolean switchMoveset(ServerPlayer p) {
        AbilityCaster c = Casters.get(p);
        update(p);
        if (!c.switchMoveset()) return false;
        p.setAttached(ModAttachments.TOOL_MOVESET, c.toolSelected());
        if (p.level() instanceof ServerLevel level) {
            Fx.play(level, "kit_switch", p.position().add(0, 1, 0), p.getLookAngle(), c.usingTool() ? 1f : 0f, p.getId());
            Fx.sound(level, p.position(), c.usingTool() ? SoundEvents.ARMOR_EQUIP_IRON.value() : SoundEvents.AMETHYST_BLOCK_CHIME, 0.7f, 1.3f);
        }
        JJKCharacter k = c.activeKit();
        p.sendOverlayMessage(Component.literal(k == null ? "" : k.displayName()).withStyle(c.usingTool() ? ChatFormatting.DARK_PURPLE : ChatFormatting.AQUA));
        return true;
    }
}
