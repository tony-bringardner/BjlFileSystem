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
 * ~version~V000.01.12-V000.01.11-V000.01.05-V000.01.04-V000.01.00-V000.00.02-V000.00.01-V000.00.00-
 */
package us.bringardner.io.filesource.java.file;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.FileSystems;
import java.nio.file.WatchService;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.attribute.UserPrincipalNotFoundException;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.FileSourceGroup;
import us.bringardner.io.filesource.FileSourceUser;
import us.bringardner.io.filesource.fileproxy.FileProxyFactory;

public class FileSourceFileSystem extends FileSystem {

	
	public static FileSourceFileSystem from(URI uri) {
		
		return null;
	}

	
	private FileSourceFactory factory;

	 FileSourceFileSystem(FileSourceFactory factory) {
		this.factory = factory;		
	}

	@Override
	public FileSystemProvider provider() {
		return FileSourceFileSystemProvider.getSingleton();
	}

	@Override
	public void close() throws IOException {
		if( factory !=null  ) {
			factory.disConnect();
		}
		
	}

	@Override
	public boolean isOpen() {
		boolean ret = false;
		if( factory != null) {
			ret = factory.isConnected();
		}
		
		return ret;
	}

	@Override
	public boolean isReadOnly() {
		// FileSourceFactory does not support read only		
		return false;
	}

	@Override
	public String getSeparator() {
		String ret = File.separator;
		if( factory != null ) {
			ret = ""+factory.getSeperatorChar();   // was getPathSeperatorChar() (':' or ';')
		}
		
		return ret;
	}

	@Override
	public Iterable<Path> getRootDirectories() {
		List<Path> ret = new ArrayList<Path>();
		if( factory != null ) {
			try {
				for(FileSource f : factory.listRoots()) {
					ret.add(new FileSourcePath(f));
				}
			} catch (IOException e) {
				throw new java.io.UncheckedIOException(e);
			}
		}
		return ret;
	}

	/**
	 * One store per root directory. (Used to throw RuntimeException.)
	 */
	@Override
	public Iterable<FileStore> getFileStores() {
		List<FileStore> ret = new ArrayList<FileStore>();
		for(Path root : getRootDirectories()) {
			ret.add(new FileSourceFileStore(((FileSourcePath) root).getFileSource()));
		}
		return ret;
	}

	/** The views FileSourceFileSystemProvider supports. (Used to throw RuntimeException.) */
	@Override
	public Set<String> supportedFileAttributeViews() {
		return FileSourceFileStore.VIEWS;
	}

	@Override
	/**
	 * Joins the strings with the separator, as FileSystem.getPath specifies.
	 * (This used to create a FileSource for the string, which made a relative
	 * name absolute against the current directory; so e.g.
	 * path.resolveSibling("x") ended up in the current directory instead of
	 * next to path.)
	 */
	public Path getPath(String first, String... more) {
		String sep = ""+factory.getSeperatorChar();
		StringBuilder path = new StringBuilder(first);
		if( more != null ) {
			for(String name : more) {
				if( name == null || name.isEmpty() ) {
					continue;
				}
				if( path.length() > 0 && !path.toString().endsWith(sep) ) {
					path.append(sep);
				}
				path.append(name);
			}
		}
		return new FileSourcePath(path.toString(), factory);
	}

	/**
	 * Supports the "glob:" and "regex:" syntaxes, with the same rules as the
	 * JDK's file systems (see FileSourceGlobs). Paths are matched on their
	 * string form, so a pattern with no separator matches file names, e.g.
	 * Files.newDirectoryStream(dir, "*.txt"). Matching is case-insensitive
	 * when the separator is '\\' (Windows), as on the JDK's Windows file system.
	 * (This used to throw "Not implemented".)
	 *
	 * @throws IllegalArgumentException if there is no "syntax:" prefix
	 * @throws UnsupportedOperationException for a syntax other than glob or regex
	 * @throws java.util.regex.PatternSyntaxException if the pattern is invalid
	 */
	@Override
	public PathMatcher getPathMatcher(String syntaxAndPattern) {
		int colon = syntaxAndPattern.indexOf(':');
		if( colon <= 0 ) {
			throw new IllegalArgumentException("Expected syntax:pattern, got "+syntaxAndPattern);
		}
		String syntax = syntaxAndPattern.substring(0, colon);
		String input = syntaxAndPattern.substring(colon + 1);
		char sep = factory != null ? factory.getSeperatorChar() : File.separatorChar;

		String regex;
		if( syntax.equalsIgnoreCase("glob") ) {
			regex = FileSourceGlobs.toRegex(input, sep);
		} else if( syntax.equalsIgnoreCase("regex") ) {
			regex = input;
		} else {
			throw new UnsupportedOperationException("Syntax '"+syntax+"' not recognized");
		}

		int flags = sep == '\\' ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0;
		Pattern pattern = Pattern.compile(regex, flags);
		return path -> pattern.matcher(path.toString()).matches();
	}

	/**
	 * Local files use the operating system's lookup, so the principals work
	 * with Files.setOwner. Other file systems only know the connected user
	 * (whoAmI) and that user's groups; any other name throws
	 * UserPrincipalNotFoundException. (Used to throw RuntimeException.)
	 */
	@Override
	public UserPrincipalLookupService getUserPrincipalLookupService() {
		if( factory instanceof FileProxyFactory ) {
			return FileSystems.getDefault().getUserPrincipalLookupService();
		}
		return new UserPrincipalLookupService() {
			@Override
			public UserPrincipal lookupPrincipalByName(String name) throws IOException {
				FileSourceUser me = factory.whoAmI();
				if( me != null && name.equals(me.getName()) ) {
					return me;
				}
				throw new UserPrincipalNotFoundException(name);
			}

			@Override
			public GroupPrincipal lookupPrincipalByGroupName(String group) throws IOException {
				FileSourceUser me = factory.whoAmI();
				if( me != null ) {
					for(FileSourceGroup g : me.getGroups().values()) {
						if( group.equals(g.getName()) ) {
							return g;
						}
					}
				}
				throw new UserPrincipalNotFoundException(group);
			}
		};
	}

	/**
	 * Not supported: FileSources have no change notification. This throws
	 * here, as the FileSystem contract allows, rather than returning a
	 * WatchService whose every method (even close) threw RuntimeException.
	 */
	@Override
	public WatchService newWatchService() throws IOException {
		throw new UnsupportedOperationException("WatchService is not supported");
	}

	
}
