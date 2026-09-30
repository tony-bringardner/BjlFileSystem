package us.bringardner.io.filesource.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;

/** getParent() returns the parent's path, as java.io.File does, for every implementation. */
public class GetParentTests {

	@ParameterizedTest
	@ValueSource(strings = { "fileproxy", "memory" })
	public void parentIsAPath(String type) throws IOException {
		FileSourceFactory factory = FileSourceFactory.getFileSourceFactory(type);
		factory.connect();
		File tmp = Files.createTempDirectory("parent").toFile();
		try {
			FileSource dir = factory.createFileSource(tmp.getAbsolutePath());
			dir.mkdirs();
			FileSource child = dir.getChild("sub").getChild("x.txt");
			String expected = new File(new File(tmp, "sub"), "x.txt").getParent();
			assertEquals(expected, child.getParent());
			assertEquals(child.getParentFile().getAbsolutePath(), child.getParent());
			assertEquals(tmp.getParent(), dir.getParent());
			FileSource root = factory.listRoots()[0];
			assertNull(root.getParent());
		} finally {
			tmp.delete();
		}
	}
}
