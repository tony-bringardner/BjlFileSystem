package us.bringardner.io.filesource.test;

import java.io.IOException;
import java.util.Properties;

import org.junit.jupiter.api.BeforeAll;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.IRandomAccessIoController;
import us.bringardner.io.filesource.memory.MemoryFileSource;
import us.bringardner.io.filesource.memory.MemoryFileSourceFactory;
import us.bringardner.io.filesource.memory.MemoryRandomAccessIoController;


public class MemoryRandomAccessIoBufferTests extends FileSourceRandomAccessIoBufferTests {

	@BeforeAll
	public static void setup() throws IOException {
		factory = new MemoryFileSourceFactory();
		remoteTestFileDirPath = "target/UnitTests";

		if(!factory.connect(new Properties())) {
			throw new IOException("Can't connect");
		}
	}

	@Override
	protected IRandomAccessIoController getRandomAccessFileStream(FileSource file) throws IOException {
		return new MemoryRandomAccessIoController((MemoryFileSource) file);
	}
}
