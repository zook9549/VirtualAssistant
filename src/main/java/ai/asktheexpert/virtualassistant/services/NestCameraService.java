package ai.asktheexpert.virtualassistant.services;

import ai.asktheexpert.virtualassistant.models.Camera;
import ai.asktheexpert.virtualassistant.repositories.FileStore;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

@Service
public class NestCameraService implements CameraService {

    public NestCameraService(WebClient nestWebClient, OAuth2AuthorizedClientService authorizedClientService, FileStore fileStore) {
        this.webClient = nestWebClient;
        this.authorizedClientService = authorizedClientService;
        this.fileStore = fileStore;
    }

    @Cacheable(value = "stream")
    public String getStream(Camera camera, String id) throws IOException {
        Map<String, String> params = new HashMap<>();
        params.put("command", "sdm.devices.commands.CameraLiveStream.GenerateRtspStream");

        Map response = webClient.post()
                .uri("/" + camera.getId() + ":executeCommand")
                .headers(headers -> {
                    {
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
        List<Camera> results = new ArrayList<>();
        JsonNode jsonNode = webClient.get()
                .uri("/enterprises/{project_id}/devices", projectId)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
        JsonNode jsonDevices = jsonNode.get("devices");
        if (jsonDevices.isArray()) {
            for (JsonNode deviceNode : jsonDevices) {

                String deviceType = deviceNode.get("type").asText();
                if ("sdm.devices.types.CAMERA" .equals(deviceType)) {
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
                    results.add(camera);
                }
            }
        }
        return results;
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
            return authorizedClient.getAccessToken().getTokenValue();
        } else {
            throw new RuntimeException("Unable to authenticate.  Access an authenticated page through the website to set credentials");
        }
    }

    public byte[] processStream(String url, String fileName) {
        log.debug("Getting stream from camera at " + url);
        int durationSeconds = 20;

        Path tempFile = null;
        try {
            // ffmpeg -t 20 -f mp4 -strict experimental -strict experimental -i -y temp.mp4
            tempFile = Files.createTempFile("ffmpeg-" + fileName, ".mp4");
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg",
                    "-i", url,
                    "-f", "mp4",
                    "-t", String.valueOf(durationSeconds),
                    "-strict", "experimental", // Allow experimental codecs for audio
                    "-y",                      // Overwrite output file if it exists
                    tempFile.toString()            // Output file path
            );
            process(pb);
            return Files.readAllBytes(tempFile);
        } catch (IOException e) {
            log.error("Error creating temp file", e);
            throw new RuntimeException("Failed to process video stream", e);
        } finally {
            try {
                if (tempFile != null) {
                    Files.delete(tempFile);
                }
            } catch (IOException e) {
                log.info("Unable to clean up temp file", e);
            }
        }
    }

    private void process(ProcessBuilder pb) {
        try {
            semaphore.acquire();
            Process process = pb.start();
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        log.trace(line);
                    }
                } catch (IOException e) {
                    log.error("Error occurred while processing camera", e);
                }
            }).start();

            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        log.trace(line);
                    }
                } catch (IOException e) {
                    log.error("Error occurred while processing camera", e);
                }
            }).start();

            int exitCode = process.waitFor();
            log.debug("FFMPEG exited with code: " + exitCode);
            if (exitCode != 0) {
                throw new RuntimeException("Unable to process video due to unexpected exit code " + exitCode);
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Error occurred while generating video from livestream", e);
        } finally {
            semaphore.release();
        }
    }

    private final WebClient webClient;
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

    private static final Semaphore semaphore = new Semaphore(3);

    private static final Logger log = LoggerFactory.getLogger(NestCameraService.class);
}
