package appeng.server.testplots;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEItemKey;
import appeng.block.misc.GrowthAcceleratorBlock;
import appeng.blockentity.misc.GrowthAcceleratorBlockEntity;
import appeng.blockentity.networking.EnergyCellBlockEntity;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.server.testworld.PlotBuilder;
import appeng.server.testworld.PlotTestHelper;

@TestPlotClass
public class ExternalEnergyTestPlots {
    private static final BlockPos ORIGIN = BlockPos.ZERO;

    @TestPlot("fe_energy_acceptor_block")
    public static void testEnergyAcceptorBlock(PlotBuilder plot) {
        placeForgeEnergyGenerator(plot);
        plot.block(ORIGIN, AEBlocks.ENERGY_ACCEPTOR);
        testGridIsReceivingEnergy(plot);
    }

    @TestPlot("fe_energy_acceptor_part")
    public static void testEnergyAcceptorPart(PlotBuilder plot) {
        placeForgeEnergyGenerator(plot);
        plot.cable(ORIGIN).part(Direction.DOWN, AEParts.ENERGY_ACCEPTOR);
        testGridIsReceivingEnergy(plot);
    }

    @TestPlot("fe_controller")
    public static void testController(PlotBuilder plot) {
        placeForgeEnergyGenerator(plot);
        plot.block(ORIGIN, AEBlocks.CONTROLLER);
        testGridIsReceivingEnergy(plot);
    }

    @TestPlot("fe_inscriber")
    public static void testInscriber(PlotBuilder plot) {
        placeForgeEnergyGenerator(plot);
        plot.blockEntity(ORIGIN, AEBlocks.INSCRIBER, inscriber -> {
            inscriber.getInternalInventory().insertItem(0, AEItems.SILICON_PRESS.stack(), false);
            inscriber.getInternalInventory().insertItem(2, AEItems.SILICON.stack(), false);
        });
        plot.test(helper -> helper.startSequence()
                .thenWaitUntil(helper::checkAllInitialized)
                .thenWaitUntil(() -> {
                    var content = helper.countContainerContentAt(ORIGIN);
                    helper.assertEquals(ORIGIN, 1L, content.get(AEItemKey.of(AEItems.SILICON_PRINT)));
                })
                // Ensure that after 1 second, the grid still has no energy
                .thenExecuteAfter(20, () -> checkGridHasNoEnergy(helper))
                .thenSucceed());
    }

    @TestPlot("fe_chest")
    public static void testChest(PlotBuilder plot) {
        placeForgeEnergyGenerator(plot);
        plot.blockEntity(ORIGIN, AEBlocks.ME_CHEST, chest -> {
            chest.getInternalInventory().addItems(AEItems.ITEM_CELL_1K.stack());
        });
        plot.test(helper -> helper.startSequence()
                .thenWaitUntil(helper::checkAllInitialized)
                .thenWaitUntil(() -> {
                    var chest = helper.getBlockEntity(ORIGIN, MEChestBlockEntity.class);
                    helper.check(chest.isPowered(), "should be powered", ORIGIN);
                    var linkStatus = chest.getLinkStatus();
                    helper.check(linkStatus.connected(), "link status should be connected: "
                            + linkStatus.statusDescription(), ORIGIN);
                })
                // Ensure that after 1 second, the grid still has no energy
                .thenExecuteAfter(20, () -> checkGridHasNoEnergy(helper))
                .thenSucceed());
    }

    // due to the chargers randomness, this can take longer than the default time
    @TestPlot(value = "fe_charger", maxTicks = 15 * 20)
    public static void testCharger(PlotBuilder plot) {
        placeForgeEnergyGenerator(plot);
        // Insert an uncharged crystal to test it gets charged
        plot.blockEntity(ORIGIN, AEBlocks.CHARGER, charger -> {
            charger.getInternalInventory().insertItem(0, AEItems.CERTUS_QUARTZ_CRYSTAL.stack(), false);
        });
        plot.test(helper -> helper.startSequence()
                .thenWaitUntil(helper::checkAllInitialized)
                .thenWaitUntil(() -> {
                    var content = helper.countContainerContentAt(ORIGIN);
                    helper.assertEquals(ORIGIN, 1L, content.get(AEItemKey.of(AEItems.CERTUS_QUARTZ_CRYSTAL_CHARGED)));
                })
                // Ensure that after 1 second, the grid still has no energy
                .thenExecuteAfter(20, () -> checkGridHasNoEnergy(helper))
                .thenSucceed());
    }

    @TestPlot("fe_growth_accelerator")
    public static void testGrowthAccelerator(PlotBuilder plot) {
        placeForgeEnergyGenerator(plot);
        plot.blockState(ORIGIN, AEBlocks.GROWTH_ACCELERATOR.block().defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.UP));
        plot.test(helper -> helper.startSequence()
                .thenWaitUntil(helper::checkAllInitialized)
                .thenWaitUntil(() -> {
                    var accel = helper.getBlockEntity(ORIGIN, GrowthAcceleratorBlockEntity.class);
                    helper.check(accel.isPowered(), "should be powered", ORIGIN);
                })
                .thenWaitUntil(() -> helper.assertBlockProperty(ORIGIN, GrowthAcceleratorBlock.POWERED, true))
                // Ensure that after 1 second, the grid still has no energy
                .thenExecuteAfter(20, () -> checkGridHasNoEnergy(helper))
                .thenSucceed());
    }

    /**
     * Two acceptors on one grid both offered the grid's full demand inside one transaction: the second one's commit can
     * only push part of it. The leftover must not block that acceptor forever
     * (https://github.com/AppliedEnergistics/Applied-Energistics-2/issues/9041).
     */
    @TestPlot("fe_acceptor_recovers_from_leftover")
    public static void testAcceptorRecoversFromLeftover(PlotBuilder plot) {
        var first = ORIGIN;
        var second = ORIGIN.east();
        plot.block(first, AEBlocks.ENERGY_ACCEPTOR);
        plot.block(second, AEBlocks.ENERGY_ACCEPTOR);
        plot.block(ORIGIN.west(), AEBlocks.ENERGY_CELL);
        plot.test(helper -> {
            var level = helper.getLevel();
            Supplier<EnergyHandler> firstHandler = () -> level.getCapability(Capabilities.Energy.BLOCK,
                    helper.absolutePos(first), Direction.UP);
            Supplier<EnergyHandler> secondHandler = () -> level.getCapability(Capabilities.Energy.BLOCK,
                    helper.absolutePos(second), Direction.UP);
            Supplier<IEnergyService> energy = () -> helper.getGrid(ORIGIN).getEnergyService();
            // The grid's stored-power figure is only refreshed periodically, so look at the cell itself.
            Supplier<Double> cellPower = () -> helper.getBlockEntity(ORIGIN.west(), EnergyCellBlockEntity.class)
                    .getAECurrentPower();
            helper.startSequence()
                    .thenWaitUntil(helper::checkAllInitialized)
                    .thenExecute(() -> {
                        try (var tx = Transaction.open(null)) {
                            var a = firstHandler.get().insert(10_000_000, tx);
                            var b = secondHandler.get().insert(10_000_000, tx);
                            helper.check(a > 0 && b > 0, "both acceptors accept the demand (" + a + ", " + b + ")",
                                    ORIGIN);
                            tx.commit();
                        }
                        // Empty the grid so that it has room again.
                        energy.get().extractAEPower(Double.MAX_VALUE, Actionable.MODULATE, PowerMultiplier.ONE);
                    })
                    .thenWaitUntil(() -> helper.check(energy.get().getEnergyDemand(1) > 0, "grid wants power", ORIGIN))
                    .thenExecute(() -> {
                        helper.check(cellPower.get() < 1, "grid drained", ORIGIN);
                        // Generators commonly simulate first and only commit when something is accepted. The
                        // power the second acceptor already took must reach the grid even then.
                        try (var tx = Transaction.open(null)) {
                            secondHandler.get().insert(1000, tx);
                        }
                        helper.check(cellPower.get() > 0,
                                "power accepted by the second acceptor never reached the grid", second);
                        energy.get().extractAEPower(Double.MAX_VALUE, Actionable.MODULATE, PowerMultiplier.ONE);
                    })
                    .thenWaitUntil(() -> {
                        try (var tx = Transaction.open(null)) {
                            var accepted = secondHandler.get().insert(1000, tx);
                            helper.check(accepted > 0,
                                    "second acceptor stopped accepting power after a partial commit", second);
                        }
                    })
                    .thenSucceed();
        });
    }

    private static void testGridIsReceivingEnergy(PlotBuilder plot) {
        plot.test(helper -> helper.startSequence()
                .thenWaitUntil(helper::checkAllInitialized)
                .thenWaitUntil(() -> {
                    var grid = helper.getGrid(BlockPos.ZERO);
                    var power = grid.getEnergyService().getStoredPower();
                    helper.check(power > 0, "grid should contain energy", ORIGIN);
                })
                .thenSucceed());
    }

    private static void checkGridHasNoEnergy(PlotTestHelper helper) {
        var grid = helper.getGrid(BlockPos.ZERO);
        var power = grid.getEnergyService().getStoredPower();
        helper.check(power == 0, "grid should not contain energy (has " + power + ")", ORIGIN);
    }

    private static void placeForgeEnergyGenerator(PlotBuilder plot) {
        plot.blockEntity(ORIGIN.below(), AEBlocks.DEBUG_ENERGY_GEN, generator -> {
            generator.setGenerationRate(128);
        });
    }
}
