package org.flossware.cloud.storage;

import com.google.api.gax.paging.Page;
import com.google.cloud.ReadChannel;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.Storage.BlobListOption;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for GcsCloudStorageClient to achieve 100% coverage.
 */
class GcsCloudStorageClientTest {

    @Mock
    private Storage storage;

    @Mock
    private Blob blob;

    @Mock
    private ReadChannel readChannel;

    @Mock
    private Page<Blob> blobPage;

    private AutoCloseable mocks;
    private GcsCloudStorageClient client;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (client != null) {
            client.close();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    @DisplayName("Should support builder chaining")
    void testBuilderChaining() {
        GcsCloudStorageClient.Builder builder = GcsCloudStorageClient.builder();
        assertSame(builder, builder.projectId("test-project"));
        assertSame(builder, builder.bucket("test-bucket"));
        assertSame(builder, builder.prefix("test-prefix"));
        assertSame(builder, builder.storage(storage));
    }

    @Test
    @DisplayName("Should throw NullPointerException when bucket is null")
    void testBuilderNullBucket() {
        assertThrows(NullPointerException.class,
            () -> GcsCloudStorageClient.builder().build());
    }

    @Test
    @DisplayName("Should read file successfully")
    void testReadFileSuccess() throws Exception {
        client = createTestClient("");

        byte[] expectedData = "test-content".getBytes();
        when(storage.get((BlobId) any())).thenReturn(blob);
        when(blob.getContent()).thenReturn(expectedData);

        byte[] result = client.readFile("path/to/file.txt");
        assertArrayEquals(expectedData, result);
    }

    @Test
    @DisplayName("Should throw IOException when file not found")
    void testReadFileNotFound() throws Exception {
        client = createTestClient("");
        when(storage.get((BlobId) any())).thenReturn(null);

        IOException exception = assertThrows(IOException.class,
            () -> client.readFile("missing.txt"));
        assertTrue(exception.getMessage().contains("File not found in GCS"));
    }

    @Test
    @DisplayName("Should throw IOException on read failure")
    void testReadFileFailure() throws Exception {
        client = createTestClient("");
        when(storage.get((BlobId) any())).thenReturn(blob);
        when(blob.getContent()).thenThrow(new RuntimeException("GCS error"));

        IOException exception = assertThrows(IOException.class,
            () -> client.readFile("file.txt"));
        assertTrue(exception.getMessage().contains("Failed to read file from GCS"));
    }

    @Test
    @DisplayName("Should open file successfully")
    void testOpenFileSuccess() throws Exception {
        client = createTestClient("");
        when(storage.get((BlobId) any())).thenReturn(blob);
        when(blob.reader()).thenReturn(readChannel);

        InputStream result = client.openFile("file.txt");
        assertNotNull(result);
        verify(blob).reader();
    }

    @Test
    @DisplayName("Should throw IOException when opening non-existent file")
    void testOpenFileNotFound() throws Exception {
        client = createTestClient("");
        when(storage.get((BlobId) any())).thenReturn(null);

        IOException exception = assertThrows(IOException.class,
            () -> client.openFile("missing.txt"));
        assertTrue(exception.getMessage().contains("File not found in GCS"));
    }

    @Test
    @DisplayName("Should check exists returns true")
    void testExistsTrue() throws Exception {
        client = createTestClient("");
        when(storage.get((BlobId) any())).thenReturn(blob);
        when(blob.exists()).thenReturn(true);

        assertTrue(client.exists("file.txt"));
    }

    @Test
    @DisplayName("Should check exists returns false when blob is null")
    void testExistsFalseNullBlob() throws Exception {
        client = createTestClient("");
        when(storage.get((BlobId) any())).thenReturn(null);

        assertFalse(client.exists("missing.txt"));
    }

    @Test
    @DisplayName("Should check exists returns false when blob.exists() is false")
    void testExistsFalseBlobNotExists() throws Exception {
        client = createTestClient("");
        when(storage.get((BlobId) any())).thenReturn(blob);
        when(blob.exists()).thenReturn(false);

        assertFalse(client.exists("missing.txt"));
    }

    @Test
    @DisplayName("Should throw IOException on exists check failure")
    void testExistsFailure() throws Exception {
        client = createTestClient("");
        when(storage.get((BlobId) any())).thenThrow(new RuntimeException("GCS error"));

        IOException exception = assertThrows(IOException.class,
            () -> client.exists("file.txt"));
        assertTrue(exception.getMessage().contains("Failed to check file existence in GCS"));
    }

    @Test
    @DisplayName("Should list files successfully")
    void testListSuccess() throws Exception {
        client = createTestClient("");

        Blob blob1 = mock(Blob.class);
        Blob blob2 = mock(Blob.class);
        when(blob1.getName()).thenReturn("file1.txt");
        when(blob2.getName()).thenReturn("file2.txt");

        when(blobPage.iterateAll()).thenReturn(Arrays.asList(blob1, blob2));
        when(storage.list(eq("test-bucket"), any(BlobListOption.class))).thenReturn(blobPage);

        List<String> files = client.list("prefix");
        assertEquals(2, files.size());
    }

    @Test
    @DisplayName("Should throw IOException on list failure")
    void testListFailure() throws Exception {
        client = createTestClient("");
        when(storage.list(eq("test-bucket"), any(BlobListOption.class)))
            .thenThrow(new RuntimeException("GCS error"));

        IOException exception = assertThrows(IOException.class,
            () -> client.list("prefix"));
        assertTrue(exception.getMessage().contains("Failed to list files in GCS"));
    }

    @Test
    @DisplayName("Should get file size successfully")
    void testGetFileSizeSuccess() throws Exception {
        client = createTestClient("");
        when(storage.get((BlobId) any())).thenReturn(blob);
        when(blob.getSize()).thenReturn(12345L);

        long size = client.getFileSize("file.txt");
        assertEquals(12345L, size);
    }

    @Test
    @DisplayName("Should throw IOException when file not found for size")
    void testGetFileSizeNotFound() throws Exception {
        client = createTestClient("");
        when(storage.get((BlobId) any())).thenReturn(null);

        IOException exception = assertThrows(IOException.class,
            () -> client.getFileSize("missing.txt"));
        assertTrue(exception.getMessage().contains("File not found in GCS"));
    }

    @Test
    @DisplayName("Should return description")
    void testGetDescription() throws Exception {
        client = createTestClient("my-prefix");
        assertTrue(client.getDescription().contains("test-bucket"));
        assertTrue(client.getDescription().contains("my-prefix"));
    }

    @Test
    @DisplayName("Should close without error")
    void testClose() throws Exception {
        client = createTestClient("");
        assertDoesNotThrow(() -> client.close());
    }

    @Test
    @DisplayName("Should throw NullPointerException when storage is null")
    void testConstructorNullStorage() throws Exception {
        java.lang.reflect.Constructor<GcsCloudStorageClient> constructor =
            GcsCloudStorageClient.class.getDeclaredConstructor(Storage.class, String.class, String.class);
        constructor.setAccessible(true);

        java.lang.reflect.InvocationTargetException exception = assertThrows(
            java.lang.reflect.InvocationTargetException.class,
            () -> constructor.newInstance(null, "bucket", "prefix"));

        assertTrue(exception.getCause() instanceof NullPointerException);
    }

    @Test
    @DisplayName("Should throw NullPointerException when bucketName is null")
    void testConstructorNullBucketName() throws Exception {
        java.lang.reflect.Constructor<GcsCloudStorageClient> constructor =
            GcsCloudStorageClient.class.getDeclaredConstructor(Storage.class, String.class, String.class);
        constructor.setAccessible(true);

        java.lang.reflect.InvocationTargetException exception = assertThrows(
            java.lang.reflect.InvocationTargetException.class,
            () -> constructor.newInstance(storage, null, "prefix"));

        assertTrue(exception.getCause() instanceof NullPointerException);
    }

    @Test
    @DisplayName("Should handle null prefix")
    void testConstructorNullPrefix() throws Exception {
        java.lang.reflect.Constructor<GcsCloudStorageClient> constructor =
            GcsCloudStorageClient.class.getDeclaredConstructor(Storage.class, String.class, String.class);
        constructor.setAccessible(true);

        GcsCloudStorageClient testClient = constructor.newInstance(storage, "bucket", null);
        assertTrue(testClient.getDescription().contains("prefix=]"));
    }

    private GcsCloudStorageClient createTestClient(String prefix) throws Exception {
        java.lang.reflect.Constructor<GcsCloudStorageClient> constructor =
            GcsCloudStorageClient.class.getDeclaredConstructor(Storage.class, String.class, String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(storage, "test-bucket", prefix);
    }
}
