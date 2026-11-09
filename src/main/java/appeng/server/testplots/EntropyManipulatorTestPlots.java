package appeng.server.testplots;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import appeng.api.config.Actionable;
import appeng.core.definitions.AEItems;
import appeng.server.testworld.PlotBuilder;
import appeng.server.testworld.PlotTestHelper;

@TestPlotClass
public final class EntropyManipulatorTestPlots {
    private EntropyManipulatorTestPlots() {
    }

    /**
     * Regression test for https://github.com/AppliedEnergistics/Applied-Energistics-2/issues/8387: using the entropy
     * manipulator on a waterlogged block must not apply the water recipes to it (which replaced the whole block with
     * ice or air).
     */
    @TestPlot("entropy_manipulator_waterlogged_block")
    public static void waterloggedBlock(PlotBuilder plot) {
        var water = BlockPos.ZERO;
        var coolSlab = new BlockPos(2, 0, 0);
        var heatSlab = new BlockPos(4, 0, 0);
        var waterloggedSlab = Blocks.STONE_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true);

        // Stone basin so the water source does not spread
        plot.block("[-1,5] [-1,0] [-1,1]", Blocks.STONE);
        plot.block(water, Blocks.WATER);
        plot.blockState(coolSlab, waterloggedSlab);
        plot.blockState(heatSlab, waterloggedSlab);

        plot.test(helper -> {
            var result = useManipulator(helper, water, true);
            helper.check(result.consumesAction(), "entropy manipulator was not used on water: " + result, water);
            helper.assertBlockPresent(Blocks.ICE, water);

            useManipulator(helper, coolSlab, true);
            helper.assertBlockPresent(Blocks.STONE_SLAB, coolSlab);

            useManipulator(helper, heatSlab, false);
            helper.assertBlockPresent(Blocks.STONE_SLAB, heatSlab);

            helper.succeed();
        });
    }

    private static InteractionResult useManipulator(PlotTestHelper helper, BlockPos pos, boolean cool) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var absPos = helper.absolutePos(pos);
        // Look straight down at the block from just above its fluid surface (the manipulator ray-traces from the eyes),
        // so nothing else in the test structure can be in the way
        player.snapTo(absPos.getX() + 0.5, absPos.getY() + 0.95 - player.getEyeHeight(), absPos.getZ() + 0.5, 0, 90);
        player.setShiftKeyDown(cool);

        var item = AEItems.ENTROPY_MANIPULATOR.get();
        var stack = AEItems.ENTROPY_MANIPULATOR.stack();
        item.injectAEPower(stack, item.getAEMaxPower(stack), Actionable.MODULATE);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);

        var hit = new BlockHitResult(Vec3.atCenterOf(absPos), Direction.UP, absPos, false);
        return item.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }
}
