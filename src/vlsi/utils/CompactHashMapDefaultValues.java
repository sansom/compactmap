/*
 * Copyright (c) 2011
 *
 * This file is part of CompactMap
 *
 * CompactMap is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * CompactMap is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with CompactMap.  If not, see <http://www.gnu.org/licenses/>.
 */

package vlsi.utils;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class CompactHashMapDefaultValues {
    // Key -> Value -> OldMap -> NewMap
    private static Map<Object, Map<Object, Map<Map, Map>>> defaultValues
            = new HashMap<Object, Map<Object, Map<Map, Map>>>();

    private static ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private static Lock readLock = readWriteLock.readLock();
    private static Lock writeLock = readWriteLock.writeLock();

    public static final String[] ALL_VALUES_MATCH = new String[]{"All values match"};

    public static void clear() {
        writeLock.lock();
        try {
            defaultValues.clear();
        } finally {
            writeLock.unlock();
        }
    }

    public static boolean add(Object key) {
        return add(key, ALL_VALUES_MATCH);
    }

    public static boolean add(Object key, Object value) {
        writeLock.lock();
        try {
            Map<Object, Map<Map, Map>> m = defaultValues.get(key);
            if (m == null)
                defaultValues.put(key, m = new HashMap<Object, Map<Map, Map>>());

            if (m.get(value) != null)
                return false; // The value is already marked as default

            m.put(value, new IdentityHashMap<Map, Map>());
            return true;
        } finally {
            writeLock.unlock();
        }
    }

    public static <K, V> Map<K, V> getNewDefaultValues(Map<K, V> prevDefaultValues, K key, Object value) {
        final Map<Object, Map<Map, Map>> m;
        Map<Map, Map> identityOld2New;

        readLock.lock();
        try {
            m = defaultValues.get(key);
            if (m == null) return null; // The key is not default

            identityOld2New = m.get(value);
            if (identityOld2New == null) {
                if (value != CompactHashMapClass.REMOVED_OBJECT && m.get(ALL_VALUES_MATCH) == null)
                    return null; // The value is not default
            } else {
                Map newMap = identityOld2New.get(prevDefaultValues);
                if (newMap != null) return newMap;
            }
        } finally {
            readLock.unlock();
        }

        Map<K, V> newMap = new HashMap<K, V>((int) ((prevDefaultValues.size() + 1) / 0.75f));

        newMap.putAll(prevDefaultValues);

        if (value == CompactHashMapClass.REMOVED_OBJECT)
            newMap.remove(key);
        else
            newMap.put(key, (V) value);

        writeLock.lock();
        try {
            if (identityOld2New == null) {
                identityOld2New = m.get(value);
                if (identityOld2New == null)
                    m.put(value, identityOld2New = new IdentityHashMap<Map, Map>());
            }

            final Map anotherNewMap = identityOld2New.get(prevDefaultValues);
            if (anotherNewMap != null) return anotherNewMap; // In case another thread has just created new map

            identityOld2New.put(prevDefaultValues, newMap);
            return newMap;
        } finally {
            writeLock.unlock();
        }
    }
}
