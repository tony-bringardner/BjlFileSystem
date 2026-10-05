package us.bringardner.io.filesource.test;

import java.io.IOException;
import java.util.Properties;

import org.junit.jupiter.api.BeforeAll;

import us.bringardner.io.filesource.memory.MemoryFileSourceFactory;


public class MemoryRandomAccessStreamTests extends AbstractRandomAccessStreamTests {

	@BeforeAll
	public static void setup() throws IOException {
		factory = new MemoryFileSourceFactory();
		remoteTestFileDirPath = "target/RandomAccessStreamTests";

		if(!factory.connect(new Properties())) {
			throw new IOException("Can't connect");
		}
	}
}
