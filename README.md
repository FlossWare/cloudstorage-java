# JCloudStorage

Universal cloud storage abstraction library for Java. Provides a simple, unified API for reading files from AWS S3, Azure Blob Storage, Google Cloud Storage, Google Drive, Dropbox, and OneDrive.

## Features

- ✅ **Unified API** - Single interface for all cloud providers
- ✅ **6 Cloud Providers** - AWS S3, Azure Blob, GCS, Google Drive, Dropbox, OneDrive
- ✅ **Builder Pattern** - Fluent, type-safe configuration
- ✅ **Optional Dependencies** - Include only the providers you need
- ✅ **Thread-Safe** - Concurrent read operations supported
- ✅ **AutoCloseable** - Proper resource management
- ✅ **Minimal Dependencies** - Java 11+, provider SDKs are optional

## Quick Start

### Maven Dependency

```xml
<dependency>
    <groupId>org.flossware</groupId>
    <artifactId>jcloudstorage</artifactId>
    <version>1.0</version>
</dependency>

<!-- Add provider SDK (only include what you need) -->
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>s3</artifactId>
    <version>2.44.12</version>
</dependency>
```

### Basic Usage

```java
import org.flossware.cloud.storage.CloudStorageClient;
import org.flossware.cloud.storage.S3CloudStorageClient;
import software.amazon.awssdk.regions.Region;

// Create S3 client
CloudStorageClient client = S3CloudStorageClient.builder()
    .bucket("my-bucket")
    .region(Region.US_EAST_1)
    .build();

// Read a file
byte[] data = client.readFile("path/to/file.txt");

// Check if file exists
if (client.exists("path/to/file.txt")) {
    System.out.println("File exists!");
}

// List files with prefix
List<String> files = client.list("path/to/directory/");

// Clean up
client.close();
```

## Supported Providers

### AWS S3

```java
CloudStorageClient s3 = S3CloudStorageClient.builder()
    .bucket("my-bucket")
    .region(Region.US_WEST_2)
    .prefix("optional/prefix/")
    .credentials("access-key", "secret-key")  // Optional (uses IAM by default)
    .build();
```

## API Reference

```java
public interface CloudStorageClient extends AutoCloseable {
    byte[] readFile(String path) throws IOException;
    InputStream openFile(String path) throws IOException;
    boolean exists(String path) throws IOException;
    List<String> list(String prefix) throws IOException;
    long getFileSize(String path) throws IOException;
    String getDescription();
    void close() throws IOException;
}
```

## Versioning and Releases

This project uses **X.Y semantic versioning** (e.g., 1.0, 1.1, 2.0). Versions are automatically incremented on commits to the main branch and published to packagecloud.io.

### Maven Repository

```xml
<repositories>
    <repository>
        <id>packagecloud-flossware</id>
        <url>https://packagecloud.io/flossware/java/maven2</url>
    </repository>
</repositories>
```

## Building from Source

```bash
git clone https://github.com/FlossWare/jcloudstorage.git
cd jcloudstorage
mvn clean install
```

## License

Apache License 2.0

## Related Projects

- [jclassloader](https://github.com/FlossWare/jclassloader) - Dynamic class loading using cloud-storage-client
