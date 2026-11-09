package appeng.server.testplots;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.core.definitions.AEParts;
import appeng.server.testworld.PlotBuilder;

@TestPlotClass
public class FormationPlaneTestPlots {

    /**
     * Placing an item whose use replaces it with a remainder (a powder snow bucket leaves an empty bucket) must not
     * destroy the remainder. Regression test for
     * https://github.com/AppliedEnergistics/Applied-Energistics-2/issues/7349
     */
    @TestPlot("formation_plane_keeps_item_remainder")
    public static void formationPlaneKeepsItemRemainder(PlotBuilder plot) {
        var origin = BlockPos.ZERO;
        var placePos = origin.above().east();
        plot.creativeEnergyCell(origin);
        // Include the block in front of the plane in the plot
        plot.block(placePos, Blocks.AIR);
        plot.cable(origin.above())
                .part(Direction.EAST, AEParts.FORMATION_PLANE, plane -> {
                    plane.getConfig().insert(0, AEItemKey.of(Items.POWDER_SNOW_BUCKET), 1, Actionable.MODULATE);
                });

        plot.test(helper -> {
            helper.startSequence()
                    .thenWaitUntil(() -> helper.getGrid(origin))
                    .thenIdle(5)
                    .thenExecute(() -> {
                        var inserted = helper.getGrid(origin).getStorageService().getInventory().insert(
                                AEItemKey.of(Items.POWDER_SNOW_BUCKET), 1, Actionable.MODULATE,
                                IActionSource.empty());
                        helper.check(inserted == 1, "the plane should report 1 bucket used, got " + inserted);
                        helper.assertBlockPresent(Blocks.POWDER_SNOW, placePos);
                        // The empty bucket is dropped in front of the plane
                        helper.assertItemEntityCountIs(Items.BUCKET, placePos, 1, 1);
                        helper.assertItemEntityCountIs(Items.POWDER_SNOW_BUCKET, placePos, 1, 0);
                    })
                    .thenSucceed();
        });
    }
}
