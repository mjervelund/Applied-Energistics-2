package appeng.server.testplots;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

import appeng.core.definitions.AEParts;
import appeng.server.testworld.PlotBuilder;
import appeng.util.Platform;

@TestPlotClass
public final class AnvilTestPlots {
    private AnvilTestPlots() {
    }

    /**
     * Annihilation planes stack but can be enchanted, and AE2 lets the anvil combine two planes. Vanilla's anvil
     * assumes enchantable items don't stack: it consumes the whole right-hand stack. An anvil operation must therefore
     * only ever enchant or combine single planes, so nothing is duplicated or destroyed.
     */
    @TestPlot("anvil_enchants_single_planes")
    public static void anvilEnchantsSinglePlanes(PlotBuilder plot) {
        plot.block(BlockPos.ZERO, Blocks.ANVIL);
        plot.test(helper -> {
            var player = helper.makeMockPlayer(GameType.SURVIVAL);

            // Controls: single planes still get enchanted and combined.
            var result = anvil(player, plane(helper, 1, 0), book(helper, 3));
            helper.check(result.getCount() == 1 && fortune(helper, result) == 3,
                    "plane + Fortune III book should give one Fortune III plane, got " + describe(helper, result));
            result = anvil(player, plane(helper, 1, 1), plane(helper, 1, 1));
            helper.check(result.getCount() == 1 && fortune(helper, result) == 2,
                    "Fortune I plane + Fortune I plane should give one Fortune II plane, got "
                            + describe(helper, result));

            // A stack of planes on the left must not all be enchanted by one book or plane (#8177).
            result = anvil(player, plane(helper, 64, 0), book(helper, 3));
            helper.check(result.isEmpty(), "64 planes + Fortune III book gave " + describe(helper, result));
            result = anvil(player, plane(helper, 64, 1), plane(helper, 1, 1));
            helper.check(result.isEmpty(), "64 Fortune I planes + Fortune I plane gave " + describe(helper, result));

            // A stack of planes on the right must not be consumed whole for one combined plane.
            var menu = new AnvilMenu(0, player.getInventory(), ContainerLevelAccess.NULL);
            menu.getSlot(AnvilMenu.INPUT_SLOT).set(plane(helper, 1, 1));
            menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(plane(helper, 64, 1));
            result = menu.getSlot(AnvilMenu.RESULT_SLOT).getItem().copy();
            if (!result.isEmpty()) {
                menu.getSlot(AnvilMenu.RESULT_SLOT).onTake(player, result);
                helper.fail("Fortune I plane + 64 Fortune I planes gave " + describe(helper, result)
                        + ", taking it left " + menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem().getCount()
                        + " of 64 planes in the right slot");
            }

            helper.succeed();
        });
    }

    private static ItemStack anvil(Player player, ItemStack left, ItemStack right) {
        var menu = new AnvilMenu(0, player.getInventory(), ContainerLevelAccess.NULL);
        menu.getSlot(AnvilMenu.INPUT_SLOT).set(left);
        menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(right);
        return menu.getSlot(AnvilMenu.RESULT_SLOT).getItem().copy();
    }

    private static ItemStack plane(GameTestHelper helper, int count, int fortune) {
        var stack = AEParts.ANNIHILATION_PLANE.stack(count);
        if (fortune > 0) {
            stack.enchant(Platform.getEnchantment(helper.getLevel(), Enchantments.FORTUNE), fortune);
        }
        return stack;
    }

    private static ItemStack book(GameTestHelper helper, int fortune) {
        var enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(Platform.getEnchantment(helper.getLevel(), Enchantments.FORTUNE), fortune);
        var book = new ItemStack(Items.ENCHANTED_BOOK);
        book.set(DataComponents.STORED_ENCHANTMENTS, enchantments.toImmutable());
        return book;
    }

    private static int fortune(GameTestHelper helper, ItemStack stack) {
        return EnchantmentHelper.getItemEnchantmentLevel(
                Platform.getEnchantment(helper.getLevel(), Enchantments.FORTUNE), stack);
    }

    private static String describe(GameTestHelper helper, ItemStack stack) {
        return stack.isEmpty() ? "nothing" : stack.getCount() + " planes with Fortune " + fortune(helper, stack);
    }
}
