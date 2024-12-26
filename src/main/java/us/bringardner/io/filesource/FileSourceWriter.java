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
 * ~version~V000.00.01-V000.00.00-
 */
/*
 * Created on Jan 28, 2006
 *
 */
package us.bringardner.io.filesource;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;

/**
 * @author Tony
 *
  */
public class FileSourceWriter extends OutputStreamWriter {

	/**
	 * @param arg0
	 * @throws IOException 
	 */
	public FileSourceWriter(FileSource fileSource) throws IOException {
		super(fileSource.getOutputStream());
	
	}

	/**
	 * @param fileSource
	 * @param arg1
	 * @throws IOException 
	 */
	public FileSourceWriter(FileSource fileSource, String arg1)
			throws IOException {
		super(fileSource.getOutputStream(), arg1);
	
	}

	/**
	 * @param fileSource
	 * @param arg1
	 * @throws IOException 
	 */
	public FileSourceWriter(FileSource fileSource, Charset arg1) throws IOException {
		super(fileSource.getOutputStream(), arg1);
	
	}

	/**
	 * @param fileSource
	 * @param arg1
	 * @throws IOException 
	 */
	public FileSourceWriter(FileSource fileSource, CharsetEncoder arg1) throws IOException {
		super(fileSource.getOutputStream(), arg1);
	
	}

}
