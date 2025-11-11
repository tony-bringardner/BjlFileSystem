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
import java.awt.event.ActionListener;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;

import us.bringardner.io.filesource.fileproxy.FileProxyFactory;

public class RecentFileMenu extends JMenu {


	private static final long serialVersionUID = 1L;
	private static final String NL = "\n";
	private static final String AMP = "&";
	private static final String COMMA= ",";
	private static final String EQ= "=";
	private static final String TILDE= "~";
	private static final String VBAR= "|";
	
	
	private static final String PREF_RECENT_LIST = "RecentList";
	private static final String PREF_MAX_FILES = "MaxRecentFiles";
	private static String algorithm = "AES/CBC/PKCS5Padding";
	private static IvParameterSpec iv = new IvParameterSpec("1234567812345678".getBytes());
	

	public static class ListEntry {
		public String id;
		public Properties prop = new Properties();
		public String path;
		public boolean isLocal=true;
		private FileSource filex;
		
		public FileSource getFile() throws IOException {
			if( filex == null ) {
				FileSourceFactory f = FileSourceFactory.getFileSourceFactory(id);
				if( !f.connect(prop)) {
					throw new IOException(""+id+" with "+prop);
				}
				filex = f.createFileSource(path);
			}
			
			return filex;
		}
		
		public String toString() {
			
			StringBuilder buf = new StringBuilder();
			
			
			for(Object name : prop.keySet()) {
				Object val = prop.get(name);
				String val2 = name.toString()+EQ+encode( val.toString());
				if(buf.length()>0) {
					buf.append(COMMA);
				}
				buf.append(val2);
			}
			String ret = id+VBAR+buf+VBAR+path;
			
			
			return ret;
		}
		
		public ListEntry(String line) {
			String parts[] = line.split("\\"+VBAR);
			id = parts[0];
			path = parts[2];
			prop.clear();
			
			for(String str : parts[1].split(COMMA)) {

				String parts2[] = str.split(EQ);
				if( parts2.length>1) {
					String name = parts2[0];
					String val = decode(parts2[1]);
					prop.setProperty(name, val);
				}
			}
		}

		public ListEntry(FileSource file) throws IOException {
			this.filex = file;
			FileSourceFactory f = file.getFileSourceFactory();
			isLocal = FileProxyFactory.FACTORY_ID.equals(f.getTypeId());
			id = f.getTypeId();
			prop = f.getConnectProperties();
			path = file.getCanonicalPath();
			
			
		}
		
		static final String illegalChar =""+ AMP+NL+COMMA+EQ+TILDE; 
		String encode(String str) {			
			StringBuilder ret = new StringBuilder();
			for(char c : str.toCharArray()) {
				if( illegalChar.indexOf(c)>=0) {
						String tmp = (Integer.toHexString(c).toUpperCase());
						if( tmp.length()==1) {
							tmp = "0"+tmp;
						}
					ret.append(""+AMP+tmp);
				} else {					
					ret.append(c);
				}
			}
			
			return ret.toString();
		}

		String decode(String str) {
			StringBuilder ret = new StringBuilder();
			char array [] = str.toCharArray();
			int amp = AMP.charAt(0);
			for (int idx = 0; idx < array.length; idx++) {
				if( array[idx] != amp) {
					ret.append(array[idx]);
				} else {
					ret.append((char)Integer.parseInt(""+array[++idx]+array[++idx], 16));
				}
				
			}
			
			return ret.toString();
		}
		
		@Override
		public boolean equals(Object obj) {
			boolean ret = false;
			if (obj instanceof ListEntry) {
				ListEntry le = (ListEntry) obj;
				ret = le.id .equals(id) && le.path.equals(path);
			}
			return ret;
		}
	}
	
	private static SecretKeySpec generateAesKeyFromPassphrase() throws Exception {
	    MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
	    byte[] keyBytes = sha256.digest(System.getProperty("user.name").getBytes(StandardCharsets.UTF_8));
	    return new SecretKeySpec(keyBytes, "AES");
	}
	
	public static String decrypt(String cipherText) throws Exception {

		Cipher cipher = Cipher.getInstance(RecentFileMenu.algorithm);
		cipher.init(Cipher.DECRYPT_MODE, generateAesKeyFromPassphrase(), iv);
		byte[] plainText = cipher.doFinal(Base64.getDecoder().decode(cipherText));
		
		return new String(plainText);
	}

	
	public static String encrypt(String input) throws Exception {

		Cipher cipher = Cipher.getInstance(RecentFileMenu.algorithm);
		cipher.init(Cipher.ENCRYPT_MODE, generateAesKeyFromPassphrase(), iv);
		byte[] cipherText = cipher.doFinal(input.getBytes());
		return Base64.getEncoder().encodeToString(cipherText);
	}
	
	
	private Preferences prefs ;
	private List<ListEntry> recentFiles;
	private int maxFiles = -1;

	static final String illegalChar = "=&,~\n"; 
	String encode(String str) {
		StringBuilder ret = new StringBuilder();
		for(char c : str.toCharArray()) {
			if( illegalChar.indexOf(c)>=0) {
					String tmp = (Integer.toHexString(c).toUpperCase());
					if( tmp.length()==1) {
						tmp = "0"+tmp;
					}
				ret.append("&"+tmp);
			} else {					
				ret.append(c);
			}
		}
		
		return ret.toString();
	}

	String decode(String str) {
		StringBuilder ret = new StringBuilder();
		char array [] = str.toCharArray();
		for (int idx = 0; idx < array.length; idx++) {
			if( array[idx] != '&') {
				ret.append(array[idx]);
			} else {
				ret.append((char)Integer.parseInt(""+array[++idx]+array[++idx], 16));
			}
			
		}
		
		return ret.toString();
	}
	

	/**
	 * RecentFileMenu uses java.util.prefs.Preferences to store information.
	 * The java.util.prefs.Preferences uses the tagetClass to identify the correct storage location.
	 * 
	 * @param targetClass
	 * @throws IOException 
	 */
	public RecentFileMenu(Class<?> targetClass) throws IOException {
		super("Recent Files");
		prefs = Preferences.userNodeForPackage(targetClass);
		buildRecentMenu();
	}

	private List<ListEntry> readRecentList() throws Exception {
		List<ListEntry> ret = new ArrayList<>();
		prefs.clear();
		String tmp = prefs.get(PREF_RECENT_LIST, null);
		
		if( tmp != null && !tmp.isEmpty()) {
			tmp = decrypt(tmp);
			for(String line : tmp.split(""+NL)) {		
				
				ListEntry e = (new ListEntry(line));	
				if( e.id != null ) {
					ret.add(e);
				}
			}
		}

		return ret;
	}

	
	
	
	public List<FileSource> getRecentFiles() throws IOException {
		List<FileSource> ret = new ArrayList<>();
		for(ListEntry e : recentFiles) {
			ret.add(e.getFile());
		}
		return ret;
	}

	public void setRecentFiles(List<FileSource> list) throws IOException {
		List<ListEntry> ret = new ArrayList<>();
		for(FileSource file : list) {
			ret.add(new ListEntry(file));
		}
		//  I wonder is we should save the new list here???
		this.recentFiles =ret;
		try {
			saveRecentList(recentFiles);
		} catch (Exception e) {
			throw new IOException(e);
		}
		buildRecentMenu();
	}

	
	public void setMaxFiles(int maxRecent) throws IOException {
		
		maxFiles = maxRecent;
		prefs.putInt(PREF_MAX_FILES, maxRecent);
		try {
			prefs.flush();
		} catch (BackingStoreException e) {
			throw new IOException("Can't save preferencec",e);
		}
	}

	private void saveRecentList(List<ListEntry> recentFiles2) throws Exception {
		StringBuffer buf = new StringBuffer();
		for(ListEntry e : recentFiles2) {
			buf.append(e.toString());
			buf.append(NL);
		}
		String enc = buf.length()==0? "" : encrypt( buf.toString());
		
		prefs.put(PREF_RECENT_LIST,enc);

		try {
			prefs.flush();
		} catch (BackingStoreException e) {
			throw new IOException("Can't save preferencec", e);
		}
		recentFiles = null;
		buildRecentMenu();
	}

	private void buildRecentMenu() throws IOException {

		removeAll();
		final int mx = getMaxFiles();
		JMenuItem item = new JMenuItem("Max Files:"+mx);
		item.addActionListener((e)->{
			String tmp = JOptionPane.showInputDialog("Max Files", mx);
			if( tmp != null ) {
				try {
					setMaxFiles(Integer.parseInt(tmp));
					buildRecentMenu();
				} catch (Exception e2) {
				}
			}
		});
		add(item);
		item = new JMenuItem("Clear Recent List");
		item.addActionListener((e)->{
			try {
				setRecentFiles(new ArrayList<FileSource>());
			} catch (IOException e1) {
				showError("Can't cler recent list",e1);
			}
			
		});
		add(item);
		
		List<Integer> obsolet = new ArrayList<Integer>();
		if( recentFiles == null ) {
			try {
				recentFiles = readRecentList();
			} catch (Exception e1) {
				throw new IOException(e1);
			}
		}

		for(int idx=0,sz=recentFiles.size(); idx<sz;idx++) {
			final ListEntry entry = recentFiles.get(idx);
			final FileSource file = entry.getFile();
			
			if( !file.exists()) {
				obsolet.add(0, idx);
			} else {
				String tmp = entry.isLocal?entry.path:entry.id+":"+entry.path;
				
				item = new JMenuItem(tmp);
				add(item);
				item.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						System.out.println("event = "+e);
						try {
							addRecent(file);
						} catch (IOException e1) {
							e1.printStackTrace();
						}
						e.setSource(file);
						for(ActionListener l : getActionListeners()) {
							l.actionPerformed(e);
						}						
					}
				});	
			}
		}
		
		for(int idx : obsolet) {
			recentFiles.remove(idx);
		}
		if( obsolet.size()>0) {			
			try {
				saveRecentList(recentFiles);
			} catch (Exception e1) {
				throw new IOException(e1);
			}			
		}
	
	}
	
	
	private void showError(String string, IOException e1) {
		JOptionPane.showMessageDialog(this, e1, string, JOptionPane.ERROR_MESSAGE);
	}

	public void addRecent(FileSource file) throws IOException {
		ListEntry entry = new ListEntry(file);
		int idx = recentFiles.indexOf(entry);
		if( idx >=0 ) {
			recentFiles.remove(idx);
		}
		recentFiles.add(0, entry);

		int mx = getMaxFiles();

		while(recentFiles.size()>mx) {
			recentFiles.remove(mx);
		}
		
		try {
			saveRecentList(recentFiles);
		} catch (Exception e) {
			throw new IOException(e);
		}
		buildRecentMenu();
	}


	public int getMaxFiles() {
		if( maxFiles < 0 ) {
			maxFiles = prefs.getInt(PREF_MAX_FILES, 10);			
		}
		return maxFiles;
	}



}
