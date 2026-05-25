package org.flossware.cloud.storage;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
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
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for GoogleDriveCloudStorageClient to achieve 100% coverage.
 */
class GoogleDriveCloudStorageClientTest {

    @Mock
    private Drive driveService;

    @Mock
    private Drive.Files files;

    @Mock
    private Drive.Files.Get get;

    @Mock
    private Drive.Files.List list;

    @Mock
    private File file;

    @Mock
    private FileList fileList;

    private AutoCloseable mocks;
    private GoogleDriveCloudStorageClient client;

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
    void testBuilderChaining() throws Exception {
        GoogleDriveCloudStorageClient.Builder builder = GoogleDriveCloudStorageClient.builder();

        com.google.auth.oauth2.GoogleCredentials creds = mock(com.google.auth.oauth2.GoogleCredentials.class);
        InputStream credStream = new ByteArrayInputStream("{}".getBytes());
        assertSame(builder, builder.credentials(creds));
        assertSame(builder, builder.folderId("folder-123"));
        assertSame(builder, builder.applicationName("test-app"));
    }

    @Test
    @DisplayName("Should read file successfully")
    void testReadFileSuccess() throws Exception {
        client = createTestClient(null);

        byte[] expectedData = "test-content".getBytes();
        String fileId = "file-id-123";

        setupFindFileIdMocks(fileId, "test.txt");

        when(driveService.files()).thenReturn(files);
        when(files.get(fileId)).thenReturn(get);
        doAnswer(invocation -> {
            ByteArrayOutputStream out = invocation.getArgument(0);
            out.write(expectedData);
            return null;
        }).when(get).executeMediaAndDownloadTo(any(ByteArrayOutputStream.class));

        byte[] result = client.readFile("test.txt");

        assertArrayEquals(expectedData, result);
        verify(files).get(fileId);
    }

    @Test
    @DisplayName("Should throw IOException when file not found on read")
    void testReadFileNotFound() throws Exception {
        client = createTestClient(null);

        setupFindFileIdMocks(null, "missing.txt");

        IOException exception = assertThrows(IOException.class,
            () -> client.readFile("missing.txt"));

        assertTrue(exception.getMessage().contains("File not found in Google Drive"));
        assertTrue(exception.getMessage().contains("missing.txt"));
    }

    @Test
    @DisplayName("Should open file successfully")
    void testOpenFileSuccess() throws Exception {
        client = createTestClient(null);

        InputStream mockStream = new ByteArrayInputStream("data".getBytes());
        String fileId = "file-id-456";

        setupFindFileIdMocks(fileId, "test.txt");

        when(driveService.files()).thenReturn(files);
        when(files.get(fileId)).thenReturn(get);
        when(get.executeMediaAsInputStream()).thenReturn(mockStream);

        InputStream result = client.openFile("test.txt");

        assertSame(mockStream, result);
    }

    @Test
    @DisplayName("Should throw IOException when file not found on open")
    void testOpenFileNotFound() throws Exception {
        client = createTestClient(null);

        setupFindFileIdMocks(null, "missing.txt");

        IOException exception = assertThrows(IOException.class,
            () -> client.openFile("missing.txt"));

        assertTrue(exception.getMessage().contains("File not found in Google Drive"));
    }

    @Test
    @DisplayName("Should check exists returns true")
    void testExistsTrue() throws Exception {
        client = createTestClient(null);

        setupFindFileIdMocks("file-id-789", "exists.txt");

        assertTrue(client.exists("exists.txt"));
    }

    @Test
    @DisplayName("Should check exists returns false")
    void testExistsFalse() throws Exception {
        client = createTestClient(null);

        setupFindFileIdMocks(null, "missing.txt");

        assertFalse(client.exists("missing.txt"));
    }

    @Test
    @DisplayName("Should check exists returns false on IOException")
    void testExistsFalseOnException() throws Exception {
        client = createTestClient(null);

        when(driveService.files()).thenReturn(files);
        when(files.list()).thenReturn(list);
        when(list.setQ(anyString())).thenReturn(list);
        when(list.setSpaces(anyString())).thenReturn(list);
        when(list.setFields(anyString())).thenReturn(list);
        when(list.execute()).thenThrow(new IOException("Drive error"));

        assertFalse(client.exists("error.txt"));
    }

    @Test
    @DisplayName("Should list files successfully without folder")
    void testListSuccessNoFolder() throws Exception {
        client = createTestClient(null);

        File file1 = mock(File.class);
        File file2 = mock(File.class);
        when(file1.getName()).thenReturn("file1.txt");
        when(file1.getId()).thenReturn("id1");
        when(file2.getName()).thenReturn("file2.txt");
        when(file2.getId()).thenReturn("id2");

        when(driveService.files()).thenReturn(files);
        when(files.list()).thenReturn(list);
        when(list.setQ(anyString())).thenReturn(list);
        when(list.setSpaces(anyString())).thenReturn(list);
        when(list.setFields(anyString())).thenReturn(list);
        when(list.execute()).thenReturn(fileList);
        when(fileList.getFiles()).thenReturn(Arrays.asList(file1, file2));

        List<String> result = client.list("");

        assertEquals(2, result.size());
        assertTrue(result.contains("file1.txt"));
        assertTrue(result.contains("file2.txt"));
    }

    @Test
    @DisplayName("Should list files successfully with folder ID")
    void testListSuccessWithFolder() throws Exception {
        client = createTestClient("folder-123");

        File file1 = mock(File.class);
        when(file1.getName()).thenReturn("file1.txt");
        when(file1.getId()).thenReturn("id1");

        when(driveService.files()).thenReturn(files);
        when(files.list()).thenReturn(list);
        when(list.setQ(anyString())).thenReturn(list);
        when(list.setSpaces(anyString())).thenReturn(list);
        when(list.setFields(anyString())).thenReturn(list);
        when(list.execute()).thenReturn(fileList);
        when(fileList.getFiles()).thenReturn(Arrays.asList(file1));

        List<String> result = client.list("");

        assertEquals(1, result.size());
        verify(list).setQ(contains("'folder-123' in parents"));
    }

    @Test
    @DisplayName("Should list files with prefix filtering")
    void testListWithPrefix() throws Exception {
        client = createTestClient(null);

        File file1 = mock(File.class);
        File file2 = mock(File.class);
        when(file1.getName()).thenReturn("test-file1.txt");
        when(file1.getId()).thenReturn("id1");
        when(file2.getName()).thenReturn("other-file.txt");
        when(file2.getId()).thenReturn("id2");

        when(driveService.files()).thenReturn(files);
        when(files.list()).thenReturn(list);
        when(list.setQ(anyString())).thenReturn(list);
        when(list.setSpaces(anyString())).thenReturn(list);
        when(list.setFields(anyString())).thenReturn(list);
        when(list.execute()).thenReturn(fileList);
        when(fileList.getFiles()).thenReturn(Arrays.asList(file1, file2));

        List<String> result = client.list("test-");

        assertEquals(1, result.size());
        assertTrue(result.contains("test-file1.txt"));
        assertFalse(result.contains("other-file.txt"));
    }

    @Test
    @DisplayName("Should handle null file list")
    void testListNullFileList() throws Exception {
        client = createTestClient(null);

        when(driveService.files()).thenReturn(files);
        when(files.list()).thenReturn(list);
        when(list.setQ(anyString())).thenReturn(list);
        when(list.setSpaces(anyString())).thenReturn(list);
        when(list.setFields(anyString())).thenReturn(list);
        when(list.execute()).thenReturn(fileList);
        when(fileList.getFiles()).thenReturn(null);

        List<String> result = client.list("");

        assertEquals(0, result.size());
    }

    @Test
    @DisplayName("Should get file size successfully")
    void testGetFileSizeSuccess() throws Exception {
        client = createTestClient(null);

        String fileId = "file-id-size";
        File fileWithSize = mock(File.class);

        setupFindFileIdMocks(fileId, "test.txt");

        when(driveService.files()).thenReturn(files);
        when(files.get(fileId)).thenReturn(get);
        when(get.setFields("size")).thenReturn(get);
        when(get.execute()).thenReturn(fileWithSize);
        when(fileWithSize.getSize()).thenReturn(12345L);

        long size = client.getFileSize("test.txt");

        assertEquals(12345L, size);
    }

    @Test
    @DisplayName("Should return 0 when file size is null")
    void testGetFileSizeNull() throws Exception {
        client = createTestClient(null);

        String fileId = "file-id-null";
        File fileWithSize = mock(File.class);

        setupFindFileIdMocks(fileId, "test.txt");

        when(driveService.files()).thenReturn(files);
        when(files.get(fileId)).thenReturn(get);
        when(get.setFields("size")).thenReturn(get);
        when(get.execute()).thenReturn(fileWithSize);
        when(fileWithSize.getSize()).thenReturn(null);

        long size = client.getFileSize("test.txt");

        assertEquals(0L, size);
    }

    @Test
    @DisplayName("Should throw IOException when file not found for size")
    void testGetFileSizeNotFound() throws Exception {
        client = createTestClient(null);

        setupFindFileIdMocks(null, "missing.txt");

        IOException exception = assertThrows(IOException.class,
            () -> client.getFileSize("missing.txt"));

        assertTrue(exception.getMessage().contains("File not found in Google Drive"));
    }

    @Test
    @DisplayName("Should return description with folder ID")
    void testGetDescriptionWithFolder() throws Exception {
        client = createTestClient("folder-xyz");
        assertTrue(client.getDescription().contains("folder-xyz"));
    }

    @Test
    @DisplayName("Should return description without folder ID")
    void testGetDescriptionNoFolder() throws Exception {
        client = createTestClient(null);
        assertTrue(client.getDescription().contains("folder=null"));
    }

    @Test
    @DisplayName("Should close without error")
    void testClose() throws Exception {
        client = createTestClient(null);
        assertDoesNotThrow(() -> client.close());
    }

    @Test
    @DisplayName("Should use cached file ID on second lookup")
    void testFileIdCaching() throws Exception {
        client = createTestClient(null);

        String fileId = "cached-id";
        setupFindFileIdMocks(fileId, "cached.txt");

        // First call should query Drive
        client.exists("cached.txt");

        // Second call should use cache - we'll verify by checking files.list() is only called once
        client.exists("cached.txt");

        // Should only call list once (during first exists check)
        verify(files, times(1)).list();
    }

    @Test
    @DisplayName("Should extract filename from path with slashes")
    void testFindFileIdWithPath() throws Exception {
        client = createTestClient(null);

        String fileId = "path-id";
        File foundFile = mock(File.class);
        when(foundFile.getId()).thenReturn(fileId);
        when(foundFile.getName()).thenReturn("file.txt");

        when(driveService.files()).thenReturn(files);
        when(files.list()).thenReturn(list);
        when(list.setQ(anyString())).thenReturn(list);
        when(list.setSpaces(anyString())).thenReturn(list);
        when(list.setFields(anyString())).thenReturn(list);
        when(list.execute()).thenReturn(fileList);
        when(fileList.getFiles()).thenReturn(Arrays.asList(foundFile));

        client.exists("path/to/file.txt");

        // Should extract "file.txt" from the path
        verify(list).setQ(contains("name='file.txt'"));
    }

    @Test
    @DisplayName("Should throw NullPointerException when driveService is null")
    void testConstructorNullDriveService() throws Exception {
        java.lang.reflect.Constructor<GoogleDriveCloudStorageClient> constructor =
            GoogleDriveCloudStorageClient.class.getDeclaredConstructor(Drive.class, String.class);
        constructor.setAccessible(true);

        java.lang.reflect.InvocationTargetException exception = assertThrows(
            java.lang.reflect.InvocationTargetException.class,
            () -> constructor.newInstance(null, "folder"));

        assertTrue(exception.getCause() instanceof NullPointerException);
        assertTrue(exception.getCause().getMessage().contains("driveService cannot be null"));
    }

    @Test
    @DisplayName("Should handle null folder ID in constructor")
    void testConstructorNullFolderId() throws Exception {
        client = createTestClient(null);
        assertNotNull(client);
        assertTrue(client.getDescription().contains("folder=null"));
    }

    @Test
    @DisplayName("Should include folderId in query when searching for file")
    void testFindFileIdWithFolderId() throws Exception {
        client = createTestClient("folder-abc");

        setupFindFileIdMocks("file-id-123", "test.txt");

        assertTrue(client.exists("test.txt"));

        // Verify the query included the folderId
        verify(list).setQ(contains("'folder-abc' in parents"));
    }

    @Test
    @DisplayName("Should build with provided credentials")
    void testBuilderWithCredentials() throws Exception {
        com.google.auth.oauth2.GoogleCredentials credentials = mock(com.google.auth.oauth2.GoogleCredentials.class);
        com.google.api.client.http.javanet.NetHttpTransport httpTransport = mock(com.google.api.client.http.javanet.NetHttpTransport.class);

        try (org.mockito.MockedStatic<com.google.api.client.googleapis.javanet.GoogleNetHttpTransport> transportStatic =
                 org.mockito.Mockito.mockStatic(com.google.api.client.googleapis.javanet.GoogleNetHttpTransport.class);
             org.mockito.MockedConstruction<com.google.api.services.drive.Drive.Builder> driveBuilderMock =
                 org.mockito.Mockito.mockConstruction(com.google.api.services.drive.Drive.Builder.class,
                     (mock, context) -> {
                         when(mock.setApplicationName(anyString())).thenReturn(mock);
                         when(mock.build()).thenReturn(driveService);
                     })) {

            transportStatic.when(com.google.api.client.googleapis.javanet.GoogleNetHttpTransport::newTrustedTransport)
                .thenReturn(httpTransport);

            GoogleDriveCloudStorageClient result = GoogleDriveCloudStorageClient.builder()
                .credentials(credentials)
                .build();

            assertNotNull(result);
        }
    }

    @Test
    @DisplayName("Should build with credentialsFromStream")
    void testBuilderWithCredentialsFromStream() throws Exception {
        com.google.auth.oauth2.GoogleCredentials credentials = mock(com.google.auth.oauth2.GoogleCredentials.class);
        when(credentials.createScoped(anyList())).thenReturn(credentials);
        com.google.api.client.http.javanet.NetHttpTransport httpTransport = mock(com.google.api.client.http.javanet.NetHttpTransport.class);

        try (org.mockito.MockedStatic<com.google.auth.oauth2.GoogleCredentials> credStatic =
                 org.mockito.Mockito.mockStatic(com.google.auth.oauth2.GoogleCredentials.class);
             org.mockito.MockedStatic<com.google.api.client.googleapis.javanet.GoogleNetHttpTransport> transportStatic =
                 org.mockito.Mockito.mockStatic(com.google.api.client.googleapis.javanet.GoogleNetHttpTransport.class);
             org.mockito.MockedConstruction<com.google.api.services.drive.Drive.Builder> driveBuilderMock =
                 org.mockito.Mockito.mockConstruction(com.google.api.services.drive.Drive.Builder.class,
                     (mock, context) -> {
                         when(mock.setApplicationName(anyString())).thenReturn(mock);
                         when(mock.build()).thenReturn(driveService);
                     })) {

            credStatic.when(() -> com.google.auth.oauth2.GoogleCredentials.fromStream(any(InputStream.class)))
                .thenReturn(credentials);
            transportStatic.when(com.google.api.client.googleapis.javanet.GoogleNetHttpTransport::newTrustedTransport)
                .thenReturn(httpTransport);

            InputStream credStream = new ByteArrayInputStream("{}".getBytes());
            GoogleDriveCloudStorageClient result = GoogleDriveCloudStorageClient.builder()
                .credentialsFromStream(credStream)
                .build();

            assertNotNull(result);
            verify(credentials).createScoped(anyList());
        }
    }

    @Test
    @DisplayName("Should build with default credentials")
    void testBuilderWithDefaultCredentials() throws Exception {
        com.google.auth.oauth2.GoogleCredentials credentials = mock(com.google.auth.oauth2.GoogleCredentials.class);
        when(credentials.createScoped(anyList())).thenReturn(credentials);
        com.google.api.client.http.javanet.NetHttpTransport httpTransport = mock(com.google.api.client.http.javanet.NetHttpTransport.class);

        try (org.mockito.MockedStatic<com.google.auth.oauth2.GoogleCredentials> credStatic =
                 org.mockito.Mockito.mockStatic(com.google.auth.oauth2.GoogleCredentials.class);
             org.mockito.MockedStatic<com.google.api.client.googleapis.javanet.GoogleNetHttpTransport> transportStatic =
                 org.mockito.Mockito.mockStatic(com.google.api.client.googleapis.javanet.GoogleNetHttpTransport.class);
             org.mockito.MockedConstruction<com.google.api.services.drive.Drive.Builder> driveBuilderMock =
                 org.mockito.Mockito.mockConstruction(com.google.api.services.drive.Drive.Builder.class,
                     (mock, context) -> {
                         when(mock.setApplicationName(anyString())).thenReturn(mock);
                         when(mock.build()).thenReturn(driveService);
                     })) {

            credStatic.when(com.google.auth.oauth2.GoogleCredentials::getApplicationDefault)
                .thenReturn(credentials);
            transportStatic.when(com.google.api.client.googleapis.javanet.GoogleNetHttpTransport::newTrustedTransport)
                .thenReturn(httpTransport);

            GoogleDriveCloudStorageClient result = GoogleDriveCloudStorageClient.builder().build();

            assertNotNull(result);
            verify(credentials).createScoped(anyList());
        }
    }

    @Test
    @DisplayName("Should build with folderId")
    void testBuilderWithFolderId() throws Exception {
        com.google.auth.oauth2.GoogleCredentials credentials = mock(com.google.auth.oauth2.GoogleCredentials.class);
        when(credentials.createScoped(anyList())).thenReturn(credentials);
        com.google.api.client.http.javanet.NetHttpTransport httpTransport = mock(com.google.api.client.http.javanet.NetHttpTransport.class);

        try (org.mockito.MockedStatic<com.google.auth.oauth2.GoogleCredentials> credStatic =
                 org.mockito.Mockito.mockStatic(com.google.auth.oauth2.GoogleCredentials.class);
             org.mockito.MockedStatic<com.google.api.client.googleapis.javanet.GoogleNetHttpTransport> transportStatic =
                 org.mockito.Mockito.mockStatic(com.google.api.client.googleapis.javanet.GoogleNetHttpTransport.class);
             org.mockito.MockedConstruction<com.google.api.services.drive.Drive.Builder> driveBuilderMock =
                 org.mockito.Mockito.mockConstruction(com.google.api.services.drive.Drive.Builder.class,
                     (mock, context) -> {
                         when(mock.setApplicationName(anyString())).thenReturn(mock);
                         when(mock.build()).thenReturn(driveService);
                     })) {

            credStatic.when(com.google.auth.oauth2.GoogleCredentials::getApplicationDefault)
                .thenReturn(credentials);
            transportStatic.when(com.google.api.client.googleapis.javanet.GoogleNetHttpTransport::newTrustedTransport)
                .thenReturn(httpTransport);

            GoogleDriveCloudStorageClient result = GoogleDriveCloudStorageClient.builder()
                .folderId("my-folder-id")
                .build();

            assertNotNull(result);
            assertTrue(result.getDescription().contains("my-folder-id"));
        }
    }

    @Test
    @DisplayName("Should build with custom applicationName")
    void testBuilderWithApplicationName() throws Exception {
        com.google.auth.oauth2.GoogleCredentials credentials = mock(com.google.auth.oauth2.GoogleCredentials.class);
        when(credentials.createScoped(anyList())).thenReturn(credentials);
        com.google.api.client.http.javanet.NetHttpTransport httpTransport = mock(com.google.api.client.http.javanet.NetHttpTransport.class);

        com.google.api.services.drive.Drive.Builder[] capturedBuilder = new com.google.api.services.drive.Drive.Builder[1];

        try (org.mockito.MockedStatic<com.google.auth.oauth2.GoogleCredentials> credStatic =
                 org.mockito.Mockito.mockStatic(com.google.auth.oauth2.GoogleCredentials.class);
             org.mockito.MockedStatic<com.google.api.client.googleapis.javanet.GoogleNetHttpTransport> transportStatic =
                 org.mockito.Mockito.mockStatic(com.google.api.client.googleapis.javanet.GoogleNetHttpTransport.class);
             org.mockito.MockedConstruction<com.google.api.services.drive.Drive.Builder> driveBuilderMock =
                 org.mockito.Mockito.mockConstruction(com.google.api.services.drive.Drive.Builder.class,
                     (mock, context) -> {
                         capturedBuilder[0] = mock;
                         when(mock.setApplicationName(anyString())).thenReturn(mock);
                         when(mock.build()).thenReturn(driveService);
                     })) {

            credStatic.when(com.google.auth.oauth2.GoogleCredentials::getApplicationDefault)
                .thenReturn(credentials);
            transportStatic.when(com.google.api.client.googleapis.javanet.GoogleNetHttpTransport::newTrustedTransport)
                .thenReturn(httpTransport);

            GoogleDriveCloudStorageClient result = GoogleDriveCloudStorageClient.builder()
                .applicationName("MyCustomApp")
                .build();

            assertNotNull(result);
            verify(capturedBuilder[0]).setApplicationName("MyCustomApp");
        }
    }

    private void setupFindFileIdMocks(String fileId, String fileName) throws IOException {
        File foundFile = mock(File.class);
        when(foundFile.getId()).thenReturn(fileId);

        when(driveService.files()).thenReturn(files);
        when(files.list()).thenReturn(list);
        when(list.setQ(anyString())).thenReturn(list);
        when(list.setSpaces(anyString())).thenReturn(list);
        when(list.setFields(anyString())).thenReturn(list);
        when(list.execute()).thenReturn(fileList);

        if (fileId != null) {
            when(fileList.getFiles()).thenReturn(Arrays.asList(foundFile));
        } else {
            when(fileList.getFiles()).thenReturn(Collections.emptyList());
        }
    }

    private GoogleDriveCloudStorageClient createTestClient(String folderId) throws Exception {
        java.lang.reflect.Constructor<GoogleDriveCloudStorageClient> constructor =
            GoogleDriveCloudStorageClient.class.getDeclaredConstructor(Drive.class, String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(driveService, folderId);
    }
}
