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
 * ~version~V000.01.09-V000.01.00-V000.00.01-V000.00.00-
 */
/*
 * Created on Dec 7, 2004
 *
 */
package us.bringardner.io.filesource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.UserPrincipal;

import javax.swing.ProgressMonitor;


/**
 * @author Tony Bringardner
 *  This is intended to define an interface that can be used to represent 
 *  an object that can replace a 'java.io.File' object.  
 *  
 */

public interface FileSource extends Serializable, Comparable<Object> {



	//  Feeble attempt to init factory class
	String pkgc = FileSourceFactory.getAllHandlerPkgs();

	/*
	 *  Compares two abstract pathnames lexicographically.
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	public abstract int compareTo(Object o) ;

	/*
	 * GEt the creation date for this fileSource
	 */
	public long getCreateDate() throws IOException;


	/*
	 * Get the MIME Content Type
	 */

	public String getContentType() ;

	/**
	 * Tests whether the application can read the 
	 * file denoted by this abstract pathname.
	 * This is only here for comparability with java.io.File.  
	 * 
	 * @return true if and only if the file system actually contains a file denoted by this abstract pathname 
	 * 	and the application is allowed to write to the file; false otherwise.  
	 * @throws IOException 
	 * 
	 */
	default public boolean canRead() throws IOException  {		
		try {

			FileSourceUser me = getFileSourceFactory().whoAmI();
			if ( getOwner().getName().equalsIgnoreCase(me.getName())) {
				return canOwnerRead();
			} else if( me.hasGroup(
					getGroup().getName()
					)) {
				return canGroupRead();
			} 
			return canOtherRead();
		} catch (Exception e) {
		}
		return false;
	}

	/**
	 * Tests whether the application can modify the file denoted by this abstract pathname. 
	 * This is only here for comparability with java.io.File.  
	 * 
	 * @return
	 * @throws IOException 
	 * 
	 */
	default public boolean canWrite() throws IOException {
		try {

			FileSourceUser me = getFileSourceFactory().whoAmI();
			if ( getOwner().getName().equalsIgnoreCase(me.getName())) {
				return canOwnerWrite();
			} else if( me.hasGroup(getGroup().getName())) {
				return canGroupWrite();
			} 
			return canOtherWrite();
		} catch (Exception e) {
		}
		return false;
	}

	/**
	 * Tests whether the application can execute the file denoted by this abstract pathname. 
	 * This is only here for comparability with java.io.File.  
	 * 
	 * @return
	 * @throws IOException 
	 * 
	 */
	default public boolean canExecute() throws IOException {
		try {

			FileSourceUser me = getFileSourceFactory().whoAmI();
			if ( getOwner().getName().equalsIgnoreCase(me.getName())) {
				return canOwnerExecute();
			} else if( me.hasGroup(getGroup().getName())) {
				return canGroupExecute();
			} 
			return canOtherExecute();
		} catch (Exception e) {
		}
		return false;
	}

	boolean canOwnerRead() throws IOException ;

	boolean canOwnerWrite() throws IOException ;

	boolean canOwnerExecute() throws IOException ;

	boolean canGroupRead() throws IOException ;

	boolean canGroupWrite() throws IOException ;

	boolean canGroupExecute() throws IOException;

	boolean canOtherRead() throws IOException ;

	boolean canOtherWrite() throws IOException ;

	boolean canOtherExecute() throws IOException;

	/*
	 * Atomically creates a new, empty file named by this abstract pathname if 
	 * and only if a file with this name does not yet exist.  
	 */
	public boolean createNewFile() throws IOException ;

	/*
	 * Equivalent to dir.getFactory().createFileSource(dir,name)
	 */
	public FileSource getChild(String path) throws IOException ;

	/*
	 * Deletes the file or directory denoted by this abstract pathname. 
	 * If this pathname denotes a directory, then the directory must be empty 
	 * in order to be deleted. 
	 */
	public boolean delete() throws IOException ;


	public boolean exists() throws IOException ;

	/*
	 * Return a FileSource Factory capable of creating FileSources
	 * of the same type as this FileSource. 
	 */

	public FileSourceFactory getFileSourceFactory();

	/*
	 * Tests whether this abstract pathname is absolute. The definition of absolute pathname is system dependent. 
	 * On UNIX systems, a pathname is absolute if its prefix is "/". On Microsoft Windows systems, a pathname is absolute 
	 * if its prefix is a drive specifier followed by "\\", or if its prefix is "\\\\".
	 *
	 *	Returns: true if this abstract pathname is absolute, false otherwise
	 */
	public String getAbsolutePath() ;

	/*
	 * Returns the canonical pathname string of this abstract pathname.
	 * A canonical pathname is both absolute and unique. The precise definition 
	 * of canonical form is system-dependent. 
	 */
	public String getCanonicalPath() throws IOException;

	public String getName() ;

	public String getParent();

	/*
	 * Get the parent file of this object.  If an object
	 * is create to represent the parent, it's stored in a cache
	 * for performance issues.
	 */
	public FileSource getParentFile() throws IOException  ;

	/**
	 * Get the first 'size' byte of a file.
	 * The purpose is to provide a way to get the first few bytes 
	 * 	without transferring any other data across the network  
	 * 
	 * @param size
	 * @return
	 * @throws IOException 
	 */
	default byte[] head(int size) throws IOException {
		int len = (int) length();
		byte [] ret = new byte[len < size ? len : size];
		InputStream in = getInputStream();
		try {
			int got = in.read(ret);
			while(got >=0 && got < ret.length) {
				int icnt = in.read(ret, got, len-got);
				if( icnt < 0 ) {
					break;
				} else {
					got += icnt;
				}
			}
		} finally {
			try {in.close();} catch (Exception e) {}
		}


		return ret;
	}

	/**
	 * Get the last 'size' byte of a file.
	 * The purpose is to provide a way to get the last few bytes 
	 * 	without transferring any other data across the network  
	 * 
	 * @param size
	 * @return
	 * @throws IOException 
	 */
	default byte[] tail(int size) throws IOException {
		byte[] ret = new byte[0];
		long len = length();
		if( len > 0 ) {
			InputStream in = getInputStream();
			try {

				long skip = len-size;
				if( skip >=0) {
					//in.skipNBytes(skip);
					in.skip(skip);
				}
				ret = new byte[size];

				int got = in.read(ret);
				while( got >=0 && got < size) {
					int cnt = in.read(ret, got, size-got);
					if( cnt < 0 ) {
						break;
					}
					got += cnt;
				}
			} finally {
				try {in.close();} catch (Exception e) {}
			} 
		}

		return ret;
	}


	/*
	 * Return true if FileSource is a child of mine..  System dependent.  
	 */
	public boolean isChildOfMine(FileSource child)  throws IOException ;

	public boolean isDirectory() throws IOException ;

	public boolean isFile()  throws IOException ;

	public boolean isHidden()  throws IOException ;

	public long length() throws IOException ;

	public long lastAccessTime () throws IOException;

	public long creationTime() throws IOException;

	public long lastModified() throws IOException;

	public String [] list() throws IOException ;

	public String[] list(FileSourceFilter filter) throws IOException ;

	public FileSource [] listFiles() throws IOException;

	public FileSource[] listFiles(FileSourceFilter filter) throws IOException ;	

	public boolean mkdir()  throws IOException ;

	public boolean mkdirs()  throws IOException ;

	public boolean renameTo(FileSource dest)  throws IOException ;

	public boolean setLastModifiedTime(long time) throws IOException;

	public boolean setLastAccessTime(long time) throws IOException;

	public boolean setCreateTime(long time) throws IOException;

	/**
	 * Set access permission for the file owner
	 * @param c
	 * @throws IOException 
	 */
	public boolean  setExecutable(boolean b) throws IOException;

	/**
	 * Set access permission for the file owner
	 * @param b
	 * @throws IOException 
	 */
	public boolean setReadable(boolean b) throws IOException;
	/**
	 * Set access permission for the file owner
	 * @param b
	 * @throws IOException 
	 */
	public boolean setWritable(boolean b) throws IOException;

	public boolean setExecutable(boolean b, boolean  ownerOnly)throws IOException;
	public boolean setReadable(boolean b, boolean  ownerOnly)throws IOException;
	public boolean setWritable(boolean b, boolean  ownerOnly) throws IOException;

	/**
	 * Set access permission for the file owner
	 * @param c
	 * @throws IOException 
	 */
	boolean setOwnerExecutable(boolean b) throws IOException ;
	/**
	 * Set access permission for the file owner
	 * @param c
	 * @throws IOException 
	 */

	boolean setOwnerReadable(boolean b) throws IOException;

	/**
	 * Set access permission for the file owner
	 * @param c
	 * @throws IOException 
	 */
	boolean setOwnerWritable(boolean b) throws IOException ;
	/**
	 * Set access permission for the file group
	 * @param c
	 * @throws IOException 
	 */
	boolean setGroupExecutable(boolean b) throws IOException;
	/**
	 * Set access permission for the file group
	 * @param c
	 * @throws IOException 
	 */
	boolean setGroupReadable(boolean b) throws IOException ;

	/**
	 * Set access permission for the file group
	 * @param c
	 * @throws IOException 
	 */
	boolean setGroupWritable(boolean b) throws IOException ;

	/**
	 * Set access permission for anyone other the file owner and group group
	 * @param c
	 * @throws IOException 
	 */
	boolean setOtherExecutable(boolean b) throws IOException ;

	/**
	 * Set access permission for anyone other the file owner and group group
	 * @param c
	 * @throws IOException 
	 */
	boolean setOtherReadable(boolean b) throws IOException ;

	/**
	 * Set access permission for anyone other the file owner and group group
	 * @param c
	 * @throws IOException 
	 */
	boolean setOtherWritable(boolean b) throws IOException ;

	/*
	 * Marks the file or directory named by this abstract pathname 
	 * so that only read operations are allowed.
	 */

	public boolean setReadOnly()  throws IOException ;

	/**
	 * This is to reduce the memory overhead of maintain a large tree of objects 
	 * when iterating over a large file structure.
	 */
	public void dereferenceChilderen() ;

	public InputStream getInputStream() throws  IOException;


	public OutputStream getOutputStream() throws  IOException;


	public OutputStream getOutputStream(boolean append) throws  IOException;

	default IRandomAccessStream getRandomAccessStream(String mode) throws IOException {
		throw new IOException("Not supported");
	}

	public URL toURL() throws MalformedURLException;

	//  These are supported by JdbcFile but not a normal Java File
	public boolean isVersionSupported()  throws IOException ;
	public long getVersion()  throws IOException ;
	public long getVersionDate() throws IOException;
	public boolean setVersionDate(long time)  throws IOException ;
	public boolean setVersion(long version, boolean saveChange) throws IOException;

	/**
	 * @return
	 * @throws IOExceptioGroup */
	public abstract long getMaxVersion() throws IOException;

	/**
	 * @param startingPosition
	 * @return An InputStream set to the requested startingPosition.
	 * @throws IOException 
	 */
	public InputStream  getInputStream(long startingPosition) throws IOException;

	public abstract void refresh() throws IOException;

	public abstract String getTitle() throws IOException;

	public abstract FileSource[] listFiles(ProgressMonitor progress) throws IOException;

	public abstract FileSource getLinkedTo() throws IOException;

	public abstract ISeekableInputStream getSeekableInputStream() throws IOException;

	public abstract GroupPrincipal getGroup() throws IOException;

	public boolean setGroup(GroupPrincipal group) throws IOException ;

	public abstract UserPrincipal getOwner() throws IOException;

	public boolean setOwner(UserPrincipal owner) throws IOException ;



}
