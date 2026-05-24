package org.flossware.cloud.storage;

import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.Metadata;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Dropbox implementation of CloudStorageClient.
 * Supports OAuth access token authentication.
 * Requires the Dropbox Core SDK dependency.
 */
public class DropboxCloudStorageClient implements CloudStorageClient {
    private final DbxClientV2 client;
    private final String basePath;

    private DropboxCloudStorageClient(DbxClientV2 client, String basePath) {
        this.client = Objects.requireNonNull(client, "client cannot be null");
        this.basePath = basePath != null ? basePath : "";
    }

    @Override
    public byte[] readFile(String path) throws IOException {
        String filePath = buildFilePath(path);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            client.files().download(filePath).download(out);
            return out.toByteArray();
        } catch (DbxException e) {
            throw new IOException("Failed to read file from Dropbox: " + filePath, e);
        }
    }

    @Override
    public InputStream openFile(String path) throws IOException {
        String filePath = buildFilePath(path);

        try {
            return client.files().download(filePath).getInputStream();
        } catch (DbxException e) {
            throw new IOException("Failed to open file from Dropbox: " + filePath, e);
        }
    }

    @Override
    public boolean exists(String path) throws IOException {
        String filePath = buildFilePath(path);

        try {
            Metadata metadata = client.files().getMetadata(filePath);
            return metadata instanceof FileMetadata;
        } catch (DbxException e) {
            return false;
        }
    }

    @Override
    public List<String> list(String prefix) throws IOException {
        String folderPath = buildFilePath(prefix);
        // Dropbox requires empty string for root folder
        if (folderPath.equals("/")) {
            folderPath = "";
        }

        List<String> files = new ArrayList<>();

        try {
            ListFolderResult result = client.files().listFolder(folderPath);
            while (true) {
                for (Metadata metadata : result.getEntries()) {
                    if (metadata instanceof FileMetadata) {
                        String name = removePrefix(metadata.getPathLower());
                        files.add(name);
                    }
                }

                if (!result.getHasMore()) {
                    break;
                }

                result = client.files().listFolderContinue(result.getCursor());
            }
            return files;
        } catch (DbxException e) {
            throw new IOException("Failed to list files in Dropbox: " + folderPath, e);
        }
    }

    @Override
    public long getFileSize(String path) throws IOException {
        String filePath = buildFilePath(path);

        try {
            Metadata metadata = client.files().getMetadata(filePath);
            if (metadata instanceof FileMetadata) {
                return ((FileMetadata) metadata).getSize();
            }
            throw new IOException("Not a file: " + filePath);
        } catch (DbxException e) {
            throw new IOException("Failed to get file size from Dropbox: " + filePath, e);
        }
    }

    @Override
    public String getDescription() {
        return "DropboxCloudStorageClient[basePath=" + basePath + "]";
    }

    @Override
    public void close() {
        // Dropbox SDK doesn't require explicit cleanup
    }

    private String buildFilePath(String path) {
        if (basePath.isEmpty()) {
            return "/" + path;
        }

        String normalizedBase = basePath.startsWith("/") ? basePath : "/" + basePath;
        normalizedBase = normalizedBase.endsWith("/") ? normalizedBase.substring(0, normalizedBase.length() - 1) : normalizedBase;

        return normalizedBase + "/" + path;
    }

    private String removePrefix(String filePath) {
        if (basePath.isEmpty()) {
            return filePath.startsWith("/") ? filePath.substring(1) : filePath;
        }

        String normalizedBase = basePath.startsWith("/") ? basePath : "/" + basePath;
        normalizedBase = normalizedBase.endsWith("/") ? normalizedBase.substring(0, normalizedBase.length() - 1) : normalizedBase;

        if (filePath.startsWith(normalizedBase + "/")) {
            return filePath.substring(normalizedBase.length() + 1);
        }
        return filePath;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String accessToken;
        private String basePath;
        private String clientIdentifier = "CloudStorageClient";

        public Builder accessToken(String accessToken) {
            this.accessToken = accessToken;
            return this;
        }

        public Builder basePath(String basePath) {
            this.basePath = basePath;
            return this;
        }

        public Builder clientIdentifier(String clientIdentifier) {
            this.clientIdentifier = clientIdentifier;
            return this;
        }

        public DropboxCloudStorageClient build() {
            Objects.requireNonNull(accessToken, "accessToken must be set");

            DbxRequestConfig config = DbxRequestConfig.newBuilder(clientIdentifier).build();
            DbxClientV2 client = new DbxClientV2(config, accessToken);

            return new DropboxCloudStorageClient(client, basePath);
        }
    }
}
