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
 * ~version~V000.01.22-V000.01.14-V000.01.12-V000.01.11-V000.01.05-V000.01.04-V000.01.03-V000.01.00-V000.00.01-V000.00.00-
 */
package us.bringardner.io.filesource.java.file;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.ProviderMismatchException;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchEvent.Modifier;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.FileSourceUri;



public class FileSourcePath implements Path {

	static {
		// make sure the factories are inited
		FileSourceFactory.getAllHandlerPkgs();
	}

	FileSourceFactory factory ;
	String rawPath;
	private FileSource file;
	private FileSourceUri fsuri;
	private FileSystem fileSystem;
	/** A Windows drive root such as C:\ or C:/ */
	private static final Pattern DRIVE_ROOT = Pattern.compile("^[A-Za-z]:[\\\\/]");



	// only available to this package
	public FileSourcePath(URI uri) throws IOException {

		fsuri = new FileSourceUri(uri);
		rawPath = fsuri.getPath();
		factory = FileSourceFactory.getFileSourceFactory(uri);
		if( factory == null ) {
			if( fsuri.getFactoryId()==null ) {
				factory = FileSourceFactory.getDefaultFactory();
			} else {
				throw new IOException("Invalid path. No factory identified by "+fsuri);
			}
		}
		//  only '/' are valid in URI
		rawPath = rawPath.replace('/', factory.getSeperatorChar());

	}


	public FileSourcePath(String path, FileSourceFactory factory2) {
		rawPath = path;
		factory = factory2;		
		
	}


	public FileSourcePath(FileSource file) {
		rawPath = file.getAbsolutePath(); 
		factory = file.getFileSourceFactory();
		this.file = file;
	}


	@Override
	public String toString() {
		return rawPath;
	}

	@Override
	public FileSystem getFileSystem() {
		if( fileSystem==null) {
			fileSystem = new FileSourceFileSystem(factory);
		}

		return fileSystem;
	}

	/**
	 * The root component of rawPath ("/", "C:\", "\\"), or null if it has none.
	 */
	private String rootString() {
		Matcher m = DRIVE_ROOT.matcher(rawPath);
		if( m.find() ) {
			return rawPath.substring(0, 2)+factory.getSeperatorChar();
		}
		char sep = factory.getSeperatorChar();
		if( rawPath.length() > 0 && rawPath.charAt(0) == sep ) {
			return ""+sep;
		}
		return null;
	}

	/**
	 * (This compared the first character with getPathSeperatorChar() -- the ':'
	 * or ';' that separates entries in a path list -- instead of the name
	 * separator, so absolute paths reported false; and the Windows pattern
	 * only matched strings like "c" or "c:".)
	 */
	@Override
	public boolean isAbsolute() {
		return rootString() != null;
	}

	/** The root, or null for a relative path (it used to return the first name). */
	@Override
	public Path getRoot() {
		String root = rootString();
		return root == null ? null : new FileSourcePath(root, factory);
	}

	@Override
	public Path getFileName() {
		String [] parts = split();
		return new FileSourcePath(parts[parts.length-1],factory );
	}

	private String[] split() {
		char sep = factory.getSeperatorChar();
		String path = rawPath;
		if(path.length()>0 && path.charAt(0) == sep) {
			path = path.substring(1);
		}
		String rx = "["+sep+"]";
		if( sep == '\\') {
			rx = "[\\\\]";
		}
		String [] ret= path.split(rx);
		return ret;
	}


	/**
	 * The parent, or null if there is none (a root, or a single relative name).
	 * It used to return the path itself for "/" and for "/name", so loops that
	 * walk up until getParent() is null never ended.
	 */
	@Override
	public Path getParent() {
		char sep = factory.getSeperatorChar();
		String root = rootString();
		String path = rawPath;
		// ignore a trailing separator (but not the root's)
		while( path.length() > 1 && path.charAt(path.length()-1) == sep && (root == null || path.length() > root.length()) ) {
			path = path.substring(0, path.length()-1);
		}
		if( root != null && path.length() <= root.length() ) {
			return null;   // the root itself
		}
		int idx = path.lastIndexOf(sep);
		if( idx < 0 ) {
			return null;   // single relative name
		}
		String parent = path.substring(0, idx);
		if( root != null && parent.length() < root.length() ) {
			parent = root;
		}
		return new FileSourcePath(parent, factory);
	}

	@Override
	public int getNameCount() {
		return split().length;
	}

	@Override
	public Path getName(int index) {
		String [] parts = split();
		return new FileSourcePath(parts[index],factory );
	}

	@Override
	public Path subpath(int beginIndex, int endIndex) {
		String [] parts = split();
		StringBuilder ret = new StringBuilder();
		for (int idx = beginIndex; idx < parts.length && idx < endIndex; idx++) {
			if( idx > beginIndex || idx == 0 ) {
				ret.append(factory.getSeperatorChar());
			}
			ret.append(parts[idx]);
		}
		return new FileSourcePath(ret.toString(), factory);
	}

	@Override
	public boolean startsWith(Path other) {
		boolean ret = false;
		if (other instanceof FileSourcePath) {
			FileSourcePath fsp = (FileSourcePath) other;
			ret = rawPath.startsWith(fsp.rawPath);
		} else {
			// Testing only
			ret = rawPath.startsWith(other.toString());
		}
		return ret;
	}

	@Override
	public boolean endsWith(Path other) {
		boolean ret = false;
		if (other instanceof FileSourcePath) {
			FileSourcePath fsp = (FileSourcePath) other;
			ret = rawPath.endsWith(fsp.rawPath);
		} else {
			// testing only
			ret = rawPath.endsWith(other.toString());
		}
		return ret;
	}

	public static String normalizeString(String path,char seperator) {
		//  Just use the file system path to normalize
		if( !(""+seperator).equals(FileSystems.getDefault().getSeparator())) {
			path = path.replaceAll(""+seperator, FileSystems.getDefault().getSeparator());
		}

		String ret = Paths.get(path).normalize().toString();
		return ret;
	}

	/** Returns a new path; Paths are immutable (this used to modify itself). */
	@Override
	public Path normalize() {
		return new FileSourcePath(normalizeString(rawPath, factory.getSeperatorChar()), factory);
	}

	public Path resolve(String other) {		
		return resolve(new FileSourcePath(other, factory));
	}

	/**
	 * Resolve the given path against this path.
	 * If the other parameter is an absolute path then this method trivially returns other. 
	 * If other is an empty path then this method trivially returns this path. 
	 * Otherwise this method considers this path to be a directory and resolves 
	 * the given path against this path. 
	 * In the simplest case, the given path does not have a root component, 
	 * in which case this method joins the given path to this path and returns a resulting path that ends with the given path. 
	 * Where the given path has a root component then resolution is highly implementation dependent and therefore unspecified.
	 */
	@Override
	public Path resolve(Path other) {


		if( other.isAbsolute()) {
			//If the other parameter is an absolute path then this method trivially returns other.
			return other;
		}
	
		//FileSourcePath fsp = ((FileSourcePath)other);

		if( other.getNameCount()==0 ) {
			//If other is an empty path then this method trivially returns this path.
			return this;
		}
		//Otherwise this method considers this path to be a directory and resolves the given path against this path.
		FileSource file = getFileSource();

		if( file !=null ) {
			try {
				FileSource file2 = file.getChild(other.toString());
				return new FileSourcePath(file2);
			} catch (IOException e) {
				throw new UncheckedIOException(e);   // was printStackTrace() and return null
			}			
		}
		return null;
	}

	@Override
	public Path relativize(Path other) {

		if (other instanceof FileSourcePath) {
			FileSourcePath child = (FileSourcePath) other;
			String me = this.toString();
			String u = child.toString();
			if( u.equals(me)) {
				// empty 
				return new FileSourcePath("", factory);
			}

			// this path is the empty path
			if (this.rawPath.isEmpty())
				return child;

			// Compare absolute forms. (A relative and an absolute path are
			// accepted and resolved against the current directory, as they
			// always effectively were while isAbsolute() returned false.)
			me = toAbsolutePath().toString();
			u = child.toAbsolutePath().toString();

			String sep = ""+factory.getSeperatorChar();
			String tmp = null;
			if( u.equals(me) ) {
				tmp = "";
			} else if( u.startsWith(me.endsWith(sep) ? me : me+sep) ) {
				// only a real descendant: "/a/bc" is not under "/a/b"
				tmp = u.substring(me.length());
				while( tmp.startsWith(sep)) {
					tmp = tmp.substring(1);
				}
			} else {
				// not below this path: return it unchanged (".." forms aren't produced)
				tmp = u;
			}


			return new FileSourcePath(tmp, factory);			
		}
		throw new ProviderMismatchException();
	}


	@Override
	public URI toUri() {
		if( factory == null ) {
			throw new RuntimeException("No factory");
		}

		// Use '/' in the URI (FileSourcePath(URI) converts back), quote characters
		// such as spaces (these used to make the URI invalid and toUri() return
		// null), and include the session id so the URI resolves to this file system.
		String query = FileSourceFactory.QUERY_STRING_SOURCE_TYPE+"="+factory.getTypeId();
		if( factory.getSessionId() >= 0 ) {
			query += "&"+FileSourceFactory.QUERY_STRING_SESSION_ID+"="+factory.getSessionId();
		}
		String path = rawPath.replace(factory.getSeperatorChar(), '/');
		try {
			return new URI(FileSourceFactory.FILE_SOURCE_PROTOCOL, path+"?"+query, null);
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException("Can't make a URI for "+rawPath, e);
		}
	}

	/**
	 * @throws UncheckedIOException if the factory can't create it (this used
	 *         to return null, which surfaced later as a NullPointerException)
	 */
	public FileSource getFileSource() {

		if( file == null ) {
			try {
				file = factory.createFileSource(rawPath);
			} catch (IOException e) {
				throw new UncheckedIOException("Can't create FileSource for "+rawPath, e);
			}
		}
		return file;
	}

	@Override
	public Path toAbsolutePath() {	

		FileSource file = getFileSource();
		if( file == null ) {
			return null;
		}
		if( isAbsolute()) {
			return this;
		}

		return new FileSourcePath(file);

	}

	@Override
	public Path toRealPath(LinkOption... options) throws IOException {
		try {
			FileSource file = factory.createFileSource(rawPath);
			FileSource link = file.getLinkedTo();
			FileSource ret = file;
			if( link !=null ) {
				ret = link;
				for (int idx = 0; idx < options.length; idx++) {
					if(options[idx] == LinkOption.NOFOLLOW_LINKS) {
						ret = link;
					}
				}
			}
			return new FileSourcePath(ret);
		} catch (IOException e) {
			throw e;
		}


	}

	/*
	 * Compares two abstract paths lexicographically. The ordering defined by this method is provider specific, 
	 * and in the case of the default provider, platform specific. This method does not access the file system and neither file is required to exist.
	 * This method may not be used to compare paths that are associated with different file system providers.
	 */
	@Override
	public WatchKey register(WatchService watcher, Kind<?>[] events, Modifier... modifiers) throws IOException {

		return null;
	}

	/**
	 * Lexicographic comparison of the path strings, as Path.compareTo requires
	 * (no file system access). Paths of different factory types are ordered by
	 * type id. (This used to normalize() -- which modified this path -- and
	 * compare against the other path made absolute.)
	 */
	@Override
	public int compareTo(Path other) {
		if (!(other instanceof FileSourcePath)) {
			throw new ClassCastException("Not a FileSourcePath: "+other);
		}
		FileSourcePath o = (FileSourcePath) other;
		int ret = typeId().compareTo(o.typeId());
		return ret != 0 ? ret : rawPath.compareTo(o.rawPath);
	}

	private String typeId() {
		return factory == null ? "" : factory.getTypeId();
	}

	/** Same factory instance, or same factory type with the same connection properties. */
	private boolean sameFileSystem(FileSourcePath o) {
		if (factory == o.factory) {
			return true;
		}
		if (factory == null || o.factory == null || !typeId().equals(o.typeId())) {
			return false;
		}
		return String.valueOf(factory.getConnectProperties()).equals(String.valueOf(o.factory.getConnectProperties()));
	}

	/**
	 * Two paths are equal when they have the same path string on the same
	 * file system (there used to be no equals at all, so identical paths
	 * were never equal).
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof FileSourcePath)) {
			return false;
		}
		FileSourcePath o = (FileSourcePath) obj;
		return rawPath.equals(o.rawPath) && sameFileSystem(o);
	}

	@Override
	public int hashCode() {
		return 31 * typeId().hashCode() + rawPath.hashCode();
	}

}
