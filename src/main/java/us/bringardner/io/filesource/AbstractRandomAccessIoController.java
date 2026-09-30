/**
 * <PRE>
 * 
 * Copyright Tony Bringarder 1998, 2025 <A href="http://bringardner.com/tony">Tony Bringardner</A>
 * 
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       <A href="http://www.apache.org/licenses/LICENSE-2.0">http://www.apache.org/licenses/LICENSE-2.0</A>
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *  </PRE>
 *   
 *   
 *	@author Tony Bringardner   
 *
 *
 * ~version~
 */
package us.bringardner.io.filesource;

import java.io.IOException;

/**
 * This class reads and writes data in Chunks :-) 
 */
public abstract class AbstractRandomAccessIoController implements IRandomAccessIoController {

	public static class Chunk {
		public boolean isNew=false;
		public boolean isDirty=false;
		public byte [] data;
		public long chunkNumber;
		public long start;
		public int size;

		public Chunk() {

		}

		public Chunk(long start,long cn,byte [] b) {
			this.start = start;
			chunkNumber = cn;
			data = b;
		}

		public boolean contains(long pos) {
			long end = (start+data.length);
			boolean b1 = pos >= start ;
			boolean b2 = pos < end;

			return b1
					&& 
					b2;
		}	

	}

	protected FileSourceFactory factory ;
	protected FileSource file;

	private long lastWritePosition;

	private long lastReadPosition;
	private int maxWriteOffset=-1;
	private Chunk currentChunk;
	private boolean closed = false;


	public AbstractRandomAccessIoController(FileSource file) throws IOException {
		this.file = file;
		factory = file.getFileSourceFactory();		
	}



	public FileSource getFile() {
		return file;
	}



	public long getLastWritePosition() {
		return lastWritePosition;
	}



	public long getLastReadPosition() {
		return lastReadPosition;
	}




	public boolean isDirty() {
		return currentChunk !=null && currentChunk.isDirty;
	}

	public boolean contains(long pos) {
		return currentChunk == null ? false: currentChunk.contains(pos);
	}


	/** Length of the stored file, or -1 when it must be re-read. */
	private long fileLength = -1;

	/**
	 * The stored file's length is read once and again only after this
	 * controller changes it (save/setLength). It used to refresh the file on
	 * every call, which for a remote factory is a round trip per call.
	 * Changes made to the file by someone else while it is open aren't seen.
	 */
	public long length() throws IOException {
		if( fileLength < 0 ) {
			file.refresh();
			fileLength = file.length();
		}
		long ret = fileLength;		
		if(currentChunk !=null && currentChunk.isNew) {
			if( maxWriteOffset>=0) {
				ret += maxWriteOffset+1;
			}
		}
		return ret;
	}

	public int read(long pos) throws IOException {
		if(pos<0) {
			throw new IOException("Negative position");
		}

		int ret = -1;
		if( !closed ) {
			if(currentChunk == null || 
					!currentChunk. contains(pos)) {
				// find and load data
				loadChunkFor(pos);

			}

			if( currentChunk.isNew) {
				if(currentChunk.isDirty) {

					int offset = (int)(pos-currentChunk.start);
					if( offset < (maxWriteOffset+1)) {
						ret = currentChunk.data[offset] & 0xFF;  // unsigned, so bytes >= 0x80 aren't mistaken for EOF
						lastReadPosition = pos;
					}				
				} else {
					//  will return -1
				}
			} else if(currentChunk.contains(pos)) {
				int offset = (int)(pos-currentChunk.start);
				ret = currentChunk.data[offset] & 0xFF;  // unsigned, so bytes >= 0x80 aren't mistaken for EOF
				lastReadPosition = pos;
			} else {
				throw new IOException("Logic error");
			}
		}
		return ret;
	}

	/**
	 * Bulk read: reads the first byte through read(long) (which loads the
	 * right chunk and handles new/dirty chunks), then copies the rest of the
	 * loaded chunk in one go.
	 */
	@Override
	public int read(long pos, byte[] b, int off, int len) throws IOException {
		java.util.Objects.checkFromIndexSize(off, len, b.length);
		if( len == 0 ) {
			return 0;
		}
		int total = 0;
		while( total < len ) {
			int first = read(pos + total);
			if( first < 0 ) {
				break;
			}
			b[off + total++] = (byte) first;
			Chunk chunk = currentChunk;
			if( total < len && chunk != null && !chunk.isNew && chunk.contains(pos + total) ) {
				int offset = (int) (pos + total - chunk.start);
				int n = Math.min(chunk.data.length - offset, len - total);
				System.arraycopy(chunk.data, offset, b, off + total, n);
				total += n;
				lastReadPosition = pos + total - 1;
			}
		}
		return total == 0 ? -1 : total;
	}

	public void write(long pos, byte value) throws IOException {
		if(pos<0) {
			throw new IOException("Negative position");
		}
		if( closed ) {
			throw new IOException("Already closed");
		}

		if( currentChunk == null) {
			loadChunkFor(pos);
		}

		if(currentChunk == null || 
				!contains(pos)
				) {			
			loadChunkFor(pos);
		}

		int offset = (int)(pos-currentChunk.start);
		if( offset < 0 ) {
			throw new IOException("Negative offset pos="+pos+" start="+currentChunk.start);
		}
		if( offset >= currentChunk.data.length ) {
			//  We're writing past the end of the file
			//  by more than the chunk size, so expand the file
			setLength(pos-1);
			loadChunkFor(pos);
			offset = (int)(pos-currentChunk.start);
			if( offset < 0 ) {
				throw new IOException("Negative offset after expanding file pos="+pos+" start="+currentChunk.start);
			}
			if( offset >= currentChunk.data.length ) {
				//  we did not solve the problem???
				throw new IOException("offset >= currentChunk.data.length "
						+ " pos="+pos+" start="+currentChunk.start
						+ " offset="+offset
						+ " data.length="+currentChunk.data.length
						);
			}
		}

		currentChunk.data[offset] = value;
		currentChunk.isDirty = true;
		maxWriteOffset = Math.max(offset, maxWriteOffset);
		currentChunk.size = Math.max(offset+1, currentChunk.size);
	}

	private void loadChunkFor(long pos) throws IOException {
		// save any new writes

		if( currentChunk!=null ) {
			if( currentChunk.isDirty)  {
				save();
			} else if(maxWriteOffset!=-1) {
				throw new IOException("MAx write is not 0 but chunk is not dirty");	
			}
		}
		// find and load data
		currentChunk = readChunkForPos(pos);

	}

	protected abstract Chunk readChunkForPos(long pos) throws IOException ;



	public void save() throws IOException {
		if( currentChunk !=null ) {
			if(!currentChunk.isNew && currentChunk.size != currentChunk.data.length) {
				throw new IOException("Chunk size is not valid chunk size="+currentChunk.size+" data.length = "+currentChunk.data.length);
			}
			if(currentChunk.isDirty) {
				if(currentChunk.isNew || currentChunk.chunkNumber<=0) {
					if( maxWriteOffset >= currentChunk.data.length ) {
						throw new IOException("Maxwrite invalid = "+maxWriteOffset+" data.len="+currentChunk.data.length);
					}
					//  chunk is dirty so we know something was written
					//  the offset is 0 based					
					int size = maxWriteOffset+1;
					if( size < currentChunk.data.length) {
						byte [] tmp = java.util.Arrays.copyOf(currentChunk.data, size);
						currentChunk.data = tmp;
						currentChunk.size = tmp.length;
					}
				}
				writeChunk(currentChunk);
				fileLength = -1;   // the stored file may have grown
			}
			currentChunk.isDirty = false;
			currentChunk.isNew = false;
			maxWriteOffset = -1;

		}
	}

	protected abstract void writeChunk(Chunk chunk) throws IOException;

	public  void setLength(long newLength) throws IOException{
		if(maxWriteOffset>=0) {
			save();
		}
		setLength0(newLength);
		fileLength = -1;
		// force reload of next chunk
		currentChunk = null;
	}




	protected abstract void setLength0(long newLength) throws IOException;



	@Override
	public void close() throws Exception {
		if( !closed ) {
			save();
			closed = true;
		}

	}

}
