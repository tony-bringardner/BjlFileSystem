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
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileStoreAttributeView;

import us.bringardner.io.filesource.FileSource;

public class FileSourceFileStore extends FileStore {

	
	private FileSource file;

	public FileSourceFileStore(FileSource file) {
		this.file = file;
	}

	@Override
	public String name() {
		return toString();
	}

	@Override
	public String type() {
		return file.getFileSourceFactory().getTypeId();
	}

	@Override
	public boolean isReadOnly() {
		return false;
	}

	@Override
	public long getTotalSpace() throws IOException {
		return Long.MAX_VALUE;
	}

	@Override
	public long getUsableSpace() throws IOException {
		return Long.MAX_VALUE;
	}

	@Override
	public long getUnallocatedSpace() throws IOException {
		return Long.MAX_VALUE;
	}

	@Override
	public boolean supportsFileAttributeView(Class<? extends FileAttributeView> type) {
		return true;
	}

	@Override
	public boolean supportsFileAttributeView(String name) {
		return true;
	}

	@Override
	public <V extends FileStoreAttributeView> V getFileStoreAttributeView(Class<V> type) {
		// Not implemented
		return null;
	}

	@Override
	public Object getAttribute(String attribute) throws IOException {
		// Not implemented
		return null;
	}

}
