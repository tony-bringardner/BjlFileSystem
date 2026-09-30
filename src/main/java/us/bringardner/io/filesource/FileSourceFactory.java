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
 * ~version~V000.01.21-V000.01.17-V000.01.16-V000.01.14-V000.01.11-V000.01.09-V000.01.05-V000.01.01-V000.01.00-V000.00.01-V000.00.00-
 */
/*
 * Created on Dec 13, 2004
 *
 */
package us.bringardner.io.filesource;

import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.nio.file.FileSystems;
import java.nio.file.Paths;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.Collections;

import us.bringardner.core.BaseObject;
import us.bringardner.core.util.LogHelper;
import us.bringardner.io.filesource.fileproxy.FileProxyFactory;

/**
 * A factory to create FileSources.  This will allow the same application code
 * to use many different type of Files without the knowledge at
 * compile time.
 * 
 * @author Tony Bringardner
 *
 */
public abstract class FileSourceFactory extends BaseObject implements URLStreamHandlerFactory, Serializable {

	public static void main(String [] args) throws IOException {
		getDefaultFactory().whoAmI();
	}


	private static final String DOT = ".";
	private static final String DOT_DOT = "..";

	public static String expandDots(String pathStr,char seperator) {
		String path = pathStr.trim();

		if( path.isEmpty() || path.equals(DOT_DOT)) {
			return path;
		}

		if( path.equals(DOT)) {
			return "";
		}

		if( File.separatorChar != seperator) {
			path = path.replace(seperator,File.separatorChar);
		}

		String ret = Paths.get(path).normalize().toString();

		if( File.separatorChar != seperator) {
			ret = ret. replace(File.separatorChar,seperator);
		}

		return ret;
	}



	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	public static final String FILE_SOURCE_PROTOCOL = "filesource";
	public static final String QUERY_STRING_SOURCE_TYPE = "sourcetype";
	public static final String QUERY_STRING_SESSION_ID = "sessionId";

	public static final FileSourceFactory fileProxyFactory = new FileProxyFactory();
	public static final String PROP_JAVA_PROTOCOL_HANDLER_PKGS = "java.protocol.handler.pkgs";

	private static int _sessionId = 0;

	private static synchronized int getNextId() {
		return _sessionId++;
	}


	/**
	 * A connected factory, addressable by id (the sessionId URL parameter).
	 * 
	 * The factory is held weakly: a session lives as long as something
	 * (typically the FileSource objects it created) still uses the factory,
	 * or until the factory is disconnected. Previously every connect() added
	 * an entry that was never removed, so e.g. each FileSourceFactory.getFileSource(URL)
	 * call leaked one.
	 */
	private static class FactorySession {
		final int id;
		final String key;
		final WeakReference<FileSourceFactory> factory;

		FactorySession(int id, FileSourceFactory factory) {
			this.id = id;
			this.factory = new WeakReference<>(factory);
			StringBuilder buf = new StringBuilder(factory.getTypeId()+":");
			Properties p = factory.getConnectProperties();
			for(String name : p.stringPropertyNames()) {
				if(! "password".equalsIgnoreCase(name)) {
					buf.append(name+"="+p.getProperty(name)+";");
				}
			}
			key = buf.toString();
		}

		FileSourceFactory get() {
			return factory.get();
		}
	}

	/** All access is synchronized on this map. */
	private static final Map<Integer,FactorySession> sessions = new HashMap<>();

	/** Drop sessions whose factory has been garbage collected. Caller holds the lock. */
	private static void purgeCollectedSessions() {
		sessions.values().removeIf(s -> s.get() == null);
	}

	/**
	 * @return the number of registered (connected, still referenced) factory sessions.
	 */
	public static int getActiveSessionCount() {
		synchronized (sessions) {
			purgeCollectedSessions();
			return sessions.size();
		}
	}

	private volatile static FileSourceFactory defaultFactory;
	/** Extension -> MIME type. Concurrent: addMimeType may be called at any time. */
	private static final Map<String, String> types = new ConcurrentHashMap<>();
	private static LogHelper logger = new LogHelper(FileSourceFactory.class);
	private volatile int sessionId=-1;

	private static ServiceLoader<FileSourceFactory> factoryLoader= ServiceLoader.load(FileSourceFactory.class);

	static {


		//  Make sure this is set
		//-Djava.protocol.handler.pkgs=us.bringardner.io
		String tmp = System.getProperty(PROP_JAVA_PROTOCOL_HANDLER_PKGS);
		for(String pkg : getAllHandlerPkgs().split("[|]")) {
			if( tmp == null || tmp.isEmpty() ) {
				tmp = pkg;
			} else if( !Arrays.asList(tmp.split("[|]")).contains(pkg) ) {
				tmp = tmp+"|"+pkg;
			}
		}

		System.setProperty(PROP_JAVA_PROTOCOL_HANDLER_PKGS, tmp);
		logger.logInfo("Set handlers to "+tmp);

		// A JVM allows only one URLStreamHandlerFactory. If something else
		// (an app server, Spring Boot, another library) already installed one,
		// setURLStreamHandlerFactory throws an Error; letting that escape this
		// static block made the class unusable (NoClassDefFoundError) for the
		// life of the JVM. Our URLs still resolve through the
		// java.protocol.handler.pkgs property set above.
		try {
			URL.setURLStreamHandlerFactory(fileProxyFactory);
		} catch (Error | SecurityException e) {
			logger.logInfo("A URLStreamHandlerFactory is already installed ("+e.getMessage()
					+ "); using "+PROP_JAVA_PROTOCOL_HANDLER_PKGS+" for FileSource URLs");
		}
		// register some basic types
		types.put("htm","text/html");
		types.put("html","text/html");
		types.put("css","text/css");
		types.put("js","text/javascript");
		types.put("jpg","image/jpeg");
		types.put("gif","image/gif");
		types.put("txt","text/plain");
	}

	private FileSourceUser localPrinciple;

	/**
	 * 
	 */
	public FileSourceFactory() {
		super();
	}




	/**
	 * Package prefixes for java.protocol.handler.pkgs, '|' separated.
	 * The JDK looks for &lt;prefix&gt;.&lt;protocol&gt;.Handler, so
	 * "us.bringardner.io" finds us.bringardner.io.filesource.Handler (filesource:)
	 * and "us.bringardner.io.filesource" finds the per-factory handlers such as
	 * us.bringardner.io.filesource.memory.Handler (memory:).
	 * (This used to return Package.toString(), i.e. "package us.bringardner.io.filesource",
	 * which the JDK could never use.)
	 */
	public static String getAllHandlerPkgs() {
		String pkg = FileSourceFactory.class.getPackage().getName();   // us.bringardner.io.filesource
		String parent = pkg.substring(0, pkg.lastIndexOf('.'));         // us.bringardner.io
		return parent+"|"+pkg;
	}

	/*
	 * Create a FileSourceFileSystemProvider identified by the URL given by 'uri'
	 */
	public static FileSourceFactory getFileSourceFactory(URI uri) throws IOException  {

		FileSourceUri fsuri = new FileSourceUri(uri);
		String tmp = fsuri.getSessionId();
		if( tmp != null ) {
			try {
				int sessionid = Integer.parseInt(tmp);
				FileSourceFactory found = null;
				synchronized (sessions) {
					FactorySession session = sessions.get(sessionid);
					if( session != null ) {
						found = session.get();
						if( found == null ) {
							sessions.remove(sessionid);
						}
					}
				}
				if( found != null ) {
					return found;
				} else if( sessionid < 0 ) {
					// Negative ids have always meant the shared local factory.
					return fileProxyFactory;
				}
			} catch (NumberFormatException e) {
				// not a session id; resolve by factory id below
			}
		}

		String id = fsuri.getFactoryId();

		if( id == null ) {
			throw new IOException("Invalid URI="+uri);
		}

		return getFileSourceFactory(id);

	}

	/*
	 * Create a FileSource identified by the URL given by 'url'
	 */
	
	public static FileSource getFileSource(String url) throws IOException {
		return getFileSource(new URL(url));
	}

	/*
	 * List all of the roots of this file system
	 */
	public abstract FileSource [] listRoots() throws IOException;

	//get Current Directory
	public abstract FileSource getCurrentDirectory() throws IOException;

	//
	// Temporary files (java.io.File.createTempFile)
	//

	private static final java.security.SecureRandom TEMP_RANDOM = new java.security.SecureRandom();

	/**
	 * Where createTempFile(prefix, suffix) and createTempDirectory(prefix)
	 * put their files. By default the current directory; the local file
	 * system uses java.io.tmpdir and the memory file system /tmp. Remote
	 * implementations can override this with a server temp directory.
	 */
	public FileSource getTempDirectory() throws IOException {
		return getCurrentDirectory();
	}

	/** createTempFile(prefix, suffix, null): in getTempDirectory(). */
	public FileSource createTempFile(String prefix, String suffix) throws IOException {
		return createTempFile(prefix, suffix, null);
	}

	/**
	 * Creates a new, empty file with a unique name, as
	 * java.io.File.createTempFile does: the name is the prefix, a random
	 * number and the suffix. Combine with deleteOnExit() to have it removed
	 * automatically.
	 * <p>
	 * Uniqueness relies on createNewFile(), which is atomic for local and
	 * memory files; on a file system where it checks and then creates (FTP),
	 * two clients could in principle pick the same random name at once.
	 *
	 * @param prefix at least three characters
	 * @param suffix e.g. ".txt"; null means ".tmp"
	 * @param directory where to create it (from this factory), or null for getTempDirectory()
	 * @return the new file
	 * @throws IllegalArgumentException if the prefix is shorter than three characters, or
	 *         the directory belongs to another factory
	 * @throws IOException if the directory doesn't exist, the prefix or suffix contains a
	 *         path separator, or no unique name could be created
	 */
	public FileSource createTempFile(String prefix, String suffix, FileSource directory) throws IOException {
		if( prefix == null || prefix.length() < 3 ) {
			throw new IllegalArgumentException("Prefix string \""+prefix+"\" too short: length must be at least 3");
		}
		return createTemp(prefix, suffix == null ? ".tmp" : suffix, directory, false);
	}

	/** createTempDirectory(prefix, null): in getTempDirectory(). */
	public FileSource createTempDirectory(String prefix) throws IOException {
		return createTempDirectory(prefix, null);
	}

	/**
	 * Creates a new directory with a unique name (the prefix, which may be
	 * null or empty, and a random number), as
	 * java.nio.file.Files.createTempDirectory does.
	 *
	 * @param directory where to create it (from this factory), or null for getTempDirectory()
	 */
	public FileSource createTempDirectory(String prefix, FileSource directory) throws IOException {
		return createTemp(prefix == null ? "" : prefix, "", directory, true);
	}

	private FileSource createTemp(String prefix, String suffix, FileSource directory, boolean dir) throws IOException {
		if( directory == null ) {
			directory = getTempDirectory();
			if( !directory.exists() ) {
				directory.mkdirs();
			}
		} else if( directory.getFileSourceFactory() != this ) {
			throw new IllegalArgumentException(directory.getAbsolutePath()+" belongs to another factory");
		}
		if( !directory.isDirectory() ) {
			throw new IOException("Not a directory: "+directory.getAbsolutePath());
		}
		String sep = String.valueOf(getSeperatorChar());
		if( prefix.contains(sep) || suffix.contains(sep) || prefix.contains("/") || suffix.contains("/") ) {
			throw new IOException("Unable to create temporary file: prefix and suffix can't contain a path separator");
		}
		for(int attempt = 0; attempt < 100; attempt++ ) {
			String name = prefix + Long.toUnsignedString(TEMP_RANDOM.nextLong()) + suffix;
			FileSource candidate = directory.getChild(name);
			boolean created = dir ? (!candidate.exists() && candidate.mkdir()) : candidate.createNewFile();
			if( created ) {
				return candidate;
			}
		}
		throw new IOException("Unable to create a unique temporary "+(dir ? "directory" : "file")+" in "+directory.getAbsolutePath());
	}

	/*
	 * Create a FileSource identified by 'name' as a sub-directory of 'parent'
	 */

	public abstract boolean isVersionSupported();

	/*
	 * Set the current directory for the type of FileSource.
	 * 
	 * While Java does not allow you to change the current
	 * directory of the File Object, it uses the underlying 
	 * file system to resolve names and relative names are
	 * resolved based on some current directory.  Most implementations 
	 * of FileSource will need to, and be able to set this value.
	 */
	public abstract void setCurrentDirectory(FileSource dir) throws IOException;

	private static String os = System.getProperty("os.name").toLowerCase();

	public static boolean isWindows() {
		return os.contains("win");		
	}


	public int getSessionId() {
		return sessionId;
	}




	/** type id (trimmed, lower case) -> factory class, in ServiceLoader order. Built once. */
	private static volatile Map<String, Class<? extends FileSourceFactory>> registry;

	private static Map<String, Class<? extends FileSourceFactory>> registry() {
		Map<String, Class<? extends FileSourceFactory>> ret = registry;
		if( ret == null ) {
			synchronized (FileSourceFactory.class) {
				ret = registry;
				if( ret == null ) {
					ret = new LinkedHashMap<>();
					for (FileSourceFactory fsf : factoryLoader) {
						ret.putIfAbsent(fsf.getTypeId().trim().toLowerCase(), fsf.getClass());
					}
					registry = ret = Collections.unmodifiableMap(ret);
				}
			}
		}
		return ret;
	}

	/** @return true if a factory with this type id is registered */
	public static boolean isRegisteredFactory(String factory_id) {
		return factory_id != null && registry().containsKey(factory_id.trim().toLowerCase());
	}

	/**
	 * Create a new factory for the given type id, or null if there is none.
	 * (This used to iterate the ServiceLoader -- which isn't thread-safe --
	 * on every call.)
	 */
	public static FileSourceFactory getFileSourceFactory(String factory_id){
		FileSourceFactory ret = null;
		if( factory_id  != null ) {
			factory_id = factory_id.trim().toLowerCase();
			Class<? extends FileSourceFactory> cls = registry().get(factory_id);
			if( cls != null ) {
				try {
					ret = cls.getDeclaredConstructor().newInstance();
					logger.logDebug("Created "+ret.getClass()+" as "+factory_id);
				} catch (Exception e) {
					logger.logError("Can't create factory for "+factory_id,e);				
				}
			}
		}

		return ret;
	}

	/*
	 * Get the default FileSource Factory
	 */
	/**
	 * (This initialised the shared field without a lock, and assigned it
	 * before checking the result, so another thread could briefly see null
	 * or an unwanted factory.)
	 */
	public static FileSourceFactory getDefaultFactory() {
		FileSourceFactory ret = defaultFactory;
		if( ret == null ){
			synchronized (FileSourceFactory.class) {
				ret = defaultFactory;
				if( ret == null ) {
					String tmp = System.getProperty("FileSource.default");
					if( tmp != null ){
						ret = getFileSourceFactory(tmp);
						if( ret == null) {
							logger.logError("Invalid FileSource.default = "+tmp+"; using "+FileProxyFactory.FACTORY_ID);
						}
					}
					if( ret == null ) {
						ret = fileProxyFactory;
					}
					defaultFactory = ret;
				}
			}
		}

		return ret;
	}

	public static String [] getRegisterdFactories() {
		List<String> ret = new ArrayList<String>();
		for (Class<? extends FileSourceFactory> cls : registry().values()) {
			try {
				ret.add(cls.getDeclaredConstructor().newInstance().getTypeId());
			} catch (ReflectiveOperationException e) {
				logger.logError("Can't create factory "+cls.getName(), e);
			}
		}

		return ret.toArray(new String[ret.size()]);
	}

	/*
	 * Set the default FileSource Factory
	 */

	public static void setDefaultFactory(FileSourceFactory defaultFactory) {
		FileSourceFactory.defaultFactory = defaultFactory;
	}

	public abstract FileSource createFileSource(String fullPath) throws IOException;

	public FileSource createFileSource1(String fullPath) throws IOException {
		FileSource ret = null;
		FileSource cwd = getCurrentDirectory();
		boolean abs = fullPath.startsWith("/");
		if( isWindows()) {
			abs = fullPath.length()>1 && Character.isAlphabetic(fullPath.charAt(0)) && fullPath.charAt(1) == ':';
		}
		
		
		String realPath = fullPath;
		if( cwd != null && !abs) {
			char sep = getSeperatorChar();
			String tmp = cwd.getAbsolutePath();
			if( tmp.endsWith(""+sep)) {
				realPath = tmp+fullPath;
			} else {
				realPath = tmp+sep+fullPath;
			}
		}
		
		FileSource root=null;
		
		for(FileSource tmp: listRoots()) {
			String rootPath =tmp.getAbsolutePath();
			if( realPath.startsWith(rootPath)) {
				ret = root = tmp;
				realPath = realPath.substring(rootPath.length());
				break;
			}
		}
		
		if(root !=null &&  !realPath.isEmpty()) {
			ret = root.getChild(realPath);
		}
	
		return ret;
		
	}
	
	/**
	 * Whether a connection property holds a secret, going by its name (case-insensitive):
	 * it contains password, passwd, passphrase, secret, token or credential, or ends with
	 * privatekey or sessionkey. Secrets are masked when edited and never saved by
	 * {@link RecentFileMenu}, so factories should name such properties to match.
	 */
	public static boolean isSecretProperty(String name) {
		if( name == null ) {
			return false;
		}
		String n = name.toLowerCase(java.util.Locale.ROOT);
		return n.contains("password") || n.contains("passwd") || n.contains("passphrase")
				|| n.contains("secret") || n.contains("token") || n.contains("credential")
				|| n.endsWith("privatekey") || n.endsWith("sessionkey");
	}

	/**
	 * The one definition of "inside" used by {@link FileSource#isChildOfMine(FileSource)}.
	 * <p>
	 * Both arguments must be canonical paths (absolute, "." and ".." resolved, links
	 * resolved where the file system has them). Returns true if child equals parent or is
	 * below it, comparing whole path elements, so /x/ab is not inside /x/a.
	 * Both "/" and "\\" count as separators. Static, so it can't be overridden.
	 *
	 * @param parentCanonical canonical path of the directory
	 * @param childCanonical canonical path of the candidate
	 * @return true if childCanonical is parentCanonical or below it
	 */
	public static boolean isSameOrDescendant(String parentCanonical, String childCanonical) {
		if( parentCanonical == null || childCanonical == null || parentCanonical.isEmpty() ) {
			return false;
		}
		if( childCanonical.equals(parentCanonical) ) {
			return true;
		}
		if( !childCanonical.startsWith(parentCanonical) ) {
			return false;
		}
		char last = parentCanonical.charAt(parentCanonical.length()-1);
		if( last == '/' || last == '\\' ) {
			return true;   // the parent is a root such as "/" or "C:\\"
		}
		char next = childCanonical.charAt(parentCanonical.length());
		return next == '/' || next == '\\';
	}

	/**
	 * @param other another factory
	 * @return true if files from other are in the same file system (the same tree of
	 * paths) as files from this factory, so their paths can be compared.
	 * The default is identity. Override when separate factory instances share one file
	 * system (e.g. every local-disk factory, or two sessions to the same SFTP host, port
	 * and user).
	 */
	public boolean isSameFileSystem(FileSourceFactory other) {
		return other == this;
	}

	/*
	 * Return the type id (File / JbdcFile / just like URL prototype)
	 */
	public abstract String getTypeId() ;


	/**
	 * Most FileSourceFactories represent a remote file system.
	 * 
	 * @return True if the FileSource is local or if currently connected 
	 * a remote file system.
	 */
	public abstract boolean isConnected();

	/**
	 * Most FileSourceFactories represent a remote file system.
	 * 
	 * @return true if successfully connected to a FileSource
	 * @throws IOException 
	 */
	public  boolean connect(Properties prop) throws IOException {
		setConnectionProperties(prop);
		return connect();
	}

	/**
	 * Most FileSourceFactories represent a remote file system.
	 * 
	 * 
	 * @return true if successfully connected to a FileSource
	 * @throws IOException 
	 */
	public  boolean connect() throws IOException {		
		boolean ret = isConnected();
		if( !ret || getTypeId().equals(FileProxyFactory.FACTORY_ID)) {
			ret = connectImpl();
			if( ret ) {
				registerSession();
			}
		}
		return ret;
	}

	/**
	 * Most FileSourceFactories represent a remote file system.
	 * 
	 * 
	 * @return true if successfully connected to a FileSource
	 * @throws IOException 
	 */
	protected abstract boolean connectImpl() throws IOException;

	/**
	 * 
	 * @return Component to edit connection properties or null if no properties are required.
	 */
	public abstract Component getEditPropertiesComponent();

	/**
	 * Disconnect from a remote FileSource
	 * @throws IOException 
	 */
	public  void disConnect() throws IOException {
		try {
			// While the connection is still open: delete what deleteOnExit registered
			if( isConnected() ) {
				deleteFilesRegisteredForExit();
			}
		} catch (RuntimeException e) {
			logger.logError("Deleting deleteOnExit files for "+getTypeId(), e);
		}
		try {
			disConnectImpl();
		} finally {
			synchronized (sessions) {
				if( sessionId >= 0 ) {
					FactorySession s = sessions.get(sessionId);
					if( s != null && s.get() == this ) {
						sessions.remove(sessionId);
					}
				}
				sessionId = -1;
			}
		}
	}

	//
	// deleteOnExit support
	//

	/** Paths registered with deleteOnExit, in registration order. Guarded by pendingDeletes. */
	private transient List<String> deleteOnExitPaths;
	/** Factories with registered paths (identity, strongly held until deleted). */
	private static final java.util.Set<FileSourceFactory> pendingDeletes =
			Collections.newSetFromMap(new java.util.IdentityHashMap<>());
	private static boolean deleteHookInstalled = false;

	/**
	 * Requests that the file be deleted when this factory disconnects, or when
	 * the virtual machine exits, whichever comes first. This is what
	 * FileSource.deleteOnExit() calls; like java.io.File.deleteOnExit(), a
	 * path is registered only once, files are deleted in reverse order of
	 * registration (so a file registered after its directory goes first), a
	 * directory is only deleted if it's empty, and failures are ignored.
	 * <p>
	 * Remote files are deleted at disConnect() because that's the last point
	 * the connection is certainly open. Files of a factory that is never
	 * disconnected are deleted by a shutdown hook, if it's still connected
	 * then. Local files (FileProxy) use java.io.File.deleteOnExit() instead.
	 *
	 * @param file a FileSource created by this factory
	 * @throws IllegalArgumentException if the file belongs to another factory
	 */
	public void deleteOnExit(FileSource file) {
		if( file.getFileSourceFactory() != this ) {
			throw new IllegalArgumentException(file.getAbsolutePath()+" belongs to another factory");
		}
		String path = file.getAbsolutePath();
		synchronized (pendingDeletes) {
			if( deleteOnExitPaths == null ) {
				deleteOnExitPaths = new ArrayList<>();
			}
			if( !deleteOnExitPaths.contains(path) ) {
				deleteOnExitPaths.add(path);
			}
			pendingDeletes.add(this);
			if( !deleteHookInstalled ) {
				Runtime.getRuntime().addShutdownHook(new Thread(FileSourceFactory::deleteAllRegisteredFiles, "FileSource deleteOnExit"));
				deleteHookInstalled = true;
			}
		}
	}

	/** Deletes (and forgets) this factory's registered paths, newest first. */
	private void deleteFilesRegisteredForExit() {
		List<String> paths;
		synchronized (pendingDeletes) {
			paths = deleteOnExitPaths;
			deleteOnExitPaths = null;
			pendingDeletes.remove(this);
		}
		if( paths == null ) {
			return;
		}
		for(int idx = paths.size() - 1; idx >= 0; idx--) {
			try {
				FileSource file = createFileSource(paths.get(idx));
				if( file.exists() ) {
					file.delete();   // a non-empty directory stays, as with java.io.File
				}
			} catch (Exception e) {
				// ignored, as java.io.File.deleteOnExit does
			}
		}
	}

	/** The shutdown hook: every factory that still has registered files and is connected. */
	private static void deleteAllRegisteredFiles() {
		List<FileSourceFactory> factories;
		synchronized (pendingDeletes) {
			factories = new ArrayList<>(pendingDeletes);
		}
		for(FileSourceFactory factory : factories) {
			try {
				if( factory.isConnected() ) {
					factory.deleteFilesRegisteredForExit();
				}
			} catch (Throwable e) {
				// ignored: the VM is exiting
			}
		}
	}

	/**
	 * Register this (connected) factory so URLs carrying its sessionId
	 * resolve back to it. Reconnecting the same factory reuses its session.
	 */
	private void registerSession() {
		synchronized (sessions) {
			purgeCollectedSessions();
			if( sessionId >= 0 ) {
				FactorySession mine = sessions.get(sessionId);
				if( mine != null && mine.get() == this ) {
					return;   // already registered
				}
			}
			FactorySession s = new FactorySession(getNextId(), this);
			if( !(this instanceof FileProxyFactory) ) {
				// Local file access has no real connection, so only warn for others
				for(FactorySession other : sessions.values()) {
					if( other.key.equals(s.key)) {
						logger.getLogger().warn("Factory session already exists for "+s.key);
						break;
					}
				}
			}
			sessions.put(s.id, s);
			sessionId = s.id;
		}
	}

	protected abstract void disConnectImpl() throws IOException;

	/**
	 * Create a copy of the factory that can be used in a thread safe manner.
	 * 
	 * @return
	 */
	public abstract FileSourceFactory createThreadSafeCopy();

	/**
	 * Most FileSourceFactories represent a remote file system.
	 * Typically some information is required such as the host
	 * name of the remote server and a userId / password.  A FileSourceFactory 
	 * must ensure that all required and optional properties are included in
	 * returned Property object.  That will allow clients to do runtime discovery. 
	 *  
	 *  If connected the current values must be returned, if not connected the
	 *  FileSourceFactory should populate the return values of optional 
	 *  Properties with an appropriate value.
	 *  
	 * @return Properties required to connect to the FileSource.  
	 */
	public abstract Properties getConnectProperties();

	/*
	 * The system-dependent path-separator character, represented as a string for convenience. This string contains a single character, namely pathSeparatorChar.
	 * @see java.io.File.pathSeparatorChar
	 */
	public abstract char getPathSeperatorChar(); 

	/*
	 * The system-dependent default name-separator character. This field is initialized to contain the first character of the value of the system property file.separator. On UNIX systems the value of this field is '/'; on Microsoft Windows systems it is '\\'.
	 * @see java.io.File.separatorChar
		See Also:java.lang.System.getProperty(java.lang.String)
	 */
	public abstract char getSeperatorChar(); 


	/* 
	 * Create a Stream for this URL String
	 * @see java.net.URLStreamHandlerFactory#createURLStreamHandler(java.lang.String)
	 */
	private static final Map<String, Optional<Class<?>>> handlerClasses = new ConcurrentHashMap<>();

	private static Optional<Class<?>> findHandlerClass(String pkgs, String protocol) {
		for (String pkg : pkgs.split("[|]")) {
			try {
				Class<?> cls = Class.forName(String.format("%s.%s.Handler", pkg, protocol));
				if( URLStreamHandler.class.isAssignableFrom(cls)) {
					return Optional.of(cls);
				}
			} catch(ClassNotFoundException e) {
				// no handler in this package; try the next one
			}
		}
		return Optional.empty();
	}

	public URLStreamHandler createURLStreamHandler(String protocol) {
		URLStreamHandler ret = null;
		if( protocol.equals(FileSourceFactory.FILE_SOURCE_PROTOCOL)) {
			ret = new us.bringardner.io.filesource.Handler();
		} else {
			//-Djava.protocol.handler.pkgs=us.bringardner.io

			String pkgs = System.getProperty(FileSourceFactory.PROP_JAVA_PROTOCOL_HANDLER_PKGS);
			if( pkgs != null) {
				// Look the class up once per (packages, protocol); it used to
				// call Class.forName for every package on every call.
				Optional<Class<?>> cls = handlerClasses.computeIfAbsent(pkgs+"#"+protocol, k -> findHandlerClass(pkgs, protocol));
				if( cls.isPresent()) {
					try {
						ret = (URLStreamHandler) cls.get().getDeclaredConstructor().newInstance();
					} catch(ReflectiveOperationException e) {
						logger.logError("Can't create "+cls.get().getName(), e);
					}
				}
			}			
		}		


		return ret;
	}

	/*
	 */
	public static String getType(String extention){
		return extention == null ? null : types.get(extention);
	}

	public static void addMimeType(String extension, String type){
		types.put(extension,type);
	}

	/**
	 * Creates a symbolic link to a target (optional operation).
	 * 		The target parameter is the target of the link. It may be an absolute or relative path and may not exist. 
	 * 
	 * @param newFileLink
	 * @param existingFile
	 * @return
	 * @throws IOException
	 */
	public abstract FileSource createSymbolicLink(FileSource newFileLink, FileSource existingFile) throws IOException ;

	/**
	 * Creates a new link (directory entry) for an existing file.
	 * 		The link parameter locates the directory entry to create. 
	 * 		The existing parameter is the path to an existing file. 
	 * 		This method creates a new directory entry for the file so that it can be accessed using link as the path. 
	 * 		
	 * 
	 * @param newFileLink
	 * @param existingFile
	 * @return
	 * @throws IOException
	 */
	public abstract FileSource createLink(FileSource newFileLink, FileSource existingFile) throws IOException ;

	/**
	 * @param url
	 * @return
	 * @throws IOException 
	 */
	public static FileSource getFileSource(URL url) throws IOException {
		FileSource ret=null;

		try {
			URI uri = url.toURI();
			FileSourceFactory factory = getFileSourceFactory(uri);
			if( factory == null ) {
				throw new IOException("No Filesource Factory avilible for id="+url);
			}
			factory.setConnectionProperties(url);
			if( !factory.connect()) {
				throw new IOException("Can't connect to "+url);
			}

			FileSourceUri fsuri = new FileSourceUri(uri);

			//Path path = Paths.get(url.getPath()).normalize();
			String path = fsuri.getPath();
			ret = factory.createFileSource(path);
		} catch (URISyntaxException e) {
			throw new IOException(e);
		}



		return ret;

	}

	/**
	 * Set connection properties based on the information in the URL
	 * 
	 * @param url
	 */
	public abstract void setConnectionProperties(URL url) ;

	/**
	 * @param prop
	 */
	public abstract void setConnectionProperties(Properties prop) ;


	/**
	 * @return a description fit for displaying in clear text
	 */
	public abstract String getTitle() ;


	/**
	 * 
	 * @return a URL that represents this connection including user-id and password
	 */
	public abstract String getURL();

	/** The user running this JVM, looked up once (it can't change). */
	private static volatile FileSourceUser localUser;

	/**
	 * The user running this JVM. Factories for remote systems may override
	 * this to return the remote user.
	 * (The lookup runs an external command. It used to be cached per factory
	 * instance, so every new factory -- e.g. each MemoryFileSourceFactory --
	 * started a process on its first permission check.)
	 */
	public FileSourceUser whoAmI() {
		if( localPrinciple == null ) {
			// a copy: FileSourceUser is mutable (e.g. MemoryFileSource.setGroup changes its owner)
			FileSourceUser shared = lookupLocalUser();
			FileSourceUser mine = new FileSourceUser();
			mine.setId(shared.getId());
			mine.setName(shared.getName());
			mine.setGroup(shared.getGroup());
			mine.setGroups(shared.getGroups());
			localPrinciple = mine;
		}
		return localPrinciple;
	}

	private static FileSourceUser lookupLocalUser() {
		FileSourceUser user = localUser;
		if( user != null ) {
			return user;
		}
		synchronized (FileSourceFactory.class) {
			if( localUser != null ) {
				return localUser;
			}
			user = null;

			//*nix, including macOS,  system use id
			String [] command = {"id"};
			if( isWindows() ) {
				// CSV without headers: parsed by column position, so it doesn't
				// depend on the (localized) labels the list format uses
				command = new String[] {"whoami","/user","/groups","/fo","csv","/nh"};
			}

			try {
				// Reads output while the command runs (waiting first could deadlock)
				ProcessRunner.Result result = ProcessRunner.run(command);
				if( result.exitCode == 0 ) {
					user = FileSourceUser.fromId(result.stdout);
				} else {
					throw new IOException(result.stderr);
				}
			} catch (IOException e) {
				logger.logError("Can't identify the current user with "+String.join(" ", command)+"; falling back to user.name", e);
			}

			if( user == null ) {
				user = new FileSourceUser();
				// was System.getProperty("user"), which doesn't exist (null)
				String name = System.getProperty("user.name");
				user.setName(name == null ? "UnKnown" : name);
				try {
					UserPrincipalLookupService svr = FileSystems.getDefault().getUserPrincipalLookupService();
					UserPrincipal principal = name == null ? null : svr.lookupPrincipalByName(name);
					if( principal !=null ) {
						user.setName(principal.getName());
					}
				} catch (IOException e) {
					logger.logError("Can't look up user "+name, e);
				}
			}

			localUser = user;
			return user;
		}
	}
}
