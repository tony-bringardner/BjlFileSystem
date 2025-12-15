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
 * ~version~V000.01.00-V000.01.14-V000.01.11-V000.01.02-V000.01.01-V000.00.01-V000.00.00-
 */
package us.bringardner.io.filesource.test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.fileproxy.FileProxyFactory;


public class FileSourceCoreTest  extends AbstractTestClass {


	@BeforeAll
	public static void setUpBeforeAll() throws IOException {
		if( FileSourceFactory.isWindows()) {
			setupWindowsDrive("L");
			setupWindowsDrive("M");
			setupWindowsDrive("N");

		}

		localTestFileDirPath = "TestFiles";
		remoteTestFileDirPath = "target/MemoryTests";		
		localCacheDirPath = "target/MemoryTestsCache";
		factory = FileSourceFactory.getDefaultFactory();

	}

	@AfterAll
	public static void tearDown() throws IOException {
		if( FileSourceFactory.isWindows()) {
			tearDownWindowsDrive("L");
			tearDownWindowsDrive("M");
			tearDownWindowsDrive("N");
		}
	}

	@Test
	public void testExpandDots() throws IOException {

		char sep = '/';//File.separatorChar;

		String paths [] = {
				"one///five/one/one/./one/.///.././////four/..///one",
				"////one/./five/..///./one/five/.///five/one",
				"three/two/two/four/one/three/three/five///two/./..//",
				"//../.././five/two/one/./../two/.",
				".///../four/one///.././../..///././two/three/..",
				"./////../five/////../three/three/.///three",
				"one/three/five///two/./two/two",
				"one/two///one/two/////..",
				"two/./../five/..//",
				"//five/five/two///.././//two//",
				"five/.././../four/five///./../one/two/.///five/.//",

				"./../////two/./one/five/two/one///////one/.///one/four",
				"one/two/../five/one/.///./five///five/.",

				"//three/./two///.././five///five/one/./two/./two/one",
				"../////../one/./three/..//",
				"five///three/two///four/..///../one/five/three/../../two",
				"..///./..///one/three/../four///three/.///one/../five/two/four/.",
				"//two/one///one///./../////../two/two/one/one",
				"./../..//",
				"./one/two/../four/two/../..///../one/two//",
				"////five/./three/./.././/",
				"////./three/////five/..",
				"////./five/./two/.//",
				"three/one/one/one/./////one/.",
				"//////one///../five",
				"../..///./two/three///two///five/one",
				"three/////five///three/three/one/four/.",
				"../four/three/two/five",
				".././two///five/////./four",
				"//./one/five///four/five///../one/../../..",
				"./../three/three/../one",
				"//../three/four/////one///four/../..///./../../three//",
				"////five/.///..///five//",
				"./four/./one/../one/../two/////three//",
				"..///.",
				"././//one/three/four/five/four/./../two/.",
				"one/../three/two///one/three/two/./three/./three/../one/one/./three"

		};

		for (int idx = 0; idx < paths.length; idx++) {
			String actual   = FileSourceFactory.expandDots(paths[idx],sep);

			String tmp = paths[idx];

			if( sep != File.separatorChar) {
				tmp = tmp.replace(sep, File.separatorChar);
			}
			String expect = Paths.get(tmp).normalize().toString();
			if( sep != File.separatorChar) {
				expect = expect.replace( File.separatorChar,sep);
			}
			if( !actual.equals(expect)) {
				System.out.println("pathStr=\""+paths[idx]+"\";");
				System.out.println("actual=\""+actual+"\";");
				System.out.println("expect=\""+expect+"\";");
			}
			//System.out.println(paths[idx]+","+expect+","+actual);
		}
	}

	@Test
	public void testListRoots() throws IOException  {
		factory = new FileProxyFactory();
		FileSource [] roots = factory.listRoots();
		assertNotNull(roots);
		assertTrue(roots.length>0);
		
		if( FileSourceFactory.isWindows()) {
			String drives [] = {"C:\\","L:\\","M:\\","N:\\"};
			//  Minimally is C:,L:,M: and N:
			assertTrue(roots.length>3);
			for(String name : drives) {
				FileSource dir = factory.createFileSource(name);
				String path = dir.getAbsolutePath();
				assertEquals(name, path);
			}
			
			for(String name1 : drives) {
				String name = name1+"AbcFile.txt";
				FileSource dir = factory.createFileSource(name);
				String path = dir.getAbsolutePath();
				assertEquals(name, path);
			}
			
			FileSource cwd = factory.createFileSource("L:\\");
			factory.setCurrentDirectory(cwd);
			String name = "AbcFile.txt";
			FileSource file = factory.createFileSource(name);
			String path = file.getAbsolutePath();
			String expect ="L:\\AbcFile.txt";
			assertEquals(expect, path);
			
			name = "O:\\AbcFile.txt";
			file = factory.createFileSource(name);
			path = file.getAbsolutePath();
			expect ="O:\\AbcFile.txt";
			assertEquals(expect, path);
			
			name = "/AbcFile.txt";
			file = factory.createFileSource(name);
			path = file.getAbsolutePath();
			expect ="L:\\AbcFile.txt";
			assertEquals(expect, path);
			
			
		}
	}

	@Test
	public void testListRoots2() throws IOException  {
		factory = new FileProxyFactory();
		FileSource [] roots = factory.listRoots();
		assertNotNull(roots);
		assertTrue(roots.length>0);
		
		if( FileSourceFactory.isWindows()) {
			String drives [] = {"C:\\","L:\\","M:\\","N:\\"};
			//  Minimally is C:,L:,M: and N:
			assertTrue(roots.length>3);
			for(String name : drives) {
				FileSource dir = factory.createFileSource1(name);
				String path = dir.getAbsolutePath();
				assertEquals(name, path);
			}
			
			for(String name1 : drives) {
				String name = name1+"AbcFile.txt";
				FileSource dir = factory.createFileSource1(name);
				String path = dir.getAbsolutePath();
				assertEquals(name, path);
			}
			
			FileSource cwd = factory.createFileSource1("L:\\");
			factory.setCurrentDirectory(cwd);
			String name = "AbcFile.txt";
			FileSource file = factory.createFileSource1(name);
			String path = file.getAbsolutePath();
			String expect ="L:\\AbcFile.txt";
			assertEquals(expect, path);
			
			name = "O:\\AbcFile.txt";
			file = factory.createFileSource1(name);
			assertEquals(null, file);
			
			name = "/AbcFile.txt";
			file = factory.createFileSource1(name);
			path = file.getAbsolutePath();
			expect ="L:\\AbcFile.txt";
			assertEquals(expect, path);
			
			
		}
	}
}
