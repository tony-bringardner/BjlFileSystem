package us.bringardner.io.filesource.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Arrays;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.fileproxy.FileProxy;

/**
 * Regression tests for FileSource.head() / tail() (review items #7 and #8).
 * The file is wrapped so its stream returns short reads and short skips,
 * as network streams often do.
 */
public class HeadTailTests {

	private static File dir;
	private static byte[] content;

	@BeforeAll
	public static void setup() throws IOException {
		dir = Files.createTempDirectory("headtail").toFile();
		content = new byte[1000];
		for (int i = 0; i < content.length; i++) {
			content[i] = (byte) i;
		}
	}

	@AfterAll
	public static void teardown() {
		File[] kids = dir.listFiles();
		if (kids != null) {
			for (File f : kids) {
				f.delete();
			}
		}
		dir.delete();
	}

	/** A local file whose streams read at most 7 bytes and skip at most 5 per call. */
	private static FileSource stingyFile(String name, byte[] data) throws IOException {
		FileSource f = new FileProxy(new File(dir, name), FileSourceFactory.getDefaultFactory()) {
			private static final long serialVersionUID = 1L;

			@Override
			public InputStream getInputStream() throws IOException {
				return new FilterInputStream(super.getInputStream()) {
					@Override
					public int read(byte[] b, int off, int len) throws IOException {
						return super.read(b, off, Math.min(len, 7));
					}

					@Override
					public long skip(long n) throws IOException {
						return super.skip(Math.min(n, 5));
					}
				};
			}
		};
		try (OutputStream out = f.getOutputStream()) {
			out.write(data);
		}
		return f;
	}

	/** #7: head() threw IndexOutOfBoundsException after a short read. */
	@Test
	public void headHandlesShortReads() throws IOException {
		FileSource f = stingyFile("head.bin", content);
		assertArrayEquals(Arrays.copyOf(content, 100), f.head(100));
	}

	@Test
	public void headOfShortFileReturnsWholeFile() throws IOException {
		FileSource f = stingyFile("head-short.bin", new byte[] { 1, 2, 3 });
		assertArrayEquals(new byte[] { 1, 2, 3 }, f.head(100));
		assertArrayEquals(new byte[0], f.head(0));
	}

	/** #8: tail() ignored short skips and so returned the wrong bytes. */
	@Test
	public void tailHandlesShortSkipsAndReads() throws IOException {
		FileSource f = stingyFile("tail.bin", content);
		assertArrayEquals(Arrays.copyOfRange(content, 900, 1000), f.tail(100));
	}

	/** #8: tail() of a file shorter than 'size' padded the result with zeros. */
	@Test
	public void tailOfShortFileReturnsWholeFileWithoutPadding() throws IOException {
		FileSource f = stingyFile("tail-short.bin", "0123456789".getBytes("UTF-8"));
		assertArrayEquals("0123456789".getBytes("UTF-8"), f.tail(100));
		assertArrayEquals("789".getBytes("UTF-8"), f.tail(3));
	}
}
