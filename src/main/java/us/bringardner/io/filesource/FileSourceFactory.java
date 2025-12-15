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
import java.io.InputStream;
import java.io.Serializable;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;

import us.bringardner.core.BaseObject;
import us.bringardner.core.util.LogHelper;
import us.bringardner.io.filesource.fileproxy.FileProxy;
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


	private static class FactorySession {
		int id = getNextId();
		String key;
		FileSourceFactory factory;
		int instanceCount = 0;

		public FactorySession(FileSourceFactory factory) {
			this.factory = factory;
			StringBuilder buf = new StringBuilder(factory.getTypeId()+":");
			Properties p = factory.getConnectProperties();
			for(String name : p.stringPropertyNames()) {
				if(! "password".equalsIgnoreCase(name)) {
					buf.append(name+"="+p.getProperty(name));
				}
			}
			key = buf.toString();
		}

	}

	private static Map<Integer,FactorySession> sessions = new HashMap<>();
	private static Map<String,FactorySession> sessionsKeyMap = new HashMap<>();

	private volatile static FileSourceFactory defaultFactory;
	private volatile static Map<String, String> types;
	private static LogHelper logger = new LogHelper(FileSourceFactory.class);
	private int sessionId=-1;

	private static ServiceLoader<FileSourceFactory> factoryLoader= ServiceLoader.load(FileSourceFactory.class);

	static {


		//  Make sure this is set
		//-Djava.protocol.handler.pkgs=us.bringardner.io
		String pkgs = getAllHandlerPkgs();
		String tmp = System.getProperty(PROP_JAVA_PROTOCOL_HANDLER_PKGS);
		if( tmp != null ) {
			tmp = tmp+"|"+pkgs;
		} else {
			tmp = pkgs;
		}

		System.setProperty(PROP_JAVA_PROTOCOL_HANDLER_PKGS, tmp);
		URL.setURLStreamHandlerFactory(fileProxyFactory);
		logger.logInfo("Set handlers to "+tmp);
		// register some basic types
		types = new HashMap<String, String>();
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




	public static String getAllHandlerPkgs() {
		String tmp = FileSourceFactory.class.getPackage().toString();
		return tmp;
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
				FactorySession session = sessions.get(sessionid);
				if( session != null ) {
					return session.factory;
				} else {
					if( sessionid < 0 ) {
						FactorySession s = new FactorySession(fileProxyFactory);
						s.id = sessionid;
						sessions.put(sessionid, s);
						return s.factory;
					}
				}
			} catch (Exception e) {
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
	@SuppressWarnings("deprecation")
	public static FileSource getFileSource(String url) throws IOException {
		return getFileSource(new URL(url));
	}

	/*
	 * List all of the roots of this file system
	 */
	public abstract FileSource [] listRoots() throws IOException;

	//get Current Directory
	public abstract FileSource getCurrentDirectory() throws IOException;

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




	public static FileSourceFactory getFileSourceFactory(String factory_id){
		FileSourceFactory ret = null;
		FileSourceFactory dummy = null;
		if( factory_id  != null ) {
			factory_id = factory_id.trim().toLowerCase();

			for (FileSourceFactory fsf : factoryLoader) {
				if( fsf.getTypeId().trim().toLowerCase().equals(factory_id)) {
					dummy = fsf;
					break;
				}
			}				

			if( dummy != null ) {
				try {
					ret = dummy.getClass().getDeclaredConstructor().newInstance();
					logger.logInfo("Created "+ret.getClass()+" as "+factory_id);
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
	public static FileSourceFactory getDefaultFactory() {
		if( defaultFactory == null ){			
			String tmp = System.getProperty("FileSource.default");
			if( tmp != null ){
				if( (defaultFactory = getFileSourceFactory(tmp)) == null) {
					System.err.println("Invalid FileSOurce default = "+tmp);
					defaultFactory = fileProxyFactory;
				}
			}
			if( defaultFactory == null ) {
				defaultFactory = fileProxyFactory;
			}
		}

		return defaultFactory;
	}

	public static String [] getRegisterdFactories() {
		List<String> ret = new ArrayList<String>();
		for (FileSourceFactory fsf : factoryLoader) {
			ret.add(fsf.getTypeId());
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
				//  this is a newly connected session so cache it
				FactorySession s = new FactorySession(this);
				sessions.put(s.id, s);
				sessionId = s.id;
				FactorySession session = sessionsKeyMap.get(s.key);
				if( session != null) {
					session.instanceCount++;
					logger.getLogger().warn("Factory session alread exists for "+s.key+" sessions="+session.instanceCount);
				} else {
					s.instanceCount=1;
					sessionsKeyMap.put(s.key, s);
				}
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
		disConnectImpl();
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
	public URLStreamHandler createURLStreamHandler(String protocol) {
		URLStreamHandler ret = null;
		if( protocol.equals(FileSourceFactory.FILE_SOURCE_PROTOCOL)) {
			ret = new us.bringardner.io.filesource.Handler();
		} else {
			//-Djava.protocol.handler.pkgs=us.bringardner.io

			String pkgs = System.getProperty(FileSourceFactory.PROP_JAVA_PROTOCOL_HANDLER_PKGS);
			if( pkgs != null) {
				String parts[] = pkgs.split("[|]");
				for (int idx = 0; idx < parts.length; idx++) {
					String className = String.format("%s.%s.Handler",parts[idx],protocol);
					try {
						Class<?> cls = Class.forName(className);
						ret = (URLStreamHandler) cls.getDeclaredConstructor().newInstance();
					} catch(Exception e) {}
				}
			}			
		}		


		return ret;
	}

	/*
	 */
	public static String getType(String extention){
		return (String) types.get(extention);
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

	public FileSourceUser whoAmI() {


		if( localPrinciple == null ) {
			//*nix, including macOS,  system use id
			String [] command = {"id"};

			if( isWindows() ) {
				String tmp []  = {"whoami","/user","/groups","/fo","list"};
				command = tmp;
			} 

			ProcessBuilder builder = new ProcessBuilder(command);
			Process process;
			try {
				process = builder.start();
				int status = -1;
				try {
					status = process.waitFor();
				} catch (InterruptedException e) {
				}

				String out = "";
				try (InputStream reader = process.getInputStream()) {
					out = new String(reader.readAllBytes());
				}

				String err = "";
				try (InputStream reader = process.getErrorStream()) {
					err = new String(reader.readAllBytes());
				}

				if( status == 0 ) {
					FileSourceUser tmp = FileSourceUser.fromId(out.toString());
					if( tmp != null ) {
						localPrinciple = tmp;
					}
				} else {
					throw new IOException(err);
				}
				
			} catch (IOException e) {
				e.printStackTrace();
			}
			if( localPrinciple == null ) {
				localPrinciple = new FileSourceUser();
				UserPrincipalLookupService svr = FileSystems.getDefault().getUserPrincipalLookupService();
				UserPrincipal user;
				try {
					user = svr.lookupPrincipalByName(System.getProperty("user"));
					if( user !=null ) {
						localPrinciple.setName(user.getName());
					} else {
						localPrinciple.setName("UnKnown");
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

			}
		}

		return localPrinciple;
	}
}
