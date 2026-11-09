package appeng.crafting.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import appeng.util.BootstrapMinecraft;

/**
 * A job whose pattern providers are all busy must not touch the CPU inventory: the inputs used to be extracted and
 * reinjected on every attempt, i.e. every tick for every task while the job waits on its machines.
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@BootstrapMinecraft
class CraftingCpuLogicBusyProvidersTest {
    private static final AEItemKey INPUT = AEItemKey.of(Items.COBBLESTONE);
    private static final AEItemKey OUTPUT = AEItemKey.of(Items.STONE);

    @Mock
    CraftingCPUCluster cluster;
    @Mock
    IGrid grid;
    @Mock
    IStorageService storageService;
    @Mock
    MEStorage networkStorage;
    @Mock
    ICraftingPlan plan;
    @Mock
    IPatternDetails pattern;
    @Mock
    IPatternDetails.IInput patternInput;
    @Mock
    CraftingService craftingService;
    @Mock
    IEnergyService energyService;
    @Mock
    Level level;

    private CraftingCpuLogic logic;
    private final List<AEKey> changes = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(cluster.isActive()).thenReturn(true);
        when(cluster.getAvailableStorage()).thenReturn(Long.MAX_VALUE);

        when(grid.getStorageService()).thenReturn(storageService);
        when(storageService.getInventory()).thenReturn(networkStorage);
        when(networkStorage.extract(any(), anyLong(), any(), any()))
                .thenAnswer(inv -> inv.getArgument(1));

        when(patternInput.getPossibleInputs()).thenReturn(new GenericStack[] { new GenericStack(INPUT, 1) });
        when(patternInput.getMultiplier()).thenReturn(1L);
        when(patternInput.isValid(any(), any())).thenReturn(true);
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[] { patternInput });
        when(pattern.getOutputs()).thenReturn(List.of(new GenericStack(OUTPUT, 1)));

        var used = new KeyCounter();
        used.add(INPUT, 10);
        when(plan.usedItems()).thenReturn(used);
        when(plan.emittedItems()).thenReturn(new KeyCounter());
        when(plan.patternTimes()).thenReturn(Map.of(pattern, 10L));
        when(plan.finalOutput()).thenReturn(new GenericStack(OUTPUT, 10));
        when(plan.bytes()).thenReturn(0L);

        when(energyService.extractAEPower(anyDouble(), any(), any())).thenAnswer(inv -> inv.getArgument(0));

        logic = new CraftingCpuLogic(cluster);
        var result = logic.trySubmitJob(grid, plan, IActionSource.empty(), null);
        assertThat(result.successful()).isTrue();
        assertThat(logic.getStored(INPUT)).isEqualTo(10);
        logic.addListener(changes::add);
    }

    @Test
    void allProvidersBusyLeavesTheInventoryAlone() {
        var busy1 = provider(true);
        var busy2 = provider(true);
        when(craftingService.getProviders(pattern)).thenReturn(List.of(busy1, busy2));

        for (int tick = 0; tick < 20; tick++) {
            assertThat(logic.executeCrafting(1, craftingService, energyService, level)).isZero();
        }

        assertThat(changes).as("CPU listeners notified while every provider was busy").isEmpty();
        assertThat(logic.getStored(INPUT)).isEqualTo(10);
        verify(busy1, never()).pushPattern(any(), any());
        verify(busy2, never()).pushPattern(any(), any());
    }

    @Test
    void noProvidersLeavesTheInventoryAlone() {
        when(craftingService.getProviders(pattern)).thenReturn(List.of());

        assertThat(logic.executeCrafting(1, craftingService, energyService, level)).isZero();

        assertThat(changes).isEmpty();
        assertThat(logic.getStored(INPUT)).isEqualTo(10);
    }

    @Test
    void idleProviderAfterBusyOnesStillGetsThePattern() {
        var busy = provider(true);
        var idle = provider(false);
        when(idle.pushPattern(any(), any())).thenReturn(true);
        when(craftingService.getProviders(pattern)).thenReturn(List.of(busy, idle));

        assertThat(logic.executeCrafting(1, craftingService, energyService, level)).isEqualTo(1);

        verify(busy, never()).pushPattern(any(), any());
        verify(idle).pushPattern(any(), any());
        assertThat(logic.getStored(INPUT)).isEqualTo(9);
        assertThat(logic.getWaitingFor(OUTPUT)).isEqualTo(1);
    }

    @Test
    void refusedPushReinjectsTheInputs() {
        var idle = provider(false);
        when(idle.pushPattern(any(), any())).thenReturn(false);
        when(craftingService.getProviders(pattern)).thenReturn(List.of(idle));

        assertThat(logic.executeCrafting(1, craftingService, energyService, level)).isZero();

        verify(idle).pushPattern(any(), any());
        assertThat(logic.getStored(INPUT)).isEqualTo(10);
    }

    @Test
    void pushesUpToMaxPatternsAcrossIdleProviders() {
        var a = provider(false);
        var b = provider(false);
        when(a.pushPattern(any(), any())).thenReturn(true);
        when(b.pushPattern(any(), any())).thenReturn(true);
        when(craftingService.getProviders(pattern)).thenReturn(List.of(a, b));

        assertThat(logic.executeCrafting(2, craftingService, energyService, level)).isEqualTo(2);

        assertThat(logic.getStored(INPUT)).isEqualTo(8);
        assertThat(logic.getWaitingFor(OUTPUT)).isEqualTo(2);
    }

    private static ICraftingProvider provider(boolean busy) {
        var provider = mock(ICraftingProvider.class);
        when(provider.isBusy()).thenReturn(busy);
        return provider;
    }
}
