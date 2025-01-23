package us.bringardner.io.filesource;

import java.io.IOException;

/**
 * This class manages the high level logic for a random access stream
 *  It manages the pointer :-)
 */
public class FileSourceRandomAsccessStream extends AbstractRandomAccessStream {


	private long pointer;	
	private boolean closed= false;	
	private IRandomAccessIoController io;
	
	
	public FileSourceRandomAsccessStream(IRandomAccessIoController iocontroller,String mode) throws IOException {
		super(iocontroller.getFile(), mode);
		this.io = iocontroller;
	}

	

	@Override
	public int read() throws IOException {
		if( closed ) {
			throw new IOException("Can't write closed");
		}
		
		int ret = io.read(pointer++);
		if( ret < 0 ) {
			pointer--;
		}
		return ret;

	}


	

	@Override
	public void write(int b) throws IOException {
		if( readOnly) {
			throw new IOException("Can't write in read only mode");
		}

		if( closed ) {
			throw new IOException("Can't write closed");
		}

		io.write(pointer++,(byte) b);

	}


	

	
	@Override
	public long getFilePointer() throws IOException {
		return pointer;
	}


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


	@Override
	public void seek(long pos) throws IOException {
		if (pos < 0) {
			throw new IOException("Negative seek offset");
		}

		pointer = pos;
	}

	
	/**
	 * Returns the length of this file.
	 *
	 * @return     the length of this file, measured in bytes.
	 * @throws     IOException  if an I/O error occurs.
	 */
	@Override
	public long length() throws IOException {
		long ret = io.length();
		return ret;
	}


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
	@Override
	public void setLength(long newLength) throws IOException {
		if( readOnly ) {
			// trying to match Java
			throw new IOException("Invalid argument (read only)");
		}
		
		io.setLength(newLength);
		if( newLength < pointer) {
			pointer = newLength;
		}
				
	}


	@Override
	public void close() throws IOException {
		try {
			io.close();
		} catch (Exception e) {
			throw new IOException(e);
		}
		closed = true;
	}


}
