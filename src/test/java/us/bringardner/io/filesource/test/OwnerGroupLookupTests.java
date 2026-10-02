package us.bringardner.io.filesource.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;

/**
 * FileProxy owner and group (BJL-31): looked up once per uid/gid and reused across files,
 * with the same names the JDK reports.
 */
public class OwnerGroupLookupTests {

	@Test
	public void sameNamesAsTheJdk() throws Exception {
		Path dir = Files.createTempDirectory("ownergroup");
		try {
			Path a = Files.write(dir.resolve("a.txt"), new byte[] { 1 });
			FileSource fa = FileSourceFactory.getDefaultFactory().createFileSource(a.toString());
			assertEquals(Files.getOwner(a).getName(), fa.getOwner().getName());
			PosixFileAttributeView posix = Files.getFileAttributeView(a, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
			if (posix != null) {
				assertEquals(posix.readAttributes().group().getName(), fa.getGroup().getName());
			} else {
				assertNotNull(fa.getGroup());
			}
		} finally {
			deleteAll(dir.toFile());
		}
	}

	@Test
	public void filesWithTheSameOwnerShareOneLookup() throws Exception {
		Assumptions.assumeTrue(FileSystemsHaveUnixView.check(), "no unix attribute view here (e.g. Windows)");
		Path dir = Files.createTempDirectory("ownergroup");
		try {
			Path a = Files.write(dir.resolve("a.txt"), new byte[] { 1 });
			Path b = Files.write(dir.resolve("b.txt"), new byte[] { 2 });
			FileSource fa = FileSourceFactory.getDefaultFactory().createFileSource(a.toString());
			FileSource fb = FileSourceFactory.getDefaultFactory().createFileSource(b.toString());
			// the second file reuses the first file's looked up principals
			assertSame(fa.getOwner(), fb.getOwner());
			assertSame(fa.getGroup(), fb.getGroup());
		} finally {
			deleteAll(dir.toFile());
		}
	}

	private static final class FileSystemsHaveUnixView {
		static boolean check() {
			try {
				Path p = Files.createTempFile("unixview", ".tmp");
				try {
					return Files.getAttribute(p, "unix:uid") instanceof Integer;
				} finally {
					Files.delete(p);
				}
			} catch (Exception e) {
				return false;
			}
		}
	}

	private static void deleteAll(File f) {
		File[] kids = f.listFiles();
		if (kids != null) {
			for (File k : kids) {
				deleteAll(k);
			}
		}
		f.delete();
	}
}
