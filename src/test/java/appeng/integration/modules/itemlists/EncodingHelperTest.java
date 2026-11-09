package appeng.integration.modules.itemlists;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.util.BootstrapMinecraft;

/**
 * https://github.com/AppliedEnergistics/Applied-Energistics-2/issues/9063: transferring a crafting recipe whose
 * ingredient requires data components must encode the stack the recipe viewer shows, not the item's default stack.
 */
@BootstrapMinecraft
class EncodingHelperTest {
    private static ItemStack swiftnessPotion() {
        var stack = new ItemStack(Items.POTION);
        stack.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.SWIFTNESS));
        return stack;
    }

    private static Ingredient swiftnessIngredient() {
        return DataComponentIngredient.of(false, DataComponents.POTION_CONTENTS,
                new PotionContents(Potions.SWIFTNESS), Items.POTION);
    }

    @Test
    void keepsComponentsOfShownIngredientWhenNetworkIsEmpty() {
        var shown = List.of(
                List.of(Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.GLASS_BOTTLE)))),
                List.of(Objects.requireNonNull(GenericStack.fromItemStack(swiftnessPotion()))));

        var result = EncodingHelper.findBestCraftingIngredient(swiftnessIngredient(), Map.of(), shown,
                stack -> true);

        assertTrue(swiftnessIngredient().test(result), "encoded stack does not satisfy the ingredient: " + result);
        assertTrue(ItemStack.isSameItemSameComponents(swiftnessPotion(), result), "components lost: " + result);
    }

    @Test
    void prefersMatchingNetworkStack() {
        var networkStack = swiftnessPotion();
        networkStack.set(DataComponents.CUSTOM_NAME, Component.literal("stored"));
        Map<AEKey, Integer> network = Map.of(AEItemKey.of(networkStack), 0);

        var result = EncodingHelper.findBestCraftingIngredient(swiftnessIngredient(), network,
                List.of(List.of(Objects.requireNonNull(GenericStack.fromItemStack(swiftnessPotion())))),
                stack -> true);

        assertTrue(ItemStack.isSameItemSameComponents(networkStack, result));
    }

    @Test
    void plainIngredientStillUsesDefaultStack() {
        var result = EncodingHelper.findBestCraftingIngredient(Ingredient.of(Items.STICK), Map.of(), List.of(),
                stack -> true);

        assertEquals(Items.STICK, result.getItem());
        assertTrue(ItemStack.isSameItemSameComponents(new ItemStack(Items.STICK), result));
    }
}
