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
 * ~version~V000.01.20-V000.01.19-V000.01.17-V000.01.16-V000.01.15-V000.01.09-V000.01.05-V000.01.02-V000.01.01-V000.00.05-V000.00.02-V000.00.01-V000.00.00-
 */
package us.bringardner.io.filesource.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;


/**
 * The FileSource contract, for any factory. A subclass sets the fields of
 * FileSourceTestSupport (factory, localTestFileDirPath, remoteTestFileDirPath,
 * localCacheDirPath) in its @BeforeAll.
 */
@TestMethodOrder(OrderAnnotation.class)
public abstract class AbstractTestClass extends FileSourceTestSupport {

	@Test
	@Order(1)
	public void testRoots() throws IOException {
		FileSource [] roots = factory.listRoots();
		assertNotNull(roots, "Roots are null");
		assertTrue(roots.length>0, "No roots files ");

	}

	@Test 
	@Order(2)
	public void replicateTestDir() throws IOException {
		FileSource _localDir = FileSourceFactory.getDefaultFactory().createFileSource(localTestFileDirPath);
		assertTrue(_localDir.isDirectory(), "local test dir does not exist ="+_localDir);

		FileSource cacheDir = FileSourceFactory.getDefaultFactory().createFileSource(localCacheDirPath);
		if( cacheDir.exists()) {
			deleteAll(cacheDir);			
		}
		assertFalse(cacheDir.exists(), "local cache dir already exists ="+cacheDir);

		//  Make a copy of the local test directory
		copy(_localDir,cacheDir);

		FileSource remoteDir = factory.createFileSource(remoteTestFileDirPath);
		if(remoteDir.exists()) {
			deleteAll(remoteDir);			
		}


		for(FileSource source : cacheDir.listFiles()) {
			String nm = source.getName();
			FileSource dest = remoteDir.getChild(nm);
			copy(source, dest);
			compare("Copy to remote dir", source, dest);			
		}
		traverseDir(remoteDir, null);

		
		if(verbose) System.out.println("Rename files\n");
		for(FileSource remoteFile : remoteDir.listFiles()) {
			String fileName = remoteFile.getName();
			FileSource localFile = cacheDir.getChild(fileName);
			compare(fileName, localFile, remoteFile);
			FileSource remoteParent = remoteFile.getParentFile();
			
			FileSource renamedFile = remoteParent.getChild(fileName+".changed");
			
			renameAndValidate(remoteFile,renamedFile);
						
			compare(fileName, localFile, renamedFile);
			
			renameAndValidate(renamedFile,remoteFile);
			
			
			compare(fileName, localFile, remoteFile);
			
		}



		//  delete the roots files
		deleteAll(cacheDir);
		deleteAll(remoteDir);
		traverseDir(remoteDir, null);

	}

	@Test 
	@Order(3)
	public void testPermissions() throws IOException {
		FileSource remoteDir = factory.createFileSource(remoteTestFileDirPath);
		if( !remoteDir.exists()) {
			assertTrue(remoteDir.mkdirs(),"Can't create dirs for "+remoteTestFileDirPath);
		}
		
		FileSource file = remoteDir.getChild("TestPermissions.txt");
		try(OutputStream out = file.getOutputStream()) {
			out.write("Put some data in the file".getBytes());
		}
		
	for(Permissions p : Permissions.values()) {
			//  if we turn off owner write we won't be able to turn it back on.
			if( p != Permissions.OwnerWrite) {
				changeAndValidatePermission(p,file);
			}
		}
		
		assertTrue(file.delete(),"Can't delete "+file);
		
	}


	@Test
	@Order(4)
	public void testAppend() throws IOException {
		FileSource remoteDir = factory.createFileSource(remoteTestFileDirPath);
		if( !remoteDir.exists()) {
			assertTrue(remoteDir.mkdirs(),"Can't create dirs for "+remoteTestFileDirPath);
		}
		FileSource file = remoteDir.getChild("AppendTest.txt");
		byte [] data = "0123456789".getBytes();
		try {
			try(OutputStream out = file.getOutputStream()) {
				for(int idx=0; idx< 10; idx++ ) {
					out.write(data);
				}
			}
			assertEquals(data.length*10, file.length(),"length after write");

			try(OutputStream out = file.getOutputStream(true)) {
				for(int idx=0; idx< 10; idx++ ) {
					out.write(data);
				}
			}
			assertEquals(data.length*20, file.length(),"length after append");

			assertArrayEquals(data, file.head(data.length),"head");
			assertArrayEquals(data, file.tail(data.length),"tail");
		} finally {
			if( file.exists()) {
				assertTrue(file.delete(),"Can't delete "+file);
			}
		}
	}


	/** As java.io.File.mkdir: one level, false if it exists or the parent doesn't. */
	@Test
	@Order(5)
	public void testMkdir() throws IOException {
		FileSource base = factory.createFileSource(remoteTestFileDirPath).getChild("mkdirTest");
		if( base.exists()) {
			deleteAll(base);
		}
		try {
			assertTrue(base.mkdirs(), "Can't create "+base);

			FileSource dir = base.getChild("dir");
			assertFalse(dir.exists(), dir+" exists before mkdir");
			assertTrue(dir.mkdir(), "mkdir of a new directory");
			assertTrue(dir.exists(), "exists after mkdir");
			assertTrue(dir.isDirectory(), "isDirectory after mkdir");
			assertTrue(factory.createFileSource(dir.getAbsolutePath()).isDirectory(), "a new FileSource sees it");
			FileSource[] kids = base.listFiles();
			assertEquals(1, kids == null ? 0 : kids.length, "the parent's listing has it");

			assertFalse(dir.mkdir(), "mkdir of an existing directory");

			FileSource deep = base.getChild("missing").getChild("dir");
			assertFalse(deep.mkdir(), "mkdir under a missing parent");
			assertFalse(deep.exists(), "nothing made under a missing parent");
		} finally {
			if( base.exists()) {
				deleteAll(base);
			}
		}
	}

	// ------------------------------------------------------------------ isChildOfMine contract
	//
	// Runs for every factory whose test class extends this one. The rule itself lives in
	// FileSource.isChildOfMine (one implementation for all factories); what each file
	// system must get right is getCanonicalPath().

	@Test
	public void testIsChildOfMineContract() throws IOException {
		FileSource base = factory.createFileSource(remoteTestFileDirPath).getChild("isChildOfMineContract");
		if( base.exists() ) {
			deleteAll(base);
		}
		try {
			FileSource root = base.getChild("root");
			FileSource sub = root.getChild("sub");
			assertTrue(sub.mkdirs(), "can't create "+sub);
			write(sub.getChild("file.txt"), "inside");
			FileSource sibling = base.getChild("rootX");   // same prefix as root
			assertTrue(sibling.mkdirs(), "can't create "+sibling);
			write(sibling.getChild("x.txt"), "sibling");
			FileSource outsideDir = base.getChild("outside");
			assertTrue(outsideDir.mkdirs(), "can't create "+outsideDir);
			write(outsideDir.getChild("secret.txt"), "secret");

			// inside
			assertTrue(root.isChildOfMine(root), "itself");
			assertTrue(root.isChildOfMine(sub), "sub");
			assertTrue(root.isChildOfMine(root.getChild("sub/file.txt")), "sub/file.txt");
			assertTrue(root.isChildOfMine(root.getChild("sub/../sub/file.txt")), "sub/../sub/file.txt");
			assertTrue(root.isChildOfMine(root.getChild("new/../also-new")), "a path that doesn't exist yet");

			// not inside
			for(String p : new String[] {"..", "../outside/secret.txt", "sub/../../outside/secret.txt", "a/../../rootX/x.txt"}) {
				assertFalse(root.isChildOfMine(root.getChild(p)), p+" must not be inside "+root);
			}
			assertFalse(root.isChildOfMine(sibling.getChild("x.txt")), "same-prefix sibling");
			assertFalse(root.isChildOfMine(base), "parent");
			assertFalse(root.isChildOfMine(null), "null");

			// a symbolic link inside root that leads outside is not inside
			FileSource link = null;
			try {
				link = factory.createSymbolicLink(root.getChild("link-out"), outsideDir);
			} catch (IOException | UnsupportedOperationException e) {
				if( verbose ) {
					System.out.println("No symbolic links for "+factory.getTypeId()+": "+e);
				}
			}
			if( link != null ) {
				assertFalse(root.isChildOfMine(link), "symbolic link to outside");
				assertFalse(root.isChildOfMine(root.getChild("link-out")), "symbolic link to outside, looked up by path");
				assertFalse(root.isChildOfMine(root.getChild("link-out/secret.txt")), "file through a symbolic link to outside");
			}

			// a hard link is just another name: it IS inside
			FileSource hard = null;
			try {
				hard = factory.createLink(root.getChild("hard.txt"), outsideDir.getChild("secret.txt"));
			} catch (IOException | UnsupportedOperationException e) {
				if( verbose ) {
					System.out.println("No hard links for "+factory.getTypeId()+": "+e);
				}
			}
			if( hard != null ) {
				assertTrue(root.isChildOfMine(root.getChild("hard.txt")), "hard link inside root");
			}

			// the rule has one implementation: no FileSource class may override it
			assertNoIsChildOfMineOverride(root.getClass());
		} finally {
			if( base.exists() ) {
				deleteAll(base);
			}
		}
	}
}
