package us.bringardner.io.filesource.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.fileproxy.FileProxy;
import us.bringardner.io.filesource.java.file.FileSourcePath;
import us.bringardner.io.filesource.memory.MemoryFileSourceFactory;

/**
 * FileSourceFileSystem.getPath made relative names absolute against the
 * current directory, so resolveSibling("x") wrote to the wrong place.
 */
public class FileSystemGetPathTests {

	@Test
	public void getPathJoinsWithoutResolving() {
		FileSystem fs = new FileSourcePath("/", new MemoryFileSourceFactory()).getFileSystem();
		assertEquals("copy.txt", fs.getPath("copy.txt").toString());
		assertFalse(fs.getPath("copy.txt").isAbsolute());
		assertEquals("a/b/c", fs.getPath("a", "b", "", "c").toString());
		assertEquals("/a/b", fs.getPath("/a/", "b").toString());
	}

	@Test
	public void resolveSiblingStaysInTheSameDirectory() throws IOException {
		File dir = Files.createTempDirectory("sibling").toFile();
		File a = new File(dir, "a.txt");
		Files.write(a.toPath(), new byte[] { 1 });
		Path p = new FileSourcePath(new FileProxy(a, FileSourceFactory.getDefaultFactory()));

		Path sibling = p.resolveSibling("copy.txt");
		Files.writeString(sibling, "x");

		File expected = new File(dir, "copy.txt");
		assertEquals(expected.getAbsolutePath(), sibling.toString());
		assertTrue(expected.exists());
		expected.delete();
		a.delete();
		dir.delete();
	}
}
