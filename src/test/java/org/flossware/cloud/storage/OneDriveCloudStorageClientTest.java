package org.flossware.cloud.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.IOException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for OneDriveCloudStorageClient to achieve 100% coverage.
 * Note: Due to direct HttpURLConnection usage, some tests use reflection to verify
 * URL construction and logic without making actual HTTP calls.
 */
class OneDriveCloudStorageClientTest {

    private OneDriveCloudStorageClient client;

    @AfterEach
    void tearDown() throws Exception {
        if (client != null) {
            client.close();
        }
    }

    @Test
    @DisplayName("Should support builder chaining")
    void testBuilderChaining() {
        OneDriveCloudStorageClient.Builder builder = OneDriveCloudStorageClient.builder();
        assertSame(builder, builder.accessToken("token"));
        assertSame(builder, builder.basePath("/base"));
        assertSame(builder, builder.driveId("drive-123"));
    }

    @Test
    @DisplayName("Should throw NullPointerException when accessToken is null in build")
    void testBuilderNullAccessToken() {
        OneDriveCloudStorageClient.Builder builder = OneDriveCloudStorageClient.builder();

        assertThrows(NullPointerException.class, builder::build);
    }

    @Test
    @DisplayName("Should build client successfully with minimal configuration")
    void testBuilderMinimal() {
        client = OneDriveCloudStorageClient.builder()
            .accessToken("test-token")
            .build();

        assertNotNull(client);
        assertTrue(client.getDescription().contains("default"));
    }

    @Test
    @DisplayName("Should build client with full configuration")
    void testBuilderFull() {
        client = OneDriveCloudStorageClient.builder()
            .accessToken("test-token")
            .basePath("/my-base")
            .driveId("drive-xyz")
            .build();

        assertNotNull(client);
        assertTrue(client.getDescription().contains("drive-xyz"));
        assertTrue(client.getDescription().contains("/my-base"));
    }

    @Test
    @DisplayName("Should throw UnsupportedOperationException for list")
    void testListUnsupported() {
        client = OneDriveCloudStorageClient.builder()
            .accessToken("token")
            .build();

        UnsupportedOperationException exception = assertThrows(
            UnsupportedOperationException.class,
            () -> client.list("prefix"));

        assertTrue(exception.getMessage().contains("Microsoft Graph SDK"));
    }

    @Test
    @DisplayName("Should return description with default drive")
    void testGetDescriptionDefaultDrive() {
        client = OneDriveCloudStorageClient.builder()
            .accessToken("token")
            .build();

        String description = client.getDescription();
        assertTrue(description.contains("OneDriveCloudStorageClient"));
        assertTrue(description.contains("default"));
    }

    @Test
    @DisplayName("Should return description with custom drive")
    void testGetDescriptionCustomDrive() {
        client = OneDriveCloudStorageClient.builder()
            .accessToken("token")
            .driveId("my-drive")
            .build();

        String description = client.getDescription();
        assertTrue(description.contains("my-drive"));
    }

    @Test
    @DisplayName("Should return description with base path")
    void testGetDescriptionWithBasePath() {
        client = OneDriveCloudStorageClient.builder()
            .accessToken("token")
            .basePath("/documents")
            .build();

        String description = client.getDescription();
        assertTrue(description.contains("/documents"));
    }

    @Test
    @DisplayName("Should close without error")
    void testClose() {
        client = OneDriveCloudStorageClient.builder()
            .accessToken("token")
            .build();

        assertDoesNotThrow(() -> client.close());
    }

    @Test
    @DisplayName("Should throw NullPointerException when accessToken is null in constructor")
    void testConstructorNullAccessToken() throws Exception {
        java.lang.reflect.Constructor<OneDriveCloudStorageClient> constructor =
            OneDriveCloudStorageClient.class.getDeclaredConstructor(
                String.class, String.class, String.class);
        constructor.setAccessible(true);

        java.lang.reflect.InvocationTargetException exception = assertThrows(
            java.lang.reflect.InvocationTargetException.class,
            () -> constructor.newInstance(null, "base", "drive"));

        assertTrue(exception.getCause() instanceof NullPointerException);
        assertTrue(exception.getCause().getMessage().contains("accessToken cannot be null"));
    }

    @Test
    @DisplayName("Should handle null basePath in constructor")
    void testConstructorNullBasePath() throws Exception {
        java.lang.reflect.Constructor<OneDriveCloudStorageClient> constructor =
            OneDriveCloudStorageClient.class.getDeclaredConstructor(
                String.class, String.class, String.class);
        constructor.setAccessible(true);

        OneDriveCloudStorageClient testClient = constructor.newInstance("token", null, null);
        // Null basePath is converted to empty string
        assertTrue(testClient.getDescription().contains("basePath="));
    }

    @Test
    @DisplayName("Should build file path without base path")
    void testBuildFilePathNoBasePath() throws Exception {
        client = OneDriveCloudStorageClient.builder()
            .accessToken("token")
            .build();

        Method buildFilePath = OneDriveCloudStorageClient.class.getDeclaredMethod(
            "buildFilePath", String.class);
        buildFilePath.setAccessible(true);

        String result = (String) buildFilePath.invoke(client, "file.txt");
        assertEquals("file.txt", result);
    }

    @Test
    @DisplayName("Should build file path with base path")
    void testBuildFilePathWithBasePath() throws Exception {
        client = OneDriveCloudStorageClient.builder()
            .accessToken("token")
            .basePath("/documents")
            .build();

        Method buildFilePath = OneDriveCloudStorageClient.class.getDeclaredMethod(
            "buildFilePath", String.class);
        buildFilePath.setAccessible(true);

        String result = (String) buildFilePath.invoke(client, "file.txt");
        assertEquals("/documents/file.txt", result);
    }

    @Test
    @DisplayName("Should build file path with trailing slash in base path")
    void testBuildFilePathTrailingSlash() throws Exception {
        client = OneDriveCloudStorageClient.builder()
            .accessToken("token")
            .basePath("/documents/")
            .build();

        Method buildFilePath = OneDriveCloudStorageClient.class.getDeclaredMethod(
            "buildFilePath", String.class);
        buildFilePath.setAccessible(true);

        String result = (String) buildFilePath.invoke(client, "file.txt");
        assertEquals("/documents/file.txt", result);
    }

    @Test
    @DisplayName("Should build download URL for default drive")
    void testBuildDownloadUrlDefaultDrive() throws Exception {
        client = OneDriveCloudStorageClient.builder()
            .accessToken("token")
            .build();

        Method buildDownloadUrl = OneDriveCloudStorageClient.class.getDeclaredMethod(
            "buildDownloadUrl", String.class);
        buildDownloadUrl.setAccessible(true);

        String result = (String) buildDownloadUrl.invoke(client, "test.txt");
        assertTrue(result.contains("/me/drive/root:/"));
        assertTrue(result.contains(":/content"));
        assertTrue(result.contains("test.txt"));
    }

    @Test
    @DisplayName("Should build download URL for custom drive")
    void testBuildDownloadUrlCustomDrive() throws Exception {
        client = OneDriveCloudStorageClient.builder()
            .accessToken("token")
            .driveId("my-drive-123")
            .build();

        Method buildDownloadUrl = OneDriveCloudStorageClient.class.getDeclaredMethod(
            "buildDownloadUrl", String.class);
        buildDownloadUrl.setAccessible(true);

        String result = (String) buildDownloadUrl.invoke(client, "test.txt");
        assertTrue(result.contains("/me/drives/my-drive-123/root:/"));
        assertTrue(result.contains(":/content"));
    }

    @Test
    @DisplayName("Should build download URL with encoded path")
    void testBuildDownloadUrlEncoded() throws Exception {
        client = OneDriveCloudStorageClient.builder()
            .accessToken("token")
            .build();

        Method buildDownloadUrl = OneDriveCloudStorageClient.class.getDeclaredMethod(
            "buildDownloadUrl", String.class);
        buildDownloadUrl.setAccessible(true);

        String result = (String) buildDownloadUrl.invoke(client, "file with spaces.txt");
        assertTrue(result.contains("file+with+spaces.txt") || result.contains("file%20with%20spaces.txt"));
    }

    @Test
    @DisplayName("Should build metadata URL for default drive")
    void testBuildMetadataUrlDefaultDrive() throws Exception {
        client = OneDriveCloudStorageClient.builder()
            .accessToken("token")
            .build();

        Method buildMetadataUrl = OneDriveCloudStorageClient.class.getDeclaredMethod(
            "buildMetadataUrl", String.class);
        buildMetadataUrl.setAccessible(true);

        String result = (String) buildMetadataUrl.invoke(client, "test.txt");
        assertTrue(result.contains("/me/drive/root:/"));
        assertFalse(result.contains(":/content"));
        assertTrue(result.contains("test.txt"));
    }

    @Test
    @DisplayName("Should build metadata URL for custom drive")
    void testBuildMetadataUrlCustomDrive() throws Exception {
        client = OneDriveCloudStorageClient.builder()
            .accessToken("token")
            .driveId("drive-abc")
            .build();

        Method buildMetadataUrl = OneDriveCloudStorageClient.class.getDeclaredMethod(
            "buildMetadataUrl", String.class);
        buildMetadataUrl.setAccessible(true);

        String result = (String) buildMetadataUrl.invoke(client, "test.txt");
        assertTrue(result.contains("/me/drives/drive-abc/root:/"));
        assertFalse(result.contains(":/content"));
    }

    @Test
    @DisplayName("Should build metadata URL with base path")
    void testBuildMetadataUrlWithBasePath() throws Exception {
        client = OneDriveCloudStorageClient.builder()
            .accessToken("token")
            .basePath("/folder")
            .build();

        Method buildMetadataUrl = OneDriveCloudStorageClient.class.getDeclaredMethod(
            "buildMetadataUrl", String.class);
        buildMetadataUrl.setAccessible(true);

        String result = (String) buildMetadataUrl.invoke(client, "test.txt");
        assertTrue(result.contains("folder"));
    }

    @Test
    @DisplayName("Should verify GRAPH_API_BASE constant")
    void testGraphApiBase() throws Exception {
        java.lang.reflect.Field field = OneDriveCloudStorageClient.class.getDeclaredField("GRAPH_API_BASE");
        field.setAccessible(true);
        String value = (String) field.get(null);
        assertEquals("https://graph.microsoft.com/v1.0", value);
    }

    @Test
    @DisplayName("Should handle empty base path in buildFilePath")
    void testBuildFilePathEmptyBasePath() throws Exception {
        client = OneDriveCloudStorageClient.builder()
            .accessToken("token")
            .basePath("")
            .build();

        Method buildFilePath = OneDriveCloudStorageClient.class.getDeclaredMethod(
            "buildFilePath", String.class);
        buildFilePath.setAccessible(true);

        String result = (String) buildFilePath.invoke(client, "file.txt");
        assertEquals("file.txt", result);
    }
}
