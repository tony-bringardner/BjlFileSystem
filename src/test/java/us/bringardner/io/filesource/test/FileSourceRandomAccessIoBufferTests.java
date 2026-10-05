package us.bringardner.io.filesource.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.IRandomAccessIoController;


/**
 * A factory's IRandomAccessIoController, the layer under
 * FileSourceRandomAccessStream. A subclass sets the fields of
 * FileSourceTestSupport in its @BeforeAll and creates the controller.
 */
@TestMethodOrder(OrderAnnotation.class)
public abstract class FileSourceRandomAccessIoBufferTests extends FileSourceTestSupport {

	protected static long targetFileSize=1000;
	protected static String testDataString = "0123456789";
	protected static byte [] testData = testDataString.getBytes();
	private static FileSource file;
	private static FileSource testDir;

	/** A read/write controller for file. */
	protected abstract IRandomAccessIoController getRandomAccessFileStream(FileSource file) throws IOException;

	private static FileSource testDir() throws IOException {
		if( testDir == null ) {
			testDir = factory.createFileSource(remoteTestFileDirPath).getChild("RandomAccessIoBuffer");
			if( !testDir.exists()) {
				assertTrue(testDir.mkdirs(),"Can't create "+testDir);
			}
		}
		return testDir;
	}

	@AfterAll
	public static void deleteIoBufferTestDir() throws IOException {
		if( testDir != null ) {
			deleteAllAndEmptyParent(testDir);
		}
		testDir = null;
		file = null;
	}

	@Test
	@Order(1)
	public void testCreateFile() throws IOException {
		file = testDir().getChild("RamIoBuffer.txt");
		int cnt = 0;

		try(OutputStream out = file.getOutputStream()) {
			while( cnt < targetFileSize) {
				out.write(testData);
				cnt+=testData.length;
			}
		}

	}

	@Test
	@Order(2)
	public void testSeekAndRead() throws IOException {
		long len = file.length();
		try(IRandomAccessIoController buf = getRandomAccessFileStream(file)){
			assertEquals(len, buf.length(),"Start lengths do not match");
			// forward
			for(long pointer = 0; pointer < len; pointer++) {
				int expect = testData[(int)(pointer % testData.length)];
				int i = buf.read(pointer);
				assertEquals((char)expect, (char)i,"Read not correct pointer="+pointer);
			}
			// backward
			for(long pointer = len-1; pointer >= 0; pointer--) {
				int expect = testData[(int)(pointer % testData.length)];
				int i = buf.read(pointer);
				assertEquals((char)expect, (char)i,"Backward read not correct pointer="+pointer);
			}
		} catch (IOException e) {
			throw e;
		} catch (Exception e) {
			throw new IOException(e);
		}
		//  do some random reads
		Random r = new Random();
		try(IRandomAccessIoController buf = getRandomAccessFileStream(file)){
			for(int tries = 0; tries < 40; tries++) {
				long pos = r.nextInt((int)len);
				int expect = testData[((int)pos) % testData.length];
				int i = buf.read(pos);
				assertEquals((char)expect, (char)i,"Read not correct pos="+pos);
			}
		} catch (IOException e) {
			throw e;
		} catch (Exception e) {
			throw new IOException(e);
		}
	}

	@Test
	@Order(3)
	public void testSeekAndWrite() throws IOException {
		long len = file.length();
		Random r = new Random();
		Map<Long,Integer> changes = new HashMap<>();
		byte data [] = "abcdefghij".getBytes();

		try(IRandomAccessIoController buf = getRandomAccessFileStream(file)){
			for(int tries = 0; tries < 40; tries++) {
				long pos = r.nextInt((int)len);
				int value = data[((int)pos) % data.length];
				buf.write(pos, (byte) value);
				assertEquals((char)value, (char)buf.read(pos),"Read after write not correct pos="+pos);
				changes.put(pos, value);
			}
		} catch (IOException e) {
			throw e;
		} catch (Exception e) {
			throw new IOException(e);
		}

		try(IRandomAccessIoController buf = getRandomAccessFileStream(file)){
			for(long pos = 0; pos < len; pos++) {
				Integer changed = changes.get(pos);
				int expect = changed != null ? changed : testData[((int)pos) % testData.length];
				int i = buf.read(pos);
				assertEquals((char)expect, (char)i,(changed != null ? "Changed":"Unchanged")+" read not correct pos="+pos);
			}
		} catch (IOException e) {
			throw e;
		} catch (Exception e) {
			throw new IOException(e);
		}
	}

	@Test
	@Order(4)
	public void testWritePastEnd() throws IOException {
		long len = file.length();

		try(IRandomAccessIoController buf = getRandomAccessFileStream(file)){
			long blen = buf.length();
			assertEquals(len, blen,"Starting lengths do not match");
			buf.write(blen+10,(byte) 'x');
			blen = buf.length();
			assertEquals(len+11, blen,"Add 10 lengths do not match");
			buf.save();
			long len2 = file.length();
			assertEquals(blen,len2,"Add 10 file lengths do not match");

		} catch (IOException e) {
			throw e;
		} catch (Exception e) {
			throw new IOException(e);
		}

		 len = file.length();

		 try(IRandomAccessIoController buf = getRandomAccessFileStream(file)){
			buf.setLength(len+150);
			long len2 = file.length();
			assertEquals(len+150,len2,"set len +150 file lengths do not match");

			buf.setLength(len+10);
			long len3 = file.length();
			assertEquals(len+10,len3,"set back to len file lengths do not match");

		} catch (IOException e) {
			throw e;
		} catch (Exception e) {
			throw new IOException(e);
		}
	}

}
