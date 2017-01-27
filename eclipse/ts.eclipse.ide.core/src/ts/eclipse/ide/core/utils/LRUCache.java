package ts.eclipse.ide.core.utils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * A LRU cache.
 */
public class LRUCache<K, V> {

	private final Map<K, V> map;

	public LRUCache(int maxEntries) {
		this.map = new LinkedHashMap<K, V>(maxEntries, 0.75F, true) {

			private static final long serialVersionUID = 2352634000800882410L;

			@Override
			protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
				return size() > maxEntries;
			}

		};
	}

	public V get(K key, Function<K, V> loader) {
		synchronized (map) {
			if (map.containsKey(key)) {
				return map.get(key);
			} else {
				V value = loader.apply(key);
				map.put(key, value);
				return value;
			}
		}
	}

	public void put(K key, V value) {
		synchronized (map) {
			map.put(key, value);
		}
	}

	public void clear() {
		synchronized (map) {
			map.clear();
		}
	}

}
