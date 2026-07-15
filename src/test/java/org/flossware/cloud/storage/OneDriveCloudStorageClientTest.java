package org.flossware.cloud.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.MockedConstruction;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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
        assertTrue(testClient.getDescription().contains("basePath="));
    }

    @Test
    @DisplayName("Should build file path without base path")
    void testBuildFilePathNoBasePath() {
        client = OneDriveCloudStorageClient.builder()
            .accessToken("token")
            .build();

        String result = client.buildFilePath("file.txt");
        assertEquals("file.txt", result);
    }

    @Test
    @DisplayName("Should build file path with base path")
    void testBuildFilePathWithBasePath() {
        client = OneDriveCloudStorageClient.builder()
            .accessToken("token")
            .basePath("/documents")
            .build();

        String result = client.buildFilePath("file.txt");
        assertEquals("/documents/file.txt", result);
    }

    @Test
    @DisplayName("Should build file path with trailing slash in base path")
    void testBuildFilePathTrailingSlash() {
        client = OneDriveCloudStorageClient.builder()
            .accessToken("token")
            .basePath("/documents/")
            .build();

        String result = client.buildFilePath("file.txt");
        assertEquals("/documents/file.txt", result);
    }

    @Test
    @DisplayName("Should build download URL for default drive")
    void testBuildDownloadUrlDefaultDrive() throws Exception {
        client = OneDriveCloudStorageClient.builder()
            .accessToken("token")
            .build();

        String result = client.buildDownloadUrl("test.txt");
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

        String result = client.buildDownloadUrl("test.txt");
        assertTrue(result.contains("/me/drives/my-drive-123/root:/"));
        assertTrue(result.contains(":/content"));
    }

    @Test
    @DisplayName("Should build download URL with spaces encoded as %20")
    void testBuildDownloadUrlEncodedSpaces() throws Exception {
        client = OneDriveCloudStorageClient.builder()
            .accessToken("token")
            .build();

        String result = client.buildDownloadUrl("file with spaces.txt");
        assertTrue(result.contains("file%20with%20spaces.txt"));
        assertFalse(result.contains("file+with+spaces.txt"));
    }

    @Test
    @DisplayName("Should build metadata URL for default drive")
    void testBuildMetadataUrlDefaultDrive() throws Exception {
        client = OneDriveCloudStorageClient.builder()
            .accessToken("token")
            .build();

        String result = client.buildMetadataUrl("test.txt");
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

        String result = client.buildMetadataUrl("test.txt");
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

        String result = client.buildMetadataUrl("test.txt");
        assertTrue(result.contains("folder"));
    }

    @Test
    @DisplayName("Should verify GRAPH_API_BASE constant")
    void testGraphApiBase() {
        assertEquals("https://graph.microsoft.com/v1.0", OneDriveCloudStorageClient.GRAPH_API_BASE);
    }

    @Test
    @DisplayName("Should handle empty base path in buildFilePath")
    void testBuildFilePathEmptyBasePath() {
        client = OneDriveCloudStorageClient.builder()
            .accessToken("token")
            .basePath("")
            .build();

        String result = client.buildFilePath("file.txt");
        assertEquals("file.txt", result);
    }

    @Test
    @DisplayName("Should extract size from JSON response")
    void testExtractSize() {
        client = OneDriveCloudStorageClient.builder()
            .accessToken("token")
            .build();

        assertEquals(1048576L, client.extractSize("{\"name\":\"file.txt\",\"size\":1048576}"));
        assertEquals(0L, client.extractSize("{\"name\":\"file.txt\"}"));
        assertEquals(0L, client.extractSize("{}"));
    }

    @Test
    @DisplayName("Should extract size with whitespace around value")
    void testExtractSizeWithWhitespace() {
        client = OneDriveCloudStorageClient.builder()
            .accessToken("token")
            .build();

        assertEquals(42L, client.extractSize("{\"size\" : 42 }"));
    }

    @Test
    @DisplayName("Should extract file names from Graph API response")
    void testExtractFileNames() {
        client = OneDriveCloudStorageClient.builder()
            .accessToken("token")
            .build();

        String json = "{\"value\":[{\"name\":\"doc.txt\",\"file\":{\"mimeType\":\"text/plain\"}},{\"name\":\"folder1\"},{\"name\":\"pic.jpg\",\"file\":{\"mimeType\":\"image/jpeg\"}}]}";
        List<String> names = client.extractFileNames(json);
        assertEquals(2, names.size());
        assertTrue(names.contains("doc.txt"));
        assertTrue(names.contains("pic.jpg"));
    }

    @Test
    @DisplayName("Should return empty list when no files in response")
    void testExtractFileNamesEmpty() {
        client = OneDriveCloudStorageClient.builder()
            .accessToken("token")
            .build();

        List<String> names = client.extractFileNames("{\"value\":[]}");
        assertTrue(names.isEmpty());
    }

    @Test
    @DisplayName("Should extract next link from Graph API response")
    void testExtractNextLink() {
        client = OneDriveCloudStorageClient.builder()
            .accessToken("token")
            .build();

        String json = "{\"value\":[],\"@odata.nextLink\":\"https://graph.microsoft.com/v1.0/next\"}";
        assertEquals("https://graph.microsoft.com/v1.0/next", client.extractNextLink(json));
    }

    @Test
    @DisplayName("Should return null when no next link")
    void testExtractNextLinkNull() {
        client = OneDriveCloudStorageClient.builder()
            .accessToken("token")
            .build();

        assertNull(client.extractNextLink("{\"value\":[]}"));
    }

    @Test
    @DisplayName("Should handle extractSize with missing colon gracefully")
    void testExtractSizeMalformed() {
        client = OneDriveCloudStorageClient.builder()
            .accessToken("token")
            .build();

        assertEquals(0L, client.extractSize("\"size\""));
    }

    @Test
    @DisplayName("Should handle extractSize with no digits after colon")
    void testExtractSizeNoDigits() {
        client = OneDriveCloudStorageClient.builder()
            .accessToken("token")
            .build();

        assertEquals(0L, client.extractSize("{\"size\":null}"));
    }

    @Test
    @DisplayName("Should read file successfully via HTTP")
    void testReadFileSuccess() throws Exception {
        client = OneDriveCloudStorageClient.builder()
            .accessToken("token")
            .build();

        byte[] content = "hello world".getBytes(StandardCharsets.UTF_8);
        HttpURLConnection mockConn = mock(HttpURLConnection.class);
        when(mockConn.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);
        when(mockConn.getInputStream()).thenReturn(new ByteArrayInputStream(content));

        try (MockedConstruction<URL> urlMock = mockConstruction(URL.class,
                (url, ctx) -> when(url.openConnection()).thenReturn(mockConn))) {

            byte[] result = client.readFile("test.txt");
            assertArrayEquals(content, result);
        }
    }

    @Test
    @DisplayName("Should throw IOException on readFile HTTP error")
    void testReadFileHttpError() throws Exception {
        client = OneDriveCloudStorageClient.builder()
            .accessToken("token")
            .build();

        HttpURLConnection mockConn = mock(HttpURLConnection.class);
        when(mockConn.getResponseCode()).thenReturn(HttpURLConnection.HTTP_NOT_FOUND);

        try (MockedConstruction<URL> urlMock = mockConstruction(URL.class,
                (url, ctx) -> when(url.openConnection()).thenReturn(mockConn))) {

            IOException ex = assertThrows(IOException.class, () -> client.readFile("missing.txt"));
            assertTrue(ex.getMessage().contains("HTTP error code: 404"));
        }
    }

    @Test
    @DisplayName("Should open file successfully via HTTP")
    void testOpenFileSuccess() throws Exception {
        client = OneDriveCloudStorageClient.builder()
            .accessToken("token")
            .build();

        byte[] content = "stream data".getBytes(StandardCharsets.UTF_8);
        HttpURLConnection mockConn = mock(HttpURLConnection.class);
        when(mockConn.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);
        when(mockConn.getInputStream()).thenReturn(new ByteArrayInputStream(content));

        try (MockedConstruction<URL> urlMock = mockConstruction(URL.class,
                (url, ctx) -> when(url.openConnection()).thenReturn(mockConn))) {

            assertNotNull(client.openFile("test.txt"));
        }
    }

    @Test
    @DisplayName("Should throw IOException on openFile HTTP error")
    void testOpenFileHttpError() throws Exception {
        client = OneDriveCloudStorageClient.builder()
            .accessToken("token")
            .build();

        HttpURLConnection mockConn = mock(HttpURLConnection.class);
        when(mockConn.getResponseCode()).thenReturn(HttpURLConnection.HTTP_FORBIDDEN);

        try (MockedConstruction<URL> urlMock = mockConstruction(URL.class,
                (url, ctx) -> when(url.openConnection()).thenReturn(mockConn))) {

            IOException ex = assertThrows(IOException.class, () -> client.openFile("secret.txt"));
            assertTrue(ex.getMessage().contains("HTTP error code: 403"));
        }
    }

    @Test
    @DisplayName("Should return true when file exists via HTTP")
    void testExistsTrue() throws Exception {
        client = OneDriveCloudStorageClient.builder()
            .accessToken("token")
            .build();

        HttpURLConnection mockConn = mock(HttpURLConnection.class);
        when(mockConn.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);

        try (MockedConstruction<URL> urlMock = mockConstruction(URL.class,
                (url, ctx) -> when(url.openConnection()).thenReturn(mockConn))) {

            assertTrue(client.exists("test.txt"));
        }
    }

    @Test
    @DisplayName("Should return false when file does not exist")
    void testExistsFalse() throws Exception {
        client = OneDriveCloudStorageClient.builder()
            .accessToken("token")
            .build();

        HttpURLConnection mockConn = mock(HttpURLConnection.class);
        when(mockConn.getResponseCode()).thenReturn(HttpURLConnection.HTTP_NOT_FOUND);

        try (MockedConstruction<URL> urlMock = mockConstruction(URL.class,
                (url, ctx) -> when(url.openConnection()).thenReturn(mockConn))) {

            assertFalse(client.exists("missing.txt"));
        }
    }

    @Test
    @DisplayName("Should return false when exists check throws IOException")
    void testExistsException() throws Exception {
        client = OneDriveCloudStorageClient.builder()
            .accessToken("token")
            .build();

        try (MockedConstruction<URL> urlMock = mockConstruction(URL.class,
                (url, ctx) -> when(url.openConnection()).thenThrow(new IOException("network error")))) {

            assertFalse(client.exists("test.txt"));
        }
    }

    @Test
    @DisplayName("Should list files successfully via HTTP")
    void testListSuccess() throws Exception {
        client = OneDriveCloudStorageClient.builder()
            .accessToken("token")
            .build();

        String jsonResponse = "{\"value\":[{\"name\":\"a.txt\",\"file\":{\"mimeType\":\"text/plain\"}},{\"name\":\"b.txt\",\"file\":{\"mimeType\":\"text/plain\"}}]}";
        HttpURLConnection mockConn = mock(HttpURLConnection.class);
        when(mockConn.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);
        when(mockConn.getInputStream()).thenReturn(new ByteArrayInputStream(jsonResponse.getBytes(StandardCharsets.UTF_8)));

        try (MockedConstruction<URL> urlMock = mockConstruction(URL.class,
                (url, ctx) -> when(url.openConnection()).thenReturn(mockConn))) {

            List<String> files = client.list("");
            assertEquals(2, files.size());
        }
    }

    @Test
    @DisplayName("Should throw IOException on list HTTP error")
    void testListHttpError() throws Exception {
        client = OneDriveCloudStorageClient.builder()
            .accessToken("token")
            .build();

        HttpURLConnection mockConn = mock(HttpURLConnection.class);
        when(mockConn.getResponseCode()).thenReturn(HttpURLConnection.HTTP_INTERNAL_ERROR);

        try (MockedConstruction<URL> urlMock = mockConstruction(URL.class,
                (url, ctx) -> when(url.openConnection()).thenReturn(mockConn))) {

            IOException ex = assertThrows(IOException.class, () -> client.list("folder"));
            assertTrue(ex.getMessage().contains("HTTP error code: 500"));
        }
    }

    @Test
    @DisplayName("Should list with custom drive and non-empty folder")
    void testListCustomDriveWithFolder() throws Exception {
        client = OneDriveCloudStorageClient.builder()
            .accessToken("token")
            .driveId("my-drive")
            .basePath("docs")
            .build();

        String jsonResponse = "{\"value\":[{\"name\":\"c.txt\",\"file\":{}}]}";
        HttpURLConnection mockConn = mock(HttpURLConnection.class);
        when(mockConn.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);
        when(mockConn.getInputStream()).thenReturn(new ByteArrayInputStream(jsonResponse.getBytes(StandardCharsets.UTF_8)));

        try (MockedConstruction<URL> urlMock = mockConstruction(URL.class,
                (url, ctx) -> when(url.openConnection()).thenReturn(mockConn))) {

            List<String> files = client.list("sub");
            assertEquals(1, files.size());
        }
    }

    @Test
    @DisplayName("Should get file size via HTTP and parse JSON response")
    void testGetFileSizeSuccess() throws Exception {
        client = OneDriveCloudStorageClient.builder()
            .accessToken("token")
            .build();

        String jsonResponse = "{\"name\":\"test.txt\",\"size\":98765}";
        HttpURLConnection mockConn = mock(HttpURLConnection.class);
        when(mockConn.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);
        when(mockConn.getInputStream()).thenReturn(new ByteArrayInputStream(jsonResponse.getBytes(StandardCharsets.UTF_8)));

        try (MockedConstruction<URL> urlMock = mockConstruction(URL.class,
                (url, ctx) -> when(url.openConnection()).thenReturn(mockConn))) {

            assertEquals(98765L, client.getFileSize("test.txt"));
        }
    }

    @Test
    @DisplayName("Should throw IOException on getFileSize HTTP error")
    void testGetFileSizeHttpError() throws Exception {
        client = OneDriveCloudStorageClient.builder()
            .accessToken("token")
            .build();

        HttpURLConnection mockConn = mock(HttpURLConnection.class);
        when(mockConn.getResponseCode()).thenReturn(HttpURLConnection.HTTP_NOT_FOUND);

        try (MockedConstruction<URL> urlMock = mockConstruction(URL.class,
                (url, ctx) -> when(url.openConnection()).thenReturn(mockConn))) {

            IOException ex = assertThrows(IOException.class, () -> client.getFileSize("missing.txt"));
            assertTrue(ex.getMessage().contains("HTTP error code: 404"));
        }
    }

    @Test
    @DisplayName("Should handle extractSize with overflow number gracefully")
    void testExtractSizeOverflow() {
        client = OneDriveCloudStorageClient.builder()
            .accessToken("token")
            .build();

        assertEquals(0L, client.extractSize("{\"size\":99999999999999999999999}"));
    }

    @Test
    @DisplayName("Should handle extractFileNames when name appears after file marker")
    void testExtractFileNamesEdgeCase() {
        client = OneDriveCloudStorageClient.builder()
            .accessToken("token")
            .build();

        List<String> names = client.extractFileNames("{\"value\":[{\"file\":{}}]}");
        assertTrue(names.isEmpty());
    }
}
