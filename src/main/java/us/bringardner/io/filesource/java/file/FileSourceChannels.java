package us.bringardner.io.filesource.java.file;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.SeekableByteChannel;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.IRandomAccessStream;

/**
 * SeekableByteChannel implementations used by FileSourceFileSystemProvider.newByteChannel,
 * which is what Files.readAllBytes, Files.readString, Files.lines, Files.write(..., options)
 * etc. are built on.
 */
final class FileSourceChannels {

	private FileSourceChannels() {
	}

	private static final int BUFFER_SIZE = 8192;

	private abstract static class Base implements SeekableByteChannel {
		protected final FileSource file;
		protected final boolean deleteOnClose;
		protected long position;
		private boolean open = true;

		Base(FileSource file, boolean deleteOnClose) {
			this.file = file;
			this.deleteOnClose = deleteOnClose;
		}

		protected void ensureOpen() throws ClosedChannelException {
			if( !open ) {
				throw new ClosedChannelException();
			}
		}

		@Override
		public boolean isOpen() {
			return open;
		}

		@Override
		public long position() throws IOException {
			ensureOpen();
			return position;
		}

		@Override
		public final void close() throws IOException {
			if( open ) {
				open = false;
				try {
					closeImpl();
				} finally {
					if( deleteOnClose ) {
						file.delete();
					}
				}
			}
		}

		protected abstract void closeImpl() throws IOException;

		/** Copy from src into a byte array (uses the backing array when there is one). */
		protected static int drain(ByteBuffer src, IoWriter writer) throws IOException {
			int len = src.remaining();
			if( src.hasArray() ) {
				writer.write(src.array(), src.arrayOffset() + src.position(), len);
				src.position(src.position() + len);
			} else {
				byte[] buf = new byte[Math.min(len, BUFFER_SIZE)];
				int left = len;
				while( left > 0 ) {
					int n = Math.min(left, buf.length);
					src.get(buf, 0, n);
					writer.write(buf, 0, n);
					left -= n;
				}
			}
			return len;
		}

		/** Read into dst (uses the backing array when there is one). */
		protected static int fill(ByteBuffer dst, IoReader reader) throws IOException {
			int len = dst.remaining();
			if( len == 0 ) {
				return 0;
			}
			int n;
			if( dst.hasArray() ) {
				n = reader.read(dst.array(), dst.arrayOffset() + dst.position(), len);
				if( n > 0 ) {
					dst.position(dst.position() + n);
				}
			} else {
				byte[] buf = new byte[Math.min(len, BUFFER_SIZE)];
				n = reader.read(buf, 0, buf.length);
				if( n > 0 ) {
					dst.put(buf, 0, n);
				}
			}
			return n;
		}
	}

	interface IoReader {
		int read(byte[] b, int off, int len) throws IOException;
	}

	interface IoWriter {
		void write(byte[] b, int off, int len) throws IOException;
	}

	/** Read-only channel over FileSource.getInputStream(); seeking backwards reopens the stream. */
	static final class Read extends Base {
		private InputStream in;
		private long streamPos;

		Read(FileSource file, boolean deleteOnClose) throws IOException {
			super(file, deleteOnClose);
			in = file.getInputStream();
		}

		@Override
		public int read(ByteBuffer dst) throws IOException {
			ensureOpen();
			if( !syncStream() ) {
				return -1;
			}
			int n = fill(dst, in::read);
			if( n > 0 ) {
				position += n;
				streamPos += n;
			}
			return n;
		}

		/** Move the stream to 'position'. @return false if EOF was reached first */
		private boolean syncStream() throws IOException {
			if( streamPos > position ) {
				in.close();
				in = file.getInputStream();
				streamPos = 0;
			}
			while( streamPos < position ) {
				long skipped = in.skip(position - streamPos);
				if( skipped <= 0 ) {
					if( in.read() < 0 ) {
						return false;
					}
					skipped = 1;
				}
				streamPos += skipped;
			}
			return true;
		}

		@Override
		public int write(ByteBuffer src) {
			throw new NonWritableChannelException();
		}

		@Override
		public SeekableByteChannel position(long newPosition) throws IOException {
			ensureOpen();
			if( newPosition < 0 ) {
				throw new IllegalArgumentException("Negative position");
			}
			position = newPosition;
			return this;
		}

		@Override
		public long size() throws IOException {
			ensureOpen();
			return file.length();
		}

		@Override
		public SeekableByteChannel truncate(long size) {
			throw new NonWritableChannelException();
		}

		@Override
		protected void closeImpl() throws IOException {
			in.close();
		}
	}

	/** Write-only, sequential channel over an OutputStream (truncating or appending). */
	static final class Write extends Base {
		private final OutputStream out;

		Write(FileSource file, OutputStream out, long startPosition, boolean deleteOnClose) {
			super(file, deleteOnClose);
			this.out = out;
			this.position = startPosition;
		}

		@Override
		public int read(ByteBuffer dst) {
			throw new NonReadableChannelException();
		}

		@Override
		public int write(ByteBuffer src) throws IOException {
			ensureOpen();
			int n = drain(src, out::write);
			position += n;
			return n;
		}

		@Override
		public SeekableByteChannel position(long newPosition) throws IOException {
			ensureOpen();
			if( newPosition != position ) {
				throw new UnsupportedOperationException("This channel can only write sequentially; open it with READ and WRITE to seek");
			}
			return this;
		}

		@Override
		public long size() throws IOException {
			ensureOpen();
			return position;
		}

		@Override
		public SeekableByteChannel truncate(long size) throws IOException {
			ensureOpen();
			if( size < position ) {
				throw new UnsupportedOperationException("This channel can only write sequentially; open it with READ and WRITE to truncate");
			}
			return this;
		}

		@Override
		protected void closeImpl() throws IOException {
			out.close();
		}
	}

	/** Read/write channel over FileSource.getRandomAccessStream("rw"). */
	static final class RandomAccess extends Base {
		private final IRandomAccessStream ra;
		private final boolean readable;
		private final boolean writable;
		private final boolean append;

		RandomAccess(FileSource file, IRandomAccessStream ra, boolean readable, boolean writable, boolean append, boolean deleteOnClose) throws IOException {
			super(file, deleteOnClose);
			this.ra = ra;
			this.readable = readable;
			this.writable = writable;
			this.append = append;
		}

		@Override
		public int read(ByteBuffer dst) throws IOException {
			ensureOpen();
			if( !readable ) {
				throw new NonReadableChannelException();
			}
			if( position >= ra.length() ) {
				return -1;
			}
			ra.seek(position);
			int n = fill(dst, ra::read);
			if( n > 0 ) {
				position += n;
			}
			return n;
		}

		@Override
		public int write(ByteBuffer src) throws IOException {
			ensureOpen();
			if( !writable ) {
				throw new NonWritableChannelException();
			}
			if( append ) {
				position = ra.length();
			}
			ra.seek(position);
			int n = drain(src, ra::write);
			position += n;
			return n;
		}

		@Override
		public SeekableByteChannel position(long newPosition) throws IOException {
			ensureOpen();
			if( newPosition < 0 ) {
				throw new IllegalArgumentException("Negative position");
			}
			position = newPosition;
			return this;
		}

		@Override
		public long size() throws IOException {
			ensureOpen();
			return ra.length();
		}

		@Override
		public SeekableByteChannel truncate(long size) throws IOException {
			ensureOpen();
			if( size < 0 ) {
				throw new IllegalArgumentException("Negative size");
			}
			if( !writable ) {
				throw new NonWritableChannelException();
			}
			if( size < ra.length() ) {
				ra.setLength(size);
			}
			if( position > size ) {
				position = size;
			}
			return this;
		}

		@Override
		protected void closeImpl() throws IOException {
			ra.close();
		}
	}

	/** OutputStream that writes through a RandomAccess stream from position 0 (WRITE without TRUNCATE_EXISTING). */
	static OutputStream overwritingStream(IRandomAccessStream ra) {
		return new OutputStream() {
			@Override
			public void write(int b) throws IOException {
				ra.write(b);
			}

			@Override
			public void write(byte[] b, int off, int len) throws IOException {
				ra.write(b, off, len);
			}

			@Override
			public void close() throws IOException {
				ra.close();
			}
		};
	}

	/** Deletes the file after the stream is closed (DELETE_ON_CLOSE). */
	static OutputStream deleteOnClose(OutputStream out, FileSource file) {
		return new OutputStream() {
			private boolean closed;

			@Override
			public void write(int b) throws IOException {
				out.write(b);
			}

			@Override
			public void write(byte[] b, int off, int len) throws IOException {
				out.write(b, off, len);
			}

			@Override
			public void flush() throws IOException {
				out.flush();
			}

			@Override
			public void close() throws IOException {
				if( !closed ) {
					closed = true;
					try {
						out.close();
					} finally {
						file.delete();
					}
				}
			}
		};
	}
}
