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
 * ~version~V000.01.16-V000.00.01-V000.00.00-
 */
package us.bringardner.io.filesource;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.io.filesource.fileproxy.FileProxy;

public class FileSourceTransferable implements Transferable {

	public  static final DataFlavor fileSourceFlavor = new DataFlavor(List.class, "A FileSource");

	protected final DataFlavor[] supportedFlavors = {
			fileSourceFlavor,
			DataFlavor.javaFileListFlavor
	};

	private List<FileSource> files;

	public FileSourceTransferable(List<FileSource> files) {
		this.files = files;		
	}

	@Override
	public DataFlavor[] getTransferDataFlavors() {
		return supportedFlavors;
	}

	@Override
	public boolean isDataFlavorSupported(DataFlavor flavor) {
		boolean ret = false;
		for(DataFlavor f : supportedFlavors) {
			if( f.equals(flavor)) {
				ret = true;
				break;
			}
		}
		return ret;
	}

	@Override
	public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
		if (!isDataFlavorSupported(flavor)) {
            throw new UnsupportedFlavorException(flavor);
        }
		
		Object ret = null;
		if (flavor.equals(fileSourceFlavor)) {
			ret = files;
		} else if( flavor.equals(DataFlavor.javaFileListFlavor)) {
			List<File> list = new ArrayList<File>();
			for(FileSource file : files) {
				File exportFile = null;
				if (file instanceof FileProxy	) {
					exportFile = new File(file.getCanonicalPath());						
				} else {
					File tmp = File.createTempFile("tmp", "fs");
					OutputStream out = new FileOutputStream(tmp);
					InputStream in = file.getInputStream();
					byte[] data = in.readAllBytes();
					out.write(data);
					out.close();
					in.close();
					exportFile = new File(tmp.getParent(),file.getName());
					tmp.renameTo(exportFile);
					tmp.deleteOnExit();
				}
				list.add(exportFile);
			}
			ret = list;
		} else if (flavor.equals(DataFlavor.stringFlavor)) {
			StringBuilder buf = new StringBuilder();
			for(FileSource f : files) {
				if( buf.length()>0) {
					buf.append("\n");
				}
				buf.append(f.toString());
			}
			ret = buf.toString();

		} 
		
		return ret;
	}

}
