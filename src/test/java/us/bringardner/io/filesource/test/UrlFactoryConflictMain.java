package us.bringardner.io.filesource.test;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;

import us.bringardner.io.filesource.FileSourceFactory;

/**
 * Run in a separate JVM by FileSourceUrlTests: installs some other
 * URLStreamHandlerFactory first (as app servers and Spring Boot do), then
 * uses the library and opens a filesource: URL. Prints "OK:<content>" on
 * success.
 */
public class UrlFactoryConflictMain {

	public static void main(String[] args) throws Exception {
		URL.setURLStreamHandlerFactory(protocol -> null);

		FileSourceFactory.getDefaultFactory();

		File f = File.createTempFile("urlconflict", ".txt");
		f.deleteOnExit();
		Files.write(f.toPath(), "hello".getBytes("UTF-8"));

		URL url = new URL(FileSourceFactory.FILE_SOURCE_PROTOCOL+":"+f.getAbsolutePath()
				+"?"+FileSourceFactory.QUERY_STRING_SOURCE_TYPE+"=fileproxy");
		try (InputStream in = url.openStream()) {
			System.out.println("OK:"+new String(in.readAllBytes(), "UTF-8"));
		}
	}
}
