package appeng.server.testplots;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.core.definitions.AEParts;
import appeng.core.network.serverbound.PartLeftClickPacket;
import appeng.parts.reporting.ConversionMonitorPart;
import appeng.server.testworld.PlotBuilder;

@TestPlotClass
public final class PartLeftClickTestPlots {
    private PartLeftClickTestPlots() {
    }

    /**
     * {@link PartLeftClickPacket} carries a client-chosen position. Clicks on parts out of reach must be ignored (a
     * conversion monitor would otherwise hand out items to a player anywhere in the dimension), and so must positions
     * in unloaded chunks (looking them up would load or generate the chunk). A click in reach still works.
     */
    @TestPlot("part_left_click_requires_reach")
    public static void partLeftClickRequiresReach(PlotBuilder plot) {
        var origin = BlockPos.ZERO;
        plot.creativeEnergyCell(origin.below());
        plot.storageDrive(origin);
        plot.cable(origin.above()).part(Direction.NORTH, AEParts.CONVERSION_MONITOR, monitor -> {
            monitor.setConfiguredItem(AEItemKey.of(Items.ACACIA_LOG));
            monitor.setLocked(true);
        });

        plot.test(helper -> {
            var monitorPos = origin.above();
            helper.startSequence()
                    .thenExecute(() -> helper.getGrid(origin)) // Wait for grid init
                    .thenWaitUntil(() -> helper.check(
                            helper.getPart(monitorPos, Direction.NORTH, ConversionMonitorPart.class).isActive(),
                            "conversion monitor is not active"))
                    .thenExecute(() -> {
                        var grid = helper.getGrid(origin);
                        grid.getStorageService().getInventory().insert(AEItemKey.of(Items.ACACIA_LOG), 64,
                                Actionable.MODULATE, IActionSource.empty());

                        var level = helper.getLevel();
                        var absPos = helper.absolutePos(monitorPos);
                        // The monitor faces north, so it covers the north face of the cable block
                        var hit = new BlockHitResult(Vec3.atCenterOf(absPos).add(0, 0, -0.45), Direction.NORTH,
                                absPos, false);
                        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "test-part-click"));

                        // Out of reach: nothing is extracted
                        player.setPos(Vec3.atCenterOf(absPos).add(0, 0, -40));
                        new PartLeftClickPacket(hit, false).handleOnServer(player);
                        helper.check(player.getInventory().countItem(Items.ACACIA_LOG) == 0,
                                "a part was clicked from out of reach");

                        // Unloaded chunk: rejected without loading it
                        var farPos = absPos.offset(100_000, 0, 100_000);
                        player.setPos(Vec3.atCenterOf(farPos).add(0, 0, -2));
                        new PartLeftClickPacket(new BlockHitResult(Vec3.atCenterOf(farPos), Direction.NORTH, farPos,
                                false), false).handleOnServer(player);
                        helper.check(!level.hasChunk(farPos.getX() >> 4, farPos.getZ() >> 4),
                                "a part click loaded a chunk");

                        // In reach: the monitor extracts a stack
                        player.setPos(Vec3.atCenterOf(absPos).add(0, -1, -2));
                        new PartLeftClickPacket(hit, false).handleOnServer(player);
                        helper.check(player.getInventory().countItem(Items.ACACIA_LOG) > 0,
                                "a part click in reach did nothing");
                    })
                    .thenSucceed();
        });
    }
}
