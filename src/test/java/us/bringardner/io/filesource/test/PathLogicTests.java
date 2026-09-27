package us.bringardner.io.filesource.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.Test;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.fileproxy.FileProxy;
import us.bringardner.io.filesource.fileproxy.FileProxyFactory;
import us.bringardner.io.filesource.memory.MemoryFileSourceFactory;

/**
 * Review section 3, "Path and prefix logic".
 */
public class PathLogicTests {

	/** isChildOfMine used a plain startsWith, so /x/ab counted as a child of /x/a. */
	@Test
	public void fileProxyIsChildOfMine() {
		FileSourceFactory f = FileSourceFactory.getDefaultFactory();
		FileProxy a = new FileProxy(new File("/x/a"), f);
		assertTrue(a.isChildOfMine(new FileProxy(new File("/x/a/b"), f)));
		assertTrue(a.isChildOfMine(new FileProxy(new File("/x/a"), f)), "itself");
		assertFalse(a.isChildOfMine(new FileProxy(new File("/x/ab"), f)));
		assertTrue(new FileProxy(new File("/"), f).isChildOfMine(a), "root");
	}

	@Test
	public void memoryIsChildOfMine() throws IOException {
		MemoryFileSourceFactory m = new MemoryFileSourceFactory();
		FileSource a = m.createFileSource("/x/a");
		assertTrue(a.isChildOfMine(m.createFileSource("/x/a/b")));
		assertFalse(a.isChildOfMine(m.createFileSource("/x/ab")));
		assertTrue(m.createFileSource("/").isChildOfMine(a), "root");
	}

	/** expandDots threw IndexOutOfBoundsException for "/..". */
	@Test
	public void dotDotAtRootStaysAtRoot() throws IOException {
		MemoryFileSourceFactory m = new MemoryFileSourceFactory();
		FileSource root = m.createFileSource("/");
		assertSame(root, m.createFileSource("/.."));
		assertSame(root, m.createFileSource("/../.."));
		assertEquals("/a", m.createFileSource("/../a").getAbsolutePath());
		assertEquals("/b", m.createFileSource("/a/../../b").getAbsolutePath());
	}

	@Test
	public void fileProxyRelativePathsResolveAgainstCurrentDirectory() throws IOException {
		FileProxyFactory f = new FileProxyFactory();
		File cwd = new File(".").getCanonicalFile();
		assertEquals(new File(cwd, "sub/file.txt").getAbsolutePath(), f.createFileSource("sub/file.txt").getAbsolutePath());
		assertEquals(new File("/tmp/x").getAbsolutePath(), f.createFileSource(new File("/tmp/x").getAbsolutePath()).getAbsolutePath());
	}

	/** UNC paths were treated as relative, and \foo wasn't resolved against the current drive. */
	@Test
	public void windowsUncAndRootRelativePaths() throws IOException {
		assumeTrue(FileSourceFactory.isWindows(), "Windows paths");
		FileProxyFactory f = new FileProxyFactory();
		assertEquals("\\\\server\\share\\x.txt", f.createFileSource("\\\\server\\share\\x.txt").getAbsolutePath());
		String drive = new File(".").getCanonicalPath().substring(0, 2);
		assertEquals(drive+"\\foo", f.createFileSource("\\foo").getAbsolutePath());
	}
}
