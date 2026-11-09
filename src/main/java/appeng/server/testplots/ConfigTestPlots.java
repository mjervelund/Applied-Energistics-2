package appeng.server.testplots;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

import appeng.api.networking.pathing.ChannelMode;
import appeng.core.AEConfig;
import appeng.server.testworld.PlotBuilder;

@TestPlotClass
public final class ConfigTestPlots {
    private ConfigTestPlots() {
    }

    /**
     * {@code /ae2 channelmode} on a dedicated server: the new mode is stored in the common config, and saving must not
     * touch the client config, which a dedicated server never loads
     * (https://github.com/AppliedEnergistics/Applied-Energistics-2/issues/8731).
     */
    @TestPlot("channel_mode_change_saves_on_server")
    public static void channelModeChangeSavesOnServer(PlotBuilder plot) {
        plot.block(BlockPos.ZERO, Blocks.STONE);
        plot.test(helper -> {
            var config = AEConfig.instance();
            var original = config.getChannelMode();
            var other = original == ChannelMode.INFINITE ? ChannelMode.DEFAULT : ChannelMode.INFINITE;
            // Set and restore within the same tick, so no grid ever paths with the other mode.
            RuntimeException error = null;
            try {
                config.setChannelModel(other);
                config.save();
            } catch (RuntimeException e) {
                error = e;
            } finally {
                try {
                    config.setChannelModel(original);
                } catch (RuntimeException ignored) {
                    // Already reported above; the value itself is restored before saving.
                }
            }
            helper.check(error == null, "Changing the channel mode failed to save the config: " + error,
                    BlockPos.ZERO);
            helper.check(config.getChannelMode() == original, "channel mode restored", BlockPos.ZERO);
            helper.succeed();
        });
    }
}
