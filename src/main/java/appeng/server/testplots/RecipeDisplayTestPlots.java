package appeng.server.testplots;

import java.util.ArrayList;
import java.util.Arrays;

import com.mojang.serialization.JsonOps;

import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.level.block.Blocks;

import appeng.core.AppEng;
import appeng.server.testworld.PlotBuilder;

@TestPlotClass
public final class RecipeDisplayTestPlots {
    private RecipeDisplayTestPlots() {
    }

    /**
     * Every display of every AE2 recipe must survive {@link RecipeDisplay#STREAM_CODEC} (what vanilla and other mods
     * use to send recipe displays to clients) and {@link RecipeDisplay#CODEC}. Both dispatch through the recipe display
     * type registry, so an unregistered display type throws on encode.
     */
    @TestPlot("recipe_displays_encode")
    public static void recipeDisplaysEncode(PlotBuilder plot) {
        plot.block(BlockPos.ZERO, Blocks.STONE);
        plot.test(helper -> {
            var level = helper.getLevel();
            var registries = level.registryAccess();
            var failures = new ArrayList<String>();
            int checked = 0;
            for (var holder : level.getServer().getRecipeManager().getRecipes()) {
                if (!holder.id().identifier().getNamespace().equals(AppEng.MOD_ID)) {
                    continue;
                }
                for (var display : holder.value().display()) {
                    checked++;
                    try {
                        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
                        RecipeDisplay.STREAM_CODEC.encode(buf, display);
                        var bytes = ByteBufUtil.getBytes(buf);
                        var decoded = RecipeDisplay.STREAM_CODEC.decode(buf);
                        if (decoded.type() != display.type() || buf.isReadable()) {
                            failures.add(holder.id() + ": stream round trip changed the display");
                            continue;
                        }
                        var again = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
                        RecipeDisplay.STREAM_CODEC.encode(again, decoded);
                        if (!Arrays.equals(bytes, ByteBufUtil.getBytes(again))) {
                            failures.add(holder.id() + ": stream round trip changed the display");
                            continue;
                        }
                        RecipeDisplay.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, registries), display)
                                .getOrThrow();
                    } catch (RuntimeException e) {
                        failures.add(holder.id() + " (" + display.getClass().getSimpleName() + "): " + e);
                    }
                }
            }
            helper.check(checked > 0, "no AE2 recipe displays found");
            helper.check(failures.isEmpty(),
                    failures.size() + " of " + checked + " AE2 recipe displays don't encode, first: "
                            + (failures.isEmpty() ? "" : failures.getFirst()));
            helper.succeed();
        });
    }
}
