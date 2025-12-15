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
 * ~version~V000.01.21-V000.01.16-V000.01.12-V000.01.00-V000.00.01-V000.00.00-
 */
/*
 * Created on Dec 13, 2004
 *
 */
package us.bringardner.io.filesource.fileproxy;

import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;


/**
 * @author Tony Bringardner
 *
 */
public class FileProxyFactory extends FileSourceFactory {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	public final String LOCAL_INDICATOR="~";
	public static final String FACTORY_ID = "fileproxy";
	private volatile FileSource [] roots;
	private volatile FileSource currentDirectory;

	/**
	 * 
	 */
	public FileProxyFactory() {
		super();	
	}


	/* Set The Current dir (Ignored for FileProxy)
	 * @see us.bringardner.io.FileSourceFactory#setCurrentDirectory(us.bringardner.io.FileSource)
	 */
	public void setCurrentDirectory(FileSource dir) {
		currentDirectory = dir;
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.FileSourceFactory#getTypeId()
	 */
	public String getTypeId() {

		return FACTORY_ID;
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.filesource.FileSourceFactory#listRoots()
	 */
	public FileSource[] listRoots() throws IOException {

		if(roots == null ) {
			synchronized(FileSourceFactory.class) {
				if(roots == null) {

					if (System.getProperty ("os.name").indexOf ("Windows") != -1) {
						List<FileProxy> list = new ArrayList<FileProxy>();
						for (char i = 'A'; i <= 'Z'; ++i) {

							File drive = new File(i+ ":\\");
							if (drive.exists() && drive.isDirectory() ) {
								list.add(new FileProxy(drive,this));
							}
						}

						roots = (FileSource[]) list.toArray(new FileSource[list.size()]);	
					} else {
						FileSource root = FileSourceFactory.getDefaultFactory().createFileSource( System.getProperty("file.separator"));
						roots = new FileSource[] { root };

					}
				}
			}
		}


		return roots;
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.filesource.FileSourceFactory#isVersionSupported()
	 */
	public boolean isVersionSupported() {
		return false;
	}

	public Properties getConnectProperties() {
		//  No properties are required.
		return new Properties();
	}

	public boolean isConnected() {
		// Local FileSource is always connected.
		return true;
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.filesource.FileSourceFactory#getEditPropertiesComponent()
	 */
	@Override
	public Component getEditPropertiesComponent() {
		// No properties are required
		return null;
	}


	/* (non-Javadoc)
	 * @see us.bringardner.io.filesource.FileSourceFactory#setProperties(java.net.URL)
	 */
	@Override
	public void setConnectionProperties(URL url) {
		// Nothing to do	

	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.filesource.FileSourceFactory#setConnectionProperties(java.util.Properties)
	 */
	@Override
	public void setConnectionProperties(Properties prop) {
		// Nothing to do		
	}

	@Override
	public FileSourceFactory createThreadSafeCopy() {
		// This factory is thread safe
		return this;
	}



	@Override
	public FileSource createFileSource(String fullPath) throws IOException {

		boolean abs = fullPath.startsWith("/");
		if( isWindows()) {
			abs = fullPath.length()>1 && Character.isAlphabetic(fullPath.charAt(0)) && fullPath.charAt(1) == ':';
		}
		
		File file = new File(fullPath);
		String realPath = fullPath;
		
		if( !abs) {
			FileSource cwd = getCurrentDirectory();			
			if( cwd != null ) {
				file = new File(((FileProxy) cwd).target,fullPath);
				realPath = file.getAbsolutePath();
			}
		}
		
		
		File root=null;
		
		for(FileSource tmp: listRoots()) {
			String rootPath =tmp.getAbsolutePath();
			if( realPath.startsWith(rootPath)) {
				root = ((FileProxy) tmp).target;
				realPath = realPath.substring(rootPath.length());
				break;
			}
		}

		if(root !=null &&  !realPath.isEmpty()) {
			file = new File(root,realPath);
		}

		return new FileProxy(file,this);
	}



	@Override
	public String getTitle() {
		return "Local";
	}



	@Override
	public String getURL() {
		return FACTORY_ID+"://";
	}



	@Override
	public FileSource getCurrentDirectory() throws IOException {
		if( currentDirectory == null ) {
			synchronized (this) {
				if( currentDirectory == null ) {
					File tmp = new File(".");
					currentDirectory = new FileProxy(tmp.getCanonicalFile(),this);

				}
			}
		}
		return currentDirectory;
	}


	@Override
	public char getPathSeperatorChar() {
		return File.pathSeparatorChar;
	}


	@Override
	public char getSeperatorChar() {
		return File.separatorChar;
	}


	@Override
	protected boolean connectImpl() throws IOException {
		// ignore
		return true;
	}


	@Override
	protected void disConnectImpl() {
		// ignore

	}


	@Override
	public FileSource createSymbolicLink(FileSource newLink, FileSource existing) throws IOException {
		FileSource ret = newLink;
		if (newLink instanceof FileProxy) {
			FileProxy sfp = (FileProxy) newLink;
			if (existing instanceof FileProxy) {
				FileProxy tfp = (FileProxy) existing;
				Path path = Files.createSymbolicLink(sfp.target.toPath(), tfp.target.toPath());

				ret = new FileProxy(path.toFile(), this); 
			}
		}

		return ret;
	}


	@Override
	public FileSource createLink(FileSource newLink, FileSource existing) throws IOException {
		FileSource ret = newLink;
		if (existing instanceof FileProxy) {
			FileProxy efp = (FileProxy) existing;
			if (newLink instanceof FileProxy) {
				FileProxy nlfp = (FileProxy) newLink;
				Path path = Files.createLink(nlfp.target.toPath(), efp.target.toPath());
				ret = new FileProxy(path.toFile(), this); 
			}
		}
		return ret;
	}



}
