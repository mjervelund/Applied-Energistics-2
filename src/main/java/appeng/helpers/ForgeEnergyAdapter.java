/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2021, TeamAppliedEnergistics, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.helpers;

import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import appeng.api.config.Actionable;
import appeng.api.config.PowerUnit;
import appeng.blockentity.powersink.IExternalPowerSink;
import appeng.me.energy.StoredEnergyAmount;

/**
 * Adapts an {@link IExternalPowerSink} to {@link EnergyHandler}, for accepting FE.
 */
public class ForgeEnergyAdapter extends SnapshotJournal<Double> implements EnergyHandler {

    private final IExternalPowerSink sink;
    /**
     * Power accepted in the current transaction, pushed into the sink when the outermost transaction commits.
     */
    private double buffer = 0;
    /**
     * Power from an already committed transaction that the sink could not take at commit time (e.g. another acceptor on
     * the same grid filled it in the same transaction). Not part of any transaction: it is pushed again on the next
     * insertion attempt, even a simulated one, so that it can never block this adapter for good
     * (https://github.com/AppliedEnergistics/Applied-Energistics-2/issues/9041).
     */
    private double leftover = 0;

    public ForgeEnergyAdapter(IExternalPowerSink sink) {
        this.sink = sink;
    }

    @Override
    protected Double createSnapshot() {
        return buffer;
    }

    @Override
    protected void revertToSnapshot(Double snapshot) {
        buffer = snapshot;
    }

    @Override
    protected void onRootCommit(Double originalState) {
        leftover += buffer;
        buffer = 0;
        pushLeftover();
    }

    private void pushLeftover() {
        if (leftover > 0) {
            leftover = sink.injectExternalPower(PowerUnit.FE, leftover, Actionable.MODULATE);
            if (leftover < StoredEnergyAmount.MIN_AMOUNT) {
                leftover = 0;
            }
        }
    }

    @Override
    public int insert(int maxAmount, TransactionContext tx) {
        TransferPreconditions.checkNonNegative(maxAmount);
        pushLeftover();
        if (leftover > 0) {
            return 0; // The sink is still full
        }

        // Always schedule a push into the network at outer commit.
        updateSnapshots(tx);

        if (buffer == 0) {
            // Cap at the remaining capacity...
            maxAmount = (int) Math
                    .floor(Math.min(maxAmount, this.sink.getExternalPowerDemand(PowerUnit.FE, maxAmount)));
            buffer = maxAmount;
            return maxAmount;
        }

        return 0;
    }

    @Override
    public final long getAmountAsLong() {
        return (long) Math.floor(PowerUnit.AE.convertTo(PowerUnit.FE, this.sink.getAECurrentPower()));
    }

    @Override
    public final long getCapacityAsLong() {
        return (long) Math.floor(PowerUnit.AE.convertTo(PowerUnit.FE, this.sink.getAEMaxPower()));
    }

    @Override
    public int extract(int maxAmount, TransactionContext transaction) {
        return 0;
    }
}
