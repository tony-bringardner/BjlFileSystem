package us.bringardner.io.filesource.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.Test;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.ISeekableInputStream;
import us.bringardner.io.filesource.fileproxy.FileProxy;

/**
 * Review section 4: P2 (FileProxy seekable stream's InputStream read one
 * byte per system call) and P3 (debug output on every chunk load / URL open).
 */
public class SeekableAndQuietTests {

	private static FileSource localFile(byte[] content) throws IOException {
		File f = File.createTempFile("seek", ".bin");
		f.deleteOnExit();
		Files.write(f.toPath(), content);
		return new FileProxy(f, FileSourceFactory.getDefaultFactory());
	}

	@Test
	public void fileProxySeekableInputStreamBulkReadSkipAvailable() throws IOException {
		byte[] data = new byte[1000];
		new Random(7).nextBytes(data);
		ISeekableInputStream s = localFile(data).getSeekableInputStream();
		s.seek(100);
		InputStream in = s.getInputStream();
		try {
			assertEquals(900, in.available());
			byte[] buf = new byte[50];
			assertEquals(50, in.read(buf));
			assertArrayEquals(Arrays.copyOfRange(data, 100, 150), buf);
			assertEquals(150, s.getFilePointer(), "position is shared");
			assertEquals(50, in.skip(50));
			assertEquals(data[200] & 0xFF, in.read());
			assertEquals(799, in.skip(10_000), "skip stops at EOF");
			assertEquals(0, in.available());
			assertEquals(-1, in.read(buf));
		} finally {
			in.close();
		}
	}

	@Test
	public void fileProxySeekableInputStreamIsFast() throws IOException {
		byte[] data = new byte[10 << 20];
		FileSource f = localFile(data);
		long t0 = System.nanoTime();
		try (InputStream in = f.getSeekableInputStream().getInputStream()) {
			byte[] buf = new byte[8192];
			long total = 0;
			int n;
			while( (n = in.read(buf)) > 0 ) {
				total += n;
			}
			assertEquals(data.length, total);
		}
		long ms = (System.nanoTime() - t0) / 1_000_000;
		assertTrue(ms < 2000, "10 MB took "+ms+" ms (was ~6 s)");
	}

	/** Opening a filesource: URL printed "url=..." and chunk loads printed blank lines. */
	@Test
	public void noOutputOnSystemOut() throws Exception {
		FileSource f = localFile("quiet".getBytes("UTF-8"));
		URL url = new URL(FileSourceFactory.FILE_SOURCE_PROTOCOL+":"+f.getAbsolutePath()
				+"?"+FileSourceFactory.QUERY_STRING_SOURCE_TYPE+"=fileproxy");

		PrintStream original = System.out;
		ByteArrayOutputStream captured = new ByteArrayOutputStream();
		System.setOut(new PrintStream(captured, true, "UTF-8"));
		try {
			try (InputStream in = url.openStream()) {
				assertEquals("quiet", new String(in.readAllBytes(), "UTF-8"));
			}
		} finally {
			System.setOut(original);
		}
		assertEquals("", captured.toString("UTF-8"));
	}
}
