/**
 * <PRE>
 * 
 * Copyright Tony Bringarder 1998, 2025 <A href="http://bringardner.com/tony">Tony Bringardner</A>
 * 
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       <A href="http://www.apache.org/licenses/LICENSE-2.0">http://www.apache.org/licenses/LICENSE-2.0</A>
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *  </PRE>
 *   
 *   
 *	@author Tony Bringardner   
 *
 *
 * ~version~V000.01.17-V000.00.01-V000.00.00-
 */
package us.bringardner.io.filesource;

import java.awt.event.ActionEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.TreeSet;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;

import us.bringardner.io.filesource.fileproxy.FileProxyFactory;

/**
 * A "Recent Files" menu, saved with java.util.prefs.Preferences.
 * <p>
 * Each entry keeps its factory's connection properties so it can be reopened,
 * but secret values (see {@link FileSourceFactory#isSecretProperty(String)}) are
 * never saved: only their names are, with an empty value. When such an entry is
 * opened the user is asked for them (see {@link #setSecretPrompter(SecretPrompter)}),
 * and the answers are kept in memory for the rest of the session.
 * <p>
 * Up to 1.0.1 the whole list, passwords included, was saved encrypted with a key
 * derived from the user name and a fixed IV, which anyone could reverse. That list
 * is read once, saved again without its secrets, and removed.
 */
public class RecentFileMenu extends JMenu {

	/** Asks for the value of a secret connection property; returns null to cancel. */
	@FunctionalInterface
	public interface SecretPrompter {
		String prompt(ListEntry entry, String propertyName);
	}

	private static final long serialVersionUID = 1L;
	private static final String NL = "\n";
	private static final String AMP = "&";
	private static final String COMMA= ",";
	private static final String EQ= "=";
	private static final String TILDE= "~";
	private static final String VBAR= "|";

	/** Where 1.0.1 and earlier kept the encrypted list; migrated, then removed. */
	public static final String PREF_LEGACY_RECENT_LIST = "RecentList";
	/** The list: one entry per line, secret values left out. */
	public static final String PREF_RECENT_LIST = "RecentFiles";
	public static final String PREF_MAX_FILES = "MaxRecentFiles";

	public static class ListEntry {
		public String id;
		public Properties prop = new Properties();
		public String path;
		public boolean isLocal=true;
		private FileSource filex;

		/**
		 * Connects to the entry's factory and returns its file. Secret properties that
		 * weren't saved must be filled in first; {@link RecentFileMenu#openEntry(ListEntry)} does that.
		 */
		public FileSource getFile() throws IOException {
			if( filex == null ) {
				FileSourceFactory f = FileSourceFactory.getFileSourceFactory(id);
				if( f == null ) {
					throw new IOException("No FileSource factory is registered for "+id);
				}
				// the properties aren't in the message: they may hold a password
				if( !f.connect(prop)) {
					throw new IOException("Can't connect to "+id+" for "+path);
				}
				filex = f.createFileSource(path);
			}

			return filex;
		}

		/** @return the names of secret properties that have no value, sorted */
		public List<String> getMissingSecrets() {
			List<String> ret = new ArrayList<>();
			for(String name : prop.stringPropertyNames()) {
				if( FileSourceFactory.isSecretProperty(name) && prop.getProperty(name).isEmpty() ) {
					ret.add(name);
				}
			}
			Collections.sort(ret);
			return ret;
		}

		/** The saved form, id|name=value,...|path, with secret values left out. */
		public String toString() {
			StringBuilder buf = new StringBuilder();
			for(String name : new TreeSet<>(prop.stringPropertyNames())) {
				String val = FileSourceFactory.isSecretProperty(name) ? "" : prop.getProperty(name);
				if(buf.length()>0) {
					buf.append(COMMA);
				}
				buf.append(encode(name)).append(EQ).append(encode(val));
			}

			return id+VBAR+buf+VBAR+path;
		}

		/** Parses the saved form. */
		public ListEntry(String line) {
			// the path is last and may itself contain '|'
			String parts[] = line.split("\\"+VBAR, 3);
			if( parts.length < 3 || parts[0].isEmpty() ) {
				// the line isn't in the message: an old one may hold a password
				throw new IllegalArgumentException("Malformed recent file entry");
			}
			id = parts[0];
			path = parts[2];
			isLocal = FileProxyFactory.FACTORY_ID.equals(id);

			for(String str : parts[1].split(COMMA)) {
				int eq = str.indexOf(EQ);
				if( eq > 0 ) {
					// an empty value is kept: it marks a secret that wasn't saved
					prop.setProperty(decode(str.substring(0, eq)), decode(str.substring(eq+1)));
				}
			}
		}

		public ListEntry(FileSource file) throws IOException {
			this.filex = file;
			FileSourceFactory f = file.getFileSourceFactory();
			isLocal = FileProxyFactory.FACTORY_ID.equals(f.getTypeId());
			id = f.getTypeId();
			// a copy, so filling in a secret never changes the factory's own properties
			Properties p = f.getConnectProperties();
			if( p != null ) {
				prop.putAll(p);
			}
			path = file.getCanonicalPath();
		}

		static final String illegalChar =""+ AMP+NL+COMMA+EQ+TILDE+VBAR;

		static String encode(String str) {
			StringBuilder ret = new StringBuilder();
			for(char c : str.toCharArray()) {
				if( illegalChar.indexOf(c)>=0) {
					String tmp = (Integer.toHexString(c).toUpperCase());
					if( tmp.length()==1) {
						tmp = "0"+tmp;
					}
					ret.append(AMP+tmp);
				} else {
					ret.append(c);
				}
			}

			return ret.toString();
		}

		static String decode(String str) {
			StringBuilder ret = new StringBuilder();
			char array [] = str.toCharArray();
			for (int idx = 0; idx < array.length; idx++) {
				if( array[idx] == AMP.charAt(0) && idx+2 < array.length ) {
					ret.append((char)Integer.parseInt(""+array[++idx]+array[++idx], 16));
				} else {
					ret.append(array[idx]);
				}
			}

			return ret.toString();
		}

		@Override
		public int hashCode() {
			return Objects.hash(id, path);
		}

		@Override
		public boolean equals(Object obj) {
			boolean ret = false;
			if (obj instanceof ListEntry) {
				ListEntry le = (ListEntry) obj;
				ret = Objects.equals(le.id, id) && Objects.equals(le.path, path);
			}
			return ret;
		}
	}

	private final Preferences prefs;
	private List<ListEntry> recentFiles;
	private int maxFiles = -1;
	private SecretPrompter secretPrompter = this::askForSecret;

	/**
	 * RecentFileMenu uses java.util.prefs.Preferences to store information.
	 * The java.util.prefs.Preferences uses the targetClass to identify the correct storage location.
	 *
	 * @param targetClass
	 * @throws IOException
	 */
	public RecentFileMenu(Class<?> targetClass) throws IOException {
		this(Preferences.userNodeForPackage(targetClass));
	}

	/** Saves the list in the given preferences node. */
	public RecentFileMenu(Preferences prefs) throws IOException {
		super("Recent Files");
		this.prefs = Objects.requireNonNull(prefs, "prefs");
		buildRecentMenu();
	}

	/** Sets how secrets are asked for; null restores the default (a password dialog). */
	public void setSecretPrompter(SecretPrompter prompter) {
		secretPrompter = prompter == null ? this::askForSecret : prompter;
	}

	/** @return the entries, newest first, without connecting to anything */
	public List<ListEntry> getRecentEntries() {
		return Collections.unmodifiableList(recentFiles);
	}

	/**
	 * Opens an entry, first asking for any secret that wasn't saved.
	 * If the open fails, the secrets just entered are forgotten so they are asked for again.
	 *
	 * @return the file, or null if a prompt was cancelled
	 */
	public FileSource openEntry(ListEntry entry) throws IOException {
		List<String> asked = new ArrayList<>();
		for(String name : entry.getMissingSecrets()) {
			String value = secretPrompter.prompt(entry, name);
			if( value == null ) {
				forget(entry, asked);
				return null;
			}
			entry.prop.setProperty(name, value);
			asked.add(name);
		}
		try {
			return entry.getFile();
		} catch (IOException | RuntimeException e) {
			forget(entry, asked);
			throw e;
		}
	}

	private static void forget(ListEntry entry, List<String> names) {
		for(String name : names) {
			entry.prop.setProperty(name, "");
		}
	}

	private String askForSecret(ListEntry entry, String name) {
		JPasswordField field = new JPasswordField(20);
		String where = entry.isLocal ? entry.path : entry.id+":"+entry.path;
		int answer = JOptionPane.showConfirmDialog(this, new Object[] {where, name, field},
				"Enter "+name, JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
		if( answer != JOptionPane.OK_OPTION ) {
			return null;
		}
		char[] value = field.getPassword();
		try {
			return new String(value);
		} finally {
			Arrays.fill(value, '\0');
		}
	}

	private List<ListEntry> readRecentList() throws IOException {
		List<ListEntry> ret = parse(prefs.get(PREF_RECENT_LIST, null));
		String legacy = prefs.get(PREF_LEGACY_RECENT_LIST, null);
		if( legacy != null ) {
			if( ret.isEmpty() && !legacy.isEmpty() ) {
				try {
					ret = parse(legacyDecrypt(legacy));
				} catch (GeneralSecurityException | RuntimeException e) {
					// unreadable (e.g. user.name changed): the old list is dropped
				}
			}
			// save without secrets first, then remove the old value
			store(ret);
			prefs.remove(PREF_LEGACY_RECENT_LIST);
			flush();
		}

		return ret;
	}

	private static List<ListEntry> parse(String text) {
		List<ListEntry> ret = new ArrayList<>();
		if( text != null ) {
			for(String line : text.split(NL)) {
				if( line.isEmpty() ) {
					continue;
				}
				try {
					ListEntry e = new ListEntry(line);
					if( !ret.contains(e) ) {
						ret.add(e);
					}
				} catch (RuntimeException e) {
					// skip a malformed line rather than lose the list
				}
			}
		}

		return ret;
	}

	/** Reads the list 1.0.1 and earlier saved (AES/CBC, key = SHA-256 of user.name, fixed IV). Migration only. */
	private static String legacyDecrypt(String cipherText) throws GeneralSecurityException {
		byte[] key = MessageDigest.getInstance("SHA-256").digest(System.getProperty("user.name", "").getBytes(StandardCharsets.UTF_8));
		Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
		cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
				new IvParameterSpec("1234567812345678".getBytes(StandardCharsets.US_ASCII)));

		return new String(cipher.doFinal(Base64.getDecoder().decode(cipherText)), StandardCharsets.UTF_8);
	}

	/**
	 * Opens every entry. Remote entries connect, and may ask for secrets;
	 * entries whose prompt is cancelled are left out.
	 */
	public List<FileSource> getRecentFiles() throws IOException {
		List<FileSource> ret = new ArrayList<>();
		for(ListEntry e : new ArrayList<>(recentFiles)) {
			FileSource file = openEntry(e);
			if( file != null ) {
				ret.add(file);
			}
		}
		return ret;
	}

	public void setRecentFiles(List<FileSource> list) throws IOException {
		List<ListEntry> ret = new ArrayList<>();
		for(FileSource file : list) {
			ListEntry e = new ListEntry(file);
			if( !ret.contains(e) ) {
				ret.add(e);
			}
		}
		recentFiles = ret;
		store(recentFiles);
		buildRecentMenu();
	}

	public void setMaxFiles(int maxRecent) throws IOException {
		maxFiles = maxRecent;
		prefs.putInt(PREF_MAX_FILES, maxRecent);
		flush();
	}

	private void store(List<ListEntry> list) throws IOException {
		StringBuilder buf = new StringBuilder();
		for(ListEntry e : list) {
			String line = e.toString()+NL;
			// a preference value is limited in length; the oldest entries that don't fit are dropped
			if( buf.length()+line.length() > Preferences.MAX_VALUE_LENGTH ) {
				break;
			}
			buf.append(line);
		}
		prefs.put(PREF_RECENT_LIST, buf.toString());
		flush();
	}

	private void flush() throws IOException {
		try {
			prefs.flush();
		} catch (BackingStoreException e) {
			throw new IOException("Can't save preferences", e);
		}
	}

	private void buildRecentMenu() throws IOException {

		removeAll();
		final int mx = getMaxFiles();
		JMenuItem item = new JMenuItem("Max Files:"+mx);
		item.addActionListener((e)->{
			String tmp = JOptionPane.showInputDialog("Max Files", mx);
			if( tmp != null ) {
				try {
					setMaxFiles(Integer.parseInt(tmp.trim()));
					buildRecentMenu();
				} catch (NumberFormatException e2) {
					// not a number: unchanged
				} catch (IOException e2) {
					showError("Can't save the maximum", e2);
				}
			}
		});
		add(item);
		item = new JMenuItem("Clear Recent List");
		item.addActionListener((e)->{
			try {
				setRecentFiles(new ArrayList<FileSource>());
			} catch (IOException e1) {
				showError("Can't clear the recent list",e1);
			}
		});
		add(item);

		if( recentFiles == null ) {
			recentFiles = readRecentList();
		}

		// Local files that are gone are dropped. Remote entries aren't checked: that
		// would connect to every server (and ask for passwords) just to draw the menu.
		boolean changed = recentFiles.removeIf(RecentFileMenu::isMissingLocalFile);

		for(ListEntry entry : recentFiles) {
			item = new JMenuItem(entry.isLocal ? entry.path : entry.id+":"+entry.path);
			item.addActionListener((e)->open(entry, e));
			add(item);
		}

		if( changed ) {
			store(recentFiles);
		}
	}

	private static boolean isMissingLocalFile(ListEntry entry) {
		if( !entry.isLocal ) {
			return false;
		}
		try {
			return !entry.getFile().exists();
		} catch (IOException e) {
			return true;
		}
	}

	private void open(ListEntry entry, ActionEvent e) {
		FileSource file;
		try {
			file = openEntry(entry);
		} catch (IOException e1) {
			showError("Can't open "+entry.path, e1);
			return;
		}
		if( file == null ) {
			return;
		}
		try {
			addRecent(file);
		} catch (IOException e1) {
			showError("Can't save the recent list", e1);
		}
		e.setSource(file);
		for(java.awt.event.ActionListener l : getActionListeners()) {
			l.actionPerformed(e);
		}
	}

	private void showError(String string, Exception e1) {
		JOptionPane.showMessageDialog(this, e1, string, JOptionPane.ERROR_MESSAGE);
	}

	public void addRecent(FileSource file) throws IOException {
		ListEntry entry = new ListEntry(file);
		int idx = recentFiles.indexOf(entry);
		if( idx >=0 ) {
			ListEntry old = recentFiles.remove(idx);
			// keep secrets entered this session if the factory doesn't report them
			for(String name : old.prop.stringPropertyNames()) {
				if( FileSourceFactory.isSecretProperty(name) && entry.prop.getProperty(name, "").isEmpty() ) {
					entry.prop.setProperty(name, old.prop.getProperty(name));
				}
			}
		}
		recentFiles.add(0, entry);

		int mx = getMaxFiles();

		while(recentFiles.size()>mx && !recentFiles.isEmpty()) {
			recentFiles.remove(recentFiles.size()-1);
		}

		store(recentFiles);
		buildRecentMenu();
	}

	public int getMaxFiles() {
		if( maxFiles < 0 ) {
			maxFiles = prefs.getInt(PREF_MAX_FILES, 10);
		}
		return maxFiles;
	}

}
