package ai.asktheexpert.virtualassistant.cache;

import ai.asktheexpert.virtualassistant.repositories.FileStore;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class CustomCacheManager implements CacheManager {
    private final Map<String, Cache> caches = new HashMap<>();
    private final FileStore fileStore;

    public CustomCacheManager(FileStore fileStore) {
        this.fileStore = fileStore;
    }

    @Override
    public Cache getCache(String name) {
        if (!caches.containsKey(name)) {
            caches.put(name, new S3Cache(name, fileStore));
        }
        return caches.get(name);
    }

    @Override
    public Collection<String> getCacheNames() {
        return caches.keySet();
    }
}
