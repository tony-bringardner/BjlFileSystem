/**
 * <PRE>
 * 
 * Copyright Tony Bringarder 1998, 2025 
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

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JOptionPane;

import us.bringardner.core.BaseThread;
import us.bringardner.io.ILineReader;
import us.bringardner.io.ILineWriter;
import us.bringardner.io.IStreamMonitor;
import us.bringardner.io.LFLineReader;
import us.bringardner.io.LFLineWriter;
import us.bringardner.io.MonitoredInputStream;
import us.bringardner.io.filesource.fileproxy.FileProxyFactory;


//  System.setProperty("java.awt.headless", "true");
public class Terminal extends BaseThread {

	public static void main(String args[]) throws IOException {

		Terminal t = new Terminal();
		t.setDaemon(false);
		t.start();
	}

	private interface TerminalAction {
		public void performAction(FileSource file) throws IOException;
	}

	enum SizeType {LessThan,LessThanOrEqual, GreterThan, GreaterThanOrEqual, Equal, Between}

	class Size {
		SizeType type;
		long a;
		long b;
	}

	Size parseSize(String val) {
		Size ret = new Size();
		switch (val.charAt(0)) {
		case '<': ret.type=SizeType.LessThan;val = val.substring(1);
		if( val.charAt(0) == '=') {
			val = val.substring(1);
			ret.type = SizeType.LessThanOrEqual;
		}
		ret.a = (long)parseNumericValue(val); 
		break;
		case '>': ret.type=SizeType.GreterThan;val = val.substring(1);
		if( val.charAt(0) == '=') {
			val = val.substring(1);
			ret.type = SizeType.LessThanOrEqual;
		}
		ret.a = (long)parseNumericValue(val); 
		break;

		case '=': ret.type=SizeType.Equal;val = val.substring(1);ret.a = Long.parseLong(val);break;
		default:
			String parts [] = val.split("[-]");
			if( parts.length == 1) {
				ret.type = SizeType.Equal;
				ret.a = (long)parseNumericValue(val);
			} else if( parts.length == 2) {
				ret.type = SizeType.Between;
				ret.a = (long)parseNumericValue(parts[0]);
				ret.b = (long)parseNumericValue(parts[1]);
			}
			break;
		}

		return ret;
	}

	double parseNumericValue(String val ) {
		double multiplier=1.0;
		int pos = -1;
		val = val.trim().toLowerCase();
		char[] data = val.toCharArray();
		for(int idx=0; pos<0 && idx < data.length; idx++ ) {
			if( !(Character.isDigit(data[idx]) || data[idx] == '.' || data[idx] == ' ')) {
				pos = idx;
			}
		}
		if( pos >0 ) {
			val = val.substring(0,pos).trim();
			switch (data[pos]) {
			case 'k': multiplier = 1024;break;
			case 'm': multiplier = 1024*1024;break;
			case 'g': multiplier = 1024*1024*1024;break;
			case 't': multiplier = 1024*1024*1024*1024;break;
			default:
				break;
			}
		}

		double ret = Double.parseDouble(val)*multiplier;

		return ret;
	}

	/**
	 * Copy one FileSource to another
	 * 
	 * @param from
	 * @param to
	 * @throws IOException 
	 */
	private void copy(FileSource from, FileSource to,boolean verbose) throws IOException {
		if(verbose) out.writeLine("Copy "+from+" ->"+to);

		if( !from.exists() ) {
			throw new IOException("Can't copy from a non existent file:"+from.getAbsolutePath());
		}
		if( from.isDirectory()) {
			if( !to.exists()) {
				if( !to.mkdirs()) {
					throw new IOException("Can't create directcory at "+to);				
				}
			}
			FileSource[] kids = from.listFiles();
			if( kids != null ) {
				for(FileSource from2 : kids) {
					FileSource to2 = to.getChild(from2.getName());
					copy(from2,to2,verbose);
				}
			}
		} else {
			final long size1 = from.length();
			final String name = from.getName();


			InputStream in = new MonitoredInputStream( from.getInputStream(),(1024*10),new IStreamMonitor() {

				public void update(long total, long transfered) {
					double perc = ((double)total/(double)size1)*100.0; 
					try {
						out.writeLine("update "+name+" size="+size1+" total="+total+" transfered="+transfered+" percent="+perc);
					} catch (IOException e) {
						// Not implemented
						e.printStackTrace();
					}					
				}

				public void start() {
					try {
						out.writeLine("Copy started for "+name);
					} catch (IOException e) {
						// Not implemented
						e.printStackTrace();
					}
				}


				public void complete(long total) {
					try {
						out.writeLine("Copy completed for "+name);
					} catch (IOException e) {
						// Not implemented
						e.printStackTrace();
					}

				}
			});

			try { 
				FileSource parent = to.getParentFile();
				if( parent != null && !parent.exists()) {
					parent.mkdirs();
				}

				OutputStream out2 = to.getOutputStream();
				long start = System.currentTimeMillis();
				int size = 0;
				try {
					byte data[] = new byte[10*1024];
					int got = 0;

					while((got=in.read(data)) >=0 ) {
						size += got;
						out2.write(data, 0, got);
					}
				} finally {
					try {
						out2.close();
					} catch (Exception e) {
					}
				}
				long time = System.currentTimeMillis()-start;
				double seconds = (time/1000);
				double bps = size/seconds;
				if( verbose ) out.writeLine(to.getName()+" seconds "+(seconds)+" size="+size+" bps="+bps);
			} finally {
				try {
					in.close();
				} catch (Exception e) {
				}
			}

		}

	}


	interface CommandProcessor {
		String getName();
		void proccess(String [] args) throws IOException ;
		String help();
	}

	class  Pwd implements CommandProcessor {

		public String getName() {
			return "pwd";
		}

		public void proccess(String[] args) throws IOException {
			if( args.length>1 ) {
				for (int idx =  1; idx < args.length; idx++) {
					ParsedFileValue val = parseOnly(args[idx]);
					if( val.path.isEmpty()) {
						FileSourceFactory factory = connected.get(val.factory);
						FileSource dir = factory.getCurrentDirectory();
						if( dir == null ) {
							out.writeLine(factory.getTypeId()+" has no current directory");
						} else {
							out.writeLine(factory.getTypeId()+" "+dir.getAbsolutePath());
						}		
					} else {

					}
				}
			} else {
				FileSource dir = connected.get(currentFactory).getCurrentDirectory();
				if( dir == null ) {
					out.writeLine(connected.get(currentFactory).getTypeId()+" has no current directory");
				} else {
					out.writeLine(connected.get(currentFactory).getTypeId()+" "+ dir.getAbsolutePath());
				}
			}

		}

		public String help() {
			return "Print the current working directory";
		}
	}

	class  Help implements CommandProcessor {

		public String getName() {
			return "help";
		}

		public void proccess(String[] args) throws IOException {
			if( args.length  < 2) {
				for(CommandProcessor cmd : procs.values()) {
					out.writeLine(cmd.getName()+": "+ cmd.help());
				}
			} else {
				for (int idx = 1; idx < args.length; idx++) {
					CommandProcessor cmd = procs.get(args[idx]);
					if( cmd == null ) {
						out.writeLine("No command for "+args[idx]);
					} else {
						out.writeLine(args[idx]+ " "+cmd.help());
					}
				}
			}
		}

		public String help() {
			return "Show help for all or specified commands.";
		}
	}

	class  Show implements CommandProcessor {

		public String getName() {
			return "show";
		}

		public String help() {
			return "Show availible and connected FileSource factories.";
		}

		public void proccess(String[] args) throws IOException {
			String[] factories = FileSourceFactory.getRegisterdFactories();
			out.writeLine("There are "+factories.length+" registered factories");
			for(String f : factories) {				
				out.writeLine("\t"+f);
			}
			out.writeLine("There are "+connected.size()+" connected factories");
			for(FileSourceFactory f : connected) {
				out.writeLine("\t"+f.getTypeId()+" "+f.getTitle());
			}

		}
	}

	class  Echo implements CommandProcessor {

		public String getName() {
			return "echo";
		}

		public String help() {
			return "Echo values to stdout.";
		}

		public void proccess(String[] args) throws IOException {

			for (int idx = 1; idx < args.length; idx++) {
				if( idx >1) {
					out.write(" ");
				}
				out.write(args[idx]);
			}
			out.writeLine("");

		}
	}

	class  Test implements CommandProcessor {

		public String getName() {
			return "test";
		}

		public String help() {
			return "A test command";
		}

		public void proccess(String[] args) throws IOException {
			ParsedArgs arg2 = parseArgs(args);
			System.out.println("Named args cnt = "+arg2.namesArgs.size());

			for(String name : arg2.namesArgs.keySet()) {
				System.out.println("\t"+name+"="+arg2.namesArgs.get(name));
			}
			System.out.println();
			System.out.println("Un named Args cnt = "+arg2.args.size());


			for(int idx=0; idx<arg2.args.size(); idx++ ) {
				System.out.println("\tunamed "+idx+"="+arg2.args.get(idx));
			}


		}


	}

	public boolean followPathWithAction(String arg,FileSourceFactory f,TerminalAction action) throws IOException {
		boolean ret = false;
		if( arg != null ) {
			arg = arg.trim();
			if( !arg.isEmpty()) {
				ParsedFileValue val = parseOnly(arg);
				List<Object> list = followPath(arg, connected.get(val.factory));
				if( list != null && list.size()>0) {						
					Object obj2 =  list.get(list.size()-1);
					ret = traversPathWithAction(obj2, action);
				}
			}
		}

		return ret;
	}

	public boolean traversPathWithAction(Object obj, TerminalAction action) throws IOException {

		boolean ret = false;
		if( obj == null ) {
			out.writeLine("No matches found.");
		} else {
			if (obj instanceof List) {
				List<?> list = (List<?>) obj;
				for(Object obj2 : list) {
					ret = traversPathWithAction(obj2,action);
				} 
			} else if( obj instanceof FileSource) {
				action.performAction((FileSource)obj);
				ret = true;
			} else {
				throw new IllegalStateException("Object is not valid class.  Class  = "+obj.getClass());
			}		
		}
		return ret;
	}

	public Object getFileFromString(String val,FileSource file) throws IOException {
		Object ret = null;
		if(val.equals(".")) {
			ret = file;
		} else if (val.equals("..")) {
			FileSource parent = file.getParentFile();
			if( parent == null) {
				out.writeLine("Can't .. past patents.");
			} else {
				ret=(parent);
			}
		} else if(val.indexOf('*')>=0) {
			val = val.replace("*", ".*");
			Pattern p = Pattern.compile(val);
			if(file == null ) {
				out.writeLine("Can't .. past patents.");
			} else {
				if( file.isFile()) {
					if(p.matcher(file.getName()).matches()) {
						ret=(file);
					}
				} else {
					FileSource[] kids = file.listFiles();
					if( kids != null ) {
						List<FileSource> tret = new ArrayList<FileSource>();

						for(FileSource f1 : kids) {
							if(p.matcher(f1.getName()).matches()) {
								tret.add(f1);
							} 
						}
						ret = tret;
					}
				}
			}
		} else {
			ret = (file.getChild(val));
		}

		return ret;
	}

	public List<Object>  followPath(String path,FileSourceFactory factory) throws IOException {
		List<Object> ret = new ArrayList<Object>();

		//out.writeLine("Look at parsing paths with rx");
		String pathSegments [] = path.split("["+factory.getSeperatorChar()+"]");

		//out.writeLine(path+" len="+pathSegments.length);
		FileSource file = null;

		int startArg = 0;
		if(pathSegments.length == 0 ||  pathSegments[0].isEmpty()) {
			file = factory.createFileSource(""+factory.getSeperatorChar()+"");
			startArg++;
		} else {
			file =  factory.getCurrentDirectory();
		}

		Object last = file;
		ret.add(last);
		for (int idx1 = startArg; idx1 < pathSegments.length; idx1++) {
			String segment = pathSegments[idx1];
			last = doit(segment,last);
			ret.add(last);
		}

		return ret;

	}



	private Object doit(String segment, Object last) throws IOException {
		Object ret = null;
		if (last instanceof FileSource) {
			FileSource file = (FileSource) last;
			ret = getFileFromString(segment, file);
		} else 	if (last instanceof List) {
			@SuppressWarnings("unchecked")
			List<Object> list = (List<Object>)last;
			List<Object> rlist = new ArrayList<Object>();
			for(Object obj : list) {
				Object tmp =  doit(segment, obj);
				if( tmp != null ) {
					if (tmp instanceof List) {
						if( ((List<?>) tmp).size()>0) {
							rlist.add( tmp);
						}
					} else {
						rlist.add( tmp);
					}
				}
			}
			ret = rlist;
		}
		return ret;
	}


	class  Use implements CommandProcessor {

		public String getName() {
			return "use";
		}

		public String help() {
			return "Specify a connected factory to use on future commands";
		}

		public void proccess(String[] args) throws IOException {
			if( args.length < 2) {
				out.writeLine("USAGE: use connected_factory_number");
			} else {
				int pos = Integer.parseInt(args[1]);
				if( pos <0 || pos >= connected.size()) {
					out.writeLine("Invalid factory number "+pos+" ");
				} else {
					currentFactory = pos;
					out.writeLine("Using factory "+pos+" ("+connected.get(currentFactory).getTypeId()+")");
				}
			}


		}
	}

	class  Env implements CommandProcessor {

		public String getName() {
			return "env";
		}

		public String help() {
			return "List environment variables";
		}

		public void proccess(String[] args) throws IOException {
			env.forEach(new BiConsumer<String, String>() {

				public void accept(String key, String val) {
					try {
						out.writeLine(key+"="+val);
					} catch (IOException e) {
						e.printStackTrace();
					}						
				}					
			});
		}
	}


	class  Export implements CommandProcessor {

		public String getName() {
			return "export";
		}

		public String help() {
			return "Set environment variable";
		}

		public void proccess(String[] args) throws IOException {
			if( args.length < 2) {
				out.writeLine("USAGE: export name=value");
			} else {
				for (int idx = 1; idx < args.length; idx++) {
					String parts[] = args[idx].split("=");
					if( parts.length !=2) {
						out.writeLine("syntax error: export="+args[idx] );
					} else {
						env.put(parts[0], parts[1]);
					}
				}
			}
		}
	}

	class  Execute implements CommandProcessor {

		public String getName() {
			return "exec";
		}

		public String help() {
			return "Execute commands from a source other than the keybord";
		}

		public void proccess(String[] args) throws IOException {
			if( args.length < 2) {
				out.writeLine("USAGE: exec source [...]");
			} else {
				for (int idx = 1; idx < args.length; idx++) {
					FileSource file = parseFile(args[idx]);
					if(!file.exists() ) {
						out.writeLine(file+" does not exist");
					} else {
						LFLineReader myIn = new LFLineReader(file.getInputStream());
						try {
							String line = myIn.readLine();
							while( line != null ) {
								line = line.trim();
								if(!line.isEmpty() && !line.startsWith("#")) {
									process(line);
								}
								line = myIn.readLine();
							}

						} finally {
							try {
								myIn.close();
							} catch (Throwable e) {
							}
						}
					}
				}
			}
		}
	}

	class  Connect implements CommandProcessor {
		//  connect to 
		public String getName() {
			return "connect";
		}

		public String help() {
			return "Connect (open) a FileSource factory and make it availible to the Terminal.\n"
					+ "\tWhen a factory is connected it is added to a list of connected factories and, \n"
					+ "\tby default is referenced by the index of the list.\n"
					+ "\tthe notation [#~]path identied a file with an optional connect number. \n"
					+ "\tExample: 1~/data/file.txt mean the file /data/file.txt from conneted factort #1\n"
					+ "\tA path with out the [#~] is assumed to be connection 0."
					+ "\n\tUSAGE: connect factory_id [position] [as name] [from property_file_name]\n"
					;
		}

		public void proccess(String[] args) throws IOException {
			if( args.length < 2) {
				out.writeLine("USAGE: connect factory_id [position] [as name] [from property_file]");
			} else {
				FileSourceFactory tmp = FileSourceFactory.getFileSourceFactory(args[1]);
				if( tmp == null ) {
					out.writeLine("Unknown factory id="+args[1]);
				} else {
					if( !tmp.isConnected()) {
						Properties props = tmp.getConnectProperties();
						if( props != null && props.size()>0) {
							String name = null;
							String fileName=null;
							for(int idx=0,sz=args.length-1; idx < sz; idx++) {
								if( args[idx].equals("as")) {
									name = args[idx+1];
								} else if( args[idx].equals("from")) {
									fileName = args[idx+1];
								}
							}

							setDefaultValues(props,name);
							if( fileName != null ) {
								FileSource file = parseFile(fileName);
								if( !file.exists() || file.length() <=0 ) {
									throw new IOException("Can't load properies. "+file+" does not exists or is empty");
								}
								InputStream pin = file.getInputStream();
								Properties ptmp = new Properties();
								try {
									ptmp.load(pin);
								} finally {
									pin.close();
								}
								props = ptmp;
								name = props.getProperty("Name");
							}


							props.remove("Name");
							String os = System.getProperty("os.name").toLowerCase();
							if( !headless && (os.contains("mac") || os.contains("win"))) {
								FactoryPropertiesDialog dialog = new FactoryPropertiesDialog();
								dialog.showDialog(tmp);
								if( dialog.isCancel()) {
									out.writeLine("Connect canceled by user");
									return;
								} 
								tmp = dialog.getFactory();
							} else {

								CommandLinePropertyEditor editor = new CommandLinePropertyEditor();
								if( !editor.editProperties(name,props) ) {
									out.writeLine("Connect canceled by user");
									return;
								}
							}
							if( name != null ) {
								props.setProperty("Name", name);
							}

							if( tmp.connect(props)) {
								int pos = -1;
								if( args.length > 2) {
									try {
										pos = Integer.parseInt(args[2]);									
									} catch (Exception e) {
									}
									if( pos <0 || pos > connected.size()-1) {
										pos = connected.size();
									} 
									out.writeLine(tmp.getTypeId()+" connected as "+pos);
								}
								connected.add(pos, tmp);
							} else {
								out.writeLine(tmp.getTypeId()+" could not connect.");
							}

						}
					} else {
						out.writeLine("Already connected");
					}
				}
			}		
		}		
	}

	class  Exit implements CommandProcessor {

		public String getName() {
			return "exit";
		}

		public String help() {
			return "Terminate the Terminal program.";
		}

		public void proccess(String[] args) throws IOException {
			int val = 0;
			if( args.length>1) {
				try {
					val = Integer.parseInt(args[1]);
				} catch (Exception e) {
				}
			}
			out.writeLine("Exit Terminal");
			System.exit(val);;
		}

	}

	class  Headless implements CommandProcessor {

		@Override
		public String getName() {
			return "headless";
		}

		@Override
		public void proccess(String[] args) throws IOException {
			if( args.length>1) {
				boolean tmp = Boolean.parseBoolean(args[1]);
				if(!tmp && GraphicsEnvironment.isHeadless()) {
					showError("Can't set headless to true in a headless JVM", null);
				}
				headless = tmp;
			} else {
				out.writeLine("headless "+headless);
			}			
		}

		@Override
		public String help() {
			return "Show the or set headless property.\n\tIf NOT running in a headless environment setting headless to false will enable the Terminal to use dialogs otherwise no GUI is availible.";
		}

	}

	class  History implements CommandProcessor {

		public String getName() {
			return "history";
		}

		public String help() {
			return "Display a list of previos commands.  Optionsally filter the list with arguments.";
		}

		public void proccess(String[] args) throws IOException {
			for(String line : history) {
				if( args.length<2) {
					out.writeLine(line);
				} else {
					for (int idx = 1; idx < args.length; idx++) {
						String val = args[idx];
						if( line.contains(val) ) {
							out.writeLine(line);
							continue;
						}
					}
				}
			}			
		}		
	}

	class  Cd implements CommandProcessor {

		TerminalAction action = new TerminalAction() {

			public void performAction(FileSource file) throws IOException {
				if( !file.exists()) {
					out.writeLine(file.toString()+" does not exist");
				} else {
					file.getFileSourceFactory().setCurrentDirectory(file);
				}
			}
		};

		public String getName() {
			return "cd";
		}

		public String help() {
			return "Change the current working directory. cd [#~]path";
		}

		public void proccess1(String[] args) throws IOException {
			if( args.length > 1) {
				ParsedFileValue val = parseOnly(args[1]);
				FileSource tmp = null;
				if( val.path.startsWith("/")) {
					tmp = connected.get(val.factory).createFileSource(val.path);
				} else {
					FileSource dir = connected.get(val.factory).getCurrentDirectory();
					if( dir != null ) {
						tmp = dir.getChild(val.path);
					}
				}
				if( tmp != null ) {
					if( tmp.exists()) {
						connected.get(val.factory).setCurrentDirectory(tmp);
					} else {
						out.writeLine(val.path+" does not exists");
					}
				} else {
					out.writeLine(val.path+" does not exists");
				}
			} else {
				out.writeLine("USAGE: cd [#~] path");
			}

		}

		public void proccess(String[] args) throws IOException {
			if( args.length == 1) {
				out.writeLine("USAGE: cd [#~]path");
			} else {
				for (int idx = 1; idx < args.length; idx++) {
					ParsedFileValue val = parseOnly(args[idx]);
					if( !followPathWithAction(val.path, connected.get(val.factory), action)) {
						out.writeLine("no matches found for "+args[idx]);
					}
				}
			}
		}
	};

	class  Copy implements CommandProcessor {

		public String getName() {
			return "cp";
		}

		public String help() {
			return "Copy a file or direcctory\n\t"
					+ "cp [-v] [#~]from_path [#~]to_path\n\t"
					+ "Where '#' represent the number of a connected factory.\n\t"
					+ "and -v for verbose output ";
		}

		public void proccess(String[] args) throws IOException {

			if( args.length < 3) {
				out.writeLine("USAGE: cp [-v] [#~]from_path [#~]to_path\nWhere '#' represent the number of a connected factory");

			} else {
				int startArg = 1;
				boolean verbose = false;
				if( args[startArg].equals("-v")) {
					verbose = true;
					startArg++;
				}

				FileSource from = parseFile(args[startArg]);
				FileSource to = parseFile(args[startArg+1]);
				copy(from,to,verbose);
			}

		}

	};

	class  Find implements CommandProcessor {

		public String getName() {
			return "find";
		}

		public String help() {
			return "Search direcctory\n\tfind [-r] [#~]path [-depth #] [reg_exp] [-size=><=#-#\n\tWhere '#' represent the number of a connected factory."
					+ "\n\tuse -r to recursivly search the tree "

					;
		}


		public void proccess(String[] args) throws IOException {
			boolean recursive = false;
			Size size=null;
			int depth=-1;

			Pattern rx = Pattern.compile(".*");
			ParsedArgs a = parseArgs(args);
			FileSource dir;
			for(String name : a.namesArgs.keySet()) {
				if( name.equals("r")) {
					recursive = true;
				} else if( name.equals("name")) {
					rx = Pattern.compile(a.namesArgs.get(name).replace("*", ".*"));						
				} else if( name.equals("depth")) {						
					depth = Integer.parseInt(a.namesArgs.get(name));
				} else if( name.equals("size")) {
					size = parseSize(a.namesArgs.get(name));
				}
			}								

			if( a.args.size()<1) {
				dir = connected.get(currentFactory).getCurrentDirectory();					
				search(dir,recursive,rx,size,depth,0);
			} else {
				for(String path : a.args) {
					dir = parseFile(path);
					search(dir,recursive,rx,size,depth,0);
				}
			}
		}


		private void search(FileSource dir, boolean recursive, Pattern rx, Size size, int depth,int currDepth) throws IOException {
			if( dir == null || !dir.exists() || !dir.isDirectory()) {
				//throw new IOException("Can only search existing directories");
				return;
			}

			FileSource[] kids = dir.listFiles();
			if( kids != null ) {

				for(FileSource file : kids) {

					boolean print = true;
					if( depth > 0 ) {
						print = currDepth<=depth;
					}
					if( rx != null ) {
						Matcher m = rx.matcher(file.getName());
						print =  m.matches();							
					}

					if( print && size != null ) {
						long len = file.length();
						switch (size.type) {
						case LessThan: print = len < size.a;break;
						case LessThanOrEqual: print = len <= size.a;break;
						case GreterThan: print = len > size.a;break;
						case GreaterThanOrEqual: print = len >= size.a;break;
						case Equal: print = len == size.a;break;
						case Between: print = len >= size.a && len < size.b;break;
						default:
							break;
						}

					}
					if( print ) {
						out.writeLine("\t"+file.toString());
					}
				}

				if( recursive ) {
					for(FileSource file : kids) {
						if( file.isDirectory()) {
							search(file,recursive,rx,size,depth, currDepth+1);
						}
					}
				}
			}
		}

	}

	class  Find1 implements CommandProcessor {


		public String getName() {
			return "find";
		}

		public String help() {
			return "Search direcctory\n\tfind [-r] [#~]path [reg_exp]\n\tWhere '#' represent the number of a connected factory."

					+ "\n\tuse -r to recursivly search the tree "
					;
		}

		public void proccess(String[] args) throws IOException {
			FileSource dir = null;
			boolean recursive = false;
			int startArg = 1;
			Pattern rx = Pattern.compile(".*");
			if( args.length == 1) {
				dir = connected.get(currentFactory).getCurrentDirectory();
			} else {

				if( args[startArg].equals("-r")) {
					recursive = true;
					startArg++;
				}
				dir = parseFile(args[startArg++]);
				if( args.length>startArg) {
					rx = Pattern.compile(args[startArg]);
				}
			}

			if( !dir.exists() || !dir.isDirectory()) {
				throw new IOException("Can only search existing directories");
			}

			search(dir,recursive,rx);



		}

		private void search(FileSource dir, boolean recursive, Pattern rx) throws IOException {
			FileSource[] kids = dir.listFiles();
			if( kids != null ) {
				for(FileSource file : kids) {
					Matcher m = rx.matcher(file.getName());
					if( m.matches()) {
						out.writeLine("\t"+file.toString());
					}
				}
				if( recursive ) {
					for(FileSource file : kids) {
						if( file.isDirectory()) {
							search(file,recursive,rx);
						}
					}
				}
			}
		}

	};


	class  Delete implements CommandProcessor {

		public String getName() {
			return "rm";
		}

		public String help() {

			return "Delete a file or direcctory\n\trm -[r] [-v] [#~]path \n\tWhere '#' represent the number of a connected factory.\n\tuse -v for verbose output\n\tuse -r to deleted direcories recursively ";
		}

		public void proccess(String[] args) throws IOException {

			if( args.length < 2) {
				out.writeLine("USAGE: rm -[r] [-v] [#~]path \n\tWhere '#' represent the number of a connected factory.\n\tuse -v for verbose output\n\tuse -r to deleted direcories recursively ");

			} else {
				int startArg = 1;
				boolean verbose = false;
				boolean recursive = false;
				int pathArg = startArg;
				for(int idx= 1; idx < args.length;idx++ ) {
					if( args[idx].equals("-v")) {
						verbose = true;
						startArg++;
					} else if( args[idx].equals("-r")) {
						recursive = true;
						startArg++;
					}	else {
						pathArg=idx;
					}
				}


				FileSource from = parseFile(args[pathArg]);

				delete(from,recursive,verbose);
			}

		}

		private void delete(FileSource file, boolean recursive, boolean verbose) throws IOException {
			if( verbose) {
				out.writeLine("Deleting "+file);
			}

			if( file.isDirectory() ) {
				FileSource[] kids = file.listFiles();
				if( kids != null && kids.length>0) {
					if( !recursive) {
						throw new IOException("Can't delete "+file+"\nuse -r to delete directories with contenet.");
					}
					for(FileSource f: kids) {
						delete(f,recursive,verbose);
					}
				}
			}
			if( !file.delete()) {
				throw new IOException("Can'r delete "+file);
			}

		}

	};


	class  Ls implements CommandProcessor {

		public String getName() {
			return "ls";
		}

		public String help() {
			return "List compunets in a FileSource factory";
		}


		public void proccess(String[] args) throws IOException {

			boolean showHidden = false;
			boolean showLong   = false;
			List<String> files = new ArrayList<>();
			for(int idx=1; idx < args.length; idx++ ) {
				if(args[idx].startsWith("-")) {
					showHidden = args[idx].contains("a");
					showLong = args[idx].contains("l");
				} else {
					files.add(args[idx]);
				}
			}

			if( files.size() == 0) {
				FileSource dir = connected.get(currentFactory).getCurrentDirectory();
				list(dir,showHidden,showLong);			
			} else {
				for(int idx=0,sz=files.size(); idx < sz; idx++ ) {
					String arg = files.get(idx);
					if( arg != null ) {
						arg = arg.trim();
						if( !arg.isEmpty()) {
							ParsedFileValue val = parseOnly(arg);
							List<Object> ret = followPath(arg, connected.get(val.factory));
							if( ret != null && ret.size()>0) {						
								Object obj2 =  ret.get(ret.size()-1);
								list1(obj2 ,showHidden,showLong);
							}
						}
					}
				}				
			}
		}

		private boolean list1(Object obj,boolean showHidden,boolean showLong) throws IOException {
			boolean ret = false;
			if( obj == null ) {
				out.writeLine("No matches found.");
			} else {
				if (obj instanceof List) {
					List<?> list = (List<?>) obj;
					for(Object obj2 : list) {
						ret = list1(obj2,showHidden,showLong);
					} 
				} else if( obj instanceof FileSource) {
					list((FileSource)obj,showHidden,showLong);
					ret = true;
				} else {
					throw new IllegalStateException("Object is not valid class.  Class  = "+obj.getClass());
				}		
			}
			return ret;
		}

		private void list(FileSource dir,boolean showHidden,boolean showLong) throws IOException {
			if( dir.isDirectory()) {
				FileSource[] kids = dir.listFiles();
				if( kids != null ) {
					for(FileSource f : kids) {
						print(f,showHidden,showLong);
					}
				}
			} else {
				print(dir,showHidden,showLong);
			}
		}
	};

	Map<String,CommandProcessor> procs = new TreeMap<String, CommandProcessor>();
	private Map<String, String> env;



	public Terminal () {

		addCmd(new Exit());
		addCmd(new Pwd());
		addCmd(new Show());
		addCmd(new Cd());
		addCmd(new Ls());
		addCmd(new Connect());
		addCmd(new Use());
		addCmd(new History());
		addCmd(new Help());
		addCmd(new Copy());
		addCmd(new Delete());
		addCmd(new Find());
		addCmd(new Test());
		addCmd(new Env());
		addCmd(new Export());
		addCmd(new Echo());
		addCmd(new Execute());

		FileSourceFactory factory = FileSourceFactory.getDefaultFactory();
		if( !factory.isConnected()) {
			try {
				if(!factory.connect()) {					
					out.writeLine("Can't connect to default ("+factory.getTypeId()+") switching to file proxy");
					factory = new FileProxyFactory();
				}
			} catch (IOException e) {
			}
		}
		connected.add(factory);

		try {
			initEnv();
		} catch (IndexOutOfBoundsException e) {
			showError("Failed to initialize", e);	
		} catch (Throwable e) {
			showError("Failed to initialize", e);
		}
	}

	public void showError(String msg, Throwable e) {
		if( !headless) {
			JOptionPane.showInternalMessageDialog(null, e, msg==null?"":msg, JOptionPane.ERROR_MESSAGE);
		} else {
			try {
				out.writeLine(msg+":"+e);
			} catch (IOException e1) {
				e1.printStackTrace();
			}		
		}
	}

	private boolean isNameChar(char c) {
		return c != '$' && Character.isJavaIdentifierPart(c);
	}

	private String expand(String str) {
		int pos = str.indexOf('$');
		while(pos >0) {
			StringBuilder tmp = new StringBuilder();
			int start = pos;
			char ch = str.charAt(++pos);
			int sz = str.length()-1;;

			do {
				tmp.append(ch);				

			}  while(pos<sz && isNameChar((ch=str.charAt(++pos)))) ;

			String name = tmp.toString();
			String val = env.get(name);
			if( val == null ) {
				val = "";
			}
			String left  = str.substring(0,start);
			String right = pos<sz?str.substring(pos):"";
			str = left+val+right;
			pos = str.indexOf('$');
		}

		return str;
	}

	private void initEnv() throws IOException {
		env = new TreeMap<String, String>();
		System.getenv().forEach(new BiConsumer<String, String>() {
			public void accept(String t, String u) {
				env.put(t, u);				
			}
		});

		String rc = env.get("HOME")+"/.fileSourceRc";
		FileSource f = FileSourceFactory.getDefaultFactory().createFileSource(rc);
		if( f.exists()) {
			Execute exec = new Execute();
			exec.proccess(("exec "+f.getAbsolutePath()).split(" "));
		}
	}


	private class ParsedArgs {
		Map<String,String> namesArgs = new HashMap<String, String>();
		List<String> args = new ArrayList<String>();
	}

	private ParsedArgs parseArgs(String [] args) {
		ParsedArgs ret = new ParsedArgs();
		for (int idx = 1; idx < args.length; idx++) {
			if( args[idx].startsWith("-")) {
				int pos = args[idx].indexOf('=');
				if( pos >0) {
					String [] parts = args[idx].split("[=]");
					ret.namesArgs.put(parts[0].substring(1), parts[1]);
				} else if( idx < args.length-1) {
					String name = args[idx].substring(1);

					String val = args[idx+1];
					if( val.startsWith("-")) {
						// -v (named arg with no value)
						ret.namesArgs.put(name, name);
					} else {
						ret.namesArgs.put(name, args[++idx]);
					}
				} else {
					ret.namesArgs.put(args[idx], args[idx]);
				}
			} else {
				ret.args.add(args[idx]);
			}
		}
		return ret;
	}

	private class ParsedFileValue {
		int factory;
		String path;
	}

	public FileSource parseFile(String path) throws IOException {
		FileSource ret = null;
		FileSourceFactory f = connected.get(currentFactory);
		if(path.length()>2 && path.charAt(1) == '~') {
			String tmp = path.substring(0,1);
			path = path.substring(2);
			int pos = Integer.parseInt(tmp);
			f = connected.get(pos);
		}

		ret = f.createFileSource(path);

		return ret;
	}

	public ParsedFileValue parseOnly(String path) throws IOException {
		ParsedFileValue ret = new ParsedFileValue();

		if(path.length()>1 && path.charAt(1) == '~') {
			String tmp = path.substring(0,1);
			ret.path = path.substring(2);
			ret.factory = Integer.parseInt(tmp);			
		} else {
			ret.factory=currentFactory;
			ret.path = path;			
		}

		return ret;
	}

	private void addCmd(CommandProcessor cmd) {
		procs.put(cmd.getName().toLowerCase(), cmd);		
	}


	int currentFactory=0;
	List<FileSourceFactory> connected = new ArrayList<FileSourceFactory>();
	ILineReader in  = new LFLineReader(System.in);
	ILineWriter out = new LFLineWriter(System.out);

	boolean headless = GraphicsEnvironment.isHeadless();
	List<String> history = new ArrayList<String>();


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

	public void run()  {
		started = running = true;

		while(running && !stopping ) {
			try {
				FileSource dir = connected.get(currentFactory).getCurrentDirectory();


				String line = "";
				while( line != null) {
					String prompt = "";

					if( dir != null) {
						prompt = (connected.get(currentFactory).getTypeId()+" "+  dir.getName());
					}
					out.write("\n"+prompt+" > ");
					if( (line = in.readLine()) !=null) {
						process(line);
						dir = connected.get(currentFactory).getCurrentDirectory();
					}			
				}
				out.writeLine("Exit Terminal");

			} catch (Throwable e) {
				try {
					out.writeLine(e.toString());
				} catch (IOException e1) {
				}
				if( isStopOnError() ) {
					stop();
				}				
			}
		}
	}



	private void process(String line) throws IOException {
		CommandProcessor cmd = null;
		line = line.trim();
		if( line.equals(".") && history.size()>0) {
			line = history.get(history.size()-1);
			out.writeLine(line);
		} else {
			line = expand(line);
		}

		String parts [] = line.split("\\s");
		if( (cmd = procs.get(parts[0])) != null ) {
			try {						
				cmd.proccess(parts);
				history.add(line);
			} catch (Exception e) {
				out.writeLine(e.toString());
				e.printStackTrace();
			}
		} else {
			out.writeLine("Unrecognized command '"+line+"'");

		}


	}



	private void setDefaultValues(Properties props2, String name) {
		for(Object key : props2.keySet()) {
			String pname = key.toString();

			String newName = pname;
			if( name != null ) {
				newName = name+"."+pname;
			}
			String val = props2.getProperty(pname);
			if( val == null || val.isEmpty()) {
				val = System.getProperty(newName);
				if( val == null || val.isEmpty()) {
					val = System.getProperty(pname);
					if( val == null || val.isEmpty()) { 
						val = "";					
					}
				}								
			}
			props2.setProperty(pname, val);
		}
		if(name != null && props2.containsKey("Name")) {
			props2.setProperty("Name", name);
		}

	}


	//-rw-rw-r-- 1 ec2-user ec2-user    2186 Feb  2 08:41 build.txt
	private  void print(FileSource file, boolean showHidden,boolean showLong) throws IOException {
		if( !file.exists()) {
			out.writeLine(file.toString()+" does not exist");
		} else {

			if( !isHidden(file) || showHidden) {
				if( showLong ) {
					SimpleDateFormat fmt = new SimpleDateFormat("MM-dd-yyyy");
					// 012345678
					char[] perm = "---------".toCharArray();
					//-rw-r--r--  1 user group  272 Mar 17 08:22 test.txt
					FileSource jdbc = file;
					if( jdbc.canOwnerRead() ) {
						perm[0] = 'r';
					}
					if( jdbc.canOwnerWrite() ) {
						perm[1] = 'w';
					}

					if( jdbc.canOwnerExecute() ) {
						perm[2] = 'x';
					}

					if( jdbc.canGroupRead() ) {
						perm[3] = 'r';
					}

					if( jdbc.canGroupWrite() ) {
						perm[4] = 'w';
					}

					if( jdbc.canGroupExecute() ) {
						perm[5] = 'x';
					}

					if( jdbc.canGroupRead() ) {
						perm[6] = 'r';
					}

					if( jdbc.canOtherWrite() ) {
						perm[7] = 'w';
					}

					if( jdbc.canGroupExecute() ) {
						perm[8] = 'x';
					}


					String str = new String(perm);
					out.writeLine(String.format(str+"  %s %s %s %s %s",

							file.isDirectory() ? "":""+file.length(),
									fmt.format(new Date(file.getCreateDate())),
									fmt.format(new Date(file.lastModified())),

									file.isDirectory() ? "d":"f",

											file.getName()
							)
							);
				} else {
					out.write(file.getName()+"\t");
				} 
			} 
		}
	}

	private boolean isHidden(FileSource file) throws IOException {		
		boolean ret = file.isHidden() || file.getName().startsWith(".");
		return ret;
	}	

}
