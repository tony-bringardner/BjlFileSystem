package us.bringardner.io.filesource.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.IOException;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.FileSourceUser;
import us.bringardner.io.filesource.ProcessRunner;
import us.bringardner.io.filesource.fileproxy.FileProxyFactory;

/**
 * Regression tests for running external commands (review section 2):
 * whoAmI() and findUser() waited for the process before reading its output,
 * which deadlocks once the output is larger than the OS pipe buffer.
 */
public class ProcessRunnerTests {

	/** Far more than any pipe buffer, on both stdout and stderr. */
	@Test
	public void largeOutputOnBothStreamsDoesNotDeadlock() {
		assumeFalse(FileSourceFactory.isWindows(), "uses sh");
		ProcessRunner.Result r = assertTimeoutPreemptively(Duration.ofSeconds(30), () ->
				ProcessRunner.run("sh", "-c", "head -c 1000000 /dev/zero; head -c 500000 /dev/zero 1>&2"));
		assertEquals(0, r.exitCode);
		assertEquals(1_000_000, r.stdout.length());
		assertEquals(500_000, r.stderr.length());
	}

	@Test
	public void exitCodeIsReported() throws IOException {
		assumeFalse(FileSourceFactory.isWindows(), "uses sh");
		ProcessRunner.Result r = ProcessRunner.run("sh", "-c", "echo oops 1>&2; exit 3");
		assertEquals(3, r.exitCode);
		assertEquals("oops", r.stderr.trim());
	}

	@Test
	public void hungCommandTimesOut() {
		assumeFalse(FileSourceFactory.isWindows(), "uses sleep");
		assertTimeoutPreemptively(Duration.ofSeconds(20), () ->
				assertThrows(IOException.class, () -> ProcessRunner.run(1, "sleep", "30")));
	}

	@Test
	public void whoAmIStillWorks() {
		FileSourceUser me = new FileProxyFactory().whoAmI();
		assertNotNull(me);
		assertNotNull(me.getName());
		assertTrue(!me.getName().isEmpty());
	}
}
