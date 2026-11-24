package appeng.server.testplots;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import io.netty.buffer.Unpooled;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.Filterable;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.network.connection.ConnectionType;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.MolecularAssemblerBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.network.clientbound.MEInventoryUpdatePacket;
import appeng.menu.me.common.GridInventoryEntry;
import appeng.server.testworld.PlotBuilder;

/**
 * Regression tests for upstream issues that do not reproduce on this version.
 */
@TestPlotClass
public final class IssueRegressionTestPlots {
    private IssueRegressionTestPlots() {
    }

    /**
     * Combining two damaged cutting knives in the crafting grid (vanilla repair recipe) must not leave a knife behind.
     * <a href="https://github.com/AppliedEnergistics/Applied-Energistics-2/issues/8623">Issue 8623</a>.
     */
    @TestPlot("cutting_knife_repair_no_dupe")
    public static void cuttingKnifeRepairNoDupe(PlotBuilder plot) {
        plot.block(BlockPos.ZERO, Blocks.STONE);
        plot.test(helper -> {
            var level = helper.getLevel();
            for (var knife : List.of(AEItems.CERTUS_QUARTZ_KNIFE.stack(), AEItems.NETHER_QUARTZ_KNIFE.stack())) {
                var a = knife.copy();
                a.setDamageValue(30);
                var b = knife.copy();
                b.setDamageValue(30);
                var items = NonNullList.withSize(9, ItemStack.EMPTY);
                items.set(0, a);
                items.set(1, b);
                var input = CraftingInput.of(3, 3, items);
                var recipe = level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level);
                helper.check(recipe.isPresent(), "two knives should be repairable in the crafting grid");
                var result = recipe.get().value().assemble(input);
                helper.check(result.is(knife.getItem()) && result.getCount() == 1,
                        "repair result should be one knife, got " + result);
                var remaining = recipe.get().value().getRemainingItems(input);
                for (var stack : remaining) {
                    helper.check(stack.isEmpty(), "repairing knives leaves " + stack + " in the grid (dupe)");
                }
            }
            helper.succeed();
        });
    }

    /**
     * Items in a molecular assembler that is saved and loaded mid-craft (server restart) must still be crafted and
     * pushed out. <a href="https://github.com/AppliedEnergistics/Applied-Energistics-2/issues/8391">Issue 8391</a>.
     */
    @TestPlot("molecular_assembler_resumes_after_reload")
    public static void molecularAssemblerResumesAfterReload(PlotBuilder plot) {
        // No power until after the reload, so the craft cannot finish before it
        plot.block(BlockPos.ZERO, Blocks.AIR);
        var assemblerPos = new BlockPos(0, 1, 0);
        plot.blockEntity(assemblerPos, AEBlocks.MOLECULAR_ASSEMBLER, assembler -> {
            var items = NonNullList.withSize(9, ItemStack.EMPTY);
            items.set(0, new ItemStack(Items.OAK_LOG));
            var input = CraftingInput.of(3, 3, items);
            var level = (ServerLevel) assembler.getLevel();
            var recipe = level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level).get();
            var sparseInputs = new ItemStack[9];
            for (int i = 0; i < 9; ++i) {
                sparseInputs[i] = items.get(i);
            }
            var encoded = PatternDetailsHelper.encodeCraftingPattern(recipe, sparseInputs,
                    new ItemStack(Items.OAK_PLANKS, 4), false, false);
            var details = PatternDetailsHelper.decodePattern(encoded, level);
            var table = new KeyCounter[] { new KeyCounter() };
            table[0].add(AEItemKey.of(Items.OAK_LOG), 1);
            if (!assembler.pushPattern(details, table, Direction.UP)) {
                throw new IllegalStateException("assembler refused the pattern");
            }
        });

        plot.test(helper -> {
            var level = helper.getLevel();
            var absPos = helper.absolutePos(assemblerPos);
            helper.startSequence()
                    .thenExecute(() -> {
                        var assembler = helper.getBlockEntity(assemblerPos, MolecularAssemblerBlockEntity.class);
                        var inv = assembler.getInternalInventory();
                        helper.check(inv.getStackInSlot(9).isEmpty(), "crafted before the reload, test is invalid");
                        helper.check(inv.getStackInSlot(0).is(Items.OAK_LOG), "log not in the assembler");

                        // Simulate a server restart: save the block entity, remove it, load a fresh one from the data
                        var out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                                level.registryAccess());
                        assembler.saveWithId(out);
                        var data = out.buildResult();
                        var state = level.getBlockState(absPos);
                        level.removeBlockEntity(absPos);
                        var loaded = BlockEntity.loadStatic(absPos, state, data, level.registryAccess());
                        helper.check(loaded != null, "assembler could not be loaded");
                        level.setBlockEntity(loaded);
                        helper.setBlock(BlockPos.ZERO, AEBlocks.CREATIVE_ENERGY_CELL.block());
                    })
                    .thenWaitUntil(() -> {
                        var assembler = helper.getBlockEntity(assemblerPos, MolecularAssemblerBlockEntity.class);
                        var inv = assembler.getInternalInventory();
                        var output = inv.getStackInSlot(9);
                        helper.check(output.is(Items.OAK_PLANKS) && output.getCount() == 4,
                                "assembler did not finish the craft after the reload; slot 9: " + output
                                        + ", slot 0: " + inv.getStackInSlot(0));
                    })
                    .thenSucceed();
        });
    }

    /**
     * A terminal inventory update containing a very large item (a written book with megabytes of text) must still
     * deliver every other entry.
     * <a href="https://github.com/AppliedEnergistics/Applied-Energistics-2/issues/8767">Issue 8767</a>.
     */
    @TestPlot("me_inventory_update_oversized_item")
    public static void meInventoryUpdateOversizedItem(PlotBuilder plot) {
        plot.block(BlockPos.ZERO, Blocks.STONE);
        plot.test(helper -> {
            var registries = helper.getLevel().registryAccess();

            // ~2.4 MB of text over 4 pages; each NBT string stays under the 64k modified-UTF-8 limit
            var chunk = "x".repeat(60000);
            var pages = new ArrayList<Filterable<Component>>();
            for (int p = 0; p < 4; p++) {
                var page = Component.literal("page " + p);
                for (int i = 0; i < 10; i++) {
                    page.append(Component.literal(chunk));
                }
                pages.add(Filterable.passThrough(page));
            }
            var book = new ItemStack(Items.WRITTEN_BOOK);
            book.set(DataComponents.WRITTEN_BOOK_CONTENT,
                    new WrittenBookContent(Filterable.passThrough("big"), "test", 0, pages, true));

            var expected = new HashSet<AEKey>();
            var builder = MEInventoryUpdatePacket.builder(1, true, registries);
            long serial = 1;
            for (var item : List.of(Items.STONE, Items.DIRT, Items.OAK_LOG, Items.DIAMOND, Items.BOOK)) {
                var key = AEItemKey.of(item);
                expected.add(key);
                builder.add(new GridInventoryEntry(serial++, key, 64, 0, false));
            }
            var bookKey = AEItemKey.of(book);
            expected.add(bookKey);
            builder.add(new GridInventoryEntry(serial++, bookKey, 1, 0, false));
            for (var item : List.of(Items.IRON_INGOT, Items.GOLD_INGOT, Items.COAL)) {
                var key = AEItemKey.of(item);
                expected.add(key);
                builder.add(new GridInventoryEntry(serial++, key, 64, 0, false));
            }

            var received = new HashSet<AEKey>();
            for (var packet : builder.build()) {
                var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries, ConnectionType.NEOFORGE);
                MEInventoryUpdatePacket.STREAM_CODEC.encode(buf, packet);
                var decoded = MEInventoryUpdatePacket.STREAM_CODEC.decode(buf);
                for (var entry : decoded.getActualEntries()) {
                    received.add(entry.getWhat());
                }
            }
            helper.check(received.equals(expected), "entries lost in transit: expected " + expected.size()
                    + ", got " + received.size());
            helper.succeed();
        });
    }
}
