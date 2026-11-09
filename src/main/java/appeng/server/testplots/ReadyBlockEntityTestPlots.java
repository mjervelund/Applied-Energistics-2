package appeng.server.testplots;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.BlockDefinition;
import appeng.server.testworld.PlotBuilder;

@TestPlotClass
public final class ReadyBlockEntityTestPlots {
    private ReadyBlockEntityTestPlots() {
    }

    /**
     * AE2 readies block entities in a later tick. If another mod changes the block underneath without removing the
     * block entity (e.g. by writing chunk sections directly), the deferred onReady must not run against the new block
     * state (it used to crash the server: "property fullness does not exist in minecraft:air" for energy cells,
     * "AirBlock cannot be cast to AbstractCraftingUnitBlock" for crafting units).
     */
    @TestPlot("ready_skips_replaced_block_entities")
    public static void readySkipsReplacedBlockEntities(PlotBuilder plot) {
        var positions = List.of(BlockPos.ZERO, new BlockPos(2, 0, 0));
        List<BlockDefinition<?>> blocks = List.of(AEBlocks.ENERGY_CELL, AEBlocks.CRAFTING_UNIT);
        plot.block("[0,2] 0 0", Blocks.STONE);
        plot.test(helper -> {
            var level = helper.getLevel();
            for (int i = 0; i < positions.size(); i++) {
                var pos = helper.absolutePos(positions.get(i));
                helper.setBlock(positions.get(i), blocks.get(i).block());
                helper.check(level.getBlockEntity(pos) != null, "no block entity", positions.get(i));
                // Replace the block in the chunk section directly, leaving the block entity in place
                var chunk = level.getChunkAt(pos);
                chunk.getSection(chunk.getSectionIndex(pos.getY()))
                        .setBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15,
                                Blocks.AIR.defaultBlockState());
            }
            helper.startSequence()
                    .thenIdle(5)
                    .thenExecute(() -> {
                        for (var relPos : positions) {
                            level.removeBlockEntity(helper.absolutePos(relPos));
                        }
                    })
                    .thenSucceed();
        });
    }
}
