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
 * ~version~V000.01.24-V000.01.12-V000.01.11-V000.01.08-V000.01.07-V000.01.06-V000.01.05-V000.01.04-V000.01.02-V000.01.00-V000.00.01-V000.00.00-
 */
package us.bringardner.io.filesource.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.fileproxy.FileProxyFactory;
import us.bringardner.io.filesource.java.file.FileSourcePath;

public class FileSourceProviderTests extends FileSourceAbstractTestClass {
	class Index {
		int idx=0;
	}

	@BeforeAll
	public static void setUpBeforeAll()  {
		localTestFileDirPath = "TestFiles";
		remoteTestFileDirPath = "target/ProviderTests";		
		localCacheDirPath = "target/ProviderTestsCache";
		factory = new FileProxyFactory();		

	}

	@AfterAll
	public static void tearDownAfterAll() {
		if( factory !=null) {
			try {
				factory.disConnect();
			} catch (Exception e) {
			}
		}
	}

	@Test
	public void testFileAttributes2() throws Exception {
		URI uri = new URI(String.format("filesource:%s?sourcetype=%s",localTestFileDirPath,FileSourceFactory.getDefaultFactory().getTypeId()));
		Path source = Paths.get(uri);

		String [] tmp = { 
				"lastModifiedTime=2024-11-22T03:48:50Z, lastAccessTime=1970-01-01T00:00:00Z, size=170, creationTime=2024-11-22T03:48:50Z, "
						+ "isSymbolicLink=false, isRegularFile=false, fileKey=filesource, isOther=false, isDirectory=true"
						, "lastModifiedTime=2024-11-22T03:48:50Z, lastAccessTime=1970-01-01T00:00:00Z, size=170"
						, "owner=tony, lastModifiedTime=2024-11-22T03:48:50Z, lastAccessTime=1970-01-01T00:00:00Z, size=170, "
								+ "creationTime=2024-11-22T03:48:50Z, isSymbolicLink=false, permissions=permissions, isRegularFile=false,"
								+ " fileKey=filesource, isOther=false, isDirectory=true, group=staff"
								, "owner=tony, size=170, permissions=permission"
		}	;

		@SuppressWarnings("unchecked")
		Map<String,Object> [] expect = new Map[tmp.length];
		for (int idx = 0; idx < tmp.length; idx++) {
			expect[idx] = new HashMap<String, Object>();
			String [] parts = tmp[idx].split("[,]");
			for(String p : parts) {
				String parts2 [] = p.split("[=]");
				expect[idx] .put(parts2[0].trim(), parts2[1].trim());
			}
		}

		String args[] = {
				"*","size,lastModifiedTime,lastAccessTime","posix:*","posix:permissions,owner,size"
		};
		for (int idx = 0; idx < args.length; idx++) {
			String str = args[idx];
			Map<String, Object> actualMap = Files.readAttributes(source, str);
			Map<String, Object> expectMap = expect[idx];
			for(String key : expectMap.keySet()) {
				key = key.trim();
				assertTrue("idx="+idx+" key="+key+" is not in actual map",actualMap.containsKey(key));
			}
			for(String key : actualMap.keySet()) {
				key = key.trim();
				assertTrue("idx="+idx+" key="+key+" is not in expected map",expectMap.containsKey(key));
			}


		}

	}

	@Test
	public void testFileAttributes() throws Exception {
		URI uri = new URI(String.format("filesource:%s?sourcetype=%s",localTestFileDirPath,FileSourceFactory.getDefaultFactory().getTypeId()));
		Path source = Paths.get(uri);

		uri = new URI(String.format("filesource:%s?sourcetype=%s",localCacheDirPath,factory.getTypeId()));		
		Path target = Paths.get(uri);




		deleteIfExists(target);
		Files.copy(source, target);
		Index idx = new Index();
		idx.idx = 0;
		FileTime now = FileTime.fromMillis(System.currentTimeMillis());
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(target)) {
			for (Path path : stream) {
				BasicFileAttributes attr = Files.readAttributes(path, BasicFileAttributes.class);
				assertNotNull("attributes are null path="+path,attr);
				checkTime("new create time "+path,now,attr.creationTime());
				checkTime("new access time "+path,now,attr.lastAccessTime());
				checkTime("new modify time "+path,now,attr.lastModifiedTime());
				assertEquals("isDir",Files.isDirectory(path), attr.isDirectory());
				assertEquals("isFile",Files.isRegularFile(path), attr.isRegularFile());
				assertEquals("isSymlink",Files.isSymbolicLink(path), attr.isSymbolicLink());

				PosixFileAttributes pattr = Files.readAttributes(path, PosixFileAttributes.class);
				assertNotNull("pattr is null for path="+path,pattr);
				checkTime("new create time "+path,now,pattr.creationTime());
				checkTime("new access time "+path,now,pattr.lastAccessTime());
				checkTime("new modify time "+path,now,pattr.lastModifiedTime());
				assertEquals("isDir",Files.isDirectory(path), pattr.isDirectory());
				assertEquals("isFile",Files.isRegularFile(path), pattr.isRegularFile());
				assertEquals("isSymlink",Files.isSymbolicLink(path), pattr.isSymbolicLink());
				assertEquals("Owner",Files.getOwner(path), pattr.owner());
				for(PosixFilePermission p :  pattr.permissions()) {
					switch (p) {
					case OWNER_READ: assertTrue("should be readable path="+path,Files.isReadable(path));break;
					case OWNER_WRITE: assertTrue("should be writable path="+path,Files.isWritable(path));break;
					case OWNER_EXECUTE: assertTrue("should be executable path="+path,Files.isExecutable(path));break;
					case GROUP_READ:
					case GROUP_WRITE:
					case GROUP_EXECUTE:
					case OTHERS_READ:
					case OTHERS_WRITE:
					case OTHERS_EXECUTE:

						break;

					default:
						break;
					}
				}
			}
		}

		String unixPerms = "r-xr-x---";

		Set<PosixFilePermission> perms = PosixFilePermissions.fromString(unixPerms);
		Files.setPosixFilePermissions(target, perms);
		validatePermission(unixPerms, target);

		unixPerms = "rw-r-xr--";

		perms = PosixFilePermissions.fromString(unixPerms);
		Files.setPosixFilePermissions(target, perms);
		validatePermission(unixPerms, target);

		unixPerms = "rwxrwxrwx";

		perms = PosixFilePermissions.fromString(unixPerms);
		Files.setPosixFilePermissions(target, perms);
		validatePermission(unixPerms, target);

		//Object a = Files.getAttribute(target, unixPerms);
		deleteIfExists(target);

	}

	public static void checkTime(String msg, FileTime expect,	 FileTime actual) {
		long et = expect.to(TimeUnit.MINUTES);
		long at = expect.to(TimeUnit.MINUTES);
		assertEquals(msg,et,at);
	}

	@Test
	public void testCopyDir() throws Exception {
		List<String> expect =Arrays.asList(
				"AnalyzerDerby.properties"
				, "AnalyzerMySql.properties"
				, "Hotel California.txt"
				);

		URI uri = new URI(String.format("filesource:%s?sourcetype=fileproxy",localTestFileDirPath));
		Path source = Paths.get(uri);

		uri = new URI(String.format("filesource:%s?sourcetype=%s",remoteTestFileDirPath,factory.getTypeId()));

		Path target = Paths.get(uri);

		deleteIfExists(target);

		//Path target2 = Files.copy(source, target);
		//assertTrue("Target was not created.",Files.exists(target2));

		Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				Path p1 = source.relativize(dir);
				Path p2 = target.resolve(p1.toString());

				Files.createDirectories( p2);

				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Path p1 = source.relativize(file);
				Path p2 = target.resolve(p1.toString());


				Files.copy(file, p2);
				return FileVisitResult.CONTINUE;
			}
		});

		compare("Copy dir",source,target);


		Index idx = new Index();
		try(Stream<Path> kids = Files.list(target)) {
			kids.forEach((path)->{
				String name = path.getFileName().toString();
				assertTrue("",expect.contains(name));
			});
		}

		idx.idx = 0;
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(source)) {
			for (Path path : stream) {
				String name = path.getFileName().toString();
				assertTrue("",expect.contains(name));
				BasicFileAttributes attr = Files.readAttributes(path, BasicFileAttributes.class);
				assertNotNull("path="+path,attr);
				//FileTime time = attr.creationTime();

			}
		}

		idx.idx = 0;
		Files.walk(target, FileVisitOption.FOLLOW_LINKS).forEach((path)->{
			if( !Files.isDirectory(path)) {
				String name = path.getFileName().toString();
				assertTrue("",expect.contains(name));

			}
		});
		deleteIfExists(target);
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

		URI uri2 = new URI("filesource:./target/CreateDir?sourcetype=fileproxy");
		Path path = Paths.get(uri2);
		if( Files.exists(path)) {
			Files.delete(path);
			assertFalse("Dir already existed and could not delete it",Files.exists(path));				
		}

		String unixPerms = "rwxr-x---";

		Set<PosixFilePermission> perms = PosixFilePermissions.fromString(unixPerms);
		FileAttribute<Set<PosixFilePermission>> attr = PosixFilePermissions.asFileAttribute(perms);
		Path dir = Files.createDirectory(path, attr);
		assertNotNull("Got null from createDirectory",dir);
		assertTrue("Dir was not created",Files.exists(dir));
		assertTrue("Return from Files.createDirectory is not a dir ",Files.isDirectory(dir));

		//  check permissions
		validatePermission(unixPerms,dir);


		Files.delete(dir);
		assertFalse("Dir was not deleted",Files.exists(dir));

	}

	private void validatePermission(String unixPerms, Path dir) throws IOException {
		assertTrue("Unix perms not the right length="+unixPerms,unixPerms.length() == 9);
		Set<PosixFilePermission> expect = PosixFilePermissions.fromString(unixPerms);
		//  this calls readAttributes
		Set<PosixFilePermission> actual = Files.getPosixFilePermissions(dir);
		assertEquals("Expectd and actual permision set size does not match",expect.size(), actual.size());

		for(PosixFilePermission p : PosixFilePermission.values()) {
			boolean eb = expect.contains(p);
			boolean ab = actual.contains(p);
			assertEquals("Permission is not correct for "+p+" path="+dir,eb,ab);
		}

		//  check permissions
		if( expect.contains(PosixFilePermission.OWNER_READ)) {
			assertTrue("Directory can't be read",Files.isReadable(dir)); 
		} else {
			assertFalse("Directory can't be read",Files.isReadable(dir));
		}

		if( expect.contains(PosixFilePermission.OWNER_WRITE)) {
			assertTrue("Directory can't be write",Files.isWritable(dir)); 
		} else {
			assertFalse("Directory can't be write",Files.isWritable(dir));
		}

		if( expect.contains(PosixFilePermission.OWNER_EXECUTE)) {
			assertTrue("Directory can't be executable",Files.isExecutable(dir)); 
		} else {
			assertFalse("Directory can't be executable",Files.isExecutable(dir));
		}

	}

	@Test
	public void testFileSourcePath () throws URISyntaxException, IOException {
		//  just uses jrt path to normalize
		Path filePath =  Paths.get("/one/two/three");
		//Path filePath = !FileSourceFactory.isWindows()? Paths.get("\\one\\two\\three") :  Paths.get("/one/two/three");
		URI uri = new URI("filesource:/one/two/three?sourcetype=fileproxy");
		FileSourcePath fileSourcePath = new FileSourcePath(uri);
		assertEquals("getFileName invalid", 
				filePath.getFileName().toString(), 
				fileSourcePath.getFileName().toString());
		assertEquals("getNameCount invalid", 
				filePath.getNameCount	(), 
				fileSourcePath.getNameCount());

		assertEquals("getParent invalid", 
				filePath.getParent().toString(), 
				fileSourcePath.getParent().toString());

		assertEquals("getRoot invalid", 
				filePath.getRoot().toString(), 
				fileSourcePath.getRoot().toString());

		for(int idx=0; idx < fileSourcePath.getNameCount();  idx++){
			assertEquals("getFileName invalid idx="+idx, 
					filePath.getName(idx).toString(),
					fileSourcePath.getName(idx).toString()
					);	
		}
		Path first = filePath.getName(0);
		Path last = filePath.getName(filePath.getNameCount()-1);

		assertEquals("startsWith invalid", 
				filePath.startsWith(first),
				fileSourcePath.startsWith(first));
		assertEquals("endsWith invalid", 
				filePath.endsWith(last), 
				fileSourcePath.endsWith(last));

		Path tmp = filePath.normalize();
		String str = tmp.toString();
		Path tmp2 = fileSourcePath.normalize();
		String str2 = tmp2.toString();

		assertEquals("normalize invalid", 
				str,
				str2
				);

		if( !FileSourceFactory.isWindows()) {
			assertEquals("", 
					filePath.toAbsolutePath().toString(), 
					fileSourcePath.toAbsolutePath().toString()
					);
			assertEquals("resolve invalid", 
					filePath.resolve(last).toString(),
					fileSourcePath.resolve(last).toString());


		} else {
			//  Windows does not handle relative paths correctly 
			assertTrue("", fileSourcePath.toAbsolutePath().toString().endsWith( filePath.toAbsolutePath().toString().substring(2))	);
			assertTrue("", fileSourcePath.resolve(last).toString().endsWith( filePath.resolve(last).toString().substring(2))	);
		}

	}


	@Test
	public void testNormalize() {
		FileSourceFactory factory = FileSourceFactory.getDefaultFactory();
		FileSystem fileSystem = FileSystems.getDefault();
		//  Weighted toward dot and dot dot
		String [] options = {".","..",".","..",".","..","one","two",""+factory.getSeperatorChar(),"three","four","five","six"};
		Random r = new Random();
		int numTests = 100;
		while(--numTests > 0) {
			int nameCount = r.nextInt(20);
			StringBuilder fileBuf = new StringBuilder();
			StringBuilder filesourceBuf = new StringBuilder();
			for(int idx=0; idx < nameCount; idx++ ) {
				if( idx>0) {
					fileBuf.append(fileSystem.getSeparator());
					filesourceBuf.append(factory.getSeperatorChar());
				}
				int pos = r.nextInt(options.length-1);
				fileBuf.append(options[pos]);
				filesourceBuf.append(options[pos]);
			}
			String filePathStr = fileBuf.toString();
			String filesourcePathStr = filesourceBuf.toString();
			if( FileSourceFactory.isWindows()) {
				while( filePathStr.startsWith("\\\\")) {
					filePathStr = filePathStr.substring(1);
					filesourcePathStr = filesourcePathStr.substring(1);
				}
			}
			String file    = Paths.get(filePathStr).normalize().toString();
			Path filesource   = new FileSourcePath(filesourcePathStr,factory).
					normalize();
			String file2 = filesource.toString();
			
			if(! file.equals(file2)) {
				System.out.println("Not eq");
			} 
			assertEquals("Normalized don't match", file, file2);

		}
	}

}
