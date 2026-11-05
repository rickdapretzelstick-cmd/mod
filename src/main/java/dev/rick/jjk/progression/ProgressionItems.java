package dev.rick.jjk.progression;

import dev.rick.jjk.JJK;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * The cursed tools' items. (The rest of the survival system's items, the glasses, relics, essences, bottles and the
 * news board, are archived under archive/survival.) Each tool is crafted: its ordinary base plus a ghast tear
 * (data/jjk/recipe), and is in the creative tools tab.
 */
public final class ProgressionItems {
    public static final Item SLAUGHTER_DEMON = tool(dev.rick.jjk.progression.tool.CursedTools.SLAUGHTER_DEMON, Rarity.RARE);
    public static final Item CURSED_CLEAVER = tool(dev.rick.jjk.progression.tool.CursedTools.CURSED_CLEAVER, Rarity.RARE);
    public static final Item CURSED_RIFLE = tool(dev.rick.jjk.progression.tool.CursedTools.CURSED_RIFLE, Rarity.EPIC);
    /** A netherite sword steeped in cursed energy. */
    public static final Item CURSED_BLADE = tool(dev.rick.jjk.progression.tool.CursedTools.CURSED_BLADE, Rarity.EPIC);

    private ProgressionItems() {}

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
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(e -> {
            e.accept(SLAUGHTER_DEMON);
            e.accept(CURSED_CLEAVER);
            e.accept(CURSED_RIFLE);
            e.accept(CURSED_BLADE);
        });
    }
}
