package org.flossware.cloud.storage;

import com.dropbox.core.DbxDownloader;
import com.dropbox.core.DbxException;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.DbxUserFilesRequests;
import com.dropbox.core.v2.files.DownloadBuilder;
import com.dropbox.core.v2.files.DownloadErrorException;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.FolderMetadata;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.Metadata;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for DropboxCloudStorageClient to achieve 100% coverage.
 */
class DropboxCloudStorageClientTest {

    @Mock
    private DbxClientV2 client;

    @Mock
    private DbxUserFilesRequests filesRequests;

    @Mock
    private DbxDownloader<FileMetadata> downloader;

    @Mock
    private FileMetadata fileMetadata;

    @Mock
    private FolderMetadata folderMetadata;

    @Mock
    private ListFolderResult listFolderResult;

    private AutoCloseable mocks;
    private DropboxCloudStorageClient storageClient;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (storageClient != null) {
            storageClient.close();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    @DisplayName("Should support builder chaining")
    void testBuilderChaining() {
        DropboxCloudStorageClient.Builder builder = DropboxCloudStorageClient.builder();
        assertSame(builder, builder.accessToken("token"));
        assertSame(builder, builder.basePath("/base"));
        assertSame(builder, builder.clientIdentifier("test-client"));
    }

    @Test
    @DisplayName("Should read file successfully")
    void testReadFileSuccess() throws Exception {
        storageClient = createTestClient("");

        byte[] expectedData = "test-content".getBytes();

        when(client.files()).thenReturn(filesRequests);
        when(filesRequests.download(anyString())).thenReturn(downloader);
        doAnswer(invocation -> {
            ByteArrayOutputStream out = invocation.getArgument(0);
            out.write(expectedData);
            return downloader;
        }).when(downloader).download(any(ByteArrayOutputStream.class));

        byte[] result = storageClient.readFile("path/to/file.txt");

        assertArrayEquals(expectedData, result);
        verify(filesRequests).download("/path/to/file.txt");
    }

    @Test
    @DisplayName("Should read file with base path")
    void testReadFileWithBasePath() throws Exception {
        storageClient = createTestClient("/my-base");

        byte[] expectedData = "data".getBytes();

        when(client.files()).thenReturn(filesRequests);
        when(filesRequests.download(anyString())).thenReturn(downloader);
        when(downloader.download(any(ByteArrayOutputStream.class))).thenAnswer(invocation -> {
            ByteArrayOutputStream out = invocation.getArgument(0);
            out.write(expectedData);
            return downloader;
        });

        byte[] result = storageClient.readFile("file.txt");

        assertArrayEquals(expectedData, result);
        verify(filesRequests).download("/my-base/file.txt");
    }

    @Test
    @DisplayName("Should throw IOException on read failure")
    void testReadFileFailure() throws Exception {
        storageClient = createTestClient("");

        when(client.files()).thenReturn(filesRequests);
        when(filesRequests.download(anyString())).thenReturn(downloader);
        when(downloader.download(any(ByteArrayOutputStream.class))).thenThrow(new DbxException("Dropbox error"));

        IOException exception = assertThrows(IOException.class,
            () -> storageClient.readFile("file.txt"));

        assertTrue(exception.getMessage().contains("Failed to read file from Dropbox"));
    }

    @Test
    @DisplayName("Should open file successfully")
    void testOpenFileSuccess() throws Exception {
        storageClient = createTestClient("");

        InputStream mockStream = new ByteArrayInputStream("data".getBytes());

        when(client.files()).thenReturn(filesRequests);
        when(filesRequests.download(anyString())).thenReturn(downloader);
        when(downloader.getInputStream()).thenReturn(mockStream);
        when(downloader.getInputStream()).thenReturn(mockStream);

        InputStream result = storageClient.openFile("file.txt");

        assertSame(mockStream, result);
    }

    @Test
    @DisplayName("Should throw IOException on open file failure")
    void testOpenFileFailure() throws Exception {
        storageClient = createTestClient("");

        when(client.files()).thenReturn(filesRequests);
        when(filesRequests.download(anyString())).thenThrow(new DbxException("Dropbox error"));

        IOException exception = assertThrows(IOException.class,
            () -> storageClient.openFile("file.txt"));

        assertTrue(exception.getMessage().contains("Failed to open file from Dropbox"));
    }

    @Test
    @DisplayName("Should check exists returns true for FileMetadata")
    void testExistsTrue() throws Exception {
        storageClient = createTestClient("");

        when(client.files()).thenReturn(filesRequests);
        when(filesRequests.getMetadata(anyString())).thenReturn(fileMetadata);

        assertTrue(storageClient.exists("file.txt"));
    }

    @Test
    @DisplayName("Should check exists returns false for FolderMetadata")
    void testExistsFalseForFolder() throws Exception {
        storageClient = createTestClient("");

        when(client.files()).thenReturn(filesRequests);
        when(filesRequests.getMetadata(anyString())).thenReturn(folderMetadata);

        assertFalse(storageClient.exists("folder"));
    }

    @Test
    @DisplayName("Should check exists returns false on exception")
    void testExistsFalseOnException() throws Exception {
        storageClient = createTestClient("");

        when(client.files()).thenReturn(filesRequests);
        when(filesRequests.getMetadata(anyString())).thenThrow(new DbxException("Not found"));

        assertFalse(storageClient.exists("missing.txt"));
    }

    @Test
    @DisplayName("Should list files successfully")
    void testListSuccess() throws Exception {
        storageClient = createTestClient("");

        FileMetadata file1 = mock(FileMetadata.class);
        FileMetadata file2 = mock(FileMetadata.class);
        when(file1.getPathLower()).thenReturn("/file1.txt");
        when(file2.getPathLower()).thenReturn("/file2.txt");

        when(client.files()).thenReturn(filesRequests);
        when(filesRequests.listFolder(anyString())).thenReturn(listFolderResult);
        when(listFolderResult.getEntries()).thenReturn(Arrays.asList(file1, file2));
        when(listFolderResult.getHasMore()).thenReturn(false);

        List<String> files = storageClient.list("prefix");

        assertEquals(2, files.size());
        assertTrue(files.contains("file1.txt"));
        assertTrue(files.contains("file2.txt"));
    }

    @Test
    @DisplayName("Should list files with pagination")
    void testListWithPagination() throws Exception {
        storageClient = createTestClient("");

        FileMetadata file1 = mock(FileMetadata.class);
        FileMetadata file2 = mock(FileMetadata.class);
        when(file1.getPathLower()).thenReturn("/file1.txt");
        when(file2.getPathLower()).thenReturn("/file2.txt");

        ListFolderResult page2 = mock(ListFolderResult.class);

        when(client.files()).thenReturn(filesRequests);
        when(filesRequests.listFolder(anyString())).thenReturn(listFolderResult);
        when(listFolderResult.getEntries()).thenReturn(Arrays.asList(file1));
        when(listFolderResult.getHasMore()).thenReturn(true, false);
        when(listFolderResult.getCursor()).thenReturn("cursor123");
        when(filesRequests.listFolderContinue(anyString())).thenReturn(page2);
        when(page2.getEntries()).thenReturn(Arrays.asList(file2));
        when(page2.getHasMore()).thenReturn(false);

        List<String> files = storageClient.list("prefix");

        assertEquals(2, files.size());
        verify(filesRequests).listFolderContinue("cursor123");
    }

    @Test
    @DisplayName("Should list files filtering out folders")
    void testListFiltersFolders() throws Exception {
        storageClient = createTestClient("");

        FileMetadata file1 = mock(FileMetadata.class);
        FolderMetadata folder = mock(FolderMetadata.class);
        when(file1.getPathLower()).thenReturn("/file1.txt");

        when(client.files()).thenReturn(filesRequests);
        when(filesRequests.listFolder(anyString())).thenReturn(listFolderResult);
        when(listFolderResult.getEntries()).thenReturn(Arrays.asList(file1, folder));
        when(listFolderResult.getHasMore()).thenReturn(false);

        List<String> files = storageClient.list("prefix");

        assertEquals(1, files.size());
        assertTrue(files.contains("file1.txt"));
    }

    @Test
    @DisplayName("Should convert root path to empty string for list")
    void testListRootPathConversion() throws Exception {
        storageClient = createTestClient("");

        when(client.files()).thenReturn(filesRequests);
        when(filesRequests.listFolder(anyString())).thenReturn(listFolderResult);
        when(listFolderResult.getEntries()).thenReturn(Arrays.asList());
        when(listFolderResult.getHasMore()).thenReturn(false);

        storageClient.list("");

        verify(filesRequests).listFolder("");
    }

    @Test
    @DisplayName("Should throw IOException on list failure")
    void testListFailure() throws Exception {
        storageClient = createTestClient("");

        when(client.files()).thenReturn(filesRequests);
        when(filesRequests.listFolder(anyString())).thenThrow(new DbxException("Dropbox error"));

        IOException exception = assertThrows(IOException.class,
            () -> storageClient.list("prefix"));

        assertTrue(exception.getMessage().contains("Failed to list files in Dropbox"));
    }

    @Test
    @DisplayName("Should get file size successfully")
    void testGetFileSizeSuccess() throws Exception {
        storageClient = createTestClient("");

        when(client.files()).thenReturn(filesRequests);
        when(filesRequests.getMetadata(anyString())).thenReturn(fileMetadata);
        when(fileMetadata.getSize()).thenReturn(12345L);

        long size = storageClient.getFileSize("file.txt");

        assertEquals(12345L, size);
    }

    @Test
    @DisplayName("Should throw IOException when getting size of folder")
    void testGetFileSizeNotAFile() throws Exception {
        storageClient = createTestClient("");

        when(client.files()).thenReturn(filesRequests);
        when(filesRequests.getMetadata(anyString())).thenReturn(folderMetadata);

        IOException exception = assertThrows(IOException.class,
            () -> storageClient.getFileSize("folder"));

        assertTrue(exception.getMessage().contains("Not a file"));
    }

    @Test
    @DisplayName("Should throw IOException on getFileSize failure")
    void testGetFileSizeFailure() throws Exception {
        storageClient = createTestClient("");

        when(client.files()).thenReturn(filesRequests);
        when(filesRequests.getMetadata(anyString())).thenThrow(new DbxException("Dropbox error"));

        IOException exception = assertThrows(IOException.class,
            () -> storageClient.getFileSize("file.txt"));

        assertTrue(exception.getMessage().contains("Failed to get file size from Dropbox"));
    }

    @Test
    @DisplayName("Should return description")
    void testGetDescription() throws Exception {
        storageClient = createTestClient("/my-base");
        assertTrue(storageClient.getDescription().contains("/my-base"));
    }

    @Test
    @DisplayName("Should close without error")
    void testClose() throws Exception {
        storageClient = createTestClient("");
        assertDoesNotThrow(() -> storageClient.close());
    }

    @Test
    @DisplayName("Should throw NullPointerException when client is null")
    void testConstructorNullClient() throws Exception {
        java.lang.reflect.Constructor<DropboxCloudStorageClient> constructor =
            DropboxCloudStorageClient.class.getDeclaredConstructor(DbxClientV2.class, String.class);
        constructor.setAccessible(true);

        java.lang.reflect.InvocationTargetException exception = assertThrows(
            java.lang.reflect.InvocationTargetException.class,
            () -> constructor.newInstance(null, "base"));

        assertTrue(exception.getCause() instanceof NullPointerException);
        assertTrue(exception.getCause().getMessage().contains("client cannot be null"));
    }

    @Test
    @DisplayName("Should handle null basePath")
    void testConstructorNullBasePath() throws Exception {
        java.lang.reflect.Constructor<DropboxCloudStorageClient> constructor =
            DropboxCloudStorageClient.class.getDeclaredConstructor(DbxClientV2.class, String.class);
        constructor.setAccessible(true);

        DropboxCloudStorageClient testClient = constructor.newInstance(client, null);
        assertTrue(testClient.getDescription().contains("basePath=]"));
    }

    @Test
    @DisplayName("Should build file path with base path having leading slash")
    void testBuildFilePathWithLeadingSlash() throws Exception {
        storageClient = createTestClient("/base-path");

        InputStream mockStream = new ByteArrayInputStream("test".getBytes());

        when(client.files()).thenReturn(filesRequests);
        when(filesRequests.download(anyString())).thenReturn(downloader);
        when(downloader.getInputStream()).thenReturn(mockStream);
        when(downloader.getInputStream()).thenReturn(mockStream);

        storageClient.openFile("file.txt");

        verify(filesRequests).download("/base-path/file.txt");
    }

    @Test
    @DisplayName("Should build file path with base path having trailing slash")
    void testBuildFilePathWithTrailingSlash() throws Exception {
        storageClient = createTestClient("base-path/");

        InputStream mockStream = new ByteArrayInputStream("test".getBytes());

        when(client.files()).thenReturn(filesRequests);
        when(filesRequests.download(anyString())).thenReturn(downloader);
        when(downloader.getInputStream()).thenReturn(mockStream);
        when(downloader.getInputStream()).thenReturn(mockStream);

        storageClient.openFile("file.txt");

        verify(filesRequests).download("/base-path/file.txt");
    }

    @Test
    @DisplayName("Should remove prefix from file path")
    void testRemovePrefixWithBasePath() throws Exception {
        storageClient = createTestClient("/my-prefix");

        FileMetadata file1 = mock(FileMetadata.class);
        when(file1.getPathLower()).thenReturn("/my-prefix/file1.txt");

        when(client.files()).thenReturn(filesRequests);
        when(filesRequests.listFolder(anyString())).thenReturn(listFolderResult);
        when(listFolderResult.getEntries()).thenReturn(Arrays.asList(file1));
        when(listFolderResult.getHasMore()).thenReturn(false);

        List<String> files = storageClient.list("");

        assertEquals(1, files.size());
        assertTrue(files.contains("file1.txt"));
    }

    private DropboxCloudStorageClient createTestClient(String basePath) throws Exception {
        java.lang.reflect.Constructor<DropboxCloudStorageClient> constructor =
            DropboxCloudStorageClient.class.getDeclaredConstructor(DbxClientV2.class, String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(client, basePath);
    }
}
