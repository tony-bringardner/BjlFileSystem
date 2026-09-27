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
import java.util.NoSuchElementException;
import java.util.List;
import java.util.Collections;
import java.util.Arrays;
import java.nio.file.NotDirectoryException;
import java.nio.file.FileSystemException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.AccessDeniedException;
import java.io.UncheckedIOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.FileAlreadyExistsException;
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

	private static transient  FileSourceFileSystemProvider singleton;




	/**
	 * Basic attributes, read once when created (as BasicFileAttributes are
	 * meant to be). This used to be a live view: every accessor went back to
	 * the file and returned null/0/false on an error.
	 */
	private static class FileSourceBasicFileAttributes implements BasicFileAttributes {
		protected final FileSource file;
		private final FileTime lastModifiedTime;
		private final FileTime lastAccessTime;
		private final FileTime creationTime;
		private final boolean regularFile;
		private final boolean directory;
		private final boolean symbolicLink;
		private final long size;

		/**
		 * @param symbolicLink true to describe a link itself (NOFOLLOW_LINKS on a link)
		 * @throws NoSuchFileException if the file doesn't exist (and isn't a link)
		 */
		FileSourceBasicFileAttributes(FileSource file, boolean symbolicLink) throws IOException {
			this.file = file;
			this.symbolicLink = symbolicLink;
			if( !symbolicLink && !file.exists()) {
				throw new NoSuchFileException(file.getAbsolutePath());
			}
			lastModifiedTime = FileTime.fromMillis(file.lastModified());
			lastAccessTime = FileTime.fromMillis(file.lastAccessTime());
			creationTime = FileTime.fromMillis(file.creationTime());
			regularFile = !symbolicLink && file.isFile();
			directory = !symbolicLink && file.isDirectory();
			size = file.length();
		}

		@Override
		public FileTime lastModifiedTime() {
			return lastModifiedTime;
		}

		@Override
		public FileTime lastAccessTime() {
			return lastAccessTime;
		}

		@Override
		public FileTime creationTime() {
			return creationTime;
		}

		@Override
		public boolean isRegularFile() {
			return regularFile;
		}

		@Override
		public boolean isDirectory() {
			return directory;
		}

		@Override
		public boolean isSymbolicLink() {
			return symbolicLink;
		}

		@Override
		public boolean isOther() {
			return false;
		}

		@Override
		public long size() {
			return size;
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
	}

	/** Basic plus POSIX attributes (owner, group, permissions), also read once. */
	private static class FileSourcePosixFileAttributes extends FileSourceBasicFileAttributes implements PosixFileAttributes {
		private final UserPrincipal owner;
		private final GroupPrincipal group;
		private final Set<PosixFilePermission> permissions;

		FileSourcePosixFileAttributes(FileSource file, boolean symbolicLink) throws IOException {
			super(file, symbolicLink);
			owner = file.getOwner();
			group = file.getGroup();
			Set<PosixFilePermission> perms = EnumSet.noneOf(PosixFilePermission.class);
			if( file.canOwnerRead()) {
				perms.add(PosixFilePermission.OWNER_READ);
			}
			if( file.canOwnerWrite()) {
				perms.add(PosixFilePermission.OWNER_WRITE);
			}
			if( file.canOwnerExecute()) {
				perms.add(PosixFilePermission.OWNER_EXECUTE);
			}
			if( file.canGroupRead()) {
				perms.add(PosixFilePermission.GROUP_READ);
			}
			if( file.canGroupWrite()) {
				perms.add(PosixFilePermission.GROUP_WRITE);
			}
			if( file.canGroupExecute()) {
				perms.add(PosixFilePermission.GROUP_EXECUTE);
			}
			if( file.canOtherRead()) {
				perms.add(PosixFilePermission.OTHERS_READ);
			}
			if( file.canOtherWrite()) {
				perms.add(PosixFilePermission.OTHERS_WRITE);
			}
			if( file.canOtherExecute()) {
				perms.add(PosixFilePermission.OTHERS_EXECUTE);
			}
			permissions = Collections.unmodifiableSet(perms);
		}

		@Override
		public UserPrincipal owner() {
			return owner;
		}

		@Override
		public GroupPrincipal group() {
			return group;
		}

		@Override
		public Set<PosixFilePermission> permissions() {
			return EnumSet.copyOf(permissions.isEmpty() ? EnumSet.noneOf(PosixFilePermission.class) : permissions);
		}
	}

	//PosixFileAttributeView extends BasicFileAttributeView, FileOwnerAttributeView
	private class FileSourcePosixFileAttributeView implements PosixFileAttributeView,BasicFileAttributeView {
		FileSource file ;
		boolean symbolicLink;

		FileSourcePosixFileAttributeView(FileSource file, boolean symbolicLink) {
			this.file = file;
			this.symbolicLink = symbolicLink;
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
			return new FileSourcePosixFileAttributes(file, symbolicLink);
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

	/** Parsed OpenOptions. */
	private static final class Opts {
		boolean read, write, append, create, createNew, truncate, deleteOnClose;

		Opts(Iterable<? extends OpenOption> options) {
			for(OpenOption op : options) {
				if( op == StandardOpenOption.READ ) {
					read = true;
				} else if( op == StandardOpenOption.WRITE ) {
					write = true;
				} else if( op == StandardOpenOption.APPEND ) {
					append = write = true;
				} else if( op == StandardOpenOption.CREATE ) {
					create = true;
				} else if( op == StandardOpenOption.CREATE_NEW ) {
					createNew = true;
				} else if( op == StandardOpenOption.TRUNCATE_EXISTING ) {
					truncate = true;
				} else if( op == StandardOpenOption.DELETE_ON_CLOSE ) {
					deleteOnClose = true;
				} else if( op == StandardOpenOption.SPARSE || op == LinkOption.NOFOLLOW_LINKS ) {
					// hints we can ignore
				} else if( op == null ) {
					throw new NullPointerException("null OpenOption");
				} else {
					throw new UnsupportedOperationException("Unsupported open option="+op);
				}
			}
			if( !write ) {
				read = true;
			}
			if( append && (read && !write || truncate) ) {
				throw new IllegalArgumentException("APPEND can't be combined with READ or TRUNCATE_EXISTING");
			}
		}
	}

	private static FileSource fileOf(Path path) {
		return ((FileSourcePath)path).getFileSource();
	}

	/** Existence checks for opening 'file' for writing, per the OpenOption contract. */
	private static void checkWritable(FileSource file, Path path, Opts o) throws IOException {
		boolean exists = file.exists();
		if( o.createNew && exists ) {
			throw new FileAlreadyExistsException(path.toString());
		}
		if( !exists && !o.create && !o.createNew ) {
			throw new NoSuchFileException(path.toString());
		}
		if( exists && file.isDirectory() ) {
			throw new FileSystemException(path.toString(), null, "Is a directory");
		}
	}

	private static void checkReadable(FileSource file, Path path) throws IOException {
		if( !file.exists() ) {
			throw new NoSuchFileException(path.toString());
		}
		if( file.isDirectory() ) {
			throw new FileSystemException(path.toString(), null, "Is a directory");
		}
	}

	/**
	 * Supports the standard options. (This used to reject CREATE and
	 * TRUNCATE_EXISTING -- the defaults Files.newOutputStream/newBufferedWriter
	 * pass when given explicit options -- and accepted CREATE_NEW without
	 * checking that the file didn't exist.)
	 */
	@Override
	public OutputStream newOutputStream(Path path, OpenOption... options) throws IOException {
		validate(path);
		List<OpenOption> list = options == null || options.length == 0
				? Arrays.asList(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
				: Arrays.asList(options);
		if( list.contains(StandardOpenOption.READ)) {
			throw new IllegalArgumentException("READ not allowed");
		}
		Opts o = new Opts(list);
		o.write = true;
		FileSource file = fileOf(path);
		checkWritable(file, path, o);

		OutputStream ret;
		if( o.append ) {
			ret = file.getOutputStream(true);
		} else if( o.truncate || !file.exists() ) {
			ret = file.getOutputStream();
		} else {
			// WRITE without TRUNCATE_EXISTING: overwrite from the start, keep the rest
			ret = FileSourceChannels.overwritingStream(file.getRandomAccessStream("rw"));
		}
		return o.deleteOnClose ? FileSourceChannels.deleteOnClose(ret, file) : ret;
	}

	@Override
	public InputStream newInputStream(Path path, OpenOption... options) throws IOException {
		validate(path);
		if( options != null ) {
			for(OpenOption op : options) {
				if( op == StandardOpenOption.APPEND || op == StandardOpenOption.WRITE ) {
					throw new UnsupportedOperationException("'"+op+"' not allowed");
				}
			}
		}
		FileSource file = resolve(path, new LinkOption[0]);
		checkReadable(file, path);
		return file.getInputStream();
	}

	/**
	 * Used by Files.readAllBytes, Files.readString, Files.lines, Files.write
	 * and others. (It used to throw UnsupportedOperationException, so all of
	 * those failed.)
	 */
	@Override
	public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
		validate(path);
		if( attrs != null && attrs.length > 0 ) {
			throw new UnsupportedOperationException("Initial file attributes are not supported");
		}
		Opts o = new Opts(options);
		FileSource file = fileOf(path);

		if( !o.write ) {
			file = resolve(path, new LinkOption[0]);
			checkReadable(file, path);
			return new FileSourceChannels.Read(file, o.deleteOnClose);
		}

		checkWritable(file, path, o);
		if( !o.read ) {
			if( o.append ) {
				long start = file.exists() ? file.length() : 0;
				return new FileSourceChannels.Write(file, file.getOutputStream(true), start, o.deleteOnClose);
			}
			if( o.truncate || !file.exists() ) {
				return new FileSourceChannels.Write(file, file.getOutputStream(), 0, o.deleteOnClose);
			}
		} else if( o.truncate && file.exists() ) {
			file.getOutputStream().close();
		}
		return new FileSourceChannels.RandomAccess(file, file.getRandomAccessStream("rw"), o.read, true, o.append, o.deleteOnClose);
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

	/**
	 * (The old iterator looped forever if the filter threw, next() didn't
	 * honour the filter unless hasNext() was called first, and neither threw
	 * NoSuchElementException.)
	 */
	@Override
	public DirectoryStream<Path> newDirectoryStream(Path dir, Filter<? super Path> filter) throws IOException {
		validate(dir);
		FileSource file = resolve(dir, new LinkOption[0]);
		if( !file.exists()) {
			throw new NoSuchFileException(dir.toString());
		}
		if(! file.isDirectory()) {
			throw new NotDirectoryException(dir.toString());
		}

		FileSource[] kids = file.listFiles();
		if( kids == null ) {
			throw new IOException("Could not list "+dir);
		}

		return  new DirectoryStream<Path>() {
			private boolean closed = false;
			private boolean iteratorReturned = false;

			@Override
			public void close() throws IOException {
				closed = true;
			}

			@Override
			public Iterator<Path> iterator() {
				if( closed ) {
					throw new IllegalStateException("Directory stream is closed");
				}
				if( iteratorReturned ) {
					throw new IllegalStateException("Iterator already obtained");
				}
				iteratorReturned = true;

				return new Iterator<Path>() {
					private int pos = 0;
					private Path next;

					@Override
					public boolean hasNext() {
						while( next == null && !closed && pos < kids.length ) {
							Path path = new FileSourcePath(kids[pos++]);
							try {
								if( filter == null || filter.accept(path)) {
									next = path;
								}
							} catch (IOException e) {
								throw new DirectoryIteratorException(e);
							}
						}
						return next != null;
					}

					@Override
					public Path next() {
						if( !hasNext()) {
							throw new NoSuchElementException();
						}
						Path ret = next;
						next = null;
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

		if( hasOption(options, StandardCopyOption.ATOMIC_MOVE)) {
			throw new UnsupportedOperationException("Atomic move is not supported");
		}
		boolean copyAttributes = hasOption(options, StandardCopyOption.COPY_ATTRIBUTES);
		boolean replaceExisting = hasOption(options, StandardCopyOption.REPLACE_EXISTING);

		/*
		 *  By default, the copy fails if the target file already exists or is a symbolic link, except if the source and target are the same file, 
		 *   in which case the method completes without copying the file.
		 */
		if( isSameFile(source, target)) {
			return;
		}

		FileSource sf = ((FileSourcePath)source).getFileSource();
		if( !sf.exists()) {
			throw new NoSuchFileException(source.toString());
		}

		FileSource tf = ((FileSourcePath)target).getFileSource();
		prepareTarget(tf, target, replaceExisting);

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
			// was tf.lastModified(), which copied the target's own time onto itself
			tf.setLastModifiedTime(sf.lastModified());
			tf.setLastAccessTime(sf.lastAccessTime());
			tf.setCreateTime(sf.creationTime());
		}
	}


	private static void validate(Path ... paths ) {
		for(Path p : paths){
			if (!(p instanceof FileSourcePath)) {
				throw new ProviderMismatchException(p.toString()+" is not a filesource path");				
			}
		}

	}



	/**
	 * Move or rename a file. As in Files.move, a symbolic link is moved
	 * itself, not the file it points to.
	 * 
	 * (This used to cast the CopyOption[] to LinkOption[], so every call
	 * threw ClassCastException, even with no options.)
	 */
	@Override
	public void move(Path source, Path target, CopyOption... options) throws IOException {
		validate(source,target);
		if( hasOption(options, StandardCopyOption.ATOMIC_MOVE)) {
			throw new AtomicMoveNotSupportedException(source.toString(), target.toString(), "Atomic move is not supported");
		}

		if( isSameFile(source, target)) {
			return;
		}

		FileSource s = ((FileSourcePath)source).getFileSource();
		if( !s.exists() && s.getLinkedTo() == null) {
			throw new NoSuchFileException(source.toString());
		}

		FileSource t = ((FileSourcePath)target).getFileSource();
		prepareTarget(t, target, hasOption(options, StandardCopyOption.REPLACE_EXISTING));

		if(!(s.renameTo(t))) {
			throw new IOException("Could not rename "+source+" to "+ target);
		}
	}

	private static boolean hasOption(CopyOption[] options, CopyOption option) {
		if( options != null ) {
			for(CopyOption o : options) {
				if( o == option ) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Make sure the copy/move target is free. If it already exists (or is a
	 * symbolic link) that's an error unless REPLACE_EXISTING was given, in
	 * which case it is deleted first (only an empty directory can be replaced).
	 */
	private static void prepareTarget(FileSource tf, Path target, boolean replaceExisting) throws IOException {
		if( !tf.exists() && tf.getLinkedTo() == null) {
			return;
		}
		if( !replaceExisting ) {
			throw new FileAlreadyExistsException(target.toString());
		}
		if( tf.isDirectory() && tf.getLinkedTo() == null) {
			FileSource [] kids = tf.listFiles();
			if( kids != null && kids.length > 0) {
				throw new DirectoryNotEmptyException(target.toString());
			}
		}
		if( !tf.delete()) {
			throw new IOException("Could not replace "+target);
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

	/**
	 * Throws NoSuchFileException / AccessDeniedException as the contract
	 * requires. (It used to throw a plain IOException for a missing file, so
	 * Files.notExists() was never true, and it checked owner permissions
	 * rather than the current user's.)
	 */
	@Override
	public void checkAccess(Path path, AccessMode... modes) throws IOException {
		validate(path);
		FileSource file = fileOf(path);
		if( !file.exists() && file.getLinkedTo() == null ) {
			throw new NoSuchFileException(path.toString());
		}
		file = resolve(path, new LinkOption[0]);

		for(AccessMode m : modes) {
			boolean ok = true;
			switch (m) {
			case READ: ok = file.canRead(); break;
			case WRITE: ok = file.canWrite(); break;
			case EXECUTE: ok = file.canExecute(); break;
			}
			if( !ok ) {
				throw new AccessDeniedException(path.toString(), null, "no "+m.name().toLowerCase()+" permission");
			}
		}
	}

	private static boolean followLinks(LinkOption[] options) {
		if( options != null ) {
			for(LinkOption lo : options) {
				if(lo == LinkOption.NOFOLLOW_LINKS) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * The FileSource a path refers to. When following links and the path
	 * itself doesn't exist but is a link (as memory links are), use the
	 * link's target. (A FileProxy link that exists already reads through to
	 * its target at the OS level.)
	 */
	private static FileSource resolve(Path path, LinkOption[] options) throws IOException {
		FileSource ret = fileOf(path);
		if( followLinks(options) && !ret.exists()) {
			FileSource link = ret.getLinkedTo();
			if( link != null ) {
				ret = link;
			}
		}
		return ret;
	}

	/** True if we should describe the link itself rather than its target. */
	private static boolean describesLink(Path path, LinkOption[] options) throws IOException {
		return !followLinks(options) && fileOf(path).getLinkedTo() != null;
	}

	/**
	 * Returns null for view types we don't support, as the contract requires.
	 * (It used to return the POSIX view whatever was asked for, so e.g. asking
	 * for an AclFileAttributeView gave the caller a ClassCastException.)
	 */
	@SuppressWarnings("unchecked")
	@Override
	public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
		validate(path);
		if( !type.isAssignableFrom(FileSourcePosixFileAttributeView.class) ) {
			return null;
		}
		try {
			return (V) new FileSourcePosixFileAttributeView(resolve(path, options), describesLink(path, options));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public <V extends BasicFileAttributes> V readAttributes(Path path, Class<V> type, LinkOption... options) throws IOException {
		validate(path);
		FileSource file = resolve(path, options);
		boolean link = describesLink(path, options);
		if( type == BasicFileAttributes.class ) {
			return (V) new FileSourceBasicFileAttributes(file, link);
		}
		if( type.isAssignableFrom(FileSourcePosixFileAttributes.class) ) {
			return (V) new FileSourcePosixFileAttributes(file, link);
		}
		throw new UnsupportedOperationException("Attributes of type "+type.getName()+" are not supported");
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
		Object attr =  readAttributes(path, BasicFileAttributes.class, options);
		String val = null;
		String [] parts = attributes.split("[:]");
		if( parts.length >= 2) {
			val= parts[1];
			if(parts[0].toLowerCase().equals("posix")) {

				attr =  readAttributes(path, PosixFileAttributes.class, options);
				cls = PosixFileAttributes.class;   // interface methods, including the basic ones
			}
		} else {
			val = attributes;
		}



		parts = val.split("[,]");
		for(String name : parts) {
			try {
				if( name.contains("*")) {
					for(Method m : cls.getMethods()) {
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



	public static FileSourceFileSystemProvider getSingleton() {
		if( singleton == null ) {
			synchronized (FileSourceFileSystemProvider.class) {
				if( singleton == null ) {
					singleton = new FileSourceFileSystemProvider();
				}
			}
		}
		
		return singleton;
	}

}
