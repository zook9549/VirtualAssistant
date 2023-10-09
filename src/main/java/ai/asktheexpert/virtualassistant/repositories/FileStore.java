package ai.asktheexpert.virtualassistant.repositories;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;

public interface FileStore {
    String cache(String name, byte[] contents) throws IOException;

    String save(String name, byte[] contents) throws IOException;

    byte[] get(String name) throws IOException;

    String getUrl(String name);

    boolean delete(String name);

    boolean exists(String name);

    public static final Map<String, String> MIME_TYPE_MAP = Map.of(
            "image/jpeg", "jpg",
            "video/mp4", "mp4",
            "audio/mpeg", "mp3"
    );

    public static byte[] downloadFileBytes(URL url) throws IOException {
        URLConnection connection = url.openConnection();
        try (InputStream inputStream = connection.getInputStream();
             ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[4096]; // 4KB buffer
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                byteArrayOutputStream.write(buffer, 0, bytesRead);
            }
            return byteArrayOutputStream.toByteArray();
        }
    }

    public static boolean existsAtUrl(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            int responseCode = connection.getResponseCode();
            connection.disconnect();
            return responseCode == HttpURLConnection.HTTP_OK;
        } catch (IOException e) {
            return false;
        }
    }
}
