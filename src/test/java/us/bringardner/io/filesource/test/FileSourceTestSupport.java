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
 */
package us.bringardner.io.filesource.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;

/**
 * What the shared FileSource test suites have in common: the factory under
 * test, the test directories, and helpers. The suites (AbstractTestClass,
 * AbstractNioProviderTests, AbstractRandomAccessStreamTests,
 * FileSourceRandomAccessIoBufferTests) extend it, and so does a test class
 * per factory, which sets the fields in its @BeforeAll.
 * <p>
 * Implementations get it from bjl_file_system's test-jar:
 * <pre>
 * &lt;dependency&gt;
 *   &lt;groupId&gt;us.bringardner&lt;/groupId&gt;
 *   &lt;artifactId&gt;bjl_file_system&lt;/artifactId&gt;
 *   &lt;version&gt;...&lt;/version&gt;
 *   &lt;type&gt;test-jar&lt;/type&gt;
 *   &lt;scope&gt;test&lt;/scope&gt;
 * &lt;/dependency&gt;
 * </pre>
 */
public abstract class FileSourceTestSupport {

	public static enum Permissions {
		OwnerRead('r'),
		OwnerWrite('w'),
		OwnerExecute('x'),

		GroupRead('r'),
		GroupWrite('w'),
		GroupExecute('x'),

		OtherRead('r'),
		OtherWrite('w'),
		OtherExecute('x');

	    public final char label;

	    private Permissions(char label) {
	        this.label = label;
	    }
	}

	public interface TestAction {
		void doSomthing(FileSource dir);
	}

	/** A local directory (default factory) with the files the tests copy. */
	public static String localTestFileDirPath ;
	/** The directory the tests use in the factory under test. */
	public static String remoteTestFileDirPath ;
	/** A local scratch directory (default factory). */
	public static String localCacheDirPath;
	/** The factory under test, connected. Disconnected after each test class. */
	public static FileSourceFactory factory;
	/**
	 * The server factory talks to, if the test class started one. It's stopped
	 * after the test class, once the suites have cleaned up and factory is
	 * disconnected. (A subclass's own @AfterAll runs before the suites'.)
	 */
	public static TestServerController server;
	public static boolean verbose = false;


	@AfterAll
	static void tearDownAfterAll() throws IOException  {
		if( factory != null ) {
			try {
				factory.disConnect();
			} catch (Throwable e) {
			}
		}
		if( server != null ) {
			TestServerController s = server;
			server = null;
			stopServer(s, SERVER_TIMEOUT);
		}
	}

	// ------------------------------------------------------------------ servers

	/** How long stopServer waits when the server field is stopped after a test class. */
	public static long SERVER_TIMEOUT = 10000;

	/**
	 * Starts the server and waits up to timeoutMs for it to run.
	 */
	public static void startServer(TestServerController server, long timeoutMs) throws IOException {
		server.start();
		long start = System.currentTimeMillis();
		while( !server.isRunning() && System.currentTimeMillis()-start < timeoutMs) {
			sleep(100);
		}
		assertTrue(server.isRunning(), server.getName()+" did not start within "+timeoutMs+"ms");
	}

	/**
	 * Stops the server and waits up to timeoutMs for it to stop.
	 */
	public static void stopServer(TestServerController server, long timeoutMs) throws IOException {
		server.stop();
		long start = System.currentTimeMillis();
		while( server.isRunning() && System.currentTimeMillis()-start < timeoutMs) {
			sleep(100);
		}
		assertFalse(server.isRunning(), server.getName()+" did not stop within "+timeoutMs+"ms");
	}

	private static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	// ------------------------------------------------------------------ Windows drives

	public static void tearDownWindowsDrive(String drive) throws IOException {
		File driveFile = new File(drive+":");
		if( driveFile.exists()) {
			List<String> cmd = Arrays.asList( "subst",drive+":","/D");
			executeExternalCommand(cmd);
		}
	}

	public static void executeExternalCommand(List<String> cmd) throws IOException {
		File cmdFile = null;

		for(String path : (""+System.getenv("PATH")).split("[;]")) {
			for(String ext : (""+System.getenv("PATHEXT")).split("[;]")) {
				File file = new File(path+"\\cmd"+ext);
				if( file.exists()) {
					cmdFile = file;
					break;
				}
			}
			if( cmdFile !=null) {
				break;
			}
		}

		List<String> cmd2 =new ArrayList<>();
		cmd2.add( cmdFile.getCanonicalPath());
		cmd2.add( "/r");
		cmd2.addAll(cmd);

		ProcessBuilder builder = new ProcessBuilder(cmd2);
		Process p = builder.start();
		int time = 0;
		while(p.isAlive()) {
			try {
				p.waitFor(1000, TimeUnit.MILLISECONDS);
				if( ++time > 3) {
					System.out.println("ExternalProcess Waiting for "+(time*1000));
				}
			} catch (InterruptedException e) {
			}
		}
		int exitCode = p.exitValue();
		if( exitCode!=0) {
			String err = new String(p.getErrorStream().readAllBytes());
			String out = new String(p.getInputStream().readAllBytes());
			throw new IOException(err+" = "+out);
		}

	}

	public static void setupWindowsDrive(String drive) throws IOException {
		File driveFile = new File(drive+":");
		if( !driveFile.exists()) {
			File sourceDir = new File("TestFiles").getCanonicalFile();
			if( !sourceDir.exists()) {
				throw new IOException("Missing souce dir  "+sourceDir);
			}

			File targetDir = new File("target\\"+drive+"DriveDir").getCanonicalFile();
			String tmp = targetDir.getAbsolutePath();
			if( !targetDir.exists()) {
				if( !targetDir.mkdirs()) {
					throw new IOException("Can't create target dir "+targetDir);
				}
			}
			FileSource from = FileSourceFactory.getDefaultFactory().createFileSource(sourceDir.getAbsolutePath());
			FileSource to = FileSourceFactory.getDefaultFactory().createFileSource(tmp);
			copy(from, to);


			List<String> cmd = Arrays.asList( "subst",drive+":",tmp);
			executeExternalCommand(cmd);
		}
	}

	// ------------------------------------------------------------------ files

	public static void traverseDir(FileSource dir,TestAction action) throws IOException {
		if(verbose) System.out.println(format(dir));
		if( action != null ) {
			action.doSomthing(dir);
		}
		if( dir.isDirectory()) {
			FileSource [] kids = dir.listFiles();
			if( kids != null ) {
				for(FileSource file : kids) {
					traverseDir(file,action);
				}
			}
		}
	}

	public static void deleteAll(FileSource file) throws IOException {
		if( file.isDirectory()) {
			FileSource[] kids = file.listFiles();
			if( kids !=null ) {
			for(FileSource child :kids) {
				deleteAll(child);
			}
			}
		}
		assertTrue(file.delete()
				, "Can't delete "+file);
	}

	/** Deletes dir and everything in it, then its parent if that's left empty. */
	public static void deleteAllAndEmptyParent(FileSource dir) throws IOException {
		FileSource parent = dir.getParentFile();
		if( dir.exists()) {
			deleteAll(dir);
		}
		if( parent != null && parent.exists()) {
			FileSource[] kids = parent.listFiles();
			if( kids == null || kids.length == 0 ) {
				parent.delete();
			}
		}
	}

	public static String format(FileSource dir) throws IOException {
		String ret = (String.format("factory=%s type=%s exists=%s path=%s read=%s write=%s size=%d",
				dir.getFileSourceFactory().getTypeId(),
				dir.isFile()?"File":dir.isDirectory()?"Dir":"Undefined",
						dir.exists() ? "true":"false",
								dir.getAbsolutePath(),
								dir.canRead()?"true":"false",
										dir.canWrite()?"true":"false",
												dir.length()
				)
				);

		return ret;

	}

	/**
	 * Compares two files, or two directory trees, by content. Children are
	 * matched by name: file systems don't list in the same order.
	 */
	public static void compare(String name,FileSource source, FileSource target) throws IOException {
		assertTrue(source.exists()
				, "Source file does not exist ("+
				source.getName()+")");
		assertTrue(target.exists()
		, "Target file does not exist ("+
		target.getName()+")");

		assertEquals(source.isDirectory(), target.isDirectory(), name+" are not the same type");

		if( source.isDirectory()) {
			FileSource [] kids1 = sortedByName(source.listFiles());
			FileSource [] kids2 = sortedByName(target.listFiles());
			assertEquals(kids1.length,kids2.length, name+" does not have the same number of kids");
			for(int idx=0;idx <  kids1.length; idx++ ) {
				assertEquals(kids1[idx].getName(), kids2[idx].getName(), name+" children differ");
				compare(name,kids1[idx],kids2[idx]);
			}

		} else {
			assertEquals(source.length(), target.length(), name+" lens are not eq");
			try(InputStream sourceIn = source.getInputStream()) {
				try(InputStream targetIn  = target.getInputStream()) {
					compare(name,sourceIn,targetIn);
				}
			}
		}
	}

	private static FileSource[] sortedByName(FileSource[] kids) {
		FileSource [] ret = kids == null ? new FileSource[0] : kids.clone();
		Arrays.sort(ret, Comparator.comparing(FileSource::getName));
		return ret;
	}

	/**
	 * Compare the bytes of two input streams
	 *
	 * @param in1
	 * @param in2
	 * @throws IOException
	 */
	public static void compare(String name,InputStream in1, InputStream in2) throws IOException {
		//  in1 & in2 will be closed by the java try / auto close in the calling function
		// use a small buffer to get multiple reads
		BufferedInputStream bin1 = new BufferedInputStream(in1);
		BufferedInputStream bin2 = new BufferedInputStream(in2);

		int ch = bin1.read();
		int pos = 0;
		while( ch >= 0) {
			assertEquals(ch, bin2.read(), name+" compare pos="+pos);
			pos++;
			ch = bin1.read();
		}
		assertEquals(ch, bin2.read(), name+" compare pos="+pos);

	}

	public static void copy(FileSource from, FileSource to) throws IOException {
		FileSource parent = to.getParentFile();
		if( parent != null && !parent.exists()) {
			parent.mkdirs();
		}
		if( from.isDirectory()) {
			FileSource [] kids = from.listFiles();
			if( kids != null ) {
				for(FileSource f : kids) {
					copy(f,to.getChild(f.getName()));
				}
			}
		} else {
			try(InputStream in = from.getInputStream()) {
				try(OutputStream out = to.getOutputStream()) {
					copy(in,out);
				}
			}

		}
	}

	public static void copy(InputStream in, OutputStream out) throws IOException {
		// use a small buffer to get multiple reads
		byte [] data = new byte[1024];
		int got = 0;

		try {
			while( (got=in.read(data)) >= 0) {
				if( got > 0 ) {
					out.write(data,0,got);
				}
			}

		} finally {
			try {
				out.close();
			} catch (Exception e) {
			}
			try {
				in.close();
			} catch (Exception e) {
			}

		}
	}

	public static void write(FileSource file, String text) throws IOException {
		try(OutputStream out = file.getOutputStream()) {
			out.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		}
	}

	public static void renameAndValidate(FileSource source, FileSource target) throws IOException {
		assertTrue(
				source.renameTo(target)
				,"Can't rename "+source+" to "+target);
		assertTrue(
				target.exists()
				,"New file does not exist after rename");
		assertFalse(
				source.exists()
				,"remoteFile still exists after rename");
	}

	// ------------------------------------------------------------------ permissions

	public static boolean setPermission(Permissions p, FileSource file,boolean b) throws IOException {
		boolean ret = false;
		switch (p) {
		case OwnerRead: 	ret = file.setOwnerReadable(b); break;
		case OwnerWrite:	ret = file.setOwnerWritable(b); break;
		case OwnerExecute:	ret = file.setOwnerExecutable(b); break;

		case GroupRead: 	ret = file.setGroupReadable(b); break;
		case GroupWrite:	ret = file.setGroupWritable(b); break;
		case GroupExecute:	ret = file.setGroupExecutable(b); break;

		case OtherRead: 	ret = file.setOtherReadable(b); break;
		case OtherWrite:	ret = file.setOtherWritable(b); break;
		case OtherExecute:	ret = file.setOtherExecutable(b); break;

		default:
			throw new RuntimeException("Invalid permision="+p);
		}

		return ret;
	}

	public static void comparePermissions(FileSource file1, FileSource file2) throws IOException {
		assertEquals(file1.canOwnerRead(), file2.canOwnerRead(),"canOwnerRead()");
		assertEquals(file1.canOwnerWrite(), file2.canOwnerWrite(),"canOwnerWrite()");
		assertEquals(file1.canOwnerExecute(), file2.canOwnerExecute(),"canOwnerExecute()");

		assertEquals(file1.canGroupRead(), file2.canGroupRead(),"canGroupRead()");
		assertEquals(file1.canGroupWrite(), file2.canGroupWrite(),"canGroupWrite()");
		assertEquals(file1.canGroupExecute(), file2.canGroupExecute(),"canGroupExecute()");

		assertEquals(file1.canOtherRead(), file2.canOtherRead(),"canOtherRead()");
		assertEquals(file1.canOtherWrite(), file2.canOtherWrite(),"canOtherWrite()");
		assertEquals(file1.canOtherExecute(), file2.canOtherExecute(),"canOtherExecute()");


	}

	public static boolean getPermission(Permissions p, FileSource file) throws IOException {
		boolean ret = false;
		switch (p) {
		case OwnerRead:    ret = file.canOwnerRead(); break;
		case OwnerWrite:   ret = file.canOwnerWrite(); break;
		case OwnerExecute: ret = file.canOwnerExecute(); break;

		case GroupRead:    ret = file.canGroupRead(); break;
		case GroupWrite:   ret = file.canGroupWrite(); break;
		case GroupExecute: ret = file.canGroupExecute(); break;

		case OtherRead:    ret = file.canOtherRead(); break;
		case OtherWrite:   ret = file.canOtherWrite(); break;
		case OtherExecute: ret = file.canOtherExecute(); break;

		default:
			throw new RuntimeException("Invalid permision="+p);
		}
		return ret;
	}

	public static void changeAndValidatePermission(Permissions p, FileSource file) throws IOException {

		//Get the current value
		boolean b = getPermission(p, file);

		// toggle it
		assertTrue(
				setPermission(p, file, !b),
				"set permission failed p="+p);
		boolean b2 = getPermission(p, file);
		assertEquals(b2, !b,"permision did not change p="+p);

		// Set it back
		assertTrue(
				setPermission(p, file, b),
				"reset permission failed p="+p);
		assertEquals(getPermission(p, file), b,"permision did not change back to original p="+p);


	}

	/**
	 * Fails if cls (or a superclass) declares isChildOfMine: every FileSource must use the
	 * single implementation in FileSource.
	 */
	public static void assertNoIsChildOfMineOverride(Class<?> cls) {
		if( java.lang.reflect.Proxy.isProxyClass(cls) ) {
			return;   // proxies forward to a real FileSource
		}
		for(Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
			for(java.lang.reflect.Method m : c.getDeclaredMethods()) {
				if( m.getName().equals("isChildOfMine") && !m.isSynthetic() ) {
					throw new AssertionError(c.getName()+" overrides isChildOfMine; FileSource has the one implementation."
							+" Provide a correct getCanonicalPath() instead.");
				}
			}
		}
	}
}
