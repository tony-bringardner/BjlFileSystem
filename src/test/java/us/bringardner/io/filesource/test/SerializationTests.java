package us.bringardner.io.filesource.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.fileproxy.FileProxy;
import us.bringardner.io.filesource.memory.MemoryFileSourceFactory;

/**
 * FileSource extends Serializable, but serializing a FileProxy threw
 * NotSerializableException (review section 3, "Serialization").
 */
public class SerializationTests {

	@SuppressWarnings("unchecked")
	private static <T> T roundTrip(T obj) throws IOException, ClassNotFoundException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
			out.writeObject(obj);
		}
		try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
			return (T) in.readObject();
		}
	}

	@Test
	public void fileProxyRoundTrip() throws Exception {
		File f = File.createTempFile("ser", ".txt");
		f.deleteOnExit();
		Files.write(f.toPath(), "hello".getBytes("UTF-8"));
		FileSource original = new FileProxy(f, FileSourceFactory.getDefaultFactory());
		original.getOwner();          // populate the cached principals first
		original.getGroup();
		original.canGroupRead();

		FileSource copy = roundTrip(original);

		assertEquals(original, copy);
		assertTrue(copy.isFile());
		assertEquals(5, copy.length());
		assertTrue(copy.canRead());
		assertNotNull(copy.getOwner());
		assertEquals(original.canGroupRead(), copy.canGroupRead());
		try (InputStream in = copy.getInputStream()) {
			assertEquals("hello", new String(in.readAllBytes(), "UTF-8"));
		}
	}

	@Test
	public void memoryFileSourceRoundTrip() throws Exception {
		MemoryFileSourceFactory factory = new MemoryFileSourceFactory();
		factory.connect();
		assertTrue(factory.createFileSource("/dir").mkdirs());
		FileSource f = factory.createFileSource("/dir/a.txt");
		try (OutputStream out = f.getOutputStream()) {
			out.write("memory".getBytes("UTF-8"));
		}
		f.getOwner();
		factory.createFileSource("/dir/lookup-only");   // a weakly held placeholder
		FileSource link = factory.createSymbolicLink(factory.createFileSource("/dir/link"), f);

		FileSource copy = roundTrip(f);
		assertEquals("/dir/a.txt", copy.getAbsolutePath());
		try (InputStream in = copy.getInputStream()) {
			assertEquals("memory", new String(in.readAllBytes(), "UTF-8"));
		}
		assertEquals(f.getOwner().getName(), copy.getOwner().getName());

		FileSource linkCopy = roundTrip(link);
		assertEquals(6, linkCopy.length());
	}
}
