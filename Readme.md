# BjlFileSystem

**FileSource** is an interface that looks very much like `java.io.File`, but lets many implementations (local disk, in-memory, FTP, SFTP, a database, …) coexist in one program. Application code works with `FileSource` and doesn't need to know at compile time which kind of file system it's talking to.

The syntax deliberately stays close to `java.io.File`, so moving code between the two takes little effort.

- One API for local files, in-memory files and remote file systems
- Implementations are discovered at runtime with `ServiceLoader` and chosen by a short type id
- Files can be addressed by URL (`filesource:/path?sourcetype=memory`)
- Random access (`IRandomAccessStream`) and seekable input streams
- A `java.nio.file` provider, so `Files.readString`, `Files.newDirectoryStream(dir, "*.txt")` and friends work on any FileSource

## Requirements

- Java 11 or later
- [BjlCore](https://github.com/tony-bringardner/BjlCore) and [BjlIo](https://github.com/tony-bringardner/BjlIo) (pulled in automatically by Maven)

## Installation

The artifacts are published to GitHub Packages:

```xml
<dependency>
    <groupId>us.bringardner</groupId>
    <artifactId>bjl_file_system</artifactId>
    <version>0.1.3</version>
</dependency>
```

Add the package repositories to your `pom.xml` (BjlCore and BjlIo are published from their own repositories):

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/tony-bringardner/BjlFileSystem</url>
    </repository>
    <repository>
        <id>github-bjlcore</id>
        <url>https://maven.pkg.github.com/tony-bringardner/BjlCore</url>
    </repository>
    <repository>
        <id>github-bjlio</id>
        <url>https://maven.pkg.github.com/tony-bringardner/BjlIo</url>
    </repository>
</repositories>
```

GitHub Packages requires authentication even for public packages. Create a personal access token with the `read:packages` scope, and add a `<server>` entry for each repository id to `~/.m2/settings.xml`:

```xml
<servers>
    <server>
        <id>github</id>
        <username>YOUR_GITHUB_USERNAME</username>
        <password>YOUR_TOKEN</password>
    </server>
    <!-- repeat for github-bjlcore and github-bjlio -->
</servers>
```

## Quick start

```java
FileSourceFactory factory = FileSourceFactory.getDefaultFactory();   // local files

FileSource dir = factory.createFileSource("/tmp/example");
dir.mkdirs();

FileSource file = dir.getChild("hello.txt");
try (OutputStream out = file.getOutputStream()) {
    out.write("Hello, FileSource".getBytes(StandardCharsets.UTF_8));
}

for (FileSource f : dir.listFiles()) {
    System.out.println(f.getName() + "  " + f.length() + " bytes");
}

try (InputStream in = file.getInputStream()) {
    System.out.println(new String(in.readAllBytes(), StandardCharsets.UTF_8));
}
```

All `FileSource` objects are created by a `FileSourceFactory`. Everything else (`getChild`, `getParentFile`, `listFiles`, `exists`, `mkdirs`, `delete`, `renameTo`, permissions, times, …) works much as it does on `java.io.File`.

## Implementations

Built in:

| Type id | Class | What it is |
|---|---|---|
| `fileproxy` | `FileProxyFactory` / `FileProxy` | Local files, backed by `java.io.File` and `java.nio.file`. The default. |
| `memory` | `MemoryFileSourceFactory` / `MemoryFileSource` | A virtual file system held in memory. Handy for tests. Thread-safe. |

Separate projects, which plug in the same way once they're on the classpath:

| Project | Artifact | What it is |
|---|---|---|
| [BjlFileSystemFtp](https://github.com/tony-bringardner/BjlFileSystemFtp) | `bjl_file_system_ftp` | FTP servers |
| [BjlFileSystemSftp](https://github.com/tony-bringardner/BjlFileSystemSftp) | `bjl_file_system_sftp` | SSH/SFTP servers |
| [BjlFileSystemJdbc](https://github.com/tony-bringardner/BjlFileSystemJdbc) | `bjl_file_system_jdbc` | A file system stored in a database, via JDBC |

Each is published to GitHub Packages from its own repository, so add a matching `<repository>` (and `<server>` entry) as in [Installation](#installation).

### Choosing an implementation at runtime

```java
FileSourceFactory memory = FileSourceFactory.getFileSourceFactory("memory");
memory.connect();
FileSource scratch = memory.createFileSource("/scratch/data.bin");

String[] available = FileSourceFactory.getRegisterdFactories();   // e.g. [fileproxy, memory]
```

Remote factories take their connection settings (host, user, …) through `setConnectionProperties(...)` or `connect(Properties)`; `getConnectProperties()` lists what a factory needs.

To change the default factory for the whole program, set the system property `-DFileSource.default=<type id>`, or call `FileSourceFactory.setDefaultFactory(...)`.

## URLs

Any FileSource can be addressed by a `filesource:` URL. The `sourcetype` parameter names the factory:

```java
FileSource f = FileSourceFactory.getFileSource("filesource:/tmp/example/hello.txt?sourcetype=fileproxy");

URL url = scratch.toURL();   // e.g. filesource:/scratch/data.bin?sourcetype=memory&sessionId=0
try (InputStream in = url.openStream()) {
    ...
}
```

The `sessionId` parameter ties a URL to an existing connected factory, so a memory file's URL resolves to the same in-memory file system. Loading the library registers the URL handler. If your application server (or another library) has already installed a `URLStreamHandlerFactory`, the handler is registered through `java.protocol.handler.pkgs` instead.

## Random access

```java
IRandomAccessStream ra = file.getRandomAccessStream("rw");
try {
    ra.seek(7);
    ra.write("filesource".getBytes(StandardCharsets.UTF_8));
} finally {
    ra.close();
}
```

`IRandomAccessStream` has the same methods as `java.io.RandomAccessFile` (`seek`, `read`, `readFully`, `readInt`, `writeUTF`, `setLength`, …). For read-only access with seeking, use `getSeekableInputStream()`.

## java.nio.file support

`FileSourcePath` adapts any FileSource to a `java.nio.file.Path`. The provider is registered for the `filesource` scheme, so `Paths.get(URI)` works too:

```java
Path path = new FileSourcePath(file);
String text = Files.readString(path);
Files.writeString(path.resolveSibling("copy.txt"), text);

try (DirectoryStream<Path> ds = Files.newDirectoryStream(new FileSourcePath(dir), "*.txt")) {
    for (Path p : ds) {
        System.out.println(p.getFileName());
    }
}

Path fromUri = Paths.get(new URI("filesource:/tmp/example/hello.txt?sourcetype=fileproxy"));
```

Supported: streams and byte channels (`readAllBytes`, `readString`, `lines`, `write`, `newByteChannel`), the standard open options, `copy`, `move`, `delete`, `createDirectory`, `exists`/`notExists`/`isReadable`, basic and POSIX attributes, directory streams, and `glob:`/`regex:` path matchers. Not supported: `FileChannel` and `AsynchronousFileChannel`, watch services, and listing file stores.

## Writing your own implementation

1. Implement `FileSource` for your storage, and extend `FileSourceFactory`. The factory's `getTypeId()` is the id used in URLs and `getFileSourceFactory(...)`. The factory needs a public no-argument constructor.
2. If you support random access, return a `FileSourceRandomAccessStream` from `getRandomAccessStream(mode)`. For remote storage, `AbstractRandomAccessIoController` handles chunked reads and writes for you.
3. Register the factory in `META-INF/services/us.bringardner.io.filesource.FileSourceFactory` (see [`resources/META-INF/services`](resources/META-INF/services) in this project):

   ```
   com.example.MyFileSourceFactory
   ```

The unit tests in `src/test/java`, starting with `AbstractTestClass`, are the best worked examples of what an implementation has to support.

## Behaviour worth knowing

- `renameTo` never replaces an existing file: it returns `false` if the destination exists or belongs to another file system, and throws the underlying `IOException` for other failures. Use `Files.move(..., REPLACE_EXISTING)` to replace.
- In both built-in implementations, reading a file that doesn't exist throws `FileNotFoundException`, like `java.io`.
- `FileSource` objects are `Serializable` (both built-in implementations support it).

## Building and testing

```
mvn test
```

The tests use JUnit 5. A few only run on Windows, or when your user belongs to more than one group, and are skipped otherwise.

## Why not just java.nio.file?

I was very excited when `java.nio.file.FileSystem` arrived in Java SE 7 (July 2011). But in my opinion its API is overly complex and nowhere near as simple as `java.io.File`, so it falls short of what I'd consider the minimal requirements. FileSource keeps the `java.io.File` style and still gives you a `java.nio.file` provider when you need one.

## License

Apache License 2.0. See the headers in the source files.
