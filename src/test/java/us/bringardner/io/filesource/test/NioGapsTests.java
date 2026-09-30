package us.bringardner.io.filesource.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.NotLinkException;
import java.nio.file.Path;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.attribute.UserPrincipalNotFoundException;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.fileproxy.FileProxy;
import us.bringardner.io.filesource.java.file.FileSourcePath;
import us.bringardner.io.filesource.memory.MemoryFileSourceFactory;

/**
 * java.nio calls that threw "Not implemented" (or UnsupportedOperationException
 * from the JDK defaults) before fix/nio-gaps, on local and memory file systems.
 */
public class NioGapsTests {

	private static final boolean WINDOWS = File.separatorChar == '\\';
	private File tmpDir;

	private Path base(String kind) throws IOException {
		if( kind.equals("memory")) {
			MemoryFileSourceFactory factory = new MemoryFileSourceFactory();
			factory.connect();
			FileSource dir = factory.createFileSource("/gaps");
			assertTrue(dir.mkdirs());
			return new FileSourcePath(dir);
		}
		tmpDir = Files.createTempDirectory("gaps").toFile();
		return new FileSourcePath(new FileProxy(tmpDir, FileSourceFactory.getDefaultFactory()));
	}

	@AfterEach
	public void cleanup() {
		if( tmpDir != null ) {
			for(File f : tmpDir.listFiles()) {
				f.delete();
			}
			tmpDir.delete();
		}
	}

	private static Path file(Path base, String name, String text) throws IOException {
		Path p = base.resolve(name);
		Files.write(p, text.getBytes(StandardCharsets.UTF_8));
		return p;
	}

	@ParameterizedTest
	@ValueSource(strings = { "local", "memory" })
	public void setAttributeTimes(String kind) throws IOException {
		Path p = file(base(kind), "t.txt", "x");
		FileTime t = FileTime.fromMillis(1_000_000_000_000L);
		Files.setAttribute(p, "lastModifiedTime", t);
		assertEquals(t.toMillis(), Files.getLastModifiedTime(p).toMillis());
		FileTime t2 = FileTime.fromMillis(1_100_000_000_000L);
		Files.setAttribute(p, "basic:lastModifiedTime", t2);
		assertEquals(t2.toMillis(), Files.getLastModifiedTime(p).toMillis());
	}

	@ParameterizedTest
	@ValueSource(strings = { "local", "memory" })
	public void setAttributePermissions(String kind) throws IOException {
		if( WINDOWS && kind.equals("local") ) {
			return;
		}
		Path p = file(base(kind), "p.txt", "x");
		Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-r-----");
		Files.setAttribute(p, "posix:permissions", perms);
		assertEquals(perms, Files.getPosixFilePermissions(p));
	}

	@ParameterizedTest
	@ValueSource(strings = { "local", "memory" })
	public void attributeNameErrors(String kind) throws IOException {
		Path p = file(base(kind), "e.txt", "x");
		assertThrows(UnsupportedOperationException.class, () -> Files.setAttribute(p, "dos:hidden", true));
		assertThrows(IllegalArgumentException.class, () -> Files.setAttribute(p, "basic:nope", 1));
		assertThrows(UnsupportedOperationException.class, () -> Files.readAttributes(p, "dos:*"));
		// a typo used to return an empty map
		assertThrows(IllegalArgumentException.class, () -> Files.readAttributes(p, "basic:sise"));
		assertThrows(ClassCastException.class, () -> Files.setAttribute(p, "lastModifiedTime", "yesterday"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "local", "memory" })
	public void ownerView(String kind) throws IOException {
		Path p = file(base(kind), "o.txt", "x");
		Map<String, Object> m = Files.readAttributes(p, "owner:owner");
		assertEquals(Files.getOwner(p).getName(), ((UserPrincipal) m.get("owner")).getName());
	}

	@ParameterizedTest
	@ValueSource(strings = { "local", "memory" })
	public void setOwnerToItsOwnOwner(String kind) throws IOException {
		if( WINDOWS && kind.equals("local") ) {
			return;
		}
		Path p = file(base(kind), "so.txt", "x");
		UserPrincipal owner = Files.getOwner(p);
		// For local files this was a FileSourceUser, which the OS rejected
		Files.setOwner(p, owner);   // now throws if the FileSource reports failure
		assertEquals(owner.getName(), Files.getOwner(p).getName());
		FileSource f = ((FileSourcePath) p).getFileSource();
		assertTrue(f.setOwner(f.getOwner()));
		assertTrue(f.setGroup(f.getGroup()));
		// A FileSourceUser (e.g. whoAmI(), or an owner from another FileSource)
		// used to be rejected by local files, which only took the OS's principals
		assertTrue(f.setOwner(f.getFileSourceFactory().whoAmI()));
		assertEquals(owner.getName(), Files.getOwner(p).getName());
	}

	@ParameterizedTest
	@ValueSource(strings = { "local", "memory" })
	public void fileStores(String kind) throws IOException {
		Path p = file(base(kind), "s.txt", "x");
		FileSystem fs = p.getFileSystem();
		assertTrue(fs.getFileStores().iterator().hasNext());
		assertTrue(fs.supportedFileAttributeViews().contains("basic"));
		assertTrue(fs.supportedFileAttributeViews().contains("posix"));

		FileStore store = Files.getFileStore(p);
		assertFalse(store.name().contains("@"), store.name());
		assertTrue(store.supportsFileAttributeView(PosixFileAttributeView.class));
		assertFalse(store.supportsFileAttributeView(AclFileAttributeView.class));
		assertFalse(store.supportsFileAttributeView("dos"));
		assertTrue(store.getTotalSpace() > 0);
		if( kind.equals("local") ) {
			assertNotEquals(Long.MAX_VALUE, store.getTotalSpace());
		}
		assertThrows(UnsupportedOperationException.class, () -> store.getAttribute("nope"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "local", "memory" })
	public void watchServiceUnsupported(String kind) throws IOException {
		FileSystem fs = base(kind).getFileSystem();
		assertThrows(UnsupportedOperationException.class, () -> fs.newWatchService());
	}

	@ParameterizedTest
	@ValueSource(strings = { "local", "memory" })
	public void lookupService(String kind) throws IOException {
		Path p = file(base(kind), "l.txt", "x");
		UserPrincipalLookupService svc = p.getFileSystem().getUserPrincipalLookupService();
		String me = Files.getOwner(p).getName();
		UserPrincipal u = svc.lookupPrincipalByName(me);
		assertNotNull(u);
		assertEquals(me, u.getName());
		assertThrows(UserPrincipalNotFoundException.class, () -> svc.lookupPrincipalByName("no-such-user-x9q"));
		if( kind.equals("memory") ) {
			GroupPrincipal g = Files.readAttributes(p, java.nio.file.attribute.PosixFileAttributes.class).group();
			assertEquals(g.getName(), svc.lookupPrincipalByGroupName(g.getName()).getName());
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "local", "memory" })
	public void symbolicLinks(String kind) throws IOException {
		if( WINDOWS && kind.equals("local") ) {
			return;   // needs admin or developer mode
		}
		Path base = base(kind);
		Path target = file(base, "target.txt", "linked");
		Path link = base.resolve("link.txt");
		Files.createSymbolicLink(link, target);
		assertEquals("linked", new String(Files.readAllBytes(link), StandardCharsets.UTF_8));
		assertEquals(target.toString(), Files.readSymbolicLink(link).toString());

		// relative target: relative to the link's directory
		Path rel = base.resolve("rel.txt");
		Files.createSymbolicLink(rel, base.getFileSystem().getPath("target.txt"));
		assertEquals("linked", new String(Files.readAllBytes(rel), StandardCharsets.UTF_8));
		assertEquals(target.getFileName().toString(), Files.readSymbolicLink(rel).getFileName().toString());

		assertThrows(java.nio.file.FileAlreadyExistsException.class, () -> Files.createSymbolicLink(link, target));
		assertThrows(NotLinkException.class, () -> Files.readSymbolicLink(target));
	}

	/** A relative link target used to be resolved against the process's current directory. */
	@Test
	public void fileProxyRelativeLinkTarget() throws IOException {
		if( WINDOWS ) {
			return;
		}
		tmpDir = Files.createTempDirectory("rel").toFile();
		Files.write(tmpDir.toPath().resolve("t.txt"), new byte[] {1});
		Files.createSymbolicLink(tmpDir.toPath().resolve("l"), java.nio.file.Paths.get("t.txt"));
		FileSource link = new FileProxy(new File(tmpDir, "l"), FileSourceFactory.getDefaultFactory());
		assertEquals(new File(tmpDir, "t.txt").getCanonicalPath(), new File(link.getLinkedTo().getAbsolutePath()).getCanonicalPath());
	}

	/** Setting one file's group used to change the owner's primary group, and so other files' group. */
	@Test
	public void memorySetGroupIsPerFile() throws IOException {
		MemoryFileSourceFactory factory = new MemoryFileSourceFactory();
		factory.connect();
		FileSource a = factory.createFileSource("/a.txt");
		FileSource b = factory.createFileSource("/b.txt");
		a.createNewFile();
		b.createNewFile();
		String before = b.getGroup().getName();
		assertTrue(a.setGroup(new us.bringardner.io.filesource.FileSourceGroup(4242, "othergroup")));
		assertEquals("othergroup", a.getGroup().getName());
		assertEquals(before, b.getGroup().getName());
		FileSource c = factory.createFileSource("/c.txt");
		c.createNewFile();
		assertEquals(before, c.getGroup().getName());
	}
}
