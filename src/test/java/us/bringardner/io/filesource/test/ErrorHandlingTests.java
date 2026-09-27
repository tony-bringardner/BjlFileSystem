package us.bringardner.io.filesource.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.AccessDeniedException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.memory.MemoryFileSource;
import us.bringardner.io.filesource.memory.MemoryFileSourceFactory;

/**
 * Review section 3, "Error handling": exceptions that were swallowed,
 * turned into java.lang.Error, or wrapped.
 */
public class ErrorHandlingTests {

	private MemoryFileSourceFactory factory;

	@BeforeEach
	public void setup() throws IOException {
		factory = new MemoryFileSourceFactory();
		factory.connect();
		assertTrue(factory.createFileSource("/d").mkdirs());
	}

	private FileSource write(String path, String content) throws IOException {
		FileSource f = factory.createFileSource(path);
		try (OutputStream out = f.getOutputStream()) {
			out.write(content.getBytes("UTF-8"));
		}
		return f;
	}

	/** Reading a missing memory file returned an empty stream (and getInputStream(long) created the file). */
	@Test
	public void readingMissingMemoryFileThrows() throws IOException {
		FileSource f = factory.createFileSource("/d/missing.txt");
		assertThrows(FileNotFoundException.class, f::getInputStream);
		assertThrows(FileNotFoundException.class, () -> f.getInputStream(0));
		assertThrows(FileNotFoundException.class, f::getSeekableInputStream);
		assertFalse(f.exists(), "reading must not create the file");
	}

	@Test
	public void getInputStreamFromPosition() throws IOException {
		FileSource f = write("/d/pos.txt", "abcdef");
		try (InputStream in = f.getInputStream(4)) {
			assertEquals("ef", new String(in.readAllBytes(), "UTF-8"));
		}
		try (InputStream in = f.getInputStream(100)) {
			assertEquals(-1, in.read());
		}
	}

	/** Permission problems threw IllegalAccessError, which catch (Exception) doesn't catch. */
	@Test
	public void permissionDeniedIsAnIOException() throws IOException {
		FileSource f = write("/d/secret.txt", "x");
		f.setOwnerReadable(false);
		assertThrows(FileNotFoundException.class, f::getInputStream);

		FileSource ro = write("/d/readonly.txt", "x");
		ro.setOwnerWritable(false);
		assertThrows(FileNotFoundException.class, ro::getOutputStream);
		assertThrows(FileNotFoundException.class, () -> ro.getOutputStream(true));

		FileSource dir = factory.createFileSource("/d");
		dir.setOwnerReadable(false);
		assertThrows(AccessDeniedException.class, dir::listFiles);
	}

	/** The link proxy rethrew the target's IOException as UndeclaredThrowableException. */
	@Test
	public void linkProxyRethrowsTheRealException() throws IOException {
		FileSource target = write("/d/target.txt", "x");
		FileSource link = factory.createSymbolicLink(factory.createFileSource("/d/link"), target);
		assertTrue(target.delete());
		assertThrows(FileNotFoundException.class, link::getInputStream);
	}

	/** canRead() and friends returned false for any exception, hiding I/O errors. */
	@Test
	public void canReadPropagatesIOException() {
		FileSource broken = new MemoryFileSource(null, "broken", factory) {
			private static final long serialVersionUID = 1L;

			@Override
			public us.bringardner.io.filesource.FileSourceUser getOwner() throws IOException {
				throw new IOException("owner lookup failed");
			}
		};
		IOException e = assertThrows(IOException.class, broken::canRead);
		assertEquals("owner lookup failed", e.getMessage());
	}

	/** The URLConnection swallowed connect errors, so reading failed later with a NullPointerException. */
	@Test
	public void urlConnectionReportsTheRealError() throws Exception {
		URL url = new URL(FileSourceFactory.FILE_SOURCE_PROTOCOL+":/no/such/file-"+System.nanoTime()
				+"?"+FileSourceFactory.QUERY_STRING_SOURCE_TYPE+"=fileproxy");
		URLConnection c = url.openConnection();
		assertThrows(FileNotFoundException.class, c::getInputStream);

		URL bad = new URL(FileSourceFactory.FILE_SOURCE_PROTOCOL+":/x?"+FileSourceFactory.QUERY_STRING_SOURCE_TYPE+"=no-such-type");
		assertThrows(java.net.MalformedURLException.class, () -> bad.openConnection().getInputStream());
		URLConnection unresolved = bad.openConnection();
		assertEquals(-1, unresolved.getContentLength(), "unknown length");
		assertEquals(0, unresolved.getLastModified(), "unknown date");
	}

}
