package ai.asktheexpert.virtualassistant.services;

import ai.asktheexpert.virtualassistant.models.Account;
import ai.asktheexpert.virtualassistant.models.Camera;
import ai.asktheexpert.virtualassistant.models.Event;
import ai.asktheexpert.virtualassistant.repositories.FileStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.text.MessageFormat;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@EnableScheduling
public class BlinkCameraService implements CameraService {
    public BlinkCameraService(ObjectMapper objectMapper, FileStore fileStore, AnswerService answerService, EventService eventService) {
        this.objectMapper = objectMapper;
        this.fileStore = fileStore;
        this.answerService = answerService;
        this.eventService = eventService;
    }


    @Scheduled(fixedDelay = 300000)
    public List<Event> getEvents() throws IOException {
        ArrayList<Event> results = new ArrayList<>();
        List<Camera> cameras = getCameras();
        Account account = getAccount();
        int page = 1;
        boolean hasMoreResults;
        do {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.add("token-auth", account.getAuthToken());
            HttpEntity<String> entity = new HttpEntity<>("parameters", headers);
            String url = MessageFormat.format(videosUrl, account.getRegion(), account.getAccountId(), "1970-01-01T00:00:00+0000", page);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            Collection<Map> mediaResults = (Collection) response.getBody().get("media");
            log.debug("Found {} results from Blink", mediaResults.size());
            for (Map map : mediaResults) {
                String cameraId = map.get("device_id").toString();
                Camera selectedCamera = cameras.stream().filter(camera -> camera.getId().equals("blink/" + cameraId)).findFirst().get();
                Event event = new Event();
                event.setEventId(map.get("id").toString());
                String mediaUrl = MessageFormat.format(postAuthUrl, account.getRegion()) + map.get("media").toString();
                event.setEventDateTime(ZonedDateTime.parse(map.get("created_at").toString(), DateTimeFormatter.ISO_OFFSET_DATE_TIME).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime());
                event.setCamera(selectedCamera);
                String medialUrl = fileStore.getUrl(event.getCamera().getId(), FileStore.MediaType.MP4, event.getEventId());
                try {
                    if (medialUrl == null) {
                        String videoUrl = fileStore.save(getVideo(account, mediaUrl), event.getCamera().getId(), FileStore.MediaType.MP4, event.getEventId());
                        event.setVideoUrl(videoUrl);
                    } else {
                        log.debug("Existing file found at {}", medialUrl);
                        event.setVideoUrl(medialUrl);
                    }
                    String narration = answerService.narrate(event, null);
                    event.setNarration(narration);
                    event.setStatus(Event.Status.DONE);
                    eventService.save(event);
                    results.add(event);
                } catch (Exception ex) {
                    log.error("Unable to retrieve media file " + mediaUrl, ex);
                }
            }
            int limit = Integer.parseInt(response.getBody().get("limit").toString());
            hasMoreResults = mediaResults.size() == limit;
            page++;
        } while (hasMoreResults);
        return results;
    }

    @Override
    public String getStream(Camera camera, String id) throws IOException {
        Account account = getAccount();
        RestTemplate restTemplate = new RestTemplate();
        Map<String, String> map = new HashMap<>();
        map.put("intent", "liveview");
        map.put("motion_event_start_time", "");
        String jsonRequest = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(map);
        HttpHeaders headers = new HttpHeaders();
        headers.add("token-auth", account.getAuthToken());
        HttpEntity<String> entity = new HttpEntity<>(jsonRequest, headers);
        String parsedId = camera.getId().replace(camera.getType() + "/", "");
        String videoUrl = MessageFormat.format(liveStreamUrl, account.getRegion(), account.getAccountId(), camera.getNetworkId(), parsedId);

        ResponseEntity<Map> response = restTemplate.exchange(videoUrl, HttpMethod.POST, entity, Map.class);
        String streamUrl = response.getBody().get("server").toString();
        String fileName = fileStore.getFileName(camera.getId(), FileStore.MediaType.MP4, id);
        byte[] clip = processStream(streamUrl, fileName);
        String url = fileStore.cache(clip, camera.getId(), FileStore.MediaType.MP4, id);
        return url;
    }

    @Override
    @Cacheable(value = "blinkCameras")
    public List<Camera> getCameras() {
        if (cameras.isEmpty()) {
            Account account = getAccount();
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.add("token-auth", account.getAuthToken());
            HttpEntity<String> entity = new HttpEntity<>("parameters", headers);
            String videoUrl = MessageFormat.format(homeScreenUrl, account.getRegion(), account.getAccountId());
            ResponseEntity<Map> response = restTemplate.exchange(videoUrl, HttpMethod.GET, entity, Map.class);
            List<Map<String, Object>> cameraList = (List<Map<String, Object>>) response.getBody().get("cameras");
            for (Map<String, Object> cameraMap : cameraList) {
                if (cameraMap.get("status").equals("done")) {
                    Camera camera = new Camera();
                    camera.setType("blink");
                    camera.setStreamable(true);
                    camera.getAvailableProtocols().add(Camera.Protocol.RTSP);
                    camera.setId(camera.getType() + "/" + cameraMap.get("id").toString());
                    camera.setName(cameraMap.get("name").toString());
                    camera.setNetworkId(cameraMap.get("network_id").toString());
                    cameras.add(camera);
                }
            }
        }
        return cameras;
    }

    public Account getAccount() {
        Account account = new Account();
        account.setUniqueId(uuid);

        RestTemplate restTemplate = new RestTemplate();
        Map<String, String> map = new HashMap<>();
        map.put("email", email);
        map.put("password", password);
        map.put("unique_id", uuid);
        map.put("reauth", "true");
        try {
            String jsonRequest = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(map);
            HttpEntity<String> entity = new HttpEntity<>(jsonRequest);
            ResponseEntity<Map> response = restTemplate.exchange(blinkLoginUrl, HttpMethod.POST, entity, Map.class);
            Map vals = response.getBody();
            account.setAccountId(((Map) vals.get("account")).get("account_id").toString());
            account.setClientId(((Map) vals.get("account")).get("client_id").toString());
            account.setAuthToken(((Map) vals.get("auth")).get("token").toString());
            account.setRegion(((Map) vals.get("account")).get("tier").toString());
            account.setPinRequired(Boolean.getBoolean(((Map) vals.get("account")).get("account_verification_required").toString()));
        } catch (JsonProcessingException ex) {
            throw new RuntimeException(ex);
        }
        return account;
    }

    private byte[] getVideo(Account account, String sourceUrl) {
        RestTemplate restMediaTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.add("token-auth", account.getAuthToken());
        HttpEntity<String> entity = new HttpEntity<>("parameters", headers);
        ResponseEntity<byte[]> mediaResponse = restMediaTemplate.exchange(sourceUrl, HttpMethod.GET, entity, byte[].class);
        return mediaResponse.getBody();
    }

    private final ObjectMapper objectMapper;
    private final FileStore fileStore;
    private final AnswerService answerService;
    private final EventService eventService;

    @Value("${blink.login.url}")
    private String blinkLoginUrl;
    @Value("${blink.postauth.url}")
    private String postAuthUrl;
    @Value("${blink.email}")
    private String email;
    @Value("${blink.pwd}")
    private String password;
    @Value("${blink.videos.url}")
    private String videosUrl;
    @Value("${blink.homescreen.url}")
    private String homeScreenUrl;
    @Value("${blink.livestream.url}")
    private String liveStreamUrl;
    @Value("${blink.uuid}")
    private String uuid;
    private final List<Camera> cameras = new ArrayList<>();

    private static final Logger log = LoggerFactory.getLogger(BlinkCameraService.class);
}
