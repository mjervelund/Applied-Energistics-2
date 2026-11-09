package appeng.me;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.minecraft.CrashReportCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Filling a crash report for a grid must never load the chunk of the grid's pivot node. Loading a chunk while a crash
 * report is built can hang the server thread until the watchdog fires, and the original exception is lost.
 * <p>
 * <a href="https://github.com/AppliedEnergistics/Applied-Energistics-2/issues/9045">#9045</a>
 */
class GridCrashReportTest extends AbstractGridNodeTest {
    private static final BlockPos POS = new BlockPos(48, 64, 16);

    private ServerChunkCache chunkSource;
    private ChestBlockEntity blockEntity;
    private GridNode node;

    @BeforeEach
    void setUp() {
        chunkSource = mock(ServerChunkCache.class);
        when(level.getChunkSource()).thenReturn(chunkSource);
        // What the level would return after loading the chunk.
        when(level.getBlockState(any())).thenReturn(Blocks.CHEST.defaultBlockState());

        blockEntity = new ChestBlockEntity(POS, Blocks.CHEST.defaultBlockState());
        blockEntity.setLevel(level);
        node = new GridNode(level, blockEntity, listener, Set.of());
    }

    @Test
    void crashReportDoesNotTouchUnloadedChunk() {
        when(chunkSource.getChunkNow(anyInt(), anyInt())).thenReturn(null);

        var category = new CrashReportCategory("Grid being ticked");
        node.getInternalGrid().fillCrashReportCategory(category);

        verify(level, never()).getBlockState(any());
        verify(level, never()).getChunk(anyInt(), anyInt());
        verify(level, never()).getChunkAt(any());
        verify(chunkSource, never()).getChunk(anyInt(), anyInt(), any(), anyBoolean());

        var details = getDetails(category);
        assertThat(details).contains("Node");
        assertThat(details).contains(POS.toString());
        assertThat(details).contains("chunk is not loaded");
    }

    @Test
    void crashReportReadsBlockInLoadedChunk() {
        when(chunkSource.getChunkNow(POS.getX() >> 4, POS.getZ() >> 4)).thenReturn(mock(LevelChunk.class));

        var category = new CrashReportCategory("Grid being ticked");
        node.getInternalGrid().fillCrashReportCategory(category);

        // The full vanilla block entity details are written, including the actual block.
        verify(level).getBlockState(POS);
        assertThat(getDetails(category)).contains("Actual block").doesNotContain("chunk is not loaded");
    }

    private static String getDetails(CrashReportCategory category) {
        var sb = new StringBuilder();
        category.getDetails(sb);
        return sb.toString();
    }
}
