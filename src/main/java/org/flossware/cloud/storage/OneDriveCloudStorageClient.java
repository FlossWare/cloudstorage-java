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
    static final String GRAPH_API_BASE = "https://graph.microsoft.com/v1.0";
    private final String accessToken;
    private final String basePath;
    private final String driveId;

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
            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();
            return responseCode == HttpURLConnection.HTTP_OK;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public List<String> list(String prefix) throws IOException {
        String folderPath = buildFilePath(prefix);
        String driveBase = driveId != null
            ? GRAPH_API_BASE + "/me/drives/" + driveId
            : GRAPH_API_BASE + "/me/drive";

        String listUrl;
        if (folderPath.isEmpty()) {
            listUrl = driveBase + "/root/children?$select=name,file";
        } else {
            String encodedPath = encodePath(folderPath);
            listUrl = driveBase + "/root:/" + encodedPath + ":/children?$select=name,file";
        }

        List<String> files = new ArrayList<>();

        while (listUrl != null) {
            HttpURLConnection connection = (HttpURLConnection) new URL(listUrl).openConnection();
            connection.setRequestProperty("Authorization", "Bearer " + accessToken);
            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP error code: " + responseCode + " listing OneDrive folder: " + prefix);
            }

            String json = readResponseBody(connection);

            for (String name : extractFileNames(json)) {
                files.add(name);
            }

            listUrl = extractNextLink(json);
        }

        return files;
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

        String json = readResponseBody(connection);
        return extractSize(json);
    }

    @Override
    public String getDescription() {
        return "OneDriveCloudStorageClient[basePath=" + basePath +
               ", drive=" + (driveId != null ? driveId : "default") + "]";
    }

    @Override
    public void close() {
    }

    String buildFilePath(String path) {
        if (basePath.isEmpty()) {
            return path;
        }

        String normalizedBase = basePath.endsWith("/") ?
            basePath.substring(0, basePath.length() - 1) : basePath;

        return normalizedBase + "/" + path;
    }

    String buildDownloadUrl(String path) throws IOException {
        String filePath = buildFilePath(path);
        String encodedPath = encodePath(filePath);

        if (driveId != null) {
            return GRAPH_API_BASE + "/me/drives/" + driveId + "/root:/" + encodedPath + ":/content";
        } else {
            return GRAPH_API_BASE + "/me/drive/root:/" + encodedPath + ":/content";
        }
    }

    String buildMetadataUrl(String path) throws IOException {
        String filePath = buildFilePath(path);
        String encodedPath = encodePath(filePath);

        if (driveId != null) {
            return GRAPH_API_BASE + "/me/drives/" + driveId + "/root:/" + encodedPath;
        } else {
            return GRAPH_API_BASE + "/me/drive/root:/" + encodedPath;
        }
    }

    private String encodePath(String path) throws IOException {
        return URLEncoder.encode(path, StandardCharsets.UTF_8.name())
            .replace("+", "%20");
    }

    private String readResponseBody(HttpURLConnection connection) throws IOException {
        try (InputStream in = connection.getInputStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    long extractSize(String json) {
        int sizeIdx = json.indexOf("\"size\"");
        if (sizeIdx < 0) {
            return 0L;
        }
        int colonIdx = json.indexOf(':', sizeIdx);
        if (colonIdx < 0) {
            return 0L;
        }
        int start = colonIdx + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        if (start == end) {
            return 0L;
        }
        try {
            return Long.parseLong(json.substring(start, end));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    List<String> extractFileNames(String json) {
        List<String> names = new ArrayList<>();
        int searchFrom = 0;
        while (true) {
            int fileIdx = json.indexOf("\"file\"", searchFrom);
            if (fileIdx < 0) {
                break;
            }
            int nameIdx = json.lastIndexOf("\"name\"", fileIdx);
            if (nameIdx < 0 || nameIdx < searchFrom) {
                searchFrom = fileIdx + 6;
                continue;
            }
            String name = extractStringValue(json, nameIdx);
            if (name != null) {
                names.add(name);
            }
            searchFrom = fileIdx + 6;
        }
        return names;
    }

    String extractNextLink(String json) {
        int idx = json.indexOf("\"@odata.nextLink\"");
        if (idx < 0) {
            return null;
        }
        return extractStringValue(json, idx);
    }

    private String extractStringValue(String json, int keyStart) {
        int colonIdx = json.indexOf(':', keyStart);
        if (colonIdx < 0) {
            return null;
        }
        int quoteStart = json.indexOf('"', colonIdx + 1);
        if (quoteStart < 0) {
            return null;
        }
        int quoteEnd = json.indexOf('"', quoteStart + 1);
        if (quoteEnd < 0) {
            return null;
        }
        return json.substring(quoteStart + 1, quoteEnd);
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
