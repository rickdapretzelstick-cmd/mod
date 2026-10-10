package dev.rick.jjk.progression.tool;

import dev.rick.jjk.JJK;
import dev.rick.jjk.progression.mastery.Mastery;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The cursed tools: each one's definition and fighting behaviour, by id. A new cursed tool is a definition, a
 * {@link ToolBehavior}, an item (ProgressionItems), its tree's JSON, and (if it is made in the cauldron) a line in
 * {@link dev.rick.jjk.progression.block.CauldronInfusions}; the Mastery screen and the reward rules need nothing new.
 */
public final class CursedTools {
    public static final CursedToolDefinition SLAUGHTER_DEMON = new CursedToolDefinition("slaughter_demon", "Slaughter Demon",
            CursedToolDefinition.Style.FAST, 4f, 2.0f, "A short cursed blade: quick, close, relentless.");
    public static final CursedToolDefinition CURSED_CLEAVER = new CursedToolDefinition("cursed_cleaver", "Cursed Cleaver",
            CursedToolDefinition.Style.HEAVY, 8f, 0.85f, "A heavy cursed blade: slow, crushing, built to break a guard.");

    /** The Cursed Rifle: found, not made (a lodge investigation's reward). Its stock makes a poor club. */
    public static final CursedToolDefinition CURSED_RIFLE = new CursedToolDefinition("cursed_rifle", "Cursed Rifle",
            CursedToolDefinition.Style.RANGED, 2f, 1.0f, "A cursed rifle with a scope, and four folded support arms round its barrel.",
            CursedToolDefinition.Rarity.UNIQUE);

    /** The Cursed Blade: a netherite sword steeped in cursed energy. Rare (it costs netherite), not unique. */
    public static final CursedToolDefinition CURSED_BLADE = new CursedToolDefinition("cursed_blade", "Cursed Blade",
            CursedToolDefinition.Style.HEAVY, 7f, 1.0f, "A great cleaver of a blade, black thorns bursting from its guard: heavy, crimson slashes.",
            CursedToolDefinition.Rarity.RARE);

    private static final Map<String, CursedToolDefinition> DEFS = new LinkedHashMap<>();
    private static final Map<String, ToolBehavior> BEHAVIORS = new LinkedHashMap<>();
    private static final Identifier SPEED_ID = JJK.id("cursed_tool_mastery_speed");

    static {
        register(SLAUGHTER_DEMON, new SlaughterDemonBehavior(SLAUGHTER_DEMON));
        register(CURSED_CLEAVER, new CursedCleaverBehavior(CURSED_CLEAVER));
        register(CURSED_RIFLE, new dev.rick.jjk.progression.tool.rifle.RifleBehavior());
        register(CURSED_BLADE, ToolBehavior.NONE);
    }

    private CursedTools() {}

    public static void register(CursedToolDefinition def, ToolBehavior behavior) {
        DEFS.put(def.id(), def);
        BEHAVIORS.put(def.id(), behavior);
    }

    public static Collection<CursedToolDefinition> all() {
        return DEFS.values();
    }

    @Nullable
    public static CursedToolDefinition get(String id) {
        return DEFS.get(id);
    }

    public static ToolBehavior behavior(String id) {
        return BEHAVIORS.getOrDefault(id, ToolBehavior.NONE);
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(CursedTools::tick);
        // Their movesets (the Cursed Item slot).
        dev.rick.jjk.progression.tool.kit.CursedKits.register(new dev.rick.jjk.progression.tool.kit.SlaughterDemonKit());
        dev.rick.jjk.progression.tool.kit.CursedKits.register(new dev.rick.jjk.progression.tool.kit.CleaverKit());
        dev.rick.jjk.progression.tool.kit.CursedKits.register(new dev.rick.jjk.progression.tool.kit.RifleKit());
        dev.rick.jjk.progression.tool.kit.CursedKits.register(new dev.rick.jjk.progression.tool.kit.BladeKit());
        dev.rick.jjk.progression.tool.kit.CursedKits.init();
        dev.rick.jjk.core.defense.Defenses.register(new dev.rick.jjk.progression.tool.kit.SlaughterDemonKit.ParryDefense());
        dev.rick.jjk.core.defense.Defenses.register(new dev.rick.jjk.progression.tool.kit.CleaverKit.WallDefense());
        dev.rick.jjk.core.defense.Defenses.register(new dev.rick.jjk.progression.tool.kit.BladeKit.ThornGuardDefense());
        dev.rick.jjk.progression.tool.rifle.RifleServer.init();
        dev.rick.jjk.progression.tool.rifle.RifleCounter.register();
    }

    /** The cursed tool in a player's main hand, or null. */
    @Nullable
    public static CursedToolItem held(ServerPlayer p) {
        ItemStack s = p.getMainHandItem();
        return s.getItem() instanceof CursedToolItem t ? t : null;
    }

    private static void tick(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            CursedToolItem t = held(p);
            double speed = t == null ? 1.0 : Mastery.param(p, t.definition().paramKey("speed"));
            applySpeed(p, speed);
            if (t != null) t.behavior().heldTick(p.level(), p, p.getMainHandItem());
        }
    }

    /** The tool's Mastery speed bonus, as a modifier on the player while it is in hand (none otherwise). */
    private static void applySpeed(ServerPlayer p, double mul) {
        AttributeInstance a = p.getAttribute(Attributes.ATTACK_SPEED);
        if (a == null) return;
        AttributeModifier cur = a.getModifier(SPEED_ID);
        double want = mul - 1.0;
        if (Math.abs(want) < 1e-6) {
            if (cur != null) a.removeModifier(SPEED_ID);
            return;
        }
        if (cur != null && Math.abs(cur.amount() - want) < 1e-6) return;
        if (cur != null) a.removeModifier(SPEED_ID);
        a.addTransientModifier(new AttributeModifier(SPEED_ID, want, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    @SuppressWarnings("unused")
    private static Holder<Attribute> unused() {
        return Attributes.ATTACK_SPEED;
    }
}
