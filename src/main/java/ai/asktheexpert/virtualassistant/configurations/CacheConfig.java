package ai.asktheexpert.virtualassistant.configurations;

import ai.asktheexpert.virtualassistant.cache.CustomCacheManager;
import ai.asktheexpert.virtualassistant.repositories.FileStore;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

//@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    @Primary
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("chat", "s3", "details", "tts", "video");
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .initialCapacity(200)
                .maximumSize(500)
                .expireAfterAccess(120, java.util.concurrent.TimeUnit.MINUTES)
                .weakKeys()
                .recordStats());
        return cacheManager;
    }

    @Bean
    public CacheManager fileStoreCacheManager(FileStore fileStore) {
        return new CustomCacheManager(fileStore);
    }
}
