package org.flossware.cloud.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for S3CloudStorageClient to achieve 100% coverage.
 */
class S3CloudStorageClientTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private HeadObjectResponse headObjectResponse;

    private AutoCloseable mocks;
    private S3CloudStorageClient client;

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
        S3CloudStorageClient.Builder builder = S3CloudStorageClient.builder();
        assertSame(builder, builder.region(Region.US_WEST_2));
        assertSame(builder, builder.region("us-east-1"));
        assertSame(builder, builder.bucket("test-bucket"));
        assertSame(builder, builder.prefix("test-prefix"));
        assertSame(builder, builder.credentials("key", "secret"));
    }

    @Test
    @DisplayName("Should throw NullPointerException when bucket is null")
    void testBuilderNullBucket() {
        assertThrows(NullPointerException.class,
            () -> S3CloudStorageClient.builder().build());
    }

    @Test
    @DisplayName("Should read file successfully")
    void testReadFileSuccess() throws Exception {
        client = createTestClient("");

        byte[] expectedData = "test-content".getBytes();
        ResponseInputStream<GetObjectResponse> mockResponse = new ResponseInputStream<>(
            GetObjectResponse.builder().build(),
            new ByteArrayInputStream(expectedData));

        when(s3Client.getObject((GetObjectRequest) any())).thenReturn(mockResponse);

        byte[] result = client.readFile("path/to/file.txt");

        assertArrayEquals(expectedData, result);
    }

    @Test
    @DisplayName("Should read file with prefix")
    void testReadFileWithPrefix() throws Exception {
        client = createTestClient("prefix");

        byte[] expectedData = "test".getBytes();
        when(s3Client.getObject((GetObjectRequest) any()))
            .thenReturn(new ResponseInputStream<>(
                GetObjectResponse.builder().build(),
                new ByteArrayInputStream(expectedData)));

        byte[] result = client.readFile("file.txt");
        assertArrayEquals(expectedData, result);
    }

    @Test
    @DisplayName("Should throw IOException on read failure")
    void testReadFileFailure() throws Exception {
        client = createTestClient("");
        when(s3Client.getObject((GetObjectRequest) any()))
            .thenThrow(new RuntimeException("S3 error"));

        IOException exception = assertThrows(IOException.class,
            () -> client.readFile("file.txt"));
        assertTrue(exception.getMessage().contains("Failed to read file from S3"));
    }

    @Test
    @DisplayName("Should open file successfully")
    void testOpenFileSuccess() throws Exception {
        client = createTestClient("");

        ResponseInputStream<GetObjectResponse> mockStream = new ResponseInputStream<>(
            GetObjectResponse.builder().build(),
            new ByteArrayInputStream("data".getBytes()));

        when(s3Client.getObject((GetObjectRequest) any())).thenReturn(mockStream);

        InputStream result = client.openFile("file.txt");
        assertNotNull(result);
    }

    @Test
    @DisplayName("Should check exists returns true")
    void testExistsTrue() throws Exception {
        client = createTestClient("");
        when(s3Client.headObject((HeadObjectRequest) any())).thenReturn(headObjectResponse);

        assertTrue(client.exists("file.txt"));
    }

    @Test
    @DisplayName("Should check exists returns false")
    void testExistsFalse() throws Exception {
        client = createTestClient("");
        when(s3Client.headObject((HeadObjectRequest) any()))
            .thenThrow(NoSuchKeyException.builder().build());

        assertFalse(client.exists("missing.txt"));
    }

    @Test
    @DisplayName("Should throw IOException on exists failure")
    void testExistsFailure() throws Exception {
        client = createTestClient("");
        when(s3Client.headObject((HeadObjectRequest) any()))
            .thenThrow(new RuntimeException("S3 error"));

        IOException exception = assertThrows(IOException.class,
            () -> client.exists("file.txt"));
        assertTrue(exception.getMessage().contains("Failed to check file existence"));
    }

    @Test
    @DisplayName("Should list files successfully")
    void testListSuccess() throws Exception {
        client = createTestClient("");

        S3Object obj1 = S3Object.builder().key("file1.txt").build();
        S3Object obj2 = S3Object.builder().key("file2.txt").build();

        ListObjectsV2Response response = ListObjectsV2Response.builder()
            .contents(Arrays.asList(obj1, obj2))
            .build();

        when(s3Client.listObjectsV2((ListObjectsV2Request) any())).thenReturn(response);

        List<String> files = client.list("prefix");
        assertEquals(2, files.size());
    }

    @Test
    @DisplayName("Should get file size successfully")
    void testGetFileSizeSuccess() throws Exception {
        client = createTestClient("");
        when(headObjectResponse.contentLength()).thenReturn(12345L);
        when(s3Client.headObject((HeadObjectRequest) any())).thenReturn(headObjectResponse);

        long size = client.getFileSize("file.txt");
        assertEquals(12345L, size);
    }

    @Test
    @DisplayName("Should throw IOException when file not found for size")
    void testGetFileSizeNotFound() throws Exception {
        client = createTestClient("");
        when(s3Client.headObject((HeadObjectRequest) any()))
            .thenThrow(NoSuchKeyException.builder().build());

        IOException exception = assertThrows(IOException.class,
            () -> client.getFileSize("missing.txt"));
        assertTrue(exception.getMessage().contains("File not found in S3"));
    }

    @Test
    @DisplayName("Should return description")
    void testGetDescription() throws Exception {
        client = createTestClient("my-prefix");
        assertTrue(client.getDescription().contains("test-bucket"));
        assertTrue(client.getDescription().contains("my-prefix"));
    }

    @Test
    @DisplayName("Should close S3 client")
    void testClose() throws Exception {
        client = createTestClient("");
        client.close();
        verify(s3Client).close();
    }

    @Test
    @DisplayName("Should handle null S3 client in close")
    void testCloseNullClient() throws Exception {
        client = createTestClient("");
        java.lang.reflect.Field field = S3CloudStorageClient.class.getDeclaredField("s3Client");
        field.setAccessible(true);
        field.set(client, null);
        assertDoesNotThrow(() -> client.close());
    }

    @Test
    @DisplayName("Should throw NullPointerException when s3Client is null")
    void testConstructorNullS3Client() throws Exception {
        java.lang.reflect.Constructor<S3CloudStorageClient> constructor =
            S3CloudStorageClient.class.getDeclaredConstructor(S3Client.class, String.class, String.class);
        constructor.setAccessible(true);

        java.lang.reflect.InvocationTargetException exception = assertThrows(
            java.lang.reflect.InvocationTargetException.class,
            () -> constructor.newInstance(null, "bucket", "prefix"));

        assertTrue(exception.getCause() instanceof NullPointerException);
    }

    @Test
    @DisplayName("Should throw NullPointerException when bucketName is null")
    void testConstructorNullBucketName() throws Exception {
        java.lang.reflect.Constructor<S3CloudStorageClient> constructor =
            S3CloudStorageClient.class.getDeclaredConstructor(S3Client.class, String.class, String.class);
        constructor.setAccessible(true);

        java.lang.reflect.InvocationTargetException exception = assertThrows(
            java.lang.reflect.InvocationTargetException.class,
            () -> constructor.newInstance(s3Client, null, "prefix"));

        assertTrue(exception.getCause() instanceof NullPointerException);
    }

    @Test
    @DisplayName("Should handle null prefix")
    void testConstructorNullPrefix() throws Exception {
        java.lang.reflect.Constructor<S3CloudStorageClient> constructor =
            S3CloudStorageClient.class.getDeclaredConstructor(S3Client.class, String.class, String.class);
        constructor.setAccessible(true);

        S3CloudStorageClient testClient = constructor.newInstance(s3Client, "bucket", null);
        assertTrue(testClient.getDescription().contains("prefix=]"));
    }

    @Test
    @DisplayName("Should build with explicit credentials provider")
    void testBuilderWithCredentialsProvider() throws Exception {
        try (org.mockito.MockedStatic<S3Client> s3ClientStatic = org.mockito.Mockito.mockStatic(S3Client.class)) {
            software.amazon.awssdk.services.s3.S3ClientBuilder s3Builder = mock(software.amazon.awssdk.services.s3.S3ClientBuilder.class);

            s3ClientStatic.when(S3Client::builder).thenReturn(s3Builder);
            when(s3Builder.region((Region) any())).thenReturn(s3Builder);
            when(s3Builder.credentialsProvider((AwsCredentialsProvider) any())).thenReturn(s3Builder);
            when(s3Builder.build()).thenReturn(s3Client);

            software.amazon.awssdk.auth.credentials.AwsCredentialsProvider provider =
                StaticCredentialsProvider.create(AwsBasicCredentials.create("key", "secret"));

            S3CloudStorageClient client = S3CloudStorageClient.builder()
                .bucket("test-bucket")
                .credentialsProvider(provider)
                .build();

            assertNotNull(client);
            verify(s3Builder).credentialsProvider(provider);
        }
    }

    @Test
    @DisplayName("Should build with access key credentials")
    void testBuilderWithAccessKey() throws Exception {
        try (org.mockito.MockedStatic<S3Client> s3ClientStatic = org.mockito.Mockito.mockStatic(S3Client.class)) {
            software.amazon.awssdk.services.s3.S3ClientBuilder s3Builder = mock(software.amazon.awssdk.services.s3.S3ClientBuilder.class);

            s3ClientStatic.when(S3Client::builder).thenReturn(s3Builder);
            when(s3Builder.region((Region) any())).thenReturn(s3Builder);
            when(s3Builder.credentialsProvider((AwsCredentialsProvider) any())).thenReturn(s3Builder);
            when(s3Builder.build()).thenReturn(s3Client);

            S3CloudStorageClient client = S3CloudStorageClient.builder()
                .bucket("test-bucket")
                .credentials("access-key", "secret-key")
                .build();

            assertNotNull(client);
            verify(s3Builder).credentialsProvider((AwsCredentialsProvider) any());
        }
    }

    @Test
    @DisplayName("Should build with default credentials provider")
    void testBuilderWithDefaultCredentials() throws Exception {
        try (org.mockito.MockedStatic<S3Client> s3ClientStatic = org.mockito.Mockito.mockStatic(S3Client.class);
             org.mockito.MockedStatic<DefaultCredentialsProvider> defaultCredsStatic =
                 org.mockito.Mockito.mockStatic(DefaultCredentialsProvider.class)) {

            software.amazon.awssdk.services.s3.S3ClientBuilder s3Builder = mock(software.amazon.awssdk.services.s3.S3ClientBuilder.class);
            DefaultCredentialsProvider defaultProvider = mock(DefaultCredentialsProvider.class);

            s3ClientStatic.when(S3Client::builder).thenReturn(s3Builder);
            defaultCredsStatic.when(DefaultCredentialsProvider::create).thenReturn(defaultProvider);
            when(s3Builder.region((Region) any())).thenReturn(s3Builder);
            when(s3Builder.credentialsProvider((AwsCredentialsProvider) any())).thenReturn(s3Builder);
            when(s3Builder.build()).thenReturn(s3Client);

            S3CloudStorageClient client = S3CloudStorageClient.builder()
                .bucket("test-bucket")
                .build();

            assertNotNull(client);
            verify(s3Builder).credentialsProvider(defaultProvider);
        }
    }

    @Test
    @DisplayName("Should set region via string")
    void testBuilderRegionString() throws Exception {
        try (org.mockito.MockedStatic<S3Client> s3ClientStatic = org.mockito.Mockito.mockStatic(S3Client.class)) {
            software.amazon.awssdk.services.s3.S3ClientBuilder s3Builder = mock(software.amazon.awssdk.services.s3.S3ClientBuilder.class);

            s3ClientStatic.when(S3Client::builder).thenReturn(s3Builder);
            when(s3Builder.region((Region) any())).thenReturn(s3Builder);
            when(s3Builder.credentialsProvider((AwsCredentialsProvider) any())).thenReturn(s3Builder);
            when(s3Builder.build()).thenReturn(s3Client);

            S3CloudStorageClient client = S3CloudStorageClient.builder()
                .bucket("test-bucket")
                .region("us-west-2")
                .build();

            assertNotNull(client);
            verify(s3Builder).region((Region) any());
        }
    }

    @Test
    @DisplayName("Should build with prefix")
    void testBuilderWithPrefix() throws Exception {
        try (org.mockito.MockedStatic<S3Client> s3ClientStatic = org.mockito.Mockito.mockStatic(S3Client.class)) {
            software.amazon.awssdk.services.s3.S3ClientBuilder s3Builder = mock(software.amazon.awssdk.services.s3.S3ClientBuilder.class);

            s3ClientStatic.when(S3Client::builder).thenReturn(s3Builder);
            when(s3Builder.region((Region) any())).thenReturn(s3Builder);
            when(s3Builder.credentialsProvider((AwsCredentialsProvider) any())).thenReturn(s3Builder);
            when(s3Builder.build()).thenReturn(s3Client);

            S3CloudStorageClient client = S3CloudStorageClient.builder()
                .bucket("test-bucket")
                .prefix("my-prefix")
                .build();

            assertNotNull(client);
            assertTrue(client.getDescription().contains("my-prefix"));
        }
    }

    @Test
    @DisplayName("Should handle list with prefix filtering")
    void testListWithPrefixFiltering() throws Exception {
        client = createTestClient("base-prefix");

        S3Object obj1 = S3Object.builder().key("base-prefix/file1.txt").build();
        S3Object obj2 = S3Object.builder().key("base-prefix/file2.txt").build();

        ListObjectsV2Response response = ListObjectsV2Response.builder()
            .contents(Arrays.asList(obj1, obj2))
            .build();

        when(s3Client.listObjectsV2((ListObjectsV2Request) any())).thenReturn(response);

        List<String> files = client.list("");
        assertEquals(2, files.size());
        assertTrue(files.contains("file1.txt"));
        assertTrue(files.contains("file2.txt"));
    }

    @Test
    @DisplayName("Should handle openFile failure")
    void testOpenFileFailure() throws Exception {
        client = createTestClient("");
        when(s3Client.getObject((GetObjectRequest) any()))
            .thenThrow(new RuntimeException("S3 error"));

        IOException exception = assertThrows(IOException.class,
            () -> client.openFile("file.txt"));
        assertTrue(exception.getMessage().contains("Failed to open file from S3"));
    }

    @Test
    @DisplayName("Should handle list failure")
    void testListFailure() throws Exception {
        client = createTestClient("");
        when(s3Client.listObjectsV2((ListObjectsV2Request) any()))
            .thenThrow(new RuntimeException("S3 error"));

        IOException exception = assertThrows(IOException.class,
            () -> client.list("prefix"));
        assertTrue(exception.getMessage().contains("Failed to list files in S3"));
    }

    @Test
    @DisplayName("Should handle getFileSize general failure")
    void testGetFileSizeFailure() throws Exception {
        client = createTestClient("");
        when(s3Client.headObject((HeadObjectRequest) any()))
            .thenThrow(new RuntimeException("S3 error"));

        IOException exception = assertThrows(IOException.class,
            () -> client.getFileSize("file.txt"));
        assertTrue(exception.getMessage().contains("Failed to get file size from S3"));
    }

    @Test
    @DisplayName("Should build key with prefix ending in slash")
    void testBuildKeyWithPrefixEndingInSlash() throws Exception {
        client = createTestClient("prefix/");

        byte[] expectedData = "test".getBytes();
        when(s3Client.getObject((GetObjectRequest) any()))
            .thenReturn(new ResponseInputStream<>(
                GetObjectResponse.builder().build(),
                new ByteArrayInputStream(expectedData)));

        byte[] result = client.readFile("file.txt");
        assertArrayEquals(expectedData, result);
    }

    @Test
    @DisplayName("Should handle removePrefix when key doesn't match prefix")
    void testRemovePrefixNoMatch() throws Exception {
        client = createTestClient("my-prefix/");

        java.lang.reflect.Method removePrefix = S3CloudStorageClient.class.getDeclaredMethod(
            "removePrefix", String.class);
        removePrefix.setAccessible(true);

        // key that doesn't start with the expected prefix
        String result = (String) removePrefix.invoke(client, "other/path/file.txt");
        assertEquals("other/path/file.txt", result);
    }

    private S3CloudStorageClient createTestClient(String prefix) throws Exception {
        java.lang.reflect.Constructor<S3CloudStorageClient> constructor =
            S3CloudStorageClient.class.getDeclaredConstructor(S3Client.class, String.class, String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(s3Client, "test-bucket", prefix);
    }
}
