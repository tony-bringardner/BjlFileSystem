package us.bringardner.io.filesource.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.FileSourceUri;
import us.bringardner.io.filesource.memory.MemoryFileSourceFactory;

/**
 * Regression tests for FileSource URLs (review items #9, #12 and #13).
 */
public class FileSourceUrlTests {

	/** #13: '&' separated query parameters weren't recognized (only ','). */
	@Test
	public void queryParsingAcceptsAmpersandAndComma() throws Exception {
		FileSourceUri amp = new FileSourceUri(new URI("filesource:/x?sourcetype=memory&sessionId=5&name=a=b"));
		assertEquals("memory", amp.getFactoryId());
		assertEquals("5", amp.getSessionId());
		assertEquals("a=b", amp.getQueryByName("name"), "value may contain '='");

		FileSourceUri comma = new FileSourceUri(new URI("filesource:/x?sourcetype=memory,sessionId=6"));
		assertEquals("memory", comma.getFactoryId(), "legacy ',' still works");
		assertEquals("6", comma.getSessionId());
	}

	/** #13: a URL with a second parameter failed with "No Filesource Factory". */
	@Test
	public void urlWithSeveralParametersResolves() throws Exception {
		File f = File.createTempFile("urltest", ".txt");
		f.deleteOnExit();
		FileSource fs = FileSourceFactory.getFileSource(FileSourceFactory.FILE_SOURCE_PROTOCOL+":"+f.getAbsolutePath()
				+"?"+FileSourceFactory.QUERY_STRING_SOURCE_TYPE+"=fileproxy&foo=bar");
		assertNotNull(fs);
		assertTrue(fs.exists());
		assertEquals(f.getAbsolutePath(), fs.getAbsolutePath());
	}

	/** #12: a memory file's own URL pointed at a brand-new, empty memory file system. */
	@Test
	public void memoryFileIsReadableThroughItsUrl() throws Exception {
		MemoryFileSourceFactory factory = new MemoryFileSourceFactory();
		factory.connect();
		FileSource f = factory.createFileSource("/urltest/hello.txt");
		assertTrue(f.getParentFile().mkdirs());
		try (OutputStream out = f.getOutputStream()) {
			out.write("hello".getBytes("UTF-8"));
		}

		URL url = f.toURL();
		try (InputStream in = url.openStream()) {
			assertEquals("hello", new String(in.readAllBytes(), "UTF-8"), "read via "+url);
		}
		FileSource again = FileSourceFactory.getFileSource(url);
		assertTrue(again.exists());
		assertEquals(5, again.length());
	}

	/** #9: the handler package list held "package us.bringardner.io.filesource". */
	@Test
	public void handlerPackagePropertyIsUsable() {
		FileSourceFactory.getDefaultFactory();
		List<String> pkgs = Arrays.asList(System.getProperty(FileSourceFactory.PROP_JAVA_PROTOCOL_HANDLER_PKGS).split("[|]"));
		assertTrue(pkgs.contains("us.bringardner.io"), "prefix for us.bringardner.io.filesource.Handler: "+pkgs);
		assertTrue(pkgs.contains("us.bringardner.io.filesource"), "prefix for per-factory handlers: "+pkgs);
		assertFalse(pkgs.stream().anyMatch(p -> p.startsWith("package ")), "no 'package ' prefix: "+pkgs);
	}

	/**
	 * #9: if another URLStreamHandlerFactory was already installed, loading
	 * FileSourceFactory threw Error and the library was unusable for the
	 * rest of the JVM. Needs a fresh JVM, so run UrlFactoryConflictMain.
	 */
	@Test
	public void worksWhenAnotherUrlFactoryIsInstalled() throws Exception {
		String java = System.getProperty("java.home")+File.separator+"bin"+File.separator+"java";
		Process p = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
				UrlFactoryConflictMain.class.getName())
				.redirectErrorStream(true)
				.start();
		String output;
		try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"))) {
			output = r.lines().collect(Collectors.joining("\n"));
		}
		assertTrue(p.waitFor(60, TimeUnit.SECONDS), "child JVM finished");
		assertTrue(output.contains("OK:hello"), "child JVM output:\n"+output);
		assertEquals(0, p.exitValue(), "child JVM output:\n"+output);
	}
}
