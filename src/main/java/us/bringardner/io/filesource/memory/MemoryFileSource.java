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
 * ~version~V000.01.19-V000.01.07-V000.01.00-V000.00.01-V000.00.00-
 */
/*
 * Created on Dec 14, 2004
 *
 */
package us.bringardner.io.filesource.memory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.UserPrincipal;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import javax.swing.ProgressMonitor;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.FileSourceFilter;
import us.bringardner.io.filesource.FileSourceGroup;
import us.bringardner.io.filesource.FileSourceRandomAccessStream;
import us.bringardner.io.filesource.FileSourceUser;
import us.bringardner.io.filesource.IRandomAccessStream;
import us.bringardner.io.filesource.ISeekableInputStream;


/**
 * @author Tony Bringardner
 * Acts as a proxy to a File Object to expose the FileSource interface
 */
public class MemoryFileSource implements FileSource {

	private static final long serialVersionUID = 1L;

	public enum FileType {Undefined,Directory,File};

	boolean isRoot = false;
	private String name;
	private volatile FileType fileType=FileType.Undefined;

	private MemoryFileSourceFactory theCreator ;
	private FileSourceGroup group;
	private FileSourceUser owner;
	private volatile MemoryFileSource parent;
	volatile FileSource linkedTo;
	/** Children that exist (or have existing descendants / are links). Held strongly. */
	private Map<String,MemoryFileSource> kidsMap = new TreeMap<>();
	/**
	 * Children that were only looked up (createFileSource on a path that doesn't
	 * exist). Held weakly so lookups don't grow the tree forever, while anyone
	 * still holding the object keeps getting the same instance for that path.
	 */
	private transient Map<String,WeakReference<MemoryFileSource>> placeholders;   // WeakReference isn't serializable
	private int placeholderPurgeAt = 64;
	private boolean canOwnerRead=true;
	private boolean canOwnerWrite=true;
	private boolean canExecute=true;
	private boolean canGroupRead=true;
	private boolean canGroupWrite=true;
	private boolean canGroupExecute=true;
	private boolean canOtherRead=true;
	private boolean canOtherWrite=true;
	private boolean canOtherExecute=true;
	private boolean deleted;
	private volatile byte[] data;
	private String cananicalPath;
	private long lastAccessed=System.currentTimeMillis();
	private long lastModified=System.currentTimeMillis();
	private long createDate=System.currentTimeMillis();

	public MemoryFileSource(MemoryFileSource parent,String name,MemoryFileSourceFactory creator) {
		this.name = name;
		this.parent = parent;
		this.theCreator = creator;
		if( parent == null ) {
			fileType = FileType.Directory;
			canOwnerRead = canOwnerWrite = true;
		}

	}

	private Map<String,WeakReference<MemoryFileSource>> placeholders() {
		if( placeholders == null ) {
			placeholders = new HashMap<>();
		}
		return placeholders;
	}

	/**
	 * All structural changes (the child maps, parent links, file data) are
	 * made while holding one lock per memory file system -- its factory --
	 * because an operation such as rename or delete touches several nodes.
	 * (Nothing was synchronized, so concurrent use could corrupt the tree.)
	 */
	private Object lock() {
		return theCreator != null ? theCreator : this;
	}

	MemoryFileSource getChildByName(String name) {
		synchronized (lock()) {
			MemoryFileSource ret = kidsMap.get(name);
			if( ret == null ) {
				WeakReference<MemoryFileSource> ref = placeholders().get(name);
				if( ref != null ) {
					ret = ref.get();
					if( ret == null ) {
						placeholders().remove(name);
					}
				}
			}
			return ret;
		}
	}

	/** A node is kept in its parent's tree only if it exists, is a link, or has kept children. */
	private boolean shouldRetain() {
		return parent == null || isRoot || fileType != FileType.Undefined || linkedTo != null || !kidsMap.isEmpty();
	}

	/**
	 * Move this node between the parent's strong (kidsMap) and weak (placeholders)
	 * maps after its state changed, and propagate up the tree.
	 */
	void updateRetention() {
		synchronized (lock()) {
			if( parent == null ) {
				return;
			}
			if( shouldRetain() ) {
				if( parent.kidsMap.get(name) != this ) {
					parent.kidsMap.put(name, this);
					parent.placeholders().remove(name);
					parent.updateRetention();
				}
			} else if( parent.kidsMap.get(name) == this ) {
				parent.kidsMap.remove(name);
				parent.addPlaceholder(this);
				parent.updateRetention();
			}
		}
	}

	private void addPlaceholder(MemoryFileSource kid) {
		placeholders().put(kid.getName(), new WeakReference<>(kid));
		if( placeholders().size() > placeholderPurgeAt ) {
			placeholders().values().removeIf(r -> r.get() == null);
			placeholderPurgeAt = Math.max(64, placeholders().size() * 2);
		}
	}

	/** Live placeholder children (for moving them on rename). */
	private List<MemoryFileSource> livePlaceholders() {
		List<MemoryFileSource> ret = new ArrayList<>();
		for(WeakReference<MemoryFileSource> ref : placeholders().values()) {
			MemoryFileSource kid = ref.get();
			if( kid != null ) {
				ret.add(kid);
			}
		}
		return ret;
	}

	/** True if this file is 'ancestor' or is somewhere below it. */
	private boolean isDescendantOf(MemoryFileSource ancestor) {
		for(MemoryFileSource f = this; f != null; f = f.parent) {
			if( f == ancestor ) {
				return true;
			}
		}
		return false;
	}

	/** Forget the cached path of this file and everything below it (after a move). */
	private void clearPathCache() {
		cananicalPath = null;
		for(MemoryFileSource kid : kidsMap.values()) {
			kid.clearPathCache();
		}
		for(MemoryFileSource kid : livePlaceholders()) {
			kid.clearPathCache();
		}
	}

	public FileType getFileType() {
		return fileType;
	}

	/* (non-Javadoc)
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	public int compareTo(Object o) {
		if (o instanceof FileSource) {
			int ret = getAbsolutePath().compareTo(((FileSource) o).getAbsolutePath());
			return ret != 0 ? ret : getClass().getName().compareTo(o.getClass().getName());
		}
		return getAbsolutePath().compareTo(String.valueOf(o));
	}

	
	@Override
	public boolean canOwnerRead() throws IOException {
		return canOwnerRead;
	}

	@Override
	public boolean canOwnerWrite() throws IOException {
		return  canOwnerWrite;
	}

	@Override
	public boolean canOwnerExecute() throws IOException {
		return canExecute;
	}

	@Override
	public boolean canGroupRead() throws IOException {
		return canGroupRead;
	}

	@Override
	public boolean canGroupWrite() throws IOException {
		return canGroupWrite;
	}
	@Override
	public boolean canGroupExecute() throws IOException {
		return canGroupExecute;
	}


	@Override
	public boolean canOtherRead() throws IOException {
		return canOtherRead;
	}

	@Override
	public boolean canOtherWrite() throws IOException {
		return canOtherWrite;
	}
	@Override
	public boolean canOtherExecute() throws IOException {
		return canOtherExecute;
	}


	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSource#createNewFile()
	 */
	/**
	 * Same failures as java.io.FileInputStream: a missing file, a directory or
	 * no read permission is a FileNotFoundException. (Reading a missing file
	 * used to return an empty stream, and permission problems threw
	 * IllegalAccessError, which is a java.lang.Error.)
	 */
	private void checkReadable() throws IOException {
		if( !exists() ) {
			throw new FileNotFoundException(getAbsolutePath()+" (No such file or directory)");
		}
		if( isDirectory() ) {
			throw new FileNotFoundException(getAbsolutePath()+" (Is a directory)");
		}
		if( !canRead() ) {
			throw new FileNotFoundException(getAbsolutePath()+" (Permission denied)");
		}
	}

	public boolean createNewFile() throws IOException {
		synchronized (lock()) {
			// Same contract as java.io.File.createNewFile(): false if it already
			// exists, IOException if the parent directory doesn't exist.
			if( exists() ) {
				return false;
			}
			if( parent == null || !parent.isDirectory() ) {
				throw new IOException("No such file or directory: "+getParent());
			}
			data = new byte[0];
			fileType = FileType.File;
			canOwnerRead = canOwnerWrite = true;
			deleted = false;
			lastModified = lastAccessed = createDate = System.currentTimeMillis();
			updateRetention();
			return true;
		}
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSource#delete()
	 */
	public boolean delete() {
		synchronized (lock()) {
			// Same contract as java.io.File.delete(): false if it doesn't exist or
			// is a directory that isn't empty. (It used to always return true, never
			// unlinked the node and happily "deleted" non-empty directories.)
			if( isRoot || parent == null ) {
				return false;
			}
			if( !exists() ) {
				if( linkedTo == null ) {
					return false;
				}
				linkedTo = null;           // deleting a link removes the link itself
				updateRetention();
				return true;
			}
			if( isDirectory() ) {
				for(MemoryFileSource kid : kidsMap.values()) {
					if( kid.fileType != FileType.Undefined || !kid.kidsMap.isEmpty() ) {
						return false;   // not empty
					}
				}
				// Only link entries are left (they never show in listFiles()); they go with the directory.
				kidsMap.clear();
			}
			deleted = true;
			data = null;
			fileType = FileType.Undefined;
			updateRetention();
			return true;
		}
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSource#exists()
	 */
	public boolean exists() {
		return fileType != FileType.Undefined;
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSource#getCanonicalPath()
	 */
	public String getCanonicalPath() throws IOException {
		synchronized (lock()) {
			if( cananicalPath == null ) {
				{
					if( cananicalPath == null ) {
						StringBuilder tmp = new StringBuilder();
						if( parent != null ) {
							tmp.append(parent.getCanonicalPath());
						} else {
							return "/";
						}
						tmp.append('/');
						if( (name.isEmpty() || !name.equals("/"))) {
							tmp.append(name);
						}
						cananicalPath = tmp.toString().trim();
					}
				}
				if( cananicalPath.startsWith("//")) {
					cananicalPath = cananicalPath.substring(1);
				}
			}

			return cananicalPath;
		}
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSource#getName()
	 */
	public String getName() {
		return name;
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSource#getParent()
	 */
	public String getParent() {
		return parent == null ? null:parent.getName();		
	}

	/**
	 * True if child is this file or below it. (A plain startsWith used to
	 * treat /x/ab as a child of /x/a.)
	 */
	public boolean isChildOfMine(FileSource child) {
		boolean ret = (child instanceof MemoryFileSource);
		if( ret ){
			String p1 = child.getAbsolutePath();
			String p2 = getAbsolutePath();
			String prefix = p2.endsWith("/") ? p2 : p2+"/";
			ret = p1.equals(p2) || p1.startsWith(prefix);
		}

		return ret;
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSource#getParentFile()
	 */
	public FileSource getParentFile() {		
		return parent;
	}



	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSource#isDirectory()
	 */
	public boolean isDirectory() {
		return fileType == FileType.Directory;
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSource#isFile()
	 */
	public boolean isFile() {
		return fileType == FileType.File;
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSource#length()
	 */
	public long length() {
		return data == null ? 0 : data.length;
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSource#lastModified()
	 */
	public long lastModified() {
		long ret = lastModified;
		return ret;
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSource#listFiles()
	 */
	public FileSource[] listFiles() throws IOException {
		return listFiles((FileSourceFilter)null);
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSource#listFiles(us.bringardner.io.FileSourceFilter)
	 */
	public FileSource[] listFiles(FileSourceFilter filter) throws IOException {
		synchronized (lock()) {
			if( !canRead() ) {
				// was IllegalAccessError, a java.lang.Error that callers' catch (Exception) misses
				throw new AccessDeniedException(getAbsolutePath());
			}
			MemoryFileSource [] ret = null;
			ArrayList<MemoryFileSource> list = new ArrayList<MemoryFileSource>();


			for(MemoryFileSource file : kidsMap.values() ) {
				if( file.fileType != FileType.Undefined) {
					if(filter==null || filter.accept(file)){
						list.add(file);
					}
				}
			}

			ret = new MemoryFileSource[list.size()];

			for(int idx=0; idx < ret.length; idx++ ) {
				ret[idx] = (MemoryFileSource)list.get(idx);
			}

			return ret;
		}
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSource#mkdir()
	 */
	public boolean mkdir()  {
		synchronized (lock()) {
			if( isFile() ) {
				return false;
			}
			if(parent!=null && !parent.exists()) {
				return false;
			}
			fileType = FileType.Directory;
			canOwnerRead = canOwnerWrite = true;
			updateRetention();

			return true;
		}
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSource#mkdirs()
	 */
	public boolean mkdirs() throws IOException {
		synchronized (lock()) {
			boolean ret = false;
			if( parent != null ) {
				ret = parent.mkdirs();			
			} else {
				ret = true;
			}

			if( ret ) {
				ret = mkdir();
			}
			return ret;
		}
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSource#renameTo(us.bringardner.io.FileSource)
	 */
	public boolean renameTo(FileSource dest) throws IOException {
		synchronized (lock()) {
			boolean ret = false;
			if( exists() && 
					!isRoot && 
					canOwnerWrite() )  {
				if (dest instanceof MemoryFileSource && ((MemoryFileSource) dest).theCreator == theCreator) {
					// (only within one memory file system; moving a node into another
					// factory's tree would leave it with the wrong factory and lock)
					MemoryFileSource newFile = (MemoryFileSource) dest;
					if( !newFile.exists() && 
							!equals(newFile) &&
							!newFile.isRoot &&
							!newFile.isDescendantOf(this) ) {

						newFile.data= data;
						newFile.canOwnerRead = canOwnerRead;
						newFile.canOwnerWrite = canOwnerWrite;
						newFile.canExecute = canExecute;
						newFile.canGroupRead = canGroupRead;
						newFile.canGroupWrite = canGroupWrite;
						newFile.canGroupExecute = canGroupExecute;
						newFile.canOtherRead = canOtherRead;
						newFile.canOtherWrite = canOtherWrite;
						newFile.canOtherExecute = canOtherExecute;
						newFile.fileType = fileType;
						newFile.group = group;
						newFile.lastModified = lastModified;
						newFile.lastAccessed = lastAccessed;
						newFile.createDate = createDate;
						newFile.owner = owner;
						newFile.linkedTo = linkedTo;
						newFile.isRoot = isRoot;
						newFile.deleted = false;

						// Move the children too (previously a renamed directory lost them).
						for(MemoryFileSource kid : kidsMap.values()) {
							kid.parent = newFile;
							kid.clearPathCache();
							newFile.kidsMap.put(kid.getName(), kid);
						}
						kidsMap.clear();
						for(MemoryFileSource kid : livePlaceholders()) {
							kid.parent = newFile;
							kid.clearPathCache();
							newFile.addPlaceholder(kid);
						}
						placeholders().clear();

						data = null;
						linkedTo = null;
						fileType = FileType.Undefined;
						canOwnerRead = canOwnerWrite = false;
						newFile.updateRetention();
						updateRetention();
						ret = true;

					}

				}
			}

			return ret; 
		}
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSource#setLastModified(long)
	 */
	public boolean setLastModifiedTime(long time) {
		lastModified = time;
		return true;
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSource#setReadOnly()
	 */
	public boolean setReadOnly() {
		canOwnerRead = true;
		return canOwnerWrite=false;
	}


	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSource#getInputStream()
	 */
	public InputStream getInputStream() throws IOException {
		synchronized (lock()) {
			if( !exists() && linkedTo != null ) {
				return linkedTo.getInputStream();
			}
			checkReadable();
			ByteArrayInputStream ret = new ByteArrayInputStream(data == null ? new byte[0] : data);
			lastAccessed = System.currentTimeMillis();

			return ret;
		}
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSource#getOutputStream()
	 */
	public OutputStream getOutputStream() throws FileNotFoundException {
		synchronized (lock()) {
			if(fileType != FileType.Undefined &&  !canOwnerWrite ) {
				throw new FileNotFoundException(getAbsolutePath()+" (Permission denied)");
			}
			if( exists() && fileType==FileType.Directory) {
				throw new FileNotFoundException();
			}

			ByteArrayOutputStream ret = new ByteArrayOutputStream() {
				@Override
				public void close() throws IOException {
					super.close();
					synchronized (lock()) {
						data = super.toByteArray();
					}
				}
			};
			fileType = FileType.File;
			canOwnerRead = canOwnerWrite = true;
			updateRetention();
			lastAccessed = System.currentTimeMillis();
			lastModified = System.currentTimeMillis();

			return ret;
		}
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSource#getOutputStream(boolean)
	 */
	public OutputStream getOutputStream(boolean append) throws FileNotFoundException {
		synchronized (lock()) {
			if( exists() && fileType==FileType.Directory) {
				throw new FileNotFoundException(getAbsolutePath()+" (Is a directory)");
			}
			if( fileType != FileType.Undefined && !canOwnerWrite ) {
				throw new FileNotFoundException(getAbsolutePath()+" (Permission denied)");
			}

			if( data == null ) {
				data = new byte[0];
				fileType = FileType.File;
				canOwnerRead = canOwnerWrite = true;
				updateRetention();
			}

			// buffer only the appended bytes (it used to be sized to the existing data)
			ByteArrayOutputStream ret = new ByteArrayOutputStream(256) {
				@Override
				public void close() throws IOException {
					super.close();
					byte [] tmp = super.toByteArray();
					synchronized (lock()) {
						if( tmp.length>0) {
							if( data.length == 0 ) {
								data = tmp;
							} else {
								byte [] tmp2 = Arrays.copyOf(data, data.length+tmp.length);
								System.arraycopy(tmp, 0, tmp2, data.length, tmp.length);
								data = tmp2;
							}
						}
					}
				}
			};
			lastAccessed = System.currentTimeMillis();
			lastModified = System.currentTimeMillis();
			return ret;
		}
	}



	public String toString() {
		return getAbsolutePath();
	}

	/* Return a Factory that can be used to create a factory of this type.
	 * @see us.bringardner.io.FileSource#getFileSourceFactory()
	 */
	public FileSourceFactory getFileSourceFactory() {
		if( theCreator == null ){
			theCreator = new MemoryFileSourceFactory();
		}
		return theCreator;
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.filesource.FileSource#toURL()
	 */
	public URL toURL() throws MalformedURLException {
		URL ret = null;

		String path = null;

		try {
			path = getCanonicalPath();
		} catch (IOException e) {
			throw new MalformedURLException("Can't get path");
		}
		// Include the factory's session id: without it, resolving the URL
		// created a brand-new, empty memory file system.
		MemoryFileSourceFactory factory = (MemoryFileSourceFactory) getFileSourceFactory();
		if( factory.getSessionId() < 0 ) {
			try {
				factory.connect();   // registers the session
			} catch (IOException e) {
				throw new MalformedURLException("Can't register memory file system session: "+e);
			}
		}
		ret = new URL(FileSourceFactory.FILE_SOURCE_PROTOCOL+":"+path
				+"?"+FileSourceFactory.QUERY_STRING_SOURCE_TYPE+"="+MemoryFileSourceFactory.FACTORY_ID
				+"&"+FileSourceFactory.QUERY_STRING_SESSION_ID+"="+factory.getSessionId());

		return ret;
	}

	//14th

	/* (non-Javadoc)
	 * @see us.bringardner.io.filesource.FileSource#getChild(java.lang.String)
	 */
	public FileSource getChild(String path) throws IOException {
		String myPath = getAbsolutePath();
		return theCreator.createFileSource(myPath+(""+theCreator.getSeperatorChar())+path);
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.filesource.FileSource#getCreateDate()
	 */
	public long getCreateDate() {
		return createDate;
	}


	byte[] getData() {
		synchronized (lock()) {
			return data;
		}
	}

	void setData(byte[] data) {
		synchronized (lock()) {
			this.data = data;
		}
	}

	public static String getContentType(String name) {
		String ret = null;
		if( name !=null ) {
			int idx=name.lastIndexOf('.');
			if( idx > 0 ){
				String ext = name.substring(idx+1);
				ret = MemoryFileSourceFactory.getType(ext);
			}
		}

		return ret;
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.filesource.FileSource#getContentType()
	 */
	public String getContentType() {
		String ret = getContentType(getName());		 
		return ret;
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.filesource.FileSource#list()
	 */
	public String[] list() {
		synchronized (lock()) {
			// Only list children that exist (same set as listFiles()).
			ArrayList<String> ret = new ArrayList<String>();
			for (MemoryFileSource kid : kidsMap.values()) {
				if( kid.fileType != FileType.Undefined) {
					ret.add(kid.getName());
				}
			}

			return ret.toArray(new String[ret.size()]); 
		}
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.filesource.FileSource#list(us.bringardner.io.filesource.FileSourceFilter)
	 */
	public String[] list(FileSourceFilter filter) throws IOException {
		String [] ret = null;
		FileSource [] list = listFiles(filter);
		if( list != null ) {
			ret = new String[list.length];
			for(int idx=0; idx<ret.length; idx++ ) {
				ret[idx] = list[idx].getName();
			}
		}
		return ret;
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.filesource.FileSource#getAbsolutePath()
	 */
	public String getAbsolutePath() {
		try {
			return getCanonicalPath();
		} catch (IOException e) {
			return name;
		}
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.filesource.FileSource#getPath()
	 */
	public String getPath() {
		return getAbsolutePath();
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.filesource.FileSource#isVersionSupported()
	 */
	public boolean isVersionSupported() {
		// No version support
		return false;
	}

	@Override
	public FileSourceUser getOwner() throws IOException {
		if(owner == null ) {
			owner = theCreator.whoAmI();
		}

		return owner;
	}

	@Override
	public FileSourceGroup getGroup() throws IOException {
		if( group == null ) {
			group = getOwner().getGroup(); 
			}
		return group;
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.filesource.FileSource#getVersion()
	 */
	public long getVersion() {
		return 0;
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.filesource.FileSource#getVersionDate()
	 */
	public long getVersionDate() {
		return lastModified();
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.filesource.FileSource#setVersionDate()
	 */
	public boolean setVersionDate(long time) {
		return false;
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.filesource.FileSource#setVersion(long, boolean)
	 */
	public boolean setVersion(long version, boolean saveChange) {
		return false; 
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.filesource.FileSource#getMaxVersion()
	 */
	public long getMaxVersion() {

		return 0;
	}

	public InputStream getInputStream(long startingPos) throws IOException {
		synchronized (lock()) {
			if( !exists() && linkedTo != null ) {
				return linkedTo.getInputStream(startingPos);
			}
			// (This used to turn a missing file into an existing, empty one.)
			checkReadable();
			if( startingPos < 0 ) {
				throw new IOException("Negative starting position "+startingPos);
			}
			byte[] bytes = data == null ? new byte[0] : data;
			int start = (int) Math.min(startingPos, bytes.length);
			ByteArrayInputStream ret = new ByteArrayInputStream(bytes, start, bytes.length - start);
			lastAccessed = System.currentTimeMillis();

			return ret;
		}
	}

	@Override
	public boolean equals(Object obj) {
		boolean ret = false;
		if (obj instanceof MemoryFileSource) {			
			ret = getAbsolutePath().equals(((MemoryFileSource) obj).getAbsolutePath());			
		}
		return ret;
	}

	@Override
	public int hashCode() {
		// equals() compares absolute paths, so hash the same thing
		return getAbsolutePath().hashCode();
	}

	@Override
	public void dereferenceChilderen() {
		// Nothing to do.

	}

	@Override
	public void refresh() {
		// Nothing to do for local files

	}

	@Override
	public String getTitle() {
		return "Memory";
	}

	@Override
	public FileSource[] listFiles(ProgressMonitor progress) throws IOException {
		// this will be instantaneous so no need for progress monitor
		FileSource [] ret = listFiles();
		progress.setProgress(progress.getMaximum());
		return ret;
	}

	@Override
	public FileSource getLinkedTo() {
		return linkedTo;
	}

	@Override
	public boolean isHidden() {
		return name.startsWith(".");
	}



	@Override
	public ISeekableInputStream getSeekableInputStream() throws IOException {
		synchronized (lock()) {
			if( !exists() && linkedTo != null ) {
				return linkedTo.getSeekableInputStream();
			}
			checkReadable();
			final byte[] snapshot = data == null ? new byte[0] : data;

			final MemoryFileSource owner = this;

			return new ISeekableInputStream() {
				int filePointer = 0;

				byte [] myData = Arrays.copyOf(snapshot, snapshot.length);
				@Override
				public void seek(long pos) throws IOException {
					// Same rules as RandomAccessFile: negative is an error, and seeking
					// at or past the end is allowed (the next read returns -1).
					if( pos < 0 ) {
						throw new IOException("Negative seek offset");
					}
					filePointer = (int) Math.min(pos, myData.length);
				}

				@Override
				public int read(byte[] data) throws IOException {

					return read(data, 0, data.length);
				}

				@Override
				public int read(byte[] data, int off, int len) throws IOException {
					Objects.checkFromIndexSize(off, len, data.length);
					if( len == 0 ) {
						return 0;
					}
					int available = myData.length - filePointer;
					if( available <= 0 ) {
						return -1;
					}
					int count = Math.min(len, available);
					System.arraycopy(myData, filePointer, data, off, count);
					filePointer += count;
					return count;
				}

				@Override
				public int read() throws IOException {
					int ret = -1;
					if( filePointer < myData.length) {
						ret = myData[filePointer++] & 0xFF;  // unsigned, so bytes >= 0x80 aren't mistaken for EOF
					}
					return ret;
				}

				@Override
				public long length() throws IOException {

					return myData.length;
				}

				/**
				 * A view of this stream starting at the current file pointer.
				 * Reading from it advances this stream's pointer, and closing it
				 * closes this stream (same behavior as FileProxySeekableInputStream).
				 */
				@Override
				public InputStream getInputStream() throws IOException {
					final ISeekableInputStream seekable = this;
					return new InputStream() {
						@Override
						public int read() throws IOException {
							return seekable.read();
						}

						@Override
						public int read(byte[] b, int off, int len) throws IOException {
							return seekable.read(b, off, len);
						}

						@Override
						public long skip(long n) throws IOException {
							int remaining = Math.max(0, myData.length - filePointer);
							if( n <= 0 || remaining == 0 ) {
								return 0;
							}
							int skipped = (int) Math.min(n, remaining);
							filePointer += skipped;
							return skipped;
						}

						@Override
						public int available() {
							return Math.max(0, myData.length - filePointer);
						}

						@Override
						public void close() throws IOException {
							seekable.close();
						}
					};
				}

				@Override
				public long getFilePointer() throws IOException {
					return filePointer;
				}

				@Override
				public FileSource getFile() throws IOException {
					return owner;
				}

				@Override
				public void close() throws IOException {
					filePointer = myData.length+1;				
				}
			};
		}
	}



	public void addChild(MemoryFileSource file) {
		synchronized (lock()) {
			addPlaceholder(file);
			file.updateRetention();
		}
	}

	@Override
	public boolean setExecutable(boolean b) {
		canExecute = true;
		return true;
	}

	@Override
	public boolean setReadable(boolean b) {
		canOwnerRead = b;
		return true;
	}

	@Override
	public boolean setWritable(boolean b) {
		canOwnerWrite = b;
		return true;
	}

	@Override
	public boolean setExecutable(boolean b, boolean ownerOnly) {
		canExecute =b;
		canOtherExecute = canGroupExecute == !ownerOnly;
		return true;
	}

	@Override
	public boolean setReadable(boolean b, boolean ownerOnly) {
		canOwnerRead = b;
		canOtherRead = canGroupRead == !ownerOnly;
		return true;
	}

	@Override
	public boolean setWritable(boolean b, boolean ownerOnly) {
		setWritable(b);
		canOtherWrite = canGroupWrite == !ownerOnly;
		return true;
	}

	@Override
	public boolean setOwnerReadable(boolean b) throws IOException {
		canOwnerRead = b;
		return true;
	}

	@Override
	public boolean setOtherWritable(boolean b) throws IOException {
		canOtherWrite = b;
		return true;
	}

	@Override
	public boolean setOwnerWritable(boolean b) throws IOException {
		canOwnerWrite = b;
		return true;
	}

	@Override
	public boolean setGroupExecutable(boolean b) throws IOException {
		canGroupExecute = b;
		return true;
	}
	@Override
	public boolean setGroupReadable(boolean b) throws IOException {
		canGroupRead = b;
		return true;
	}

	@Override
	public boolean setGroupWritable(boolean b) throws IOException {
		canGroupWrite = b;
		return true;
	}

	@Override
	public boolean setOtherExecutable(boolean b) throws IOException {
		canOtherExecute = b;
		return true;
	}
	@Override
	public boolean setOtherReadable(boolean b) throws IOException {
		canOtherRead = b;
		return true;
	}
	@Override
	public boolean setOwnerExecutable(boolean b) throws IOException {
		canExecute = b;
		return true;
	}

	@Override
	public long lastAccessTime() throws IOException {		
		return lastAccessed;
	}

	@Override
	public long creationTime() throws IOException {
		return createDate;
	}


	@Override
	public boolean setLastAccessTime(long time) throws IOException {
		lastAccessed = time;
		return true;
	}

	@Override
	public boolean setCreateTime(long time) throws IOException {
		createDate = time;
		return true;
	}

	@Override
	public boolean setGroup(GroupPrincipal group1) throws IOException {
		boolean ret = false;
		if (group1 instanceof FileSourceGroup) {
			// was also getOwner().setGroup(group), which changed the owner's
			// primary group, and so the group of every file it owns
			group = (FileSourceGroup) group1;
			ret = true;
		}
		
		return ret;
	}

	@Override
	public boolean setOwner(UserPrincipal owner1) throws IOException {
		boolean ret = false;
		if (owner1 instanceof FileSourceUser) {
			owner = (FileSourceUser) owner1;
			ret = true;
		}
		
		return ret;
	}

	@Override
	public IRandomAccessStream getRandomAccessStream(String mode) throws IOException {
		return new FileSourceRandomAccessStream(new MemoryRandomAccessIoController(this), mode);
	}

}
