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
/*
 * Created on Dec 14, 2004
 *
 */
package us.bringardner.io.filesource;

import javax.swing.filechooser.FileView;

/**
 * @author Tony Bringardner
 *
 */
@FunctionalInterface
public interface FileSourceFilter {
	
	
	 /**
     * Whether the given file is accepted by this filter.
     *
     * @param f the File to test
     * @return true if the file is to be accepted
     */
    public abstract boolean accept(FileSource f);

    /**
     * The description of this filter, shown by the file chooser dialog.
     * For example: "JPG and GIF Images".
     * <p>
     * A default method, so a filter can be written as a lambda, e.g.
     * {@code dir.listFiles(f -> f.getName().endsWith(".log"))}. Override it
     * when the filter is shown in a chooser.
     *
     * @return the description of this filter; "Filtered files" by default
     * @see FileView#getName
     */
    default String getDescription() {
        return "Filtered files";
    }
}
