package ai.asktheexpert.virtualassistant;

import java.util.LinkedHashMap;
import java.util.Map;

public class LRUCache<K, V> extends LinkedHashMap<K, V> {
    private final int maxSize;

    public LRUCache(int maxSize) {
        // The 'true' parameter makes the LinkedHashMap record access order (for LRU behavior)
        super((int) Math.ceil(maxSize / 0.75) + 1, 0.75f, true);
        this.maxSize = maxSize;
    }

    // This method is invoked by put and putAll after inserting a new entry into the map
    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > maxSize;  // remove the oldest element when size limit is exceeded
    }
}