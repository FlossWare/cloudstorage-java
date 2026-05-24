package org.flossware.cloud.storage;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Google Drive implementation of CloudStorageClient.
 * Supports service account and OAuth credentials authentication.
 * Requires the Google Drive API SDK dependency.
 */
public class GoogleDriveCloudStorageClient implements CloudStorageClient {
    private final Drive driveService;
    private final String folderId;
    private final Map<String, String> pathToFileIdCache;

    private GoogleDriveCloudStorageClient(Drive driveService, String folderId) {
        this.driveService = Objects.requireNonNull(driveService, "driveService cannot be null");
        this.folderId = folderId;
        this.pathToFileIdCache = new HashMap<>();
    }

    @Override
    public byte[] readFile(String path) throws IOException {
        String fileId = findFileId(path);

        if (fileId == null) {
            throw new IOException("File not found in Google Drive: " + path);
        }

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            driveService.files().get(fileId).executeMediaAndDownloadTo(out);
            return out.toByteArray();
        }
    }

    @Override
    public InputStream openFile(String path) throws IOException {
        String fileId = findFileId(path);

        if (fileId == null) {
            throw new IOException("File not found in Google Drive: " + path);
        }

        return driveService.files().get(fileId).executeMediaAsInputStream();
    }

    @Override
    public boolean exists(String path) throws IOException {
        try {
            return findFileId(path) != null;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public List<String> list(String prefix) throws IOException {
        String query = "trashed=false";

        if (folderId != null) {
            query += " and '" + folderId + "' in parents";
        }

        FileList result = driveService.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("files(id, name)")
            .execute();

        List<String> files = new ArrayList<>();
        if (result.getFiles() != null) {
            for (File file : result.getFiles()) {
                String name = file.getName();
                if (prefix.isEmpty() || name.startsWith(prefix)) {
                    files.add(name);
                    pathToFileIdCache.put(name, file.getId());
                }
            }
        }

        return files;
    }

    @Override
    public long getFileSize(String path) throws IOException {
        String fileId = findFileId(path);

        if (fileId == null) {
            throw new IOException("File not found in Google Drive: " + path);
        }

        File file = driveService.files().get(fileId)
            .setFields("size")
            .execute();

        return file.getSize() != null ? file.getSize() : 0L;
    }

    @Override
    public String getDescription() {
        return "GoogleDriveCloudStorageClient[folder=" + folderId + "]";
    }

    @Override
    public void close() {
        // Google Drive SDK doesn't require explicit cleanup
    }

    private String findFileId(String path) throws IOException {
        if (pathToFileIdCache.containsKey(path)) {
            return pathToFileIdCache.get(path);
        }

        String fileName = path.substring(path.lastIndexOf('/') + 1);
        String query = "name='" + fileName + "'";

        if (folderId != null) {
            query += " and '" + folderId + "' in parents";
        }

        query += " and trashed=false";

        FileList result = driveService.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("files(id, name)")
            .execute();

        if (result.getFiles() != null && !result.getFiles().isEmpty()) {
            File file = result.getFiles().get(0);
            pathToFileIdCache.put(path, file.getId());
            return file.getId();
        }

        return null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private GoogleCredentials credentials;
        private String folderId;
        private String applicationName = "CloudStorageClient";

        public Builder credentials(GoogleCredentials credentials) {
            this.credentials = credentials;
            return this;
        }

        public Builder credentialsFromStream(InputStream credentialsStream) throws IOException {
            this.credentials = GoogleCredentials.fromStream(credentialsStream)
                .createScoped(Collections.singletonList("https://www.googleapis.com/auth/drive.readonly"));
            return this;
        }

        public Builder folderId(String folderId) {
            this.folderId = folderId;
            return this;
        }

        public Builder applicationName(String applicationName) {
            this.applicationName = applicationName;
            return this;
        }

        public GoogleDriveCloudStorageClient build() throws IOException, GeneralSecurityException {
            if (credentials == null) {
                credentials = GoogleCredentials.getApplicationDefault()
                    .createScoped(Collections.singletonList("https://www.googleapis.com/auth/drive.readonly"));
            }

            HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            JsonFactory jsonFactory = GsonFactory.getDefaultInstance();

            Drive driveService = new Drive.Builder(
                httpTransport,
                jsonFactory,
                new HttpCredentialsAdapter(credentials))
                .setApplicationName(applicationName)
                .build();

            return new GoogleDriveCloudStorageClient(driveService, folderId);
        }
    }
}
