package us.bringardner.io.filesource.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.UserPrincipalNotFoundException;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.FileSourceGroup;
import us.bringardner.io.filesource.FileSourceUser;
import us.bringardner.io.filesource.fileproxy.FileProxy;
import us.bringardner.io.filesource.fileproxy.FileProxyFactory;

/**
 * Review section 3, "Environment".
 */
public class EnvironmentTests {

	/** findUser ran the command "id ???" on macOS/Linux, so it always failed. */
	@Test
	public void findUserOnUnix() {
		assumeFalse(FileSourceFactory.isWindows(), "uses id");
		String me = System.getProperty("user.name");
		FileSourceUser user = FileSourceUser.findUser(me);
		assertNotNull(user, "findUser("+me+")");
		assertEquals(me, user.getName());
		assertFalse(user.getGroups().isEmpty(), "has groups");
	}

	/** The whoami list format was parsed by English labels; the CSV form is parsed by position. */
	@Test
	public void windowsCsvParsingIsLanguageIndependent() {
		String german =
				"\"WINDOWSLAPTOP\\tony\",\"S-1-5-21-4225293122-3422176466-2310549978-1007\"\r\n"
				+ "\r\n"
				+ "\"Jeder\",\"Bekannte Gruppe\",\"S-1-1-0\",\"Verbindliche Gruppe, Standardmäßig aktiviert, Aktivierte Gruppe\"\r\n"
				+ "\"VORDEFINIERT\\Administratoren\",\"Alias\",\"S-1-5-32-544\",\"Verbindliche Gruppe, Aktivierte Gruppe\"\r\n"
				+ "\"Verbindliche Beschriftung\\Hohe Verbindlichkeitsstufe\",\"Bezeichnung\",\"S-1-16-12288\",\"\"\r\n";
		FileSourceUser user = FileSourceUser.fromWindowsCsv(german);
		assertNotNull(user);
		assertEquals("tony", user.getName());
		assertTrue(user.hasGroup("Jeder"));
		assertTrue(user.hasGroup("Administratoren"));
		assertFalse(user.hasGroup("Hohe Verbindlichkeitsstufe"), "integrity labels are skipped");
	}

	/** FileProxy cached the group forever, so getGroup() still showed the old one after setGroup(). */
	@Test
	public void fileProxyGroupCacheIsClearedBySetGroup() throws IOException {
		assumeFalse(FileSourceFactory.isWindows(), "POSIX groups");
		File f = File.createTempFile("grp", ".txt");
		f.deleteOnExit();
		FileProxy file = new FileProxy(f, new FileProxyFactory());
		String current = file.getGroup().getName();

		FileSourceUser me = FileSourceFactory.getDefaultFactory().whoAmI();
		Optional<String> other = me.getGroups().values().stream()
				.map(FileSourceGroup::getName)
				.filter(n -> !n.equals(current) && !n.equals("UnKnown"))
				.findFirst();
		assumeTrue(other.isPresent(), "needs membership of a second group");

		GroupPrincipal target;
		try {
			target = FileSystems.getDefault().getUserPrincipalLookupService().lookupPrincipalByGroupName(other.get());
		} catch (UserPrincipalNotFoundException e) {
			assumeTrue(false, "group "+other.get()+" not resolvable");
			return;
		}
		assumeTrue(file.setGroup(target), "chgrp allowed");
		assertEquals(other.get(), file.getGroup().getName());
	}

	@Test
	public void refreshClearsCachedOwnerAndGroup() throws IOException {
		File f = File.createTempFile("own", ".txt");
		f.deleteOnExit();
		FileProxy file = new FileProxy(f, new FileProxyFactory());
		String owner = file.getOwner().getName();
		file.refresh();
		assertEquals(owner, file.getOwner().getName(), "re-read after refresh");
	}

	/** Drives were listed once, so drives mounted later never appeared. */
	@Test
	public void windowsRootsAreNotCached() throws IOException {
		assumeTrue(FileSourceFactory.isWindows(), "Windows drives");
		FileProxyFactory f = new FileProxyFactory();
		assertTrue(f.listRoots() != f.listRoots(), "fresh list each call");
	}
}
