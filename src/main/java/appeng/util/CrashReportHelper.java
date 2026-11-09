package appeng.util;

import net.minecraft.CrashReportCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Helpers for filling crash reports without side effects on the level.
 */
public final class CrashReportHelper {
    private CrashReportHelper() {
    }

    /**
     * Adds the details of a block entity to a crash report category, like {@link BlockEntity#fillCrashReportCategory},
     * but never loads a chunk.
     * <p>
     * {@link BlockEntity#fillCrashReportCategory} reads the "actual block" from the level, which synchronously loads
     * (or generates) the chunk if it is not loaded. Called while a crash report is being built, that can hang the
     * server until the watchdog kills it, and the original exception is lost. If the chunk of the block entity is not
     * loaded right now, only the details that need no level access are written.
     */
    public static void fillBlockEntityDetails(CrashReportCategory category, BlockEntity blockEntity) {
        var level = blockEntity.getLevel();
        var pos = blockEntity.getBlockPos();
        if (level == null || isChunkLoadedNow(level, pos)) {
            blockEntity.fillCrashReportCategory(category);
            return;
        }

        category.setDetail("Name", () -> BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType()) + " // "
                + blockEntity.getClass().getCanonicalName());
        category.setDetail("Cached block", () -> String.valueOf(blockEntity.getBlockState()));
        category.setDetail("Block location", () -> String.valueOf(pos));
        category.setDetail("Actual block", "(not read: chunk is not loaded)");
    }

    /**
     * @return true if the chunk at the given position is loaded to FULL status right now. Never loads the chunk.
     */
    private static boolean isChunkLoadedNow(Level level, BlockPos pos) {
        try {
            return level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(pos.getX()),
                    SectionPos.blockToSectionCoord(pos.getZ())) != null;
        } catch (Throwable t) {
            return false;
        }
    }
}
