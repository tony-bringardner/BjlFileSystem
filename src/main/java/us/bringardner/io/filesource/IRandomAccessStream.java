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
 * ~version~V000.00.01-V000.00.00-
 */
package us.bringardner.io.filesource;

import java.io.Closeable;
import java.io.DataInput;
import java.io.DataOutput;

public interface IRandomAccessStream extends DataOutput, DataInput, Closeable  {

		
	public long length() throws java.io.IOException;
	public void write(int arg) throws java.io.IOException;
	public void write(byte[]arg) throws java.io.IOException;
	public void write(byte[]arg0,int arg1,int arg2) throws java.io.IOException;
	public int read() throws java.io.IOException;
	public int read(byte[]arg,int arg1,int arg2) throws java.io.IOException;
	public int read(byte[]arg) throws java.io.IOException;
	public  java.lang.String readLine() throws java.io.IOException;
	public void setLength(long arg) throws java.io.IOException;
	public void close() throws java.io.IOException;
	public  void writeInt(int arg) throws java.io.IOException;
	public  int readInt() throws java.io.IOException;
	public  void writeUTF(java.lang.String arg) throws java.io.IOException;
	public  java.lang.String readUTF() throws java.io.IOException;	
	
	public  void writeBytes(java.lang.String arg) throws java.io.IOException;
	public  void writeChar(int arg) throws java.io.IOException;
	public  char readChar() throws java.io.IOException;
	public  void writeFloat(float arg) throws java.io.IOException;
	public  float readFloat() throws java.io.IOException;
	public  int readUnsignedShort() throws java.io.IOException;
	public  long readLong() throws java.io.IOException;
	public  byte readByte() throws java.io.IOException;
	public  void readFully(byte[]arg) throws java.io.IOException;
	public  void readFully(byte[]arg,int arg1,int arg2) throws java.io.IOException;
	public int skipBytes(int arg) throws java.io.IOException;
	public  boolean readBoolean() throws java.io.IOException;
	public  int readUnsignedByte() throws java.io.IOException;
	public  short readShort() throws java.io.IOException;
	public  double readDouble() throws java.io.IOException;
	public  void writeBoolean(boolean arg) throws java.io.IOException;
	public  void writeByte(int arg) throws java.io.IOException;
	public  void writeShort(int arg) throws java.io.IOException;
	public  void writeLong(long arg) throws java.io.IOException;
	public  void writeDouble(double arg) throws java.io.IOException;
	public  void writeChars(java.lang.String arg) throws java.io.IOException;
	public void seek(long arg) throws java.io.IOException;


}
