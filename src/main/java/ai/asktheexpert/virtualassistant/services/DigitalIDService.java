package ai.asktheexpert.virtualassistant.services;

import ai.asktheexpert.virtualassistant.models.Persona;
import ai.asktheexpert.virtualassistant.repositories.FileStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
// @CacheConfig(cacheManager = "fileStoreCacheManager")
public class DigitalIDService implements AvatarService, CreditService {
    public DigitalIDService(FileStore fileStore, ObjectMapper objectMapper) {
        this.fileStore = fileStore;
        this.objectMapper = objectMapper;
    }

    @Cacheable(value = "video", key = "#audioUrl.path")
    public byte[] getVideo(Persona persona, URL audioUrl) throws Exception {
        Map<String, Object> script = new HashMap<>();
        script.put("type", "audio");
        script.put("audio_url", audioUrl.toString());
        return getVideo(persona, script);
    }

    public byte[] getVideo(Persona persona, String text) throws Exception {
        Map<String, Object> script = new HashMap<>();
        script.put("type", "text");
        script.put("ssml", "true");
        script.put("input", text);
        return getVideo(persona, script);
    }

    private byte[] getVideo(Persona persona, Map<String, Object> audioHeader) throws Exception {
        byte[] video;
        String imgUrl = fileStore.getUrl(persona.getName().toLowerCase(), FileStore.MediaType.JPG);

        Map<String, Object> config = new HashMap<>();
        config.put("fluent", "false");
        config.put("pad_audio", "0.0");
        config.put("result_format", "mp4");

        Map<String, Object> payload = new HashMap<>();
        payload.put("source_url", imgUrl);
        payload.put("config", config);
        payload.put("script", audioHeader);

        HttpEntity<?> entity = getHttpEntity(payload);

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Map> response = restTemplate.exchange(didUrl + "/talks", HttpMethod.POST, entity, Map.class);
        String id = Objects.requireNonNull(response.getBody()).get("id").toString();
        log.debug("Generated video request {}", id);
        int maxTime = 60000;
        int totalTime = 0;
        int waitTime = 1000;
        do {
            Thread.sleep(waitTime);
            video = fetchVideo(id);
            totalTime += waitTime;
        } while (video == null && totalTime < maxTime);
        if (video == null) {
            throw new Exception("Unable to get video for " + id);
        }
        return video;
    }

    public int creditsRemaining() {
        HttpEntity<?> entity = getHttpEntity();
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Map> response = restTemplate.exchange(didUrl + "/credits", HttpMethod.GET, entity, Map.class);
        Map vals = (Map) ((List<?>) Objects.requireNonNull(response.getBody()).get("credits")).get(0);
        int remaining = (Integer) vals.get("remaining");
        log.debug("Credit information for D-ID: {}", vals);
        return remaining;
    }

    private byte[] fetchVideo(String videoId) throws Exception {
        byte[] results = null;
        HttpEntity<?> entity = getHttpEntity();

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Map> response = restTemplate.exchange(didUrl + "/talks/" + videoId, HttpMethod.GET, entity, Map.class);
        String status = Objects.requireNonNull(response.getBody()).get("status").toString();
        if (status.equals("done")) {
            String resultUrl = (String) response.getBody().get("result_url");
            if (resultUrl != null && FileStore.existsAtUrl(resultUrl)) {
                results = FileStore.downloadFileBytes(new URL(resultUrl));
            }
        } else if (!isPending(status)) {
            throw new Exception("Unable to get idle video for " + videoId);
        }
        return results;
    }

    private HttpEntity<?> getHttpEntity() {
        return getHttpEntity(null);
    }

    private HttpEntity<?> getHttpEntity(Map<String, Object> payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + didKey);
        headers.set("Content-Type", "application/json");
        if (payload != null) {
            try {
                String jsonRequest = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
                return new HttpEntity<>(jsonRequest, headers);
            } catch (Exception e) {
                log.error("Unable to serialize payload {}", payload);
                throw new RuntimeException(e);
            }

        } else {
            return new HttpEntity<>(headers);
        }
    }

    private boolean isPending(String status) {
        return status.equals("created") || status.equals("started");
    }


    @Value("${did.url}")
    private String didUrl;
    @Value("${did.key}")
    private String didKey;

    private final FileStore fileStore;
    private final ObjectMapper objectMapper;
    private static final Logger log = LoggerFactory.getLogger(DigitalIDService.class);
}
