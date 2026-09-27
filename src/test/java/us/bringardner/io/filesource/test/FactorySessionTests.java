package us.bringardner.io.filesource.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;

import org.junit.jupiter.api.Test;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.fileproxy.FileProxyFactory;
import us.bringardner.io.filesource.memory.MemoryFileSourceFactory;

/**
 * Regression tests for the factory session registry, which used to gain an
 * entry on every connect() and never lose one (review section 2).
 */
public class FactorySessionTests {

	@Test
	public void reconnectingSameFactoryReusesItsSession() throws IOException {
		FileProxyFactory factory = new FileProxyFactory();
		assertTrue(factory.connect());
		int id = factory.getSessionId();
		int count = FileSourceFactory.getActiveSessionCount();

		for (int i = 0; i < 1000; i++) {
			assertTrue(factory.connect());   // FileProxy reconnects every time
		}

		assertEquals(id, factory.getSessionId(), "same session id");
		assertEquals(count, FileSourceFactory.getActiveSessionCount(), "no new sessions");
	}

	@Test
	public void disconnectRemovesSession() throws IOException {
		MemoryFileSourceFactory factory = new MemoryFileSourceFactory();
		factory.connect();
		int count = FileSourceFactory.getActiveSessionCount();
		assertNotEquals(-1, factory.getSessionId());

		factory.disConnect();

		assertEquals(-1, factory.getSessionId());
		assertEquals(count - 1, FileSourceFactory.getActiveSessionCount());
	}

	@Test
	public void sessionStillResolvesWhileFactoryIsInUse() throws IOException {
		MemoryFileSourceFactory factory = new MemoryFileSourceFactory();
		factory.connect();
		FileSource f = factory.createFileSource("/s.txt");
		try (OutputStream out = f.getOutputStream()) {
			out.write(1);
		}
		URL url = f.toURL();

		gc();

		assertEquals(1, FileSourceFactory.getFileSource(url).length(), "same file system via "+url);
	}

	/** Each FileSourceFactory.getFileSource(URL) used to leak one session forever. */
	@Test
	public void urlLookupsDoNotAccumulateSessions() throws Exception {
		File tmp = File.createTempFile("sessions", ".txt");
		tmp.deleteOnExit();
		String url = FileSourceFactory.FILE_SOURCE_PROTOCOL+":"+tmp.getAbsolutePath()
				+"?"+FileSourceFactory.QUERY_STRING_SOURCE_TYPE+"=fileproxy";

		int before = FileSourceFactory.getActiveSessionCount();
		for (int i = 0; i < 2000; i++) {
			FileSourceFactory.getFileSource(url);
		}

		int after = before + 2000;
		for (int i = 0; i < 20 && after > before + 50; i++) {
			gc();
			after = FileSourceFactory.getActiveSessionCount();
		}
		assertTrue(after <= before + 50, "sessions before="+before+" after="+after);
	}

	private static void gc() {
		System.gc();
		try {
			Thread.sleep(50);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
