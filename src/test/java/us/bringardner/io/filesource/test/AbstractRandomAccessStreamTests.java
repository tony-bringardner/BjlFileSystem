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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.IRandomAccessStream;

/**
 * FileSource.getRandomAccessStream for any factory, checked against
 * java.io.RandomAccessFile. A subclass sets the fields of
 * FileSourceTestSupport in its @BeforeAll (a small chunk size, where the
 * factory has one, makes the tests cross many chunks).
 */
@TestMethodOrder(OrderAnnotation.class)
public abstract class AbstractRandomAccessStreamTests extends FileSourceTestSupport {

	enum Action{Write,Read,Seek,SetLength}

	static class Entry {
		Action action;
		long value;
		long pointerBefore;
		long pointerAfter;
		long jpointerBefore;
		long jpointerAfter;

		Entry(Action action) {
			this.action = action;
		}

		@Override
		public String toString() {
			return action+","+value+",ram("+pointerBefore+","+pointerAfter+") jram("+jpointerBefore+","+jpointerAfter+")";
		}
	}

	protected static long targetFileSize=500;
	protected static String testDataString = "0123456789";
	protected static byte [] testData = testDataString.getBytes();
	/** Random actions in compareWithJava. */
	protected static int compareActions = 50;

	private static FileSource testDir;
	private static FileSource file;

	private static FileSource testDir() throws IOException {
		if( testDir == null ) {
			testDir = factory.createFileSource(remoteTestFileDirPath).getChild("RandomAccessStream");
			if( !testDir.exists()) {
				assertTrue(testDir.mkdirs(),"Can't create "+testDir);
			}
		}
		return testDir;
	}

	@AfterAll
	public static void deleteRandomAccessTestDir() throws IOException {
		if( testDir != null ) {
			deleteAllAndEmptyParent(testDir);
		}
		testDir = null;
		file = null;
	}

	/**
	 * setLength, skipping the test where the server can't do it (some SFTP
	 * servers can't truncate).
	 */
	private static void setLength(IRandomAccessStream ram, long len) throws IOException {
		try {
			ram.setLength(len);
		} catch (UnsupportedOperationException e) {
			assumeFalse(true, "setLength is not supported: "+e);
		} catch (IOException e) {
			assumeFalse("Server does not support this function".equals(e.getMessage()), "setLength is not supported: "+e);
			throw e;
		}
	}

	@Test
	@Order(1)
	public void testCreateFile() throws IOException {
		file = testDir().getChild("RamUnitTest.txt");
		if( file.exists()) {
			assertTrue(file.delete(),"Could not delete exesting file");
		}

		assertThrows(IOException.class, ()-> file.getRandomAccessStream("r").close(),
				"opening a missing file for read should fail");

		StringBuilder buf = new StringBuilder();

		try(IRandomAccessStream ram = file.getRandomAccessStream("rw")) {
			assertTrue(file.exists(),"should create a new file when opening a non existed file for rw");
			while( ram.length() < targetFileSize) {
				ram.write(testData);
				buf.append(testDataString);
			}
		}
		file.refresh();

		byte buffer [] = new byte[(int)file.length()];
		try(IRandomAccessStream ram = file.getRandomAccessStream("r")) {
			ram.readFully(buffer);
		}

		assertEquals(buf.toString(), new String(buffer),"Did not read the expected value");
	}

	@Test
	@Order(2)
	public void testSeekAndRead() throws IOException {
		int len = (int)file.length();

		try(IRandomAccessStream ram = file.getRandomAccessStream("rw")) {
			for(int idx = 0; idx<len; idx++) {
				int pos = idx % testData.length;
				int expect = testData[pos];
				ram.seek(idx);
				int actual = ram.read();

				assertEquals(expect, actual,"Sequential forward seek read the wrong value after seek to "+idx+" pos="+pos);
			}

			for(int idx = len-1; idx>=0; idx--) {
				int pos = idx % testData.length;
				int expect = testData[pos];
				ram.seek(idx);
				int actual = ram.read();
				assertEquals(expect, actual,"Sequential reverese seek read the wrong value after seek to "+idx+" pos="+pos);
			}

		}

		try(IRandomAccessStream ram = file.getRandomAccessStream("rw")) {
			Random r = new Random();
			for(int tries = 0; tries < 1000; tries++) {
				int idx = r.nextInt(len);
				int pos = idx % testData.length;
				int expect = testData[pos];
				ram.seek(idx);
				int actual = ram.read();
				assertEquals(expect, actual,"Read the wrong value after seek to "+idx+" pos="+pos+" tries="+tries);
			}
		}
	}


	@Test
	@Order(3)
	public void testSetLength() throws IOException {
		int len = (int)file.length();
		assertTrue(len > 0," Test file is empty" );

		try(IRandomAccessStream ram = file.getRandomAccessStream("rw")) {
			setLength(ram, len*2);
			assertEquals(len*2,file.length(),"Wrong value set len to *2" );
			setLength(ram, len);
			assertEquals(len,file.length(),"Wrong value set len to len" );
			setLength(ram, len/2);
			assertEquals(len/2,file.length(),"Wrong value set len to 1/2" );
		}
	}

	@Test
	@Order(4)
	public void compareWithJava() throws IOException {
		File jdir = new File("target").getAbsoluteFile();
		jdir.mkdirs();
		File jfile = new File(jdir,getClass().getSimpleName()+"-RamFile.txt");
		if( jfile.exists()) {
			assertTrue(jfile.delete(),"Can't delet existing java file "+jfile);
		}

		if( file.exists()) {
			assertTrue(file.delete(),"Can't delet existing file "+file);
		}

		// make both file the same
		try(RandomAccessFile jram = new RandomAccessFile(jfile, "rw")){
			try(IRandomAccessStream ram = file.getRandomAccessStream("rw")) {
				int cnt = 0;
				while(cnt < targetFileSize){
					jram.write(testData);
					ram.write(testData);
					cnt+= testData.length;
				}

			}
		}

		try {
			testAndCompare(file,jfile);
		} finally {
			assertTrue(jfile.delete(),"Can't delet existing java file "+jfile);
		}
	}

	private void testAndCompare(FileSource file, File jfile) throws IOException {

		Random r = new Random();
		byte [] data = new byte[testData.length];
		byte [] jdata = new byte[testData.length];
		List<Entry> list = new ArrayList<>();

		try(RandomAccessFile jram = new RandomAccessFile(jfile, "rw")){
			try(IRandomAccessStream ram = file.getRandomAccessStream("rw")) {

				int actions = compareActions;
				while((--actions) >= 0) {
					int ai = r.nextInt(Action.values().length);
					Action action = Action.values()[ai];
					Entry entry = new Entry(action);

					list.add(entry);
					entry.jpointerBefore = jram.getFilePointer();
					entry.pointerBefore = ram.getFilePointer();

					switch(action) {
					case Read:
						int ji = jram.read(jdata);
						int i  = ram.read(data);
						assertEquals(ji, i,"Read count not the same actions="+list);
						for (int idx = 0; idx < jdata.length; idx++) {
							assertEquals(jdata[idx], data[idx],"Data wrong at idx="+idx+" actions = "+list);
						}

						break;

					case Write:
						jram.write(testData);
						ram.write(testData);
						break;
					case Seek:
						long len = ram.length();
						if( len == 0 ) {
							len = 10;
						}
						long pos = r.nextInt((int)(len*1.5));
						entry.value = pos;
						jram.seek(pos);
						ram.seek(pos);
						break;
					case SetLength:
						len = ram.length();
						if( len == 0 ) {
							len = 10;
						}
						pos = r.nextInt((int)(len*1.5));
						entry.value = pos;
						setLength(ram, pos);
						jram.setLength(pos);
						assertEquals(jram.length(), ram.length(),"Length not the same actions="+list);
						break;
					default:throw new IOException("Unknown action="+action);
					}
					entry.jpointerAfter = jram.getFilePointer();
					entry.pointerAfter = ram.getFilePointer();

					assertEquals(jram.getFilePointer(), ram.getFilePointer(),"Filepointer not the same actions="+list);

				}
				assertEquals(jram.length(), ram.length(),"Length not the same actions="+list);
			}

		}
	}
}
