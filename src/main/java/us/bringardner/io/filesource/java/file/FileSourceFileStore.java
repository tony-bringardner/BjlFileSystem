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
 * ~version~V000.01.00-V000.00.01-V000.00.00-
 */
package us.bringardner.io.filesource.java.file;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileOwnerAttributeView;
import java.nio.file.attribute.FileStoreAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import us.bringardner.io.filesource.FileSource;

public class FileSourceFileStore extends FileStore {

	/** The attribute views FileSourceFileSystemProvider supports. */
	static final Set<String> VIEWS = Collections.unmodifiableSet(new LinkedHashSet<String>(java.util.Arrays.asList("basic", "posix", "owner")));
	
	private FileSource file;

	public FileSourceFileStore(FileSource file) {
		this.file = file;
	}

	@Override
	public String name() {
		// was toString(), i.e. "FileSourceFileStore@1b6d3586"
		String title = null;
		try {
			title = file.getFileSourceFactory().getTitle();
		} catch (RuntimeException e) {
			// fall through
		}
		return title != null ? title : type();
	}

	@Override
	public String toString() {
		return name()+" ("+type()+")";
	}

	@Override
	public String type() {
		return file.getFileSourceFactory().getTypeId();
	}

	@Override
	public boolean isReadOnly() {
		return false;
	}

	/**
	 * The FileSource's getTotalSpace/getFreeSpace/getUsableSpace: the real
	 * disk for local files, 0 when an implementation can't tell (as
	 * java.io.File reports). This used to be Long.MAX_VALUE for everything
	 * but local files.
	 */
	@Override
	public long getTotalSpace() throws IOException {
		return file.getTotalSpace();
	}

	@Override
	public long getUsableSpace() throws IOException {
		return file.getUsableSpace();
	}

	@Override
	public long getUnallocatedSpace() throws IOException {
		return file.getFreeSpace();
	}

	/** Was true for any view, e.g. ACL or DOS, which the provider doesn't support. */
	@Override
	public boolean supportsFileAttributeView(Class<? extends FileAttributeView> type) {
		return type == BasicFileAttributeView.class
				|| type == PosixFileAttributeView.class
				|| type == FileOwnerAttributeView.class;
	}

	@Override
	public boolean supportsFileAttributeView(String name) {
		return VIEWS.contains(name);
	}

	@Override
	public <V extends FileStoreAttributeView> V getFileStoreAttributeView(Class<V> type) {
		// Not implemented
		return null;
	}

	@Override
	public Object getAttribute(String attribute) throws IOException {
		switch (attribute) {
		case "totalSpace":       return getTotalSpace();
		case "usableSpace":      return getUsableSpace();
		case "unallocatedSpace": return getUnallocatedSpace();
		default:
			throw new UnsupportedOperationException("'"+attribute+"' is not supported");
		}
	}

}
