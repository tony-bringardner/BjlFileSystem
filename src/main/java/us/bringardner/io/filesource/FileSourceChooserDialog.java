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

import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.EventQueue;
import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.BeanProperty;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Stack;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.swing.DefaultCellEditor;
import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.UIDefaults;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.DefaultTreeCellRenderer;

public class FileSourceChooserDialog extends JDialog implements DragGestureListener, ClipboardOwner {

	private static final long serialVersionUID = 1L;
	//data:image/jpeg;base64,
	private Icon fileIcon;
	private Icon folderIcon;
	private RecentFileMenu recentFileMenu;


	// ************************
	// ***** Dialog Types *****
	// ************************

	/**
	 * Type value indicating that the <code>JFileChooser</code> supports an
	 * "Open" file operation.
	 */
	public static final int OPEN_DIALOG = 0;

	/**
	 * Type value indicating that the <code>JFileChooser</code> supports a
	 * "Save" file operation.
	 */
	public static final int SAVE_DIALOG = 1;

	/**
	 * Type value indicating that the <code>JFileChooser</code> supports a
	 * developer-specified file operation.
	 */
	public static final int CUSTOM_DIALOG = 2;


	/**
	 * Type value indicating that the <code>JFileChooser</code> supports a
	 * developer-specified file operation.
	 */
	public static final int BROWSER_DIALOG = 3;

	// ********************************
	// ***** Dialog Return Values *****
	// ********************************

	/**
	 * Return value if cancel is chosen.
	 */
	public static final int CANCEL_OPTION = 1;

	/**
	 * Return value if approve (yes, ok) is chosen.
	 */
	public static final int APPROVE_OPTION = 0;

	/**
	 * Return value if an error occurred.
	 */
	public static final int ERROR_OPTION = -1;


	// **********************************
	// ***** JFileChooser properties *****
	// **********************************


	/** Instruction to display only files. */
	public static final int FILES_ONLY = 0;

	/** Instruction to display only directories. */
	public static final int DIRECTORIES_ONLY = 1;

	/** Instruction to display both files and directories. */
	public static final int FILES_AND_DIRECTORIES = 2;

	/** Instruction to cancel the current selection. */
	public static final String CANCEL_SELECTION = "CancelSelection";

	/**
	 * Instruction to approve the current selection
	 * (same as pressing yes or ok).
	 */
	public static final String APPROVE_SELECTION = "ApproveSelection";

	/** Identifies change in the text on the approve (yes, ok) button. */
	public static final String APPROVE_BUTTON_TEXT_CHANGED_PROPERTY = "ApproveButtonTextChangedProperty";

	/**
	 * Identifies change in the tooltip text for the approve (yes, ok)
	 * button.
	 */
	public static final String APPROVE_BUTTON_TOOL_TIP_TEXT_CHANGED_PROPERTY = "ApproveButtonToolTipTextChangedProperty";

	/** Identifies change in the mnemonic for the approve (yes, ok) button. */
	public static final String APPROVE_BUTTON_MNEMONIC_CHANGED_PROPERTY = "ApproveButtonMnemonicChangedProperty";

	/** Identifies change in the mnemonic for the approve (yes, ok) button. */
	public static final String CANCEL_BUTTON_MNEMONIC_CHANGED_PROPERTY = "CancelButtonMnemonicChangedProperty";

	/** Instruction to display the control buttons. */
	public static final String CONTROL_BUTTONS_ARE_SHOWN_CHANGED_PROPERTY = "ControlButtonsAreShownChangedProperty";

	/** Identifies user's directory change. */
	public static final String DIRECTORY_CHANGED_PROPERTY = "directoryChanged";

	/** Identifies change in user's single-file selection. */
	public static final String SELECTED_FILE_CHANGED_PROPERTY = "SelectedFileChangedProperty";

	/** Identifies change in user's multiple-file selection. */
	public static final String SELECTED_FILES_CHANGED_PROPERTY = "SelectedFilesChangedProperty";

	/** Enables multiple-file selections. */
	public static final String MULTI_SELECTION_ENABLED_CHANGED_PROPERTY = "MultiSelectionEnabledChangedProperty";

	/**
	 * Says that a different object is being used to find available drives
	 * on the system.
	 */
	public static final String FILE_SYSTEM_VIEW_CHANGED_PROPERTY = "FileSystemViewChanged";

	/**
	 * Says that a different object is being used to retrieve file
	 * information.
	 */
	public static final String FILE_VIEW_CHANGED_PROPERTY = "fileViewChanged";

	/** Identifies a change in the display-hidden-files property. */
	public static final String FILE_HIDING_CHANGED_PROPERTY = "FileHidingChanged";

	/** User changed the kind of files to display. */
	public static final String FILE_FILTER_CHANGED_PROPERTY = "fileFilterChanged";

	/**
	 * Identifies a change in the kind of selection (single,
	 * multiple, etc.).
	 */
	public static final String FILE_SELECTION_MODE_CHANGED_PROPERTY = "fileSelectionChanged";

	/**
	 * Says that a different accessory component is in use
	 * (for example, to preview files).
	 */
	public static final String ACCESSORY_CHANGED_PROPERTY = "AccessoryChangedProperty";

	/**
	 * Identifies whether a the AcceptAllFileFilter is used or not.
	 */
	public static final String ACCEPT_ALL_FILE_FILTER_USED_CHANGED_PROPERTY = "acceptAllFileFilterUsedChanged";

	/** Identifies a change in the dialog title. */
	public static final String DIALOG_TITLE_CHANGED_PROPERTY = "DialogTitleChangedProperty";

	/**
	 * Identifies a change in the type of files displayed (files only,
	 * directories only, or both files and directories).
	 */
	public static final String DIALOG_TYPE_CHANGED_PROPERTY = "DialogTypeChangedProperty";

	/**
	 * Identifies a change in the list of predefined file filters
	 * the user can choose from.
	 */
	public static final String CHOOSABLE_FILE_FILTER_CHANGED_PROPERTY = "ChoosableFileFilterChangedProperty";
	private static final String SHOW_EXTENTIONS_PREFERENCE = "ShowExtentions";
	private static final String SHOW_HIDDEN_FILES_PREFERENCE = "ShowHiddenFiles";

	private class FileSourceDropTargetListener extends DropTargetAdapter {

		@SuppressWarnings("unused")
		private final DropTarget dropTarget;
		@SuppressWarnings("unused")
		private final Component table;

		public FileSourceDropTargetListener(Component table) {
			this.table = table;
			dropTarget = new DropTarget(table, DnDConstants.ACTION_COPY, this, true, null);
		}


		@SuppressWarnings("unchecked")
		public void drop(DropTargetDropEvent event) {

			System.out.println("Enter drop");
			if( currentDirectory == null ) {
				return;
			}
			System.out.println("Enter drop 2");
			try {


				FileSourceFactory factory = currentDirectory.getFileSourceFactory();
				Transferable tr = event.getTransferable();
				List<FileSource> newFiles = null;

				if (event.isDataFlavorSupported(FileSourceTransferable. fileSourceFlavor)) {
					event.acceptDrop(DnDConstants.ACTION_COPY);
					Object obj = tr.getTransferData(FileSourceTransferable.fileSourceFlavor);
					if (obj != null && obj instanceof List) {
						newFiles = (List<FileSource>) obj;
						event.acceptDrop(DnDConstants.ACTION_COPY);
						event.dropComplete(true);
					}					
				} else if( event.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
					event.acceptDrop(DnDConstants.ACTION_COPY);
					Object obj =  tr.getTransferData(DataFlavor.javaFileListFlavor);

					if (obj != null && obj instanceof List) {
						newFiles = new ArrayList<FileSource>();
						List<File> list = (List<File>) obj;
						for(File f : list) {							
							newFiles.add(factory.createFileSource(f.getCanonicalPath()));
						}
						event.acceptDrop(DnDConstants.ACTION_COPY);
						event.dropComplete(true);
					}

					if( newFiles !=null && newFiles.size()>0 ) {
						// move into the currentDirectory if it's not already there
						for(FileSource file : newFiles) {
							copyToCurrentDir(currentDirectory, file);
						}
						rescanCurrentDirectory();
						event.dropComplete(true);
						return;
					}

				}

				event.rejectDrop();
			} catch (Exception e) {

				e.printStackTrace();
				event.rejectDrop();
			}
			System.out.println("Exit drop");
		}

	}


	class FileSourceTableCellRenderer extends DefaultTableCellRenderer {

		private static final long serialVersionUID = 1L;
		private TableCellRenderer oldRenderer;
		public final long KB = 1024;
		public final  long MB = KB * KB;
		public final  long GB = MB * KB;
		public  final long TB = GB * KB;

		private SimpleDateFormat fmt = new SimpleDateFormat("MM/dd/yyyy hh:mm:ss a");

		JLabel label = new JLabel();
		private Color background;
		private Color selectedBackground;




		public FileSourceTableCellRenderer(TableCellRenderer r) {
			oldRenderer = r;
			label.setOpaque(true);
			UIDefaults defaults = javax.swing.UIManager.getDefaults();
			selectedBackground = defaults.getColor("List.selectionBackground");
			background = defaults.getColor("List.Background");

		}




		private String fmt(long val) {
			String ret = null;
			String lb = null;

			if( val < 0 ) {
				ret = "";
				lb = "";
			} else if( val > TB) {

				ret = ""+((double)val / (double)TB);
				lb = " TB";
			} else if( val > GB) {
				ret = ""+((double)val / (double)GB);
				lb = " GB";
			} else if( val > MB) {
				ret = ""+((double)val / (double)MB);
				lb = " MB";
			} else if( val > KB) {
				ret = ""+((double)val / (double)KB);
				lb = " KB";
			} else {
				ret = ""+val;
				lb = "";
			}
			int idx = ret.indexOf('.');
			if( idx > 0 && ret.length() > idx+3) {
				ret = ret.substring(0,idx+3);
			}

			return ret+lb;

		}

		public Component getTableCellRendererComponent(JTable table, Object value,boolean isSelected, boolean hasFocus, int row, int col) {

			Component c = null;


			if( oldRenderer != null ) {
				c = oldRenderer.getTableCellRendererComponent(table, value,isSelected, hasFocus, row, col);
			} else {
				c = super.getTableCellRendererComponent(table, value,isSelected, hasFocus, row, col);
			}

			if(isSelected) {
				c.setBackground(selectedBackground);
			} else {
				c.setBackground(background);
			}

			if( col == 3 ) {

				Long lv = (Long)value;
				label.setText(fmt(lv));

				label.setBackground(c.getBackground());
				label.setForeground(c.getForeground());
				label.setOpaque(true);
				c = label;
			} else if(col == 2) {
				label.setText(fmt.format((Date)value));
				label.setBackground(c.getBackground());
				label.setForeground(c.getForeground());
				label.setOpaque(true);
				c = label;
			}
			FileSourceTableModel m =  (FileSourceTableModel) table.getModel();
			FileSourceWrapper w = m.taccepted.get(row);
			if( !w.accepted) {
				c.setBackground(Color.lightGray);				
			}
			if( col == 1 && isSelected) {

			}

			return c;
		}
	}

	private class FileSourceTableModel extends DefaultTableModel {
		private static final long serialVersionUID = 1L;

		private String [] columnNames = {"","Name","Date Modified","Size"};

		private FileSource dir;
		private List<FileSourceWrapper> taccepted= new ArrayList<>();

		public FileSourceTableModel(FileSource dir1) throws IOException {
			this.dir = dir1;
			if( dir != null ) {
				FileSource [] kids = dir.listFiles();
				if( kids != null ) {
					Arrays.sort(kids,new Comparator<FileSource>() {

						@Override
						public int compare(FileSource o1, FileSource o2) {
							return o1.getName().toLowerCase().compareTo(o2.getName().toLowerCase());
						}

					});
					for(FileSource f : kids) {
						if(!showHiddenFiles && f.isHidden()) {
							continue;
						}
						FileSourceWrapper w = new FileSourceWrapper(f);
						w.accepted= accept(f);
						taccepted.add(w);
					}
				}
			}
		}

		@Override
		public boolean isCellEditable(int row, int column) {
			FileSourceWrapper w = taccepted.get(row);
			if( w.accepted) {
				return column==1?true:false;
			} else {
				return false;
			}
		}

		@Override
		public void setValueAt(Object aValue, int row, int column) {
			if( column == 1 ) {
				FileSourceWrapper w = taccepted.get(row);
				try {
					String name = w.file.getName();
					String display = w.name;
					String newName = (String)aValue;
					if(newName.equals(display) || newName.equals(name)) {
						return;
					}
					if( !name.equals(display)) {
						int idx = name.lastIndexOf('.');
						if( idx > 0 ) {
							String ext = name.substring(idx);
							if( !newName.endsWith(ext)) {
								newName += ext;				
							}
						}
					}

					FileSource p = w.file.getParentFile();
					FileSource file2 = p.getChild(newName);
					if(w.file.renameTo(file2)) {
						taccepted.set(row, new FileSourceWrapper(file2) );
						fireTableCellUpdated(row, column);
					} else {
						System.out.println("Can't rename "+w.file +" to "+aValue);
					}
				} catch (IOException e) {
					showError("", e);


				}
			}
		}

		public int getRow(FileSource file) throws IOException {
			int ret = -1;
			String fileName = file.getCanonicalPath(); 
			for (int idx = 0,sz=taccepted.size(); idx < sz; idx++) {
				FileSourceWrapper tmp = taccepted.get(idx);
				if( fileName.equals(tmp.file.getCanonicalPath())) {
					ret = idx;
					break;
				}				
			}
			return ret;
		}

		@Override
		public Object getValueAt(int row, int column) {
			Object ret = null;
			FileSourceWrapper w = taccepted.get(row);
			try {
				switch (column) {
				case 0:ret = w.file.isDirectory()?folderIcon:fileIcon;break;
				case 1:ret = w.name;

				break;
				case 2:ret = new Date(w.file.lastModified());break;
				case 3:ret = w.file.length();break;

				default:
					break;
				}
			} catch (IOException e) {
				showError("", e);

			}
			return ret;
		}

		@Override
		public int getRowCount() {
			if( taccepted == null ) {
				return 0;
			}
			return taccepted.size();
		}

		@Override
		public int getColumnCount() {			
			return columnNames.length;
		}


		@Override
		public String getColumnName(int column) {			
			return columnNames[column];
		}

		@Override
		public Class<?> getColumnClass(int columnIndex) {
			Class<?> ret = null;
			switch (columnIndex) {
			case 0:ret=Icon.class;break;
			case 1:ret=String.class;break;
			case 2:ret=Date.class;break;
			case 3:ret=Long.class;break;
			default:
				throw new RuntimeException("Invalid col="+columnIndex);				
			}
			return ret;
		}

	}

	private class FileSourceWrapper {
		boolean accepted=true;
		FileSource file;
		String name ;
		FileSourceWrapper(FileSource f) {
			file = f;
			name = f.getName();
			if(!showExtention) {
				int idx = name.lastIndexOf('.');
				if( idx >0 ) {
					name = name.substring(0, idx);
				}
			}
		}


		@Override
		public String toString() {
			return name;
		}
	}

	// ******************************
	// ***** instance variables *****
	// ******************************

	private String approveButtonText = null;
	private String approveButtonToolTipText = null;


	private List<FileSourceFilter> filters = new ArrayList<>();

	private List<ActionListener> actionListners = new ArrayList<ActionListener>();

	private int dialogType = 13;	

	private JComponent accessory = null;

	private boolean controlsShown = true;

	private boolean showHiddenFiles = false;

	private int fileSelectionMode = FILES_AND_DIRECTORIES;

	private boolean multiSelectionEnabled = true;

	private boolean useAcceptAllFileFilter = true;

	private boolean dragEnabled = false;

	private FileSourceFilter fileFilter = null;

	private FileSource currentDirectory = null;

	private List<FileSource> parentTreeList = new ArrayList<FileSource>();

	//============================================
	private final JPanel contentPanel = new JPanel();
	private JTable table;
	private boolean isMac = false;

	private boolean showExtention=false;
	private boolean canceled;	
	private JComboBox<String> parentTreeComboBox;
	private JComboBox<String> filterComboBox;

	private JButton cancelButton;
	private JButton okButton;
	private JButton newFolderButton;
	private JPanel controlPane;
	private JTextField saveAsFileNameTextField;
	private JPanel saveAsFileNamePanel;
	private JMenuBar menuBar;
	private JMenu mainMenu;
	@SuppressWarnings("unused")
	private FileSourceDropTargetListener dropTargetListener;
	private DragSource dragSource;
	private JButton previousButton;
	private JButton nextButton;



	public boolean isControlsShown() {
		return controlsShown;
	}


	public void copyToCurrentDir(FileSource dir, FileSource file) throws IOException {
		if( !dir.isChildOfMine(file) && file.exists()) {

			FileSource newFile = dir.getChild(file.getName());
			if( file.isDirectory()) {
				if(! newFile.mkdirs()) {
					showError("Can't create directory "+newFile.getCanonicalPath(), null);
				} else {
					FileSource[] kids = file.listFiles();
					if( kids != null ) {
						for(FileSource f : kids) {
							copyToCurrentDir(newFile, f);
						}
					}
				}
			} else {
				OutputStream out = newFile.getOutputStream();
				InputStream in = file.getInputStream();
				byte[] data = in.readAllBytes();
				out.write(data);
				out.close();
				in.close();															
			}

		}
	}


	public boolean isShowExtention() {
		return showExtention;
	}

	public void setShowExtention(boolean showExtention) {
		this.showExtention = showExtention;
		rescanCurrentDirectory();
	}

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		
		//System.getProperties().list(System.out);
		//System.exit(0);
		try {
			FileSource file1 = FileSourceFactory.getDefaultFactory().createFileSource("/Volumes/Data/eclipse-workspace-jmail/BjlFileSystem/pom2.xml");
			FileSource file2 = FileSourceFactory.getDefaultFactory().createFileSource("/Volumes/Data/eclipse-workspace-jmail/BjlFileSystem/src");
			FileSource [] files = {file1,file2};

			/*

			File file3 =new File("/Volumes/Data/eclipse-workspace-jmail/BjlFileSystem/src/pom.xml");



			JFileChooser fc = new JFileChooser();

			//fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
			FileFilter cff = new FileFilter() {

				@Override
				public String getDescription() {

					return "Text File";
				}

				@Override
				public boolean accept(File f) {

					return f.getName().endsWith(".txt");
				}
			};
			fc.addChoosableFileFilter(cff);
			//fc.setSelectedFile(file3);
			fc.showSaveDialog(null);

			System.exit(0);
			 */

			FileSourceChooserDialog dialog = new FileSourceChooserDialog();
			dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
			dialog.setControlButtonsAreShown(true);
			dialog.addChoosableFileFilter(new FileSourceFilter() {

				@Override
				public String getDescription() {

					return "Text Files (.txt)";
				}

				@Override
				public boolean accept(FileSource f) {
					return f.getName().endsWith(".txt");
				}
			});

			dialog.setDragEnabled(true);
			//dialog.setFileSelectionMode(DIRECTORIES_ONLY);
			dialog.setSelectedFile(files[0]);
			//dialog.setMultiSelectionEnabled(false);
			if(dialog.showOpenDialog(null) == APPROVE_OPTION) {

				FileSource[] files2 = dialog.getSelectedFiles();
				System.out.println("Selected "+files2.length+" files");
				for(FileSource f : files2) {
					System.out.println(f);
				}

			} else {
				System.out.println("Nothing selected");
			}
			//dialog.showSaveDialog(null);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	
	Stack<FileSource> nextStack = new Stack<FileSource>();
	
	/**
	 * Create the dialog.
	 */
	public FileSourceChooserDialog() {
		//os.name=Mac OS X
		isMac = System.getProperty("os.name").toLowerCase().startsWith("mac");
		String[] factories = FileSourceFactory.getRegisterdFactories();
		Preferences p = Preferences.userNodeForPackage(FileSourceChooserDialog.class);
		showExtention = p.getBoolean(SHOW_EXTENTIONS_PREFERENCE, showExtention);
		showHiddenFiles = p.getBoolean(SHOW_HIDDEN_FILES_PREFERENCE, showHiddenFiles);

		setTitle("Open");
		setBounds(100, 100, 753, 442);
		getContentPane().setLayout(new BorderLayout());
		contentPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
		getContentPane().add(contentPanel, BorderLayout.CENTER);
		contentPanel.setLayout(new BorderLayout(0, 0));

		JPanel centerPanel = new JPanel();
		contentPanel.add(centerPanel, BorderLayout.CENTER);
		centerPanel.setLayout(new BorderLayout(0, 0));
		JPanel northPanel = new JPanel();
		centerPanel.add(northPanel, BorderLayout.NORTH);
		northPanel.setLayout(new BorderLayout(0, 0));

		JPanel panel = new JPanel();
		northPanel.add(panel, BorderLayout.CENTER);
		
		previousButton = new JButton("<");
		previousButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionPrevious();
			}
		});
		panel.add(previousButton);
		parentTreeComboBox = new JComboBox<String>();
		panel.add(parentTreeComboBox);
		
		nextButton = new JButton(">");
		nextButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionNext();
			}
		});
		nextButton.setEnabled(false);
		panel.add(nextButton);

		menuBar = new JMenuBar();
		northPanel.add(menuBar, BorderLayout.WEST);

		mainMenu = new JMenu("");
		mainMenu.setIcon(new ImageIcon(FileSourceChooserDialog.class.getResource("/HamburgerMenu_20_black.png")));
		menuBar.add(mainMenu);

		JCheckBoxMenuItem chckbxmntmNewCheckItem = new JCheckBoxMenuItem("Show Hidden Files",showHiddenFiles);
		chckbxmntmNewCheckItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionShowHiddenChanged(chckbxmntmNewCheckItem.isSelected());
			}
		});
		mainMenu.add(chckbxmntmNewCheckItem);

		JCheckBoxMenuItem chckbxmntmNewCheckItem_1 = new JCheckBoxMenuItem("Show File Extentions",showExtention);
		chckbxmntmNewCheckItem_1.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionShowExtebtionsChanged(chckbxmntmNewCheckItem_1.isSelected());
			}
		});
		mainMenu.add(chckbxmntmNewCheckItem_1);

		saveAsFileNamePanel = new JPanel();
		northPanel.add(saveAsFileNamePanel, BorderLayout.NORTH);

		JLabel lblNewLabel_1 = new JLabel("Save As:  ");
		saveAsFileNamePanel.add(lblNewLabel_1);

		saveAsFileNameTextField = new JTextField();
		saveAsFileNameTextField.addKeyListener(new KeyAdapter() {
			@Override
			public void keyTyped(KeyEvent e) {
				SwingUtilities.invokeLater(()->{
					setOkEnabled();	
				});

			}
		});
		saveAsFileNamePanel.add(saveAsFileNameTextField);
		saveAsFileNameTextField.setColumns(20);

	
		
		if( factories.length>1) {

			JMenuItem item = new JMenuItem("Open Remote Browser");
			item.addActionListener((e)->actionOpenFactory());
			mainMenu.add(item);

		}
		
		parentTreeComboBox.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				int idx = parentTreeComboBox.getSelectedIndex();
				FileSource dir = parentTreeList.get(idx);
				setCurrentDirectory(dir);				
			}
		});

		//-----------------------

		JTree tmptree = new JTree();

		DefaultTreeCellRenderer renderer = (DefaultTreeCellRenderer) tmptree.getCellRenderer();
		folderIcon = renderer.getClosedIcon();
		fileIcon = renderer.getDefaultLeafIcon();


		JPanel panel3 = new JPanel();
		centerPanel.add(panel3, BorderLayout.SOUTH);
		JLabel lblNewLabel = new JLabel("File Format");
		panel3.add(lblNewLabel);
		filterComboBox = new JComboBox<String>();
		filterComboBox.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				int idx = filterComboBox.getSelectedIndex();
				FileSourceFilter tmp = null;
				if( idx > 0 ) {
					tmp = filters.get(idx-1);
				}
				setFileFilter(tmp);
			}
		});

		filterComboBox.setModel(new DefaultComboBoxModel<String>(new String[] {"All Files"}));
		panel3.add(filterComboBox);


		table = new JTable();
		try {
			table.setModel(new FileSourceTableModel(currentDirectory));
			table.addKeyListener(new KeyAdapter() {
				@Override
				public void keyReleased(KeyEvent e) {
					int code = e.getKeyCode();
					if( code == KeyEvent.VK_DELETE || (code == KeyEvent.VK_D && (isMac?e.isMetaDown():e.isControlDown()))) {
						FileSource[] files = getAllSelected();
						if( files != null && files.length>0) {
							String msg = "Are you sure you want to delete "+files.length+" files?";
							if(JOptionPane.showConfirmDialog(table, msg) == JOptionPane.OK_OPTION) {
								int cnt = 0;
								try {
									for(FileSource file : files) {
										deletFile(file);
										cnt++;
									}
								} catch (IOException e2) {
									showError(null, e2);
								}
								if( cnt > 0 ) {
									rescanCurrentDirectory();
								}
							}
						}
					}
				}
			});
			table.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseReleased(MouseEvent e) {
					if( e.getButton() == 1 && e.getClickCount()>=2) {
						int row = table.rowAtPoint(e.getPoint());

						FileSourceTableModel tm = (FileSourceTableModel) table.getModel();

						if( row >=0 && row < tm.taccepted.size()) {
							FileSourceWrapper w = tm.taccepted.get(row);
							try {
								if(w.file.isDirectory() ) {
									setCurrentDirectory(w.file);
								} else {
									actionAccept();
								}
							} catch (IOException e1) {
								showError("", e1);
							}
						}
					}
				}
			});


			dragSource  = new DragSource();
			dragSource.createDefaultDragGestureRecognizer(table, DnDConstants.ACTION_COPY, this);
			dropTargetListener = new FileSourceDropTargetListener(table);
		} catch (IOException e1) {
			showError("", e1);

		}
		table.setAutoCreateRowSorter(true);
		table.setDragEnabled(isDragEnabled());
		table.setSelectionMode(multiSelectionEnabled?ListSelectionModel.MULTIPLE_INTERVAL_SELECTION: ListSelectionModel.SINGLE_SELECTION);
		table.setFillsViewportHeight(true);
		final KeyStroke copy = KeyStroke.getKeyStroke(KeyEvent.VK_C, isMac?InputEvent.META_DOWN_MASK:InputEvent.CTRL_DOWN_MASK, false);
		final KeyStroke paste = KeyStroke.getKeyStroke(KeyEvent.VK_V, isMac?InputEvent.META_DOWN_MASK:InputEvent.CTRL_DOWN_MASK, false);


		table.registerKeyboardAction((e)->{actionCopy();}, "Copy", copy, JComponent.WHEN_FOCUSED);
		table.registerKeyboardAction((e)->{actionPaste();}, "Paste", paste, JComponent.WHEN_FOCUSED);

		ListSelectionModel selectionModel = table.getSelectionModel();

		selectionModel.addListSelectionListener(new ListSelectionListener() {
			public void valueChanged(ListSelectionEvent e) {
				if( !e.getValueIsAdjusting()) {
					ListSelectionModel lsm = (ListSelectionModel)e.getSource();
					int fst = lsm.getMinSelectionIndex();
					int last = lsm.getMaxSelectionIndex();
					if( fst >=0 ) {
						FileSourceTableModel tm = (FileSourceTableModel) table.getModel();
						for(int idx=fst; idx <= last; idx++ ) {
							FileSourceWrapper w = tm.taccepted.get(idx);
							try {
								if(!w.accepted || 
										fileSelectionMode == DIRECTORIES_ONLY && w.file.isFile() || 
										fileSelectionMode == FILES_ONLY && w.file.isDirectory()) {
									selectionModel.removeIndexInterval(idx, idx);
								} else {
									if(w.file.isFile()) {
										saveAsFileNameTextField.setText(w.file.getName());
									}
								}
							} catch (IOException e1) {
								showError("", e1);

							}

							setOkEnabled();


						}
					}
				}
			}
		});


		table.setDefaultRenderer(String.class,new FileSourceTableCellRenderer(null));
		table.setDefaultRenderer(Long.class,new FileSourceTableCellRenderer(null));
		table.setDefaultRenderer(Date.class,new FileSourceTableCellRenderer(null));
		TableCellRenderer r = table.getDefaultRenderer(Icon.class);

		table.setDefaultRenderer(Icon.class,new FileSourceTableCellRenderer(r));

		JScrollPane tableScrollPane = new JScrollPane();
		tableScrollPane.setViewportView(table);
		centerPanel.add(tableScrollPane, BorderLayout.CENTER);


		controlPane = new JPanel();
		getContentPane().add(controlPane, BorderLayout.SOUTH);
		controlPane.setLayout(new BorderLayout(0, 0));
		JPanel panel4 = new JPanel();
		controlPane.add(panel4, BorderLayout.WEST);
		newFolderButton = new JButton("New Folder");
		newFolderButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionNewFolder();
			}
		});
		newFolderButton.setVisible(false);
		panel4.add(newFolderButton);
		JPanel panel5 = new JPanel();
		controlPane.add(panel5, BorderLayout.EAST);
		cancelButton = new JButton("Cancel");
		cancelButton.setMnemonic(KeyEvent.VK_C);
		cancelButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionCancel();
			}

		});
		panel5.add(cancelButton);

		okButton = new JButton("OK");
		okButton.setMnemonic(KeyEvent.VK_S);
		setOkEnabled();
		okButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionAccept();
			}
		});

		panel5.add(okButton);



		getRootPane().setDefaultButton(okButton);



	}




	protected void actionNext() {
		if(!nextStack.isEmpty()) {
			FileSource dir = nextStack.pop();
			setCurrentDirectory(dir);
		}
		
	}


	protected void actionPrevious() {
		int idx = parentTreeList.size();
		if( idx > 1 ) {
			FileSource dir = parentTreeList.get(1);
			setCurrentDirectory(dir);
			while(nextStack.size()>20) {
				nextStack.remove(19);
			}
		}
	}


	protected void deletFile(FileSource file) throws IOException {
		if( file.isDirectory()) {
			FileSource[] kids = file.listFiles();
			if( kids != null ) {
				for(FileSource kid : kids) {
					deletFile(kid);
				}
			}
		}
		
		if(! file.delete()) {
			throw new IOException("Can't delete "+file);
		}
		
	}


	@SuppressWarnings("unchecked")
	private void actionPaste() {
		
		Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
		Transferable tr = clipboard.getContents(this);//.getTransferData(DataFlavor.stringFlavor));
		if( tr != null) {

			try {

				List<FileSource> newFiles = null;

				if (tr.isDataFlavorSupported(FileSourceTransferable.fileSourceFlavor)) {

					Object obj = tr.getTransferData(FileSourceTransferable.fileSourceFlavor);
					if (obj != null && obj instanceof List) {
						newFiles = (List<FileSource>) obj;						
					}					
				} else if( tr.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
					FileSourceFactory factory = currentDirectory.getFileSourceFactory();
					Object obj =  tr.getTransferData(DataFlavor.javaFileListFlavor);

					if (obj != null && obj instanceof List) {
						newFiles = new ArrayList<FileSource>();
						List<File> list = (List<File>) obj;
						for(File f : list) {							
							newFiles.add(factory.createFileSource(f.getCanonicalPath()));
						}
					}

					if( newFiles !=null && newFiles.size()>0 ) {
						// move into the currentDirectory if it's not already there
						for(FileSource file : newFiles) {
							copyToCurrentDir(currentDirectory, file);
						}
						rescanCurrentDirectory();
						return;
					}

				}
			} catch (Exception e) {
			
			}
		}



	}


	private void actionCopy() {

		FileSource[] files = getAllSelected();
		if( files !=null && files.length>0) {
			Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();			
			clipboard.setContents(new FileSourceTransferable(Arrays.asList(files)), null);
		}

	}


	private void setOkEnabled() {
		if(dialogType==OPEN_DIALOG) {
			okButton.setEnabled(currentDirectory!= null && table.getSelectedRowCount()>0);
		} else {
			okButton.setEnabled(currentDirectory!= null && saveAsFileNameTextField.getText().length()>0);
		}
		nextButton.setEnabled(!nextStack.isEmpty());
	}


	protected void actionOpenFactory() {
		FactoryPropertiesDialog dialog = new FactoryPropertiesDialog();
		dialog.showDialog();
		FileSourceFactory nf = dialog.getFactory();
		if( nf != null && nf.isConnected()) {
			try {
				FileSource[] roots = nf.listRoots();
				
				if( roots.length>0) {
					if( dialogType == BROWSER_DIALOG) {
						FileSourceChooserDialog d = new FileSourceChooserDialog();
						d.setCurrentDirectory(roots[0]);
						d.openDialogAsBrowser();
					} else {
						setCurrentDirectory(roots[0]);
					}
				}
			} catch (IOException e) {
				showError("", e);
			}
		}


	}


	protected void actionShowExtebtionsChanged(boolean b) {
		if( b != showExtention) {
			showExtention = b;
			rescanCurrentDirectory();
			Preferences p = Preferences.userNodeForPackage(FileSourceChooserDialog.class);
			p.put(SHOW_EXTENTIONS_PREFERENCE, ""+showExtention);
			try {
				p.flush();
			} catch (BackingStoreException e) {
				showError("Can't save preferece", e);
			}
		}


	}


	protected void actionShowHiddenChanged(boolean b) {
		if( b != showHiddenFiles) {
			showHiddenFiles = b;
			rescanCurrentDirectory();
			Preferences p = Preferences.userNodeForPackage(FileSourceChooserDialog.class);
			p.put(SHOW_HIDDEN_FILES_PREFERENCE, ""+showHiddenFiles);
			try {
				p.flush();
			} catch (BackingStoreException e) {
				showError("Can't save preferece", e);
			}
		}

	}


	protected void actionNewFolder() {
		try {
			FileSource file = currentDirectory.getChild("New Folder");
			if( !file.mkdir()) {
				showError("", new IOException("Can't create directory "+file.getAbsolutePath()));
			} else {
				rescanCurrentDirectory();
			}
		} catch (IOException e) {
			showError("", e);

		}

	}

	public String getApproveButtonText() {
		return approveButtonText;
	}

	public void setApproveButtonText(String approveButtonText) {
		if(this.approveButtonText == approveButtonText) {
			return;
		}
		String oldValue = this.approveButtonText;
		this.approveButtonText = approveButtonText;
		firePropertyChange(APPROVE_BUTTON_TEXT_CHANGED_PROPERTY, oldValue, approveButtonText);
	}

	public String getApproveButtonToolTipText() {
		return approveButtonToolTipText;
	}

	public void setApproveButtonToolTipText(String toolTipText) {
		if(approveButtonToolTipText.equals(toolTipText)) {
			return;
		}
		String oldValue = approveButtonToolTipText;
		approveButtonToolTipText = toolTipText;
		firePropertyChange(APPROVE_BUTTON_TOOL_TIP_TEXT_CHANGED_PROPERTY, oldValue, approveButtonToolTipText);
	}


	public int getApproveButtonMnemonic() {
		return okButton.getMnemonic();
	}

	/**
	 * 
	 * @param cls the class who's package is used for storing preferences
	 * @param inMainMenu if yes, the recentFilesMenu is added to the main menu, otherwise a separate menu is created in the menubar
	 * @throws IOException	 
	 */
	public void setSupportRecentFiles(Class<?> cls,boolean inMainMenu) throws IOException {
		if( recentFileMenu != null ) {
			menuBar.remove(recentFileMenu);
			mainMenu.remove(recentFileMenu);
		}
		recentFileMenu = new RecentFileMenu(cls);
		if( inMainMenu) {
			mainMenu.add(recentFileMenu);
		} else {
			menuBar.add(recentFileMenu);
		}

		recentFileMenu.addActionListener((e)->{
			Object source =  e.getSource();
			if (source instanceof FileSource) {
				FileSource file = (FileSource) source;
				try {
					setSelectedFile(file);
				} catch (Exception e2) {
					JOptionPane.showMessageDialog(FileSourceChooserDialog.this, e2, "Could not get file path", JOptionPane.ERROR_MESSAGE);
				}	
			} else {
				JOptionPane.showMessageDialog(FileSourceChooserDialog.this, "Did not get a FileSOurce as source of event.", source.getClass().getName(), JOptionPane.ERROR_MESSAGE);
			}
		});

	}

	public void setApproveButtonMnemonic(int mnemonic) {
		int oldValue = okButton.getMnemonic();
		if(oldValue == mnemonic) {
			return;
		}
		okButton.setMnemonic(mnemonic);
		firePropertyChange(APPROVE_BUTTON_MNEMONIC_CHANGED_PROPERTY, oldValue, mnemonic);
	}

	public int getCancelButtonMnemonic() {
		return cancelButton.getMnemonic();
	}

	public void setCancelButtonMnemonic(int mnemonic) {
		int oldValue = cancelButton.getMnemonic();
		if(oldValue == mnemonic) {
			return;
		}
		cancelButton.setMnemonic(mnemonic);
		firePropertyChange(CANCEL_BUTTON_MNEMONIC_CHANGED_PROPERTY, oldValue, mnemonic);
	}

	public boolean isShowHiddenFiles() {
		return showHiddenFiles;
	}

	public void setShowHiddenFiles(boolean useFileHiding) {
		if( showHiddenFiles != useFileHiding) {			
			showHiddenFiles = useFileHiding;
			rescanCurrentDirectory();			
		}
	}

	public int getFileSelectionMode() {
		return fileSelectionMode;
	}

	public void setFileSelectionMode(int fileSelectionMode) {
		this.fileSelectionMode = fileSelectionMode;
	}

	public boolean isMultiSelectionEnabled() {
		return multiSelectionEnabled;
	}

	public void setMultiSelectionEnabled(boolean multiSelectionEnabled) {
		if( multiSelectionEnabled != this.multiSelectionEnabled) {
			boolean old = this.multiSelectionEnabled;
			this.multiSelectionEnabled = multiSelectionEnabled;
			table.setSelectionMode(multiSelectionEnabled?ListSelectionModel.MULTIPLE_INTERVAL_SELECTION: ListSelectionModel.SINGLE_SELECTION);
			firePropertyChange(MULTI_SELECTION_ENABLED_CHANGED_PROPERTY, old, multiSelectionEnabled);
		}

	}

	public boolean isUseAcceptAllFileFilter() {
		return useAcceptAllFileFilter;
	}

	public void setUseAcceptAllFileFilter(boolean useAcceptAllFileFilter) {
		this.useAcceptAllFileFilter = useAcceptAllFileFilter;
	}

	public boolean isDragEnabled() {
		return dragEnabled;
	}

	public void setDragEnabled(boolean dragEnabled) {
		this.dragEnabled = dragEnabled;
	}

	public FileSource getCurrentDirectory() {
		return currentDirectory;
	}

	public void setCurrentDirectory(FileSource dir) {
		try {
			if( dir.isFile()) {
				dir = dir.getParentFile();
			}
		} catch (IOException e) {
			showError("", e);
		}
		String one = ""+currentDirectory;
		String two = ""+dir;

		if( !one.equals(two)) {
			FileSource old = currentDirectory;
			if( old != null ) {
				nextStack.push(old);
			}
			currentDirectory = dir;
			rescanCurrentDirectory();
			firePropertyChange(DIRECTORY_CHANGED_PROPERTY, old, currentDirectory);

		}
	}

	//--------------------------------------
	// *****************************
	// ****** File Operations ******
	// *****************************

	/**
	 * Returns the selected file. This can be set either by the
	 * programmer via <code>setSelectedFile</code> or by a user action, such as
	 * either typing the filename into the UI or selecting the
	 * file from a list in the UI.
	 *
	 * @see #setSelectedFile
	 * @return the selected file
	 */
	public FileSource getSelectedFile() {
		FileSource ret = null;
		FileSource dir = getCurrentDirectory();

		if(dialogType == SAVE_DIALOG) {
			try {
				ret =  dir.getChild(saveAsFileNameTextField.getText());
			} catch (IOException e) {
				showError("", e);
			}
		} else {

			int row = table.getSelectedRow();
			if( row >=0 ) {
				FileSourceTableModel m = (FileSourceTableModel) table.getModel();
				if( row < m.taccepted.size()) {
					ret = m.taccepted.get(row).file;
				}
			}
		}
		if( recentFileMenu != null && ret != null) {
			try {
				recentFileMenu.addRecent(ret);
			} catch (IOException e) {
				showError("", e);
			}
		}


		return ret;
	}

	/**
	 * Sets the selected file. If the file's parent directory is
	 * not the current directory, changes the current directory
	 * to be the file's parent directory.
	 *
	 * @see #getSelectedFile
	 *
	 * @param file the selected file
	 * @throws IOException 
	 */
	@BeanProperty(preferred = true)
	public void setSelectedFile(final FileSource file) throws IOException {
		FileSource oldValue = getSelectedFile();


		if(file != null) {


			FileSource p = file.getParentFile();

			if(currentDirectory == null ) {
				setCurrentDirectory(p);
			} else if (!p.getCanonicalPath().equals(currentDirectory.getCanonicalPath())) {
				setCurrentDirectory(p);                
			}

			if( !file.isDirectory()) {				
				saveAsFileNameTextField.setText(file.getName());
				setOkEnabled();
			}

			FileSourceTableModel m = (FileSourceTableModel) table.getModel();
			final int row = m.getRow(file);
			firePropertyChange(SELECTED_FILE_CHANGED_PROPERTY, oldValue, file);

			new Thread(new Runnable() {

				@Override
				public void run() {

					try {
						Thread.sleep(10);
					} catch (InterruptedException e) {
					}
					if( row >=0 ) {
						SwingUtilities.invokeLater(new Runnable() {

							@Override
							public void run() {
								table.getSelectionModel().setSelectionInterval(row, row);
								table.scrollRectToVisible(table.getCellRect(row, 0, true));

							}
						});    	        		
					}    	        					
				}
			}).start();	

		}

	}



	/**
	 * Returns a list of selected files if the file chooser is
	 * set to allow multiple selection.
	 *
	 * @return an array of selected {@code File}s
	 */
	public FileSource[] getSelectedFiles() {
		if( dialogType==SAVE_DIALOG) {
			FileSource[] ret = new FileSource[1];
			ret[0] = getSelectedFile();
			return ret;
		}

		int [] rows = table.getSelectedRows();

		FileSource ret [] = new FileSource[rows.length];
		if( rows.length>0) {
			FileSourceTableModel m = (FileSourceTableModel) table.getModel();

			for(int idx=0; idx < ret.length; idx++ ) {
				ret[idx] = m.taccepted.get(rows[idx]).file;
			}
		}

		return ret;
	}

	/**
	 * Sets the list of selected files if the file chooser is
	 * set to allow multiple selection.
	 *
	 * @param selectedFiles an array {@code File}s to be selected
	 * @throws IOException 
	 */
	@BeanProperty(description
			= "The list of selected files if the chooser is in multiple selection mode.")
	public void setSelectedFiles(FileSource[] files) throws IOException {

		FileSource [] oldValue = getSelectedFiles();
		if (files == null || files.length == 0) {           
			setSelectedFile(null);
		} else {
			//  this will set currentDir if needed

			setSelectedFile(files[0]);
			new Thread(new Runnable() {

				@Override
				public void run() {
					while(table.getSelectedRowCount()==0) {
						try {
							Thread.sleep(4);
						} catch (InterruptedException e) {

						}
					}
					SwingUtilities.invokeLater(new Runnable() {

						@Override
						public void run() {
							FileSourceTableModel m = (FileSourceTableModel) table.getModel();
							for (int i = 1; i < files.length; i++) {

								try {
									int row = m.getRow(files[i]);
									if( row >=0) {
										table.getSelectionModel().addSelectionInterval(i, i);
									} else {
										System.out.println("Not in model "+files[i]);
									}
								} catch (IOException e) {
									e.printStackTrace();
								}				            	
							}				            
						}
					});					
				}
			}).start();

		}
		firePropertyChange(SELECTED_FILES_CHANGED_PROPERTY, oldValue, files);
	}


	//-------------------------------------
	protected void actionAccept() {
		if( dialogType != BROWSER_DIALOG) {
			canceled = false;

			fireActionPerformed(APPROVE_SELECTION);
			dispose();
		}
	}

	@SuppressWarnings("deprecation")
	private void fireActionPerformed(String command) {
		if( actionListners.size()>0) {
			long mostRecentEventTime = EventQueue.getMostRecentEventTime();
			int modifiers = 0;
			AWTEvent currentEvent = EventQueue.getCurrentEvent();
			if (currentEvent instanceof InputEvent) {
				modifiers = ((InputEvent)currentEvent).getModifiers();
			} else if (currentEvent instanceof ActionEvent) {
				modifiers = ((ActionEvent)currentEvent).getModifiers();
			}
			ActionEvent e = new ActionEvent(this, ActionEvent.ACTION_PERFORMED,command, mostRecentEventTime,modifiers);
			// Process the listeners last to first, notifying
			// those that are interested in this event
			for (int i = actionListners.size()-1; i>=0; i--) {
				actionListners.get(i).actionPerformed(e);            
			}
		}
	}

	protected void actionCancel() {
		if( dialogType != BROWSER_DIALOG) {
			canceled = true;
			fireActionPerformed(CANCEL_SELECTION);
			dispose();
		}
	}

	/**
	 * 
	 * @param parent is here for comparability with JFileChooser but is not used.  
	 * @param approveButtonText
	 * @return
	 */
	public int showDialog(Component parent, String approveButtonText) {

		int ret = ERROR_OPTION;
		try {
			
			if( approveButtonText != null) {
				okButton.setText(approveButtonText);
				setDialogType(CUSTOM_DIALOG);
			} else {
				okButton.setText(dialogType==OPEN_DIALOG?"Open":"Save");
			}
			rescanCurrentDirectory();
			if(dialogType == BROWSER_DIALOG) {
				setControlButtonsAreShown(false);
				setDragEnabled(true);
			} else {			
				setModal(true);
			}
			setVisible(true);
			if( canceled) {
				ret = CANCEL_OPTION;
			} else {
				ret = APPROVE_OPTION;
			}
		} catch (Exception e) {
			ret = ERROR_OPTION;
		}
		return ret;
	}

	public int openDialogAsBrowser() throws HeadlessException {
		setDialogType(BROWSER_DIALOG);
		setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

		return showDialog(null, null);
	}
	
	public int showOpenDialog(Component parent) throws HeadlessException {
		setDialogType(OPEN_DIALOG);
		
		return showDialog(parent, null);
	}

	public int showSaveDialog(Component parent) throws HeadlessException {
		setDialogType(SAVE_DIALOG);
		return showDialog(parent, null);
	}

	private void setDialogType(int type) {
		if(this.dialogType == type) {
			return;
		}
		int oldValue = dialogType;
		dialogType = type;
		newFolderButton.setVisible(false);
		saveAsFileNamePanel.setVisible(false);
		switch (dialogType) {
		case BROWSER_DIALOG:
			
				JMenuItem item = new JMenuItem("Open Local Browser");
				item.addActionListener((e)->{
					FileSourceChooserDialog d = new FileSourceChooserDialog();
					d.openDialogAsBrowser();
				});
				mainMenu.add(item);	
				setTitle("");
			break;
		case OPEN_DIALOG:setTitle("Open");break;
		case SAVE_DIALOG:setTitle("Save");
		newFolderButton.setVisible(true);
		saveAsFileNamePanel.setVisible(true);
		break;
		case CUSTOM_DIALOG:setTitle("Custom");break;
		default:
			break;
		}
		firePropertyChange(DIALOG_TYPE_CHANGED_PROPERTY, oldValue, dialogType);
	}

	private void rescanCurrentDirectory()  {
		try {
			if( currentDirectory == null ) {
				FileSourceFactory factory = FileSourceFactory.getDefaultFactory();

				currentDirectory=factory.getCurrentDirectory();
				if( currentDirectory == null ) {
					currentDirectory = factory.listRoots()[0];
				}
			}
			parentTreeList.clear();
			parentTreeList.add(currentDirectory);
			FileSource p = currentDirectory.getParentFile();

			while(p != null ) {
				parentTreeList.add(p);
				p = p.getParentFile();
			}

			String [] tmp = new String[parentTreeList.size()];

			for(int idx=0,sz=parentTreeList.size(); idx < sz; idx++) {
				tmp[idx] = parentTreeList.get(idx).getName();
			}
			parentTreeComboBox.setModel(new DefaultComboBoxModel<String>(tmp));


			setOkEnabled();

			table.setModel(new FileSourceTableModel(currentDirectory));
			table.getColumnModel().getColumn(0).setMaxWidth(20);

			final DefaultCellEditor defaultEditor = (DefaultCellEditor) table.getDefaultEditor(String.class);
			defaultEditor.setClickCountToStart(1);			
			table.clearSelection();
		} catch (Throwable e) {
			showError("",e);
		}
	}

	private void showError(String title, Throwable e) {

		if( !(title == null && e == null )) {
			if( e != null ) {
				e.printStackTrace();
			}
			JOptionPane.showMessageDialog(this, e==null?"":e.toString(), title==null?"":title, APPROVE_OPTION);
		}
	}

	private boolean accept(FileSource f) {

		boolean	ret = f == null || fileFilter == null || fileFilter.accept(f);

		try {

			if( ret ) {
				switch (fileSelectionMode) {
				case FILES_ONLY: ret = f.isFile();					
				break;
				case DIRECTORIES_ONLY: ret = f.isDirectory();
				default:
					break;
				}					
			}

		}catch (Exception e) {
			// Ignore here
		}
		return ret;
	}

	public void addActionListner(ActionListener listner) {
		actionListners.add(listner);
	}

	public void removeActionListner(ActionListener l) {
		actionListners.remove(l);
	}

	public List<ActionListener> getActionListners() {
		List<ActionListener> ret = new ArrayList<>();
		for(ActionListener al : actionListners) {
			ret.add(al);
		}
		return ret;
	}

	/**
	 * Returns the accessory component.
	 *
	 * @return this JFileChooser's accessory component, or null
	 * @see #setAccessory
	 */
	public JComponent getAccessory() {
		return accessory;
	}

	/**
	 * Sets the accessory component. An accessory is often used to show a
	 * preview image of the selected file; however, it can be used for anything
	 * that the programmer wishes, such as extra custom file chooser controls.
	 *
	 * <p>
	 * Note: if there was a previous accessory, you should unregister
	 * any listeners that the accessory might have registered with the
	 * file chooser.
	 *
	 * @param newAccessory the accessory component to be set
	 */
	@BeanProperty(preferred = true, description
			= "Sets the accessory component on the JFileChooser.")
	public void setAccessory(JComponent newAccessory) {
		JComponent oldValue = accessory;
		accessory = newAccessory;
		firePropertyChange(ACCESSORY_CHANGED_PROPERTY, oldValue, accessory);
	}

	/**
	 * Returns the value of the <code>controlButtonsAreShown</code>
	 * property.
	 *
	 * @return   the value of the <code>controlButtonsAreShown</code>
	 *     property
	 *
	 * @see #setControlButtonsAreShown
	 * @since 1.3
	 */
	public boolean getControlButtonsAreShown() {
		return controlsShown;
	}


	/**
	 * Sets the property
	 * that indicates whether the <i>approve</i> and <i>cancel</i>
	 * buttons are shown in the file chooser.  This property
	 * is <code>true</code> by default.  Look and feels
	 * that always show these buttons will ignore the value
	 * of this property.
	 * This method fires a property-changed event,
	 * using the string value of
	 * <code>CONTROL_BUTTONS_ARE_SHOWN_CHANGED_PROPERTY</code>
	 * as the name of the property.
	 *
	 * @param b <code>false</code> if control buttons should not be
	 *    shown; otherwise, <code>true</code>
	 *
	 * @see #getControlButtonsAreShown
	 * @see #CONTROL_BUTTONS_ARE_SHOWN_CHANGED_PROPERTY

	 */
	public void setControlButtonsAreShown(boolean b) {
		if(controlsShown == b) {
			return;
		}
		boolean oldValue = controlsShown;
		controlsShown = b;
		controlPane.setVisible(controlsShown);

		firePropertyChange(CONTROL_BUTTONS_ARE_SHOWN_CHANGED_PROPERTY, oldValue, controlsShown);

	}

	/**
	 * Adds a filter to the list of user choosable file filters.
	 * For information on setting the file selection mode, see
	 * {@link #setFileSelectionMode setFileSelectionMode}.
	 *
	 * @param filter the <code>FileFilter</code> to add to the choosable file
	 *               filter list
	 *
	 * @see #getChoosableFileFilters
	 * @see #removeChoosableFileFilter
	 * @see #resetChoosableFileFilters
	 * @see #setFileSelectionMode
	 */
	@BeanProperty(preferred = true, description
			= "Adds a filter to the list of user choosable file filters.")
	public void addChoosableFileFilter(FileSourceFilter filter) {
		if(filter != null && !filters.contains(filter)) {
			FileSourceFilter[] oldValue = getChoosableFileFilters();
			filters.add(filter);
			firePropertyChange(CHOOSABLE_FILE_FILTER_CHANGED_PROPERTY, oldValue, getChoosableFileFilters());
			if (!useAcceptAllFileFilter && fileFilter == null && filters.size() == 1) {
				setFileFilter(filter);
			}
			String [] names =null;
			if( useAcceptAllFileFilter) {
				names = new String[filters.size()+1] ;
				names[0] = "All Files";
				for(int idx = 0,sz=filters.size(); idx < sz; idx++) {
					names[idx+1] = filters.get(idx).getDescription();
				}

			} else {
				names = new String[filters.size()] ;
				names[0] = "All Files";
				for(int idx = 0,sz=filters.size(); idx < sz; idx++) {
					names[idx] = filters.get(idx).getDescription();
				}

			}
			filterComboBox.setModel(new DefaultComboBoxModel<String>(names));
		}
	}

	/**
	 * Sets the current file filter. The file filter is used by the
	 * file chooser to filter out files from the user's view.
	 *
	 * @param filter the new current file filter to use
	 * @see #getFileFilter
	 */
	@BeanProperty(preferred = true, description
			= "Sets the File Filter used to filter out files of type.")
	public void setFileFilter(FileSourceFilter filter) {
		FileSourceFilter oldValue = fileFilter;
		fileFilter = filter;
		rescanCurrentDirectory();
		firePropertyChange(FILE_FILTER_CHANGED_PROPERTY, oldValue, fileFilter);
	}

	/**
	 * Gets the list of user choosable file filters.
	 *
	 * @return a <code>FileFilter</code> array containing all the choosable
	 *         file filters
	 *
	 * @see #addChoosableFileFilter
	 * @see #removeChoosableFileFilter
	 * @see #resetChoosableFileFilters
	 */
	@BeanProperty(bound = false)
	public FileSourceFilter[] getChoosableFileFilters() {
		FileSourceFilter[] ret = new FileSourceFilter[filters.size()];
		for(int idx=0,sz=filters.size(); idx < sz; idx++ )  {
			ret[idx] = filters.get(idx);
		}
		return ret;
	}


	@Override
	public void dragGestureRecognized(DragGestureEvent event) {
		if( dragEnabled) {
			//getSelectedFiles has logic than prevents me using it here.

			FileSource files [] = getAllSelected();

			if( files !=null && files.length>0) {
				Cursor cursor1 = Cursor.getDefaultCursor();
				if (event.getDragAction() == DnDConstants.ACTION_COPY) {
					cursor1 = DragSource.DefaultCopyDrop;
				}
				List<FileSource> list = new ArrayList<FileSource>();
				for(FileSource f : files) {
					list.add(f);
				}
				event.startDrag(cursor1, new FileSourceTransferable(list));				
			}
		}
	}


	private FileSource[] getAllSelected() {
		int [] rows = table.getSelectedRows();

		FileSource files [] = new FileSource[rows.length];
		if( rows.length>0) {
			FileSourceTableModel m = (FileSourceTableModel) table.getModel();

			for(int idx=0; idx < files.length; idx++ ) {
				files[idx] = m.taccepted.get(rows[idx]).file;
			}
		}

		return files;
	}


	@Override
	public void lostOwnership(Clipboard clipboard, Transferable contents) {

	}
}
