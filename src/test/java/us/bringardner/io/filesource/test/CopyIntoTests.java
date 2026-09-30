package us.bringardner.io.filesource.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceChooserDialog;
import us.bringardner.io.filesource.memory.MemoryFileSourceFactory;

/** BJL-24: copying a dropped or pasted file into a folder. */
public class CopyIntoTests {

	private MemoryFileSourceFactory mem;

	@BeforeEach
	public void setUp() throws IOException {
		mem = new MemoryFileSourceFactory();
		write("/src/a.txt", "A");
		write("/src/sub/b.txt", "B");
		write("/src/tree/inner/c.txt", "C");
		assertTrue(mem.createFileSource("/dst").mkdirs());
	}

	private FileSource at(String path) throws IOException {
		return mem.createFileSource(path);
	}

	private void write(String path, String text) throws IOException {
		FileSource f = at(path);
		f.getParentFile().mkdirs();
		try(OutputStream out = f.getOutputStream()) {
			out.write(text.getBytes(StandardCharsets.UTF_8));
		}
	}

	private String read(String path) throws IOException {
		try(InputStream in = at(path).getInputStream()) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	@Test
	public void aFileIsCopied() throws IOException {
		assertEquals("/dst/a.txt", FileSourceChooserDialog.copyInto(at("/dst"), at("/src/a.txt")).getAbsolutePath());
		assertEquals("A", read("/dst/a.txt"));
		assertEquals("A", read("/src/a.txt"));
	}

	@Test
	public void aFolderIsCopiedWithEverythingInIt() throws IOException {
		FileSourceChooserDialog.copyInto(at("/dst"), at("/src/tree"));
		assertEquals("C", read("/dst/tree/inner/c.txt"));
	}

	@Test
	public void aFileAlreadyThereIsLeftAlone() throws IOException {
		assertNull(FileSourceChooserDialog.copyInto(at("/src"), at("/src/a.txt")));
		assertEquals("A", read("/src/a.txt"));
	}

	@Test
	public void aFileFromASubfolderIsCopiedUp() throws IOException {
		// skipped before BJL-24, because it is "inside" /src
		FileSourceChooserDialog.copyInto(at("/src"), at("/src/sub/b.txt"));
		assertEquals("B", read("/src/b.txt"));
	}

	@Test
	public void aFolderCantBeCopiedIntoItself() throws IOException {
		assertThrows(IOException.class, () -> FileSourceChooserDialog.copyInto(at("/src/tree"), at("/src/tree")));
		assertThrows(IOException.class, () -> FileSourceChooserDialog.copyInto(at("/src/tree/inner"), at("/src/tree")));
		assertFalse(at("/src/tree/inner/tree").exists());
		// a sibling with the same name prefix is fine
		assertTrue(at("/src/treeX").mkdirs());
		FileSourceChooserDialog.copyInto(at("/src/treeX"), at("/src/tree"));
		assertEquals("C", read("/src/treeX/tree/inner/c.txt"));
	}

	@Test
	public void aMissingFileCopiesNothing() throws IOException {
		assertNull(FileSourceChooserDialog.copyInto(at("/dst"), at("/src/missing.txt")));
		assertFalse(at("/dst/missing.txt").exists());
	}
}
