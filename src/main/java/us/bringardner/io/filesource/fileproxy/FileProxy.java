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
 * ~version~V000.01.05-V000.01.04-V000.01.00-V000.00.01-V000.00.00-
 */
/*
 * Created on Dec 14, 2004
 *
 */
package us.bringardner.io.filesource.fileproxy;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileOwnerAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import javax.swing.ProgressMonitor;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.FileSourceFilter;
import us.bringardner.io.filesource.FileSourceRandomAccessStream;
import us.bringardner.io.filesource.IRandomAccessStream;
import us.bringardner.io.filesource.ISeekableInputStream;


/**
 * @author Tony Bringardner
 * Acts as a proxy to a File Object to expose the FileSource interface
 */
public class FileProxy implements FileSource {

	public interface PermissionManager {
		boolean canRead() throws IOException;
		boolean canWrite() throws IOException;
		boolean canExecute() throws IOException;
		boolean canOwnerExecute() throws IOException;
		boolean canOwnerRead() throws IOException;
		boolean canOwnerWrite() throws IOException;
		boolean canGroupRead() throws IOException;
		boolean canGroupWrite() throws IOException;
		boolean canGroupExecute() throws IOException;
		boolean canOtherRead() throws IOException;
		boolean canOtherWrite() throws IOException;
		boolean canOtherExecute() throws IOException;

		boolean setExecutable(boolean b, boolean ownerOnly) throws IOException;
		boolean setReadable(boolean b, boolean  ownerOnly) throws IOException;
		boolean setWritable(boolean b, boolean  ownerOnly) throws IOException;
		boolean setExecutable(boolean b) throws IOException;
		boolean setReadable(boolean b) throws IOException;
		boolean setWritable(boolean b) throws IOException;
		boolean setGroupExecutable(boolean b) throws IOException;
		boolean setGroupReadable(boolean b) throws IOException;
		boolean setGroupWritable(boolean b) throws IOException;
		boolean setOwnerReadable(boolean b) throws IOException;
		boolean setOwnerWritable(boolean b) throws IOException;
		boolean setOwnerExecutable(boolean b) throws IOException;
		boolean setOtherReadable(boolean b) throws IOException;
		boolean setOtherWritable(boolean b) throws IOException;
		boolean setOtherExecutable(boolean b) throws IOException;

		public boolean setLastAccessTime(long time) throws IOException ;

		public boolean setCreateTime(long time) throws IOException;
		public boolean setGroup(GroupPrincipal group) throws IOException; 
	}

	private class PosixPermissionManager implements PermissionManager {
		/* (non-Javadoc)
		 * @see us.bringardner.io.FileSource#canRead()
		 */
		public boolean canRead() throws IOException {
			return target.canRead();
		}

		/* (non-Javadoc)
		 * @see us.bringardner.io.FileSource#canWrite()
		 */
		public boolean canWrite() {
			return target.canWrite();
		}
		
		@Override
		public boolean canExecute() throws IOException {
			return target.canExecute();
		}

		@Override
		public boolean canOwnerExecute() throws IOException {
			boolean ret = false;
			Set<PosixFilePermission> p = getPosixPermissions();
			if( p != null ) {
				ret =  p.contains(PosixFilePermission.OWNER_EXECUTE);
			}
			return ret;
		}

		@Override
		public boolean canOwnerRead() throws IOException {		 
			return canRead();
		}
		@Override
		public boolean canOwnerWrite() throws IOException {		
			return canWrite();
		}
		@Override
		public boolean canGroupRead() throws IOException {
			boolean ret = false;
			Set<PosixFilePermission> p = getPosixPermissions();
			if( p != null ) {
				ret =  p.contains(PosixFilePermission.GROUP_READ);
			}
			return ret;
		}
		@Override
		public boolean canGroupWrite() throws IOException {
			boolean ret = false;
			Set<PosixFilePermission> p = getPosixPermissions();
			if( p != null ) {
				ret =  p.contains(PosixFilePermission.GROUP_WRITE);
			}
			return ret;
		}

		@Override
		public boolean canGroupExecute() throws IOException {
			boolean ret = false;
			Set<PosixFilePermission> p = getPosixPermissions();
			if( p != null ) {
				ret =  p.contains(PosixFilePermission.GROUP_EXECUTE);
			}
			return ret;
		}

		@Override
		public boolean canOtherRead() throws IOException {
			boolean ret = false;
			Set<PosixFilePermission> p = getPosixPermissions();
			if( p != null ) {
				ret =  p.contains(PosixFilePermission.OTHERS_READ);
			}
			return ret;
		}

		@Override
		public boolean canOtherWrite() throws IOException {
			boolean ret = false;
			Set<PosixFilePermission> p = getPosixPermissions();
			if( p != null ) {
				ret =  p.contains(PosixFilePermission.OTHERS_WRITE);
			}
			return ret;
		}
		@Override
		public boolean canOtherExecute() throws IOException {
			boolean ret = false;
			Set<PosixFilePermission> p = getPosixPermissions();
			if( p != null ) {
				ret =  p.contains(PosixFilePermission.OTHERS_EXECUTE);
			}
			return ret;
		}

		@Override
		public boolean setExecutable(boolean executable, boolean ownerOnly) {
			return target.setExecutable(executable, ownerOnly);		
		}



		@Override
		public boolean setReadable(boolean readable, boolean ownerOnly) {
			return target.setReadable(readable, ownerOnly);

		}



		@Override
		public boolean setWritable(boolean writetable, boolean ownerOnly) {
			return target.setWritable(writetable, ownerOnly);

		}



		@Override
		public boolean setExecutable(boolean executable)  throws IOException {
			return target.setExecutable(executable);

		}



		@Override
		public boolean setReadable(boolean readable) throws IOException {
			boolean ret = target.setReadable(readable);
			return ret;

		}



		@Override
		public boolean setWritable(boolean writetable)  throws IOException {
			return target.setWritable(writetable);		
		}

		@Override
		public boolean setGroupExecutable(boolean executable) throws IOException {
			return setPosixPermision(executable, PosixFilePermission.GROUP_EXECUTE);		
		}

		@Override
		public boolean setGroupReadable(boolean readable) throws IOException {
			return setPosixPermision(readable, PosixFilePermission.GROUP_READ);
		}

		@Override
		public boolean setGroupWritable(boolean writeable) throws IOException {
			return setPosixPermision(writeable, PosixFilePermission.GROUP_WRITE);
		}

		@Override
		public boolean setOwnerReadable(boolean readable) throws IOException {
			return setPosixPermision(readable, PosixFilePermission.OWNER_READ);
		}

		@Override
		public boolean setOwnerWritable(boolean writeable) throws IOException {
			return setPosixPermision(writeable, PosixFilePermission.OWNER_WRITE);
		}

		@Override
		public boolean setOwnerExecutable(boolean executable) throws IOException {		
			return setPosixPermision(executable, PosixFilePermission.OWNER_EXECUTE);
		}

		@Override
		public boolean setOtherReadable(boolean readable) throws IOException {
			return setPosixPermision(readable,PosixFilePermission.OTHERS_READ);
		}

		@Override
		public boolean setOtherWritable(boolean writeable) throws IOException {
			return setPosixPermision(writeable,PosixFilePermission.OTHERS_WRITE);
		}

		@Override
		public boolean setOtherExecutable(boolean executable) throws IOException {
			return setPosixPermision(executable,PosixFilePermission.OTHERS_EXECUTE);
		}
		@Override
		public boolean setLastAccessTime(long time) throws IOException {

			try {
				PosixFileAttributeView v = Files.getFileAttributeView(target.toPath(), PosixFileAttributeView.class);
				v.setTimes(null,FileTime.from(Instant.ofEpochMilli(time)), null);

			} catch(Throwable e) {
				return false;
			}

			return true;
		}

		@Override
		public boolean setCreateTime(long time) throws IOException {
			try {
				PosixFileAttributeView v = Files.getFileAttributeView(target.toPath(), PosixFileAttributeView.class);
				v.setTimes(null,null,FileTime.from(Instant.ofEpochMilli(time)));			
			} catch(Throwable e) {
				return false;
			}

			return true;
		}

		@Override
		public boolean setGroup(GroupPrincipal group) throws IOException {
			try {
				PosixFileAttributeView v = Files.getFileAttributeView(target.toPath(), PosixFileAttributeView.class);
				v.setGroup(group);
			} catch (Exception e) {
				return false;
			}				
			return true;
		}

		

	}

	private static final long serialVersionUID = 1L;
	File target; 
	private String name;
	private FileSourceFactory theCreator ;
	private GroupPrincipal group;
	private UserPrincipal owner;
	private PermissionManager permissions;

	public FileProxy(File target,FileSourceFactory creator) {
		permissions= FileSourceFactory.isWindows()?new WindowsPermissionManager(target): new PosixPermissionManager();
		this.target = target;
		this.theCreator = creator;
	}

	private synchronized	Set<PosixFilePermission> getPosixPermissions() throws IOException {
		Set<PosixFilePermission> ret = new HashSet<PosixFilePermission>() ;
		PosixFileAttributeView view2 = Files.getFileAttributeView(target.toPath(), PosixFileAttributeView.class,LinkOption.NOFOLLOW_LINKS);
		if (view2 != null) {
			PosixFileAttributes at = view2.readAttributes();
			if( at != null ) {
				ret = at.permissions();
			}
		}

		return ret;
	}


	private boolean setPosixPermision(boolean b, PosixFilePermission p) throws IOException {
		Set<PosixFilePermission> perms = getPosixPermissions();
		
		if(b) {
			if( !perms.contains(p)) {
				perms.add(p);
				Files.setPosixFilePermissions(target.toPath(), perms);
			}
		} else {
			if( perms.contains(p)) {
				perms.remove(p);
				Files.setPosixFilePermissions(target.toPath(), perms);
			}
		}	
		// no errors so I assume it worked
		return true;
	}
	
	

	/* (non-Javadoc)
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	public int compareTo(Object o) {
		return target.getAbsolutePath().compareTo(o.toString());
	}


	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSource#canRead()
	 */
	public boolean canRead() throws IOException {
		return permissions.canRead();
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSource#canWrite()
	 */
	public boolean canWrite() throws IOException {
		return permissions.canWrite();
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSource#canWrite()
	 */
	public boolean canExecute() throws IOException {
		return permissions.canExecute();
	}

	@Override
	public boolean canOwnerExecute() throws IOException {
		return permissions.canOwnerExecute();
	}

	@Override
	public boolean canOwnerRead() throws IOException {		 
		return permissions.canOwnerRead();
	}
	@Override
	public boolean canOwnerWrite() throws IOException {		
		return permissions.canOwnerWrite();
	}
	@Override
	public boolean canGroupRead() throws IOException {
		return permissions.canGroupRead();
	}
	@Override
	public boolean canGroupWrite() throws IOException {
		return permissions.canGroupWrite();
	}

	@Override
	public boolean canGroupExecute() throws IOException {
		return permissions.canGroupExecute();
	}

	@Override
	public boolean canOtherRead() throws IOException {
		return permissions.canOtherRead();
	}

	@Override
	public boolean canOtherWrite() throws IOException {
		return permissions.canOtherWrite();
	}

	@Override
	public boolean canOtherExecute() throws IOException {
		return permissions.canOtherExecute();
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSource#createNewFile()
	 */
	public boolean createNewFile() throws IOException {
		return target.createNewFile();
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSource#delete()
	 */
	public boolean delete() {

		return target.delete();
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSource#exists()
	 */
	public boolean exists() {
		return target.exists();
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSource#getCanonicalPath()
	 */
	public String getCanonicalPath() throws IOException {

		return target.getCanonicalPath().replace('\\','/');
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSource#getName()
	 */
	public String getName() {
		if(name == null ) {
			synchronized(this) {
				if(name == null ) {
					name = target.getName();
					if( name.isEmpty()) {
						name = target.getPath();
					}
				}
			}
		}
		return name;
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSource#getParent()
	 */
	public String getParent() {

		return target.getParent();
	}

	public boolean isChildOfMine(FileSource child) {
		boolean ret = (child instanceof FileProxy);
		if( ret ){
			String p1 = child.getAbsolutePath();
			String p2 = getAbsolutePath();
			ret = p1.startsWith(p2);

		}

		return ret;
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSource#getParentFile()
	 */
	public FileSource getParentFile() {
		FileSource ret = null;
		File f = target.getParentFile();
		if( f != null ) {
			ret = new FileProxy(f,theCreator);
		}

		return ret;
	}



	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSource#isDirectory()
	 */
	public boolean isDirectory() {

		return target.isDirectory();
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSource#isFile()
	 */
	public boolean isFile() {

		return target.isFile();
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSource#length()
	 */
	public long length() {

		return target.length();
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSource#lastModified()
	 */
	public long lastModified() {
		long ret = target.lastModified();
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
		File [] ret1 = target.listFiles();
		ArrayList<FileProxy> list = new ArrayList<FileProxy>();
		FileSource [] ret = null;

		if( ret1 != null ) {
			int sz = 0;
			for(int idx=0; idx < ret1.length; idx++ ) {
				FileProxy tmp  = new FileProxy(ret1[idx],theCreator);
				if(filter==null || filter.accept(tmp)){
					list.add(tmp);
					sz ++;
				}
			}

			ret = new FileSource[sz];

			for(int idx=0; idx < ret.length; idx++ ) {
				ret[idx] = (FileProxy)list.get(idx);
			}
		}

		return ret;
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSource#mkdir()
	 */
	public boolean mkdir() {

		return target.mkdir();
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSource#mkdirs()
	 */
	public boolean mkdirs() {

		return target.mkdirs();
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSource#renameTo(us.bringardner.io.FileSource)
	 */
	public boolean renameTo(FileSource dest) {

		boolean ret = false;

		try {
			ret = target.renameTo(new File (dest.getCanonicalPath()));
			name = null;
		} catch (IOException e) {
			e.printStackTrace();
		} 

		return ret; 
	}


	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSource#setLastModified(long)
	 */
	@Override
	public boolean setLastModifiedTime(long time) {
		return target.setLastModified(time);		
	}


	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSource#setReadOnly()
	 */
	public boolean setReadOnly() {
		return target.setReadOnly();
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSource#getInputStream()
	 */
	public InputStream getInputStream() throws IOException {
		return new FileInputStream(target);
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSource#getOutputStream()
	 */
	public OutputStream getOutputStream() throws FileNotFoundException {
		return new FileOutputStream(target);
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSource#getOutputStream(boolean)
	 */
	public OutputStream getOutputStream(boolean append) throws FileNotFoundException {

		return new FileOutputStream(target,append);
	}



	public String toString() {
		return target.toString().replace('\\','/');
	}

	/* Return a Factory taht can be used to create a factory of this type.
	 * @see us.bringardner.io.FileSource#getFileSourceFactory()
	 */
	public FileSourceFactory getFileSourceFactory() {
		if( theCreator == null ){
			theCreator = FileSourceFactory.fileProxyFactory;
		}
		return theCreator;
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.filesource.FileSource#toURL()
	 */
	@SuppressWarnings("deprecation")
	public URL toURL() throws MalformedURLException {
		URL ret = null;

		String path = null;

		try {
			path = getCanonicalPath();
		} catch (IOException e) {
			throw new MalformedURLException("Can't get path");
		}
		ret = new URL(FileSourceFactory.FILE_SOURCE_PROTOCOL+":"+path+"?"+FileSourceFactory.QUERY_STRING_SOURCE_TYPE+"="+FileProxyFactory.FACTORY_ID);

		return ret;
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.filesource.FileSource#getChild(java.lang.String)
	 */
	public FileSource getChild(String path) throws IOException {
		return new FileProxy(new File(target,path),theCreator);
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.filesource.FileSource#getCreateDate()
	 */
	public long getCreateDate() {
		return target.lastModified();
	}

	public static String getContentType(String name) {
		String ret = null;
		if( name !=null ) {
			int idx=name.lastIndexOf('.');
			if( idx > 0 ){
				String ext = name.substring(idx+1);
				ret = FileProxyFactory.getType(ext);
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

		return target.list();
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

		return target.getAbsolutePath();
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.filesource.FileSource#getPath()
	 */
	public String getPath() {

		return target.getPath();
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
			synchronized (this) {
				owner = Files.getOwner(target.toPath());	
			}
		}

		return owner;
	}

	@Override
	public GroupPrincipal getGroup() throws IOException {
		if( group == null ) {
			synchronized (this) {
				if (permissions instanceof WindowsPermissionManager) {
					WindowsPermissionManager wpm = (WindowsPermissionManager) permissions;
					group = (GroupPrincipal) wpm.getGroupPrincipal();
				} else {
					PosixFileAttributeView view2 = Files.getFileAttributeView(target.toPath(), PosixFileAttributeView.class,LinkOption.NOFOLLOW_LINKS);
					if (view2 != null) {
						PosixFileAttributes at = view2.readAttributes();
						group = at.group();
						if( group == null ) {
							group = new GroupPrincipal() {

								@Override
								public String getName() {
									return "Unknown";
								}
							};
						}
					}
				}
			}
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
		//  Only one version so just use the lastModified date 
		return lastModified();
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.filesource.FileSource#setVersionDate()
	 */
	public boolean setVersionDate(long time) {
		// Just update the modification date
		return setLastModifiedTime(time);

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
		InputStream ret = getInputStream();
		long skipped = ret.skip(startingPos);
		if( skipped != startingPos) {
			throw new IOException("Can't skipp to "+startingPos+" skipped="+skipped);
		}

		return ret;
	}

	@Override
	public boolean equals(Object obj) {
		boolean ret = false;
		if (obj instanceof FileProxy) {			
			ret = getAbsolutePath().equals(((FileProxy) obj).getAbsolutePath());			
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

	public File getTarget() {
		return target;
	}


	@Override
	public String getTitle() {
		return "Local";
	}

	@Override
	public FileSource[] listFiles(ProgressMonitor progress) {
		return listFiles();
	}

	@Override
	public FileSource getLinkedTo() {
		FileSource ret = null;
		try {

			Path path = target.toPath();
			BasicFileAttributeView view = Files.getFileAttributeView(path, BasicFileAttributeView.class,LinkOption.NOFOLLOW_LINKS);
			if (view != null) {
				BasicFileAttributes at = view.readAttributes();
				if( at.isSymbolicLink()) {
					Path newPath = Files.readSymbolicLink(path);
					if( newPath != null) {
						ret = new FileProxy(newPath.toFile(), theCreator);
					}

				}
			}

		} catch (IOException e) {
			//ignore here
		}

		return ret;
	}

	@Override
	public boolean isHidden() {
		return target.isHidden();
	}



	@Override
	public ISeekableInputStream getSeekableInputStream() throws IOException {

		return new FileProxySeekableInputStream(this);
	}



	@Override
	public boolean setExecutable(boolean executable, boolean ownerOnly) throws IOException {
		return permissions.setExecutable(executable, ownerOnly);		
	}



	@Override
	public boolean setReadable(boolean readable, boolean ownerOnly) throws IOException {
		return permissions.setReadable(readable, ownerOnly);

	}



	@Override
	public boolean setWritable(boolean writetable, boolean ownerOnly) throws IOException {
		return permissions.setWritable(writetable, ownerOnly);

	}



	@Override
	public boolean setExecutable(boolean executable)  throws IOException {
		return permissions.setExecutable(executable);

	}



	@Override
	public boolean setReadable(boolean readable) throws IOException {
		boolean ret = permissions.setReadable(readable);
		return ret;

	}



	@Override
	public boolean setWritable(boolean writetable)  throws IOException {
		return permissions.setWritable(writetable);		
	}

	@Override
	public boolean setGroupExecutable(boolean executable) throws IOException {
		return permissions.setGroupExecutable(executable);		
	}

	@Override
	public boolean setGroupReadable(boolean readable) throws IOException {
		return permissions.setGroupReadable(readable);
	}

	@Override
	public boolean setGroupWritable(boolean writeable) throws IOException {
		return permissions.setGroupWritable(writeable);
	}

	@Override
	public boolean setOwnerReadable(boolean readable) throws IOException {
		return permissions.setOwnerReadable(readable);
	}

	@Override
	public boolean setOwnerWritable(boolean writeable) throws IOException {
		return permissions.setOwnerWritable(writeable);
	}

	@Override
	public boolean setOwnerExecutable(boolean executable) throws IOException {		
		return permissions.setOwnerExecutable(executable);
	}

	@Override
	public boolean setOtherReadable(boolean readable) throws IOException {
		return permissions.setOtherReadable(readable);
	}

	@Override
	public boolean setOtherWritable(boolean writeable) throws IOException {
		return permissions.setOtherWritable(writeable);
	}

	@Override
	public boolean setOtherExecutable(boolean executable) throws IOException {
		return permissions.setOtherExecutable(executable);
	}

	@Override
	public long lastAccessTime() throws IOException {
		BasicFileAttributes attrs = Files.readAttributes(target.toPath(), BasicFileAttributes.class);
		FileTime time = attrs.lastAccessTime();
		return time.toMillis();
	}

	@Override
	public long creationTime() throws IOException {
		BasicFileAttributes attrs = Files.readAttributes(target.toPath(), BasicFileAttributes.class);
		FileTime time = attrs.creationTime();
		return time.toMillis();		
	}

	@Override
	public boolean setLastAccessTime(long time) throws IOException {		
		return permissions.setLastAccessTime(time);
	}

	@Override
	public boolean setCreateTime(long time) throws IOException {
		return permissions.setCreateTime(time);
	}

	@Override
	public boolean setGroup(GroupPrincipal group) throws IOException {
		return permissions.setGroup(group);
	}

	@Override
	public boolean setOwner(UserPrincipal owner) throws IOException {
		try {
			FileOwnerAttributeView v = Files.getFileAttributeView(target.toPath(), FileOwnerAttributeView.class);
			v.setOwner(owner);
		} catch (Exception e) {
			return false;
		}				
		return true;
	}

	@Override
	public IRandomAccessStream getRandomAccessStream(String mode) throws IOException {

		return new FileSourceRandomAccessStream(new FileProxyRandomAccessIoController(this, mode), mode);
	}

}
