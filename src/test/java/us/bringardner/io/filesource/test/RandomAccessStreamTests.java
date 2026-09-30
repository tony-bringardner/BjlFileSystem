package us.bringardner.io.filesource.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.Closeable;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import us.bringardner.io.filesource.AbstractRandomAccessStream;
import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.fileproxy.FileProxy;
import us.bringardner.io.filesource.memory.MemoryFileSourceFactory;

/**
 * AbstractRandomAccessStream (the DataInput/DataOutput methods behind every
 * FileSource random-access stream, local and remote) checked against
 * java.io.RandomAccessFile: each script runs on a RandomAccessFile and on a
 * FileSource stream, and every result, exception, file pointer and the final
 * file contents must match.
 *
 * Subjects: "memory" and "local" are the real streams (bulk reads/writes);
 * "bytewise" is a minimal subclass that only implements read()/write(int), so
 * the base class's own byte-at-a-time readBytes/writeBytes are covered too.
 */
public class RandomAccessStreamTests {

	/** The methods RandomAccessFile and IRandomAccessStream have in common. */
	public interface RA extends DataInput, DataOutput, Closeable {
		int read() throws IOException;
		int read(byte[] b) throws IOException;
		int read(byte[] b, int off, int len) throws IOException;
		void seek(long pos) throws IOException;
		long getFilePointer() throws IOException;
		long length() throws IOException;
		void setLength(long len) throws IOException;
	}

	/** Calls the method with the same signature on the target. */
	private static RA wrap(Object target) {
		return (RA) Proxy.newProxyInstance(RA.class.getClassLoader(), new Class<?>[] { RA.class }, (p, m, args) -> {
			Method tm = findMethod(target.getClass(), m.getName(), m.getParameterTypes());
			tm.setAccessible(true);
			try {
				return tm.invoke(target, args);
			} catch (InvocationTargetException e) {
				throw e.getCause();
			}
		});
	}

	private static Method findMethod(Class<?> cls, String name, Class<?>[] types) throws NoSuchMethodException {
		for(Class<?> c = cls; c != null; c = c.getSuperclass()) {
			try {
				return c.getDeclaredMethod(name, types);
			} catch (NoSuchMethodException e) {
				// keep looking
			}
		}
		return cls.getMethod(name, types);
	}

	/**
	 * Only read() and write(int) (plus positioning): everything else is the
	 * base class's code.
	 */
	public static class ByteWiseStream extends AbstractRandomAccessStream {
		private byte[] data = new byte[0];
		private long pointer;
		private boolean closed;

		public ByteWiseStream(FileSource file, String mode) throws IOException {
			super(file, mode);
		}

		private void check() throws IOException {
			if( closed ) {
				throw new IOException("Stream Closed");
			}
		}

		@Override
		public int read() throws IOException {
			check();
			return pointer < data.length ? data[(int) pointer++] & 0xFF : -1;
		}

		@Override
		public void write(int b) throws IOException {
			check();
			if( readOnly ) {
				throw new IOException("read only");
			}
			if( pointer >= data.length ) {
				data = Arrays.copyOf(data, (int) pointer + 1);
			}
			data[(int) pointer++] = (byte) b;
		}

		@Override
		public long getFilePointer() throws IOException {
			check();
			return pointer;
		}

		@Override
		public void seek(long pos) throws IOException {
			check();
			if( pos < 0 ) {
				throw new IOException("Negative seek offset");
			}
			pointer = pos;
		}

		@Override
		public long length() throws IOException {
			check();
			return data.length;
		}

		@Override
		public void setLength(long newLength) throws IOException {
			check();
			if( readOnly ) {
				throw new IOException("read only");
			}
			data = Arrays.copyOf(data, (int) newLength);
			pointer = Math.min(pointer, newLength);
		}

		@Override
		public void close() {
			closed = true;
		}

		byte[] contents() {
			return data.clone();
		}
	}

	private final List<File> tmp = new ArrayList<>();

	@AfterEach
	public void cleanup() {
		for(File f : tmp) {
			f.delete();
		}
	}

	private File tempFile() throws IOException {
		File f = File.createTempFile("ras", ".bin");
		tmp.add(f);
		return f;
	}

	/** An open subject stream plus a way to read the stored bytes after close. */
	private static class Subject {
		RA ra;
		Object raw;
		FileSource file;
		byte[] contents() throws IOException {
			if( raw instanceof ByteWiseStream ) {
				return ((ByteWiseStream) raw).contents();
			}
			file.refresh();
			try (java.io.InputStream in = file.getInputStream()) {
				return in.readAllBytes();
			}
		}
	}

	private Subject open(String kind, byte[] initial, String mode) throws IOException {
		Subject s = new Subject();
		if( kind.equals("local") ) {
			File f = tempFile();
			Files.write(f.toPath(), initial);
			s.file = new FileProxy(f, FileSourceFactory.getDefaultFactory());
		} else {
			MemoryFileSourceFactory factory = new MemoryFileSourceFactory();
			factory.connect();
			s.file = factory.createFileSource("/ras.bin");
			try (java.io.OutputStream out = s.file.getOutputStream()) {
				out.write(initial);
			}
		}
		if( kind.equals("bytewise") ) {
			ByteWiseStream b = new ByteWiseStream(s.file, mode);
			b.data = initial.clone();
			s.raw = b;
		} else {
			s.raw = s.file.getRandomAccessStream(mode);
		}
		s.ra = wrap(s.raw);
		return s;
	}

	private interface Script {
		void run(RA ra, List<String> log) throws IOException;
	}

	private interface Step {
		Object call() throws Exception;
	}

	/** Logs a step's result (or exception class) and the file pointer after it. */
	private static void rec(RA ra, List<String> log, String label, Step step) {
		String result;
		try {
			Object v = step.call();
			if( v instanceof byte[] ) {
				v = Arrays.toString((byte[]) v);
			} else if( v instanceof Float ) {
				v = "float:" + Integer.toHexString(Float.floatToIntBits((Float) v));
			} else if( v instanceof Double ) {
				v = "double:" + Long.toHexString(Double.doubleToLongBits((Double) v));
			}
			result = String.valueOf(v);
		} catch (Exception e) {
			result = "threw " + (e instanceof IOException && e.getClass() != IOException.class
					? e.getClass().getSimpleName() : e instanceof IOException ? "IOException" : e.getClass().getSimpleName());
		}
		String ptr;
		try {
			ptr = String.valueOf(ra.getFilePointer());
		} catch (Exception e) {
			ptr = "?";
		}
		log.add(label + " -> " + result + " @" + ptr);
	}

	/** Runs the script on a RandomAccessFile and on the subject and compares everything. */
	private void compare(String kind, byte[] initial, String mode, Script script) throws Exception {
		File f = tempFile();
		Files.write(f.toPath(), initial);
		List<String> expected = new ArrayList<>();
		try (RandomAccessFile raf = new RandomAccessFile(f, mode)) {
			script.run(wrap(raf), expected);
		}
		byte[] expectedBytes = Files.readAllBytes(f.toPath());

		Subject s = open(kind, initial, mode);
		List<String> actual = new ArrayList<>();
		try {
			script.run(s.ra, actual);
		} finally {
			s.ra.close();
		}
		for(int i = 0; i < Math.min(expected.size(), actual.size()); i++) {
			assertEquals(expected.get(i), actual.get(i), "step " + i + " (RandomAccessFile vs " + kind + ")");
		}
		assertEquals(expected.size(), actual.size(), "number of steps");
		assertArrayEquals(expectedBytes, s.contents(), "file contents");
	}

	private static final String TEXT = "Héllo € \u0000 😀";

	@ParameterizedTest
	@ValueSource(strings = { "memory", "local", "bytewise" })
	public void writeAndReadEveryType(String kind) throws Exception {
		compare(kind, new byte[0], "rw", (ra, log) -> {
			rec(ra, log, "writeBoolean", () -> { ra.writeBoolean(true); ra.writeBoolean(false); return null; });
			for(int v : new int[] { -129, -128, -1, 0, 127, 128, 255, 256 }) {
				rec(ra, log, "writeByte " + v, () -> { ra.writeByte(v); return null; });
				rec(ra, log, "write " + v, () -> { ra.write(v); return null; });
			}
			for(int v : new int[] { Short.MIN_VALUE, -1, 0, 0x7fff, 0x8000, 0xffff, 0x12345 }) {
				rec(ra, log, "writeShort " + v, () -> { ra.writeShort(v); return null; });
				rec(ra, log, "writeChar " + v, () -> { ra.writeChar(v); return null; });
			}
			for(int v : new int[] { Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE, 0x80402010 }) {
				rec(ra, log, "writeInt " + v, () -> { ra.writeInt(v); return null; });
			}
			for(long v : new long[] { Long.MIN_VALUE, -1, 0, Long.MAX_VALUE, 0x8070605040302010L }) {
				rec(ra, log, "writeLong " + v, () -> { ra.writeLong(v); return null; });
			}
			for(float v : new float[] { 0f, -0f, 1.5f, Float.MIN_VALUE, Float.MAX_VALUE, Float.NaN, Float.NEGATIVE_INFINITY }) {
				rec(ra, log, "writeFloat " + v, () -> { ra.writeFloat(v); return null; });
			}
			for(double v : new double[] { 0d, -0d, Math.PI, Double.MIN_VALUE, Double.MAX_VALUE, Double.NaN, Double.POSITIVE_INFINITY }) {
				rec(ra, log, "writeDouble " + v, () -> { ra.writeDouble(v); return null; });
			}
			rec(ra, log, "writeBytes(String)", () -> { ra.writeBytes(TEXT); return null; });
			rec(ra, log, "writeChars", () -> { ra.writeChars(TEXT); return null; });
			for(String v : new String[] { "", "ascii", TEXT, "߿ࠀ￿" }) {
				rec(ra, log, "writeUTF " + v.length(), () -> { ra.writeUTF(v); return null; });
			}
			byte[] all = new byte[256];
			for(int i = 0; i < all.length; i++) {
				all[i] = (byte) i;
			}
			rec(ra, log, "write(byte[])", () -> { ra.write(all); return null; });
			rec(ra, log, "write(byte[],off,len)", () -> { ra.write(all, 250, 6); return null; });
			rec(ra, log, "length", ra::length);

			rec(ra, log, "seek 0", () -> { ra.seek(0); return null; });
			rec(ra, log, "readBoolean", ra::readBoolean);
			rec(ra, log, "readBoolean", ra::readBoolean);
			for(int i = 0; i < 8; i++) {
				rec(ra, log, "readByte", ra::readByte);
				rec(ra, log, "readUnsignedByte", ra::readUnsignedByte);
			}
			for(int i = 0; i < 7; i++) {
				rec(ra, log, "readShort", ra::readShort);
				rec(ra, log, "readChar", () -> (int) ra.readChar());
			}
			for(int i = 0; i < 6; i++) {
				rec(ra, log, "readInt", ra::readInt);
			}
			for(int i = 0; i < 5; i++) {
				rec(ra, log, "readLong", ra::readLong);
			}
			for(int i = 0; i < 7; i++) {
				rec(ra, log, "readFloat", ra::readFloat);
			}
			for(int i = 0; i < 7; i++) {
				rec(ra, log, "readDouble", ra::readDouble);
			}
			byte[] asBytes = new byte[TEXT.length()];
			rec(ra, log, "readFully writeBytes", () -> { ra.readFully(asBytes); return asBytes; });
			rec(ra, log, "readChars", () -> {
				StringBuilder b = new StringBuilder();
				for(int i = 0; i < TEXT.length(); i++) {
					b.append(ra.readChar());
				}
				return b.toString().equals(TEXT);
			});
			for(int i = 0; i < 4; i++) {
				rec(ra, log, "readUTF", ra::readUTF);
			}
			rec(ra, log, "readUnsignedShort", ra::readUnsignedShort);
			rec(ra, log, "skipBytes 10", () -> ra.skipBytes(10));
			byte[] rest = new byte[300];
			rec(ra, log, "read(byte[]) at tail", () -> ra.read(rest));
			rec(ra, log, "read(byte[]) at EOF", () -> ra.read(rest));
		});
	}

	@ParameterizedTest
	@ValueSource(strings = { "memory", "local", "bytewise" })
	public void endOfFile(String kind) throws Exception {
		compare(kind, new byte[] { 1, 2, 3 }, "rw", (ra, log) -> {
			Step[] steps = {
					ra::readInt, ra::readLong, ra::readDouble, ra::readFloat,
					ra::readShort, ra::readUnsignedShort, ra::readChar, ra::readUTF,
			};
			for(Step s : steps) {
				rec(ra, log, "seek 1", () -> { ra.seek(1); return null; });
				rec(ra, log, "read past end", s);
			}
			rec(ra, log, "seek 3", () -> { ra.seek(3); return null; });
			rec(ra, log, "read", ra::read);
			rec(ra, log, "readByte", ra::readByte);
			rec(ra, log, "readBoolean", ra::readBoolean);
			rec(ra, log, "readUnsignedByte", ra::readUnsignedByte);
			rec(ra, log, "readLine", ra::readLine);
			byte[] b = new byte[4];
			rec(ra, log, "read(b)", () -> ra.read(b));
			rec(ra, log, "read(b,0,0)", () -> ra.read(b, 0, 0));
			rec(ra, log, "readFully(b,0,0)", () -> { ra.readFully(b, 0, 0); return null; });
			rec(ra, log, "skipBytes", () -> ra.skipBytes(5));
			rec(ra, log, "seek 1", () -> { ra.seek(1); return null; });
			rec(ra, log, "readFully(4)", () -> { ra.readFully(b); return null; });
			rec(ra, log, "seek 1", () -> { ra.seek(1); return null; });
			rec(ra, log, "read(b) short", () -> ra.read(b));
			rec(ra, log, "seek 10", () -> { ra.seek(10); return null; });
			rec(ra, log, "read beyond", ra::read);
			rec(ra, log, "skipBytes beyond", () -> ra.skipBytes(5));
			rec(ra, log, "seek -1", () -> { ra.seek(-1); return null; });
			rec(ra, log, "skipBytes -3", () -> ra.skipBytes(-3));
			rec(ra, log, "read(b,-1,2)", () -> ra.read(b, -1, 2));
			rec(ra, log, "read(b,2,5)", () -> ra.read(b, 2, 5));
			rec(ra, log, "write(b,3,2)", () -> { ra.write(b, 3, 2); return null; });
		});
	}

	@ParameterizedTest
	@ValueSource(strings = { "memory", "local", "bytewise" })
	public void readLine(String kind) throws Exception {
		byte[] text = { 'a', '\n', 'b', '\r', '\n', 'c', '\r', 'd', '\n', '\n', '\r', (byte) 0xC3, (byte) 0xA9, 'e', '\r' };
		compare(kind, text, "r", (ra, log) -> {
			for(int i = 0; i < 9; i++) {
				rec(ra, log, "readLine", ra::readLine);
			}
		});
		compare(kind, "no newline at end".getBytes(), "r", (ra, log) -> {
			rec(ra, log, "readLine", ra::readLine);
			rec(ra, log, "readLine", ra::readLine);
		});
	}

	@ParameterizedTest
	@ValueSource(strings = { "memory", "local", "bytewise" })
	public void seekLengthAndGaps(String kind) throws Exception {
		compare(kind, "0123456789".getBytes(), "rw", (ra, log) -> {
			rec(ra, log, "seek 15", () -> { ra.seek(15); return null; });
			rec(ra, log, "length unchanged", ra::length);
			rec(ra, log, "writeInt", () -> { ra.writeInt(0x41424344); return null; });
			rec(ra, log, "length", ra::length);
			rec(ra, log, "seek 8", () -> { ra.seek(8); return null; });
			byte[] b = new byte[11];
			rec(ra, log, "readFully gap", () -> { ra.readFully(b); return b; });
			rec(ra, log, "setLength 5 (pointer beyond)", () -> { ra.setLength(5); return null; });
			rec(ra, log, "length", ra::length);
			rec(ra, log, "read", ra::read);
			rec(ra, log, "seek 2", () -> { ra.seek(2); return null; });
			rec(ra, log, "setLength 4 (pointer inside)", () -> { ra.setLength(4); return null; });
			rec(ra, log, "writeShort overwrite+append", () -> { ra.writeShort(0x7a7a); return null; });
			rec(ra, log, "writeUTF", () -> { ra.writeUTF("tail"); return null; });
			rec(ra, log, "length", ra::length);
		});
	}

	@ParameterizedTest
	@ValueSource(strings = { "memory", "local", "bytewise" })
	public void utfErrors(String kind) throws Exception {
		// length 2, then an invalid UTF-8 sequence
		compare(kind, new byte[] { 0, 2, (byte) 0xC3, 0x28 }, "rw", (ra, log) -> {
			rec(ra, log, "readUTF malformed", ra::readUTF);
			rec(ra, log, "seek 4", () -> { ra.seek(4); return null; });
			char[] big = new char[30000];
			Arrays.fill(big, 'é');   // 2 bytes each: 60000 fits
			rec(ra, log, "writeUTF 60000 bytes", () -> { ra.writeUTF(new String(big)); return null; });
			char[] tooBig = new char[22000];
			Arrays.fill(tooBig, '€');   // 3 bytes each: 66000 is too long
			rec(ra, log, "writeUTF too long", () -> { ra.writeUTF(new String(tooBig)); return null; });
			rec(ra, log, "length", ra::length);
			rec(ra, log, "seek 4", () -> { ra.seek(4); return null; });
			rec(ra, log, "readUTF 60000 bytes", () -> ra.readUTF().equals(new String(big)));
		});
	}

	@ParameterizedTest
	@ValueSource(strings = { "memory", "local", "bytewise" })
	public void readOnly(String kind) throws Exception {
		compare(kind, "abc".getBytes(), "r", (ra, log) -> {
			rec(ra, log, "readByte", ra::readByte);
			rec(ra, log, "write", () -> { ra.write(1); return null; });
			rec(ra, log, "writeInt", () -> { ra.writeInt(1); return null; });
			rec(ra, log, "setLength", () -> { ra.setLength(1); return null; });
			rec(ra, log, "length", ra::length);
		});
	}

	private static List<String> afterClose(RA ra) throws IOException {
		List<String> log = new ArrayList<>();
		ra.close();
		rec(ra, log, "close again", () -> { ra.close(); return null; });
		rec(ra, log, "read", ra::read);
		rec(ra, log, "read(b)", () -> ra.read(new byte[2]));
		rec(ra, log, "readInt", ra::readInt);
		rec(ra, log, "write", () -> { ra.write(1); return null; });
		rec(ra, log, "write(b)", () -> { ra.write(new byte[2]); return null; });
		rec(ra, log, "writeLong", () -> { ra.writeLong(1); return null; });
		rec(ra, log, "seek", () -> { ra.seek(0); return null; });
		rec(ra, log, "length", ra::length);
		rec(ra, log, "setLength", () -> { ra.setLength(1); return null; });
		rec(ra, log, "getFilePointer", ra::getFilePointer);
		return log;
	}

	/** Every operation on a closed stream throws IOException, as for RandomAccessFile. */
	@ParameterizedTest
	@ValueSource(strings = { "memory", "local", "bytewise" })
	public void closedStream(String kind) throws Exception {
		File f = tempFile();
		Files.write(f.toPath(), "abcd".getBytes());
		List<String> expected = afterClose(wrap(new RandomAccessFile(f, "rw")));
		List<String> actual = afterClose(open(kind, "abcd".getBytes(), "rw").ra);
		assertEquals(String.join("\n", expected), String.join("\n", actual));
	}

	/** Bad modes are rejected like RandomAccessFile does; "r" needs an existing file. */
	@ParameterizedTest
	@ValueSource(strings = { "memory", "local" })
	public void modes(String kind) throws Exception {
		Subject s = open(kind, new byte[0], "rw");
		s.ra.close();
		FileSource missing = s.file.getParentFile().getChild("missing.bin");
		File f = tempFile();
		for(String mode : new String[] { "x", "", "rwx", "rws", "rwd" }) {
			String expected;
			try (RandomAccessFile raf = new RandomAccessFile(f, mode)) {
				expected = "ok";
			} catch (Exception e) {
				expected = e.getClass().getSimpleName();
			}
			String actual;
			try (Closeable c = s.file.getRandomAccessStream(mode)) {
				actual = "ok";
			} catch (Exception e) {
				actual = e.getClass().getSimpleName();
			}
			assertEquals(expected, actual, "mode '" + mode + "'");
		}
		org.junit.jupiter.api.Assertions.assertThrows(java.io.FileNotFoundException.class, () -> missing.getRandomAccessStream("r"));
	}

	/** getFilePointer() is on the IRandomAccessStream interface: no cast needed. */
	@ParameterizedTest
	@ValueSource(strings = { "memory", "local" })
	public void filePointerOnTheInterface(String kind) throws Exception {
		Subject s = open(kind, "0123456789".getBytes(), "rw");
		s.ra.close();
		us.bringardner.io.filesource.IRandomAccessStream ras = s.file.getRandomAccessStream("rw");
		assertEquals(0, ras.getFilePointer());
		ras.readInt();
		assertEquals(4, ras.getFilePointer());
		ras.seek(8);
		ras.writeLong(1);
		assertEquals(16, ras.getFilePointer());
		ras.close();
		org.junit.jupiter.api.Assertions.assertThrows(IOException.class, ras::getFilePointer);
	}
}
