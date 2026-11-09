package appeng.client.gui.me.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.util.BootstrapMinecraft;

/**
 * The terminal's name and mod comparators must sort exactly like the plain ones while building each display name only
 * once per sort.
 */
@BootstrapMinecraft
class KeySortersTest {

    private static List<AEKey> allItemKeys() {
        var keys = new ArrayList<AEKey>();
        for (Item item : BuiltInRegistries.ITEM) {
            var key = AEItemKey.of(item);
            if (key != null) {
                keys.add(key);
            }
        }
        return keys;
    }

    @Test
    void sameOrderAsThePlainComparators() {
        var keys = allItemKeys();
        assertThat(keys).hasSizeGreaterThan(1000);

        assertSameOrder(keys, KeySorters.NAME_ASC, SortOrder.NAME, SortDir.ASCENDING);
        assertSameOrder(keys, KeySorters.NAME_DESC, SortOrder.NAME, SortDir.DESCENDING);
        assertSameOrder(keys, KeySorters.MOD_ASC, SortOrder.MOD, SortDir.ASCENDING);
        assertSameOrder(keys, KeySorters.MOD_DESC, SortOrder.MOD, SortDir.DESCENDING);

        // Rough timing for the notes (no assertion: timings are machine dependent).
        long plainNanos = 0, cachedNanos = 0;
        for (int round = 0; round < 50; round++) {
            var copy = new ArrayList<>(keys);
            java.util.Collections.shuffle(copy, new Random(round));
            var copy2 = new ArrayList<>(copy);
            long t0 = System.nanoTime();
            copy.sort(KeySorters.NAME_ASC);
            long t1 = System.nanoTime();
            copy2.sort(KeySorters.getComparator(SortOrder.NAME, SortDir.ASCENDING));
            long t2 = System.nanoTime();
            if (round >= 10) {
                plainNanos += t1 - t0;
                cachedNanos += t2 - t1;
            }
        }
        System.out.printf("KeySortersTest: %d item keys, name sort: plain %.2f ms, cached %.2f ms (mean of 40)%n",
                keys.size(), plainNanos / 40e6, cachedNanos / 40e6);
    }

    private static void assertSameOrder(List<AEKey> keys, Comparator<AEKey> plain, SortOrder order, SortDir dir) {
        var expected = new ArrayList<>(keys);
        expected.sort(plain);
        var actual = new ArrayList<>(keys);
        actual.sort(KeySorters.getComparator(order, dir));
        assertThat(actual).as("%s %s", order, dir).containsExactlyElementsOf(expected);
    }

    @Test
    void buildsEachDisplayNameOnce() {
        var calls = new IdentityHashMap<AEKey, Integer>();
        var random = new Random(1);
        var keys = new ArrayList<AEKey>();
        for (int i = 0; i < 2000; i++) {
            // AEKey caches the display name Component; the repeated cost is Component.getString().
            var key = mock(AEKey.class);
            var name = mock(Component.class);
            var text = Integer.toString(random.nextInt(1_000_000), 36);
            when(name.getString()).thenAnswer(inv -> {
                calls.merge(key, 1, Integer::sum);
                return text;
            });
            when(key.getDisplayName()).thenReturn(name);
            when(key.getModId()).thenReturn(random.nextBoolean() ? "minecraft" : "ae2");
            keys.add(key);
        }

        for (var order : List.of(SortOrder.NAME, SortOrder.MOD)) {
            calls.clear();
            var sorted = new ArrayList<>(keys);
            sorted.sort(KeySorters.getComparator(order, SortDir.ASCENDING));
            assertThat(calls.values()).as("%s: getString() calls per key", order).allMatch(n -> n == 1);

            calls.clear();
            var expected = new ArrayList<>(keys);
            expected.sort(order == SortOrder.NAME ? KeySorters.NAME_ASC : KeySorters.MOD_ASC);
            assertThat(sorted).containsExactlyElementsOf(expected);
            // For comparison: the plain comparators build about 2 n log n names (~40k here for NAME).
            int plainCalls = calls.values().stream().mapToInt(Integer::intValue).sum();
            assertThat(plainCalls).as("%s: getString() calls by the plain comparator", order)
                    .isGreaterThan(5 * keys.size());
        }
    }
}
