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
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;

public class FileSourceFileSystem extends FileSystem {

	
	public static FileSourceFileSystem from(URI uri) {
		
		return null;
	}

	
	private FileSourceFactory factory;
	private transient FileSourceFileSystemProvider provider;

	 FileSourceFileSystem(FileSourceFactory factory) {
		this.factory = factory;		
	}

	@Override
	public FileSystemProvider provider() {
		if( provider == null ) {
			synchronized (this) {
				if( provider == null ){
					provider = FileSourceFileSystemProvider.getSingleton();
				}
			}
		}
		return this.provider;
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
			ret = ""+factory.getPathSeperatorChar();
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
				e.printStackTrace();
			}
		}
		return ret;
	}

	@Override
	public Iterable<FileStore> getFileStores() {
		throw new RuntimeException("Not implimented");
		
	}

	@Override
	public Set<String> supportedFileAttributeViews() {
		// Not implemented
		throw new RuntimeException("Not implimented");
	}

	@Override
	public Path getPath(String first, String... more) {
		FileSource file=null;
		try {
			file = factory.createFileSource(first);
			if( more !=null) {
				for(String name: more) {
					file = file.getChild(name);
				}						
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		
		return new FileSourcePath(file);
	}

	@Override
	public PathMatcher getPathMatcher(String syntaxAndPattern) {
		// Not implemented
		throw new RuntimeException("Not implimented");
	}

	@Override
	public UserPrincipalLookupService getUserPrincipalLookupService() {
		// Not implemented
		throw new RuntimeException("Not implimented");
	}

	@Override
	public WatchService newWatchService() throws IOException {
		return new WatchService() {
			
			@Override
			public WatchKey take() throws InterruptedException {
				// Not implemented
				throw new RuntimeException("Not implimented");
			}
			
			@Override
			public WatchKey poll(long timeout, TimeUnit unit) throws InterruptedException {
				// Not implemented
				throw new RuntimeException("Not implimented");
			}
			
			@Override
			public WatchKey poll() {
				// Not implemented
				throw new RuntimeException("Not implimented");
			}
			
			@Override
			public void close() throws IOException {
				// Not implemented
				throw new RuntimeException("Not implimented");
			}
		};
	}

	
}
