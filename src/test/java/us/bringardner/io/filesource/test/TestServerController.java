package us.bringardner.io.filesource.test;

import java.io.IOException;

/**
 * A server a FileSource test needs (FTP, SSH, a database...), so
 * FileSourceTestSupport.startServer / stopServer can start it, wait for it
 * and stop it the same way for every implementation.
 */
public interface TestServerController {

	boolean isRunning();

	void start() throws IOException;

	void stop() throws IOException;

	String getName();
}
