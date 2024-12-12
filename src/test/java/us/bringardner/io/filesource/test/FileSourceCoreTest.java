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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Random;

import org.junit.jupiter.api.Test;

import us.bringardner.io.filesource.FileSourceFactory;



public class FileSourceCoreTest  {

	@Test
	public void testExpandDots() throws IOException {
	
	
		String [] options = {
				".",
				"..",
				"one",
				"two",
				"/",
				"three","four","five","six"
		};
		Random r = new Random();
		int numRuns = 100;
		
		while(--numRuns>0) {
			int cnt = r.nextInt(20);
			while(cnt <1) {
				cnt = r.nextInt(20);
			}
			StringBuilder buf = new StringBuilder();
			for(int idx=0; idx < cnt; idx++ ) {
				if( idx>0) {
					buf.append('/');
				}
				int pos = r.nextInt(options.length-1);
				buf.append(options[pos]);
			}
			String pathStr = "./"+buf.toString();

			String u    = Paths.get(pathStr).normalize().toString();
			String me   = FileSourceFactory.expandDots(pathStr, '/');
			if( !me.equals(u)) {
				System.out.println("String path1=\""+pathStr+"\";");
				System.out.println("//String me=\""+me+"\";");
				System.out.println("String expect=\""+u+"\";");
			}
			assertEquals(u,me,"Bad dot expantion path='"+pathStr+"'");
		}
	}

}
