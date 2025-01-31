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

import java.awt.Component;
import java.awt.FlowLayout;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

public class PropertyPanel extends JPanel implements IConnectionPropertiesEditor{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private Properties properties = new Properties();
	private Map<String,Component> map = new HashMap<String, Component>();


	public Properties getProperties() {
		//  get current values
		for (Entry<Object, Object> e : properties.entrySet()) {
			String nm = e.getKey().toString();
			Component fld = map.get(nm);
			if (fld instanceof JPasswordField) {
				JPasswordField pw = (JPasswordField) fld;
				properties.setProperty(nm, new String(pw.getPassword()));
			} else if (fld instanceof JTextField) {
				JTextField txt = (JTextField) fld;
				properties.setProperty(nm, txt.getText());
			}
		}

		return properties;
	}



	public void setProperties(Properties properties) {
		Properties old = properties;
		if(properties == null ) {
			properties = new Properties();
		}

		this.properties = properties;
		removeAll();
		map.clear();
		for (Entry<Object, Object> e : properties.entrySet()) {
			final String name = e.getKey().toString();
			String val  = e.getValue().toString();
			if( old != null ) {
				val = old.getProperty(name,val);
			}
			JPanel p = new JPanel();
			p.setLayout(new FlowLayout(FlowLayout.LEFT));
			p.add(new JLabel(name));
			Component fld = new JTextField(val,10);

			if( name.toLowerCase().contains("password")) {
				fld = new JPasswordField(val,20);
			}
			map.put(name,fld);
			p.add(fld);
			add(p);
		}
	}



	/**
	 * Create the panel.
	 */
	public PropertyPanel() {
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

	}

}
