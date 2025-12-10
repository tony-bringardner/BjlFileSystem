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
 * ~version~V000.01.00-V000.01.15-V000.01.11-V000.01.02-V000.01.01-V000.00.01-V000.00.00-
 */
package us.bringardner.io.filesource.test.link;



import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static us.bringardner.io.filesource.test.AbstractTestClass.compare;
import static us.bringardner.io.filesource.test.AbstractTestClass.copy;
import static us.bringardner.io.filesource.test.AbstractTestClass.deleteAll;

import java.io.IOException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.memory.MemoryFileSourceFactory;

@TestMethodOrder(OrderAnnotation.class)
public class FileSourceMemoryLinkTests {



	private static String localTestFileDirPath;
	private static String remoteTestFileDirPath;
	private static String localCacheDirPath;
	private static FileSourceFactory factory;

	@BeforeAll
	public static void beforeAll() throws IOException  {
		//System.out.println("Enter beforeAll");
		localTestFileDirPath = "TestFiles";
		localCacheDirPath = "target/MemoryLinkCache";
		remoteTestFileDirPath = "target/MemoryLinkTests";		
		
		factory = new MemoryFileSourceFactory();
		
		//System.out.println("Exit beforeAll");
	}
	
	@AfterAll
	static void afterAll()  {
		//System.out.println("Enter afterAll");
		if( factory != null ) {
			try {
				factory.disConnect();
			} catch (Throwable e) {
			}
		}
		//System.out.println("Exit afterAll");
	}
	
	public void createLocalCache() throws IOException {
		//System.out.println("Enter testCreateLocalCache");
		FileSource _localDir = FileSourceFactory.getDefaultFactory().createFileSource(localTestFileDirPath);
		assertTrue("local test dir does not exist ="+_localDir,_localDir.isDirectory());

		FileSource cacheDir = FileSourceFactory.getDefaultFactory().createFileSource(localCacheDirPath);
		if( cacheDir.exists()) {
			deleteAll(cacheDir);			
		}
		assertFalse("local cache dir already exists ="+cacheDir,cacheDir.exists());

		//  Make a copy of the local test directory
		copy(_localDir,cacheDir);

		FileSource remoteDir = factory.createFileSource(remoteTestFileDirPath);
		if( remoteDir.exists()) {
			deleteAll(remoteDir);			
		}
		
		if( !remoteDir.exists()) {
			assertTrue("Cannot create remote directory"+remoteDir,
					remoteDir.mkdirs()
					);			
		}
		//  Make another copy of the local test directory
		copy(cacheDir,remoteDir);

		//System.out.println("Exit testCreateLocalCache");
		
	}

	@Test 
	@Order(1)
	public void testSymbolicLink() throws IOException {
		//System.out.println("Enter testSymbolicLink");
		createLocalCache();
		FileSource remoteDir = factory.createFileSource(remoteTestFileDirPath);
		FileSource [] kids = remoteDir.listFiles();
		for(FileSource existing : kids) {
			String name = existing.getName();
			String lname = "Symlink_"+name;
			FileSource link0 =  remoteDir.getChild(lname);
			FileSource link = factory.createSymbolicLink(link0,existing);
			
			compare(lname, link, existing);
			
			FileSource link1 = existing.getLinkedTo();
			assertNull(link1);
			FileSource link2 = link.getLinkedTo();
			assertNotNull(link2);
			assertEquals(link2.getAbsolutePath(), existing.getAbsolutePath());
			
		}
		//System.out.println("Exit testSymbolicLink");
	}
		
	@Test 
	@Order(2)
	public void testHardLink() throws IOException {
		//System.out.println("Enter testHardLink");
		createLocalCache();
		FileSource remoteDir = factory.createFileSource(remoteTestFileDirPath);
		FileSource [] kids = remoteDir.listFiles();
		for(FileSource existing : kids) {
			String name = existing.getName();
			String lname = "Link_"+name;
			FileSource link0 =  remoteDir.getChild(lname);
			FileSource link = factory.createLink(link0,existing);
			
			compare(lname, link, existing);
			
			FileSource link1 = existing.getLinkedTo();
			assertNull(link1);
			FileSource link2 = link.getLinkedTo();
			assertNull(link2);			
			
		}
		//System.out.println("Exit testHardLink");
	}

}
