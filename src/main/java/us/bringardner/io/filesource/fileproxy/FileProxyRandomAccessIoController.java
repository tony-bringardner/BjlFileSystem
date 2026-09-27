package us.bringardner.io.filesource.fileproxy;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.IRandomAccessIoController;

/**
 * Random access to a local file using positional FileChannel reads and
 * writes, so no seek is needed per call. (This used to seek and then read or
 * write a single byte for every byte: about 1 microsecond per byte.)
 */
public class FileProxyRandomAccessIoController implements IRandomAccessIoController{

	private RandomAccessFile target;
	private FileChannel channel;
	private FileProxy file;
	private final ByteBuffer one = ByteBuffer.allocate(1);

	public FileProxyRandomAccessIoController(FileProxy file,String mode) throws IOException {
		this.file = file;
		this.target = new RandomAccessFile(file.target, mode);
		this.channel = target.getChannel();
	}

	@Override
	public void close() throws Exception {
		target.close();		
	}

	@Override
	public int read(long position) throws IOException {
		one.clear();
		int n = channel.read(one, position);
		return n <= 0 ? -1 : one.get(0) & 0xFF;
	}

	@Override
	public int read(long position, byte[] b, int off, int len) throws IOException {
		java.util.Objects.checkFromIndexSize(off, len, b.length);
		if( len == 0 ) {
			return 0;
		}
		ByteBuffer buf = ByteBuffer.wrap(b, off, len);
		int total = 0;
		// a positional read may return fewer bytes than asked; keep going until EOF
		while( buf.hasRemaining() ) {
			int n = channel.read(buf, position + total);
			if( n < 0 ) {
				break;
			}
			total += n;
		}
		return total == 0 ? -1 : total;
	}

	@Override
	public void write(long position, byte value) throws IOException {
		one.clear();
		one.put(0, value);
		while( one.hasRemaining() ) {
			channel.write(one, position);
		}
	}

	@Override
	public void write(long position, byte[] b, int off, int len) throws IOException {
		java.util.Objects.checkFromIndexSize(off, len, b.length);
		ByteBuffer buf = ByteBuffer.wrap(b, off, len);
		long pos = position;
		while( buf.hasRemaining() ) {
			pos += channel.write(buf, pos);
		}
	}

	@Override
	public long length() throws IOException {
		return channel.size();
	}

	@Override
	public void setLength(long newLength) throws IOException {
		target.setLength(newLength);		
	}

	@Override
	public void save() throws IOException {
		// Nothing to do
	}

	@Override
	public FileSource getFile() {
		return file;
	}

}
