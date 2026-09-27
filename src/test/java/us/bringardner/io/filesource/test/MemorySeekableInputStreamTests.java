package us.bringardner.io.filesource.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.ISeekableInputStream;
import us.bringardner.io.filesource.memory.MemoryFileSourceFactory;

/**
 * Regression tests for MemoryFileSource's seekable stream:
 * - read() must return unsigned values (0..255); a signed return made 0xFF
 *   read as -1 (EOF) and other bytes >= 0x80 read as negative numbers.
 * - read(byte[],off,len) must return the number of bytes read, or -1 at EOF
 *   (it always returned 0, so read-until-EOF loops never ended).
 * - seek() must allow positioning at the end, like RandomAccessFile.
 * - getInputStream() must start at the current file pointer (it used to
 *   start at the beginning of the file).
 */
public class MemorySeekableInputStreamTests {

	private static byte[] allBytes() {
		byte[] all = new byte[256];
		for (int i = 0; i < all.length; i++) {
			all[i] = (byte) i;
		}
		return all;
	}

	private static ISeekableInputStream open(byte[] content) throws IOException {
		MemoryFileSourceFactory factory = new MemoryFileSourceFactory();
		factory.connect();
		FileSource file = factory.createFileSource("/seekable.bin");
		try (OutputStream out = file.getOutputStream()) {
			out.write(content);
		}
		return file.getSeekableInputStream();
	}

	@Test
	public void readReturnsUnsignedBytes() throws IOException {
		byte[] all = allBytes();
		ISeekableInputStream in = open(all);
		try {
			for (int i = 0; i < all.length; i++) {
				assertEquals(i, in.read(), "byte at position " + i);
			}
			assertEquals(-1, in.read(), "EOF after last byte");

			in.seek(0xFF);
			assertEquals(0xFF, in.read(), "0xFF after seek");
		} finally {
			in.close();
		}
	}

	/** Bulk reads must return the byte count, then -1 at EOF (previously always returned 0). */
	@Test
	public void bulkReadReturnsCountThenEof() throws IOException {
		byte[] all = allBytes();
		ISeekableInputStream in = open(all);
		try {
			byte[] buf = new byte[100];
			assertEquals(100, in.read(buf), "first read");
			assertArrayEquals(Arrays.copyOfRange(all, 0, 100), buf);
			assertEquals(100, in.read(buf, 0, 100), "second read");
			assertEquals(56, in.read(buf, 0, 100), "short read at end");
			assertArrayEquals(Arrays.copyOfRange(all, 200, 256), Arrays.copyOf(buf, 56));
			assertEquals(-1, in.read(buf), "EOF");
			assertEquals(0, in.read(buf, 0, 0), "zero-length read");
		} finally {
			in.close();
		}
	}

	/** Must honour offset and length, and never write outside [off, off+len). */
	@Test
	public void bulkReadRespectsOffsetAndLength() throws IOException {
		ISeekableInputStream in = open(new byte[] { 1, 2, 3, 4, 5 });
		try {
			byte[] buf = new byte[10];
			Arrays.fill(buf, (byte) 9);
			assertEquals(3, in.read(buf, 4, 3));
			assertArrayEquals(new byte[] { 9, 9, 9, 9, 1, 2, 3, 9, 9, 9 }, buf);
			assertEquals(3, in.getFilePointer(), "pointer advanced by bytes read");
			assertThrows(IndexOutOfBoundsException.class, () -> in.read(buf, 8, 5));
		} finally {
			in.close();
		}
	}

	/** Seeking to (or past) the end is allowed and reads return EOF, like RandomAccessFile. */
	@Test
	public void seekToEndGivesEof() throws IOException {
		byte[] all = allBytes();
		ISeekableInputStream in = open(all);
		try {
			in.seek(all.length);
			assertEquals(all.length, in.getFilePointer(), "pointer at end");
			assertEquals(-1, in.read(), "single read at end");
			assertEquals(-1, in.read(new byte[4]), "bulk read at end");

			in.seek(all.length + 1000);
			assertEquals(-1, in.read(), "read past end");

			in.seek(250);
			byte[] buf = new byte[10];
			assertEquals(6, in.read(buf), "partial read after seek");

			assertThrows(IOException.class, () -> in.seek(-1));
		} finally {
			in.close();
		}
	}
	/** getInputStream() reads from the current pointer and shares it with the seekable stream. */
	@Test
	public void inputStreamStartsAtFilePointer() throws IOException {
		byte[] all = allBytes();
		ISeekableInputStream seekable = open(all);
		seekable.seek(200);
		InputStream in = seekable.getInputStream();
		try {
			assertEquals(56, in.available(), "available from pointer");
			assertEquals(200, in.read(), "first byte comes from the pointer");
			assertEquals(201, seekable.getFilePointer(), "pointer is shared");

			assertEquals(9, in.skip(9), "skip");
			byte[] buf = new byte[100];
			assertEquals(46, in.read(buf), "rest of file");
			assertEquals(210, buf[0] & 0xFF, "data after skip");
			assertEquals(-1, in.read(), "EOF");
			assertEquals(0, in.skip(5), "skip at EOF");
		} finally {
			in.close();
		}
		assertEquals(-1, seekable.read(), "closing the view closes the stream");
		assertEquals(0, in.skip(5), "skip after close");
	}
}
