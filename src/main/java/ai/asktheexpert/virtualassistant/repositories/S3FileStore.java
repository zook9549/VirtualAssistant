package ai.asktheexpert.virtualassistant.repositories;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

@Service
public class S3FileStore implements FileStore {

    public S3FileStore(AmazonS3 amazonS3) {
        this.amazonS3 = amazonS3;
    }

    public String save(String name, byte[] contents) throws IOException {
        if (!exists(name)) {
            log.info("Uploading new file: {}", name);
        } else {
            log.info("Updating an existing file {}", name);
        }
        try (InputStream is = new ByteArrayInputStream(contents);) {
            ObjectMetadata metaData = new ObjectMetadata();
            metaData.setContentLength(contents.length);
            metaData.setContentType(getMimeTypeFromExtension(name));
            PutObjectRequest objectRequest = new PutObjectRequest(bucket, name, is, metaData);
            amazonS3.putObject(objectRequest);
            return ((AmazonS3Client) amazonS3).getResourceUrl(bucket, name);
        }
    }

    public byte[] get(String name) throws IOException {
        if (exists(name)) {
            log.info("Fetching file: {}", name);
            S3Object result = amazonS3.getObject(bucket, name);
            return toByteArray(result.getObjectContent());
        } else {
            log.info("Updating an existing file {}", name);
            return null;
        }
    }

    @Override
    public String getUrl(String name) {
        if (exists(name)) {
            log.info("Fetching file: {}", name);
            return ((AmazonS3Client) amazonS3).getResourceUrl(bucket, name);
        } else {
            log.info("No external file {}", name);
            return null;
        }
    }

    @Override
    public boolean delete(String name) {
        if (exists(name)) {
            log.info("Deleting  file: {}", name);
            amazonS3.deleteObject(bucket, name);
            return true;
        } else {
            log.info("Skipping delete since file doesn't exist: {}", name);
            return false;
        }
    }

    public boolean exists(String name) {
        return amazonS3.doesObjectExist(bucket, name);
    }

    private String getMimeTypeFromExtension(String name) {
        String extension = FilenameUtils.getExtension(name);
        Optional<String> match = MIME_TYPE_MAP.entrySet()
                .stream()
                .filter(entry -> extension.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .findFirst();

        return match.orElse(null);
    }

    private byte[] toByteArray(InputStream inputStream) throws IOException {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                byteArrayOutputStream.write(buffer, 0, len);
            }
            return byteArrayOutputStream.toByteArray();
        }
    }


    @Value("${aws.s3.bucket}")
    private String bucket;

    private final AmazonS3 amazonS3;

    private static final Logger log = LoggerFactory.getLogger(S3FileStore.class);
}
