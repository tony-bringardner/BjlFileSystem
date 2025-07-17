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

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import us.bringardner.io.filesource.FileSourceFactory;



public class FileSourceCoreTest  {

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

}
