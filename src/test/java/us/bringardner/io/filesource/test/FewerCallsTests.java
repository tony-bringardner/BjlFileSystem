package us.bringardner.io.filesource.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import us.bringardner.io.filesource.AbstractRandomAccessIoController;
import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.FileSourceGroup;
import us.bringardner.io.filesource.FileSourceUser;
import us.bringardner.io.filesource.memory.MemoryFileSource;
import us.bringardner.io.filesource.memory.MemoryFileSourceFactory;

/**
 * Review section 4, P4: fewer calls per operation.
 */
public class FewerCallsTests {

	/** A memory file that counts refresh() and length() calls, standing in for a remote file. */
	private static class CountingFile extends MemoryFileSource {
		private static final long serialVersionUID = 1L;
		final AtomicInteger refreshes = new AtomicInteger();
		long len = 100;

		CountingFile(MemoryFileSourceFactory f) {
			super(null, "counting", f);
		}

		@Override
		public void refresh() {
			refreshes.incrementAndGet();
		}

		@Override
		public long length() {
			return len;
		}
	}

	private static class Controller extends AbstractRandomAccessIoController {
		Controller(FileSource f) throws IOException {
			super(f);
		}

		@Override
		protected Chunk readChunkForPos(long pos) {
			Chunk c = new Chunk(0, 0, new byte[100]);
			c.size = 100;
			return c;
		}

		@Override
		protected void writeChunk(Chunk chunk) {
		}

		@Override
		protected void setLength0(long newLength) {
		}
	}

	/** length() refreshed the (possibly remote) file on every call. */
	@Test
	public void chunkedLengthIsNotRefreshedOnEveryCall() throws Exception {
		CountingFile file = new CountingFile(new MemoryFileSourceFactory());
		try (Controller c = new Controller(file)) {
			for (int i = 0; i < 1000; i++) {
				assertEquals(100, c.length());
			}
			assertEquals(1, file.refreshes.get(), "one refresh for 1000 length() calls");

			file.len = 50;
			c.setLength(50);   // the controller changed it: re-read
			assertEquals(50, c.length());
			assertEquals(2, file.refreshes.get());
		}
	}

	/** Each factory ran `id`/`whoami` on first use; the user is now looked up once, but each factory gets its own copy. */
	@Test
	public void whoAmIIsSharedButNotTheSameObject() {
		FileSourceUser a = new MemoryFileSourceFactory().whoAmI();
		FileSourceUser b = new MemoryFileSourceFactory().whoAmI();
		assertNotNull(a.getName());
		assertEquals(a.getName(), b.getName());
		assertEquals(a.getGroups().keySet(), b.getGroups().keySet());
		assertNotSame(a, b);

		a.setGroup(new FileSourceGroup(424242, "changed-in-a"));
		assertFalse(b.getGroup() != null && "changed-in-a".equals(b.getGroup().getName()), "copies are independent");
	}

	/** getFileSourceFactory iterated the (non-thread-safe) ServiceLoader on every call. */
	@Test
	public void factoryRegistry() throws Exception {
		assertTrue(FileSourceFactory.isRegisteredFactory("memory"));
		assertTrue(FileSourceFactory.isRegisteredFactory(" FileProxy "));
		assertFalse(FileSourceFactory.isRegisteredFactory("no-such-factory"));
		assertTrue(Arrays.asList(FileSourceFactory.getRegisterdFactories()).containsAll(Arrays.asList("fileproxy", "memory")));

		FileSourceFactory m1 = FileSourceFactory.getFileSourceFactory("memory");
		FileSourceFactory m2 = FileSourceFactory.getFileSourceFactory("MEMORY");
		assertTrue(m1 instanceof MemoryFileSourceFactory);
		assertNotSame(m1, m2, "still a new factory per call");

		ExecutorService pool = Executors.newFixedThreadPool(8);
		try {
			List<Future<Integer>> results = new ArrayList<>();
			for (int t = 0; t < 8; t++) {
				results.add(pool.submit(() -> {
					int ok = 0;
					for (int i = 0; i < 500; i++) {
						if( FileSourceFactory.getFileSourceFactory(i % 2 == 0 ? "memory" : "fileproxy") != null ) {
							ok++;
						}
					}
					return ok;
				}));
			}
			for (Future<Integer> f : results) {
				assertEquals(500, f.get());
			}
		} finally {
			pool.shutdown();
		}
	}
}
