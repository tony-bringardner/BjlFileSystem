package us.bringardner.io.filesource;

import java.io.IOException;

/**
 * Controls the IO for a random access stream
 */
public interface IRandomAccessIoController extends AutoCloseable {
	int read(long position) throws IOException;
	void write(long position, byte value) throws IOException;
	long length() throws IOException;
	void setLength(long newLength) throws IOException;
	void save() throws IOException;
}
