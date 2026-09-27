package us.bringardner.io.filesource.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import us.bringardner.io.filesource.AbstractRandomAccessIoController;
import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceRandomAccessStream;
import us.bringardner.io.filesource.memory.MemoryFileSourceFactory;

/**
 * Regression test: AbstractRandomAccessIoController.read() must return
 * bytes as unsigned values (0..255). A signed return made every byte
 * >= 0x80 look like EOF to FileSourceRandomAccessStream.
 */
public class AbstractRandomAccessIoControllerTests {

	/** Minimal single-chunk controller backed by a byte array. */
	private static class ArrayController extends AbstractRandomAccessIoController {
		private final byte[] data;

		ArrayController(FileSource file, byte[] data) throws IOException {
			super(file);
			this.data = data;
		}

		@Override
		protected Chunk readChunkForPos(long pos) {
			Chunk c = new Chunk(0, 1, data.clone());
			c.size = data.length;
			return c;
		}

		@Override
		protected void writeChunk(Chunk chunk) {}

		@Override
		protected void setLength0(long newLength) {}
	}

	private static FileSource newFile(byte[] content) throws IOException {
		MemoryFileSourceFactory factory = new MemoryFileSourceFactory();
		factory.connect();
		FileSource file = factory.createFileSource("/unsigned.bin");
		try (java.io.OutputStream out = file.getOutputStream()) {
			out.write(content);
		}
		return file;
	}

	@Test
	public void readReturnsUnsignedBytes() throws Exception {
		byte[] all = new byte[256];
		for (int i = 0; i < all.length; i++) {
			all[i] = (byte) i;
		}
		FileSource file = newFile(all);
		try (ArrayController io = new ArrayController(file, all)) {
			for (int i = 0; i < all.length; i++) {
				assertEquals(i, io.read(i), "byte at position " + i);
			}
		}
	}

	@Test
	public void streamReadsHighBytesWithoutEof() throws Exception {
		byte[] data = { (byte) 0xFF, (byte) 0x80, (byte) 0xC8, 0x05 };
		FileSource file = newFile(data);
		try (FileSourceRandomAccessStream in = new FileSourceRandomAccessStream(new ArrayController(file, data), "r")) {
			byte[] got = new byte[data.length];
			in.readFully(got);
			assertArrayEquals(data, got);
		}
	}
}
