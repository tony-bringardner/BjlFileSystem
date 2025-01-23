package us.bringardner.io.filesource.memory;

import java.io.IOException;
import java.util.Arrays;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.IRandomAccessIoController;

/**
 * Random access implementation for memory file 
 * Much of the logic comes from ByteArrayInputStream and ByteArrayOutputStream 
 */
public class MemoryRandomAccessIoController implements IRandomAccessIoController {

	/**
     * A soft maximum array length imposed by array growth computations.
     * Some JVMs (such as HotSpot) have an implementation limit that will cause
     *
     *     OutOfMemoryError("Requested array size exceeds VM limit")
     *
     * to be thrown if a request is made to allocate an array of some length near
     * Integer.MAX_VALUE, even if there is sufficient heap available. The actual
     * limit might depend on some JVM implementation-specific characteristics such
     * as the object header size. The soft maximum value is chosen conservatively so
     * as to be smaller than any implementation limit that is likely to be encountered.
     */
    public static final int SOFT_MAX_ARRAY_LENGTH = Integer.MAX_VALUE - 8;


	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

	private MemoryFileSource file;
	private boolean isDirty = false;

	private byte[] data;
	protected int size;
	protected int pos;
	private boolean closed;

	public MemoryRandomAccessIoController(MemoryFileSource file) {
		this.file = file;
		this.data = file.getData();		
		this.size = data.length;
	}

	@Override
	public void close() throws Exception {
		save();
		closed = true;

	}

	/**
	 * Reads the next byte of data from this input stream. The value
	 * byte is returned as an {@code int} in the range
	 * {@code 0} to {@code 255}. If no byte is available
	 * because the end of the stream has been reached, the value
	 * {@code -1} is returned.
	 * <p>
	 * This {@code read} method
	 * cannot block.
	 *
	 * @return  {@inheritDoc}
	 * @throws IOException 
	 */
	@Override
	public synchronized int read(long pos) throws IOException {
		if( closed ) {
			throw new IOException("Already closed");
		}

		return (pos < size) ? (data[(int)pos] & 0xff) : -1;
	}


	/**
	 * Writes the specified byte to this {@code ByteArrayOutputStream}.
	 *
	 * @param   b   the byte to be written.
	 * @throws IOException 
	 */
	@Override
	public synchronized void write(long pos,byte b) throws IOException {
		if( closed ) {
			throw new IOException("Already closed");
		}
		ensureCapacity((int)pos);
		data[(int)pos] = (byte) b;
		// Remember... the array is zero based so the size is one more that the highest write
		size = Math.max(size, (int)pos+1);
		isDirty = true;
	}

	/**
	 * Increases the capacity if necessary to ensure that it can hold
	 * at least the number of elements specified by the minimum
	 * capacity argument.
	 * @throws IOException 
	 *
	 */
	private void ensureCapacity(int minCapacity) throws IOException {
		if( minCapacity>=SOFT_MAX_ARRAY_LENGTH) {
			throw new IOException("Files larger that "+SOFT_MAX_ARRAY_LENGTH+" are not supported");
		}
		int oldCapacity = data.length;
		int minGrowth = minCapacity - oldCapacity;
		if (minGrowth > 0) {
			data =  Arrays.copyOf(data, newLength(oldCapacity,minGrowth, oldCapacity ));			
		}
	}
	
	 public static int newLength(int oldLength, int minGrowth, int prefGrowth) {
	        // preconditions not checked because of inlining
	        // assert oldLength >= 0
	        // assert minGrowth > 0

	        int prefLength = oldLength + Math.max(minGrowth, prefGrowth); // might overflow
	        if (0 < prefLength && prefLength <= SOFT_MAX_ARRAY_LENGTH) {
	            return prefLength;
	        } else {
	            // put code cold in a separate method
	            return hugeLength(oldLength, minGrowth);
	        }
	    }

	    private static int hugeLength(int oldLength, int minGrowth) {
	        int minLength = oldLength + minGrowth;
	        if (minLength < 0) { // overflow
	            throw new OutOfMemoryError(
	                "Required array length " + oldLength + " + " + minGrowth + " is too large");
	        } else if (minLength <= SOFT_MAX_ARRAY_LENGTH) {
	            return SOFT_MAX_ARRAY_LENGTH;
	        } else {
	            return minLength;
	        }
	    }

	@Override
	public long length() throws IOException {
		return size;
	}

	@Override
	public void setLength(long newLength) throws IOException {
		ensureCapacity((int)newLength);
		size = (int)newLength;
		isDirty = true;
		// this impacts the file now....
		save();
	}

	@Override
	public void save() throws IOException {
		if( isDirty) {
			byte ret[] = new byte[size];
			for (int idx = 0; idx < ret.length; idx++) {
				ret[idx] = data[idx];
			}
			file.setData(ret);
			isDirty = false;
		}
	}

	@Override
	public FileSource getFile() {		
		return file;
	}

}
