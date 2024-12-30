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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.UserPrincipal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

import javax.swing.ProgressMonitor;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.FileSourceFilter;
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
	private FileType fileType=FileType.Undefined;

	private MemoryFileSourceFactory theCreator ;
	private GroupPrincipal group;
	private UserPrincipal owner;
	private MemoryFileSource parent;
	private Map<String,MemoryFileSource> kidsMap = new TreeMap<>();
	private boolean canRead=true;
	private boolean canWrite=true;
	private boolean canExecute=true;
	private boolean canGroupRead=true;
	private boolean canGroupWrite=true;
	private boolean canGroupExecute=true;
	private boolean canOtherRead=true;
	private boolean canOtherWrite=true;
	private boolean canOtherExecute=true;
	private boolean deleted;
	private byte[] data;
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
			canRead = canWrite = true;
		}

	}

	MemoryFileSource getChildByName(String name) {
		return kidsMap.get(name);
	}

	public FileType getFileType() {
		return fileType;
	}

	/* (non-Javadoc)
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	public int compareTo(Object o) {
		return getAbsolutePath().compareTo(o.toString());
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSource#canRead()
	 */
	public boolean canRead() {
		return canRead;
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSource#canWrite()
	 */
	public boolean canWrite() {

		return canWrite;
	}

	@Override
	public boolean canOwnerRead() throws IOException {
		return canRead;
	}

	@Override
	public boolean canOwnerWrite() throws IOException {
		return  canWrite;
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
	public boolean createNewFile() throws IOException {
		return false;
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSource#delete()
	 */
	public boolean delete() {
		deleted = true;
		data = null;
		fileType = FileType.Undefined;
		return deleted;
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
		if( cananicalPath == null ) {
			synchronized (this) {
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

	public boolean isChildOfMine(FileSource child) {
		boolean ret = (child instanceof MemoryFileSource);
		if( ret ){
			try {
				ret = child.getCanonicalPath().startsWith(getCanonicalPath());
			} catch (IOException e) {
				e.printStackTrace();
			}
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
	public FileSource[] listFiles() {
		return listFiles((FileSourceFilter)null);
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSource#listFiles(us.bringardner.io.FileSourceFilter)
	 */
	public FileSource[] listFiles(FileSourceFilter filter) {
		if( !canRead ) {
			throw new IllegalAccessError("Permission denied");
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

	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSource#mkdir()
	 */
	public boolean mkdir() {
		if( isFile() ) {
			return false;
		}
		fileType = FileType.Directory;
		canRead = canWrite = true;

		return true;
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSource#mkdirs()
	 */
	public boolean mkdirs() {
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

	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSource#renameTo(us.bringardner.io.FileSource)
	 */
	public boolean renameTo(FileSource dest) throws IOException {
		boolean ret = false;
		if( exists() && 
				!isRoot && 
				canWrite )  {
			if (dest instanceof MemoryFileSource) {
				MemoryFileSource newFile = (MemoryFileSource) dest;
				if( !newFile.exists() && 
						!equals(newFile) &&
						!newFile.isRoot ) {

					newFile.data= data;
					newFile.canRead = canRead;
					newFile.canWrite = canWrite;
					newFile.fileType = fileType;
					newFile.group = group;
					newFile.lastModified = lastModified;
					newFile.owner = owner;
					newFile.isRoot = isRoot;

					
					fileType = FileType.Undefined;
					canRead = canWrite = false;
					ret = true;

				}

			}
		}

		return ret; 
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
		canRead = true;
		return canWrite=false;
	}


	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSource#getInputStream()
	 */
	public InputStream getInputStream() throws FileNotFoundException {
		if(fileType != FileType.Undefined &&  !canRead ) {
			throw new IllegalAccessError("Permission denied");
		}
		if( exists() && fileType==FileType.Directory) {
			throw new FileNotFoundException();
		}

		if( data == null ) {
			data = new byte[0];
		}
		ByteArrayInputStream ret = new ByteArrayInputStream(data);
		lastAccessed = System.currentTimeMillis();

		return ret;
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSource#getOutputStream()
	 */
	public OutputStream getOutputStream() throws FileNotFoundException {
		if(fileType != FileType.Undefined &&  !canWrite ) {
			throw new IllegalAccessError("Permission denied");
		}
		if( exists() && fileType==FileType.Directory) {
			throw new FileNotFoundException();
		}

		ByteArrayOutputStream ret = new ByteArrayOutputStream() {
			@Override
			public void close() throws IOException {
				super.close();
				data = super.toByteArray();
			}
		};
		fileType = FileType.File;
		canRead = canWrite = true;
		lastAccessed = System.currentTimeMillis();
		lastModified = System.currentTimeMillis();

		return ret;
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSource#getOutputStream(boolean)
	 */
	public OutputStream getOutputStream(boolean append) throws FileNotFoundException {
		if( exists() && fileType==FileType.Directory) {
			throw new FileNotFoundException();
		}

		if( data == null ) {
			data = new byte[0];
			fileType = FileType.File;
			canRead = canWrite = true;
		}

		ByteArrayOutputStream ret = new ByteArrayOutputStream(data.length==0?200:data.length) {
			@Override
			public void close() throws IOException {
				super.close();
				byte [] tmp = super.toByteArray();
				if( tmp.length>0) {
					if( data.length == 0 ) {
						data = tmp;
					} else {
						byte [] tmp2 = new byte [data.length+tmp.length];
						for (int idx = 0; idx < data.length; idx++) {
							tmp2[idx] = data[idx];
						}
						for (int idx = 0; idx < tmp.length; idx++) {
							tmp2[data.length+idx] = tmp[idx];
						}
						data = tmp2;
					}
				}
			}
		};
		lastAccessed = System.currentTimeMillis();
		lastModified = System.currentTimeMillis();
		return ret;
	}



	public String toString() {
		return getAbsolutePath();
	}

	/* Return a Factory taht can be used to create a factory of this type.
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
		ret = new URL(FileSourceFactory.FILE_SOURCE_PROTOCOL+":"+path+"?"+FileSourceFactory.QUERY_STRING_SOURCE_TYPE+"="+MemoryFileSourceFactory.FACTORY_ID);

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
		String ret [] = new String[kidsMap.size()];
		int idx=0;
		for (String n : kidsMap.keySet()) {
			ret[idx] = n;
		}

		return ret; 
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.filesource.FileSource#list(us.bringardner.io.filesource.FileSourceFilter)
	 */
	public String[] list(FileSourceFilter filter) {
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
	public UserPrincipal getOwner() throws IOException {
		if(owner == null ) {
			owner = new UserPrincipal() {
				@Override
				public String getName() {
					return "Unknown";
				}
			};
		}

		return owner;
	}

	@Override
	public GroupPrincipal getGroup() throws IOException {
		if( group == null ) {
			group = new GroupPrincipal() {

				@Override
				public String getName() {
					return "Unknown";
				}
			};
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
		if( !canRead ) {
			throw new IllegalAccessError("Permission denied");
		}
		if( exists() && fileType==FileType.Directory) {
			throw new FileNotFoundException();
		}

		ByteArrayInputStream ret = null;

		if( data == null ) {
			ret = new ByteArrayInputStream(new byte[0]);
		} else {
			ret = new ByteArrayInputStream(data,(int)startingPos,data.length);
		}
		fileType = FileType.File;
		canRead = canWrite = true;
		lastAccessed = System.currentTimeMillis();

		return ret;
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
	public FileSource[] listFiles(ProgressMonitor progress) {
		// this will be instantaneous so no need for progress monitor
		FileSource [] ret = listFiles();
		progress.setProgress(progress.getMaximum());
		return ret;
	}

	@Override
	public FileSource getLinkedTo() {
		return null;
	}

	@Override
	public boolean isHidden() {
		return name.startsWith(".");
	}



	@Override
	public ISeekableInputStream getSeekableInputStream() throws IOException {
		if( !canRead ) {
			throw new IllegalAccessError("Permission denied");
		}
		if( data == null ) {
			data = new byte[0];
		}

		final MemoryFileSource owner = this;

		return new ISeekableInputStream() {
			int filePointer = 0;

			byte [] myData = Arrays.copyOf(owner.data, owner.data.length);
			@Override
			public void seek(long length) throws IOException {
				filePointer = (int)length;
				if( filePointer >= myData.length) {
					filePointer = myData.length-1;
				}
			}

			@Override
			public int read(byte[] data) throws IOException {

				return read(data, 0, data.length);
			}

			@Override
			public int read(byte[] data, int i, int toRead) throws IOException {
				int ret = 0;

				for (int idx = i; idx < data.length && ret < toRead; idx++) {
					int tmp = read();
					if( tmp == -1) {
						break;
					}
					data[idx]  =(byte) tmp;
				}

				return ret;
			}

			@Override
			public int read() throws IOException {
				int ret = -1;
				if( filePointer < myData.length) {
					ret = (int)myData[filePointer++];
				}
				return ret;
			}

			@Override
			public long length() throws IOException {

				return myData.length;
			}

			@Override
			public InputStream getInputStream() throws IOException {
				return owner.getInputStream();
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



	public void addChild(MemoryFileSource file) {
		kidsMap.put(file.getName(), file);
	}

	@Override
	public boolean setExecutable(boolean b) {
		canExecute = true;
		return true;
	}

	@Override
	public boolean setReadable(boolean b) {
		canRead = b;
		return true;
	}

	@Override
	public boolean setWritable(boolean b) {
		canWrite = b;
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
		canRead = b;
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
		canRead = b;
		return true;
	}

	@Override
	public boolean setOtherWritable(boolean b) throws IOException {
		canOtherWrite = b;
		return true;
	}

	@Override
	public boolean setOwnerWritable(boolean b) throws IOException {
		canWrite = b;
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
	public boolean setGroup(GroupPrincipal group) throws IOException {
		this.group = group;
		return true;
	}

	@Override
	public boolean setOwner(UserPrincipal owner) throws IOException {
		this.owner = owner;
		return true;
	}

}
