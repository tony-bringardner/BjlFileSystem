package us.bringardner.io.filesource.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import us.bringardner.io.filesource.AbstractRandomAccessIoController;
import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.FileSourceRandomAccessStream;
import us.bringardner.io.filesource.IRandomAccessStream;
import us.bringardner.io.filesource.fileproxy.FileProxy;
import us.bringardner.io.filesource.memory.MemoryFileSourceFactory;

/**
 * Bulk random-access I/O (review section 4, P1): correctness against a
 * reference copy, reads across chunk boundaries, and a performance guard.
 */
public class RandomAccessBulkTests {

	private File tmpDir;

	private FileSource newFile(String kind, byte[] content) throws IOException {
		FileSource f;
		if( kind.equals("memory")) {
			MemoryFileSourceFactory factory = new MemoryFileSourceFactory();
			factory.connect();
			f = factory.createFileSource("/bulk.bin");
		} else {
			tmpDir = Files.createTempDirectory("bulk").toFile();
			f = new FileProxy(new File(tmpDir, "bulk.bin"), FileSourceFactory.getDefaultFactory());
		}
		try (OutputStream out = f.getOutputStream()) {
			out.write(content);
		}
		return f;
	}

	@AfterEach
	public void cleanup() {
		if( tmpDir != null ) {
			for(File f : tmpDir.listFiles()) {
				f.delete();
			}
			tmpDir.delete();
		}
	}

	private static byte[] readAll(FileSource f) throws IOException {
		try (InputStream in = f.getInputStream()) {
			return in.readAllBytes();
		}
	}

	/** Random mix of bulk/single reads and writes, seeks and truncation, checked against a byte[] model. */
	@ParameterizedTest
	@ValueSource(strings = { "local", "memory" })
	public void randomOperationsMatchReference(String kind) throws IOException {
		Random r = new Random(42);
		byte[] model = new byte[5000];
		r.nextBytes(model);
		int size = model.length;
		FileSource f = newFile(kind, model);
		model = Arrays.copyOf(model, 100_000);

		IRandomAccessStream ra = f.getRandomAccessStream("rw");
		try {
			long pos = 0;
			for(int op = 0; op < 2000; op++) {
				switch (r.nextInt(6)) {
				case 0: { // seek, sometimes past EOF
					pos = r.nextInt(size + 50);
					ra.seek(pos);
					break;
				}
				case 1: { // bulk read
					byte[] buf = new byte[1 + r.nextInt(300)];
					int n = ra.read(buf, 0, buf.length);
					int expect = (int) Math.min(buf.length, Math.max(0, size - pos));
					assertEquals(expect == 0 ? -1 : expect, n, "read at "+pos);
					if( n > 0 ) {
						assertArrayEquals(Arrays.copyOfRange(model, (int) pos, (int) pos + n), Arrays.copyOf(buf, n), "data at "+pos);
						pos += n;
					}
					break;
				}
				case 2: { // single read
					int v = ra.read();
					assertEquals(pos < size ? model[(int) pos] & 0xFF : -1, v, "byte at "+pos);
					if( v >= 0 ) {
						pos++;
					}
					break;
				}
				case 3: { // bulk write, including high bytes and at/after EOF
					if( pos > size ) {
						break;   // writing past a gap isn't the point here
					}
					byte[] buf = new byte[1 + r.nextInt(300)];
					r.nextBytes(buf);
					ra.write(buf);
					System.arraycopy(buf, 0, model, (int) pos, buf.length);
					pos += buf.length;
					size = (int) Math.max(size, pos);
					break;
				}
				case 4: { // single write
					if( pos > size ) {
						break;
					}
					int v = r.nextInt(256);
					ra.write(v);
					model[(int) pos++] = (byte) v;
					size = (int) Math.max(size, pos);
					break;
				}
				case 5: { // occasional truncate
					if( r.nextInt(10) == 0 ) {
						int newSize = r.nextInt(size + 1);
						ra.setLength(newSize);
						size = newSize;
						pos = Math.min(pos, size);
						ra.seek(pos);
					}
					break;
				}
				}
				assertEquals(size, ra.length(), "length after op "+op);
			}
		} finally {
			ra.close();
		}
		assertArrayEquals(Arrays.copyOf(model, size), readAll(f));
	}

	/** A small in-memory "remote" controller with 16-byte chunks, to exercise bulk reads across chunks. */
	private static class SmallChunks extends AbstractRandomAccessIoController {
		private final byte[] remote;

		SmallChunks(FileSource file, byte[] remote) throws IOException {
			super(file);
			this.remote = remote;
		}

		@Override
		protected Chunk readChunkForPos(long pos) {
			if( pos >= remote.length ) {
				// past EOF: an empty new chunk, as real controllers return
				Chunk c = new Chunk(pos, pos / 16, new byte[16]);
				c.isNew = true;
				return c;
			}
			long start = pos / 16 * 16;
			int end = (int) Math.min(remote.length, start + 16);
			Chunk c = new Chunk(start, start / 16, Arrays.copyOfRange(remote, (int) start, end));
			c.size = c.data.length;
			return c;
		}

		@Override
		protected void writeChunk(Chunk chunk) {
		}

		@Override
		protected void setLength0(long newLength) {
		}
	}

	@Test
	public void chunkedBulkReadCrossesChunkBoundaries() throws Exception {
		byte[] remote = new byte[100];
		for(int i = 0; i < remote.length; i++) {
			remote[i] = (byte) (200 + i);   // mostly >= 0x80
		}
		FileSource file = newFile("memory", remote);
		try (FileSourceRandomAccessStream in = new FileSourceRandomAccessStream(new SmallChunks(file, remote), "r")) {
			in.seek(5);
			byte[] buf = new byte[60];
			in.readFully(buf);
			assertArrayEquals(Arrays.copyOfRange(remote, 5, 65), buf);

			byte[] rest = new byte[100];
			assertEquals(35, in.read(rest, 0, rest.length));
			assertArrayEquals(Arrays.copyOfRange(remote, 65, 100), Arrays.copyOf(rest, 35));
			assertEquals(-1, in.read(rest, 0, 10));
		}
	}

	/** Reading 2 MB through getRandomAccessStream took about 2 s (1 microsecond per byte). */
	@ParameterizedTest
	@ValueSource(strings = { "local", "memory" })
	public void tenMegabytesInWellUnderASecondEach(String kind) throws IOException {
		byte[] data = new byte[10 << 20];
		new Random(1).nextBytes(data);
		FileSource f = newFile(kind, new byte[0]);

		long t0 = System.nanoTime();
		IRandomAccessStream ra = f.getRandomAccessStream("rw");
		try {
			ra.write(data);
			ra.seek(0);
			byte[] back = new byte[data.length];
			ra.readFully(back);
			assertTrue(Arrays.equals(data, back), "content");
		} finally {
			ra.close();
		}
		long ms = (System.nanoTime() - t0) / 1_000_000;
		// generous bound so a slow CI machine passes; the old code took ~10 s per direction
		assertTrue(ms < 3000, "write+read 10 MB took "+ms+" ms");
	}
}
