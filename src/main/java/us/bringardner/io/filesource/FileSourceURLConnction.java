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
 * Created on Dec 17, 2004
 *
 */
package us.bringardner.io.filesource;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Properties;

import us.bringardner.core.util.LogHelper;
import us.bringardner.io.filesource.fileproxy.FileProxyFactory;

/**
 * 
 * @author Tony Bringardner
 *
 */
public class FileSourceURLConnction extends URLConnection {

	private FileSource target ;
	private static LogHelper logger = new LogHelper(FileSourceURLConnction.class);

	static {
		// force FileSourceFactory class loading
		 FileSourceFactory.getDefaultFactory();
	}
	
	/**
	 * @param arg0
	 */
	public FileSourceURLConnction(URL url) {
		super(url);
		try {
			connect();
		} catch (IOException e) {
			logger.logError("Can't connect", e);
		}
	}

	public FileSource getTarget() {
		return target;
	}
	
	/* (non-Javadoc)
	 * @see java.net.URLConnection#connect()
	 */
	public void connect1() throws IOException {
		if( target == null ) {
			//  Find the target FileSOurce
			URL url = getURL();
			logger.logDebug("url="+url);
						
			String tmp = url.toString();
			String parts[] = tmp.split(":");
			
			logger.logDebug("parts.len = "+parts.length);
			
			if( parts.length < 3 ) {
				throw new MalformedURLException("URL does not look like a fileSource URL len="+parts.length);
			}
			
			String type = parts[1];
			logger.logDebug("type="+type);
			
			String file = parts[2];
			if(type.equals(FileProxyFactory.FACTORY_ID) ) {
				file = parts[2]+":"+parts[3];
			}
			
			//  Create a Property from the remaining parts
			Properties prop = new Properties(System.getProperties());
			
			for (int idx = 3; idx < parts.length; idx++) {
				String [] kv = parts[idx].split("=");
				if( kv.length > 1) {
					prop.setProperty(kv[0], kv[1]);
				}
			}
			
			FileSourceFactory factory = null;
			
			
			factory = FileSourceFactory.getFileSourceFactory(type);
			if( factory == null ){
				throw new FileNotFoundException("No factory for "+type);
			}
		
			factory.connect(prop);
			
			target = factory.createFileSource(file);

		}
	}
	


	public java.io.InputStream getInputStream()
		throws java.io.IOException
	{
		return  target.getInputStream();
	}


	public int getContentLength()

	{
		try {
			return  (int)target.length();
		} catch (IOException e) {
			throw new IllegalStateException("Can't get length e="+e);
		}
	}

	
	public long getLastModified()

	{
		try {
			return  target.lastModified();
		} catch (IOException e) {
			throw new IllegalStateException("Can't get length e="+e);
		}
	}

	public java.io.OutputStream getOutputStream()
		throws java.io.IOException
	{
		return  target.getOutputStream();
	}


	/* (non-Javadoc)
	 * @see java.net.URLConnection#connect()
	 */
	public void connect() throws IOException {
		if( target == null ) {
			//  Find the target FileSOurce
			URL url = getURL();
			System.out.println("url="+url);
			if( !url.toString().startsWith(FileSourceFactory.FILE_SOURCE_PROTOCOL+":")) {
				throw new IllegalArgumentException("URL is not a filesource = "+url);
			}
			logger.logDebug("url="+url);
						
			String tmp = url.getQuery();
			if( tmp == null ) {
				connect1();
				return;
			} 
			
			String parts[] = tmp.split("&");
			
			logger.logDebug("parts.len = "+parts.length);
			
			if( parts.length < 1 ) {
				throw new MalformedURLException("query string is not define in the URL");
			}

			String type = null;
			
			for (int idx = 0; type==null && idx < parts.length; idx++) {
				String pt [] = parts[idx].split("=");
				if(pt.length == 2 && pt[0].toLowerCase().equals(FileSourceFactory.QUERY_STRING_SOURCE_TYPE)) {
					type = pt[1];
				}				
			}
			
			logger.logDebug("type="+type);
			
			if( type == null ) {
				throw new MalformedURLException(FileSourceFactory.QUERY_STRING_SOURCE_TYPE+" is not defined int the query string. ("+tmp+")");
			}
			
			FileSourceFactory factory = FileSourceFactory.getFileSourceFactory(type);
			
			if( factory == null ){
				throw new MalformedURLException(FileSourceFactory.QUERY_STRING_SOURCE_TYPE+" or "+ type +" does not exist.");
			}
			
			target = FileSourceFactory.getFileSource(url);
	
		}
	}


}
