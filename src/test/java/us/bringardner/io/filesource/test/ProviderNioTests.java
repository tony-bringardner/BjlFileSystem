package us.bringardner.io.filesource.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.fileproxy.FileProxy;
import us.bringardner.io.filesource.java.file.FileSourcePath;
import us.bringardner.io.filesource.memory.MemoryFileSourceFactory;

/**
 * java.nio provider behaviour (review section 3, "java.nio provider"),
 * run against both the local (FileProxy) and memory file systems.
 */
public class ProviderNioTests {

	private File tmpDir;

	private Path base(String kind) throws IOException {
		if( kind.equals("memory")) {
			MemoryFileSourceFactory factory = new MemoryFileSourceFactory();
			factory.connect();
			FileSource dir = factory.createFileSource("/nio");
			assertTrue(dir.mkdirs());
			return new FileSourcePath(dir);
		}
		tmpDir = Files.createTempDirectory("nio").toFile();
		return new FileSourcePath(new FileProxy(tmpDir, FileSourceFactory.getDefaultFactory()));
	}

	@AfterEach
	public void cleanup() {
		if( tmpDir != null ) {
			for(File f : tmpDir.listFiles()) {
				f.delete();
			}
			tmpDir.delete();
		}
	}

	private static byte[] utf8(String s) {
		return s.getBytes(StandardCharsets.UTF_8);
	}

	/** newByteChannel was unsupported, so all of these threw. */
	@ParameterizedTest
	@ValueSource(strings = { "local", "memory" })
	public void readAllBytesReadStringAndLines(String kind) throws IOException {
		Path p = base(kind).resolve("text.txt");
		Files.write(p, utf8("one\ntwo\nthree"));
		assertArrayEquals(utf8("one\ntwo\nthree"), Files.readAllBytes(p));
		assertEquals("one\ntwo\nthree", Files.readString(p));
		try (Stream<String> lines = Files.lines(p)) {
			assertEquals(Arrays.asList("one", "two", "three"), lines.collect(Collectors.toList()));
		}
		assertEquals(Arrays.asList("one", "two", "three"), Files.readAllLines(p));
	}

	/** newOutputStream rejected CREATE / TRUNCATE_EXISTING and ignored CREATE_NEW. */
	@ParameterizedTest
	@ValueSource(strings = { "local", "memory" })
	public void outputStreamOptions(String kind) throws IOException {
		Path p = base(kind).resolve("out.txt");
		try (OutputStream out = Files.newOutputStream(p, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
			out.write(utf8("first"));
		}
		assertEquals("first", Files.readString(p));

		assertThrows(FileAlreadyExistsException.class, () -> Files.newOutputStream(p, StandardOpenOption.CREATE_NEW));
		assertThrows(NoSuchFileException.class, () -> Files.newOutputStream(base(kind).resolve("missing.txt"), StandardOpenOption.WRITE));

		Files.write(p, utf8("+more"), StandardOpenOption.APPEND);
		assertEquals("first+more", Files.readString(p));

		try (OutputStream out = Files.newOutputStream(p, StandardOpenOption.WRITE)) {   // no truncate: overwrite in place
			out.write(utf8("FIRST"));
		}
		assertEquals("FIRST+more", Files.readString(p));

		Files.writeString(p, "replaced");
		assertEquals("replaced", Files.readString(p));
	}

	@ParameterizedTest
	@ValueSource(strings = { "local", "memory" })
	public void readWriteChannelSeeksAndTruncates(String kind) throws IOException {
		Path p = base(kind).resolve("chan.bin");
		Files.write(p, utf8("0123456789"));
		try (SeekableByteChannel ch = Files.newByteChannel(p, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
			assertEquals(10, ch.size());
			ch.position(4);
			ch.write(ByteBuffer.wrap(utf8("ab")));
			assertEquals(6, ch.position());
			ByteBuffer buf = ByteBuffer.allocate(10);
			ch.position(2);
			assertEquals(8, ch.read(buf));
			assertEquals("23ab6789", new String(buf.array(), 0, 8, StandardCharsets.UTF_8));
			assertEquals(-1, ch.read(ByteBuffer.allocate(1)));
			ch.truncate(3);
			assertEquals(3, ch.size());
		}
		assertEquals("012", Files.readString(p));
	}

	@ParameterizedTest
	@ValueSource(strings = { "local", "memory" })
	public void readChannelCanSeekBackwards(String kind) throws IOException {
		Path p = base(kind).resolve("seek.txt");
		Files.write(p, utf8("abcdef"));
		try (SeekableByteChannel ch = Files.newByteChannel(p)) {
			ByteBuffer b = ByteBuffer.allocate(2);
			ch.position(4);
			ch.read(b);
			assertEquals("ef", new String(b.array(), StandardCharsets.UTF_8));
			b.clear();
			ch.position(1);
			ch.read(b);
			assertEquals("bc", new String(b.array(), StandardCharsets.UTF_8));
			ch.position(100);
			assertEquals(-1, ch.read(ByteBuffer.allocate(1)));
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "local", "memory" })
	public void deleteOnClose(String kind) throws IOException {
		Path p = base(kind).resolve("temp.txt");
		try (OutputStream out = Files.newOutputStream(p, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.DELETE_ON_CLOSE)) {
			out.write(1);
			assertTrue(Files.exists(p));
		}
		assertFalse(Files.exists(p));
	}

	/** checkAccess threw a plain IOException, so Files.notExists was always false. */
	@ParameterizedTest
	@ValueSource(strings = { "local", "memory" })
	public void existsAndNotExists(String kind) throws IOException {
		Path base = base(kind);
		Path missing = base.resolve("nope");
		assertTrue(Files.notExists(missing));
		assertFalse(Files.exists(missing));
		assertThrows(NoSuchFileException.class, () -> missing.getFileSystem().provider().checkAccess(missing));

		Path p = base.resolve("here.txt");
		Files.write(p, utf8("x"));
		assertTrue(Files.exists(p));
		assertFalse(Files.notExists(p));
		assertTrue(Files.isReadable(p));
		assertTrue(Files.isWritable(p));
	}

	/** getFileAttributeView returned the POSIX view whatever type was asked for. */
	@ParameterizedTest
	@ValueSource(strings = { "local", "memory" })
	public void unsupportedAttributeViewIsNull(String kind) throws IOException {
		Path p = base(kind).resolve("v.txt");
		Files.write(p, utf8("x"));
		assertNull(Files.getFileAttributeView(p, AclFileAttributeView.class));
		assertNotNull(Files.getFileAttributeView(p, BasicFileAttributeView.class));
	}

	/** Attributes were a live view that re-read the file on every call and returned null/0 on errors. */
	@ParameterizedTest
	@ValueSource(strings = { "local", "memory" })
	public void attributesAreASnapshot(String kind) throws IOException {
		Path p = base(kind).resolve("snap.txt");
		Files.write(p, utf8("12345"));
		BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
		PosixFileAttributes posix = Files.readAttributes(p, PosixFileAttributes.class);
		Files.write(p, utf8("123456789"));

		assertEquals(5, attrs.size());
		assertEquals(5, posix.size());
		assertTrue(attrs.isRegularFile());
		assertNotNull(posix.owner());
		assertThrows(NoSuchFileException.class, () -> Files.readAttributes(p.resolveSibling("missing"), BasicFileAttributes.class));
	}

	/** The iterator looped forever if the filter threw, and next() ignored the filter. */
	@ParameterizedTest
	@ValueSource(strings = { "local", "memory" })
	public void directoryStreamIterator(String kind) throws IOException {
		Path base = base(kind);
		for (String n : new String[] { "a.txt", "b.log", "c.txt" }) {
			Files.write(base.resolve(n), utf8(n));
		}

		List<String> names = new ArrayList<>();
		try (DirectoryStream<Path> ds = Files.newDirectoryStream(base, path -> path.getFileName().toString().endsWith(".txt"))) {
			Iterator<Path> it = ds.iterator();
			names.add(it.next().getFileName().toString());   // next() without hasNext()
			names.add(it.next().getFileName().toString());
			assertFalse(it.hasNext());
			assertThrows(NoSuchElementException.class, it::next);
		}
		names.sort(null);
		assertEquals(Arrays.asList("a.txt", "c.txt"), names);

		try (DirectoryStream<Path> ds = Files.newDirectoryStream(base, path -> { throw new IOException("boom"); })) {
			Iterator<Path> it = ds.iterator();
			assertThrows(DirectoryIteratorException.class, it::hasNext);
		}
	}
}
