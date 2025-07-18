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
 * ~version~V000.01.16-V000.01.11-V000.01.10-V000.01.09-V000.01.00-V000.00.01-V000.00.00-
 */
/*
 * Created on Dec 13, 2004
 *
 */
package us.bringardner.io.filesource.memory;

import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;


/**
 * @author Tony Bringardner
 *
 */
public class MemoryFileSourceFactory extends FileSourceFactory {

	private static final long serialVersionUID = 1L;

	private static class Link implements InvocationHandler {

		@SuppressWarnings("unused")
		boolean hardLink = true;
		MemoryFileSource existing;
		MemoryFileSource link;
		
		public Link(MemoryFileSource source,MemoryFileSource link,boolean hardLink) {
			this.existing = source;
			this.link = link;
			this.hardLink = hardLink;
		}
		
		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			Object ret = null;
			String mname = method.getName();
			
			if(mname.equals("getLinkedTo") && !hardLink) {
				ret = method.invoke(link, args);	
			} else if(mname.equals("getName") || mname.equals("toString") || mname.equals("getCanonicalPath")) {
				ret = method.invoke(link, args);
			} else {
				ret = method.invoke(existing, args);
			}
			 			   
			return ret;
		}
		
	}

	
	public static final String FACTORY_ID = "memory";

	private static final String PROP_NAME = "name";
	
	
	private volatile  MemoryFileSource [] roots;
	private volatile FileSource currentDirectory;
	private String name = "MemoryFileSet";
	private boolean connected = false;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	String expandDots(String path) {
		char sep = getSeperatorChar();
		List<String> expanded = new ArrayList<>();
		String [] parts = path.split(""+sep);
		//System.out.println("lsn="+parts.length);

		if(! path.startsWith(""+sep)) {
			String p2[] = currentDirectory.getAbsolutePath().split(""+sep);
			for (int i = 0; i < p2.length; i++) {
				expanded.add(p2[i]);
			}
		}
		for (int idx1 = 0; idx1 < parts.length; idx1++) {

			String name = parts[idx1].trim();
			if( name.isEmpty()) {
				continue;
			}
			if( name.equals("..")) {
				expanded.remove(expanded.size()-1);

			} else if(name.equals(".")) {
				if( idx1 == 0 && !path.startsWith(".")) {
					String p2[] = currentDirectory.getAbsolutePath().split(""+sep);
					for (int i = 0; i < p2.length-1; i++) {
						expanded.add(p2[i]);
					}
				} else {
					// ignore
				}
			} else {
				expanded.add(name);
			}
		}
		StringBuilder buf = new StringBuilder();
		for(String name: expanded) {
			buf.append(""+sep);
			buf.append(name);
		}
		String ret = buf.toString().replaceAll("//", "/");
		return ret;
	}

	/**
	 * 
	 */
	public MemoryFileSourceFactory() {
		super();
		init();
	}


	private void init() {
		connected=false;
		roots = new MemoryFileSource[1];
		MemoryFileSource root = new MemoryFileSource(null, "/", this);
		root.isRoot = true;		
		currentDirectory = roots[0] = root;
		//  set the current dir to the java current dir
		File file = new File(".");
		try {
			currentDirectory =  createFileSource(file.getCanonicalPath());
		} catch (IOException e) {
			// should never happen
			e.printStackTrace();
		}		
	}

	/* Set The Current dir (Ignored for FileProxy)
	 * @see us.bringardner.io.FileSourceFactory#setCurrentDirectory(us.bringardner.io.FileSource)
	 */
	public void setCurrentDirectory(FileSource dir) {
		if (!(dir instanceof MemoryFileSource)) {
			throw new RuntimeException("currentDir can only be a MemoryFileSource");			
		}
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
		return roots;
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.filesource.FileSourceFactory#isVersionSupported()
	 */
	public boolean isVersionSupported() {
		return false;
	}

	
	protected boolean connectImpl() throws IOException {
		if( !connected) {
			connected = true;
		}
		
		return connected;
	}

	protected void disConnectImpl() {
		init();
	}

	public Properties getConnectProperties() {
		Properties ret = new Properties();
		ret.setProperty(PROP_NAME, name==null?"":name);
		return ret;
	}

	public boolean isConnected() {
		return connected;
	}

	/* (non-Javadoc)
	 * @see us.bringardner.io.filesource.FileSourceFactory#getEditPropertiesComponent()
	 */
	@Override
	public Component getEditPropertiesComponent() {
		return null;
	}


	/* (non-Javadoc)
	 * @see us.bringardner.io.filesource.FileSourceFactory#setProperties(java.net.URL)
	 */
	@Override
	public void setConnectionProperties(URL url) {
		
			String tmp = url.getQuery();			
			String [] q = tmp.split(",");
			for (int idx = 0; idx < q.length; idx++) {				
				String [] q2 = q[idx].split("=");
				if( q2.length==2 && PROP_NAME.equals(q2[0])) {
					name = q2[1];
				}	
		}		
	}

	

	/* (non-Javadoc)
	 * @see us.bringardner.io.filesource.FileSourceFactory#setConnectionProperties(java.util.Properties)
	 */
	@Override
	public void setConnectionProperties(Properties prop) {
		String tmp = prop.getProperty(PROP_NAME);
		if( tmp != null ) {
			this.name = tmp;
		}
	}

	@Override
	public FileSourceFactory createThreadSafeCopy() {
		// This factory is thread safe
		return this;
	}


	@Override
	public FileSource createFileSource(String fullPath) throws IOException {
		String expand = expandDots(fullPath);

		if(fullPath.equals("/")) {
			return roots[0];
		}
		MemoryFileSource ret = null;
		String [] parts = expand.split(""+getSeperatorChar());
		MemoryFileSource parent = roots[0];

		// always starts with the root by now

		for (int idx = 1; idx < parts.length; idx++) {		
			String name = parts[idx].trim();
			if(!name.isEmpty()) {				
				if( (ret=parent.getChildByName(name))==null) {
					ret = new MemoryFileSource(parent, name, this);
					parent.addChild(ret);
				}
				
				parent = ret;
			}
		}
		return ret;
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
					currentDirectory = roots[0];										
				}
			}
		}

		return currentDirectory;
	}


	@Override
	public char getPathSeperatorChar() {
		return ':';
	}


	@Override
	public char getSeperatorChar() {
		return '/';
	}

	FileSource getProxy(MemoryFileSource source,MemoryFileSource target,boolean hardLink) {
		FileSource ret = (FileSource) Proxy.newProxyInstance(
				  getClass().getClassLoader(), 
				  new Class[] { FileSource.class }, 
				  new Link(source,target,hardLink));

		return ret;
	}
	
	@Override
	public FileSource createSymbolicLink(FileSource newLink, FileSource existing) throws IOException {
		FileSource ret = createLink(newLink, existing, false);
		return ret;
	}


	@Override
	public FileSource createLink(FileSource newLink, FileSource existing) throws IOException {
		FileSource ret = createLink(newLink, existing, true);
		return ret;
	}

	private FileSource createLink(FileSource newLink, FileSource existing,boolean hardLink) throws IOException {
		FileSource ret = newLink;
		if (newLink instanceof MemoryFileSource) {
			MemoryFileSource nfs = (MemoryFileSource) newLink;
			if (existing instanceof MemoryFileSource) {
				MemoryFileSource efs = (MemoryFileSource) existing;
				ret = getProxy(efs, nfs, hardLink);
				nfs.linkedTo = ret;
			}
		}
		return ret;
	}

}
