package org.flossware.cloud.storage;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * AWS S3 implementation of CloudStorageClient.
 * Supports IAM role authentication, access key authentication, and regional buckets.
 * Requires the AWS SDK for Java 2.x dependency.
 */
public class S3CloudStorageClient implements CloudStorageClient {
    private final S3Client s3Client;
    private final String bucketName;
    private final String prefix;

    private S3CloudStorageClient(S3Client s3Client, String bucketName, String prefix) {
        this.s3Client = Objects.requireNonNull(s3Client, "s3Client cannot be null");
        this.bucketName = Objects.requireNonNull(bucketName, "bucketName cannot be null");
        this.prefix = prefix != null ? prefix : "";
    }

    @Override
    public byte[] readFile(String path) throws IOException {
        String key = buildKey(path);

        try {
            GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

            try (ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = response.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }

                return out.toByteArray();
            }
        } catch (Exception e) {
            throw new IOException("Failed to read file from S3: " + key, e);
        }
    }

    @Override
    public InputStream openFile(String path) throws IOException {
        String key = buildKey(path);

        try {
            GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

            return s3Client.getObject(request);
        } catch (Exception e) {
            throw new IOException("Failed to open file from S3: " + key, e);
        }
    }

    @Override
    public boolean exists(String path) throws IOException {
        String key = buildKey(path);

        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

            s3Client.headObject(request);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            throw new IOException("Failed to check file existence in S3: " + key, e);
        }
    }

    @Override
    public List<String> list(String prefix) throws IOException {
        String key = buildKey(prefix);

        try {
            ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(key)
                .build();

            ListObjectsV2Response response = s3Client.listObjectsV2(request);

            return response.contents().stream()
                .map(S3Object::key)
                .map(this::removePrefix)
                .collect(Collectors.toList());
        } catch (Exception e) {
            throw new IOException("Failed to list files in S3: " + key, e);
        }
    }

    @Override
    public long getFileSize(String path) throws IOException {
        String key = buildKey(path);

        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

            return s3Client.headObject(request).contentLength();
        } catch (NoSuchKeyException e) {
            throw new IOException("File not found in S3: " + key, e);
        } catch (Exception e) {
            throw new IOException("Failed to get file size from S3: " + key, e);
        }
    }

    @Override
    public String getDescription() {
        return "S3CloudStorageClient[bucket=" + bucketName + ", prefix=" + prefix + "]";
    }

    @Override
    public void close() {
        if (s3Client != null) {
            s3Client.close();
        }
    }

    private String buildKey(String path) {
        if (prefix.isEmpty()) {
            return path;
        }
        return prefix + (prefix.endsWith("/") ? "" : "/") + path;
    }

    private String removePrefix(String key) {
        if (prefix.isEmpty()) {
            return key;
        }
        String prefixWithSlash = prefix.endsWith("/") ? prefix : prefix + "/";
        if (key.startsWith(prefixWithSlash)) {
            return key.substring(prefixWithSlash.length());
        }
        return key;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Region region = Region.US_EAST_1;
        private String bucketName;
        private String prefix;
        private AwsCredentialsProvider credentialsProvider;
        private String accessKeyId;
        private String secretAccessKey;

        public Builder region(Region region) {
            this.region = region;
            return this;
        }

        public Builder region(String regionName) {
            this.region = Region.of(regionName);
            return this;
        }

        public Builder bucket(String bucketName) {
            this.bucketName = bucketName;
            return this;
        }

        public Builder prefix(String prefix) {
            this.prefix = prefix;
            return this;
        }

        public Builder credentials(String accessKeyId, String secretAccessKey) {
            this.accessKeyId = accessKeyId;
            this.secretAccessKey = secretAccessKey;
            return this;
        }

        public Builder credentialsProvider(AwsCredentialsProvider provider) {
            this.credentialsProvider = provider;
            return this;
        }

        public S3CloudStorageClient build() {
            Objects.requireNonNull(bucketName, "bucketName must be set");

            AwsCredentialsProvider provider;
            if (credentialsProvider != null) {
                provider = credentialsProvider;
            } else if (accessKeyId != null && secretAccessKey != null) {
                provider = StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKeyId, secretAccessKey)
                );
            } else {
                provider = DefaultCredentialsProvider.create();
            }

            S3Client client = S3Client.builder()
                .region(region)
                .credentialsProvider(provider)
                .build();

            return new S3CloudStorageClient(client, bucketName, prefix);
        }
    }
}
