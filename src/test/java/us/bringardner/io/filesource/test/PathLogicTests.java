package us.bringardner.io.filesource.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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

	// ------------------------------------------------------------------ escapes

	/**
	 * ".." must be resolved before comparing: root/../outside is NOT inside root.
	 * isChildOfMine compared getAbsolutePath(), which keeps "..", so a plain prefix test
	 * accepted it. Servers using isChildOfMine as a sandbox check could be escaped.
	 */
	@Test
	public void fileProxyDotDotDoesNotEscape() throws IOException {
		Path base = Files.createTempDirectory("childOfMine");
		try {
			Path root = Files.createDirectories(base.resolve("root/sub"));
			root = root.getParent();
			Files.write(base.resolve("outside.txt"), new byte[] { 1 });
			FileSource r = new FileProxyFactory().createFileSource(root.toString());

			for (String p : new String[] { "../outside.txt", "sub/../../outside.txt", "..", "a/../../../x", "sub/../.." }) {
				assertFalse(r.isChildOfMine(r.getChild(p)), p + " must not be inside " + r);
			}
			for (String p : new String[] { "sub", "sub/../sub", "./sub/x.txt", "sub/..", "new/../also-new" }) {
				assertTrue(r.isChildOfMine(r.getChild(p)), p + " is inside " + r);
			}
		} finally {
			deleteTree(base);
		}
	}

	/** A symbolic link inside the root that points outside it must not count as inside. */
	@Test
	public void fileProxySymlinkOutDoesNotEscape() throws IOException {
		Path base = Files.createTempDirectory("childOfMine");
		try {
			Path root = Files.createDirectories(base.resolve("root"));
			Path outside = Files.createDirectories(base.resolve("outside"));
			Path sibling = Files.createDirectories(base.resolve("rootX")); // same prefix as root
			try {
				Files.createSymbolicLink(root.resolve("out"), outside);
				Files.createSymbolicLink(root.resolve("sib"), sibling);
			} catch (UnsupportedOperationException | IOException e) {
				assumeTrue(false, "symbolic links not supported: " + e);
			}
			FileSource r = new FileProxyFactory().createFileSource(root.toString());
			assertFalse(r.isChildOfMine(r.getChild("out")), "link to outside");
			assertFalse(r.isChildOfMine(r.getChild("out/secret.txt")), "file through link to outside");
			assertFalse(r.isChildOfMine(r.getChild("sib/x")), "link to same-prefix sibling");
		} finally {
			deleteTree(base);
		}
	}

	@Test
	public void memoryDotDotDoesNotEscape() throws IOException {
		MemoryFileSourceFactory m = new MemoryFileSourceFactory();
		FileSource root = m.createFileSource("/srv/root");
		m.createFileSource("/srv/outside.txt");
		for (String p : new String[] { "../outside.txt", "sub/../../outside.txt", "..", "a/../../../x" }) {
			assertFalse(root.isChildOfMine(root.getChild(p)), p + " must not be inside /srv/root");
		}
		assertTrue(root.isChildOfMine(root.getChild("sub/../sub/file")));
		assertFalse(root.isChildOfMine(m.createFileSource("/srv/rootX/file")), "same-prefix sibling");
	}

	private static void deleteTree(Path p) throws IOException {
		if (Files.isDirectory(p, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
			try (java.util.stream.Stream<Path> kids = Files.list(p)) {
				for (Path k : (Iterable<Path>) kids::iterator) {
					deleteTree(k);
				}
			}
		}
		Files.deleteIfExists(p);
	}
}
