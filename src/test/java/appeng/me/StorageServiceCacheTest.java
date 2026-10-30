package appeng.me.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IStackWatcher;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageWatcherNode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.me.AbstractGridNodeTest;
import appeng.me.storage.ChangeTrackedStorage;

/**
 * Checks when the grid lists its storage again to update the cached inventory and notify storage watchers.
 */
class StorageServiceCacheTest extends AbstractGridNodeTest {
    private static final AEKey DIAMOND = AEItemKey.of(Items.DIAMOND);

    private final List<Long> notifiedAmounts = new ArrayList<>();

    @Test
    void testTrackedStorageIsOnlyListedAfterChanges() {
        var storage = new CountingStorage();
        var grid = makeGrid(new TrackedStorage(storage));

        runTick(grid);
        var listings = storage.listings;
        runTick(grid, 5);
        assertThat(storage.listings).isEqualTo(listings);

        // A change through the grid is picked up at the end of the tick
        grid.getStorageService().getInventory().insert(DIAMOND, 3, Actionable.MODULATE, IActionSource.empty());
        runTick(grid);
        assertThat(storage.listings).isEqualTo(listings + 1);
        assertThat(notifiedAmounts).containsExactly(3L);

        // Simulated operations change nothing
        grid.getStorageService().getInventory().extract(DIAMOND, 1, Actionable.SIMULATE, IActionSource.empty());
        runTick(grid, 5);
        assertThat(storage.listings).isEqualTo(listings + 1);

        // Changes outside of the grid are picked up after the owner invalidates the cache
        storage.stacks.add(DIAMOND, 2);
        grid.getStorageService().invalidateCache();
        runTick(grid);
        assertThat(storage.listings).isEqualTo(listings + 2);
        assertThat(notifiedAmounts).containsExactly(3L, 5L);
    }

    @Test
    void testUntrackedStorageIsListedEveryTick() {
        var storage = new CountingStorage();
        var grid = makeGrid(storage);

        runTick(grid);
        var listings = storage.listings;
        runTick(grid, 5);
        assertThat(storage.listings).isEqualTo(listings + 5);

        // Changes outside of the grid are picked up on the next tick
        storage.stacks.add(DIAMOND, 2);
        runTick(grid);
        assertThat(notifiedAmounts).containsExactly(2L);
    }

    private IGrid makeGrid(MEStorage mountedStorage) {
        var node = makeNode();
        node.addService(IStorageProvider.class, new IStorageProvider() {
            @Override
            public void mountInventories(IStorageMounts mounts) {
                mounts.mount(mountedStorage);
            }
        });
        node.addService(IStorageWatcherNode.class, new IStorageWatcherNode() {
            @Override
            public void updateWatcher(IStackWatcher newWatcher) {
                newWatcher.setWatchAll(true);
            }

            @Override
            public void onStackChange(AEKey what, long amount) {
                notifiedAmounts.add(amount);
            }
        });
        node.markReady();
        return node.getGrid();
    }

    private static class CountingStorage implements MEStorage {
        final KeyCounter stacks = new KeyCounter();
        int listings;

        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            if (mode == Actionable.MODULATE) {
                stacks.add(what, amount);
            }
            return amount;
        }

        @Override
        public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            var extracted = Math.min(amount, stacks.get(what));
            if (mode == Actionable.MODULATE) {
                stacks.remove(what, extracted);
            }
            return extracted;
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            listings++;
            out.addAll(stacks);
        }

        @Override
        public Component getDescription() {
            return Component.literal("counting storage");
        }
    }

    private record TrackedStorage(CountingStorage delegate) implements ChangeTrackedStorage {
        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            return delegate.insert(what, amount, mode, source);
        }

        @Override
        public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            return delegate.extract(what, amount, mode, source);
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            delegate.getAvailableStacks(out);
        }

        @Override
        public Component getDescription() {
            return delegate.getDescription();
        }
    }
}
