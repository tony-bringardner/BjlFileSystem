package us.bringardner.io.filesource.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import us.bringardner.io.filesource.IRandomAccessIoController;
import us.bringardner.io.filesource.fileproxy.FileProxyFactory;


@TestMethodOrder(OrderAnnotation.class)
public class FileProxyRandomAccessIoBufferTests extends FileSourceRandomAccessIoBufferTests {


	@BeforeAll
	public static void setup() throws IOException {
		factory = new FileProxyFactory();

		Properties prop = new Properties();

		if(!factory.connect(prop)) {
			throw new IOException("Can't connect");
		}

		
		testDir = factory.createFileSource("target/UnitTests");
		if( !testDir.exists()) {
			assertTrue(testDir.mkdirs(),"Can't create test dir");
		}

	}

	@AfterAll
	public static void teardown() throws IOException {
		factory.disConnect();
	}

	@Test
	@Order(1)
	public void testCreateFile() throws IOException {
		file = testDir.getChild("RamIoBuffer.txt");
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
		long pointer = 0;
		long len = file.length();
		try(IRandomAccessIoController buf = getRandomAccessFileStream(file)){
			while( pointer < len) {
				int idx = ((int)pointer) % testData.length;
				int expect = testData[idx];
				int i = buf.read(pointer++);
				assertEquals((char)expect, (char)i,"Read not correct pointer="+pointer);
			}
		} catch (Exception e) {
			throw new IOException(e);
		}
		//  do some random reads
		Random r = new Random();
		int tries = 0;
		int doTries = 40;
		try(IRandomAccessIoController buf = getRandomAccessFileStream(file)){
			while(tries < doTries) {
				long pos = r.nextLong(len);
				int idx = ((int)pos) % testData.length;
				int expect = testData[idx];
				int i = buf.read(pos);
				assertEquals((char)expect, (char)i,"Read not correct pos="+pos);
				tries ++;
			}
		} catch (Exception e) {
			throw new IOException(e);
		}
		//System.out.println("testSeekAndRead  Done");
	}


	

	@Test
	@Order(3)
	public void testSeekAndWrite() throws IOException {
		long len = file.length();
		Random r = new Random();
		Map<Long,Integer> changes = new HashMap<>();
		byte data [] = "abcdefghij".getBytes();
		
		int tries = 0;
		int doTries = 40;
		try(IRandomAccessIoController buf = getRandomAccessFileStream(file)){
			while(tries < doTries) {
				long pos = r.nextLong(len);
				int idx = ((int)pos) % data.length;
				int expect = testData[idx];
				buf.write(pos, (byte) expect);
				changes.put(pos, expect);
				tries ++;
			}
		} catch (Exception e) {
			throw new IOException(e);
		}
		
		try(IRandomAccessIoController buf = getRandomAccessFileStream(file)){
			for(long pos : changes.keySet()) {
				int idx = ((int)pos) % data.length;
				int expect = testData[idx];
				int i = buf.read(pos);
				assertEquals((char)expect, (char)i,"Changed Read not correct pos="+pos);				
			}
		} catch (Exception e) {
			throw new IOException(e);
		}
		

		//System.out.println("done with testSeekAndWrite");
		
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
			
		} catch (Exception e) {
			throw new IOException(e);
		}
	
		
		//System.out.println("done with testWritePastEnd");
		
	}
	
}
