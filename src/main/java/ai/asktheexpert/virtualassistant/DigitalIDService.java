package ai.asktheexpert.virtualassistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

@Service
public class DigitalIDService implements AvatarService, CreditService {
    public DigitalIDService(FileStore fileStore, ObjectMapper objectMapper) {
        this.fileStore = fileStore;
        this.objectMapper = objectMapper;
    }

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
        String imgUrl = fileStore.getUrl(persona.getName().toLowerCase() + ".jpg");
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + didKey);
        headers.set("Content-Type", "application/json");

        Map<String, Object> config = new HashMap<>();
        config.put("fluent", "false");
        config.put("pad_audio", "0.0");
        config.put("result_format", "mp4");

        Map<String, Object> payload = new HashMap<>();
        payload.put("source_url", imgUrl);
        payload.put("config", config);
        payload.put("script", audioHeader);
        String jsonRequest = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);

        HttpEntity<String> entity = new HttpEntity<>(jsonRequest, headers);

        // Make the API call
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Map> response = restTemplate.exchange(didUrl + "/talks", HttpMethod.POST, entity, Map.class);
        String id = response.getBody().get("id").toString();
        log.debug("Generated video {}", id);
        int maxTime = 20000;
        int totalTime = 0;
        int waitTime = 1000;
        do {
            Thread.sleep(waitTime);
            video = fetchVideo(id);
            totalTime += waitTime;
        } while (video == null && totalTime < maxTime);
        return video;
    }

    public int creditsRemaining() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + didKey);
        headers.set("Content-Type", "application/json");

        // Create an HttpEntity object
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(headers);

        // Make the API call
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Map> response = restTemplate.exchange(didUrl + "/credits", HttpMethod.GET, entity, Map.class);
        Map vals = ((Map) ((List) response.getBody().get("credits")).get(0));
        int remaining = (Integer) vals.get("remaining");
        log.debug("Credit information for D-ID: {}", vals);
        return remaining;
    }

    private byte[] fetchVideo(String videoId) throws Exception {
        byte[] results = null;
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + didKey);
        headers.set("Content-Type", "application/json");

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(headers);

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Map> response = restTemplate.exchange(didUrl + "/talks/" + videoId, HttpMethod.GET, entity, Map.class);
        String status = response.getBody().get("status").toString();
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
