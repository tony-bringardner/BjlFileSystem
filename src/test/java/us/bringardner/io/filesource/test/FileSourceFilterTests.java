package us.bringardner.io.filesource.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFilter;
import us.bringardner.io.filesource.memory.MemoryFileSourceFactory;

/** FileSourceFilter is a functional interface: lambdas work, as for java.io.FileFilter. */
public class FileSourceFilterTests {

	@Test
	public void lambdaFilters() throws IOException {
		MemoryFileSourceFactory factory = new MemoryFileSourceFactory();
		factory.connect();
		FileSource dir = factory.createFileSource("/filters");
		dir.mkdirs();
		for(String name : new String[] { "a.log", "b.txt", "c.log" }) {
			dir.getChild(name).createNewFile();
		}
		dir.getChild("sub").mkdir();

		FileSource[] logs = dir.listFiles(f -> f.getName().endsWith(".log"));
		String[] names = Arrays.stream(logs).map(FileSource::getName).sorted().toArray(String[]::new);
		assertArrayEquals(new String[] { "a.log", "c.log" }, names);

		String[] dirs = dir.list(f -> {
			try {
				return f.isDirectory();
			} catch (IOException e) {
				return false;
			}
		});
		assertArrayEquals(new String[] { "sub" }, dirs);

		FileSourceFilter all = f -> true;
		assertEquals("Filtered files", all.getDescription());
	}

	@Test
	public void existingFiltersStillWork() throws IOException {
		FileSourceFilter named = new FileSourceFilter() {
			@Override
			public boolean accept(FileSource f) {
				return true;
			}

			@Override
			public String getDescription() {
				return "Everything";
			}
		};
		assertEquals("Everything", named.getDescription());
	}
}
