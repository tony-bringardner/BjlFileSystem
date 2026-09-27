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
	 * 	and the application is allowed to read the file; false otherwise.  
	 * @throws IOException 
	 * 
	 */
	/**
	 * Whether the current user can read this file, using the owner, group or
	 * other permission that applies to them.
	 * (These defaults used to catch every exception and return false, hiding
	 * I/O errors; they now propagate IOException and treat a missing owner or
	 * group as "doesn't match".)
	 */
	default public boolean canRead() throws IOException  {
		switch (accessClass()) {
		case 0: return canOwnerRead();
		case 1: return canGroupRead();
		default: return canOtherRead();
		}
	}

	default public boolean canWrite() throws IOException {
		switch (accessClass()) {
		case 0: return canOwnerWrite();
		case 1: return canGroupWrite();
		default: return canOtherWrite();
		}
	}

	default public boolean canExecute() throws IOException {
		switch (accessClass()) {
		case 0: return canOwnerExecute();
		case 1: return canGroupExecute();
		default: return canOtherExecute();
		}
	}

	/** 0 = the current user owns the file, 1 = is in its group, 2 = other. */
	private int accessClass() throws IOException {
		FileSourceUser me = getFileSourceFactory().whoAmI();
		String myName = me == null ? null : me.getName();
		UserPrincipal owner = getOwner();
		if( myName != null && owner != null && myName.equalsIgnoreCase(owner.getName()) ) {
			return 0;
		}
		GroupPrincipal group = getGroup();
		if( me != null && group != null && group.getName() != null && me.hasGroup(group.getName()) ) {
			return 1;
		}
		return 2;
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
		int want = (int) Math.max(0, Math.min((long) size, length()));
		byte [] ret = new byte[want];
		int got = 0;
		if( want > 0 ) {
			try (InputStream in = getInputStream()) {
				got = readUpTo(in, ret);
			}
		}
		// If the file shrank while we were reading, don't return trailing zeros.
		return got == ret.length ? ret : java.util.Arrays.copyOf(ret, got);
	}

	/**
	 * Get the last 'size' byte of a file.
	 * The purpose is to provide a way to get the last few bytes 
	 * 	without transferring any other data across the network  
	 * 
	 * @param size
	 * @return the last min(size, length()) bytes of the file
	 * @throws IOException 
	 */
	default byte[] tail(int size) throws IOException {
		long len = length();
		int want = (int) Math.max(0, Math.min((long) size, len));
		byte [] ret = new byte[want];
		int got = 0;
		if( want > 0 ) {
			try (InputStream in = getInputStream()) {
				skipFully(in, len - want);
				got = readUpTo(in, ret);
			}
		}
		return got == ret.length ? ret : java.util.Arrays.copyOf(ret, got);
	}

	/**
	 * Read until 'buf' is full or the stream ends.
	 * InputStream.read may return fewer bytes than requested, so loop.
	 * @return the number of bytes read
	 */
	private static int readUpTo(InputStream in, byte[] buf) throws IOException {
		int got = 0;
		while( got < buf.length ) {
			int cnt = in.read(buf, got, buf.length - got);
			if( cnt < 0 ) {
				break;
			}
			got += cnt;
		}
		return got;
	}

	/**
	 * Skip exactly n bytes (or to EOF). InputStream.skip may skip fewer
	 * bytes than requested, or none, so fall back to reading.
	 */
	private static void skipFully(InputStream in, long n) throws IOException {
		byte [] discard = null;
		while( n > 0 ) {
			long skipped = in.skip(n);
			if( skipped <= 0 ) {
				if( discard == null ) {
					discard = new byte[(int) Math.min(8192, n)];
				}
				int cnt = in.read(discard, 0, (int) Math.min(discard.length, n));
				if( cnt < 0 ) {
					return;
				}
				skipped = cnt;
			}
			n -= skipped;
		}
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
