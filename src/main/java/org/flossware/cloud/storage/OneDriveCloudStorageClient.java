package org.flossware.cloud.storage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * OneDrive implementation of CloudStorageClient using Microsoft Graph REST API.
 * Requires an OAuth access token with Files.Read.All permissions.
 */
public class OneDriveCloudStorageClient implements CloudStorageClient {
    private static final String GRAPH_API_BASE = "https://graph.microsoft.com/v1.0";
    private final String accessToken;
    private final String basePath;
    private final String driveId;  // null for default drive

    private OneDriveCloudStorageClient(String accessToken, String basePath, String driveId) {
        this.accessToken = Objects.requireNonNull(accessToken, "accessToken cannot be null");
        this.basePath = basePath != null ? basePath : "";
        this.driveId = driveId;
    }

    @Override
    public byte[] readFile(String path) throws IOException {
        String downloadUrl = buildDownloadUrl(path);

        HttpURLConnection connection = (HttpURLConnection) new URL(downloadUrl).openConnection();
        connection.setRequestProperty("Authorization", "Bearer " + accessToken);
        connection.setRequestMethod("GET");

        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("HTTP error code: " + responseCode + " for OneDrive file: " + path);
        }

        try (InputStream in = connection.getInputStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }

            return out.toByteArray();
        }
    }

    @Override
    public InputStream openFile(String path) throws IOException {
        String downloadUrl = buildDownloadUrl(path);

        HttpURLConnection connection = (HttpURLConnection) new URL(downloadUrl).openConnection();
        connection.setRequestProperty("Authorization", "Bearer " + accessToken);
        connection.setRequestMethod("GET");

        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("HTTP error code: " + responseCode + " for OneDrive file: " + path);
        }

        return connection.getInputStream();
    }

    @Override
    public boolean exists(String path) throws IOException {
        try {
            String metadataUrl = buildMetadataUrl(path);

            HttpURLConnection connection = (HttpURLConnection) new URL(metadataUrl).openConnection();
            connection.setRequestProperty("Authorization", "Bearer " + accessToken);
            connection.setRequestMethod("HEAD");

            int responseCode = connection.getResponseCode();
            return responseCode == HttpURLConnection.HTTP_OK;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public List<String> list(String prefix) throws IOException {
        // OneDrive listing requires Microsoft Graph SDK for proper implementation
        // This is a simplified version
        throw new UnsupportedOperationException("List operation requires Microsoft Graph SDK");
    }

    @Override
    public long getFileSize(String path) throws IOException {
        String metadataUrl = buildMetadataUrl(path);

        HttpURLConnection connection = (HttpURLConnection) new URL(metadataUrl).openConnection();
        connection.setRequestProperty("Authorization", "Bearer " + accessToken);
        connection.setRequestMethod("GET");

        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("HTTP error code: " + responseCode + " for OneDrive file: " + path);
        }

        String contentLength = connection.getHeaderField("Content-Length");
        return contentLength != null ? Long.parseLong(contentLength) : 0L;
    }

    @Override
    public String getDescription() {
        return "OneDriveCloudStorageClient[basePath=" + basePath +
               ", drive=" + (driveId != null ? driveId : "default") + "]";
    }

    @Override
    public void close() {
        // No cleanup required
    }

    private String buildFilePath(String path) {
        if (basePath.isEmpty()) {
            return path;
        }

        String normalizedBase = basePath.endsWith("/") ?
            basePath.substring(0, basePath.length() - 1) : basePath;

        return normalizedBase + "/" + path;
    }

    private String buildDownloadUrl(String path) throws IOException {
        String filePath = buildFilePath(path);
        String encodedPath = URLEncoder.encode(filePath, StandardCharsets.UTF_8.name());

        if (driveId != null) {
            return GRAPH_API_BASE + "/me/drives/" + driveId + "/root:/" + encodedPath + ":/content";
        } else {
            return GRAPH_API_BASE + "/me/drive/root:/" + encodedPath + ":/content";
        }
    }

    private String buildMetadataUrl(String path) throws IOException {
        String filePath = buildFilePath(path);
        String encodedPath = URLEncoder.encode(filePath, StandardCharsets.UTF_8.name());

        if (driveId != null) {
            return GRAPH_API_BASE + "/me/drives/" + driveId + "/root:/" + encodedPath;
        } else {
            return GRAPH_API_BASE + "/me/drive/root:/" + encodedPath;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String accessToken;
        private String basePath;
        private String driveId;

        public Builder accessToken(String accessToken) {
            this.accessToken = accessToken;
            return this;
        }

        public Builder basePath(String basePath) {
            this.basePath = basePath;
            return this;
        }

        public Builder driveId(String driveId) {
            this.driveId = driveId;
            return this;
        }

        public OneDriveCloudStorageClient build() {
            Objects.requireNonNull(accessToken, "accessToken must be set");
            return new OneDriveCloudStorageClient(accessToken, basePath, driveId);
        }
    }
}
