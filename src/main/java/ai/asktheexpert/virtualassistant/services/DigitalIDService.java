package ai.asktheexpert.virtualassistant.services;

import ai.asktheexpert.virtualassistant.models.Assistant;
import ai.asktheexpert.virtualassistant.repositories.FileStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
    public byte[] getVideo(Assistant assistant, URL audioUrl) throws Exception {
        Map<String, Object> script = new HashMap<>();
        script.put("type", "audio");
        script.put("audio_url", audioUrl.toString());
        return createVideo(assistant, script);
    }

    public byte[] getVideo(Assistant assistant) throws Exception {
        Map<String, Object> script = new HashMap<>();
        script.put("type", "text");
        script.put("ssml", "true");
        script.put("input", "<break time=\"20000ms\"/>");
        return createVideo(assistant, null);
    }

    private byte[] createVideo(Assistant assistant, Map<String, Object> audioHeader) throws Exception {
        String imgUrl = assistant.getProfilePicture();
        if(imgUrl == null) {
            imgUrl = fileStore.getUrl(assistant.getAssistantId(), FileStore.MediaType.JPG);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("source_url", imgUrl);

        Map<String, Object> config = new HashMap<>();
        config.put("result_format", "mp4");

        String endpoint;
        if(audioHeader != null) {
            endpoint = "talks";
            config.put("fluent", "false");
            config.put("pad_audio", "0.0");
            payload.put("script", audioHeader);
        } else {
            log.info("Creating using animations endpoint");
            payload.put("driver_url", "bank://nostalgia/");
            endpoint = "animations";
        }
        payload.put("config", config);

        HttpEntity<?> entity = getHttpEntity(payload);
        return fetchVideo(endpoint, entity);
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

    private byte[] fetchVideo(String endPoint, HttpEntity<?> videoEntity) throws Exception {
        RestTemplate restVideoTemplate = new RestTemplate();
        ResponseEntity<Map> videoResponse = restVideoTemplate.exchange(didUrl + "/" + endPoint, HttpMethod.POST, videoEntity, Map.class);
        String videoId = Objects.requireNonNull(videoResponse.getBody()).get("id").toString();
        log.debug("Generated video request {}", videoId);

        byte[] video = null;
        HttpEntity<?> entity = getHttpEntity();
        int maxTime = 60000;
        int totalTime = 0;
        int waitTime = 1000;
        do {
            Thread.sleep(waitTime);
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<Map> response = restTemplate.exchange(didUrl + "/" + endPoint + "/" + videoId, HttpMethod.GET, entity, Map.class);
            String status = Objects.requireNonNull(response.getBody()).get("status").toString();
            if (status.equals("done")) {
                String resultUrl = (String) response.getBody().get("result_url");
                if (resultUrl != null && FileStore.existsAtUrl(resultUrl)) {
                    video = FileStore.downloadFileBytes(new URL(resultUrl));
                }
            } else if (!isPending(status)) {
                throw new Exception("Unable to get video for " + videoId);
            }
            totalTime += waitTime;
        } while (video == null && totalTime < maxTime);

        if (video == null) {
            throw new Exception("Unable to get video in allocated time for " + videoId);
        }
        return video;
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
