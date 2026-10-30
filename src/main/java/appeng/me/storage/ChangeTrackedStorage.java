/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
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

package appeng.me.storage;

import appeng.api.networking.storage.IStorageService;
import appeng.api.storage.MEStorage;

/**
 * A storage mounted into a grid whose listed contents only change through the grid's {@link NetworkStorage}, or whose
 * owner calls {@link IStorageService#invalidateCache()} when they change otherwise. The grid then only lists its
 * storage again when something changed. As long as any other kind of storage is mounted, the grid lists all of its
 * storage every tick.
 */
public interface ChangeTrackedStorage extends MEStorage {
    /**
     * @return false if the contents can currently change without the grid being told.
     */
    default boolean isChangeTracked() {
        return true;
    }
}
