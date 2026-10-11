package dev.rick.jjk.progression;

import dev.rick.jjk.JJK;
import dev.rick.jjk.progression.item.CursedFingerItem;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.Consumables;

/**
 * The Survival progression's items. The bottles are plain ingredients on purpose (anything later can brew, pour or
 * offer them); the glasses are worn on the face; the Cursed Finger is the first cursed object.
 */
public final class ProgressionItems {
    /** A soul drawn out of Cursed Soul Sand. */
    public static final Item SOUL_IN_A_BOTTLE = item("soul_in_a_bottle", Item::new, new Item.Properties().stacksTo(16));
    /** Soul in a Bottle brewed with a Ghast Tear: refined cursed energy. */
    public static final Item CURSED_ENERGY_BOTTLE = item("cursed_energy_bottle", Item::new, new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));
    /** Ordinary glasses. Worn on the face; they show nothing that isn't there. */
    public static final Item GLASSES = item("glasses", Item::new, new Item.Properties().stacksTo(1).equippable(EquipmentSlot.HEAD));
    /** Glasses steeped in cursed energy: their wearer perceives curses (item tag jjk:grants_curse_perception). */
    public static final Item CURSED_GLASSES = item("cursed_glasses", Item::new,
            new Item.Properties().stacksTo(1).equippable(EquipmentSlot.HEAD).rarity(Rarity.RARE));
    /** One of Sukuna's fingers. Eaten, it makes its eater the world's Yuji, if Yuji is still unclaimed. */
    public static final Item CURSED_FINGER = item("cursed_finger", CursedFingerItem::new, new Item.Properties().stacksTo(16).rarity(Rarity.EPIC)
            .food(new FoodProperties.Builder().nutrition(1).saturationModifier(0.1f).alwaysEdible().build(),
                    Consumables.defaultFood().consumeSeconds(3.2f).build()));

    /** Four shulker shells round a nether star: the Prison Realm, still empty of cursed energy. */
    public static final Item DORMANT_PRISON_REALM = item("dormant_prison_realm", Item::new, new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
    /** The Prison Realm itself (only ever one live in a world: dipped in a full cauldron of cursed energy). */
    public static final Item PRISON_REALM = item("prison_realm", dev.rick.jjk.progression.prison.PrisonRealmItem::new,
            new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());

    /** The first cursed tools: ordinary steel steeped in a full cauldron of cursed energy. */
    public static final Item SLAUGHTER_DEMON = tool(dev.rick.jjk.progression.tool.CursedTools.SLAUGHTER_DEMON, Rarity.RARE);
    public static final Item CURSED_CLEAVER = tool(dev.rick.jjk.progression.tool.CursedTools.CURSED_CLEAVER, Rarity.RARE);

    /** A compass steeped in cursed energy: it follows the incident its carrier is investigating. */
    public static final Item CURSED_COMPASS = item("cursed_compass", dev.rick.jjk.progression.investigation.CursedCompassItem::new,
            new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));

    public static final Item CURSED_RIFLE = tool(dev.rick.jjk.progression.tool.CursedTools.CURSED_RIFLE, Rarity.EPIC);
    /** A netherite sword steeped in a full cauldron of cursed energy. */
    public static final Item CURSED_BLADE = tool(dev.rick.jjk.progression.tool.CursedTools.CURSED_BLADE, Rarity.EPIC);

    // --- Character storylines (see progression.story): Essence -> object -> infused relic (one per world) ---

    public static final Item YUJI_ESSENCE = essence("yuji_essence", "Condensed from a film that never stopped playing.");
    public static final Item GOJO_ESSENCE = essence("gojo_essence", "Condensed from a distance that would not close.");
    public static final Item YUTA_ESSENCE = essence("yuta_essence", "Condensed from a love that would not let go.");
    public static final Item RYU_ESSENCE = essence("ryu_essence", "Condensed from a hunger that was never satisfied.");
    public static final Item HAKARI_ESSENCE = essence("hakari_essence", "Condensed from a winning streak that never ended.");

    public static final Item HUMAN_EARTHWORM_VHS = item("human_earthworm_vhs", Item::new, object("A worn tape. The label reads HUMAN EARTHWORM."));
    public static final Item BLINDFOLD = item("blindfold", Item::new, object("A band of black cloth.").equippable(EquipmentSlot.HEAD));
    public static final Item CURSED_RING = item("cursed_ring", Item::new, object("A plain silver ring, cold to the touch."));
    public static final Item COMB = item("comb", Item::new, object("A fine-tooth comb. Made for serious hair."));
    public static final Item SCRATCH_OFF_TICKET = item("scratch_off_ticket", Item::new, object("Three panels left to scratch."));

    public static final Item INFUSED_HUMAN_EARTHWORM_VHS = relic("infused_human_earthworm_vhs", "yuji", "Something in the film is waiting to be watched.", false);
    public static final Item INFUSED_BLINDFOLD = relic("infused_blindfold", "gojo", "Behind it, the dark is full of light.", true);
    public static final Item INFUSED_CURSED_RING = relic("infused_cursed_ring", "yuta", "Someone is always close when you wear it.", false);
    public static final Item INFUSED_COMB = relic("infused_comb", "ryu", "Are you satisfied?", false);
    public static final Item INFUSED_SCRATCH_OFF_TICKET = relic("infused_scratch_off_ticket", "hakari", "Feeling lucky?", false);

    public static final Item CURSED_SOUL_SAND = item("cursed_soul_sand", p -> new BlockItem(ProgressionBlocks.CURSED_SOUL_SAND, p),
            new Item.Properties().useBlockDescriptionPrefix());

    private ProgressionItems() {}

    public static final Item NEWS_BOARD = item("news_board", p -> new BlockItem(ProgressionBlocks.NEWS_BOARD, p), new Item.Properties().useBlockDescriptionPrefix());
    /** Decoration only (a placed rack or scope isn't any lodge's: only a lodge's own do anything). */
    public static final Item GUN_RACK = item("gun_rack", p -> new BlockItem(ProgressionBlocks.GUN_RACK, p), new Item.Properties().useBlockDescriptionPrefix());
    public static final Item MOUNTED_SCOPE = item("mounted_scope", p -> new BlockItem(ProgressionBlocks.MOUNTED_SCOPE, p), new Item.Properties().useBlockDescriptionPrefix());

    /** A character storyline's Essence: the reward for its village storyline, presented, never mob loot. */
    private static Item essence(String name, String lore) {
        return item(name, Item::new, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()
                .component(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
                .component(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(java.util.List.of(
                        net.minecraft.network.chat.Component.literal(lore)))));
    }

    /** A dormant character object: crafted from an Essence, nothing yet. */
    private static Item.Properties object(String lore) {
        return new Item.Properties().stacksTo(1).rarity(Rarity.RARE).fireResistant()
                .component(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(java.util.List.of(
                        net.minecraft.network.chat.Component.literal(lore), net.minecraft.network.chat.Component.literal("Dormant."))));
    }

    /** An infused relic: one live per world ({@link dev.rick.jjk.progression.story.UniqueRelics}). */
    private static Item relic(String name, String kit, String lore, boolean worn) {
        Item.Properties p = new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()
                .component(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
                .component(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(java.util.List.of(
                        net.minecraft.network.chat.Component.literal(lore))));
        if (worn) p = p.equippable(EquipmentSlot.HEAD);
        return item(name, props -> new dev.rick.jjk.progression.story.RelicItem(kit, props), p);
    }

    private static Item item(String name, java.util.function.Function<Item.Properties, Item> factory, Item.Properties properties) {
        // 26.3: the properties carry the item's id; vanilla's Items.register* helpers are private.
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JJK.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
    }

    /** A cursed tool's item: its swing damage and speed from its definition; it never breaks. */
    private static Item tool(dev.rick.jjk.progression.tool.CursedToolDefinition def, Rarity rarity) {
        var attrs = net.minecraft.world.item.component.ItemAttributeModifiers.builder()
                .add(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                        Item.BASE_ATTACK_DAMAGE_ID, def.damage(), net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE),
                        net.minecraft.world.entity.EquipmentSlotGroup.MAINHAND)
                .add(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED, new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                        Item.BASE_ATTACK_SPEED_ID, def.attackSpeed() - 4.0, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE),
                        net.minecraft.world.entity.EquipmentSlotGroup.MAINHAND)
                .build();
        return item(def.id(), p -> new dev.rick.jjk.progression.tool.CursedToolItem(def, p),
                new Item.Properties().stacksTo(1).rarity(rarity).attributes(attrs).fireResistant());
    }

    public static void init() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(e -> e.accept(CURSED_SOUL_SAND));
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(e -> {
            e.accept(NEWS_BOARD);
            e.accept(GUN_RACK);
            e.accept(MOUNTED_SCOPE);
        });
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(e -> {
            e.accept(SOUL_IN_A_BOTTLE);
            e.accept(CURSED_ENERGY_BOTTLE);
            e.accept(CURSED_FINGER);
            e.accept(DORMANT_PRISON_REALM);
        });
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(e -> {
            for (Item i : new Item[] {YUJI_ESSENCE, GOJO_ESSENCE, YUTA_ESSENCE, RYU_ESSENCE, HAKARI_ESSENCE,
                    HUMAN_EARTHWORM_VHS, BLINDFOLD, CURSED_RING, COMB, SCRATCH_OFF_TICKET}) e.accept(i);
            // The infused relics are only ever made by a cauldron (a Creative copy carries no token: a test relic).
        });
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(e -> {
            e.accept(GLASSES);
            e.accept(CURSED_GLASSES);
            e.accept(CURSED_COMPASS);
            e.accept(SLAUGHTER_DEMON);
            e.accept(CURSED_CLEAVER);
            e.accept(CURSED_RIFLE);
            e.accept(CURSED_BLADE);
        });
    }
}
