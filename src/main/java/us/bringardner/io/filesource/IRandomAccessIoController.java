package us.bringardner.io.filesource;

import java.io.IOException;

/**
 * Controls the IO for a random access stream
 */
public interface IRandomAccessIoController extends AutoCloseable {
	int read(long position) throws IOException;
	void write(long position, byte value) throws IOException;

	/**
	 * Read up to len bytes starting at position into b[off..].
	 * Implementations should override this: the default calls read(long)
	 * once per byte.
	 * @return the number of bytes read, or -1 if position is at or past EOF
	 */
	default int read(long position, byte[] b, int off, int len) throws IOException {
		java.util.Objects.checkFromIndexSize(off, len, b.length);
		if( len == 0 ) {
			return 0;
		}
		int count = 0;
		while( count < len ) {
			int v = read(position + count);
			if( v < 0 ) {
				break;
			}
			b[off + count++] = (byte) v;
		}
		return count == 0 ? -1 : count;
	}

	/**
	 * Write len bytes from b[off..] starting at position.
	 * Implementations should override this: the default calls
	 * write(long, byte) once per byte.
	 */
	default void write(long position, byte[] b, int off, int len) throws IOException {
		java.util.Objects.checkFromIndexSize(off, len, b.length);
		for(int i = 0; i < len; i++) {
			write(position + i, b[off + i]);
		}
	}
	long length() throws IOException;
	void setLength(long newLength) throws IOException;
	void save() throws IOException;
	FileSource getFile();
}
