package us.bringardner.io.filesource.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.fileproxy.FileProxy;

/** The java.io.File-named default methods on FileSource (isAbsolute, getPath, toPath, space, ...). */
public class FileCompatTests {

	private File tmp;

	@AfterEach
	public void cleanup() {
		if( tmp != null ) {
			for(File f : tmp.listFiles()) {
				f.delete();
			}
			tmp.delete();
		}
	}

	private FileSource dir(String type) throws IOException {
		FileSourceFactory factory = FileSourceFactory.getFileSourceFactory(type);
		factory.connect();
		tmp = Files.createTempDirectory("compat").toFile();
		FileSource dir = factory.createFileSource(tmp.getAbsolutePath());
		dir.mkdirs();
		return dir;
	}

	@ParameterizedTest
	@ValueSource(strings = { "fileproxy", "memory" })
	public void pathsAndFiles(String type) throws IOException {
		FileSource dir = dir(type);
		FileSource f = dir.getChild("a.txt");
		f.createNewFile();

		assertTrue(f.isAbsolute());
		assertEquals(new File(tmp, "a.txt").getPath(), f.getPath());
		assertSame(f, f.getAbsoluteFile());
		assertEquals(f.getCanonicalPath(), f.getCanonicalFile().getAbsolutePath());
		assertTrue(f.getCanonicalFile().exists());
	}

	@ParameterizedTest
	@ValueSource(strings = { "fileproxy", "memory" })
	public void setLastModified(String type) throws IOException {
		FileSource f = dir(type).getChild("t.txt");
		f.createNewFile();
		assertTrue(f.setLastModified(1_000_000_000_000L));
		assertEquals(1_000_000_000_000L, f.lastModified());
		assertThrows(IllegalArgumentException.class, () -> f.setLastModified(-1));
	}

	@ParameterizedTest
	@ValueSource(strings = { "fileproxy", "memory" })
	public void toPathAndUri(String type) throws IOException {
		FileSource f = dir(type).getChild("with space.txt");
		Files.writeString(f.toPath(), "via nio");
		try (java.io.InputStream in = f.getInputStream()) {
			assertEquals("via nio", new String(in.readAllBytes(), StandardCharsets.UTF_8));
		}
		URI uri = f.toURI();
		assertEquals("filesource", uri.getScheme());
		Path back = Paths.get(uri);
		assertEquals("via nio", Files.readString(back));
	}

	@Test
	public void spaceLocalMatchesFile() throws IOException {
		FileSource dir = dir("fileproxy");
		assertEquals(tmp.getTotalSpace(), dir.getTotalSpace());
		assertTrue(dir.getUsableSpace() > 0);
		assertTrue(dir.getFreeSpace() > 0);
		assertEquals(tmp.getTotalSpace(), Files.getFileStore(dir.toPath()).getTotalSpace());
	}

	@Test
	public void spaceUnknownIsZero() throws IOException {
		FileSource dir = dir("memory");
		assertEquals(0L, dir.getTotalSpace());
		assertEquals(0L, dir.getFreeSpace());
		assertEquals(0L, dir.getUsableSpace());
		assertEquals(0L, Files.getFileStore(dir.toPath()).getUsableSpace());
	}

	/** A FileProxy made from a relative File is relative, as the File is. */
	@Test
	public void fileProxyRelative() {
		FileSource rel = new FileProxy(new File("rel.txt"), FileSourceFactory.getDefaultFactory());
		assertEquals(false, rel.isAbsolute());
		assertEquals("rel.txt", rel.getPath());
	}
}
