package appeng.recipes;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.neoforged.neoforge.registries.DeferredRegister;

import appeng.core.AppEng;
import appeng.recipes.game.CraftingUnitTransformDisplay;
import appeng.recipes.game.StorageCellUpgradeDisplay;
import appeng.recipes.handlers.ChargerRecipeDisplay;
import appeng.recipes.handlers.InscriberRecipeDisplay;
import appeng.recipes.mattercannon.MatterCannonAmmoDisplay;
import appeng.recipes.transform.TransformRecipeDisplay;

/**
 * Registers our {@link RecipeDisplay.Type}s. {@link RecipeDisplay#STREAM_CODEC} dispatches on the type through
 * {@link Registries#RECIPE_DISPLAY}, so a display whose type is not registered cannot be sent to a client.
 */
public final class AERecipeDisplayTypes {
    private AERecipeDisplayTypes() {
    }

    public static final DeferredRegister<RecipeDisplay.Type<?>> DR = DeferredRegister
            .create(Registries.RECIPE_DISPLAY, AppEng.MOD_ID);

    static {
        register("inscriber", InscriberRecipeDisplay.TYPE);
        register("charger", ChargerRecipeDisplay.TYPE);
        register("transform", TransformRecipeDisplay.TYPE);
        register("storage_cell_upgrade", StorageCellUpgradeDisplay.TYPE);
        register("crafting_unit_transform", CraftingUnitTransformDisplay.TYPE);
        register("matter_cannon_ammo", MatterCannonAmmoDisplay.TYPE);
    }

    private static void register(String id, RecipeDisplay.Type<?> type) {
        DR.register(id, () -> type);
    }
}
