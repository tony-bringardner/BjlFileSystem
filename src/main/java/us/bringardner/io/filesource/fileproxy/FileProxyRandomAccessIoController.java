package us.bringardner.io.filesource.fileproxy;

import java.io.IOException;
import java.io.RandomAccessFile;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.IRandomAccessIoController;

public class FileProxyRandomAccessIoController implements IRandomAccessIoController{

	private RandomAccessFile target;
	private FileProxy file;

	public FileProxyRandomAccessIoController(FileProxy file,String mode) throws IOException {
		this.file = file;
		this.target = new RandomAccessFile(file.target, mode);
	}
	
	@Override
	public void close() throws Exception {
		target.close();		
	}

	@Override
	public int read(long position) throws IOException {
		target.seek(position);
		return target.read();
	}

	@Override
	public void write(long position, byte value) throws IOException {
		target.seek(position);
		target.write(value);		
	}

	@Override
	public long length() throws IOException {
		return target.length();
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
