package us.bringardner.io.filesource.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.fileproxy.FileProxy;
import us.bringardner.io.filesource.java.file.FileSourcePath;

/**
 * Regression tests for Files.move / Files.copy through the FileSource
 * java.nio provider (review items #10 and #11).
 */
public class ProviderMoveCopyTests {

	private File dir;
	private Path base;

	@BeforeEach
	public void setup() throws IOException {
		dir = Files.createTempDirectory("movecopy").toFile();
		base = new FileSourcePath(new FileProxy(dir, FileSourceFactory.getDefaultFactory()));
	}

	@AfterEach
	public void teardown() {
		deleteTree(dir);
	}

	private static void deleteTree(File f) {
		File[] kids = f.listFiles();
		if (kids != null) {
			for (File k : kids) {
				deleteTree(k);
			}
		}
		f.delete();
	}

	private Path write(String name, String content) throws IOException {
		Files.write(new File(dir, name).toPath(), content.getBytes("UTF-8"));
		return base.resolve(name);
	}

	private String read(String name) throws IOException {
		return new String(Files.readAllBytes(new File(dir, name).toPath()), "UTF-8");
	}

	/** #10: Files.move always threw ClassCastException. */
	@Test
	public void moveWithoutOptions() throws IOException {
		Path a = write("a.txt", "alpha");
		Files.move(a, base.resolve("b.txt"));
		assertFalse(new File(dir, "a.txt").exists());
		assertEquals("alpha", read("b.txt"));
	}

	@Test
	public void moveOntoExistingFileNeedsReplaceExisting() throws IOException {
		Path a = write("a.txt", "alpha");
		Path b = write("b.txt", "beta");
		assertThrows(FileAlreadyExistsException.class, () -> Files.move(a, b));
		assertEquals("beta", read("b.txt"), "target untouched");

		Files.move(a, b, StandardCopyOption.REPLACE_EXISTING);
		assertEquals("alpha", read("b.txt"));
		assertFalse(new File(dir, "a.txt").exists());
	}

	@Test
	public void moveMissingSourceThrowsNoSuchFile() {
		assertThrows(NoSuchFileException.class, () -> Files.move(base.resolve("nope"), base.resolve("x")));
	}

	/** #11: COPY_ATTRIBUTES copied the target's own mtime onto itself. */
	@Test
	public void copyAttributesCopiesLastModifiedTime() throws IOException {
		Path a = write("a.txt", "alpha");
		long mtime = 1_000_000_000_000L; // 2001-09-09
		assertTrue(new File(dir, "a.txt").setLastModified(mtime));

		Files.copy(a, base.resolve("b.txt"), StandardCopyOption.COPY_ATTRIBUTES);

		assertEquals("alpha", read("b.txt"));
		assertEquals(FileTime.fromMillis(mtime), Files.getLastModifiedTime(new File(dir, "b.txt").toPath()));
	}

	/** #11: REPLACE_EXISTING was ignored, so copying onto an existing file always failed. */
	@Test
	public void copyReplaceExisting() throws IOException {
		Path a = write("a.txt", "alpha");
		Path b = write("b.txt", "beta");
		assertThrows(FileAlreadyExistsException.class, () -> Files.copy(a, b));

		Files.copy(a, b, StandardCopyOption.REPLACE_EXISTING);
		assertArrayEquals("alpha".getBytes("UTF-8"), Files.readAllBytes(new File(dir, "b.txt").toPath()));
		assertEquals("alpha", read("a.txt"), "source untouched");
	}
}
