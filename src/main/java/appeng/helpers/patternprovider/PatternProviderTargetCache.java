package appeng.helpers.patternprovider;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import appeng.api.AECapabilities;
import appeng.api.behaviors.ExternalStorageStrategy;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.me.storage.CompositeStorage;
import appeng.me.storage.ExternalStorageFacade;
import appeng.parts.automation.StackWorldBehaviors;

class PatternProviderTargetCache {
    private final BlockCapabilityCache<MEStorage, Direction> cache;
    private final IActionSource src;
    private final Map<AEKeyType, ExternalStorageStrategy> strategies;

    PatternProviderTargetCache(ServerLevel l, BlockPos pos, Direction direction, IActionSource src) {
        this.cache = BlockCapabilityCache.create(AECapabilities.ME_STORAGE, l, pos, direction);
        this.src = src;
        this.strategies = StackWorldBehaviors.createExternalStorageStrategies(l, pos, direction);
    }

    @Nullable
    PatternProviderTarget find() {
        // our capability first: allows any storage channel
        var meStorage = cache.getCapability();
        if (meStorage != null) {
            return wrapMeStorage(meStorage);
        }

        // otherwise fall back to the platform capability
        var externalStorages = new IdentityHashMap<AEKeyType, MEStorage>(2);
        for (var entry : strategies.entrySet()) {
            var wrapper = entry.getValue().createWrapper(false, () -> {
            });
            if (wrapper != null) {
                externalStorages.put(entry.getKey(), wrapper);
            }
        }

        if (!externalStorages.isEmpty()) {
            return wrapExternalStorages(externalStorages);
        }

        return null;
    }

    /**
     * Checks the inputs that go to item and fluid handlers together, in one transaction that is then rolled back.
     * Checked one by one, a single free slot accepts each input, so a pattern with several inputs was pushed partially
     * and the rest stayed in the send list (issue 9035).
     */
    private PatternProviderTarget wrapExternalStorages(Map<AEKeyType, MEStorage> externalStorages) {
        var composite = wrapMeStorage(new CompositeStorage(externalStorages));
        return new PatternProviderTarget() {
            @Override
            public long insert(AEKey what, long amount, Actionable type) {
                return composite.insert(what, amount, type);
            }

            @Override
            public boolean containsPatternInput(Set<AEKey> patternInputs) {
                return composite.containsPatternInput(patternInputs);
            }

            @Override
            public boolean acceptsAll(KeyCounter[] inputHolder) {
                if (Transaction.getLifecycle() != Transaction.Lifecycle.NONE) {
                    return PatternProviderTarget.super.acceptsAll(inputHolder);
                }

                // Other storages (from addons) are checked on their own, outside the transaction, because they may
                // open root transactions of their own.
                for (var inputList : inputHolder) {
                    for (var input : inputList) {
                        var storage = externalStorages.get(input.getKey().getType());
                        if (storage == null) {
                            return false;
                        }
                        if (!isTransactional(storage)
                                && storage.insert(input.getKey(), input.getLongValue(), Actionable.SIMULATE,
                                        src) == 0) {
                            return false;
                        }
                    }
                }

                try (var tx = Transaction.openRoot()) {
                    for (var inputList : inputHolder) {
                        for (var input : inputList) {
                            var storage = externalStorages.get(input.getKey().getType());
                            if (isTransactional(storage) && ((ExternalStorageFacade) storage)
                                    .insert(input.getKey(), input.getLongValue(), tx) == 0) {
                                return false;
                            }
                        }
                    }
                    return true;
                } // not committed: rolled back
            }
        };
    }

    private static boolean isTransactional(MEStorage storage) {
        return storage instanceof ExternalStorageFacade facade && facade.supportsTransactionalInsert();
    }

    private PatternProviderTarget wrapMeStorage(MEStorage storage) {
        return new PatternProviderTarget() {
            @Override
            public long insert(AEKey what, long amount, Actionable type) {
                return storage.insert(what, amount, type, src);
            }

            @Override
            public boolean containsPatternInput(Set<AEKey> patternInputs) {
                for (var stack : storage.getAvailableStacks()) {
                    if (patternInputs.contains(stack.getKey().dropSecondary())) {
                        return true;
                    }
                }
                return false;
            }
        };
    }
}
