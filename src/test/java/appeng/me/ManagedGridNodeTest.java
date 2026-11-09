package appeng.me;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.assertj.core.data.Offset;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IAEPowerStorage;
import appeng.me.service.EnergyService;

class ManagedGridNodeTest extends AbstractGridNodeTest {
    public static final Offset<Double> TOLERANCE = Offset.offset(0.1);
    private final Object owner = new Object();

    /**
     * Regression test for https://github.com/AppliedEnergistics/Applied-Energistics-2/issues/9018. When a level is
     * unloaded, TickHandler destroys the raw grid nodes one by one while the block entities (and their managed nodes)
     * are still around. Destroying a later node can cause neighbours to re-validate their grid, which triggers block
     * updates that make e.g. a spatial pylon query the power state of an already destroyed node.
     */
    @Test
    void testIsPoweredAfterNodeWasDestroyedDirectly() {
        var managedNode = new ManagedGridNode(owner, listener);
        managedNode.addService(IAEPowerStorage.class, new InfinitePowerStorage());
        managedNode.create(level, null);
        var node = (GridNode) managedNode.getNode();
        ((EnergyService) node.getInternalGrid().getEnergyService()).onServerEndTick();
        assertThat(managedNode.isPowered()).isTrue();

        // This is what TickHandler.onUnloadLevel does, bypassing the managed node
        node.destroy();

        assertThat(managedNode.isPowered()).isFalse();
        assertThat(managedNode.isOnline()).isFalse();
        assertThat(managedNode.isActive()).isFalse();
    }

    @Test
    void testGridStorageRetrieval() {

        // Create the NBT tag that contains a fully loaded energy service
        var precursorNode = createAndInitNode();
        var precursorEnergyService = precursorNode.getGrid().getEnergyService();
        precursorEnergyService.injectPower(10, Actionable.MODULATE);
        assertThat(precursorEnergyService.extractAEPower(10, Actionable.SIMULATE, PowerMultiplier.ONE))
                .isCloseTo(10, Offset.offset(0.1));
        var output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        precursorNode.serialize(output);
        var savedNode = output.buildResult();
        precursorNode.destroy();

        // We create 2 nodes and re-initialize both with the same storage
        // We should have twice the storage as before.
        var mgn1 = new ManagedGridNode(owner, listener);
        mgn1.deserialize(TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, savedNode));
        var mgn2 = new ManagedGridNode(owner, listener);
        mgn2.deserialize(TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, savedNode));

        mgn1.create(level, BlockPos.ZERO);
        mgn2.create(level, BlockPos.ZERO);
        GridConnection.create(
                mgn1.getNode(),
                mgn2.getNode(),
                null);

        assertSame(mgn1.getGrid(), mgn2.getGrid());
        assertThat(mgn1.getGrid().getEnergyService().extractAEPower(1000, Actionable.SIMULATE, PowerMultiplier.ONE))
                .isCloseTo(20, TOLERANCE);
    }

    @NotNull
    private ManagedGridNode createAndInitNode() {
        var mgn = new ManagedGridNode(owner, listener);
        mgn.create(level, null);
        return mgn;
    }

}
