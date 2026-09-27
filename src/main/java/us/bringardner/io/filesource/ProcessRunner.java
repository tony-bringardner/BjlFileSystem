package us.bringardner.io.filesource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.concurrent.TimeUnit;

/**
 * Runs an external command and captures its output safely.
 * 
 * Both stdout and stderr are drained on background threads while the process
 * runs. Waiting for the process first and reading its output afterwards (as
 * whoAmI() and findUser() used to) deadlocks as soon as the command writes
 * more than the OS pipe buffer (typically 4-64 KB), which `whoami /groups`
 * easily does on a domain-joined Windows machine.
 * 
 * Used internally by FileSourceFactory and FileSourceUser.
 */
public final class ProcessRunner {

	/** Output of a finished command. */
	public static final class Result {
		public final int exitCode;
		public final String stdout;
		public final String stderr;

		Result(int exitCode, String stdout, String stderr) {
			this.exitCode = exitCode;
			this.stdout = stdout;
			this.stderr = stderr;
		}
	}

	public static final long DEFAULT_TIMEOUT_SECONDS = 30;

	private ProcessRunner() {
	}

	public static Result run(String... command) throws IOException {
		return run(DEFAULT_TIMEOUT_SECONDS, command);
	}

	/**
	 * @throws IOException if the command can't be started, or doesn't finish
	 *         within timeoutSeconds (it is then killed)
	 */
	public static Result run(long timeoutSeconds, String... command) throws IOException {
		Process process = new ProcessBuilder(command).start();
		try {
			process.getOutputStream().close();   // no input; don't let the command wait for any
		} catch (IOException e) {
			// ignore, the process may already have exited
		}

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ByteArrayOutputStream err = new ByteArrayOutputStream();
		Thread outReader = drain(process.getInputStream(), out, command[0]+"-stdout");
		Thread errReader = drain(process.getErrorStream(), err, command[0]+"-stderr");

		try {
			if( !process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				throw new IOException("Command timed out after "+timeoutSeconds+"s: "+String.join(" ", command));
			}
			outReader.join(TimeUnit.SECONDS.toMillis(5));
			errReader.join(TimeUnit.SECONDS.toMillis(5));
		} catch (InterruptedException e) {
			process.destroyForcibly();
			Thread.currentThread().interrupt();
			throw new InterruptedIOException("Interrupted while running "+command[0]);
		}

		synchronized (out) {
			synchronized (err) {
				return new Result(process.exitValue(), out.toString(), err.toString());
			}
		}
	}

	private static Thread drain(InputStream in, ByteArrayOutputStream sink, String name) {
		Thread t = new Thread(() -> {
			byte[] buf = new byte[8192];
			try (InputStream is = in) {
				int n;
				while( (n = is.read(buf)) >= 0 ) {
					synchronized (sink) {
						sink.write(buf, 0, n);
					}
				}
			} catch (IOException e) {
				// process ended / stream closed
			}
		}, "ProcessRunner-"+name);
		t.setDaemon(true);
		t.start();
		return t;
	}
}
