package ai.asktheexpert.virtualassistant.services;

import ai.asktheexpert.virtualassistant.models.Assistant;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Service
// @CacheConfig(cacheManager = "fileStoreCacheManager")
public class ElevenLabsService implements TextToSpeechService {

    public ElevenLabsService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Cacheable(value = "tts", key = "#assistant.assistantId + #text")
    public byte[] getTextToSpeech(Assistant assistant, String text) throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.add("xi-api-key", tts_key);
        headers.add("Content-Type", "application/json");


        Map<String, Object> config = new HashMap<>();
        config.put("stability", ".4");
        config.put("similarity_boost", "0.2");
        config.put("style", "0.5");
        config.put("use_speaker_boost", "true");

        Map<String, Object> payload = new HashMap<>();
        payload.put("text", text);
        payload.put("model_id", "eleven_turbo_v2"); //eleven_multilingual_v2
        payload.put("voice_settings", config);
        String jsonRequest = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);

        HttpEntity<String> entity = new HttpEntity<>(jsonRequest, headers);
        String ttsFullUrl = tts_url + "/v1/text-to-speech/" + assistant.getVoiceId() + "/stream?optimize_streaming_latency=0&output_format=mp3_44100_64";
        ResponseEntity<byte[]> response = restTemplate.exchange(ttsFullUrl, HttpMethod.POST, entity, byte[].class);
        log.debug("Completed getting text to speech");
        return response.getBody();
    }

    public int creditsRemaining() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("xi-api-key", tts_key);
        headers.set("Content-Type", "application/json");

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(headers);

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Map> response = restTemplate.exchange(tts_url + "/v1/user/subscription", HttpMethod.GET, entity, Map.class);
        Map vals = response.getBody();
        int remaining = (Integer) Objects.requireNonNull(vals).get("character_limit") - (Integer) vals.get("character_count");
        log.debug("Credit information for TTS: {}", vals);
        return remaining;
    }

    public String save(String name, String personaRole, byte[] audio)  {
        HttpHeaders headers = new HttpHeaders();
        headers.add("xi-api-key", tts_key);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ByteArrayResource fileResource = new ByteArrayResource(audio) {
            @Override
            public String getFilename() {
                return "soundsample.mp3";
            }
        };

        HttpHeaders audioHeaders = new HttpHeaders();
        audioHeaders.setContentType(MediaType.valueOf("audio/mpeg"));
        HttpEntity<Resource> audioEntity = new HttpEntity<>(fileResource, audioHeaders);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("files", audioEntity);
        body.add("name", name);
        body.add("description", personaRole);

        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Map> response = restTemplate.exchange(tts_url + "/v1/voices/add", HttpMethod.POST, entity, Map.class);
        String voiceId = response.getBody().get("voice_id").toString();
        log.debug("Added voice {}", voiceId);
        return voiceId;
    }

    @Value("${tts_url}")
    private String tts_url;
    @Value("${tts_key}")
    private String tts_key;

    private final ObjectMapper objectMapper;

    private static final Logger log = LoggerFactory.getLogger(ElevenLabsService.class);
}
