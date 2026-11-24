package appeng.server.testplots;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import appeng.server.testworld.PlotBuilder;

@TestPlotClass
public final class PatternProviderPlots {
    private PatternProviderPlots() {
    }

    /**
     * Tests that pattern providers pushing into each other do not cause an infinite loop on the server. Regression test
     * for <a href="https://github.com/AppliedEnergistics/Applied-Energistics-2/issues/7590">issue 7590</a>.
     */
    @TestPlot("pattern_provider_loop")
    public static void patternProviderLoop(PlotBuilder plot) {
        plot.creativeEnergyCell("0 0 0");
        plot.cable("0 1 0");
        plot.block("1 0 0", AEBlocks.PATTERN_PROVIDER);
        plot.cable("1 1 0").part(Direction.DOWN, AEParts.STORAGE_BUS);
        plot.block("-1 0 0", AEBlocks.PATTERN_PROVIDER);
        plot.cable("-1 1 0").part(Direction.DOWN, AEParts.STORAGE_BUS);

        plot.test(helper -> {
            helper.startSequence()
                    .thenIdle(5)
                    .thenExecute(() -> {
                        var pp = helper.getBlockEntity(new BlockPos(1, 0, 0), PatternProviderBlockEntity.class);
                        pp.getLogic().getReturnInv().insert(0, AEItemKey.of(Items.ANDESITE), 1, Actionable.MODULATE);
                    })
                    .thenIdle(5)
                    .thenSucceed();
        });
    }

    /**
     * A pattern provider without a channel refuses every push, so its patterns must not be offered to the grid. Once it
     * gets a channel, its patterns must become craftable. Regression test for
     * <a href="https://github.com/AppliedEnergistics/Applied-Energistics-2/issues/8409">issue 8409</a>.
     */
    @TestPlot("pattern_provider_without_channel_offers_no_patterns")
    public static void patternProviderWithoutChannelOffersNoPatterns(PlotBuilder plot) {
        var output = AEItemKey.of(Items.STONE);
        // Ad-hoc network (no controller) with 9 channel-requiring devices: more than the 8 channels it can carry, so
        // none of them gets a channel.
        plot.creativeEnergyCell("-1 0 0");
        plot.blockEntity("0 0 0", AEBlocks.PATTERN_PROVIDER, provider -> {
            var pattern = PatternDetailsHelper.encodeProcessingPattern(
                    List.of(new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1)),
                    List.of(new GenericStack(output, 1)));
            provider.getLogic().getPatternInv().addItems(pattern);
        });
        plot.block("[1,8] 0 0", AEBlocks.PATTERN_PROVIDER);

        plot.test(helper -> {
            helper.startSequence()
                    .thenWaitUntil(() -> {
                        var grid = helper.getGrid(BlockPos.ZERO);
                        helper.check(!grid.getPathingService().isNetworkBooting(), "network is still booting");
                        helper.check(grid.getEnergyService().isNetworkPowered(), "network is not powered");
                    })
                    .thenExecute(() -> {
                        var grid = helper.getGrid(BlockPos.ZERO);
                        helper.check(!helper.getGridNode(BlockPos.ZERO).isActive(),
                                "pattern provider should have no channel");
                        helper.check(!grid.getCraftingService().isCraftable(output),
                                "pattern of a provider without a channel is offered as craftable");
                        // Remove one device: the 8 left all get a channel.
                        helper.destroyBlock(new BlockPos(8, 0, 0));
                    })
                    .thenWaitUntil(() -> {
                        var grid = helper.getGrid(BlockPos.ZERO);
                        helper.check(helper.getGridNode(BlockPos.ZERO).isActive(),
                                "pattern provider should have a channel");
                        helper.check(grid.getCraftingService().isCraftable(output),
                                "pattern of a provider with a channel is not craftable");
                    })
                    .thenSucceed();
        });
    }

    /**
     * A processing pattern with three different inputs must not be pushed into a chest that has room for only one of
     * them: the provider used to check each input on its own (each fits into the free slot), pushed the first one and
     * kept the other two in its send list, stuck until the chest makes room. Regression test for
     * <a href="https://github.com/AppliedEnergistics/Applied-Energistics-2/issues/9035">issue 9035</a>.
     */
    @TestPlot("pattern_provider_push_is_all_or_nothing")
    public static void patternProviderPushIsAllOrNothing(PlotBuilder plot) {
        var ppPos = BlockPos.ZERO;
        var chestPos = ppPos.above();
        plot.creativeEnergyCell(ppPos.below());
        plot.blockEntity(ppPos, AEBlocks.PATTERN_PROVIDER, pp -> pp.getLogic().getPatternInv().addItems(
                PatternDetailsHelper.encodeProcessingPattern(threeInputs(), List.of(
                        new GenericStack(AEItemKey.of(Items.DIAMOND), 1)))));
        // 26 of the chest's 27 slots are full
        var filler = new ItemStack[26];
        for (int i = 0; i < filler.length; i++) {
            filler[i] = new ItemStack(Items.COBBLESTONE, 64);
        }
        plot.chest(chestPos, filler);

        plot.test(helper -> helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    var pp = helper.getBlockEntity(ppPos, PatternProviderBlockEntity.class).getLogic();
                    var details = PatternDetailsHelper.decodePattern(pp.getPatternInv().getStackInSlot(0),
                            (ServerLevel) helper.getLevel());
                    helper.check(details != null, "pattern did not decode", ppPos);
                    var inputs = new KeyCounter[3];
                    var stacks = threeInputs();
                    for (int i = 0; i < 3; i++) {
                        inputs[i] = new KeyCounter();
                        inputs[i].add(stacks.get(i).what(), stacks.get(i).amount());
                    }

                    var pushed = pp.pushPattern(details, inputs);

                    var chest = helper.countContainerContentAt(chestPos);
                    for (var input : stacks) {
                        helper.check(chest.get(input.what()) == 0,
                                "partial push: the chest got " + chest.get(input.what()) + " " + input.what(),
                                chestPos);
                    }
                    helper.check(!pushed, "push reported success although the chest has room for one input only",
                            ppPos);
                    helper.check(!pp.isBusy(), "provider is busy with a stuck send list", ppPos);

                    // Control: with room for all three inputs the same push goes through
                    var chestBe = (Container) helper.getBlockEntity(chestPos,
                            BlockEntity.class);
                    chestBe.setItem(0, ItemStack.EMPTY);
                    chestBe.setItem(1, ItemStack.EMPTY);
                    helper.check(pp.pushPattern(details, inputs), "push with room for all inputs failed", ppPos);
                    chest = helper.countContainerContentAt(chestPos);
                    for (var input : stacks) {
                        helper.check(chest.get(input.what()) == 1, "missing " + input.what(), chestPos);
                    }
                    helper.check(!pp.isBusy(), "provider is busy after a full push", ppPos);
                })
                .thenSucceed());
    }

    private static List<GenericStack> threeInputs() {
        return List.of(
                new GenericStack(AEItemKey.of(Items.REDSTONE), 1),
                new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1),
                new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1));
    }
}
