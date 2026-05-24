package org.flossware.cloud.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Generic interface for cloud storage operations.
 * Provides a unified API for reading files from various cloud storage providers
 * (AWS S3, Azure Blob Storage, Google Cloud Storage, Google Drive, Dropbox, OneDrive).
 *
 * <p>Implementations handle provider-specific authentication, API calls, and error handling,
 * presenting a simple file-like interface to clients.</p>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // AWS S3
 * CloudStorageClient s3 = S3CloudStorageClient.builder()
 *     .bucket("my-bucket")
 *     .region(Region.US_EAST_1)
 *     .build();
 *
 * // Read a file
 * byte[] data = s3.readFile("path/to/file.dat");
 *
 * // Check if file exists
 * if (s3.exists("path/to/file.dat")) {
 *     // ...
 * }
 *
 * // List files with prefix
 * List<String> files = s3.list("path/to/directory/");
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>Implementations should be thread-safe for concurrent read operations.</p>
 *
 * @see S3CloudStorageClient
 * @see AzureBlobCloudStorageClient
 * @see GcsCloudStorageClient
 */
public interface CloudStorageClient extends AutoCloseable {
    /**
     * Reads the entire contents of a file from cloud storage.
     *
     * @param path The path to the file (provider-specific format)
     * @return The file contents as a byte array
     * @throws IOException if the file does not exist or cannot be read
     */
    byte[] readFile(String path) throws IOException;

    /**
     * Opens an input stream to read a file from cloud storage.
     * The caller is responsible for closing the stream.
     *
     * @param path The path to the file (provider-specific format)
     * @return An input stream for reading the file
     * @throws IOException if the file does not exist or cannot be opened
     */
    InputStream openFile(String path) throws IOException;

    /**
     * Checks if a file exists in cloud storage.
     *
     * @param path The path to the file (provider-specific format)
     * @return true if the file exists, false otherwise
     * @throws IOException if the existence check fails due to a service error
     */
    boolean exists(String path) throws IOException;

    /**
     * Lists all files with the specified prefix.
     *
     * @param prefix The prefix to filter files (e.g., "folder/" or "folder/subfolder/")
     * @return A list of file paths matching the prefix
     * @throws IOException if the listing operation fails
     */
    List<String> list(String prefix) throws IOException;

    /**
     * Gets the size of a file in bytes.
     *
     * @param path The path to the file
     * @return The file size in bytes
     * @throws IOException if the file does not exist or size cannot be determined
     */
    long getFileSize(String path) throws IOException;

    /**
     * Gets a description of this cloud storage client (provider, bucket/container, etc.).
     *
     * @return A human-readable description
     */
    String getDescription();

    /**
     * Closes this cloud storage client and releases any resources.
     * After closing, no further operations should be performed.
     *
     * @throws IOException if an error occurs during cleanup
     */
    @Override
    void close() throws IOException;
}
