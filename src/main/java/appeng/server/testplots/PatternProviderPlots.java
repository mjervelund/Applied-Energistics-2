package appeng.server.testplots;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Items;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
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
}
