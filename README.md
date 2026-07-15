# cloudstorage-java

A lightweight Java library that provides a unified, read-only API for accessing files across multiple cloud storage providers. Write your file-reading logic once and swap providers without changing application code.

## Supported Providers

| Provider | Implementation | Required Dependency |
|----------|---------------|-------------------|
| AWS S3 | `S3CloudStorageClient` | `software.amazon.awssdk:s3` |
| Azure Blob Storage | `AzureBlobCloudStorageClient` | `com.azure:azure-storage-blob` |
| Google Cloud Storage | `GcsCloudStorageClient` | `com.google.cloud:google-cloud-storage` |
| Google Drive | `GoogleDriveCloudStorageClient` | `com.google.apis:google-api-services-drive` |
| Dropbox | `DropboxCloudStorageClient` | `com.dropbox.core:dropbox-core-sdk` |
| OneDrive | `OneDriveCloudStorageClient` | *(none -- uses Microsoft Graph REST API directly)* |

All provider dependencies are declared as `<optional>` in the POM. You only need to include the dependency for the provider(s) you use.

## Installation

Add the library to your `pom.xml`:

```xml
<repository>
    <id>packagecloud-flossware</id>
    <url>https://packagecloud.io/flossware/java/maven2/</url>
</repository>
```

```xml
<dependency>
    <groupId>org.flossware</groupId>
    <artifactId>cloudstorage-java</artifactId>
    <version>1.0</version>
</dependency>
```

Then add the optional dependency for your chosen provider. For example, for AWS S3:

```xml
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>s3</artifactId>
    <version>2.44.12</version>
</dependency>
```

## API Overview

All providers implement the `CloudStorageClient` interface:

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

## Quick Start

### AWS S3

```java
try (CloudStorageClient client = S3CloudStorageClient.builder()
        .bucket("my-bucket")
        .region(Region.US_EAST_1)
        .prefix("data/")                         // optional key prefix
        .credentials("accessKey", "secretKey")   // or use default credentials
        .build()) {

    byte[] data = client.readFile("report.csv");
    List<String> files = client.list("reports/");
}
```

Authentication options:
- `.credentials("accessKeyId", "secretAccessKey")` -- static credentials
- `.credentialsProvider(provider)` -- custom `AwsCredentialsProvider`
- *(omit both)* -- uses `DefaultCredentialsProvider` (IAM roles, env vars, etc.)

### Azure Blob Storage

```java
try (CloudStorageClient client = AzureBlobCloudStorageClient.builder()
        .connectionString("DefaultEndpointsProtocol=https;AccountName=...")
        .container("my-container")
        .prefix("uploads/")                      // optional blob prefix
        .build()) {

    boolean found = client.exists("image.png");
    long size = client.getFileSize("image.png");
}
```

Authentication options:
- `.connectionString(connStr)` -- Azure connection string
- `.accountName(name).accountKey(key)` -- shared key credentials (optionally with `.endpoint(url)`)

### Google Cloud Storage

```java
try (CloudStorageClient client = GcsCloudStorageClient.builder()
        .bucket("my-gcs-bucket")
        .projectId("my-project")                 // optional
        .prefix("archive/")                      // optional
        .build()) {

    try (InputStream in = client.openFile("document.pdf")) {
        // stream the file
    }
}
```

Authentication options:
- `.projectId("project")` -- uses application default credentials with the given project
- `.storage(storageInstance)` -- inject a pre-configured `Storage` client
- *(omit both)* -- uses `StorageOptions.getDefaultInstance()`

### Google Drive

```java
try (CloudStorageClient client = GoogleDriveCloudStorageClient.builder()
        .credentialsFromStream(new FileInputStream("service-account.json"))
        .folderId("1A2B3C...")                   // optional root folder
        .applicationName("MyApp")                // optional
        .build()) {

    List<String> files = client.list("");
    byte[] data = client.readFile("spreadsheet.xlsx");
}
```

Authentication options:
- `.credentialsFromStream(inputStream)` -- service account JSON key file
- `.credentials(googleCredentials)` -- pre-built `GoogleCredentials` object
- *(omit both)* -- uses application default credentials

### Dropbox

```java
try (CloudStorageClient client = DropboxCloudStorageClient.builder()
        .accessToken("your-oauth-token")
        .basePath("/project-files")              // optional root path
        .clientIdentifier("MyApp/1.0")           // optional
        .build()) {

    List<String> files = client.list("documents");
    long size = client.getFileSize("notes.txt");
}
```

### OneDrive

```java
try (CloudStorageClient client = OneDriveCloudStorageClient.builder()
        .accessToken("your-oauth-bearer-token")
        .basePath("Documents/work")              // optional
        .driveId("drive-id")                     // optional, defaults to user's drive
        .build()) {

    byte[] data = client.readFile("presentation.pptx");
    boolean exists = client.exists("draft.docx");
}
```

Requires an OAuth access token with `Files.Read.All` permissions. Uses the Microsoft Graph REST API directly -- no additional SDK dependency required.

## Provider-Agnostic Code

The primary benefit of this library is writing storage logic that works across providers:

```java
public class ReportProcessor {
    private final CloudStorageClient storage;

    public ReportProcessor(CloudStorageClient storage) {
        this.storage = storage;
    }

    public void processReports(String folder) throws IOException {
        for (String file : storage.list(folder)) {
            if (file.endsWith(".csv")) {
                byte[] data = storage.readFile(file);
                // process the CSV data
            }
        }
    }
}

// Works with any provider:
new ReportProcessor(s3Client);
new ReportProcessor(azureClient);
new ReportProcessor(gcsClient);
```

## Requirements

- Java 11 or later
- Maven 3.x for building from source

## Building

```bash
mvn clean verify
```

This runs all tests and enforces code coverage thresholds (91% instruction, 90% branch, 91% line).

## License

[GNU General Public License v3.0](LICENSE)

## Contributing

Issues and pull requests are welcome at [github.com/FlossWare/cloudstorage-java](https://github.com/FlossWare/cloudstorage-java/issues).
