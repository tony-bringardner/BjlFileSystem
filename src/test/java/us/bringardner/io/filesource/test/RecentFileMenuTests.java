package us.bringardner.io.filesource.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.RecentFileMenu;
import us.bringardner.io.filesource.RecentFileMenu.ListEntry;
import us.bringardner.io.filesource.fileproxy.FileProxyFactory;
import us.bringardner.io.filesource.memory.MemoryFileSourceFactory;

/** BJL-20: the recent file list must never save secrets, and must survive a restart. */
public class RecentFileMenuTests {

	private static final String BASE = "us/bringardner/io/filesource/test/RecentFileMenuTests";
	private static final String PASSWORD = "s3cret|,=&~";
	private static final String USER = "b|o,b=&~";

	/**
	 * A memory factory whose connection properties include secrets, like a remote one.
	 * It names its secrets exactly: "pin" isn't something the name guess would catch,
	 * and "passphrase" is a secret it reports empty (not used by this connection).
	 */
	public static class SecretFactory extends MemoryFileSourceFactory {
		private static final long serialVersionUID = 1L;
		private static final List<String> SECRETS = Arrays.asList("password", "jdbcPassword", "pin", "passphrase");

		@Override
		public Properties getConnectProperties() {
			Properties p = super.getConnectProperties();
			p.setProperty("user", USER);
			p.setProperty("password", PASSWORD);
			p.setProperty("jdbcPassword", "pw2");
			p.setProperty("pin", "4321");
			p.setProperty("passphrase", "");
			return p;
		}

		@Override
		public boolean isSecretProperty(String name) {
			return SECRETS.contains(name);
		}
	}

	private Preferences node;

	@BeforeEach
	public void setUp() {
		node = Preferences.userRoot().node(BASE+"/"+UUID.randomUUID());
	}

	@AfterEach
	public void tearDown() throws BackingStoreException {
		Preferences parent = node.parent();
		node.removeNode();
		parent.flush();
	}

	@AfterAll
	public static void removeBase() throws BackingStoreException {
		Preferences.userRoot().node(BASE).removeNode();
		Preferences.userRoot().flush();
	}

	private static FileSource secretFile(String path) throws IOException {
		return new SecretFactory().createFileSource(path);
	}

	@Test
	public void secretsAreNeverSaved() throws IOException {
		RecentFileMenu menu = new RecentFileMenu(node);
		menu.addRecent(secretFile("/dir|x/a,b.txt"));

		String saved = node.get(RecentFileMenu.PREF_RECENT_LIST, null);
		assertNotNull(saved);
		assertFalse(saved.contains("s3cret"), saved);
		assertFalse(saved.contains("pw2"), saved);
		assertFalse(saved.contains("4321"), saved);
		// needed secrets are saved by name only; the unused one not at all
		assertTrue(saved.contains(",jdbcPassword,password,pin|"), saved);
		assertFalse(saved.contains("password="), saved);
		assertFalse(saved.contains("passphrase"), saved);
		// kept in memory for this session
		assertEquals(PASSWORD, menu.getRecentEntries().get(0).prop.getProperty("password"));
	}

	@Test
	public void listSurvivesARestartAndAsksForSecrets() throws IOException {
		new RecentFileMenu(node).addRecent(secretFile("/dir|x/a,b.txt"));

		RecentFileMenu menu = new RecentFileMenu(node);
		assertEquals(1, menu.getRecentEntries().size());
		ListEntry entry = menu.getRecentEntries().get(0);
		assertEquals("/dir|x/a,b.txt", entry.path);
		assertEquals(USER, entry.prop.getProperty("user"));
		assertFalse(entry.isLocal);
		assertEquals(Arrays.asList("jdbcPassword", "password", "pin"), entry.getMissingSecrets());

		List<String> asked = new ArrayList<>();
		menu.setSecretPrompter((e, name) -> { asked.add(name); return "typed"; });
		FileSource file = menu.openEntry(entry);
		assertNotNull(file);
		assertEquals("/dir|x/a,b.txt", file.getCanonicalPath());
		assertEquals(Arrays.asList("jdbcPassword", "password", "pin"), asked);

		// asked once per session
		menu.openEntry(entry);
		assertEquals(3, asked.size());
		assertFalse(node.get(RecentFileMenu.PREF_RECENT_LIST, "").contains("typed"));
	}

	@Test
	public void cancellingAPromptOpensNothing() throws IOException {
		new RecentFileMenu(node).addRecent(secretFile("/a.txt"));
		RecentFileMenu menu = new RecentFileMenu(node);
		menu.setSecretPrompter((e, name) -> null);
		ListEntry entry = menu.getRecentEntries().get(0);

		assertNull(menu.openEntry(entry));
		assertEquals(Arrays.asList("jdbcPassword", "password", "pin"), entry.getMissingSecrets());
	}

	@Test
	public void aFailedOpenForgetsTheSecretsEntered() throws IOException {
		RecentFileMenu menu = new RecentFileMenu(node);
		menu.setSecretPrompter((e, name) -> "wrong");
		ListEntry entry = new ListEntry("nosuchfactory|host=h,password|/x");

		assertThrows(IOException.class, () -> menu.openEntry(entry));
		assertEquals(Arrays.asList("password"), entry.getMissingSecrets());
	}

	@Test
	public void maxFilesAndOrderSurviveARestart() throws IOException {
		RecentFileMenu menu = new RecentFileMenu(node);
		menu.setMaxFiles(2);
		menu.addRecent(secretFile("/1.txt"));
		menu.addRecent(secretFile("/2.txt"));
		menu.addRecent(secretFile("/3.txt"));
		menu.addRecent(secretFile("/2.txt"));

		RecentFileMenu again = new RecentFileMenu(node);
		assertEquals(2, again.getMaxFiles());
		assertEquals(2, again.getRecentEntries().size());
		assertEquals("/2.txt", again.getRecentEntries().get(0).path);
		assertEquals("/3.txt", again.getRecentEntries().get(1).path);
	}

	@Test
	public void missingLocalFilesAreDropped() throws IOException {
		File tmp = Files.createTempFile("recent", ".txt").toFile();
		try {
			FileProxyFactory local = new FileProxyFactory();
			RecentFileMenu menu = new RecentFileMenu(node);
			menu.addRecent(local.createFileSource(tmp.getAbsolutePath()));
			menu.addRecent(secretFile("/remote.txt"));
			assertTrue(tmp.delete());

			RecentFileMenu again = new RecentFileMenu(node);
			assertEquals(1, again.getRecentEntries().size());
			assertEquals("/remote.txt", again.getRecentEntries().get(0).path);
		} finally {
			tmp.delete();
		}
	}

	@Test
	public void legacyListIsMigratedWithoutSecrets() throws Exception {
		node.put(RecentFileMenu.PREF_LEGACY_RECENT_LIST,
				legacyEncrypt("memory|name=,password=hunter2,user=bob|/docs/a.txt\n"));
		node.flush();

		RecentFileMenu menu = new RecentFileMenu(node);
		assertEquals(1, menu.getRecentEntries().size());
		assertEquals("/docs/a.txt", menu.getRecentEntries().get(0).path);
		assertNull(node.get(RecentFileMenu.PREF_LEGACY_RECENT_LIST, null));
		String saved = node.get(RecentFileMenu.PREF_RECENT_LIST, "");
		assertTrue(saved.contains("user=bob"), saved);
		assertFalse(saved.contains("hunter2"), saved);
		// the memory factory doesn't declare secrets, so the name guess decides
		assertTrue(saved.contains(",password|") || saved.contains("|password,") || saved.contains(",password,"), saved);
	}

	@Test
	public void unreadableLegacyListIsRemoved() throws Exception {
		node.put(RecentFileMenu.PREF_LEGACY_RECENT_LIST, "not base64 !!");
		node.flush();

		RecentFileMenu menu = new RecentFileMenu(node);
		assertTrue(menu.getRecentEntries().isEmpty());
		assertNull(node.get(RecentFileMenu.PREF_LEGACY_RECENT_LIST, null));
	}

	@Test
	public void theNameGuessIsOnlyADefault() {
		for(String s : new String[] {"password", "PASSWORD", "jdbcPassword", "passwd", "keyPassphrase",
				"privateKey", "sessionKey", "apiToken", "clientSecret", "credentials"}) {
			assertTrue(FileSourceFactory.looksLikeSecret(s), s);
		}
		for(String s : new String[] {"user", "host", "port", "name", "privateKeyFileName", "", null}) {
			assertFalse(FileSourceFactory.looksLikeSecret(s), String.valueOf(s));
		}
		// a factory that doesn't say uses the guess; one that does is exact
		assertTrue(new MemoryFileSourceFactory().isSecretProperty("password"));
		assertTrue(new SecretFactory().isSecretProperty("pin"));
		assertFalse(new SecretFactory().isSecretProperty("clientSecret"));
	}

	@Test
	public void theFactoryDecidesWhatIsSecret() throws IOException {
		ListEntry entry = new ListEntry(secretFile("/a.txt"));
		assertTrue(entry.isSecret("pin"));
		assertTrue(entry.isSecret("passphrase"));
		assertFalse(entry.isSecret("user"));
		String saved = entry.toString();
		assertTrue(saved.startsWith("memory|"), saved);
		assertTrue(saved.endsWith(",jdbcPassword,password,pin|/a.txt"), saved);
		assertFalse(saved.contains("passphrase"), saved);
		assertFalse(saved.contains("4321") || saved.contains("pw2") || saved.contains("s3cret"), saved);
		// the non-secret value survives a round trip, separators and all
		assertEquals(USER, new ListEntry(saved).prop.getProperty("user"));
	}

	/** How 1.0.1 and earlier saved the list. */
	private static String legacyEncrypt(String text) throws Exception {
		byte[] key = MessageDigest.getInstance("SHA-256").digest(System.getProperty("user.name").getBytes(StandardCharsets.UTF_8));
		Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
		cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
				new IvParameterSpec("1234567812345678".getBytes(StandardCharsets.US_ASCII)));
		return Base64.getEncoder().encodeToString(cipher.doFinal(text.getBytes(StandardCharsets.UTF_8)));
	}
}
