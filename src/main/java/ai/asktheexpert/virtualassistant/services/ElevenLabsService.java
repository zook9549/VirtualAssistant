package ai.asktheexpert.virtualassistant.services;

import ai.asktheexpert.virtualassistant.repositories.FileStore;
import ai.asktheexpert.virtualassistant.models.Persona;
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

import java.io.IOException;
import java.util.*;

@Service
public class ElevenLabsService implements TextToSpeechService, PersonaService {

    public ElevenLabsService(ObjectMapper objectMapper, FileStore fileStore) {
        this.objectMapper = objectMapper;
        this.fileStore = fileStore;
    }

    @Cacheable(value = "tts", key = "#persona.name + #persona.currentMood + #text")
    public byte[] getTextToSpeech(Persona persona, String text) throws Exception {
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
        payload.put("model_id", "eleven_multilingual_v2");
        payload.put("voice_settings", config);
        String jsonRequest = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);

        HttpEntity<String> entity = new HttpEntity<>(jsonRequest, headers);
        String ttsFullUrl = tts_url + "/v1/text-to-speech/" + persona.getVoiceId() + "/stream?optimize_streaming_latency=0&output_format=mp3_44100_64";
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

    @Override
    public Collection<Persona> getPersonas() {
        log.debug("Getting all configured personas");
        List<Persona> personas = new ArrayList<>();
        HttpHeaders headers = new HttpHeaders();
        headers.add("xi-api-key", tts_key);
        headers.set("Content-Type", "application/json");

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(headers);

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Map> response = restTemplate.exchange(tts_url + "/v1/voices", HttpMethod.GET, entity, Map.class);
        List<Map<String, Object>> voices = (List) (response.getBody().get("voices"));
        for (Map voice : voices) {
            String name = voice.get("name").toString();
            if (voice.get("category").equals("cloned") && !name.endsWith("-")) {
                Persona persona = new Persona();
                persona.setName(name);
                persona.setRole(voice.get("description").toString());
                persona.setVoiceId(voice.get("voice_id").toString());
                persona.setAvatarId(persona.getName().toLowerCase() + ".jpg");
                personas.add(persona);
            }
        }
        log.debug("Personas found: {}", personas);
        return personas;
    }


    public Persona addPersona(String personaName, String personaRole, byte[] audio, byte[] profilePicture) throws IOException {
        String name = personaName.trim();
        log.debug("Adding persona {}", name);
        String fileName = name.toLowerCase() + ".jpg";
        fileStore.save(fileName, profilePicture);

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
        log.debug("Response {}", response.getBody());
        String voiceId = response.getBody().get("voice_id").toString();
        Persona persona = new Persona();
        persona.setRole(personaRole);
        persona.setName(name);
        persona.setVoiceId(voiceId);
        persona.setAvatarId(persona.getName().toLowerCase() + ".jpg");
        log.debug("Added persona {}", persona);
        return persona;
    }

    @Value("${tts_url}")
    private String tts_url;
    @Value("${tts_key}")
    private String tts_key;

    private final ObjectMapper objectMapper;
    private final FileStore fileStore;

    private static final Logger log = LoggerFactory.getLogger(ElevenLabsService.class);
}
