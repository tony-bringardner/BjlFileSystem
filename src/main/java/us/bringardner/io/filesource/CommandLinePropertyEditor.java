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

import java.io.IOException;
import java.util.Enumeration;
import java.util.Properties;

import us.bringardner.io.ILineReader;
import us.bringardner.io.ILineWriter;
import us.bringardner.io.LFLineReader;
import us.bringardner.io.LFLineWriter;


public class CommandLinePropertyEditor {
	ILineReader in = new LFLineReader(System.in);
	ILineWriter out = new LFLineWriter(System.out);

	public CommandLinePropertyEditor() {
		
	}

	public CommandLinePropertyEditor(ILineReader in, ILineWriter out) {
		this.in = in;
		this.out = out;
	}
	
	public ILineReader getIn() {
		return in;
	}

	public void setIn(ILineReader in) {
		this.in = in;
	}

	public ILineWriter getOut() {
		return out;
	}

	public void setOut(ILineWriter out) {
		this.out = out;
	}

	public  boolean editProperties(String name1, Properties props1) throws IOException {

		boolean cancel = false;
		boolean accepted=false;
		Properties props2 = new Properties();
		for (Enumeration<?> e = props1.propertyNames(); e.hasMoreElements(); )   {
			String key = (String)e.nextElement();
			String value = props1.getProperty( key );
			props2.setProperty(key, value);
		}		

		out.writeLine("There are "+props1.size()+" Connection properties. ");
		out.writeLine("Enter 'ok' or 'done' to when you are done editing.");
		

		while(!accepted && !cancel ) {
			for(Object key : props2.keySet()) {
				String name = key.toString();
				String val = props2.getProperty(name);
				if( val == null || val.isEmpty()) {
					val = props2.getProperty(name1+"."+name);
					if( val == null || val.isEmpty()) {
						val = System.getProperty(name1+"."+name);
						if( val == null ) {
							val = "";
						}
					}
				}

				out.writeLine("Enter value for "+name+" or enter to keep '" +val+"'");

				String line = in.readLine();
				if( line.isEmpty()) {
					line = val;
				}
				line = line.trim();
				if( "cancel".equals(line) || "exit".equals(line)) {
					cancel=true;
					break;
				} else if( "done".equals(line) || "ok".equals(line)) {
					accepted=true;
					break;
				} else {
					props2.setProperty(name, line);
				}
			}

		}

		if( accepted) {
			for (Enumeration<?> e = props1.propertyNames(); e.hasMoreElements(); )   {
				String key = (String)e.nextElement();
				String value = props2.getProperty( key );
				props1.setProperty(key, value);
			}
		}

		return accepted;
	}

	public static void main(String[] args) {
		// Not implemented

	}

}
