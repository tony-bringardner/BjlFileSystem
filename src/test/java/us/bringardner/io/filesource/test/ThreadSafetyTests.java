package us.bringardner.io.filesource.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.fileproxy.FileProxy;
import us.bringardner.io.filesource.memory.MemoryFileSourceFactory;

/**
 * Review section 5, "Thread safety".
 */
public class ThreadSafetyTests {

	private static final int THREADS = 8;

	/** Runs the task on THREADS threads at once and rethrows the first failure. */
	private static void concurrently(Callable<Void> task) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(THREADS);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<Void>> results = new ArrayList<>();
			for (int t = 0; t < THREADS; t++) {
				results.add(pool.submit(() -> {
					start.await();
					return task.call();
				}));
			}
			start.countDown();
			for (Future<Void> f : results) {
				f.get(60, TimeUnit.SECONDS);
			}
		} finally {
			pool.shutdownNow();
		}
	}

	private static String read(FileSource f) throws IOException {
		try (InputStream in = f.getInputStream()) {
			return new String(in.readAllBytes(), "UTF-8");
		}
	}

	/** The memory file system's child maps and data weren't synchronized at all. */
	@Test
	public void memoryFileSystemUnderConcurrentUse() throws Exception {
		MemoryFileSourceFactory factory = new MemoryFileSourceFactory();
		factory.connect();
		FileSource shared = factory.createFileSource("/shared");
		assertTrue(shared.mkdirs());
		java.util.concurrent.atomic.AtomicInteger ids = new java.util.concurrent.atomic.AtomicInteger();

		concurrently(() -> {
			int t = ids.getAndIncrement();
			for (int i = 0; i < 400; i++) {
				String name = "t"+t+"-"+i;
				FileSource f = factory.createFileSource("/shared/"+name);
				try (OutputStream out = f.getOutputStream()) {
					out.write(name.getBytes("UTF-8"));
				}
				assertEquals(name, read(f));
				factory.createFileSource("/shared/missing-"+t+"-"+i).exists();   // lookup-only nodes
				shared.list();
				shared.listFiles();
				if( i % 2 == 1 ) {
					assertTrue(f.delete(), "delete "+name);
				}
				if( i % 50 == 0 ) {
					FileSource moved = factory.createFileSource("/shared/moved-"+name);
					if( i % 2 == 0 ) {
						assertTrue(f.renameTo(moved), "rename "+name);
						assertTrue(moved.renameTo(f), "rename back "+name);
					}
				}
			}
			return null;
		});

		Set<String> expected = new TreeSet<>();
		for (int t = 0; t < THREADS; t++) {
			for (int i = 0; i < 400; i += 2) {
				expected.add("t"+t+"-"+i);
			}
		}
		assertEquals(expected, new TreeSet<>(Arrays.asList(shared.list())));
		for (String name : expected) {
			assertEquals(name, read(factory.createFileSource("/shared/"+name)));
		}
	}

	/** The MIME type map was a plain static HashMap that addMimeType writes to. */
	@Test
	public void mimeTypesUnderConcurrentUse() throws Exception {
		java.util.concurrent.atomic.AtomicInteger ids = new java.util.concurrent.atomic.AtomicInteger();
		concurrently(() -> {
			int t = ids.getAndIncrement();
			for (int i = 0; i < 2000; i++) {
				FileSourceFactory.addMimeType("x"+t+"-"+i, "type/"+t+"-"+i);
				FileSourceFactory.getType("x"+t+"-"+(i/2));
			}
			return null;
		});
		for (int t = 0; t < THREADS; t++) {
			for (int i = 0; i < 2000; i++) {
				assertEquals("type/"+t+"-"+i, FileSourceFactory.getType("x"+t+"-"+i));
			}
		}
		assertEquals("text/html", FileSourceFactory.getType("html"));
		assertEquals(null, FileSourceFactory.getType(null));
	}

	/** setPosixPermision was an unsynchronized read-modify-write, so concurrent updates could be lost. */
	@Test
	public void concurrentPermissionBitsAreNotLost() throws Exception {
		assumeFalse(FileSourceFactory.isWindows(), "POSIX permissions");
		File file = File.createTempFile("perm", ".txt");
		file.deleteOnExit();
		FileProxy f = new FileProxy(file, FileSourceFactory.getDefaultFactory());
		f.setOwnerReadable(true);
		f.setOwnerWritable(true);

		List<Callable<Boolean>> setters = Arrays.asList(
				() -> f.setGroupReadable(true), () -> f.setGroupWritable(true), () -> f.setGroupExecutable(true),
				() -> f.setOtherReadable(true), () -> f.setOtherWritable(true), () -> f.setOtherExecutable(true),
				() -> f.setOwnerExecutable(true), () -> f.setOwnerReadable(true));
		List<Callable<Boolean>> clearers = Arrays.asList(
				() -> f.setGroupReadable(false), () -> f.setGroupWritable(false), () -> f.setGroupExecutable(false),
				() -> f.setOtherReadable(false), () -> f.setOtherWritable(false), () -> f.setOtherExecutable(false),
				() -> f.setOwnerExecutable(false), () -> f.setOwnerReadable(true));
		java.util.concurrent.atomic.AtomicInteger ids = new java.util.concurrent.atomic.AtomicInteger();

		concurrently(() -> {
			int t = ids.getAndIncrement();
			for (int i = 0; i < 300; i++) {
				clearers.get(t).call();
				setters.get(t).call();
			}
			return null;
		});

		Set<PosixFilePermission> perms = Files.getPosixFilePermissions(file.toPath());
		assertEquals(PosixFilePermission.values().length, perms.size(), "all bits set: "+perms);
	}

	private File tmpDir() throws IOException {
		File d = Files.createTempDirectory("rename").toFile();
		d.deleteOnExit();
		return d;
	}

	private static FileProxy local(File f) {
		return new FileProxy(f, FileSourceFactory.getDefaultFactory());
	}

	private static void write(File f, String s) throws IOException {
		Files.write(f.toPath(), s.getBytes("UTF-8"));
		f.deleteOnExit();
	}

	/** renameTo resolved dest.getCanonicalPath(), which followed a symlink and overwrote its target. */
	@Test
	public void fileProxyRenameDoesNotFollowDestinationLinks() throws IOException {
		assumeFalse(FileSourceFactory.isWindows(), "symlinks");
		File dir = tmpDir();
		File a = new File(dir, "a.txt");
		File t = new File(dir, "target.txt");
		File link = new File(dir, "link.txt");
		write(a, "A");
		write(t, "keep me");
		Files.createSymbolicLink(link.toPath(), t.toPath());
		link.deleteOnExit();

		assertFalse(local(a).renameTo(local(link)), "destination exists (it's a link)");
		assertEquals("keep me", new String(Files.readAllBytes(t.toPath()), "UTF-8"));
		assertTrue(a.exists());
	}

	@Test
	public void fileProxyRenameResults() throws IOException {
		File dir = tmpDir();
		File a = new File(dir, "a.txt");
		File b = new File(dir, "b.txt");
		write(a, "A");
		write(b, "B");

		assertFalse(local(a).renameTo(local(b)), "never replaces an existing file");
		assertEquals("B", new String(Files.readAllBytes(b.toPath()), "UTF-8"));

		MemoryFileSourceFactory mem = new MemoryFileSourceFactory();
		assertFalse(local(a).renameTo(mem.createFileSource("/a.txt")), "can't rename to another file system");
		assertTrue(a.exists());

		File c = new File(dir, "c.txt");
		assertTrue(local(a).renameTo(local(c)));
		assertTrue(c.exists());
		assertFalse(a.exists());
		c.deleteOnExit();

		assertThrows(NoSuchFileException.class, () -> local(a).renameTo(local(new File(dir, "d.txt"))), "real error, not false");
	}

	/** A memory node moved into another memory file system would keep the wrong factory (and lock). */
	@Test
	public void memoryRenameAcrossFileSystemsIsRefused() throws IOException {
		MemoryFileSourceFactory one = new MemoryFileSourceFactory();
		MemoryFileSourceFactory two = new MemoryFileSourceFactory();
		FileSource f = one.createFileSource("/f.txt");
		try (OutputStream out = f.getOutputStream()) {
			out.write(1);
		}
		assertFalse(f.renameTo(two.createFileSource("/g.txt")));
		assertTrue(f.exists());
	}
}
