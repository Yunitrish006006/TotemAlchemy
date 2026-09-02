package dev.totem.alchemy.registry;

import dev.totem.core.api.v1.migration.LegacyItemMigrationRegistry;
import dev.totem.alchemy.alchemy.AlchemyPotions;
import dev.totem.alchemy.item.AlchemyDrinkItem;
import dev.totem.alchemy.item.LargePotionFlaskItem;
import dev.totem.alchemy.item.PigManureItem;
import dev.totem.alchemy.item.StoneBowlItem;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.food.Foods;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumables;

public final class AlchemyItems {
    public static final Item SALTPETER = canonical("saltpeter", Item::new);

    public static final Item PIG_MANURE = canonical("pig_manure", PigManureItem::new);

    public static final Item WOOD_ASH = canonical("wood_ash", Item::new);

    public static final Item COCOA_POWDER = canonical("cocoa_powder",
            props -> new Item(props.stacksTo(1)));

    public static final Item HOT_COCOA = canonical("hot_cocoa",
            props -> new AlchemyDrinkItem(props.stacksTo(16)
                    .food(Foods.HONEY_BOTTLE, Consumables.defaultDrink()
                            .sound(SoundEvents.GENERIC_DRINK)
                            .build())
                    .usingConvertsTo(Items.GLASS_BOTTLE), null, "minecraft:saturation"));

    public static final Item CHERRY_BREW = canonical("cherry_brew",
            props -> new AlchemyDrinkItem(props.stacksTo(16)
                    .food(new FoodProperties.Builder()
                                    .nutrition(4)
                                    .saturationModifier(0.4F)
                                    .alwaysEdible()
                                    .build(),
                            Consumables.defaultDrink()
                                    .sound(SoundEvents.GENERIC_DRINK)
                                    .build())
                    .usingConvertsTo(Items.GLASS_BOTTLE), () -> AlchemyPotions.CHERRY_SWIFTNESS, null));

    public static final Item STONE_BOWL = canonical("stone_bowl",
            props -> new StoneBowlItem(props.stacksTo(1)));

    public static final Item SULFUR_BOWL = canonical("sulfur_bowl",
            props -> new Item(props.stacksTo(1).craftRemainder(STONE_BOWL)));

    public static final Item LARGE_POTION_FLASK = canonical("large_potion_flask",
            props -> new LargePotionFlaskItem(props.stacksTo(1)));

    private AlchemyItems() {
    }

    public static void register() {
        // Class loading registers this owner's items.
    }

    public static boolean isStoneBowl(ItemStack stack) {
        return LegacyItemMigrationRegistry.matches(stack, STONE_BOWL);
    }

    public static boolean isPigManure(ItemStack stack) {
        return LegacyItemMigrationRegistry.matches(stack, PIG_MANURE);
    }

    private static Item canonical(
            String path,
            java.util.function.Function<Item.Properties, Item> factory
    ) {
        return AlchemyItemRegistrar.register("totem", "alchemy/" + path, factory);
    }

}
