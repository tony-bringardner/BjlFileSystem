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
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package us.bringardner.io.filesource;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

/**
 * @author Tony
 *
 * 
 * 
 */
public class FileSourceReader extends InputStreamReader {


	/**
	 * @param arg0
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	public FileSourceReader(FileSource fileSource) throws FileNotFoundException, IOException {
		super(fileSource.getInputStream());	
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @throws UnsupportedEncodingException
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	public FileSourceReader(FileSource fileSource, String encoding)
			throws UnsupportedEncodingException, FileNotFoundException, IOException {
		super(fileSource.getInputStream(), encoding);

	}

	/**
	 * @param arg0
	 * @param arg1
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	public FileSourceReader(FileSource fileSource, Charset arg1) throws FileNotFoundException, IOException {
		super(fileSource.getInputStream(), arg1);

	}

	/**
	 * @param arg0
	 * @param arg1
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	public FileSourceReader(FileSource fileSource, CharsetDecoder arg1) throws FileNotFoundException, IOException {
		super(fileSource.getInputStream(), arg1);
	}

}
