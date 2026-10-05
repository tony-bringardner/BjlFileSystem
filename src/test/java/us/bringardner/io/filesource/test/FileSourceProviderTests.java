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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.fileproxy.FileProxyFactory;
import us.bringardner.io.filesource.java.file.FileSourcePath;

/**
 * The java.nio.file provider over FileProxy (the shared tests are in
 * AbstractNioProviderTests), and FileSourcePath's own path logic.
 */
public class FileSourceProviderTests extends AbstractNioProviderTests {

	@BeforeAll
	public static void setUpBeforeAll()  {
		localTestFileDirPath = "TestFiles";
		remoteTestFileDirPath = "target/ProviderTests";		
		localCacheDirPath = "target/ProviderTestsCache";
		factory = new FileProxyFactory();		

	}

	@Test
	public void testFileSourcePath () throws URISyntaxException, IOException {
		//  just uses jrt path to normalize
		Path filePath =  Paths.get("/one/two/three");
		//Path filePath = !FileSourceFactory.isWindows()? Paths.get("\\one\\two\\three") :  Paths.get("/one/two/three");
		URI uri = new URI("filesource:/one/two/three?sourcetype=fileproxy");
		FileSourcePath fileSourcePath = new FileSourcePath(uri);
		assertEquals(filePath.getFileName().toString(), 
				fileSourcePath.getFileName().toString(), "getFileName invalid");
		assertEquals(filePath.getNameCount	(), 
				fileSourcePath.getNameCount(), "getNameCount invalid");

		assertEquals(filePath.getParent().toString(), 
				fileSourcePath.getParent().toString(), "getParent invalid");

		assertEquals(filePath.getRoot().toString(), 
				fileSourcePath.getRoot().toString(), "getRoot invalid");

		for(int idx=0; idx < fileSourcePath.getNameCount();  idx++){
			assertEquals(filePath.getName(idx).toString(),
					fileSourcePath.getName(idx).toString()
					, "getFileName invalid idx="+idx);	
		}
		Path first = filePath.getName(0);
		Path last = filePath.getName(filePath.getNameCount()-1);

		assertEquals(filePath.startsWith(first),
				fileSourcePath.startsWith(first), "startsWith invalid");
		assertEquals(filePath.endsWith(last), 
				fileSourcePath.endsWith(last), "endsWith invalid");

		Path tmp = filePath.normalize();
		String str = tmp.toString();
		Path tmp2 = fileSourcePath.normalize();
		String str2 = tmp2.toString();

		assertEquals(str,
				str2
				, "normalize invalid");

		if( !FileSourceFactory.isWindows()) {
			assertEquals(filePath.toAbsolutePath().toString(), 
					fileSourcePath.toAbsolutePath().toString()
					, "");
			assertEquals(filePath.resolve(last).toString(),
					fileSourcePath.resolve(last).toString(), "resolve invalid");


		} else {
			//  Windows does not handle relative paths correctly 
			assertTrue(fileSourcePath.toAbsolutePath().toString().endsWith( filePath.toAbsolutePath().toString().substring(2))	, "");
			assertTrue(fileSourcePath.resolve(last).toString().endsWith( filePath.resolve(last).toString().substring(2))	, "");
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
			assertEquals(file, file2, "Normalized don't match");

		}
	}

}
