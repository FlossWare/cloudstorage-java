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

    @Test
    @DisplayName("Should build with connection string")
    void testBuilderWithConnectionString() throws Exception {
        com.azure.storage.blob.BlobServiceClient serviceClient = mock(com.azure.storage.blob.BlobServiceClient.class);
        when(serviceClient.getBlobContainerClient(anyString())).thenReturn(containerClient);

        try (org.mockito.MockedConstruction<com.azure.storage.blob.BlobServiceClientBuilder> builderMock =
                 org.mockito.Mockito.mockConstruction(com.azure.storage.blob.BlobServiceClientBuilder.class,
                     (mock, context) -> {
                         when(mock.connectionString(anyString())).thenReturn(mock);
                         when(mock.buildClient()).thenReturn(serviceClient);
                     })) {

            AzureBlobCloudStorageClient client = AzureBlobCloudStorageClient.builder()
                .connectionString("DefaultEndpointsProtocol=https;AccountName=myaccount;AccountKey=key123;")
                .container("test-container")
                .build();

            assertNotNull(client);
        }
    }

    @Test
    @DisplayName("Should build with account name and key")
    void testBuilderWithAccountCredentials() throws Exception {
        com.azure.storage.blob.BlobServiceClient serviceClient = mock(com.azure.storage.blob.BlobServiceClient.class);
        when(serviceClient.getBlobContainerClient(anyString())).thenReturn(containerClient);

        try (org.mockito.MockedConstruction<com.azure.storage.blob.BlobServiceClientBuilder> builderMock =
                 org.mockito.Mockito.mockConstruction(com.azure.storage.blob.BlobServiceClientBuilder.class,
                     (mock, context) -> {
                         when(mock.endpoint(anyString())).thenReturn(mock);
                         when(mock.credential(any(com.azure.storage.common.StorageSharedKeyCredential.class))).thenReturn(mock);
                         when(mock.buildClient()).thenReturn(serviceClient);
                     })) {

            AzureBlobCloudStorageClient client = AzureBlobCloudStorageClient.builder()
                .accountName("myaccount")
                .accountKey("key123")
                .container("test-container")
                .build();

            assertNotNull(client);
        }
    }

    @Test
    @DisplayName("Should build with custom endpoint")
    void testBuilderWithCustomEndpoint() throws Exception {
        com.azure.storage.blob.BlobServiceClient serviceClient = mock(com.azure.storage.blob.BlobServiceClient.class);
        when(serviceClient.getBlobContainerClient(anyString())).thenReturn(containerClient);

        try (org.mockito.MockedConstruction<com.azure.storage.blob.BlobServiceClientBuilder> builderMock =
                 org.mockito.Mockito.mockConstruction(com.azure.storage.blob.BlobServiceClientBuilder.class,
                     (mock, context) -> {
                         when(mock.endpoint(anyString())).thenReturn(mock);
                         when(mock.credential(any(com.azure.storage.common.StorageSharedKeyCredential.class))).thenReturn(mock);
                         when(mock.buildClient()).thenReturn(serviceClient);
                     })) {

            AzureBlobCloudStorageClient client = AzureBlobCloudStorageClient.builder()
                .accountName("myaccount")
                .accountKey("key123")
                .endpoint("https://custom.blob.core.windows.net")
                .container("test-container")
                .build();

            assertNotNull(client);
        }
    }

    @Test
    @DisplayName("Should build with prefix")
    void testBuilderWithPrefix() throws Exception {
        com.azure.storage.blob.BlobServiceClient serviceClient = mock(com.azure.storage.blob.BlobServiceClient.class);
        when(serviceClient.getBlobContainerClient(anyString())).thenReturn(containerClient);
        when(containerClient.getBlobContainerName()).thenReturn("test-container");

        try (org.mockito.MockedConstruction<com.azure.storage.blob.BlobServiceClientBuilder> builderMock =
                 org.mockito.Mockito.mockConstruction(com.azure.storage.blob.BlobServiceClientBuilder.class,
                     (mock, context) -> {
                         when(mock.connectionString(anyString())).thenReturn(mock);
                         when(mock.buildClient()).thenReturn(serviceClient);
                     })) {

            AzureBlobCloudStorageClient client = AzureBlobCloudStorageClient.builder()
                .connectionString("DefaultEndpointsProtocol=https;AccountName=myaccount;AccountKey=key123;")
                .container("test-container")
                .prefix("my-prefix")
                .build();

            assertNotNull(client);
            assertTrue(client.getDescription().contains("my-prefix"));
        }
    }

    @Test
    @DisplayName("Should throw IllegalStateException when neither connectionString nor credentials provided")
    void testBuilderMissingCredentials() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> AzureBlobCloudStorageClient.builder()
                .container("test-container")
                .build());

        assertTrue(exception.getMessage().contains("Either connectionString or (accountName + accountKey) must be provided"));
    }

    @Test
    @DisplayName("Should throw NullPointerException when container is null")
    void testBuilderNullContainer() {
        assertThrows(NullPointerException.class,
            () -> AzureBlobCloudStorageClient.builder()
                .connectionString("connection-string")
                .build());
    }

    @Test
    @DisplayName("Should handle removePrefix when blobName doesn't match prefix")
    void testRemovePrefixNoMatch() throws Exception {
        AzureBlobCloudStorageClient testClient = createTestClient("my-prefix");

        java.lang.reflect.Method removePrefix = AzureBlobCloudStorageClient.class.getDeclaredMethod(
            "removePrefix", String.class);
        removePrefix.setAccessible(true);

        // blobName that doesn't start with the expected prefix
        String result = (String) removePrefix.invoke(testClient, "other/path/file.txt");
        assertEquals("other/path/file.txt", result);
    }

    @Test
    @DisplayName("Should handle buildBlobName with prefix ending in slash")
    void testBuildBlobNamePrefixEndingSlash() throws Exception {
        AzureBlobCloudStorageClient testClient = createTestClient("my-prefix/");

        java.lang.reflect.Method buildBlobName = AzureBlobCloudStorageClient.class.getDeclaredMethod(
            "buildBlobName", String.class);
        buildBlobName.setAccessible(true);

        String result = (String) buildBlobName.invoke(testClient, "file.txt");
        assertEquals("my-prefix/file.txt", result);
    }

    @Test
    @DisplayName("Should handle removePrefix with prefix ending in slash")
    void testRemovePrefixEndingSlash() throws Exception {
        AzureBlobCloudStorageClient testClient = createTestClient("my-prefix/");

        java.lang.reflect.Method removePrefix = AzureBlobCloudStorageClient.class.getDeclaredMethod(
            "removePrefix", String.class);
        removePrefix.setAccessible(true);

        String result = (String) removePrefix.invoke(testClient, "my-prefix/file.txt");
        assertEquals("file.txt", result);
    }

    private AzureBlobCloudStorageClient createTestClient(String prefix) throws Exception {
        java.lang.reflect.Constructor<AzureBlobCloudStorageClient> constructor =
            AzureBlobCloudStorageClient.class.getDeclaredConstructor(BlobContainerClient.class, String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(containerClient, prefix);
    }
}
