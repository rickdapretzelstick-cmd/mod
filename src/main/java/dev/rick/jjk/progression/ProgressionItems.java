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

    public static final Item CURSED_SOUL_SAND = item("cursed_soul_sand", p -> new BlockItem(ProgressionBlocks.CURSED_SOUL_SAND, p),
            new Item.Properties().useBlockDescriptionPrefix());

    private ProgressionItems() {}

    private static Item item(String name, java.util.function.Function<Item.Properties, Item> factory, Item.Properties properties) {
        // 26.3: the properties carry the item's id; vanilla's Items.register* helpers are private.
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JJK.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
    }

    public static void init() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(e -> e.accept(CURSED_SOUL_SAND));
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(e -> {
            e.accept(SOUL_IN_A_BOTTLE);
            e.accept(CURSED_ENERGY_BOTTLE);
            e.accept(CURSED_FINGER);
            e.accept(DORMANT_PRISON_REALM);
        });
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(e -> {
            e.accept(GLASSES);
            e.accept(CURSED_GLASSES);
        });
    }
}
