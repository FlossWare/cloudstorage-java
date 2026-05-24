package org.flossware.cloud.storage;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.common.StorageSharedKeyCredential;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Azure Blob Storage implementation of CloudStorageClient.
 * Supports connection string and shared key credential authentication.
 * Requires the Azure Storage Blob SDK dependency.
 */
public class AzureBlobCloudStorageClient implements CloudStorageClient {
    private final BlobContainerClient containerClient;
    private final String prefix;

    private AzureBlobCloudStorageClient(BlobContainerClient containerClient, String prefix) {
        this.containerClient = Objects.requireNonNull(containerClient, "containerClient cannot be null");
        this.prefix = prefix != null ? prefix : "";
    }

    @Override
    public byte[] readFile(String path) throws IOException {
        String blobName = buildBlobName(path);
        BlobClient blobClient = containerClient.getBlobClient(blobName);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            blobClient.downloadStream(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IOException("Failed to read file from Azure Blob: " + blobName, e);
        }
    }

    @Override
    public InputStream openFile(String path) throws IOException {
        String blobName = buildBlobName(path);
        BlobClient blobClient = containerClient.getBlobClient(blobName);

        try {
            return blobClient.openInputStream();
        } catch (Exception e) {
            throw new IOException("Failed to open file from Azure Blob: " + blobName, e);
        }
    }

    @Override
    public boolean exists(String path) throws IOException {
        String blobName = buildBlobName(path);
        BlobClient blobClient = containerClient.getBlobClient(blobName);

        try {
            return blobClient.exists();
        } catch (Exception e) {
            throw new IOException("Failed to check file existence in Azure Blob: " + blobName, e);
        }
    }

    @Override
    public List<String> list(String prefix) throws IOException {
        String blobPrefix = buildBlobName(prefix);
        List<String> files = new ArrayList<>();

        try {
            for (BlobItem item : containerClient.listBlobsByHierarchy(blobPrefix)) {
                String name = removePrefix(item.getName());
                files.add(name);
            }
            return files;
        } catch (Exception e) {
            throw new IOException("Failed to list files in Azure Blob: " + blobPrefix, e);
        }
    }

    @Override
    public long getFileSize(String path) throws IOException {
        String blobName = buildBlobName(path);
        BlobClient blobClient = containerClient.getBlobClient(blobName);

        try {
            return blobClient.getProperties().getBlobSize();
        } catch (Exception e) {
            throw new IOException("Failed to get file size from Azure Blob: " + blobName, e);
        }
    }

    @Override
    public String getDescription() {
        return "AzureBlobCloudStorageClient[container=" + containerClient.getBlobContainerName() +
               ", prefix=" + prefix + "]";
    }

    @Override
    public void close() {
        // Azure SDK doesn't require explicit cleanup for BlobContainerClient
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
        private String accountName;
        private String accountKey;
        private String connectionString;
        private String containerName;
        private String prefix;
        private String endpoint;

        public Builder accountName(String accountName) {
            this.accountName = accountName;
            return this;
        }

        public Builder accountKey(String accountKey) {
            this.accountKey = accountKey;
            return this;
        }

        public Builder connectionString(String connectionString) {
            this.connectionString = connectionString;
            return this;
        }

        public Builder container(String containerName) {
            this.containerName = containerName;
            return this;
        }

        public Builder prefix(String prefix) {
            this.prefix = prefix;
            return this;
        }

        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        public AzureBlobCloudStorageClient build() {
            Objects.requireNonNull(containerName, "containerName must be set");

            BlobServiceClient serviceClient;

            if (connectionString != null) {
                serviceClient = new BlobServiceClientBuilder()
                    .connectionString(connectionString)
                    .buildClient();
            } else if (accountName != null && accountKey != null) {
                String endpointUrl = endpoint != null ? endpoint :
                    "https://" + accountName + ".blob.core.windows.net";

                StorageSharedKeyCredential credential =
                    new StorageSharedKeyCredential(accountName, accountKey);

                serviceClient = new BlobServiceClientBuilder()
                    .endpoint(endpointUrl)
                    .credential(credential)
                    .buildClient();
            } else {
                throw new IllegalStateException(
                    "Either connectionString or (accountName + accountKey) must be provided");
            }

            BlobContainerClient containerClient = serviceClient.getBlobContainerClient(containerName);
            return new AzureBlobCloudStorageClient(containerClient, prefix);
        }
    }
}
