package ai.asktheexpert.virtualassistant.services;

import ai.asktheexpert.virtualassistant.models.Camera;
import ai.asktheexpert.virtualassistant.models.Event;
import ai.asktheexpert.virtualassistant.repositories.FileStore;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@EnableScheduling
public class NestCameraService implements CameraService {

    public NestCameraService(OAuth2AuthorizedClientManager authorizedClientManager, OAuth2AuthorizedClientService authorizedClientService, FileStore fileStore) {
        this.authorizedClientManager = authorizedClientManager;
        this.authorizedClientService = authorizedClientService;
        this.fileStore = fileStore;
    }

    @Cacheable(value = "stream")
    public String getStream(Camera camera, String id) throws IOException {
        String accessToken = getAccessToken();
        WebClient webClient = WebClient.builder()
                .baseUrl("https://smartdevicemanagement.googleapis.com/v1")
                .clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create()
                                .responseTimeout(Duration.ofSeconds(60))
                ))
                .build();

        Map<String, String> params = new HashMap<>();
        params.put("command", "sdm.devices.commands.CameraLiveStream.GenerateRtspStream");

        Map response = webClient.post()
                .uri("/" + camera.getId() + ":executeCommand")
                .headers(headers -> {
                    {
                        headers.setBearerAuth(accessToken);
                        headers.set("command", "sdm.devices.commands.CameraLiveStream.GenerateRtspStream");
                    }
                })
                .bodyValue(params)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
        String streamUrl = ((Map) ((Map) response.get("results")).get("streamUrls")).get("rtspUrl").toString();
        String fileName = fileStore.getFileName(camera.getId(), FileStore.MediaType.MP4, id);
        byte[] clip = processStream(streamUrl, fileName);
        String url = fileStore.cache(clip, camera.getId(), FileStore.MediaType.MP4, id);
        log.debug("Finished processing stream");
        return url;
    }

    @Cacheable(value = "cameras")
    public List<Camera> getCameras() {
        if (cameras.isEmpty()) {
            String accessToken = getAccessToken();
            WebClient webClient = WebClient.builder()
                    .baseUrl("https://smartdevicemanagement.googleapis.com/v1")
                    .build();
            JsonNode jsonNode = webClient.get()
                    .uri("/enterprises/{project_id}/devices", projectId)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            JsonNode jsonDevices = jsonNode.get("devices");
            if (jsonDevices.isArray()) {
                for (JsonNode deviceNode : jsonDevices) {

                    String deviceType = deviceNode.get("type").asText();
                    if ("sdm.devices.types.CAMERA".equals(deviceType)) {
                        Camera camera = new Camera();
                        camera.setType("nest");
                        camera.setId(deviceNode.get("name").asText());
                        camera.setName(deviceNode.get("traits").get("sdm.devices.traits.Info").get("customName").asText());
                        if (camera.getName() == null || camera.getName().length() == 0) {
                            camera.setName(getCameraAlias(camera.getId()));
                        }
                        JsonNode protocolsNode = deviceNode.get("traits").path("sdm.devices.traits.CameraLiveStream").get("supportedProtocols");
                        for (JsonNode protocolNode : protocolsNode) {
                            String protocol = protocolNode.asText();
                            try {
                                Camera.Protocol availableProtocol = Camera.Protocol.valueOf(protocol);
                                camera.setStreamable(availableProtocol == Camera.Protocol.RTSP);
                                camera.getAvailableProtocols().add(availableProtocol);
                            } catch (IllegalArgumentException ex) {
                                log.info("Unmapped protocol {}", protocol);
                            }

                        }
                        cameras.add(camera);
                    }
                }
            }
        }
        return cameras;
    }

    @Override
    public List<Event> getEvents() {
        List<Event> events = new ArrayList<>();
        try {
            List<Camera> cameras = getCameras();
            for (Camera camera : cameras) {
                List<String> urls = fileStore.list(camera.getId(), FileStore.MediaType.MP4);
                for (String url : urls) {
                    Event event = new Event();
                    event.setCamera(camera);
                    event.setVideoUrl(url);
                    events.add(event);
                }
            }
        } catch (Exception ex) {
            log.debug("Couldn't pull events", ex);
        }
        return events;
    }

    private String getCameraAlias(String id) {
        for (Map.Entry<String, String> entry : cameraAliases.entrySet()) {
            if (id.equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        return "Camera-" + id.substring(id.length() - 4);
    }

    private String getAccessToken() {
        OAuth2AuthorizedClient authorizedClient = authorizedClientService.loadAuthorizedClient(authRegistration, authProxy);
        if (authorizedClient != null) {
            OAuth2AccessToken accessToken = authorizedClient.getAccessToken();
            if (accessToken.getExpiresAt().isBefore(Instant.now().plusSeconds(60))) {
                OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                        .withClientRegistrationId(authRegistration)
                        .principal(authProxy)
                        .build();
                OAuth2AuthorizedClient refreshedClient = authorizedClientManager.authorize(authorizeRequest);
                if (refreshedClient != null) {
                    return refreshedClient.getAccessToken().getTokenValue();
                } else {
                    throw new RuntimeException("Unable to refresh the access token.");
                }
            } else {
                return accessToken.getTokenValue();
            }
        } else {
            throw new RuntimeException("Unable to authenticate. Access an authenticated page through the website to set credentials");
        }
    }

    private final OAuth2AuthorizedClientManager authorizedClientManager;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final FileStore fileStore;

    @Value("${google.project.id}")
    private String projectId;
    @Value("${google.auth.proxy}")
    private String authProxy;
    @Value("${google.auth.registration}")
    private String authRegistration;

    @Value("#{${camera.alias}}")
    private Map<String, String> cameraAliases = new HashMap<>();
    private List<Camera> cameras = new ArrayList<>();
}
