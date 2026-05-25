package org.flossware.cloud.storage;

import com.azure.core.http.rest.PagedIterable;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobItemProperties;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.specialized.BlobInputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for AzureBlobCloudStorageClient to achieve 100% coverage.
 */
class AzureBlobCloudStorageClientTest {

    @Mock
    private BlobContainerClient containerClient;

    @Mock
    private BlobClient blobClient;

    @Mock
    private BlobProperties blobProperties;

    @Mock
    private BlobItem blobItem;

    private AutoCloseable mocks;
    private AzureBlobCloudStorageClient client;

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
        AzureBlobCloudStorageClient.Builder builder = AzureBlobCloudStorageClient.builder();
        assertSame(builder, builder.accountName("account"));
        assertSame(builder, builder.accountKey("key"));
        assertSame(builder, builder.connectionString("conn-string"));
        assertSame(builder, builder.container("container"));
        assertSame(builder, builder.prefix("prefix"));
        assertSame(builder, builder.endpoint("https://endpoint.com"));
    }

    @Test
    @DisplayName("Should read file successfully")
    void testReadFileSuccess() throws Exception {
        client = createTestClient("");

        byte[] expectedData = "test-content".getBytes();

        when(containerClient.getBlobClient(anyString())).thenReturn(blobClient);
        doAnswer(invocation -> {
            ByteArrayOutputStream out = invocation.getArgument(0);
            out.write(expectedData);
            return null;
        }).when(blobClient).downloadStream(any(ByteArrayOutputStream.class));

        byte[] result = client.readFile("path/to/file.txt");

        assertArrayEquals(expectedData, result);
        verify(containerClient).getBlobClient("path/to/file.txt");
    }

    @Test
    @DisplayName("Should read file with prefix")
    void testReadFileWithPrefix() throws Exception {
        client = createTestClient("my-prefix");

        byte[] expectedData = "data".getBytes();

        when(containerClient.getBlobClient(anyString())).thenReturn(blobClient);
        doAnswer(invocation -> {
            ByteArrayOutputStream out = invocation.getArgument(0);
            out.write(expectedData);
            return null;
        }).when(blobClient).downloadStream(any(ByteArrayOutputStream.class));

        byte[] result = client.readFile("file.txt");

        assertArrayEquals(expectedData, result);
        verify(containerClient).getBlobClient("my-prefix/file.txt");
    }

    @Test
    @DisplayName("Should throw IOException on read failure")
    void testReadFileFailure() throws Exception {
        client = createTestClient("");

        when(containerClient.getBlobClient(anyString())).thenReturn(blobClient);
        doThrow(new RuntimeException("Azure error")).when(blobClient).downloadStream(any(ByteArrayOutputStream.class));

        IOException exception = assertThrows(IOException.class,
            () -> client.readFile("file.txt"));

        assertTrue(exception.getMessage().contains("Failed to read file from Azure Blob"));
    }

    @Test
    @DisplayName("Should open file successfully")
    void testOpenFileSuccess() throws Exception {
        client = createTestClient("");

        BlobInputStream mockStream = mock(BlobInputStream.class);

        when(containerClient.getBlobClient(anyString())).thenReturn(blobClient);
        when(blobClient.openInputStream()).thenReturn(mockStream);

        InputStream result = client.openFile("file.txt");

        assertSame(mockStream, result);
    }

    @Test
    @DisplayName("Should throw IOException on open file failure")
    void testOpenFileFailure() throws Exception {
        client = createTestClient("");

        when(containerClient.getBlobClient(anyString())).thenReturn(blobClient);
        when(blobClient.openInputStream()).thenThrow(new RuntimeException("Azure error"));

        IOException exception = assertThrows(IOException.class,
            () -> client.openFile("file.txt"));

        assertTrue(exception.getMessage().contains("Failed to open file from Azure Blob"));
    }

    @Test
    @DisplayName("Should check exists returns true")
    void testExistsTrue() throws Exception {
        client = createTestClient("");

        when(containerClient.getBlobClient(anyString())).thenReturn(blobClient);
        when(blobClient.exists()).thenReturn(true);

        assertTrue(client.exists("file.txt"));
    }

    @Test
    @DisplayName("Should check exists returns false")
    void testExistsFalse() throws Exception {
        client = createTestClient("");

        when(containerClient.getBlobClient(anyString())).thenReturn(blobClient);
        when(blobClient.exists()).thenReturn(false);

        assertFalse(client.exists("missing.txt"));
    }

    @Test
    @DisplayName("Should throw IOException on exists failure")
    void testExistsFailure() throws Exception {
        client = createTestClient("");

        when(containerClient.getBlobClient(anyString())).thenReturn(blobClient);
        when(blobClient.exists()).thenThrow(new RuntimeException("Azure error"));

        IOException exception = assertThrows(IOException.class,
            () -> client.exists("file.txt"));

        assertTrue(exception.getMessage().contains("Failed to check file existence in Azure Blob"));
    }

    @Test
    @DisplayName("Should list files successfully")
    void testListSuccess() throws Exception {
        client = createTestClient("");

        BlobItem item1 = mock(BlobItem.class);
        BlobItem item2 = mock(BlobItem.class);
        when(item1.getName()).thenReturn("file1.txt");
        when(item2.getName()).thenReturn("file2.txt");

        @SuppressWarnings("unchecked")
        PagedIterable<BlobItem> pagedIterable = mock(PagedIterable.class);
        when(pagedIterable.iterator()).thenReturn(Arrays.asList(item1, item2).iterator());

        when(containerClient.listBlobsByHierarchy(anyString())).thenReturn(pagedIterable);

        List<String> files = client.list("prefix");

        assertEquals(2, files.size());
        assertTrue(files.contains("file1.txt"));
        assertTrue(files.contains("file2.txt"));
    }

    @Test
    @DisplayName("Should list files with prefix removal")
    void testListWithPrefixRemoval() throws Exception {
        client = createTestClient("base-prefix");

        BlobItem item1 = mock(BlobItem.class);
        when(item1.getName()).thenReturn("base-prefix/file1.txt");

        @SuppressWarnings("unchecked")
        PagedIterable<BlobItem> pagedIterable = mock(PagedIterable.class);
        when(pagedIterable.iterator()).thenReturn(Arrays.asList(item1).iterator());

        when(containerClient.listBlobsByHierarchy(anyString())).thenReturn(pagedIterable);

        List<String> files = client.list("");

        assertEquals(1, files.size());
        assertTrue(files.contains("file1.txt"));
    }

    @Test
    @DisplayName("Should throw IOException on list failure")
    void testListFailure() throws Exception {
        client = createTestClient("");

        when(containerClient.listBlobsByHierarchy(anyString()))
            .thenThrow(new RuntimeException("Azure error"));

        IOException exception = assertThrows(IOException.class,
            () -> client.list("prefix"));

        assertTrue(exception.getMessage().contains("Failed to list files in Azure Blob"));
    }

    @Test
    @DisplayName("Should get file size successfully")
    void testGetFileSizeSuccess() throws Exception {
        client = createTestClient("");

        when(containerClient.getBlobClient(anyString())).thenReturn(blobClient);
        when(blobClient.getProperties()).thenReturn(blobProperties);
        when(blobProperties.getBlobSize()).thenReturn(12345L);

        long size = client.getFileSize("file.txt");

        assertEquals(12345L, size);
    }

    @Test
    @DisplayName("Should throw IOException on getFileSize failure")
    void testGetFileSizeFailure() throws Exception {
        client = createTestClient("");

        when(containerClient.getBlobClient(anyString())).thenReturn(blobClient);
        when(blobClient.getProperties()).thenThrow(new RuntimeException("Azure error"));

        IOException exception = assertThrows(IOException.class,
            () -> client.getFileSize("file.txt"));

        assertTrue(exception.getMessage().contains("Failed to get file size from Azure Blob"));
    }

    @Test
    @DisplayName("Should return description")
    void testGetDescription() throws Exception {
        client = createTestClient("my-prefix");

        when(containerClient.getBlobContainerName()).thenReturn("test-container");

        String description = client.getDescription();

        assertTrue(description.contains("test-container"));
        assertTrue(description.contains("my-prefix"));
    }

    @Test
    @DisplayName("Should close without error")
    void testClose() throws Exception {
        client = createTestClient("");
        assertDoesNotThrow(() -> client.close());
    }

    @Test
    @DisplayName("Should throw NullPointerException when containerClient is null")
    void testConstructorNullContainerClient() throws Exception {
        java.lang.reflect.Constructor<AzureBlobCloudStorageClient> constructor =
            AzureBlobCloudStorageClient.class.getDeclaredConstructor(BlobContainerClient.class, String.class);
        constructor.setAccessible(true);

        java.lang.reflect.InvocationTargetException exception = assertThrows(
            java.lang.reflect.InvocationTargetException.class,
            () -> constructor.newInstance(null, "prefix"));

        assertTrue(exception.getCause() instanceof NullPointerException);
        assertTrue(exception.getCause().getMessage().contains("containerClient cannot be null"));
    }

    @Test
    @DisplayName("Should handle null prefix")
    void testConstructorNullPrefix() throws Exception {
        java.lang.reflect.Constructor<AzureBlobCloudStorageClient> constructor =
            AzureBlobCloudStorageClient.class.getDeclaredConstructor(BlobContainerClient.class, String.class);
        constructor.setAccessible(true);

        when(containerClient.getBlobContainerName()).thenReturn("test-container");

        AzureBlobCloudStorageClient testClient = constructor.newInstance(containerClient, null);
        assertTrue(testClient.getDescription().contains("prefix=]"));
    }

    private AzureBlobCloudStorageClient createTestClient(String prefix) throws Exception {
        java.lang.reflect.Constructor<AzureBlobCloudStorageClient> constructor =
            AzureBlobCloudStorageClient.class.getDeclaredConstructor(BlobContainerClient.class, String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(containerClient, prefix);
    }
}
