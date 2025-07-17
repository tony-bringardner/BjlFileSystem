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
 * ~version~V000.01.22-V000.01.12-V000.01.11-V000.01.09-V000.01.07-V000.01.06-V000.01.05-V000.01.04-V000.01.03-V000.01.00-V000.00.01-V000.00.00-
 */
package us.bringardner.io.filesource.java.file;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.spi.FileSystemProvider;
import java.security.ProviderException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
//import java.nio.file.spi.FileSystemProvider;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;




public class FileSourceFileSystemProvider extends FileSystemProvider {

	Map<String,FileSystem> fileSystems = new TreeMap<String, FileSystem>();

	private int streamBufferSize = 10240;

	public FileSourceFileSystemProvider() {

	}

	static FileSourceFileSystemProvider singleton = new FileSourceFileSystemProvider();




	private class FileSourcePosixFileAttributes implements PosixFileAttributes,BasicFileAttributes {

		private FileSource file;

		FileSourcePosixFileAttributes(FileSource file) {
			this.file = file;
		}

		@Override
		public FileTime lastModifiedTime() {
			try {
				return FileTime.fromMillis(file.lastModified());
			} catch (IOException e) {
			}
			return null;
		}

		@Override
		public FileTime lastAccessTime() {
			try {
				return FileTime.fromMillis(file.lastAccessTime());
			} catch (IOException e) {
			}
			return null;

		}

		@Override
		public FileTime creationTime() {
			try {
				return FileTime.fromMillis(file.getCreateDate());
			} catch (IOException e) {
			}
			return null;

		}

		@Override
		public boolean isRegularFile() {
			try {
				return file.isFile() && file.getLinkedTo()==null;
			} catch (IOException e) {
			}
			return false;
		}

		@Override
		public boolean isDirectory() {
			try {
				return file.isDirectory();
			} catch (IOException e) {

			}
			return false;
		}

		@Override
		public boolean isSymbolicLink() {
			boolean ret = false;
			try {
				ret = file.getLinkedTo()  != null;
			} catch (IOException e) {
			}
			return ret;
		}

		@Override
		public boolean isOther() {
			return false;
		}

		@Override
		public long size() {
			long ret = 0;
			try {
				ret = file.length();
			} catch (IOException e) {
			}
			return ret;			
		}

		@Override
		public Object fileKey() {
			Object ret = null;
			try {
				ret = file.toURL();
			} catch (MalformedURLException e) {
			}
			return  ret;
		}

		@Override
		public UserPrincipal owner() {
			UserPrincipal ret = null;
			try {
				ret = file.getOwner();
			} catch (IOException e) {
			}
			return ret;
		}

		@Override
		public GroupPrincipal group() {
			GroupPrincipal ret = null;
			try {
				ret = file.getGroup();
			} catch (IOException e) {
			}
			return ret;
		}

		@Override
		public Set<PosixFilePermission> permissions() {
			Set<PosixFilePermission> ret = EnumSet.noneOf(PosixFilePermission.class);

			try {
				if( file.canOwnerRead()) {
					ret.add(PosixFilePermission.OWNER_READ);
				}
				if( file.canOwnerWrite()) {
					ret.add(PosixFilePermission.OWNER_WRITE);
				}
				if( file.canOwnerExecute()) {
					ret.add(PosixFilePermission.OWNER_EXECUTE);
				}
				if( file.canGroupRead()) {
					ret.add(PosixFilePermission.GROUP_READ);
				}
				if( file.canGroupWrite()) {
					ret.add(PosixFilePermission.GROUP_WRITE);
				}
				if( file.canGroupExecute()) {
					ret.add(PosixFilePermission.GROUP_EXECUTE);
				}
				if( file.canOtherRead()) {
					ret.add(PosixFilePermission.OTHERS_READ);
				}
				if( file.canOtherWrite()) {
					ret.add(PosixFilePermission.OTHERS_WRITE);
				}
				if( file.canOtherExecute()) {
					ret.add(PosixFilePermission.OTHERS_EXECUTE);
				}

			} catch (IOException e) {
			}
			return ret;
		}

	}

	//PosixFileAttributeView extends BasicFileAttributeView, FileOwnerAttributeView
	private class FileSourcePosixFileAttributeView implements PosixFileAttributeView,BasicFileAttributeView {
		FileSource file ;

		FileSourcePosixFileAttributeView(FileSource file) {
			this.file = file;
		}

		@Override
		public void setTimes(FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime) throws IOException {
			if( lastModifiedTime != null ) {
				file.setLastModifiedTime(lastModifiedTime.toMillis());
			}
			if( lastAccessTime != null ) {
				file.setLastAccessTime(lastAccessTime.toMillis());
			}
			if( createTime != null ) {
				file.setCreateTime(createTime.toMillis());
			}

		}

		@Override
		public UserPrincipal getOwner() throws IOException {
			return file.getOwner();
		}

		@Override
		public void setOwner(UserPrincipal owner) throws IOException {
			file.setOwner(owner);
		}

		@Override
		public String name() {

			return "posix";
		}

		@Override
		public PosixFileAttributes readAttributes() throws IOException {

			return new FileSourcePosixFileAttributes(file);
		}

		@Override
		public void setPermissions(Set<PosixFilePermission> perms) throws IOException {
			for(PosixFilePermission p : PosixFilePermission.values()) {
				if( perms.contains(p)) {
					setPermission(file,p, true);
				} else {
					setPermission(file,p, false);
				}
			}

		}



		@Override
		public void setGroup(GroupPrincipal group) throws IOException {
			file.setGroup(group);
		}

	}


	@Override
	public String getScheme() {
		return FileSourceFactory.FILE_SOURCE_PROTOCOL;
	}



	@Override
	public FileSourceFileSystem newFileSystem(URI uri, Map<String, ?> env) throws IOException {
		throw new UnsupportedOperationException("newFileSystem is not supported");	
	}

	@Override
	public FileSourceFileSystem getFileSystem(URI uri) {
		throw new UnsupportedOperationException("getFileSystem Not supported");
	}

	@Override
	public Path getPath(URI uri) {
		try {
			return new FileSourcePath(uri);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}		
	}

	@Override
	public OutputStream newOutputStream(Path path, OpenOption... options) throws IOException {
		validate(path);
		boolean append = false;
		if( options != null ) {
			for(OpenOption op : options) {
				if (op instanceof StandardOpenOption) {
					StandardOpenOption op1 = (StandardOpenOption) op;
					switch (op1) {
					case WRITE: break;  
					case APPEND:append = true; break;
					case CREATE_NEW: break;
					case CREATE:

					case DELETE_ON_CLOSE:
					case DSYNC:					
					case SPARSE:
					case SYNC:
					case TRUNCATE_EXISTING:
					case READ:						
					default:
						throw new IOException("Unsupported open option="+op);						
					}
				}
			}
		}

		return ((FileSourcePath)path).getFileSource().getOutputStream(append);
	}

	@Override
	public InputStream newInputStream(Path path, OpenOption... options) throws IOException {
		validate(path);

		if( options != null ) {
			for(OpenOption op : options) {
				if (op instanceof StandardOpenOption) {
					StandardOpenOption op1 = (StandardOpenOption) op;
					switch (op1) {
					case READ: break;  //  this is the only one supported
					case APPEND:
					case CREATE:
					case CREATE_NEW:
					case DELETE_ON_CLOSE:
					case DSYNC:					
					case SPARSE:
					case SYNC:
					case TRUNCATE_EXISTING:
					case WRITE:						
					default:
						throw new IOException("Unsupported open option="+op);						
					}
				}
			}
		}

		return ((FileSourcePath)path).getFileSource().getInputStream();
	}


	@Override
	public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
		throw new UnsupportedOperationException("SeekableByteChannel Not supported");
	}

	@Override
	public AsynchronousFileChannel newAsynchronousFileChannel(Path path, Set<? extends OpenOption> options,
			ExecutorService executor, FileAttribute<?>... attrs) throws IOException {
		throw new UnsupportedOperationException("AsynchronousFileChannel Not supported");
	}

	@Override
	public FileChannel newFileChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
			throws IOException {
		throw new UnsupportedOperationException("FileChannel Not supported");
	}

	@Override
	public FileSystem newFileSystem(Path path, Map<String, ?> env) throws IOException {
		throw new UnsupportedOperationException("newFileSystem Not supported");
	}

	@Override
	public DirectoryStream<Path> newDirectoryStream(Path dir, Filter<? super Path> filter) throws IOException {
		validate(dir);
		FileSource file = getLinkedTo(dir, null);
		if(! file.isDirectory()) {
			throw new IOException(""+file+" is not a diirectory");
		}

		FileSource[] kids = file.listFiles();


		return  new DirectoryStream<Path>() {
			int pos = 0;

			@Override
			public void close() throws IOException {
				pos = kids.length;				
			}

			@Override
			public Iterator<Path> iterator() {

				return new Iterator<Path>() {

					@Override
					public Path next() {
						return new FileSourcePath(kids[pos++]);
					}

					@Override
					public boolean hasNext() {
						boolean ret = false;
						while(!ret && pos< kids.length ) {
							FileSourcePath path = new FileSourcePath(kids[pos]);
							try {
								if( filter.accept(path)) {
									ret = true;
								} else {
									pos++;
								}
							} catch (IOException e) {
							}
						}
						return ret;
					}
				};
			}

		};
	}


	private void setPermission(FileSource file,PosixFilePermission p, boolean b) throws IOException {
		if( p == null ) {
			throw new NullPointerException("Permission may NOT be null");
		}


		switch (p) {
		case OWNER_READ: 
			file.setOwnerReadable(b);
			break;
		case OWNER_WRITE: 
			file.setOwnerWritable(b);
			break;
		case OWNER_EXECUTE:
			file.setOwnerExecutable(b);
			break;

		case GROUP_READ: 
			file.setGroupReadable(b);
			break;
		case GROUP_WRITE: 
			file.setGroupWritable(b);
			break;
		case GROUP_EXECUTE: 
			file.setGroupExecutable(b);
			break;

		case OTHERS_READ: 
			file.setOtherReadable(b);
			break;
		case OTHERS_WRITE: 
			file.setOtherWritable(b);
			break;
		case OTHERS_EXECUTE: 
			file.setOtherExecutable(b);
			break;

		}


	}


	@Override
	public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {		
		validate(dir);
		FileSource file = ((FileSourcePath)dir).getFileSource();

		if(!file.mkdirs()) {
			throw new ProviderException("Could not create directory for "+dir);
		}

		if( attrs != null && attrs.length > 0) {
			for(FileAttribute<?> attr : attrs) {
				String name = attr.name();
				if (!name.equals("posix:permissions") && !name.equals("unix:permissions")) {
					throw new UnsupportedOperationException("'" + attr.name() +
							"' not supported as initial attribute");
				}
				Object val = attr.value();

				if (val instanceof Set) {
					@SuppressWarnings("unchecked")
					Set<PosixFilePermission> perms = (Set<PosixFilePermission>)val;
					for(PosixFilePermission p : PosixFilePermission.values()) {
						if( perms.contains(p)) {
							setPermission(file,p, true);
						} else {
							setPermission(file,p, false);
						}
					}

				}
			}
		}
	}

	@Override
	public void delete(Path path) throws IOException {
		validate(path);
		FileSource file = ((FileSourcePath)path).getFileSource();
		if(!file.exists()) {
			throw new NoSuchFileException(file.getAbsolutePath());
		}

		if( file.isDirectory()) {
			FileSource kids [] = file.listFiles();
			if( kids != null && kids.length>0) {
				// I would rather just delete the directory but this is what the BOSS says :-(
				throw new DirectoryNotEmptyException(""+file+" is a directory with childeren" );
			}
		}

		if(!file.delete()) {
			throw new ProviderException("Could not delete "+path);
		}

	}

	/**
	 * This method copies a file to the target file with the options parameter specifying how the copy is performed. 
	 * By default, the copy fails if the target file already exists or is a symbolic link, except if the source and target are the same file, 
	 * in which case the method completes without copying the file. File attributes are not required to be copied to the target file. 
	 * If symbolic links are supported, and the file is a symbolic link, then the final target of the link is copied. 
	 * If the file is a directory then it creates an empty directory in the target location (entries in the directory are not copied). 
	 * 
	 * This method can be used with the walkFileTree method to copy a directory and all entries in the directory, or an entire file-tree where required.
	 */
	@Override
	public void copy(Path source, Path target, CopyOption... options) throws IOException {
		validate(source,target);

		boolean copyAttributes = false;
		for(CopyOption co : options) {
			if (co instanceof StandardCopyOption) {
				StandardCopyOption sco = (StandardCopyOption) co;
				if( sco == StandardCopyOption.ATOMIC_MOVE) {
					throw new UnsupportedOperationException("Atomic move is not supported");
				} else if( co == StandardCopyOption.COPY_ATTRIBUTES) {
					copyAttributes = true;
				}
			}
		}

		/*
		 *  By default, the copy fails if the target file already exists or is a symbolic link, except if the source and target are the same file, 
		 *   in which case the method completes without copying the file.
		 */
		if( isSameFile(source, target)) {
			return;
		}

		FileSource tf = ((FileSourcePath)target).getFileSource();
		if( tf.exists() || tf.getLinkedTo() != null) {
			throw new IOException("Can't copy because target exists or is a symbolic link. target="+tf);
		}

		FileSource sf = ((FileSourcePath)source).getFileSource();
		if( sf.isDirectory()) {
			if(!tf.mkdirs()) {
				throw new IOException("Can't create directories for target="+tf);
			}

		} else {
			try(InputStream in = sf.getInputStream()) {
				try(OutputStream out = tf.getOutputStream()) {
					byte [] data = new byte[streamBufferSize];
					int got = 0;
					while( (got=in.read(data)) >= 0) {
						if( got > 0 ) {
							out.write(data,0,got);
						}
					}
				}							
			}
		}

		if( copyAttributes ) {
			tf.setLastModifiedTime(tf.lastModified());
			tf.setLastAccessTime(sf.lastAccessTime());
			tf.setCreateTime(sf.creationTime());
		}
	}


	private void validate(Path ... paths ) throws IOException {
		for(Path p : paths){
			if (!(p instanceof FileSourcePath)) {
				throw new ProviderMismatchException(p.toString()+" is not a filesource path");				
			}
		}

	}



	@Override
	public void move(Path source, Path target, CopyOption... options) throws IOException {
		validate(source,target);
		FileSource s = getLinkedTo(source, (LinkOption[]) options);

		if(!(s.renameTo(((FileSourcePath)target).getFileSource()))) {
			throw new IOException("Could not rename "+source+" to "+ target);
		}
	}

	@Override
	public boolean isSameFile(Path path, Path path2) throws IOException {
		validate(path,path2);
		FileSource f1 = ((FileSourcePath)path).getFileSource();
		FileSource f2 = ((FileSourcePath)path2).getFileSource();
		boolean ret = (f1.getAbsolutePath().equals(f2.getAbsolutePath())) && (f1.getFileSourceFactory().getTypeId().equals(f2.getFileSourceFactory().getTypeId()));
		if( ret ) {
			ret = f1.getFileSourceFactory().getConnectProperties().toString().equals(f2.getFileSourceFactory().getConnectProperties().toString());
		}

		return ret;
	}

	@Override
	public boolean isHidden(Path path) throws IOException {
		validate(path);
		return ((FileSourcePath)path).getFileSource().isHidden();
	}

	@Override
	public FileStore getFileStore(Path path) throws IOException {
		validate(path);
		return new FileSourceFileStore(((FileSourcePath) path).getFileSource());
	}

	@Override
	public void checkAccess(Path path, AccessMode... modes) throws IOException {
		if (path instanceof FileSourcePath) {
			FileSourcePath fsp = (FileSourcePath) path;
			if( !fsp.getFileSource().exists()) {
				throw new IOException(""+fsp.getFileSource()+" does not exist");
			}

			for(AccessMode m : modes) {
				switch (m) {
				case EXECUTE: if(!fsp.getFileSource().canOwnerExecute()) {
					throw new IOException("no execute privileges");
				}
				break;
				case READ: if( !fsp.getFileSource().canOwnerRead()) {
					throw new IOException("no read privileges");
				}
				break;
				case WRITE: if( !fsp.getFileSource().canOwnerWrite()) {
					throw new IOException("no write privileges");
				}
				}
			}

		}

	}

	@SuppressWarnings("unchecked")
	@Override
	public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
		V ret = null;

		try {

			validate(path);
			FileSource file = getLinkedTo(path,options);
			ret = (V) new FileSourcePosixFileAttributeView(file);

		} catch (IOException e) {
		}

		return ret;
	}

	private FileSource getLinkedTo(Path path,LinkOption[] options) throws IOException {
		FileSource ret = ((FileSourcePath)path).getFileSource();
		boolean follow = true;

		if( options != null ) {
			for(LinkOption lo : options) {
				if(lo == LinkOption.NOFOLLOW_LINKS) {
					follow = false;
				}
			}
		}

		if( follow) {
			FileSource link = ret.getLinkedTo();
			if( link != null ) {
				ret = link;
			}
		}

		return ret;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <V extends BasicFileAttributes> V readAttributes(Path path, Class<V> type, LinkOption... options) throws IOException {
		FileSourcePosixFileAttributes ret = new FileSourcePosixFileAttributes(((FileSourcePath)path).getFileSource());

		return (V) ret;
	}

	/**
	 * 
			"*"	Read all basic-file-attributes.
			"size,lastModifiedTime,lastAccessTime"	Reads the file size, last modified time, and last access time attributes.
			"posix:*"	Read all POSIX-file-attributes.
			"posix:permissions,owner,size"	Reads the POSX file permissions, owner, and file size.
	 */
	@Override
	public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
		Map<String, Object>  ret = new HashMap<String, Object>();

		Class<?> cls = BasicFileAttributes.class;
		Object attr =  readAttributes(path, BasicFileAttributes.class);
		String val = null;
		String [] parts = attributes.split("[:]");
		if( parts.length >= 2) {
			val= parts[1];
			if(parts[0].toLowerCase().equals("posix")) {

				attr =  readAttributes(path, PosixFileAttributes.class);
				cls = attr.getClass();
			}
		} else {
			val = attributes;
		}



		parts = val.split("[,]");
		for(String name : parts) {
			try {
				if( name.contains("*")) {
					for(Method m : cls.getDeclaredMethods()) {
						try {
							m.setAccessible(true);
							Object v = m.invoke(attr);
							if( v != null ) {
								ret.put(m.getName(), v);
							}
						} catch (Exception e) {
						}
					}
				} else {	
					Method m = cls.getMethod(name);
					if( m != null ) {
						m.setAccessible(true);
						Object v = m.invoke(attr);
						ret.put(name, v);
					}					
				}
			} catch (Throwable e) {
				// bad name, skip it
			}
		}

		return ret;
	}

	@Override
	public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
		// Not implemented
		throw new RuntimeException("setAttribute Not implemented");
	}

}
