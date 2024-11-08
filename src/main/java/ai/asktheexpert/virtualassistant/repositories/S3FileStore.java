package ai.asktheexpert.virtualassistant.repositories;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.*;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Service
public class S3FileStore implements FileStore {

    public S3FileStore(AmazonS3 amazonS3) {
        this.amazonS3 = amazonS3;
    }


    @Cacheable(value = "s3",  key = "T(ai.asktheexpert.virtualassistant.repositories.FileStore).getFileName(#name, #mediaType, #params)")
    public String cache(byte[] contents, String name, MediaType mediaType, Object... params) throws IOException {
        String fileName = FileStore.getFileName(name, mediaType, params);
        String path = save(contents, fileName);
        List<Tag> tags = List.of(new Tag("TempDate", String.valueOf(System.currentTimeMillis())));
        amazonS3.setObjectTagging(new SetObjectTaggingRequest(
                bucket,
                fileName,
                new ObjectTagging(tags)
        ));
        return path;
    }

    @Cacheable(value = "s3",  key = "T(ai.asktheexpert.virtualassistant.repositories.FileStore).getFileName(#name, #mediaType, #params)")
    public String save(byte[] contents, String name, MediaType mediaType, Object... params) throws IOException {
        String fileName = FileStore.getFileName(name, mediaType, params);
        return save(contents, fileName);
    }

    @Cacheable(value = "s3",  key = "T(ai.asktheexpert.virtualassistant.repositories.FileStore).getFileName(#name, #mediaType, #params)")
    public byte[] get(String name, MediaType mediaType, Object... params) throws IOException {
        String fileName = FileStore.getFileName(name, mediaType, params);
        if (exists(fileName)) {
            log.debug("Fetching file: {}", fileName);
            S3Object result = amazonS3.getObject(bucket, fileName);
            return toByteArray(result.getObjectContent());
        } else {
            log.info("File does not exist: {}", fileName);
            return null;
        }
    }

    private String save(byte[] contents, String fileName) throws IOException {
        if (!exists(fileName)) {
            log.debug("Uploading new file: {}", fileName);
        } else {
            log.debug("Updating an existing file {}", fileName);
        }
        try (InputStream is = new ByteArrayInputStream(contents)) {
            ObjectMetadata metaData = new ObjectMetadata();
            metaData.setContentLength(contents.length);
            metaData.setContentType(getMimeTypeFromExtension(fileName));
            PutObjectRequest objectRequest = new PutObjectRequest(bucket, fileName, is, metaData);
            amazonS3.putObject(objectRequest);
            return ((AmazonS3Client) amazonS3).getResourceUrl(bucket, fileName);
        }
    }

    public List<String> list(String prefix, MediaType mediaType) {
        List<String> files = new ArrayList<>();
        ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
                .withBucketName(bucket)
                .withPrefix(prefix);

        ObjectListing objectListing;
        do {
            objectListing = amazonS3.listObjects(listObjectsRequest);
            for (S3ObjectSummary objectSummary : objectListing.getObjectSummaries()) {
                if (objectSummary.getKey().endsWith(mediaType.getExtension())) {
                    files.add(((AmazonS3Client) amazonS3).getResourceUrl(bucket, objectSummary.getKey()));
                }
            }
            listObjectsRequest.setMarker(objectListing.getNextMarker());
        } while (objectListing.isTruncated());
        return files;
    }

    @Override
    public String getUrl(String name, MediaType mediaType, Object... params) {
        String fileName = FileStore.getFileName(name, mediaType, params);
        if (exists(fileName)) {
            log.trace("Fetching file path: {}", fileName);
            return ((AmazonS3Client) amazonS3).getResourceUrl(bucket, fileName);
        } else {
            log.info("No external file path found: {}", fileName);
            return null;
        }
    }

    @Override
    @CacheEvict(value = "s3")
    public boolean delete(String name, MediaType mediaType, Object... params) {
        String fileName = FileStore.getFileName(name, mediaType, params);
        if (exists(fileName)) {
            log.debug("Deleting  file: {}", fileName);
            amazonS3.deleteObject(bucket, fileName);
            return true;
        } else {
            log.debug("Skipping delete since file doesn't exist: {}", fileName);
            return false;
        }
    }

    public boolean exists(String name, MediaType mediaType, Object... params) {
        return amazonS3.doesObjectExist(bucket, FileStore.getFileName(name, mediaType, params));
    }

    private boolean exists(String name) {
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
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
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
