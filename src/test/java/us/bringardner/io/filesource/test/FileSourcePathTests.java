package us.bringardner.io.filesource.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.java.file.FileSourcePath;
import us.bringardner.io.filesource.memory.MemoryFileSourceFactory;

/**
 * FileSourcePath (review section 3, "java.nio provider": isAbsolute,
 * normalize, getParent, toUri) and FileSourceFileSystem.getSeparator.
 */
public class FileSourcePathTests {

	private static final MemoryFileSourceFactory MEM = new MemoryFileSourceFactory();

	private static Path mem(String p) {
		return new FileSourcePath(p, MEM);
	}

	/** isAbsolute() compared against ':' (the path-list separator) instead of '/'. */
	@Test
	public void isAbsoluteAndGetRoot() {
		assertTrue(mem("/a/b").isAbsolute());
		assertTrue(mem("/").isAbsolute());
		assertFalse(mem("a/b").isAbsolute());
		assertEquals("/", mem("/a/b").getRoot().toString());
		assertNull(mem("a/b").getRoot(), "relative paths have no root");
		assertTrue(mem("C:/x").isAbsolute(), "drive letter");
		assertFalse(mem("c").isAbsolute(), "old pattern matched a bare letter");
	}

	/** getParent() returned the path itself for "/" and "/name". */
	@Test
	public void getParentEndsWithNull() {
		assertEquals("/a", mem("/a/b").getParent().toString());
		assertEquals("/", mem("/a").getParent().toString());
		assertNull(mem("/").getParent());
		assertEquals("a", mem("a/b").getParent().toString());
		assertNull(mem("a").getParent());
		assertEquals("/a", mem("/a/b/").getParent().toString(), "trailing separator ignored");

		int steps = 0;
		for (Path p = mem("/one/two/three/four"); p != null; p = p.getParent()) {
			assertTrue(++steps < 10, "walking up must terminate");
		}
		assertEquals(5, steps);
	}

	/** normalize() changed the path it was called on. */
	@Test
	public void normalizeReturnsNewPath() {
		Path p = mem("/a/./b/../c");
		Path n = p.normalize();
		assertEquals("/a/./b/../c", p.toString());
		assertEquals("/a/c", n.toString());
	}

	/** A space in the path made toUri() return null. */
	@Test
	public void toUriQuotesAndRoundTrips() throws Exception {
		assumeFalse(FileSourceFactory.isWindows(), "uses a POSIX temp path");
		File dir = Files.createTempDirectory("with space").toFile();
		dir.deleteOnExit();
		Path p = new FileSourcePath(dir.getAbsolutePath(), FileSourceFactory.getDefaultFactory());
		URI uri = p.toUri();
		assertNotNull(uri);
		assertTrue(uri.toString().contains("with%20space"), uri.toString());
		assertEquals(p.toString(), new FileSourcePath(uri).toString());
	}

	/** A memory path's URI must lead back to the same in-memory file system. */
	@Test
	public void memoryUriResolvesToSameFileSystem() throws Exception {
		MemoryFileSourceFactory factory = new MemoryFileSourceFactory();
		factory.connect();
		FileSource f = factory.createFileSource("/u.txt");
		try (OutputStream out = f.getOutputStream()) {
			out.write(new byte[] { 1, 2, 3 });
		}
		URI uri = new FileSourcePath(f).toUri();
		FileSourcePath back = new FileSourcePath(uri);
		assertEquals(3, back.getFileSource().length(), uri.toString());
	}

	/** getSeparator() returned the path-list separator. */
	@Test
	public void separatorIsTheNameSeparator() throws IOException {
		assertEquals("/", mem("/x").getFileSystem().getSeparator());
		assertEquals(File.separator, new FileSourcePath("/x", FileSourceFactory.getDefaultFactory()).getFileSystem().getSeparator());
	}
	@Test
	public void relativizeOnlyStripsRealAncestors() {
		assertEquals("c/d", mem("/a/b").relativize(mem("/a/b/c/d")).toString());
		assertEquals("", mem("/a/b").relativize(mem("/a/b")).toString());
		assertEquals("/a/bc", mem("/a/b").relativize(mem("/a/bc")).toString(), "'/a/bc' is not under '/a/b'");
	}
}
