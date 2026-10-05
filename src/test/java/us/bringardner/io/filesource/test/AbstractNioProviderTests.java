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
 */
package us.bringardner.io.filesource.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.java.file.FileSourcePath;

/**
 * The java.nio.file provider (Files.*) over the factory under test. A
 * subclass sets the fields of FileSourceTestSupport in its @BeforeAll; the
 * tests work in remoteTestFileDirPath/nio and read localTestFileDirPath.
 */
public abstract class AbstractNioProviderTests extends FileSourceTestSupport {

	/** How far a new file's modified time may be from the test's clock. */
	static final long TIME_TOLERANCE_MINUTES = 5;

	@AfterAll
	public static void deleteNioTestDir() throws IOException {
		if( factory != null ) {
			deleteAllAndEmptyParent(factory.createFileSource(remoteTestFileDirPath).getChild("nio"));
		}
	}

	/** A fresh, empty directory in the factory under test. */
	protected static Path nioDir(String name) throws IOException {
		Path dir = factory.createFileSource(remoteTestFileDirPath).getChild("nio").getChild(name).toPath();
		deleteIfExists(dir);
		return dir;
	}

	protected static Path localTestDir() throws IOException {
		return FileSourceFactory.getDefaultFactory().createFileSource(localTestFileDirPath).toPath();
	}

	/** Copies the local test directory to target with Files.* only. */
	protected static void copyTree(Path source, Path target) throws IOException {
		Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				Files.createDirectories(target.resolve(source.relativize(dir).toString()));
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.copy(file, target.resolve(source.relativize(file).toString()));
				return FileVisitResult.CONTINUE;
			}
		});
	}

	@Test
	public void testFileAttributes2() throws Exception {
		Path dir = nioDir("attributes2");
		copyTree(localTestDir(), dir);
		try {
			// the keys each view gives; the values depend on the file system
			Map<String, List<String>> expect = Map.of(
					"*", Arrays.asList("lastModifiedTime", "lastAccessTime", "size", "creationTime",
							"isSymbolicLink", "isRegularFile", "fileKey", "isOther", "isDirectory"),
					"size,lastModifiedTime,lastAccessTime", Arrays.asList("lastModifiedTime", "lastAccessTime", "size"),
					"posix:*", Arrays.asList("owner", "lastModifiedTime", "lastAccessTime", "size", "creationTime",
							"isSymbolicLink", "permissions", "isRegularFile", "fileKey", "isOther", "isDirectory", "group"),
					"posix:permissions,owner,size", Arrays.asList("owner", "size", "permissions")
					);

			for(Map.Entry<String, List<String>> e : expect.entrySet()) {
				Map<String, Object> actual = Files.readAttributes(dir, e.getKey());
				assertEquals(new HashSet<>(e.getValue()), actual.keySet(), "keys for "+e.getKey());
			}
		} finally {
			deleteIfExists(dir);
		}
	}

	@Test
	public void testFileAttributes() throws Exception {
		Path target = nioDir("attributes");
		FileTime now = FileTime.fromMillis(System.currentTimeMillis());
		copyTree(localTestDir(), target);
		try {
			try (DirectoryStream<Path> stream = Files.newDirectoryStream(target)) {
				int count = 0;
				for (Path path : stream) {
					count++;
					BasicFileAttributes attr = Files.readAttributes(path, BasicFileAttributes.class);
					assertNotNull(attr, "attributes are null path="+path);
					checkTime("new modify time "+path,now,attr.lastModifiedTime());
					assertEquals(Files.isDirectory(path), attr.isDirectory(), "isDir");
					assertEquals(Files.isRegularFile(path), attr.isRegularFile(), "isFile");
					assertEquals(Files.isSymbolicLink(path), attr.isSymbolicLink(), "isSymlink");

					PosixFileAttributes pattr = Files.readAttributes(path, PosixFileAttributes.class);
					assertNotNull(pattr, "pattr is null for path="+path);
					checkTime("new modify time "+path,now,pattr.lastModifiedTime());
					assertEquals(Files.isDirectory(path), pattr.isDirectory(), "isDir");
					assertEquals(Files.isRegularFile(path), pattr.isRegularFile(), "isFile");
					assertEquals(Files.isSymbolicLink(path), pattr.isSymbolicLink(), "isSymlink");
					// by name: not every FileSource's principals implement equals
					assertEquals(Files.getOwner(path).getName(), pattr.owner().getName(), "Owner");
					Set<PosixFilePermission> perms = currentUserOwns(path) ? pattr.permissions() : Set.of();
					if( perms.contains(PosixFilePermission.OWNER_READ)) {
						assertTrue(Files.isReadable(path), "should be readable path="+path);
					}
					if( perms.contains(PosixFilePermission.OWNER_WRITE)) {
						assertTrue(Files.isWritable(path), "should be writable path="+path);
					}
					if( perms.contains(PosixFilePermission.OWNER_EXECUTE)) {
						assertTrue(Files.isExecutable(path), "should be executable path="+path);
					}
				}
				assertTrue(count > 0, "nothing was copied to "+target);
			}

			// owner write stays on: some servers (FTP) refuse to change the
			// permissions of a file the owner can't write, so it couldn't be undone
			for(String unixPerms : new String[] {"rwxr-x---", "rw-r-xr--", "rwxrwxrwx"}) {
				Files.setPosixFilePermissions(target, PosixFilePermissions.fromString(unixPerms));
				validatePermission(unixPerms, target);
			}
		} finally {
			deleteIfExists(target);
		}
	}

	/**
	 * Only the modified time: not every file system keeps creation or
	 * access times.
	 */
	public static void checkTime(String msg, FileTime expect, FileTime actual) {
		long et = expect.to(TimeUnit.MINUTES);
		long at = actual.to(TimeUnit.MINUTES);
		assertTrue(Math.abs(et-at) <= TIME_TOLERANCE_MINUTES, msg+" expected about "+expect+" but was "+actual);
	}

	@Test
	public void testCopyDir() throws Exception {
		Path source = localTestDir();
		Set<String> expect;
		try(Stream<Path> kids = Files.list(source)) {
			expect = kids.map(p -> p.getFileName().toString()).collect(Collectors.toSet());
		}
		assertFalse(expect.isEmpty(), "no test files in "+source);

		Path target = nioDir("copyDir");
		copyTree(source, target);
		try {
			compare("Copy dir",source,target);

			try(Stream<Path> kids = Files.list(target)) {
				assertEquals(expect, kids.map(p -> p.getFileName().toString()).collect(Collectors.toSet()), "Files.list");
			}

			Set<String> names = new HashSet<>();
			try (DirectoryStream<Path> stream = Files.newDirectoryStream(target)) {
				for (Path path : stream) {
					names.add(path.getFileName().toString());
					assertNotNull(Files.readAttributes(path, BasicFileAttributes.class), "path="+path);
				}
			}
			assertEquals(expect, names, "Files.newDirectoryStream");

			try(Stream<Path> all = Files.walk(target, FileVisitOption.FOLLOW_LINKS)) {
				Set<String> files = all.filter(p -> !Files.isDirectory(p))
						.map(p -> p.getFileName().toString())
						.collect(Collectors.toSet());
				assertEquals(expect, files, "Files.walk");
			}
		} finally {
			deleteIfExists(target);
		}
	}

	public static void deleteIfExists(Path path) throws IOException {
		if( Files.exists(path)) {
			if(Files.isDirectory(path)) {
				try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
					for (Path child : stream) {
						deleteIfExists(child);
					}
				}
			}
			Files.delete(path);
		}

	}

	public static void compare(String name, Path source, Path target) throws IOException {
		FileSource s = ((FileSourcePath)source).getFileSource();
		FileSource t = ((FileSourcePath)target).getFileSource();
		compare(name, s,t);

	}

	@Test
	public void testCreateDir() throws  Exception {
		Path path = nioDir("createDir");
		Files.createDirectories(path.getParent());

		String unixPerms = "rwxr-x---";

		Set<PosixFilePermission> perms = PosixFilePermissions.fromString(unixPerms);
		FileAttribute<Set<PosixFilePermission>> attr = PosixFilePermissions.asFileAttribute(perms);
		Path dir = Files.createDirectory(path, attr);
		assertNotNull(dir, "Got null from createDirectory");
		assertTrue(Files.exists(dir), "Dir was not created");
		assertTrue(Files.isDirectory(dir), "Return from Files.createDirectory is not a dir ");

		//  check permissions
		validatePermission(unixPerms,dir);


		Files.delete(dir);
		assertFalse(Files.exists(dir), "Dir was not deleted");

	}

	/** True if the factory's user owns path. */
	public static boolean currentUserOwns(Path path) throws IOException {
		String owner = Files.getOwner(path).getName();
		return owner != null && owner.equalsIgnoreCase(factory.whoAmI().getName());
	}

	public static void validatePermission(String unixPerms, Path dir) throws IOException {
		assertTrue(unixPerms.length() == 9, "Unix perms not the right length="+unixPerms);
		Set<PosixFilePermission> expect = PosixFilePermissions.fromString(unixPerms);
		//  this calls readAttributes
		Set<PosixFilePermission> actual = Files.getPosixFilePermissions(dir);
		assertEquals(expect, actual, "Permissions of "+dir);

		//  the owner bits decide access only for the owner (an anonymous FTP
		//  login, for one, owns nothing)
		if( !currentUserOwns(dir)) {
			return;
		}
		assertEquals(expect.contains(PosixFilePermission.OWNER_READ), Files.isReadable(dir), "Files.isReadable "+unixPerms);
		assertEquals(expect.contains(PosixFilePermission.OWNER_WRITE), Files.isWritable(dir), "Files.isWritable "+unixPerms);
		assertEquals(expect.contains(PosixFilePermission.OWNER_EXECUTE), Files.isExecutable(dir), "Files.isExecutable "+unixPerms);
	}
}
