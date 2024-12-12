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
	private static Pattern windows = Pattern.compile("^[a-z][:]*");



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

	@Override
	public boolean isAbsolute() {
		boolean ret = rawPath.length()>0 && rawPath.charAt(0)==factory.getPathSeperatorChar();
		if( !ret) {
			Matcher mm = windows.matcher(rawPath);
			ret = mm.matches();
		}

		return ret;
	}

	@Override
	public Path getRoot() {
		char sep = factory.getSeperatorChar();
		if( rawPath.charAt(0) == sep) {
			return new FileSourcePath(""+sep,factory);
		}

		String root = split()[0];
		return new FileSourcePath(root, factory);
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
		String [] ret= path.split(rx);
		return ret;
	}


	@Override
	public Path getParent() {
		String ret = rawPath;
		int idx = rawPath.lastIndexOf(factory.getSeperatorChar());
		if( idx> 0 ) {
			ret = ret.substring(0,idx);
		}
		return new FileSourcePath(ret,factory);
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

	@Override
	public Path normalize() {
		rawPath = normalizeString(rawPath, factory.getSeperatorChar());
		try {
			file = factory.createFileSource(rawPath);
		} catch (IOException e) {
		}
		return this;
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
		FileSourcePath fsp = ((FileSourcePath)other);

		if( other.getNameCount()==0 || fsp.rawPath.isEmpty()) {
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
				e.printStackTrace();
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

			// can only relativize paths of the same type
			if (this.isAbsolute() != child.isAbsolute())
				throw new IllegalArgumentException("'other' is different type of Path");

			// this path is the empty path
			if (this.rawPath.isEmpty())
				return child;


			me = toAbsolutePath().toString();
			u = child.toAbsolutePath().toString();

			String tmp = null;
			if(u.startsWith(me)) {
				tmp = u.substring(me.length());
			} else {
				tmp = u;
			}

			while( tmp.startsWith(""+factory.getSeperatorChar())) {
				tmp = tmp.substring(1);
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

		URI ret=null;
		try {
			ret = new URI(String.format("filesource:%s?sourcetype=%s",rawPath,factory.getTypeId()));
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}

		return ret;
	}

	public FileSource getFileSource() {

		if( file == null ) {
			try {
				file = factory.createFileSource(rawPath);
			} catch (IOException e) {
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
			e.printStackTrace();
			return null;
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

	@Override
	public int compareTo(Path other) {

		if (!(other instanceof FileSourcePath)) {
			throw new ProviderMismatchException();			
		}

		int	ret = normalize().toString().compareTo(other.toAbsolutePath().toString());

		return ret;
	}

}
