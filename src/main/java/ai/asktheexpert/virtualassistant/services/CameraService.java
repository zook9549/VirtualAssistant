package ai.asktheexpert.virtualassistant.services;

import ai.asktheexpert.virtualassistant.models.Camera;
import ai.asktheexpert.virtualassistant.models.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Semaphore;

public interface CameraService {
    String getStream(Camera camera, String id) throws IOException;
    List<Camera> getCameras();
    List<Event> getEvents() throws IOException;


    default byte[] processStream(String url, String fileName) {
        log.debug("Getting stream from camera at " + url);
        int durationSeconds = 20;

        Path tempFile = null;
        try {
            // /apps/ffmpeg/ffmpeg-6.1-i686-static/ffmpeg -t 20 -f mp4 -strict experimental -strict experimental -i rtsps://stream-ue1-delta.dropcam.com:443/sdm_live_stream/CiUA2vuxryR-eBtauDOIH7SxvFQ_ot9WZ4hJhmiBTgz9_ajw0VcpEnEAge1nccwi1zazu4qHTsWL5qIpEIdUxwNK4MX6aXyT1uonddyRjfNHyT-9tPbP7wurOPS5UWMjP63fHGNY_nuPH9uzz2C_so4-dA5XSdv-7UEC_NBMdlXzR4OV8Y1OXRC0JOV6Hd2buNqNNydblFB4Sw?auth=g.0.eyJraWQiOiIyMzhiNTUxZmMyM2EyM2Y4M2E2ZTE3MmJjZTg0YmU3ZjgxMzAzMmM4IiwiYWxnIjoiUlMyNTYifQ.eyJpc3MiOiJuZXN0LXNlY3VyaXR5LWF1dGhwcm94eSIsInN1YiI6Im5lc3RfaWQ6bmVzdC1waG9lbml4LXByb2Q6NzU4NjUzMCIsInBvbCI6IjNwLW9hdXRoLXNjb3BlLUFQSV9TRE1fU0VSVklDRS1jbGllbnQtNTM4MTE3MzczMjg4LTRzcWV1ZDRpMWhsMWFnZHVsYmUxaXU3cDJha2xxZWxvLmFwcHMuZ29vZ2xldXNlcmNvbnRlbnQuY29tIiwiZXhwIjoxNzExNzU1MzQzfQ.KrfqVWKxynHYe8aIzAUdM56fo2RTDDbV1pTai7rlDNZaW8IZjqP0RlCnXQFBHJmYExAjOKU8UWCjrhsSbxRcjzAFyc_maUQh4C_X2Hd6j8-EFJw1pX8HnEOKafNtlE9PNlqZ6_tugpu_4_QMq_8wRoUbZaHivVpfEctN5apoqvGsYBm2lU-BmvoX46fEo645PMKqZ8sx5ilk0sXMsvGc84fhs_VUYMC6qJpnOZ9f7k9_6gSRTZ_BglVxSoF0DXfQjWOpXSQm3Yzjlfq2HMSFrCIvKY3N0jsOwXl0chaq8T_zrF8iIbimyEEADkk4unCp8S_NV-yvM5i_YYGg62ks9A -y temp.mp4
            tempFile = Files.createTempFile("ffmpeg-" + fileName.replace('/', '_'), ".mp4");
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

    Semaphore semaphore = new Semaphore(3);
    Logger log = LoggerFactory.getLogger(CameraService.class);
}
