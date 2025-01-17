package us.bringardner.io.filesource;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UTFDataFormatException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;



/**
 * Instances of this class support both reading and writing to a
 * random access file. A random access file behaves like a large
 * array of bytes stored in the file system. There is a kind of cursor,
 * or index into the implied array, called the <em>file pointer</em>;
 * input operations read bytes starting at the file pointer and advance
 * the file pointer past the bytes read. If the random access file is
 * created in read/write mode, then output operations are also available;
 * output operations write bytes starting at the file pointer and advance
 * the file pointer past the bytes written. Output operations that write
 * past the current end of the implied array cause the array to be
 * extended. The file pointer can be read by the
 * {@code getFilePointer} method and set by the {@code seek}
 * method.
 * <p>
 * It is generally true of all the reading routines in this class that
 * if end-of-file is reached before the desired number of bytes has been
 * read, an {@code EOFException} (which is a kind of
 * {@code IOException}) is thrown. If any byte cannot be read for
 * any reason other than end-of-file, an {@code IOException} other
 * than {@code EOFException} is thrown. In particular, an
 * {@code IOException} may be thrown if the stream has been closed.
 *
 * @since   1.0
 */

public abstract class AbstractRandomAccessStream implements IRandomAccessStream {


	private static class ByteArray {

		private static final VarHandle SHORT = create(short[].class);
		//private static final VarHandle CHAR = create(char[].class);
		private static final VarHandle INT = create(int[].class);
		//private static final VarHandle FLOAT = create(float[].class);
		private static final VarHandle LONG = create(long[].class);
		//private static final VarHandle DOUBLE = create(double[].class);

		/*
		 * Methods for unpacking primitive values from byte arrays starting at
		 * a given offset.
		 */

		/**
		 * {@return a {@code boolean} from the provided {@code array} at the given {@code offset}}.
		 *
		 * @param array  to read a value from.
		 * @param offset where extraction in the array should begin
		 * @throws IndexOutOfBoundsException if the provided {@code offset} is outside
		 *                                   the range [0, array.length - 1]
		 * @see #setBoolean(byte[], int, boolean)
		 *
		    public static boolean getBoolean(byte[] array, int offset) {
		        return array[offset] != 0;
		    }
		 */
		/**
		 * {@return a {@code char} from the provided {@code array} at the given {@code offset}
		 * using big endian order}.
		 * <p>
		 * There are no access alignment requirements.
		 *
		 * @param array  to get a value from.
		 * @param offset where extraction in the array should begin
		 * @throws IndexOutOfBoundsException if the provided {@code offset} is outside
		 *                                   the range [0, array.length - 2]
		 * @see #setChar(byte[], int, char)
		 *
		    public static char getChar(byte[] array, int offset) {
		        return (char) CHAR.get(array, offset);
		    }
		 */

		/**
		 * {@return a {@code short} from the provided {@code array} at the given {@code offset}
		 * using big endian order}.
		 * <p>
		 * There are no access alignment requirements.
		 *
		 * @param array  to get a value from.
		 * @param offset where extraction in the array should begin
		 * @return a {@code short} from the array
		 * @throws IndexOutOfBoundsException if the provided {@code offset} is outside
		 *                                   the range [0, array.length - 2]
		 * @see #setShort(byte[], int, short)
		 *
		    public static short getShort(byte[] array, int offset) {
		        return (short) SHORT.get(array, offset);
		    }
		 */
		/**
		 * {@return an {@code unsigned short} from the provided {@code array} at the given {@code offset}
		 * using big endian order}.
		 * <p>
		 * There are no access alignment requirements.
		 *
		 * @param array  to get a value from.
		 * @param offset where extraction in the array should begin
		 * @return an {@code int} representing an unsigned short from the array
		 * @throws IndexOutOfBoundsException if the provided {@code offset} is outside
		 *                                   the range [0, array.length - 2]
		 * @see #setUnsignedShort(byte[], int, int)
		 *
		    public static int getUnsignedShort(byte[] array, int offset) {
		        return Short.toUnsignedInt((short) SHORT.get(array, offset));
		    }
		 */

		/**
		 * {@return an {@code int} from the provided {@code array} at the given {@code offset}
		 * using big endian order}.
		 * <p>
		 * There are no access alignment requirements.
		 *
		 * @param array  to get a value from.
		 * @param offset where extraction in the array should begin
		 * @throws IndexOutOfBoundsException if the provided {@code offset} is outside
		 *                                   the range [0, array.length - 4]
		 * @see #setInt(byte[], int, int)
		 */
		public static int getInt(byte[] array, int offset) {
			return (int) INT.get(array, offset);
		}

		/**
		 * {@return a {@code float} from the provided {@code array} at the given {@code offset}
		 * using big endian order}.
		 * <p>
		 * Variants of {@linkplain Float#NaN } values are canonized to a single NaN value.
		 * <p>
		 * There are no access alignment requirements.
		 *
		 * @param array  to get a value from.
		 * @param offset where extraction in the array should begin
		 * @throws IndexOutOfBoundsException if the provided {@code offset} is outside
		 *                                   the range [0, array.length - 4]
		 * @see #setFloat(byte[], int, float)
		 */
		public static float getFloat(byte[] array, int offset) {
			// Using Float.intBitsToFloat collapses NaN values to a single
			// "canonical" NaN value
			return Float.intBitsToFloat((int) INT.get(array, offset));
		}

		/**
		 * {@return a {@code float} from the provided {@code array} at the given {@code offset}
		 * using big endian order}.
		 * <p>
		 * Variants of {@linkplain Float#NaN } values are silently read according
		 * to their bit patterns.
		 * <p>
		 * There are no access alignment requirements.
		 *
		 * @param array  to get a value from.
		 * @param offset where extraction in the array should begin
		 * @throws IndexOutOfBoundsException if the provided {@code offset} is outside
		 *                                   the range [0, array.length - 4]
		 * @see #setFloatRaw(byte[], int, float)
		 *
		    public static float getFloatRaw(byte[] array, int offset) {
		        // Just gets the bits as they are
		        return (float) FLOAT.get(array, offset);
		    }
		 */

		/**
		 * {@return a {@code long} from the provided {@code array} at the given {@code offset}
		 * using big endian order}.
		 * <p>
		 * There are no access alignment requirements.
		 *
		 * @param array  to get a value from.
		 * @param offset where extraction in the array should begin
		 * @throws IndexOutOfBoundsException if the provided {@code offset} is outside
		 *                                   the range [0, array.length - 8]
		 * @see #setLong(byte[], int, long)
		 */
		public static long getLong(byte[] array, int offset) {
			return (long) LONG.get(array, offset);
		}

		/**
		 * {@return a {@code double} from the provided {@code array} at the given {@code offset}
		 * using big endian order}.
		 * <p>
		 * Variants of {@linkplain Double#NaN } values are canonized to a single NaN value.
		 * <p>
		 * There are no access alignment requirements.
		 *
		 * @param array  to get a value from.
		 * @param offset where extraction in the array should begin
		 * @throws IndexOutOfBoundsException if the provided {@code offset} is outside
		 *                                   the range [0, array.length - 8]
		 * @see #setDouble(byte[], int, double)
		 */
		public static double getDouble(byte[] array, int offset) {
			// Using Double.longBitsToDouble collapses NaN values to a single
			// "canonical" NaN value
			return Double.longBitsToDouble((long) LONG.get(array, offset));
		}

		/**
		 * {@return a {@code double} from the provided {@code array} at the given {@code offset}
		 * using big endian order}.
		 * <p>
		 * Variants of {@linkplain Double#NaN } values are silently read according to
		 * their bit patterns.
		 * <p>
		 * There are no access alignment requirements.
		 *
		 * @param array  to get a value from.
		 * @param offset where extraction in the array should begin
		 * @throws IndexOutOfBoundsException if the provided {@code offset} is outside
		 *                                   the range [0, array.length - 8]
		 * @see #setDoubleRaw(byte[], int, double)
		 *
		    public static double getDoubleRaw(byte[] array, int offset) {
		        // Just gets the bits as they are
		        return (double) DOUBLE.get(array, offset);
		    }
		 */

		/*
		 * Methods for packing primitive values into byte arrays starting at a given
		 * offset.
		 */

		/**
		 * Sets (writes) the provided {@code value} into
		 * the provided {@code array} beginning at the given {@code offset}.
		 *
		 * @param array  to set (write) a value into
		 * @param offset where setting (writing) in the array should begin
		 * @param value  value to set in the array
		 * @throws IndexOutOfBoundsException if the provided {@code offset} is outside
		 *                                   the range [0, array.length]
		 * @see #getBoolean(byte[], int)
		 *
		    public static void setBoolean(byte[] array, int offset, boolean value) {
		        array[offset] = (byte) (value ? 1 : 0);
		    }
		 */

		/**
		 * Sets (writes) the provided {@code value} using big endian order into
		 * the provided {@code array} beginning at the given {@code offset}.
		 * <p>
		 * There are no access alignment requirements.
		 *
		 * @param array  to set (write) a value into
		 * @param offset where setting (writing) in the array should begin
		 * @param value  value to set in the array
		 * @throws IndexOutOfBoundsException if the provided {@code offset} is outside
		 *                                   the range [0, array.length - 2]
		 * @see #getChar(byte[], int)
		 *
		    public static void setChar(byte[] array, int offset, char value) {
		        CHAR.set(array, offset, value);
		    }
		 */

		/**
		 * Sets (writes) the provided {@code value} using big endian order into
		 * the provided {@code array} beginning at the given {@code offset}.
		 * <p>
		 * There are no access alignment requirements.
		 *
		 * @param array  to set (write) a value into
		 * @param offset where setting (writing) in the array should begin
		 * @param value  value to set in the array
		 * @throws IndexOutOfBoundsException if the provided {@code offset} is outside
		 *                                   the range [0, array.length - 2]
		 * @see #getShort(byte[], int)
		 *
		    public static void setShort(byte[] array, int offset, short value) {
		        SHORT.set(array, offset, value);
		    }
		 */

		/**
		 * Sets (writes) the provided {@code value} using big endian order into
		 * the provided {@code array} beginning at the given {@code offset}.
		 * <p>
		 * There are no access alignment requirements.
		 *
		 * @param array  to set (write) a value into
		 * @param offset where setting (writing) in the array should begin
		 * @param value  value to set in the array
		 * @throws IndexOutOfBoundsException if the provided {@code offset} is outside
		 *                                   the range [0, array.length - 2]
		 * @see #getUnsignedShort(byte[], int)
		 */
		public static void setUnsignedShort(byte[] array, int offset, int value) {
			SHORT.set(array, offset, (short) (char) value);
		}

		/**
		 * Sets (writes) the provided {@code value} using big endian order into
		 * the provided {@code array} beginning at the given {@code offset}.
		 * <p>
		 * There are no access alignment requirements.
		 *
		 * @param array  to set (write) a value into
		 * @param offset where setting (writing) in the array should begin
		 * @param value  value to set in the array
		 * @throws IndexOutOfBoundsException if the provided {@code offset} is outside
		 *                                   the range [0, array.length - 4]
		 * @see #getInt(byte[], int)
		 */
		public static void setInt(byte[] array, int offset, int value) {
			INT.set(array, offset, value);
		}

		/**
		 * Sets (writes) the provided {@code value} using big endian order into
		 * the provided {@code array} beginning at the given {@code offset}.
		 * <p>
		 * Variants of {@linkplain Float#NaN } values are canonized to a single NaN value.
		 * <p>
		 * There are no access alignment requirements.
		 *
		 * @param array  to set (write) a value into
		 * @param offset where setting (writing) in the array should begin
		 * @param value  value to set in the array
		 * @throws IndexOutOfBoundsException if the provided {@code offset} is outside
		 *                                   the range [0, array.length - 2]
		 * @see #getFloat(byte[], int)
		 */
		public static void setFloat(byte[] array, int offset, float value) {
			// Using Float.floatToIntBits collapses NaN values to a single
			// "canonical" NaN value
			INT.set(array, offset, Float.floatToIntBits(value));
		}

		/**
		 * Sets (writes) the provided {@code value} using big endian order into
		 * the provided {@code array} beginning at the given {@code offset}.
		 * <p>
		 * Variants of {@linkplain Float#NaN } values are silently written according to
		 * their bit patterns.
		 * <p>
		 * There are no access alignment requirements.
		 *
		 * @param array  to set (write) a value into
		 * @param offset where setting (writing) in the array should begin
		 * @param value  value to set in the array
		 * @throws IndexOutOfBoundsException if the provided {@code offset} is outside
		 *                                   the range [0, array.length - 2]
		 * @see #getFloatRaw(byte[], int)
		 *
		    public static void setFloatRaw(byte[] array, int offset, float value) {
		        // Just sets the bits as they are
		        FLOAT.set(array, offset, value);
		    }
		 */

		/**
		 * Sets (writes) the provided {@code value} using big endian order into
		 * the provided {@code array} beginning at the given {@code offset}.
		 * <p>
		 * There are no access alignment requirements.
		 *
		 * @param array  to set (write) a value into
		 * @param offset where setting (writing) in the array should begin
		 * @param value  value to set in the array
		 * @throws IndexOutOfBoundsException if the provided {@code offset} is outside
		 *                                   the range [0, array.length - 4]
		 * @see #getLong(byte[], int)
		 */
		public static void setLong(byte[] array, int offset, long value) {
			LONG.set(array, offset, value);
		}

		/**
		 * Sets (writes) the provided {@code value} using big endian order into
		 * the provided {@code array} beginning at the given {@code offset}.
		 * <p>
		 * Variants of {@linkplain Double#NaN } values are canonized to a single NaN value.
		 * <p>
		 * There are no access alignment requirements.
		 *
		 * @param array  to set (write) a value into
		 * @param offset where setting (writing) in the array should begin
		 * @param value  value to set in the array
		 * @throws IndexOutOfBoundsException if the provided {@code offset} is outside
		 *                                   the range [0, array.length - 2]
		 * @see #getDouble(byte[], int)
		 */
		public static void setDouble(byte[] array, int offset, double value) {
			// Using Double.doubleToLongBits collapses NaN values to a single
			// "canonical" NaN value
			LONG.set(array, offset, Double.doubleToLongBits(value));
		}

		/**
		 * Sets (writes) the provided {@code value} using big endian order into
		 * the provided {@code array} beginning at the given {@code offset}.
		 * <p>
		 * Variants of {@linkplain Double#NaN } values are silently written according to
		 * their bit patterns.
		 * <p>
		 * There are no access alignment requirements.
		 *
		 * @param array  to set (write) a value into
		 * @param offset where setting (writing) in the array should begin
		 * @param value  value to set in the array
		 * @throws IndexOutOfBoundsException if the provided {@code offset} is outside
		 *                                   the range [0, array.length - 2]
		 * @see #getDoubleRaw(byte[], int)
		 *
		    public static void setDoubleRaw(byte[] array, int offset, double value) {
		        // Just sets the bits as they are
		        DOUBLE.set(array, offset, value);
		    }
		 */

		private static VarHandle create(Class<?> viewArrayClass) {
			return MethodHandles.byteArrayViewVarHandle(viewArrayClass, ByteOrder.BIG_ENDIAN);
		}		
	}




	/**
	 * A local buffer that allows reading and writing of
	 * longer primitive parameters (e.g. long) to be performed
	 * using bulk operations rather than on a per-byte basis.
	 */
	private final byte[] buffer = new byte[Long.BYTES];
	protected FileSource file;
	protected boolean readOnly=false;

	public AbstractRandomAccessStream(FileSource file, String mode) throws IOException {
		this.file = file;

		// read only causes file not found
		// rw creates an empty file
		if( mode.equals("r")) {
			if( !file.isFile() || !file.canRead()) {
				throw new FileNotFoundException(file.getAbsolutePath()+" is not a valid readable file");
			}
			readOnly = true;
		} else if(mode.equals("w") || mode.startsWith("rw")) {
			if( !file.exists()) {
				if( !file.createNewFile()) {
					throw new IOException("Could not create "+file);
				}
			}
			if( !file.isFile() || !file.canRead() || !file.canWrite()) {
				throw new FileNotFoundException(file.getAbsolutePath()+" is not a valid readable/writable file");
			}
		} else {
			throw new IllegalStateException("invalid mode = "+mode);
		}


	}


	// 'Read' primitives

	/**
	 * Reads a byte of data from this file. The byte is returned as an
	 * integer in the range 0 to 255 ({@code 0x00-0x0ff}). This
	 * method blocks if no input is yet available.
	 * <p>
	 * Although {@code AbstractRandomAccessStream} is not a subclass of
	 * {@code InputStream}, this method behaves in exactly the same
	 * way as the {@link InputStream#read()} method of
	 * {@code InputStream}.
	 *
	 * @return     the next byte of data, or {@code -1} if the end of the
	 *             file has been reached.
	 * @throws     IOException  if an I/O error occurs. Not thrown if
	 *                          end-of-file has been reached.
	 */
	public abstract int read() throws IOException ;

	/**
	 * Reads a sub array as a sequence of bytes.
	 * @param     b the buffer into which the data is read.
	 * @param     off the start offset of the data.
	 * @param     len the number of bytes to read.
	 * @throws    IOException If an I/O error has occurred.
	 */
	public abstract int readBytes(byte[] b, int off, int len) throws IOException; 

	/**
	 * Reads up to {@code len} bytes of data from this file into an
	 * array of bytes. This method blocks until at least one byte of input
	 * is available.
	 * <p>
	 * Although {@code AbstractRandomAccessStream} is not a subclass of
	 * {@code InputStream}, this method behaves in exactly the
	 * same way as the {@link InputStream#read(byte[], int, int)} method of
	 * {@code InputStream}.
	 *
	 * @param      b     the buffer into which the data is read.
	 * @param      off   the start offset in array {@code b}
	 *                   at which the data is written.
	 * @param      len   the maximum number of bytes read.
	 * @return     the total number of bytes read into the buffer, or
	 *             {@code -1} if there is no more data because the end of
	 *             the file has been reached.
	 * @throws     IOException If the first byte cannot be read for any reason
	 *             other than end of file, or if the random access file has been closed,
	 *             or if some other I/O error occurs.
	 * @throws     NullPointerException If {@code b} is {@code null}.
	 * @throws     IndexOutOfBoundsException If {@code off} is negative,
	 *             {@code len} is negative, or {@code len} is greater than
	 *             {@code b.length - off}
	 */
	public int read(byte[] b, int off, int len) throws IOException {
		return readBytes(b, off, len);
	}

	/**
	 * Reads up to {@code b.length} bytes of data from this file
	 * into an array of bytes. This method blocks until at least one byte
	 * of input is available.
	 * <p>
	 * Although {@code AbstractRandomAccessStream} is not a subclass of
	 * {@code InputStream}, this method behaves in exactly the
	 * same way as the {@link InputStream#read(byte[])} method of
	 * {@code InputStream}.
	 *
	 * @param      b   the buffer into which the data is read.
	 * @return     the total number of bytes read into the buffer, or
	 *             {@code -1} if there is no more data because the end of
	 *             this file has been reached.
	 * @throws     IOException If the first byte cannot be read for any reason
	 *             other than end of file, or if the random access file has been closed,
	 *             or if some other I/O error occurs.
	 * @throws     NullPointerException If {@code b} is {@code null}.
	 */
	public int read(byte[] b) throws IOException {
		return readBytes(b, 0, b.length);
	}

	/**
	 * Reads {@code b.length} bytes from this file into the byte
	 * array, starting at the current file pointer. This method reads
	 * repeatedly from the file until the requested number of bytes are
	 * read. This method blocks until the requested number of bytes are
	 * read, the end of the stream is detected, or an exception is thrown.
	 *
	 * @param   b   the buffer into which the data is read.
	 * @throws  NullPointerException if {@code b} is {@code null}.
	 * @throws  EOFException  if this file reaches the end before reading
	 *              all the bytes.
	 * @throws  IOException   if an I/O error occurs.
	 */
	public  void readFully(byte[] b) throws IOException {
		readFully(b, 0, b.length);
	}

	/**
	 * Reads exactly {@code len} bytes from this file into the byte
	 * array, starting at the current file pointer. This method reads
	 * repeatedly from the file until the requested number of bytes are
	 * read. This method blocks until the requested number of bytes are
	 * read, the end of the stream is detected, or an exception is thrown.
	 *
	 * @param   b     the buffer into which the data is read.
	 * @param   off   the start offset into the data array {@code b}.
	 * @param   len   the number of bytes to read.
	 * @throws  NullPointerException if {@code b} is {@code null}.
	 * @throws  IndexOutOfBoundsException if {@code off} is negative,
	 *                {@code len} is negative, or {@code len} is greater than
	 *                {@code b.length - off}.
	 * @throws  EOFException  if this file reaches the end before reading
	 *                all the bytes.
	 * @throws  IOException   if an I/O error occurs.
	 */
	public  void readFully(byte[] b, int off, int len) throws IOException {
		int n = 0;
		do {
			int count = this.read(b, off + n, len - n);
			if (count < 0)
				throw new EOFException();
			n += count;
		} while (n < len);
	}

	/**
	 * Attempts to skip over {@code n} bytes of input discarding the
	 * skipped bytes.
	 * <p>
	 *
	 * This method may skip over some smaller number of bytes, possibly zero.
	 * This may result from any of a number of conditions; reaching end of
	 * file before {@code n} bytes have been skipped is only one
	 * possibility. This method never throws an {@code EOFException}.
	 * The actual number of bytes skipped is returned.  If {@code n}
	 * is negative, no bytes are skipped.
	 *
	 * @param      n   the number of bytes to be skipped.
	 * @return     the actual number of bytes skipped.
	 * @throws     IOException  if an I/O error occurs.
	 */
	public int skipBytes(int n) throws IOException {
		long pos;
		long len;
		long newpos;

		if (n <= 0) {
			return 0;
		}
		pos = getFilePointer();
		len = length();
		newpos = pos + n;
		if (newpos > len) {
			newpos = len;
		}
		seek(newpos);

		/* return the actual number of bytes skipped */
		return (int) (newpos - pos);
	}

	// 'Write' primitives

	/**
	 * Writes the specified byte to this file. The write starts at
	 * the current file pointer.
	 *
	 * @param      b   the {@code byte} to be written.
	 * @throws     IOException  if an I/O error occurs.
	 */
	public abstract void write(int b) throws IOException ;


	/**
	 * Writes a sub array as a sequence of bytes.
	 *
	 * @param     b the data to be written
	 * @param     off the start offset in the data
	 * @param     len the number of bytes that are written
	 * @throws    IOException If an I/O error has occurred.
	 */
	public abstract void writeBytes(byte[] b, int off, int len) throws IOException ;

	/**
	 * Writes {@code b.length} bytes from the specified byte array
	 * to this file, starting at the current file pointer.
	 *
	 * @param      b   the data.
	 * @throws     IOException  if an I/O error occurs.
	 */
	public void write(byte[] b) throws IOException {
		writeBytes(b, 0, b.length);
	}

	/**
	 * Writes {@code len} bytes from the specified byte array
	 * starting at offset {@code off} to this file.
	 *
	 * @param      b     the data.
	 * @param      off   the start offset in the data.
	 * @param      len   the number of bytes to write.
	 * @throws     IOException  if an I/O error occurs.
	 * @throws     IndexOutOfBoundsException {@inheritDoc}
	 */
	public void write(byte[] b, int off, int len) throws IOException {
		writeBytes(b, off, len);
	}

	// 'Random access' stuff

	/**
	 * Returns the current offset in this file.
	 *
	 * @return     the offset from the beginning of the file, in bytes,
	 *             at which the next read or write occurs.
	 * @throws     IOException  if an I/O error occurs.
	 */
	public abstract long getFilePointer() throws IOException;

	/**
	 * Sets the file-pointer offset, measured from the beginning of this
	 * file, at which the next read or write occurs.  The offset may be
	 * set beyond the end of the file. Setting the offset beyond the end
	 * of the file does not change the file length.  The file length will
	 * change only by writing after the offset has been set beyond the end
	 * of the file.
	 *
	 * @param      pos   the offset position, measured in bytes from the
	 *                   beginning of the file, at which to set the file
	 *                   pointer.
	 * @throws     IOException  if {@code pos} is less than
	 *                          {@code 0} or if an I/O error occurs.
	 */
	public abstract void seek(long pos) throws IOException ;

	/**
	 * Returns the length of this file.
	 *
	 * @return     the length of this file, measured in bytes.
	 * @throws     IOException  if an I/O error occurs.
	 */
	public abstract long length() throws IOException ;


	/**
	 * Sets the length of this file.
	 *
	 * <p> If the present length of the file as returned by the
	 * {@code length} method is greater than the {@code newLength}
	 * argument then the file will be truncated.  In this case, if the file
	 * offset as returned by the {@code getFilePointer} method is greater
	 * than {@code newLength} then after this method returns the offset
	 * will be equal to {@code newLength}.
	 *
	 * <p> If the present length of the file as returned by the
	 * {@code length} method is smaller than the {@code newLength}
	 * argument then the file will be extended.  In this case, the contents of
	 * the extended portion of the file are not defined.
	 *
	 * @param      newLength    The desired length of the file
	 * @throws     IOException  If an I/O error occurs
	 * @since      1.2
	 */
	public abstract void setLength(long newLength) throws IOException ;

	/**
	 * Closes this random access file stream and releases any system
	 * resources associated with the stream. A closed random access
	 * file cannot perform input or output operations and cannot be
	 * reopened.
	 *
	 * <p> If this file has an associated channel then the channel is closed
	 * as well.
	 *
	 * @apiNote
	 * If this stream has an associated channel then this method will close the
	 * channel, which in turn will close this stream. Subclasses that override
	 * this method should be prepared to handle possible reentrant invocation.
	 *
	 * @throws     IOException  if an I/O error occurs.
	 *
	 * @revised 1.4
	 */
	public abstract void close() throws IOException ;


	//
	//  Some "reading/writing Java data types" methods stolen from
	//  DataInputStream and DataOutputStream.
	//

	/**
	 * Reads a {@code boolean} from this file. This method reads a
	 * single byte from the file, starting at the current file pointer.
	 * A value of {@code 0} represents
	 * {@code false}. Any other value represents {@code true}.
	 * This method blocks until the byte is read, the end of the stream
	 * is detected, or an exception is thrown.
	 *
	 * @return     the {@code boolean} value read.
	 * @throws     EOFException  if this file has reached the end.
	 * @throws     IOException   if an I/O error occurs.
	 */
	public  boolean readBoolean() throws IOException {
		return readUnsignedByte() != 0;
	}

	/**
	 * Reads a signed eight-bit value from this file. This method reads a
	 * byte from the file, starting from the current file pointer.
	 * If the byte read is {@code b}, where
	 * {@code 0 <= b <= 255},
	 * then the result is:
	 * {@snippet lang=java :
	 *     (byte)(b)
	 * }
	 * <p>
	 * This method blocks until the byte is read, the end of the stream
	 * is detected, or an exception is thrown.
	 *
	 * @return     the next byte of this file as a signed eight-bit
	 *             {@code byte}.
	 * @throws     EOFException  if this file has reached the end.
	 * @throws     IOException   if an I/O error occurs.
	 */
	public  byte readByte() throws IOException {
		return (byte) readUnsignedByte();
	}

	/**
	 * Reads an unsigned eight-bit number from this file. This method reads
	 * a byte from this file, starting at the current file pointer,
	 * and returns that byte.
	 * <p>
	 * This method blocks until the byte is read, the end of the stream
	 * is detected, or an exception is thrown.
	 *
	 * @return     the next byte of this file, interpreted as an unsigned
	 *             eight-bit number.
	 * @throws     EOFException  if this file has reached the end.
	 * @throws     IOException   if an I/O error occurs.
	 */
	public  int readUnsignedByte() throws IOException {
		int ch = this.read();
		if (ch < 0)
			throw new EOFException();
		return ch;
	}

	/**
	 * Reads a signed 16-bit number from this file. The method reads two
	 * bytes from this file, starting at the current file pointer.
	 * If the two bytes read, in order, are
	 * {@code b1} and {@code b2}, where each of the two values is
	 * between {@code 0} and {@code 255}, inclusive, then the
	 * result is equal to:
	 * {@snippet lang=java :
	 *     (short)((b1 << 8) | b2)
	 * }
	 * <p>
	 * This method blocks until the two bytes are read, the end of the
	 * stream is detected, or an exception is thrown.
	 *
	 * @return     the next two bytes of this file, interpreted as a signed
	 *             16-bit number.
	 * @throws     EOFException  if this file reaches the end before reading
	 *               two bytes.
	 * @throws     IOException   if an I/O error occurs.
	 */
	public  short readShort() throws IOException {
		return (short) readUnsignedShort();
	}

	/**
	 * Reads an unsigned 16-bit number from this file. This method reads
	 * two bytes from the file, starting at the current file pointer.
	 * If the bytes read, in order, are
	 * {@code b1} and {@code b2}, where
	 * {@code 0 <= b1, b2 <= 255},
	 * then the result is equal to:
	 * {@snippet lang=java :
	 *     (b1 << 8) | b2
	 * }
	 * <p>
	 * This method blocks until the two bytes are read, the end of the
	 * stream is detected, or an exception is thrown.
	 *
	 * @return     the next two bytes of this file, interpreted as an unsigned
	 *             16-bit integer.
	 * @throws     EOFException  if this file reaches the end before reading
	 *               two bytes.
	 * @throws     IOException   if an I/O error occurs.
	 */
	public  int readUnsignedShort() throws IOException {
		readFully(buffer, 0, Short.BYTES);
		return  ((buffer[1] & 0xff)      ) +
				((buffer[0] & 0xff) <<  8);
	}

	/**
	 * Reads a character from this file. This method reads two
	 * bytes from the file, starting at the current file pointer.
	 * If the bytes read, in order, are
	 * {@code b1} and {@code b2}, where
	 * {@code 0 <= b1, b2 <= 255},
	 * then the result is equal to:
	 * {@snippet lang=java :
	 *     (char)((b1 << 8) | b2)
	 * }
	 * <p>
	 * This method blocks until the two bytes are read, the end of the
	 * stream is detected, or an exception is thrown.
	 *
	 * @return     the next two bytes of this file, interpreted as a
	 *                  {@code char}.
	 * @throws     EOFException  if this file reaches the end before reading
	 *               two bytes.
	 * @throws     IOException   if an I/O error occurs.
	 */
	public  char readChar() throws IOException {
		return (char) readUnsignedShort();
	}

	/**
	 * Reads a signed 32-bit integer from this file. This method reads 4
	 * bytes from the file, starting at the current file pointer.
	 * If the bytes read, in order, are {@code b1},
	 * {@code b2}, {@code b3}, and {@code b4}, where
	 * {@code 0 <= b1, b2, b3, b4 <= 255},
	 * then the result is equal to:
	 * {@snippet lang=java :
	 *     (b1 << 24) | (b2 << 16) + (b3 << 8) + b4
	 * }
	 * <p>
	 * This method blocks until the four bytes are read, the end of the
	 * stream is detected, or an exception is thrown.
	 *
	 * @return     the next four bytes of this file, interpreted as an
	 *             {@code int}.
	 * @throws     EOFException  if this file reaches the end before reading
	 *               four bytes.
	 * @throws     IOException   if an I/O error occurs.
	 */
	public  int readInt() throws IOException {
		readFully(buffer, 0, Integer.BYTES);
		return ByteArray.getInt(buffer, 0);
	}

	/**
	 * Reads a signed 64-bit integer from this file. This method reads eight
	 * bytes from the file, starting at the current file pointer.
	 * If the bytes read, in order, are
	 * {@code b1}, {@code b2}, {@code b3},
	 * {@code b4}, {@code b5}, {@code b6},
	 * {@code b7}, and {@code b8,} where:
	 * {@snippet :
	 *     0 <= b1, b2, b3, b4, b5, b6, b7, b8 <= 255
	 * }
	 * <p>
	 * then the result is equal to:
	 * {@snippet lang=java :
	 *     ((long)b1 << 56) + ((long)b2 << 48)
	 *         + ((long)b3 << 40) + ((long)b4 << 32)
	 *         + ((long)b5 << 24) + ((long)b6 << 16)
	 *         + ((long)b7 << 8) + b8
	 * }
	 * <p>
	 * This method blocks until the eight bytes are read, the end of the
	 * stream is detected, or an exception is thrown.
	 *
	 * @return     the next eight bytes of this file, interpreted as a
	 *             {@code long}.
	 * @throws     EOFException  if this file reaches the end before reading
	 *               eight bytes.
	 * @throws     IOException   if an I/O error occurs.
	 */
	public  long readLong() throws IOException {
		readFully(buffer, 0, Long.BYTES);
		return ByteArray.getLong(buffer, 0);
	}

	/**
	 * Reads a {@code float} from this file. This method reads an
	 * {@code int} value, starting at the current file pointer,
	 * as if by the {@code readInt} method
	 * and then converts that {@code int} to a {@code float}
	 * using the {@code intBitsToFloat} method in class
	 * {@code Float}.
	 * <p>
	 * This method blocks until the four bytes are read, the end of the
	 * stream is detected, or an exception is thrown.
	 *
	 * @return     the next four bytes of this file, interpreted as a
	 *             {@code float}.
	 * @throws     EOFException  if this file reaches the end before reading
	 *             four bytes.
	 * @throws     IOException   if an I/O error occurs.
	 * @see        java.io.AbstractRandomAccessStream#readInt()
	 * @see        java.lang.Float#intBitsToFloat(int)
	 */
	public  float readFloat() throws IOException {
		readFully(buffer, 0, Float.BYTES);
		return ByteArray.getFloat(buffer, 0);
	}

	/**
	 * Reads a {@code double} from this file. This method reads a
	 * {@code long} value, starting at the current file pointer,
	 * as if by the {@code readLong} method
	 * and then converts that {@code long} to a {@code double}
	 * using the {@code longBitsToDouble} method in
	 * class {@code Double}.
	 * <p>
	 * This method blocks until the eight bytes are read, the end of the
	 * stream is detected, or an exception is thrown.
	 *
	 * @return     the next eight bytes of this file, interpreted as a
	 *             {@code double}.
	 * @throws     EOFException  if this file reaches the end before reading
	 *             eight bytes.
	 * @throws     IOException   if an I/O error occurs.
	 * @see        java.io.AbstractRandomAccessStream#readLong()
	 * @see        java.lang.Double#longBitsToDouble(long)
	 */
	public  double readDouble() throws IOException {
		readFully(buffer, 0, Double.BYTES);
		return ByteArray.getDouble(buffer, 0);
	}

	/**
	 * Reads the next line of text from this file.  This method successively
	 * reads bytes from the file, starting at the current file pointer,
	 * until it reaches a line terminator or the end
	 * of the file.  Each byte is converted into a character by taking the
	 * byte's value for the lower eight bits of the character and setting the
	 * high eight bits of the character to zero.  This method does not,
	 * therefore, support the full Unicode character set.
	 *
	 * <p> A line of text is terminated by a carriage-return character
	 * ({@code '\u005Cr'}), a newline character ({@code '\u005Cn'}), a
	 * carriage-return character immediately followed by a newline character,
	 * or the end of the file.  Line-terminating characters are discarded and
	 * are not included as part of the string returned.
	 *
	 * <p> This method blocks until a newline character is read, a carriage
	 * return and the byte following it are read (to see if it is a newline),
	 * the end of the file is reached, or an exception is thrown.
	 *
	 * @return     the next line of text from this file, or null if end
	 *             of file is encountered before even one byte is read.
	 * @throws     IOException  if an I/O error occurs.
	 */

	public  String readLine() throws IOException {
		StringBuilder input = new StringBuilder();
		int c = -1;
		boolean eol = false;

		while (!eol) {
			switch (c = read()) {
			case -1:
			case '\n': eol = true;
			break;
			case '\r':   {
				eol = true;
				long cur = getFilePointer();
				if ((read()) != '\n') {
					seek(cur);
				}
			}
			break;
			default : input.append((char) c);
			}
		}

		if ((c == -1) && (input.length() == 0)) {
			return null;
		}
		return input.toString();
	}

	/**
	 * Reads in a string from this file. The string has been encoded
	 * using a
	 * <a href="DataInput.html#modified-utf-8">modified UTF-8</a>
	 * format.
	 * <p>
	 * The first two bytes are read, starting from the current file
	 * pointer, as if by
	 * {@code readUnsignedShort}. This value gives the number of
	 * following bytes that are in the encoded string, not
	 * the length of the resulting string. The following bytes are then
	 * interpreted as bytes encoding characters in the modified UTF-8 format
	 * and are converted into characters.
	 * <p>
	 * This method blocks until all the bytes are read, the end of the
	 * stream is detected, or an exception is thrown.
	 *
	 * @return     a Unicode string.
	 * @throws     EOFException            if this file reaches the end before
	 *               reading all the bytes.
	 * @throws     IOException             if an I/O error occurs.
	 * @throws     UTFDataFormatException  if the bytes do not represent
	 *               valid modified UTF-8 encoding of a Unicode string.
	 * @see        java.io.AbstractRandomAccessStream#readUnsignedShort()
	 */
	public  String readUTF() throws IOException {
		return DataInputStream.readUTF(this);
	}

	/**
	 * Writes a {@code boolean} to the file as a one-byte value. The
	 * value {@code true} is written out as the value
	 * {@code (byte)1}; the value {@code false} is written out
	 * as the value {@code (byte)0}. The write starts at
	 * the current position of the file pointer.
	 *
	 * @param      v   a {@code boolean} value to be written.
	 * @throws     IOException  if an I/O error occurs.
	 */
	public  void writeBoolean(boolean v) throws IOException {
		write(v ? 1 : 0);
	}

	/**
	 * Writes a {@code byte} to the file as a one-byte value. The
	 * write starts at the current position of the file pointer.
	 *
	 * @param      v   a {@code byte} value to be written.
	 * @throws     IOException  if an I/O error occurs.
	 */
	public  void writeByte(int v) throws IOException {
		write(v);
	}

	/**
	 * Writes a {@code short} to the file as two bytes, high byte first.
	 * The write starts at the current position of the file pointer.
	 *
	 * @param      v   a {@code short} to be written.
	 * @throws     IOException  if an I/O error occurs.
	 */
	public  void writeShort(int v) throws IOException {
		buffer[1] = (byte)(v       );
		buffer[0] = (byte)(v >>>  8);
		write(buffer, 0, Short.BYTES);
	}

	/**
	 * Writes a {@code char} to the file as a two-byte value, high
	 * byte first. The write starts at the current position of the
	 * file pointer.
	 *
	 * @param      v   a {@code char} value to be written.
	 * @throws     IOException  if an I/O error occurs.
	 */
	public  void writeChar(int v) throws IOException {
		writeShort(v);
	}

	/**
	 * Writes an {@code int} to the file as four bytes, high byte first.
	 * The write starts at the current position of the file pointer.
	 *
	 * @param      v   an {@code int} to be written.
	 * @throws     IOException  if an I/O error occurs.
	 */
	public  void writeInt(int v) throws IOException {
		ByteArray.setInt(buffer, 0, v);
		write(buffer, 0, Integer.BYTES);
		//written += 4;
	}

	/**
	 * Writes a {@code long} to the file as eight bytes, high byte first.
	 * The write starts at the current position of the file pointer.
	 *
	 * @param      v   a {@code long} to be written.
	 * @throws     IOException  if an I/O error occurs.
	 */
	public  void writeLong(long v) throws IOException {
		ByteArray.setLong(buffer, 0, v);
		write(buffer, 0, Long.BYTES);
	}

	/**
	 * Converts the float argument to an {@code int} using the
	 * {@code floatToIntBits} method in class {@code Float},
	 * and then writes that {@code int} value to the file as a
	 * four-byte quantity, high byte first. The write starts at the
	 * current position of the file pointer.
	 *
	 * @param      v   a {@code float} value to be written.
	 * @throws     IOException  if an I/O error occurs.
	 * @see        java.lang.Float#floatToIntBits(float)
	 */
	public  void writeFloat(float v) throws IOException {
		ByteArray.setFloat(buffer, 0, v);
		write(buffer, 0, Float.BYTES);
	}

	/**
	 * Converts the double argument to a {@code long} using the
	 * {@code doubleToLongBits} method in class {@code Double},
	 * and then writes that {@code long} value to the file as an
	 * eight-byte quantity, high byte first. The write starts at the current
	 * position of the file pointer.
	 *
	 * @param      v   a {@code double} value to be written.
	 * @throws     IOException  if an I/O error occurs.
	 * @see        java.lang.Double#doubleToLongBits(double)
	 */
	public  void writeDouble(double v) throws IOException {
		ByteArray.setDouble(buffer, 0, v);
		write(buffer, 0, Double.BYTES);
	}

	/**
	 * Writes the string to the file as a sequence of bytes. Each
	 * character in the string is written out, in sequence, by discarding
	 * its high eight bits. The write starts at the current position of
	 * the file pointer.
	 *
	 * @param      s   a string of bytes to be written.
	 * @throws     IOException  if an I/O error occurs.
	 */

	public  void writeBytes(String s) throws IOException {
		byte[] b = s.getBytes();        
		writeBytes(b, 0, b.length);
	}

	/**
	 * Writes a string to the file as a sequence of characters. Each
	 * character is written to the data output stream as if by the
	 * {@code writeChar} method. The write starts at the current
	 * position of the file pointer.
	 *
	 * @param      s   a {@code String} value to be written.
	 * @throws     IOException  if an I/O error occurs.
	 * @see        java.io.AbstractRandomAccessStream#writeChar(int)
	 */
	public  void writeChars(String s) throws IOException {
		int clen = s.length();
		int blen = 2*clen;
		byte[] b = new byte[blen];
		char[] c = new char[clen];
		s.getChars(0, clen, c, 0);
		for (int i = 0, j = 0; i < clen; i++) {
			b[j++] = (byte)(c[i] >>> 8);
			b[j++] = (byte)(c[i] >>> 0);
		}
		writeBytes(b, 0, blen);
	}

	/**
	 * Writes a string to the file using
	 * <a href="DataInput.html#modified-utf-8">modified UTF-8</a>
	 * encoding in a machine-independent manner.
	 * <p>
	 * First, two bytes are written to the file, starting at the
	 * current file pointer, as if by the
	 * {@code writeShort} method giving the number of bytes to
	 * follow. This value is the number of bytes actually written out,
	 * not the length of the string. Following the length, each character
	 * of the string is output, in sequence, using the modified UTF-8 encoding
	 * for each character.
	 *
	 * @param      str   a string to be written.
	 * @throws     IOException  if an I/O error occurs.
	 */
	public  void writeUTF(String str) throws IOException {
		final int strlen = str.length();
		int utflen = strlen; // optimized for ASCII

		for (int i = 0; i < strlen; i++) {
			int c = str.charAt(i);
			if (c >= 0x80 || c == 0)
				utflen += (c >= 0x800) ? 2 : 1;
		}

		if (utflen > 65535 || /* overflow */ utflen < strlen)
			throw new UTFDataFormatException();

		final byte[] bytearr;
		bytearr = new byte[utflen + 2];

		int count = 0;
		ByteArray.setUnsignedShort(bytearr, count, utflen);
		count += 2;
		int i = 0;
		for (i = 0; i < strlen; i++) { // optimized for initial run of ASCII
			int c = str.charAt(i);
			if (c >= 0x80 || c == 0) break;
			bytearr[count++] = (byte) c;
		}

		for (; i < strlen; i++) {
			int c = str.charAt(i);
			if (c < 0x80 && c != 0) {
				bytearr[count++] = (byte) c;
			} else if (c >= 0x800) {
				bytearr[count++] = (byte) (0xE0 | ((c >> 12) & 0x0F));
				bytearr[count++] = (byte) (0x80 | ((c >>  6) & 0x3F));
				bytearr[count++] = (byte) (0x80 | ((c >>  0) & 0x3F));
			} else {
				bytearr[count++] = (byte) (0xC0 | ((c >>  6) & 0x1F));
				bytearr[count++] = (byte) (0x80 | ((c >>  0) & 0x3F));
			}
		}
		write(bytearr, 0, utflen + 2);
		// return utflen + 2;

	}


}
