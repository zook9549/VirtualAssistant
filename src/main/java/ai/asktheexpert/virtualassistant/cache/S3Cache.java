package ai.asktheexpert.virtualassistant.cache;

import ai.asktheexpert.virtualassistant.repositories.FileStore;
import org.springframework.cache.Cache;

import java.io.IOException;
import java.util.concurrent.Callable;

public class S3Cache implements Cache {
    private final String name;
    private final FileStore fileStore;

    public S3Cache(String name, FileStore fileStore) {
        this.name = name;
        this.fileStore = fileStore;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Object getNativeCache() {
        return fileStore;
    }

    @Override
    public ValueWrapper get(Object key) {
        return () -> {
            try {
                return fileStore.get(key.toString(), FileStore.MediaType.MP3);
            } catch (IOException e) {
                return null;
            }
        };
    }

    @Override
    public <T> T get(Object key, Class<T> type) {
        // Implement conversion from retrieved data to the desired type
        // e.g., JSON to a POJO
        ValueWrapper wrapper = get(key);
        if (wrapper != null) {
            return type.cast(wrapper.get());
        }
        return null;
    }

    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        // Implement logic to load data if not found in the cache
        // Use the valueLoader to load data and store it in S3
        return null; // Replace with your implementation
    }

    @Override
    public void put(Object key, Object value) {
        // Implement logic to store data in S3 using the key
        // Use amazonS3.putObject(bucketName, key, value) or a similar method
    }

    @Override
    public ValueWrapper putIfAbsent(Object key, Object value) {
        // Implement logic to put the value if absent and return the previous value
        return null; // Replace with your implementation
    }

    @Override
    public void evict(Object key) {
        // Implement logic to remove data from S3 using the key
        // Use amazonS3.deleteObject(bucketName, key) or a similar method
    }

    @Override
    public void clear() {
        // Implement logic to clear the cache
        // Optionally, you can delete all objects in the S3 bucket related to this cache
    }
}

