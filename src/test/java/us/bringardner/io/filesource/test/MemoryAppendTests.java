package us.bringardner.io.filesource.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.junit.jupiter.api.Test;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.memory.MemoryFileSourceFactory;

/** Memory getOutputStream(true) after the copy-loop cleanup (review P6). */
public class MemoryAppendTests {

	@Test
	public void appendKeepsExistingBytes() throws IOException {
		MemoryFileSourceFactory factory = new MemoryFileSourceFactory();
		factory.connect();
		FileSource f = factory.createFileSource("/append.bin");
		try (OutputStream out = f.getOutputStream(true)) {
			out.write(new byte[] { 1, 2, (byte) 0xFF });
		}
		try (OutputStream out = f.getOutputStream(true)) {
			out.write(new byte[] { 4, 5 });
		}
		try (OutputStream out = f.getOutputStream(true)) {
			// nothing appended
		}
		try (InputStream in = f.getInputStream()) {
			assertArrayEquals(new byte[] { 1, 2, (byte) 0xFF, 4, 5 }, in.readAllBytes());
		}
		assertEquals(5, f.length());
	}
}
