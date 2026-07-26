package dev.totem.alchemy.registry;

import dev.totem.alchemy.effect.AlchemyMobEffects;
import dev.totem.alchemy.item.PigManureItem;
import dev.totem.alchemy.item.StoneBowlItem;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.food.Foods;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;

public final class AlchemyItems {
    public static final Item SALTPETER = AlchemyItemRegistrar.register("saltpeter", Item::new);

    public static final Item PIG_MANURE = AlchemyItemRegistrar.register("pig_manure", PigManureItem::new);

    public static final Item WOOD_ASH = AlchemyItemRegistrar.register("wood_ash", Item::new);

    public static final Item COCOA_POWDER = AlchemyItemRegistrar.register("cocoa_powder",
            props -> new Item(props.stacksTo(1)));

    public static final Item HOT_COCOA = AlchemyItemRegistrar.register("hot_cocoa",
            props -> new Item(props.stacksTo(16)
                    .food(Foods.HONEY_BOTTLE, Consumables.defaultDrink()
                            .sound(SoundEvents.GENERIC_DRINK)
                            .build())
                    .usingConvertsTo(Items.GLASS_BOTTLE)));

    public static final Item CHERRY_BREW = AlchemyItemRegistrar.register("cherry_brew",
            props -> new Item(props.stacksTo(16)
                    .food(new FoodProperties.Builder()
                                    .nutrition(4)
                                    .saturationModifier(0.4F)
                                    .alwaysEdible()
                                    .build(),
                            Consumables.defaultDrink()
                                    .sound(SoundEvents.GENERIC_DRINK)
                                    .onConsume(new ApplyStatusEffectsConsumeEffect(
                                            new MobEffectInstance(AlchemyMobEffects.CHERRY_BLOOM, 20 * 180, 0),
                                            1.0F
                                    ))
                                    .build())
                    .usingConvertsTo(Items.GLASS_BOTTLE)));

    public static final Item STONE_BOWL = AlchemyItemRegistrar.register("stone_bowl",
            props -> new StoneBowlItem(props.stacksTo(1)));

    public static final Item SULFUR_BOWL = AlchemyItemRegistrar.register("sulfur_bowl",
            props -> new Item(props.stacksTo(1).craftRemainder(STONE_BOWL)));

    private AlchemyItems() {
    }

    public static void register() {
        // Class loading registers this owner's items.
    }
}
