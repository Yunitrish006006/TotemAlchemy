package dev.totem.alchemy.client.manual;

import dev.totem.alchemy.alchemy.AlchemyPotions;
import dev.totem.alchemy.alchemy.MultiOutcomeBrewing;
import dev.totem.alchemy.alchemy.VanillaBrewingChance;
import dev.totem.alchemy.registry.AlchemyItems;
import dev.totem.core.api.v1.client.manual.TotemManualPageOverlayRegistry;
import dev.totem.core.api.v1.client.manual.TotemManualPageRenderContext;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** Icon-first ingredient and outcome reference for the Alchemy manual chapter. */
public final class AlchemyManualPageOverlay {
    private static final String PAGE_PREFIX = "book.deadrecall.alchemy_manual.page.";
    private static final int INK = 0xFF4B3826;
    private static final int MUTED = 0xFF765B3D;
    private static final int GOOD = 0xFF287A45;
    private static final int WARN = 0xFFA33A2B;

    private static final List<List<Item>> OUTCOME_PAGES = List.of(
            List.of(Items.SPIDER_EYE, Items.RED_MUSHROOM, Items.GLISTERING_MELON_SLICE, Items.SUGAR),
            List.of(Items.RABBIT_FOOT, Items.MAGMA_CREAM, Items.GOLDEN_CARROT, Items.BLAZE_POWDER),
            List.of(Items.GHAST_TEAR, Items.PUFFERFISH, Items.TURTLE_HELMET, Items.PHANTOM_MEMBRANE),
            List.of(Items.BREEZE_ROD, Items.SLIME_BLOCK, Items.STONE, Items.COBWEB)
    );
    private static volatile List<List<EffectEntry>> effectPages;

    private AlchemyManualPageOverlay() {
    }

    public static void register() {
        TotemManualPageOverlayRegistry.register(
                Identifier.fromNamespaceAndPath("totem-alchemy", "manual_ingredients"),
                AlchemyManualPageOverlay::render
        );
    }

    private static void render(TotemManualPageRenderContext context) {
        if (context.pageKey() == null || !context.pageKey().startsWith(PAGE_PREFIX)) {
            return;
        }
        int page;
        try {
            page = Integer.parseInt(context.pageKey().substring(PAGE_PREFIX.length()));
        } catch (NumberFormatException ignored) {
            return;
        }
        switch (page) {
            case 1 -> renderOverview(context);
            case 2 -> renderBasesAndModifiers(context);
            case 3, 4, 5, 6 -> renderOutcomePage(context, OUTCOME_PAGES.get(page - 3));
            case 7 -> renderSpecialEffects(context);
            case 8 -> renderCauldronRecipes(context);
            case 9, 10, 11, 12, 13, 14 -> renderEffectIndex(
                    context, effectPages().get(page - 9));
            default -> {
            }
        }
    }

    private static void renderOverview(TotemManualPageRenderContext context) {
        int y = context.pageTop() + 34;
        item(context, Items.POTION, 38, y);
        plus(context, 59, y + 8);
        item(context, Items.SUGAR, 72, y);
        arrow(context, 94, y + 8, 12);
        potion(context, Potions.SWIFTNESS, 112, y);
        centeredKeyClipped(context, "book.deadrecall.alchemy_manual.diagram.dynamic", y + 21, MUTED);

        y += 43;
        item(context, Items.BREWING_STAND, 42, y);
        text(context, "≈", 65, y + 4, MUTED);
        percentage(context, Items.SUGAR, 82, y);
        centeredKeyClipped(context, "book.deadrecall.alchemy_manual.diagram.batch", y + 21, GOOD);

        y += 43;
        item(context, Items.BOOK, 50, y);
        arrow(context, 72, y + 8, 12);
        item(context, Items.BREWING_STAND, 90, y);
        arrow(context, 112, y + 8, 10);
        item(context, Items.WRITTEN_BOOK, 128, y);
        centeredKeyClipped(context, "book.deadrecall.alchemy_manual.diagram.obtain", y + 21, MUTED);
    }

    private static void renderBasesAndModifiers(TotemManualPageRenderContext context) {
        int y = context.pageTop() + 36;
        baseRow(context, Items.NETHER_WART, 88, false, y);
        baseRow(context, Items.RED_MUSHROOM, 65, true, y + 28);

        modifier(context, Items.REDSTONE, 92, "book.deadrecall.alchemy_manual.diagram.extend", 38, y + 58);
        modifier(context, Items.GLOWSTONE_DUST, 75, "book.deadrecall.alchemy_manual.diagram.strengthen", 103, y + 58);
        modifier(context, Items.GUNPOWDER, 74, "book.deadrecall.alchemy_manual.diagram.splash", 38, y + 94);
        modifier(context, Items.DRAGON_BREATH, 68, "book.deadrecall.alchemy_manual.diagram.lingering", 103, y + 94);
    }

    private static void renderOutcomePage(TotemManualPageRenderContext context, List<Item> ingredients) {
        ItemStack awkward = PotionContents.createItemStack(Items.POTION, Potions.AWKWARD);
        int y = context.pageTop() + 38;
        for (Item ingredient : ingredients) {
            List<MultiOutcomeBrewing.Outcome> outcomes = MultiOutcomeBrewing.outcomesFor(
                    new ItemStack(ingredient), awkward);
            outcomeRow(context, ingredient, outcomes, y);
            y += 30;
        }
    }

    private static void renderSpecialEffects(TotemManualPageRenderContext context) {
        ItemStack awkward = PotionContents.createItemStack(Items.POTION, Potions.AWKWARD);
        outcomeRow(context, Items.FERMENTED_SPIDER_EYE,
                MultiOutcomeBrewing.outcomesFor(new ItemStack(Items.FERMENTED_SPIDER_EYE), awkward),
                context.pageTop() + 42);

        variantRow(context, Items.CHERRY_LEAVES, 80, Potions.SWIFTNESS,
                AlchemyPotions.CHERRY_SWIFTNESS,
                "book.deadrecall.alchemy_manual.diagram.cherry_bonus", context.pageTop() + 84);
        variantRow(context, Items.FIREFLY_BUSH, 82, Potions.STRENGTH,
                AlchemyPotions.FIREFLY_STRENGTH,
                "book.deadrecall.alchemy_manual.diagram.firefly_bonus", context.pageTop() + 126);
    }

    private static void renderCauldronRecipes(TotemManualPageRenderContext context) {
        int y = context.pageTop() + 40;
        recipe(context,
                List.of(new ItemStack(AlchemyItems.WOOD_ASH), new ItemStack(Items.RED_MUSHROOM),
                        new ItemStack(AlchemyItems.PIG_MANURE)),
                new ItemStack(AlchemyItems.SALTPETER), y);
        centeredClipped(context, Component.translatable(
                "book.deadrecall.alchemy_manual.diagram.saltpeter"), y + 21, MUTED, 140);

        y += 40;
        recipe(context,
                List.of(new ItemStack(Items.MILK_BUCKET), new ItemStack(AlchemyItems.COCOA_POWDER),
                        new ItemStack(Items.SUGAR)),
                new ItemStack(AlchemyItems.HOT_COCOA), y);
        centeredKeyClipped(context, "book.deadrecall.alchemy_manual.diagram.hot_cocoa", y + 21, MUTED);

        y += 40;
        recipe(context,
                List.of(new ItemStack(Items.SUGAR), new ItemStack(Items.CHERRY_LEAVES),
                        new ItemStack(Items.GLOW_BERRIES), new ItemStack(Items.SWEET_BERRIES)),
                new ItemStack(AlchemyItems.CHERRY_BREW), y);
        centeredKeyClipped(context, "book.deadrecall.alchemy_manual.diagram.cherry_brew", y + 21, MUTED);
    }

    /** Reverse index: a discovered effect reveals every material the player has confirmed for it. */
    private static void renderEffectIndex(TotemManualPageRenderContext context, List<EffectEntry> effects) {
        List<EffectEntry> discoveredEffects = effects.stream()
                .filter(effect -> effect.ingredients().stream()
                        .anyMatch(ingredient -> AlchemyDiscoveryClientCache.has(ingredient, effect.potion())))
                .toList();
        if (discoveredEffects.isEmpty()) {
            unknownPotion(context, 48, context.pageTop() + 72);
            centeredAtClipped(context,
                    Component.translatable("book.deadrecall.alchemy_manual.diagram.no_effect_records"),
                    105, context.pageTop() + 76, WARN, 88);
            return;
        }
        int y = context.pageTop() + 38;
        for (EffectEntry effect : discoveredEffects) {
            List<Item> discoveredIngredients = effect.ingredients().stream()
                    .filter(ingredient -> AlchemyDiscoveryClientCache.has(ingredient, effect.potion()))
                    .toList();
            potion(context, effect.potion(), 22, y);
            centeredAtClipped(context, Component.translatable(effect.messageKey()),
                    65, y + 4, INK, 55);
            arrow(context, 91, y + 8, 9);
            int startX = 108;
            for (int index = 0; index < discoveredIngredients.size(); index++) {
                item(context, discoveredIngredients.get(index), startX + index * 18, y);
            }
            y += 34;
        }
    }

    private static List<List<EffectEntry>> buildEffectPages() {
        ItemStack awkward = PotionContents.createItemStack(Items.POTION, Potions.AWKWARD);
        Map<Holder<Potion>, EffectEntry> effects = new LinkedHashMap<>();
        List<Item> ingredients = new ArrayList<>();
        OUTCOME_PAGES.forEach(ingredients::addAll);
        ingredients.add(Items.FERMENTED_SPIDER_EYE);
        for (Item ingredient : ingredients) {
            for (MultiOutcomeBrewing.Outcome outcome : MultiOutcomeBrewing.outcomesFor(
                    new ItemStack(ingredient), awkward)) {
                addEffectIngredient(effects, outcome.potion(), outcome.messageKey(), ingredient);
            }
        }
        addEffectIngredient(effects, AlchemyPotions.CHERRY_SWIFTNESS,
                "message.deadrecall.alchemy.outcome.cherry_swiftness", Items.CHERRY_LEAVES);
        addEffectIngredient(effects, AlchemyPotions.FIREFLY_STRENGTH,
                "message.deadrecall.alchemy.outcome.firefly_strength", Items.FIREFLY_BUSH);

        List<EffectEntry> entries = List.copyOf(effects.values());
        List<List<EffectEntry>> pages = new ArrayList<>();
        for (int index = 0; index < entries.size(); index += 4) {
            pages.add(List.copyOf(entries.subList(index, Math.min(index + 4, entries.size()))));
        }
        return List.copyOf(pages);
    }

    private static List<List<EffectEntry>> effectPages() {
        List<List<EffectEntry>> current = effectPages;
        if (current == null) {
            current = buildEffectPages();
            effectPages = current;
        }
        return current;
    }

    private static void addEffectIngredient(Map<Holder<Potion>, EffectEntry> effects,
                                            Holder<Potion> potion, String messageKey, Item ingredient) {
        EffectEntry existing = effects.get(potion);
        if (existing == null) {
            effects.put(potion, new EffectEntry(potion, messageKey, List.of(ingredient)));
            return;
        }
        if (existing.ingredients().contains(ingredient)) {
            return;
        }
        List<Item> ingredients = new ArrayList<>(existing.ingredients());
        ingredients.add(ingredient);
        effects.put(potion, new EffectEntry(potion, existing.messageKey(), List.copyOf(ingredients)));
    }

    private static void baseRow(TotemManualPageRenderContext context, Item ingredient, int chance,
                                boolean unstable, int y) {
        potion(context, Potions.WATER, 35, y);
        plus(context, 55, y + 8);
        percentage(context, ingredient, 67, y);
        arrow(context, 101, y + 8, 9);
        if (AlchemyDiscoveryClientCache.has(ingredient, Potions.AWKWARD)) {
            potion(context, Potions.AWKWARD, 116, y);
        } else {
            unknownPotion(context, 116, y);
        }
        if (unstable) {
            text(context, "−20%", 137, y + 4, WARN);
        }
    }

    private static void modifier(TotemManualPageRenderContext context, Item ingredient, int chance,
                                 String label, int localX, int y) {
        percentage(context, ingredient, localX, y);
        centeredAt(context, label, localX + 15, y + 21, MUTED);
    }

    private static void outcomeRow(TotemManualPageRenderContext context, Item ingredient,
                                   List<MultiOutcomeBrewing.Outcome> outcomes, int y) {
        percentage(context, ingredient, 31, y);
        arrow(context, 66, y + 8, 10);
        int startX = outcomes.size() == 2 ? 100 : 87;
        for (int index = 0; index < outcomes.size(); index++) {
            MultiOutcomeBrewing.Outcome outcome = outcomes.get(index);
            if (AlchemyDiscoveryClientCache.has(ingredient, outcome.potion())) {
                potion(context, outcome.potion(), startX + index * 25, y);
            } else {
                unknownPotion(context, startX + index * 25, y);
            }
        }
        Component names = Component.empty();
        for (int index = 0; index < outcomes.size(); index++) {
            if (index > 0) {
                names = names.copy().append(Component.literal(" · "));
            }
            MultiOutcomeBrewing.Outcome outcome = outcomes.get(index);
            names = names.copy().append(AlchemyDiscoveryClientCache.has(ingredient, outcome.potion())
                    ? Component.translatable(outcome.messageKey())
                    : Component.translatable("book.deadrecall.alchemy_manual.diagram.unknown"));
        }
        centeredClipped(context, names, y + 20, MUTED, 150);
    }

    private static void variantRow(TotemManualPageRenderContext context, Item ingredient, int chance,
                                   Holder<Potion> base, Holder<Potion> result,
                                   String label, int y) {
        boolean discovered = AlchemyDiscoveryClientCache.has(ingredient, result);
        potion(context, base, 31, y);
        plus(context, 52, y + 8);
        percentage(context, ingredient, 64, y);
        arrow(context, 99, y + 8, 9);
        if (discovered) {
            potion(context, result, 114, y);
            text(context, "+10%", 135, y + 4, GOOD);
            centeredKeyClipped(context, label, y + 20, MUTED);
        } else {
            unknownPotion(context, 114, y);
            centeredKeyClipped(context, "book.deadrecall.alchemy_manual.diagram.unrecorded", y + 20, WARN);
        }
    }

    private static void recipe(TotemManualPageRenderContext context, List<ItemStack> ingredients,
                               ItemStack result, int y) {
        int startX = 27;
        for (int index = 0; index < ingredients.size(); index++) {
            stack(context, ingredients.get(index), startX + index * 23, y);
            if (index < ingredients.size() - 1) {
                plus(context, startX + 17 + index * 23, y + 8);
            }
        }
        int arrowX = startX + ingredients.size() * 23 + 1;
        arrow(context, arrowX, y + 8, 9);
        stack(context, result, arrowX + 15, y);
    }

    private static void percentage(TotemManualPageRenderContext context, Item item, int localX, int y) {
        stack(context, new ItemStack(item), localX, y);
        int chance = (int) Math.round(VanillaBrewingChance.chanceFor(new ItemStack(item)) * 100.0D);
        text(context, chance + "%", localX + 17, y + 5, INK);
    }

    private static void potion(TotemManualPageRenderContext context, Holder<Potion> potion, int localX, int y) {
        stack(context, PotionContents.createItemStack(Items.POTION, potion), localX, y);
    }

    private static void unknownPotion(TotemManualPageRenderContext context, int localX, int y) {
        stack(context, new ItemStack(Items.GLASS_BOTTLE), localX, y);
        text(context, "?", localX + 6, y + 4, WARN);
    }

    private static void item(TotemManualPageRenderContext context, Item item, int localX, int y) {
        stack(context, new ItemStack(item), localX, y);
    }

    private static void stack(TotemManualPageRenderContext context, ItemStack stack, int localX, int y) {
        int x = context.pageLeft() + localX;
        context.graphics().item(stack, x, y);
        if (inside(context, x, y, 16, 16)) {
            context.graphics().setTooltipForNextFrame(
                    context.font(), stack, context.mouseX(), context.mouseY());
        }
    }

    private static void arrow(TotemManualPageRenderContext context, int localX, int y, int length) {
        int x = context.pageLeft() + localX;
        context.graphics().fill(x, y, x + length + 1, y + 1, MUTED);
        context.graphics().fill(x + length - 2, y - 2, x + length + 1, y + 3, MUTED);
    }

    private static void plus(TotemManualPageRenderContext context, int localX, int y) {
        text(context, "+", localX, y - 4, MUTED);
    }

    private static void centered(TotemManualPageRenderContext context, String key, int y, int color) {
        context.graphics().centeredText(context.font(), Component.translatable(key),
                context.pageLeft() + 93, y, color);
    }

    private static void centeredAt(TotemManualPageRenderContext context, String key, int localX,
                                   int y, int color) {
        context.graphics().centeredText(context.font(), Component.translatable(key),
                context.pageLeft() + localX, y, color);
    }

    private static void centeredAtClipped(TotemManualPageRenderContext context, Component component,
                                          int localX, int y, int color, int maxWidth) {
        String value = component.getString();
        if (context.font().width(value) > maxWidth) {
            value = context.font().plainSubstrByWidth(
                    value, maxWidth - context.font().width("…")) + "…";
        }
        context.graphics().centeredText(context.font(), value, context.pageLeft() + localX, y, color);
    }

    private static void centeredKeyClipped(TotemManualPageRenderContext context,
                                           String key, int y, int color) {
        centeredClipped(context, Component.translatable(key), y, color, 150);
    }

    private static void centeredClipped(TotemManualPageRenderContext context, Component component,
                                        int y, int color, int maxWidth) {
        String text = component.getString();
        if (context.font().width(text) > maxWidth) {
            text = context.font().plainSubstrByWidth(text, maxWidth - context.font().width("…")) + "…";
        }
        context.graphics().centeredText(context.font(), text, context.pageLeft() + 93, y, color);
    }

    private static void text(TotemManualPageRenderContext context, String literal,
                             int localX, int y, int color) {
        context.graphics().text(context.font(), literal, context.pageLeft() + localX, y, color, false);
    }

    private static boolean inside(TotemManualPageRenderContext context,
                                  int x, int y, int width, int height) {
        return context.mouseX() >= x && context.mouseX() < x + width
                && context.mouseY() >= y && context.mouseY() < y + height;
    }

    private record EffectEntry(Holder<Potion> potion, String messageKey, List<Item> ingredients) {
    }
}
