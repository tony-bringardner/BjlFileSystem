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
package us.bringardner.io.filesource.test;



import org.junit.jupiter.api.BeforeAll;

import us.bringardner.io.filesource.memory.MemoryFileSourceFactory;



public class FileSourceMemoryTests extends FileSourceAbstractTestClass {



	@BeforeAll
	public static void setUpBeforeAll()  {
		localTestFileDirPath = "TestFiles";
		remoteTestFileDirPath = "target/MemoryTests";		
		localCacheDirPath = "target/MemoryTestsCache";
		factory = new MemoryFileSourceFactory();

	}


}
