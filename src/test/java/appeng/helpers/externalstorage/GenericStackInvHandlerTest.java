package appeng.helpers.externalstorage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.atomic.AtomicInteger;

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import net.minecraft.world.item.Items;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.helpers.ResourceConversion;
import appeng.util.BootstrapMinecraft;

@BootstrapMinecraft
class GenericStackInvHandlerTest {
    private static final AEItemKey STICK = AEItemKey.of(Items.STICK);
    private static final AEItemKey STONE = AEItemKey.of(Items.STONE);

    /**
     * Counts key conversions, so the test can check that inserting into the whole inventory converts the resource once
     * instead of once per slot.
     */
    private static final class CountingConversion implements ResourceConversion<ItemResource> {
        final AtomicInteger getKeyCalls = new AtomicInteger();

        @Override
        public AEKeyType getKeyType() {
            return ITEM.getKeyType();
        }

        @Override
        public ItemResource getVariant(@Nullable AEKey key) {
            return ITEM.getVariant(key);
        }

        @Override
        public @Nullable AEKey getKey(ItemResource variant) {
            getKeyCalls.incrementAndGet();
            return ITEM.getKey(variant);
        }

        @Override
        public long getBaseSlotSize(ItemResource variant) {
            return ITEM.getBaseSlotSize(variant);
        }
    }

    @Test
    void insertFillsMatchingSlotsFirstThenEmptySlotsAndConvertsOnce() {
        var inv = new GenericStackInv(null, 5);
        inv.setStack(0, new GenericStack(STONE, 10));
        inv.setStack(3, new GenericStack(STICK, 60));
        var conversion = new CountingConversion();
        var handler = new GenericStackInvHandler<>(conversion, AEKeyType.items(), inv);

        int inserted;
        try (var tx = Transaction.open(null)) {
            inserted = handler.insert(STICK.toResource(), 100, tx);
            tx.commit();
        }

        // 4 into the partly filled stick slot, then 64 into the first empty slot, then the remaining 32 into the next
        assertEquals(100, inserted);
        assertEquals(new GenericStack(STONE, 10), inv.getStack(0));
        assertEquals(new GenericStack(STICK, 64), inv.getStack(1));
        assertEquals(new GenericStack(STICK, 32), inv.getStack(2));
        assertEquals(new GenericStack(STICK, 64), inv.getStack(3));
        assertNull(inv.getStack(4));
        assertEquals(1, conversion.getKeyCalls.get());
    }

    @Test
    void insertIsRolledBackWithoutCommit() {
        var inv = new GenericStackInv(null, 3);
        inv.setStack(1, new GenericStack(STICK, 1));
        var handler = new GenericStackItemHandler(inv);

        try (var tx = Transaction.open(null)) {
            assertEquals(128, handler.insert(STICK.toResource(), 128, tx));
        }

        assertNull(inv.getStack(0));
        assertEquals(new GenericStack(STICK, 1), inv.getStack(1));
        assertNull(inv.getStack(2));
    }

    @Test
    void singleSlotInsertStillMatchesKey() {
        var inv = new GenericStackInv(null, 2);
        inv.setStack(0, new GenericStack(STONE, 1));
        var handler = new GenericStackItemHandler(inv);

        try (var tx = Transaction.open(null)) {
            assertEquals(0, handler.insert(0, STICK.toResource(), 5, tx));
            assertEquals(5, handler.insert(1, STICK.toResource(), 5, tx));
            tx.commit();
        }

        assertEquals(new GenericStack(STONE, 1), inv.getStack(0));
        assertEquals(new GenericStack(STICK, 5), inv.getStack(1));
    }
}
