package us.bringardner.io.filesource.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.IRandomAccessStream;
import us.bringardner.io.filesource.memory.MemoryFileSourceFactory;

/**
 * Regression tests for MemoryFileSource directory and file-creation bugs
 * (review items #4, #5 and #6).
 */
public class MemoryFileSourceTests {

	private MemoryFileSourceFactory factory;

	@BeforeEach
	public void setup() throws IOException {
		factory = new MemoryFileSourceFactory();
		factory.connect();
	}

	private FileSource write(String path, String content) throws IOException {
		FileSource f = factory.createFileSource(path);
		try (OutputStream out = f.getOutputStream()) {
			out.write(content.getBytes("UTF-8"));
		}
		return f;
	}

	private static String read(FileSource f) throws IOException {
		try (InputStream in = f.getInputStream()) {
			return new String(in.readAllBytes(), "UTF-8");
		}
	}

	/** #4: list() returned [z, null, null] because the index was never incremented. */
	@Test
	public void listReturnsAllExistingChildren() throws IOException {
		FileSource dir = factory.createFileSource("/list");
		assertTrue(dir.mkdirs());
		write("/list/x", "1");
		write("/list/y", "2");
		write("/list/z", "3");
		factory.createFileSource("/list/never-created"); // lookup only; must not be listed

		assertArrayEquals(new String[] { "x", "y", "z" }, dir.list());
	}

	/** #5: renaming a directory dropped all of its children. */
	@Test
	public void renameDirectoryKeepsChildren() throws IOException {
		assertTrue(factory.createFileSource("/src/sub").mkdirs());
		write("/src/a.txt", "alpha");
		write("/src/sub/b.txt", "beta");
		FileSource src = factory.createFileSource("/src");
		FileSource dest = factory.createFileSource("/dest");

		assertTrue(src.renameTo(dest));

		assertFalse(src.exists(), "old dir gone");
		assertTrue(dest.isDirectory(), "new dir exists");
		assertArrayEquals(new String[] { "a.txt", "sub" }, dest.list());
		assertEquals("alpha", read(factory.createFileSource("/dest/a.txt")));
		assertEquals("beta", read(factory.createFileSource("/dest/sub/b.txt")));
		assertEquals("/dest/sub/b.txt", factory.createFileSource("/dest/sub/b.txt").getAbsolutePath());
		assertFalse(factory.createFileSource("/src/a.txt").exists(), "old child path gone");
	}

	/** #5: other attributes must survive a rename too. */
	@Test
	public void renameKeepsPermissionsAndTimes() throws IOException {
		FileSource f = write("/perm.txt", "x");
		f.setGroupWritable(false);
		f.setOtherReadable(false);
		f.setCreateTime(1_000_000L);
		FileSource g = factory.createFileSource("/perm2.txt");

		assertTrue(f.renameTo(g));

		assertFalse(g.canGroupWrite());
		assertFalse(g.canOtherRead());
		assertEquals(1_000_000L, g.creationTime());
	}

	/** Moving a directory into itself would create a cycle; it must be refused. */
	@Test
	public void renameIntoOwnSubdirectoryIsRefused() throws IOException {
		assertTrue(factory.createFileSource("/loop").mkdirs());
		FileSource inside = factory.createFileSource("/loop/inner");
		assertFalse(factory.createFileSource("/loop").renameTo(inside));
		assertTrue(factory.createFileSource("/loop").isDirectory());
	}

	/** #6: createNewFile() always returned false. */
	@Test
	public void createNewFileFollowsJavaIoFileContract() throws IOException {
		assertTrue(factory.createFileSource("/cnf").mkdirs());
		FileSource f = factory.createFileSource("/cnf/new.txt");

		assertTrue(f.createNewFile(), "creates a missing file");
		assertTrue(f.isFile());
		assertEquals(0, f.length());
		assertFalse(f.createNewFile(), "false when it already exists");

		FileSource orphan = factory.createFileSource("/no-such-dir/f.txt");
		assertThrows(IOException.class, orphan::createNewFile, "parent must exist");
	}

	/** #6: opening a new file "rw" threw NullPointerException. */
	@Test
	public void randomAccessRwCreatesNewFile() throws Exception {
		assertTrue(factory.createFileSource("/ra").mkdirs());
		FileSource f = factory.createFileSource("/ra/new.bin");

		IRandomAccessStream ra = f.getRandomAccessStream("rw");
		try {
			ra.write(new byte[] { 1, 2, (byte) 0xFF });
			ra.seek(0);
			assertEquals(1, ra.read());
		} finally {
			ra.close();
		}

		assertTrue(f.isFile());
		assertEquals(3, f.length());
	}
	/** #6: writing one byte past the current end must grow the file. */
	@Test
	public void randomAccessAppendAtEof() throws Exception {
		FileSource f = write("/append.bin", "abc");
		IRandomAccessStream ra = f.getRandomAccessStream("rw");
		try {
			ra.seek(3);
			ra.write('d');
		} finally {
			ra.close();
		}
		assertEquals("abcd", read(f));
	}
}
