package org.flossware.cloud.storage;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Google Cloud Storage implementation of CloudStorageClient.
 * Supports service account and application default credentials authentication.
 * Requires the Google Cloud Storage SDK dependency.
 */
public class GcsCloudStorageClient implements CloudStorageClient {
    private final Storage storage;
    private final String bucketName;
    private final String prefix;

    private GcsCloudStorageClient(Storage storage, String bucketName, String prefix) {
        this.storage = Objects.requireNonNull(storage, "storage cannot be null");
        this.bucketName = Objects.requireNonNull(bucketName, "bucketName cannot be null");
        this.prefix = prefix != null ? prefix : "";
    }

    @Override
    public byte[] readFile(String path) throws IOException {
        String blobName = buildBlobName(path);
        BlobId blobId = BlobId.of(bucketName, blobName);
        Blob blob = storage.get(blobId);

        if (blob == null) {
            throw new IOException("File not found in GCS: " + blobName);
        }

        try {
            return blob.getContent();
        } catch (Exception e) {
            throw new IOException("Failed to read file from GCS: " + blobName, e);
        }
    }

    @Override
    public InputStream openFile(String path) throws IOException {
        String blobName = buildBlobName(path);
        BlobId blobId = BlobId.of(bucketName, blobName);
        Blob blob = storage.get(blobId);

        if (blob == null) {
            throw new IOException("File not found in GCS: " + blobName);
        }

        try {
            return Channels.newInputStream(blob.reader());
        } catch (Exception e) {
            throw new IOException("Failed to open file from GCS: " + blobName, e);
        }
    }

    @Override
    public boolean exists(String path) throws IOException {
        String blobName = buildBlobName(path);
        BlobId blobId = BlobId.of(bucketName, blobName);

        try {
            Blob blob = storage.get(blobId);
            return blob != null && blob.exists();
        } catch (Exception e) {
            throw new IOException("Failed to check file existence in GCS: " + blobName, e);
        }
    }

    @Override
    public List<String> list(String prefix) throws IOException {
        String blobPrefix = buildBlobName(prefix);
        List<String> files = new ArrayList<>();

        try {
            for (Blob blob : storage.list(bucketName, Storage.BlobListOption.prefix(blobPrefix)).iterateAll()) {
                String name = removePrefix(blob.getName());
                files.add(name);
            }
            return files;
        } catch (Exception e) {
            throw new IOException("Failed to list files in GCS: " + blobPrefix, e);
        }
    }

    @Override
    public long getFileSize(String path) throws IOException {
        String blobName = buildBlobName(path);
        BlobId blobId = BlobId.of(bucketName, blobName);
        Blob blob = storage.get(blobId);

        if (blob == null) {
            throw new IOException("File not found in GCS: " + blobName);
        }

        return blob.getSize();
    }

    @Override
    public String getDescription() {
        return "GcsCloudStorageClient[bucket=" + bucketName + ", prefix=" + prefix + "]";
    }

    @Override
    public void close() {
        // GCS SDK doesn't require explicit cleanup
    }

    private String buildBlobName(String path) {
        if (prefix.isEmpty()) {
            return path;
        }
        return prefix + (prefix.endsWith("/") ? "" : "/") + path;
    }

    private String removePrefix(String blobName) {
        if (prefix.isEmpty()) {
            return blobName;
        }
        String prefixWithSlash = prefix.endsWith("/") ? prefix : prefix + "/";
        if (blobName.startsWith(prefixWithSlash)) {
            return blobName.substring(prefixWithSlash.length());
        }
        return blobName;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String projectId;
        private String bucketName;
        private String prefix;
        private Storage storage;

        public Builder projectId(String projectId) {
            this.projectId = projectId;
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

        public Builder storage(Storage storage) {
            this.storage = storage;
            return this;
        }

        public GcsCloudStorageClient build() {
            Objects.requireNonNull(bucketName, "bucketName must be set");

            Storage storageClient;
            if (storage != null) {
                storageClient = storage;
            } else if (projectId != null) {
                storageClient = StorageOptions.newBuilder()
                    .setProjectId(projectId)
                    .build()
                    .getService();
            } else {
                storageClient = StorageOptions.getDefaultInstance().getService();
            }

            return new GcsCloudStorageClient(storageClient, bucketName, prefix);
        }
    }
}
