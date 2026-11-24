package appeng.server.testplots;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueOutput;

import appeng.block.qnb.QuantumBaseBlock;
import appeng.blockentity.qnb.QuantumBridgeBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.server.testworld.PlotBuilder;
import appeng.server.testworld.PlotTestHelper;

@TestPlotClass
public final class QnbTestPlots {
    private QnbTestPlots() {
    }

    @TestPlot("simple_qnb_link")
    public static void simpleQnbLink(PlotBuilder plot) {
        var qnbA = BlockPos.ZERO.above();
        var qnbB = qnbA.east(4);

        qnbRing(plot, qnbA);
        qnbRing(plot, qnbB);

        // Woosh!
        plot.test(helper -> {
            helper.startSequence()
                    // Wait until both QNBs have formed
                    .thenWaitUntil(() -> {
                        ringAround(qnbA, pos -> helper.assertBlockProperty(pos, QuantumBaseBlock.FORMED, true));
                        helper.assertBlockProperty(qnbA, QuantumBaseBlock.FORMED, true);
                        ringAround(qnbB, pos -> helper.assertBlockProperty(pos, QuantumBaseBlock.FORMED, true));
                        helper.assertBlockProperty(qnbB, QuantumBaseBlock.FORMED, true);
                    })
                    // Place singularities
                    .thenExecute(() -> {
                        var singularities = AEItems.QUANTUM_ENTANGLED_SINGULARITY.stack();
                        QuantumBridgeBlockEntity.assignFrequency(singularities);

                        var coreA = getCore(helper, qnbA);
                        helper.check(
                                coreA.getExposedInventoryForSide(Direction.SOUTH).addItems(singularities.copy())
                                        .isEmpty(),
                                "failed to add singularity",
                                qnbA);

                        var coreB = getCore(helper, qnbB);
                        helper.check(
                                coreB.getExposedInventoryForSide(Direction.SOUTH).addItems(singularities.copy())
                                        .isEmpty(),
                                "failed to add singularity",
                                qnbB);
                    })
                    // Wait until linked (-> same grid). This should now happen without power!
                    .thenWaitUntil(() -> {
                        var gridA = helper.getGrid(qnbA);
                        var gridB = helper.getGrid(qnbB);
                        if (gridA != gridB) {
                            throw helper.assertionException(qnbA, "not same grid");
                        }
                    })
                    // Wait until linked (-> same grid). This should now happen without power!
                    .thenWaitUntil(() -> {
                        var gridA = helper.getGrid(qnbA);
                        var gridB = helper.getGrid(qnbB);
                        if (gridA != gridB) {
                            throw helper.assertionException(qnbA, "not same grid");
                        }
                    })
                    // Remove singularity on one end
                    .thenExecute(() -> getCore(helper, qnbA).clearContent())
                    // Wait until unlinked
                    .thenWaitUntil(() -> {
                        var coreA = getCore(helper, qnbA);
                        helper.check(!coreA.hasQES(), "still has singularity", qnbA);

                        var gridA = helper.getGrid(qnbA);
                        var gridB = helper.getGrid(qnbB);
                        helper.check(gridA != gridB, "still same grid", qnbA);
                    })
                    .thenSucceed();
        });
    }

    /**
     * Part of a linked QNB is unloaded and loaded again (as when it straddles a chunk border and only one chunk
     * unloads), then the neighbours get block updates while the loaded block entities become ready. Must re-form and
     * link again without an exception. Regression test for
     * <a href="https://github.com/AppliedEnergistics/Applied-Energistics-2/issues/7458">issue 7458</a> (NPE in
     * QuantumCluster.destroy on a ring entry that is null).
     */
    @TestPlot("qnb_partial_unload_reload")
    public static void qnbPartialUnloadReload(PlotBuilder plot) {
        var qnbA = BlockPos.ZERO.above();
        var qnbB = qnbA.east(4);

        qnbRing(plot, qnbA);
        qnbRing(plot, qnbB);
        // Cable next to the part that is unloaded: its in-world connection update sends block updates to the QNB
        plot.cable(qnbA.west(2));
        plot.creativeEnergyCell(qnbA.west(2).below());

        plot.test(helper -> {
            var unloaded = List.of(qnbA.offset(-1, -1, 0), qnbA.offset(-1, 0, 0), qnbA.offset(-1, 1, 0));
            var saved = new ArrayList<CompoundTag>();
            helper.startSequence()
                    .thenWaitUntil(() -> {
                        helper.assertBlockProperty(qnbA, QuantumBaseBlock.FORMED, true);
                        helper.assertBlockProperty(qnbB, QuantumBaseBlock.FORMED, true);
                    })
                    .thenExecute(() -> {
                        var singularities = AEItems.QUANTUM_ENTANGLED_SINGULARITY.stack();
                        QuantumBridgeBlockEntity.assignFrequency(singularities);
                        getCore(helper, qnbA).getExposedInventoryForSide(Direction.SOUTH)
                                .addItems(singularities.copy());
                        getCore(helper, qnbB).getExposedInventoryForSide(Direction.SOUTH)
                                .addItems(singularities.copy());
                    })
                    .thenWaitUntil(() -> helper.check(helper.getGrid(qnbA) == helper.getGrid(qnbB), "not linked",
                            qnbA))
                    .thenExecute(() -> {
                        // "Unload" the west column like a chunk unload does: onChunkUnloaded, then remove the BE
                        var level = helper.getLevel();
                        for (var pos : unloaded) {
                            var be = helper.getBlockEntity(pos, BlockEntity.class);
                            var out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                                    level.registryAccess());
                            be.saveWithId(out);
                            saved.add(out.buildResult());
                            be.onChunkUnloaded();
                            level.removeBlockEntity(helper.absolutePos(pos));
                        }
                    })
                    .thenExecuteAfter(1, () -> {
                        // "Load" them again from the saved data, and send block updates around them
                        var level = helper.getLevel();
                        for (int i = 0; i < unloaded.size(); i++) {
                            var abs = helper.absolutePos(unloaded.get(i));
                            var loaded = BlockEntity.loadStatic(abs, level.getBlockState(abs), saved.get(i),
                                    level.registryAccess());
                            helper.check(loaded != null, "could not load", unloaded.get(i));
                            level.setBlockEntity(loaded);
                        }
                        for (var pos : unloaded) {
                            var abs = helper.absolutePos(pos);
                            level.updateNeighborsAt(abs, level.getBlockState(abs).getBlock());
                        }
                    })
                    .thenWaitUntil(() -> {
                        ringAround(qnbA, pos -> helper.assertBlockProperty(pos, QuantumBaseBlock.FORMED, true));
                        helper.assertBlockProperty(qnbA, QuantumBaseBlock.FORMED, true);
                        helper.check(helper.getGrid(qnbA) == helper.getGrid(qnbB), "not linked again", qnbA);
                    })
                    .thenSucceed();
        });
    }

    private static QuantumBridgeBlockEntity getCore(PlotTestHelper helper, BlockPos pos) {
        var be = helper.getBlockEntity(pos, BlockEntity.class);
        helper.check(be instanceof QuantumBridgeBlockEntity, "is not a QNB", pos);
        var qnb = (QuantumBridgeBlockEntity) be;
        helper.check(qnb.isFormed(), "not formed", pos);
        helper.check(!qnb.isCorner(), "is corner", pos);
        return qnb;
    }

    private static void qnbRing(PlotBuilder plot, BlockPos origin) {
        plot.block(origin, AEBlocks.QUANTUM_LINK);
        ringAround(origin, pos -> {
            plot.block(pos, AEBlocks.QUANTUM_RING);
        });
    }

    private static void ringAround(BlockPos origin, Consumer<BlockPos> consumer) {
        for (var x = -1; x <= 1; x++) {
            for (var y = -1; y <= 1; y++) {
                var pos = origin.offset(x, y, 0);
                if (x != 0 || y != 0) {
                    consumer.accept(pos);
                }
            }
        }
    }
}
