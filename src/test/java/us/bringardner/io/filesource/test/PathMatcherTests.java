package us.bringardner.io.filesource.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.PatternSyntaxException;

import org.junit.jupiter.api.Test;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.fileproxy.FileProxy;
import us.bringardner.io.filesource.java.file.FileSourcePath;
import us.bringardner.io.filesource.memory.MemoryFileSourceFactory;

/**
 * FileSourceFileSystem.getPathMatcher (it used to throw "Not implemented").
 */
public class PathMatcherTests {

	private static final MemoryFileSourceFactory MEM = new MemoryFileSourceFactory();

	private static Path mem(String p) {
		return new FileSourcePath(p, MEM);
	}

	private static FileSystem memFs() {
		return mem("/").getFileSystem();
	}

	private static boolean glob(String pattern, String path) {
		return memFs().getPathMatcher("glob:"+pattern).matches(mem(path));
	}

	@Test
	public void globBasics() {
		assertTrue(glob("*.txt", "a.txt"));
		assertFalse(glob("*.txt", "dir/a.txt"), "* doesn't cross a separator");
		assertTrue(glob("**/*.java", "src/main/A.java"), "** does");
		assertTrue(glob("/home/**", "/home/tony/x"));
		assertTrue(glob("?.c", "a.c"));
		assertFalse(glob("?.c", "ab.c"));
		assertTrue(glob("[abc].txt", "b.txt"));
		assertFalse(glob("[!abc].txt", "b.txt"));
		assertTrue(glob("[a-c]x", "cx"));
		assertTrue(glob("{foo,bar}.txt", "bar.txt"));
		assertFalse(glob("{foo,bar}.txt", "baz.txt"));
		assertTrue(glob("\\*.txt", "*.txt"), "escaped *");
		assertFalse(glob("\\*.txt", "a.txt"));
		assertTrue(glob("a+b(1).txt", "a+b(1).txt"), "regex metacharacters are literal");
		assertTrue(glob("*.{java,class}", "Foo.class"));
	}

	@Test
	public void regexSyntax() {
		PathMatcher m = memFs().getPathMatcher("regex:.*\\.(txt|log)");
		assertTrue(m.matches(mem("dir/a.log")));
		assertFalse(m.matches(mem("a.bin")));
	}

	@Test
	public void errors() {
		assertThrows(IllegalArgumentException.class, () -> memFs().getPathMatcher("*.txt"), "no syntax");
		assertThrows(UnsupportedOperationException.class, () -> memFs().getPathMatcher("wild:*.txt"));
		assertThrows(PatternSyntaxException.class, () -> memFs().getPathMatcher("glob:{a,{b}}"), "nested group");
		assertThrows(PatternSyntaxException.class, () -> memFs().getPathMatcher("glob:[a/b]"), "separator in class");
		assertThrows(PatternSyntaxException.class, () -> memFs().getPathMatcher("glob:abc\\"), "dangling escape");
		assertThrows(PatternSyntaxException.class, () -> memFs().getPathMatcher("glob:{a,b"), "missing }");
	}

	/** Same answers as the JDK's own glob implementation (on a '/' file system). */
	@Test
	public void sameResultsAsTheJdk() {
		assumeFalse(FileSourceFactory.isWindows(), "the default file system uses '/' here");
		String[] patterns = { "*", "**", "*.txt", "**.txt", "**/*.txt", "a/*", "a/**", "?", "??.c", "[abc]*", "[!a]*",
				"[a-c][0-9]", "{a,b}*", "{*.txt,*.log}", "a?c", "*/b/*", "\\?", "[-a]", "[a-]", "[!-]", "x[!/]y".replace("/", "a"),
				"a.b", "a+b", "(x)", "^$", "*.[ch]" };
		String[] paths = { "a", "b", "a.txt", "dir/a.txt", "a/b", "a/b/c", "a/b/c.txt", "ab.c", "x.c", "c9", "a1",
				"b.log", "abc", "a/b/d", "?", "-", "a.b", "axb", "a+b", "(x)", "^$", "main.c", "main.h", "main.o", "", "/a.txt" };
		FileSystem jdk = FileSystems.getDefault();
		List<String> differences = new ArrayList<>();
		for (String p : patterns) {
			PathMatcher expected = jdk.getPathMatcher("glob:"+p);
			PathMatcher actual = memFs().getPathMatcher("glob:"+p);
			for (String s : paths) {
				boolean e = expected.matches(Paths.get(s));
				boolean a = actual.matches(mem(s));
				if( e != a ) {
					differences.add("glob:"+p+" on '"+s+"': JDK="+e+" ours="+a);
				}
			}
		}
		assertEquals(new ArrayList<String>(), differences);
	}

	/** Files.newDirectoryStream(dir, glob) calls getPathMatcher; it used to throw. */
	@Test
	public void directoryStreamWithGlob() throws IOException {
		for (String kind : Arrays.asList("local", "memory")) {
			Path dir;
			File tmp = null;
			if( kind.equals("memory") ) {
				MemoryFileSourceFactory m = new MemoryFileSourceFactory();
				m.connect();
				FileSource d = m.createFileSource("/globdir");
				assertTrue(d.mkdirs());
				dir = new FileSourcePath(d);
			} else {
				tmp = Files.createTempDirectory("glob").toFile();
				dir = new FileSourcePath(new FileProxy(tmp, FileSourceFactory.getDefaultFactory()));
			}
			for (String n : new String[] { "a.txt", "b.log", "c.txt", "d.TXT" }) {
				FileSource f = ((FileSourcePath) dir.resolve(n)).getFileSource();
				try (OutputStream out = f.getOutputStream()) {
					out.write(1);
				}
			}
			List<String> names = new ArrayList<>();
			try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.txt")) {
				for (Path p : ds) {
					names.add(p.getFileName().toString());
				}
			}
			names.sort(null);
			List<String> expected = FileSourceFactory.isWindows() && kind.equals("local")
					? Arrays.asList("a.txt", "c.txt", "d.TXT")   // case-insensitive on Windows
					: Arrays.asList("a.txt", "c.txt");
			assertEquals(expected, names, kind);
			if( tmp != null ) {
				for (File f : tmp.listFiles()) {
					f.delete();
				}
				tmp.delete();
			}
		}
	}
}
