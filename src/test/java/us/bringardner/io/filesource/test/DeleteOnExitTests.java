package us.bringardner.io.filesource.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.fileproxy.FileProxy;
import us.bringardner.io.filesource.memory.MemoryFileSourceFactory;

/** FileSource.deleteOnExit(): deleted at the factory's disConnect(), or by the shutdown hook. */
public class DeleteOnExitTests {

	/**
	 * A memory factory that keeps its files across disConnect() (the real one
	 * wipes everything), standing in for a remote file system.
	 */
	public static class KeepingFactory extends MemoryFileSourceFactory {
		private static final long serialVersionUID = 1L;
		boolean offline = false;

		@Override
		protected void disConnectImpl() {
			// keep the files, like a remote server would
		}

		@Override
		public boolean isConnected() {
			return !offline && super.isConnected();
		}
	}

	private static KeepingFactory factory() throws IOException {
		KeepingFactory f = new KeepingFactory();
		f.connect();
		return f;
	}

	private static FileSource file(FileSourceFactory f, String path) throws IOException {
		FileSource ret = f.createFileSource(path);
		ret.getParentFile().mkdirs();
		ret.createNewFile();
		return ret;
	}

	@Test
	public void deletedOnDisconnectNewestFirst() throws IOException {
		KeepingFactory f = factory();
		FileSource dir = f.createFileSource("/work");
		dir.mkdirs();
		dir.deleteOnExit();                       // registered before its contents
		FileSource a = file(f, "/work/a.txt");
		a.deleteOnExit();
		a.deleteOnExit();                         // a second registration is ignored
		FileSource later = f.createFileSource("/work/later.txt");
		later.deleteOnExit();                     // registered before it exists
		later.createNewFile();

		FileSource keep = f.createFileSource("/keep");
		keep.mkdirs();
		keep.deleteOnExit();                      // not empty at the end: stays
		FileSource unregistered = file(f, "/keep/data.txt");

		assertTrue(a.exists(), "nothing is deleted before disConnect");

		f.disConnect();

		assertFalse(f.createFileSource("/work/a.txt").exists());
		assertFalse(f.createFileSource("/work/later.txt").exists());
		assertFalse(f.createFileSource("/work").exists(), "directory deleted after its contents");
		assertTrue(f.createFileSource("/keep").exists(), "non-empty directory stays");
		assertTrue(unregistered.exists());

		// the list was consumed: disconnecting again deletes nothing more
		FileSource again = file(f, "/work2/b.txt");
		f.disConnect();
		assertTrue(again.exists());
	}

	@Test
	public void shutdownHookDeletesForConnectedFactories() throws Exception {
		KeepingFactory connected = factory();
		FileSource x = file(connected, "/x.txt");
		x.deleteOnExit();

		KeepingFactory offline = factory();
		FileSource y = file(offline, "/y.txt");
		y.deleteOnExit();
		offline.offline = true;

		Method hook = FileSourceFactory.class.getDeclaredMethod("deleteAllRegisteredFiles");
		hook.setAccessible(true);
		hook.invoke(null);

		assertFalse(x.exists(), "connected factory's file deleted");
		offline.offline = false;
		assertTrue(y.exists(), "a disconnected factory is skipped, not reconnected");
	}

	@Test
	public void otherFactoriesFilesRejected() throws IOException {
		KeepingFactory one = factory();
		KeepingFactory two = factory();
		FileSource f = file(one, "/z.txt");
		assertThrows(IllegalArgumentException.class, () -> two.deleteOnExit(f));
	}

	/** Local files use java.io.File.deleteOnExit(): checked in a separate JVM that exits. */
	@Test
	public void localFilesDeletedWhenTheVmExits() throws Exception {
		File tmp = File.createTempFile("doe", ".txt");
		try {
			String java = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
			Process p = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
					LocalMain.class.getName(), tmp.getAbsolutePath())
					.redirectErrorStream(true).start();
			assertTrue(p.waitFor(60, TimeUnit.SECONDS), "child JVM finished");
			String out = new String(p.getInputStream().readAllBytes());
			assertEquals(0, p.exitValue(), out);
			assertTrue(out.contains("registered"), out);
			assertFalse(tmp.exists(), "deleted when the child JVM exited");
		} finally {
			Files.deleteIfExists(tmp.toPath());
		}
	}

	public static class LocalMain {
		public static void main(String[] args) throws IOException {
			FileSource f = new FileProxy(new File(args[0]), FileSourceFactory.getDefaultFactory());
			if( !f.exists() ) {
				throw new IllegalStateException("missing " + args[0]);
			}
			f.deleteOnExit();
			System.out.println("registered");
		}
	}
}
