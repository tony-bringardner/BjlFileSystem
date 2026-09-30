package us.bringardner.io.filesource.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.memory.MemoryFileSourceFactory;

/** FileSourceFactory.createTempFile / createTempDirectory, as java.io.File.createTempFile. */
public class TempFileTests {

	private static FileSourceFactory factory(String type) throws IOException {
		FileSourceFactory f = type.equals("memory") ? new MemoryFileSourceFactory() : FileSourceFactory.getFileSourceFactory(type);
		f.connect();
		return f;
	}

	@ParameterizedTest
	@ValueSource(strings = { "fileproxy", "memory" })
	public void createsUniqueEmptyFiles(String type) throws IOException {
		FileSourceFactory f = factory(type);
		FileSource tmpDir = f.getTempDirectory();
		assertTrue(tmpDir.isDirectory());
		if( type.equals("fileproxy") ) {
			assertEquals(new File(System.getProperty("java.io.tmpdir")).getCanonicalPath(), tmpDir.getCanonicalPath());
		} else {
			assertEquals("/tmp", tmpDir.getAbsolutePath());
		}

		Set<String> names = new HashSet<>();
		try {
			for(int i = 0; i < 50; i++) {
				FileSource t = f.createTempFile("abc", ".dat");
				assertTrue(t.isFile());
				assertEquals(0, t.length());
				assertEquals(tmpDir.getAbsolutePath(), t.getParentFile().getAbsolutePath());
				assertTrue(t.getName().matches("abc\\d+\\.dat"), t.getName());
				assertTrue(names.add(t.getName()), "unique");
			}
			FileSource def = f.createTempFile("xyz", null);
			names.add(def.getName());
			assertTrue(def.getName().endsWith(".tmp"), "null suffix means .tmp");
		} finally {
			for(String n : names) {
				tmpDir.getChild(n).delete();
			}
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "fileproxy", "memory" })
	public void inAGivenDirectory(String type) throws IOException {
		FileSourceFactory f = factory(type);
		FileSource dir = f.createTempDirectory("tdir");
		try {
			assertTrue(dir.isDirectory());
			assertTrue(dir.getName().startsWith("tdir"));
			FileSource t = f.createTempFile("pre", "", dir);
			assertEquals(dir.getAbsolutePath(), t.getParentFile().getAbsolutePath());
			assertTrue(t.getName().matches("pre\\d+"), t.getName());
			FileSource sub = f.createTempDirectory(null, dir);
			assertTrue(sub.isDirectory());
			assertTrue(sub.getName().matches("\\d+"), sub.getName());
			assertTrue(sub.delete());
			assertTrue(t.delete());
		} finally {
			dir.delete();
		}
		assertFalse(dir.exists());
	}

	@ParameterizedTest
	@ValueSource(strings = { "fileproxy", "memory" })
	public void errors(String type) throws IOException {
		FileSourceFactory f = factory(type);
		assertThrows(IllegalArgumentException.class, () -> f.createTempFile("ab", ".x"));
		assertThrows(IllegalArgumentException.class, () -> f.createTempFile(null, ".x"));
		assertThrows(IOException.class, () -> f.createTempFile("abc/def", ".x"));
		assertThrows(IOException.class, () -> f.createTempFile("abc", "/.x"));
		FileSource notADir = f.createTempFile("file", ".x");
		try {
			assertThrows(IOException.class, () -> f.createTempFile("abc", ".x", notADir));
			FileSource missing = notADir.getParentFile().getChild("no-such-dir-" + System.nanoTime());
			assertThrows(IOException.class, () -> f.createTempFile("abc", ".x", missing));
			MemoryFileSourceFactory other = new MemoryFileSourceFactory();
			other.connect();
			FileSource otherDir = other.getTempDirectory();
			assertThrows(IllegalArgumentException.class, () -> f.createTempFile("abc", ".x", otherDir));
		} finally {
			notADir.delete();
		}
	}

	/** The usual pattern: a temp file that removes itself. */
	@ParameterizedTest
	@ValueSource(strings = { "memory" })
	public void withDeleteOnExit(String type) throws IOException {
		DeleteOnExitTests.KeepingFactory f = new DeleteOnExitTests.KeepingFactory();
		f.connect();
		FileSource t = f.createTempFile("scratch", ".bin");
		t.deleteOnExit();
		assertTrue(t.exists());
		f.disConnect();
		assertFalse(f.createFileSource(t.getAbsolutePath()).exists());
	}
}
