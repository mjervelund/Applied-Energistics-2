/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
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

package appeng.client.gui.me.common;

import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.function.Function;

import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.stacks.AEKey;

final class KeySorters {

    private KeySorters() {
    }

    // FIXME: Calling .getString() to compare two untranslated strings is a problem, we need to investigate how to do
    // this better
    public static final Comparator<AEKey> NAME_ASC = Comparator.comparing(
            is -> is.getDisplayName().getString(),
            String::compareToIgnoreCase);

    public static final Comparator<AEKey> NAME_DESC = NAME_ASC.reversed();

    public static final Comparator<AEKey> MOD_ASC = Comparator.comparing(
            AEKey::getModId,
            String::compareToIgnoreCase).thenComparing(NAME_ASC);

    public static final Comparator<AEKey> MOD_DESC = MOD_ASC.reversed();

    /**
     * Returns a comparator with the same order as {@link #NAME_ASC}, {@link #NAME_DESC}, {@link #MOD_ASC} or
     * {@link #MOD_DESC}, which turns each key's display name into a string only once. The plain comparators call
     * {@code getString()} (a translation lookup and a new string) twice per comparison, about 2 n log n times for n
     * keys, and the terminal sorts again on every inventory update. The strings are kept for the lifetime of the
     * comparator: get a new one for each sort.
     */
    public static Comparator<AEKey> getComparator(SortOrder order, SortDir dir) {
        var names = new IdentityHashMap<AEKey, String>();
        Function<AEKey, String> name = key -> names.computeIfAbsent(key, k -> k.getDisplayName().getString());
        Comparator<AEKey> nameAsc = Comparator.comparing(name, String::compareToIgnoreCase);
        Comparator<AEKey> ascending = switch (order) {
            case NAME -> nameAsc;
            case MOD -> Comparator.comparing(AEKey::getModId, String::compareToIgnoreCase).thenComparing(nameAsc);
            case AMOUNT -> throw new UnsupportedOperationException();
        };
        return dir == SortDir.ASCENDING ? ascending : ascending.reversed();
    }

}
