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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

import javax.swing.Box;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSpinner.DateEditor;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerDateModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.DateFormatter;

public class FileSourceExamineDialog extends JDialog {

	private static final long serialVersionUID = 1L;

	private enum ViewAction{View,All,Head,Tail};
	private enum Multiple {B,KB,MB,GB};


	private final JPanel contentPanel = new JPanel();
	private JTextField nameTextField;
	private JTextField userTextField;
	private JTextField groupTextField;
	private JLabel sizeLabel;
	private JCheckBox ownerReadableCheckBox;
	private JCheckBox groupReadableCheckBox;
	private JCheckBox groupWritableCheckBox;
	private JCheckBox otherReadableCheckBox;
	private JCheckBox otherWritableCheckBox;
	private JCheckBox ownerWritableCheckBox;
	private JLabel mimneTypeLabel;
	private JButton saveButton;
	private boolean cancel;
	private JCheckBox ownerExecuteCheckBox;
	private JCheckBox otherExecuteCheckBox;
	private JPanel userPermissionPanel_1;
	private JCheckBox groupExecuteCheckBox;
	private FileSource file;
	private JComboBox<ViewAction> viewComboBox;
	private Font normalFont;
	private SpinnerDateModel lastModifiedDateSpinnerModel;
	private JSpinner lastModifiedDateSpinner;
	private DateFormatter lastModifiedDateFormatter;
	private DateEditor lastModifiedDateEditorPanel;


	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		try {
			FileSourceExamineDialog dialog = new FileSourceExamineDialog();
			dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
			//FileSource file = FileSourceFactory.getDefaultFactory().createFileSource("/Volumes/Data/eclipse-workspace-jmail/BjlFileSystemTest/TestFiles/Hotel California.txt");

			FileSource file = FileSourceFactory.getDefaultFactory().createFileSource("/Volumes/Data/DownloadsTony/FileSigs/FTK_sigs_GCK.zip");
			dialog.showDialog(file);


		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Create the dialog.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public FileSourceExamineDialog() {
		setBounds(100, 100, 1443, 528);
		getContentPane().setLayout(new BorderLayout());
		contentPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
		getContentPane().add(contentPanel, BorderLayout.CENTER);
		contentPanel.setLayout(new BorderLayout(0, 0));
		{
			JPanel northPanel = new JPanel();
			contentPanel.add(northPanel, BorderLayout.NORTH);
			northPanel.setLayout(new BorderLayout(0, 0));
			{
				JPanel controPanel = new JPanel();
				northPanel.add(controPanel, BorderLayout.NORTH);
				{
					nameTextField = new JTextField();
					nameTextField.addCaretListener(new CaretListener() {
						public void caretUpdate(CaretEvent e) {
							actionSomthingShanged();
						}
					});
					nameTextField.addFocusListener(new FocusAdapter() {
						@Override
						public void focusLost(FocusEvent e) {
							actionSomthingShanged();
						}
					});
					nameTextField.addActionListener(new ActionListener() {
						public void actionPerformed(ActionEvent e) {
							actionSomthingShanged();
						}
					});
					nameTextField.setBorder(new TitledBorder(null, "Name:", TitledBorder.LEADING, TitledBorder.TOP, null, null));
					controPanel.add(nameTextField);
					nameTextField.setText("My Name");
					nameTextField.setColumns(20);
				}
				{
					userTextField = new JTextField();
					userTextField.addActionListener(new ActionListener() {
						public void actionPerformed(ActionEvent e) {
							actionSomthingShanged();
						}
					});
					userTextField.addFocusListener(new FocusAdapter() {
						@Override
						public void focusLost(FocusEvent e) {
							actionSomthingShanged();
						}
					});
					userTextField.setBorder(new TitledBorder(null, "User", TitledBorder.LEADING, TitledBorder.TOP, null, null));
					controPanel.add(userTextField);
					userTextField.setText("MyUser");
					userTextField.setEditable(false);
					userTextField.setColumns(10);
				}
				{
					groupTextField = new JTextField();
					groupTextField.setEditable(false);
					groupTextField.addFocusListener(new FocusAdapter() {
						@Override
						public void focusLost(FocusEvent e) {
							actionSomthingShanged();
						}
					});
					groupTextField.addActionListener(new ActionListener() {
						public void actionPerformed(ActionEvent e) {
							actionSomthingShanged();
						}
					});
					groupTextField.setText("MyGroup");
					groupTextField.setBorder(new TitledBorder(null, "Group", TitledBorder.LEADING, TitledBorder.TOP, null, null));
					controPanel.add(groupTextField);
					groupTextField.setColumns(10);
				}
				{
					createDateLabel = new JLabel("01/01/2024 09:09:09.111");
					createDateLabel.setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED, null, null), "Created", TitledBorder.LEADING, TitledBorder.TOP, null, new Color(0, 0, 0)));
					controPanel.add(createDateLabel);
				}
				{
					lastModifiedDateSpinnerModel = new SpinnerDateModel();
					lastModifiedDateSpinner = new JSpinner(lastModifiedDateSpinnerModel);
					lastModifiedDateSpinner.addChangeListener(new ChangeListener() {
						public void stateChanged(ChangeEvent e) {
							actionSomthingShanged();
						}
					});
					lastModifiedDateSpinner.setBorder(new TitledBorder(null, "Last Modified", TitledBorder.LEADING, TitledBorder.TOP, null, null));

					lastModifiedDateEditorPanel = new JSpinner.DateEditor(lastModifiedDateSpinner, "HH:mm:ss.SSS");
					lastModifiedDateFormatter = (DateFormatter)lastModifiedDateEditorPanel.getTextField().getFormatter();
					lastModifiedDateFormatter.setAllowsInvalid(false); // this makes what you want
					lastModifiedDateFormatter.setOverwriteMode(true);
					lastModifiedDateSpinner.setPreferredSize(new Dimension(200, 50));
					controPanel.add(lastModifiedDateSpinner);
				}
				{
					Component horizontalStrut = Box.createHorizontalStrut(20);
					controPanel.add(horizontalStrut);
				}
				{
					sizeLabel = new JLabel("1.5M");
					controPanel.add(sizeLabel);
				}
				{
					Component horizontalStrut = Box.createHorizontalStrut(20);
					controPanel.add(horizontalStrut);
				}
				{
					mimneTypeLabel = new JLabel("MIME");
					controPanel.add(mimneTypeLabel);
				}
				{
					isBinaryCheckBox = new JCheckBox("Is Binary");
					controPanel.add(isBinaryCheckBox);
				}
			}
			{
				JPanel controlPanel2 = new JPanel();
				controlPanel2.setBorder(new TitledBorder(null, "Permissions", TitledBorder.CENTER, TitledBorder.TOP, null, null));
				northPanel.add(controlPanel2, BorderLayout.SOUTH);
				{
					fileTypeLabel = new JLabel("File");
					controlPanel2.add(fileTypeLabel);
				}
				{
					JPanel userPermissionPanel = new JPanel();
					userPermissionPanel.setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED, null, null), "Owner", TitledBorder.LEADING, TitledBorder.TOP, null, new Color(0, 0, 0)));
					controlPanel2.add(userPermissionPanel);
					{
						ownerReadableCheckBox = new JCheckBox("Can Read");
						ownerReadableCheckBox.addActionListener(new ActionListener() {
							public void actionPerformed(ActionEvent e) {
								actionSomthingShanged();
							}
						});
						userPermissionPanel.add(ownerReadableCheckBox);
					}
					{
						ownerWritableCheckBox = new JCheckBox("Can Write");
						ownerWritableCheckBox.addActionListener(new ActionListener() {
							public void actionPerformed(ActionEvent e) {
								actionSomthingShanged();
							}
						});
						userPermissionPanel.add(ownerWritableCheckBox);
					}
					{
						ownerExecuteCheckBox = new JCheckBox("Can Execute");
						ownerExecuteCheckBox.addChangeListener(new ChangeListener() {
							public void stateChanged(ChangeEvent e) {
								actionSomthingShanged();
							}
						});
						ownerExecuteCheckBox.addActionListener(new ActionListener() {
							public void actionPerformed(ActionEvent e) {

							}
						});
						userPermissionPanel.add(ownerExecuteCheckBox);
					}
				}
				{
					userPermissionPanel_1 = new JPanel();
					userPermissionPanel_1.setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED, null, null), "Group", TitledBorder.LEADING, TitledBorder.TOP, null, new Color(0, 0, 0)));
					controlPanel2.add(userPermissionPanel_1);
					{
						groupReadableCheckBox = new JCheckBox("Can Read");
						groupReadableCheckBox.addActionListener(new ActionListener() {
							public void actionPerformed(ActionEvent e) {
								actionSomthingShanged();
							}
						});
						userPermissionPanel_1.add(groupReadableCheckBox);
					}
					{
						groupWritableCheckBox = new JCheckBox("Can Write");
						groupWritableCheckBox.addActionListener(new ActionListener() {
							public void actionPerformed(ActionEvent e) {
								actionSomthingShanged();
							}
						});
						userPermissionPanel_1.add(groupWritableCheckBox);
					}
					{
						groupExecuteCheckBox = new JCheckBox("Can Execute");
						groupExecuteCheckBox.addActionListener(new ActionListener() {
							public void actionPerformed(ActionEvent e) {
								actionSomthingShanged();
							}
						});
						userPermissionPanel_1.add(groupExecuteCheckBox);
					}
				}
				{
					JPanel userPermissionPanel3 = new JPanel();
					userPermissionPanel3.setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED, null, null), "Other", TitledBorder.LEADING, TitledBorder.TOP, null, new Color(0, 0, 0)));
					controlPanel2.add(userPermissionPanel3);
					{
						otherReadableCheckBox = new JCheckBox("Can Read");
						otherReadableCheckBox.addActionListener(new ActionListener() {
							public void actionPerformed(ActionEvent e) {
								actionSomthingShanged();
							}
						});
						userPermissionPanel3.add(otherReadableCheckBox);
					}
					{
						otherWritableCheckBox = new JCheckBox("Can Write");
						otherWritableCheckBox.addActionListener(new ActionListener() {
							public void actionPerformed(ActionEvent e) {
								actionSomthingShanged();
							}
						});
						userPermissionPanel3.add(otherWritableCheckBox);
					}
					{
						otherExecuteCheckBox = new JCheckBox("Can Execute");
						otherExecuteCheckBox.addActionListener(new ActionListener() {
							public void actionPerformed(ActionEvent e) {
								actionSomthingShanged();
							}
						});
						userPermissionPanel3.add(otherExecuteCheckBox);
					}
				}
			}
		}
		{
			JPanel southPanel = new JPanel();
			contentPanel.add(southPanel, BorderLayout.SOUTH);
		}
		{
			JPanel westPanel = new JPanel();
			contentPanel.add(westPanel, BorderLayout.WEST);
		}
		{
			JPanel eastPanel = new JPanel();
			contentPanel.add(eastPanel, BorderLayout.EAST);
		}
		{
			JScrollPane scrollPane = new JScrollPane();
			contentPanel.add(scrollPane, BorderLayout.CENTER);
			{
				textArea = new JTextArea();
				scrollPane.setViewportView(textArea);
				normalFont = textArea.getFont();
			}
		}
		{
			JPanel buttonPane = new JPanel();
			getContentPane().add(buttonPane, BorderLayout.SOUTH);
			buttonPane.setLayout(new BorderLayout(0, 0));
			{
				JPanel panel = new JPanel();
				buttonPane.add(panel, BorderLayout.EAST);
				{
					saveButton = new JButton("Save");
					saveButton.setEnabled(false);
					saveButton.addActionListener(new ActionListener() {
						public void actionPerformed(ActionEvent e) {
							actionSave();
						}
					});
					panel.add(saveButton);
					saveButton.setActionCommand("OK");
					getRootPane().setDefaultButton(saveButton);
				}
				{
					JButton cancelButton = new JButton("Cancel");
					cancelButton.addActionListener(new ActionListener() {
						public void actionPerformed(ActionEvent e) {
							actionCancel();
						}
					});
					panel.add(cancelButton);
				}

			}
			{
				JPanel panel = new JPanel();
				buttonPane.add(panel, BorderLayout.WEST);
				{
					viewComboBox = new JComboBox<ViewAction>();
					viewComboBox.setModel(new DefaultComboBoxModel<ViewAction>(ViewAction.values()));
					viewComboBox.addActionListener(new ActionListener() {
						public void actionPerformed(ActionEvent e) {
							actionView();
						}
					});

					panel.add(viewComboBox);
				}
				{
					viewSizePanel = new JPanel();
					viewSizePanel.setVisible(false);
					panel.add(viewSizePanel);
					{
						viewSizeTextField = new JTextField();
						viewSizeTextField.setHorizontalAlignment(SwingConstants.RIGHT);
						viewSizeTextField.setText("200");
						viewSizePanel.add(viewSizeTextField);
						viewSizeTextField.setColumns(4);
					}
					{
						viewMultipleComboBox = new JComboBox<Multiple>();
						viewMultipleComboBox.setModel(new DefaultComboBoxModel(Multiple.values()));
						viewSizePanel.add(viewMultipleComboBox);
					}
					{
						JButton viewRefreashButton = new JButton("Refresh");
						viewRefreashButton.addActionListener(new ActionListener() {
							public void actionPerformed(ActionEvent e) {
								actionView();
							}
						});
						viewSizePanel.add(viewRefreashButton);
					}
				}
			}
		}
	}

	protected void actionSomthingShanged() {
		boolean changed=false;
		try {
			long lastMod = lastModifiedDateSpinnerModel.getDate().getTime();
			long lastMod1 = file.lastModified();
			boolean b1 = lastMod == lastMod1;
			
			changed = ownerReadableCheckBox.isSelected() == file.canOwnerRead() 
					&& ownerWritableCheckBox.isSelected() == file.canOwnerWrite()
					&& ownerExecuteCheckBox.isSelected() == file.canOwnerExecute()					
					&& groupReadableCheckBox.isSelected() == file.canGroupRead() 
					&& groupWritableCheckBox.isSelected() == file.canGroupWrite()
					&& groupExecuteCheckBox.isSelected() == file.canGroupExecute()									
					&& otherReadableCheckBox.isSelected() == file.canOtherRead() 
					&& otherWritableCheckBox.isSelected() == file.canOtherWrite()
					&& otherExecuteCheckBox.isSelected() == file.canOtherExecute()
					&& nameTextField.getText().trim().equals(file.getName())
					&& b1
					;

			System.out.println("changed="+changed+" b1="+b1+" lm="+lastMod+" lm1="+lastMod1);
			saveButton.setEnabled(!changed);
		} catch (IOException e) {
			// Not implemented
			e.printStackTrace();
		}



	}

	protected void actionSave() {
		// Not implemented
		dispose();

	}

	protected void actionCancel() {
		cancel = true;
		dispose();
	}

	protected void actionView() {
		InputStream in = null;
		byte [] data = null;
		ViewAction action = (ViewAction) viewComboBox.getSelectedItem();
		Multiple mul = (Multiple) viewMultipleComboBox.getSelectedItem();

		int size = 200;
		try {
			size = Integer.parseInt(viewSizeTextField.getText());
			viewSizePanel.setVisible(true);
			switch (action) {
			case All: 
				in = file.getInputStream();
				data = in.readAllBytes();

				break;
			case Head:data = file.head(multiple(mul,size));	break;
			case Tail:	data = file.tail(multiple(mul,size));					break;
			case View:viewSizePanel.setVisible(false);break;


			default:
				break;
			}

		} catch (Exception e) {
			showError(e);
		} finally {
			if( in != null ) {
				try {in.close();} catch (Exception e2) {}
			}
		}

		if( data != null) {
			if( isBinaryCheckBox.isSelected()) {
				textArea.setFont( new Font("monospaced", normalFont.getStyle(), normalFont.getSize()));
				textArea.setText(formatBinary(data));
			} else {
				textArea.setFont(normalFont);
				textArea.setText(new String(data));
			}
		}

	}

	private String formatBinary(byte[] data) {
		String hdr = "01|02|03|04|05|06|07|08|09|10|11|12|13|14|15|16|";
		StringBuilder ret    = new StringBuilder(hdr);
		ret.append('\t');
		ret.append(hdr);
		ret.append('\n');
		int l = ret.length()+6;
		for(int idx=0; idx < l; idx++ ) {
			ret.append('-');
		}
		ret.append('\n');
		StringBuilder left   = new StringBuilder();
		StringBuilder right = new StringBuilder();

		for (int idx = 0; idx < data.length; idx++) {
			int i = idx % 16;
			if( idx > 0  &&  i == 0 ) {
				ret.append(left.toString().toUpperCase());
				ret.append('\t');
				ret.append(right.toString());
				ret.append('\n');
				left.setLength(0);
				right.setLength(0);
			}

			String tmp = Integer.toHexString(data[idx]);
			if( tmp.length()== 1) {
				tmp = "0"+tmp;
			} else if( tmp.length()>2) {
				tmp = tmp.substring(tmp.length()-2);
			}
			left.append(tmp);
			left.append('|');
			if( data[idx] >=33 && data[idx] <= 126) {
				right.append((char)data[idx]);
			} else {
				right.append('.');
			}
			right.append(" |");

		}
		if( left.length()>0) {
			while(left.length()< 48) {
				left.append(' ');
			}
			ret.append(left.toString().toUpperCase());
			ret.append('\t');
			ret.append(right.toString());
			ret.append('\n');
		}

		return ret.toString();
	}

	private int multiple(Multiple mul, int size) {
		int ret = size;
		switch (mul) {
		case KB: ret = (int)(size * K); break;
		case MB: ret = (int)(size * M); break;
		case GB: ret = (int)(size * G); break;	
		default:
			break;
		}
		return ret;
	}

	private void showError(Exception e) {
		JOptionPane.showMessageDialog(this, e, "", JOptionPane.ERROR_MESSAGE);

	}

	public void showDialog(FileSource file) throws IOException {
		this.file = file;
		setModal(true);

		ownerReadableCheckBox.setSelected(file.canOwnerRead());
		ownerWritableCheckBox.setSelected(file.canOwnerWrite());
		ownerExecuteCheckBox.setSelected(file.canOwnerExecute());

		groupReadableCheckBox.setSelected(file.canGroupRead());
		groupWritableCheckBox.setSelected(
				file.canGroupWrite()
				);
		groupExecuteCheckBox.setSelected(file.canGroupExecute());

		otherReadableCheckBox.setSelected(file.canOtherRead());
		otherWritableCheckBox.setSelected(file.canOtherWrite());
		otherExecuteCheckBox.setSelected(file.canOtherExecute());

		sizeLabel.setText(format(file.length()));
		mimneTypeLabel.setText(getType(file));
		saveButton.setEnabled(false);
		nameTextField.setText(file.getName());
		userTextField.setText(file.getOwner().getName());
		groupTextField.setText(file.getGroup().getName());
		fileTypeLabel.setText(file.isDirectory()?"Dir":"File");
		lastModifiedDateSpinnerModel.setValue(new Date(file.lastModified()));
		FileSource p = file.getParentFile();
		if( p == null) {
			setTitle("Root");
		} else {
			setTitle(p.getAbsolutePath());
		}
		new Thread(new Runnable() {

			@Override
			public void run() {
				try {
					if( file.length()>= 15) {
						byte[] data = file.head(15);
						for (int idx = 0; idx < data.length; idx++) {
							if( data[idx]<=0 || data[idx] >= 127) {
								SwingUtilities.invokeLater(new Runnable() {

									@Override
									public void run() {
										isBinaryCheckBox.setSelected(true);
									}
								});
								break;
							}

						}
					}
				} catch (IOException e) {
					// Not implemented
					e.printStackTrace();
				} 

			}
		}).start();
		setVisible(true);
		if( !cancel ) {
			System.out.println("Change it");
		}
	}

	private String getType(FileSource file2) {
		String ret = "?";
		String name = file2.getName();
		int idx = name.lastIndexOf('.');
		if( idx > 0 ) {
			ret = name.substring(idx);
		}
		return ret;
	}

	public static double K = 1024;
	public static double M = K*K;
	public static double G = M*M;
	public static double T = G*G;
	private JLabel fileTypeLabel;
	private JTextField viewSizeTextField;
	private JPanel viewSizePanel;
	private JComboBox<Multiple> viewMultipleComboBox;
	private JTextArea textArea;
	private JCheckBox isBinaryCheckBox;
	private JLabel createDateLabel;

	private String format(long length) {
		String ret = ""+length+"B";
		String suf = "";
		double group = 1;
		if( length>=T) {
			group = T;
			suf = "T";
		} else if( length>= G) {
			group = G;
			suf = "G";
		}else if( length>= M) {
			group = M;
			suf = "M";
		}else if( length>= K) {
			group =  K;
			suf = "K";
		}

		if( group != 1) {
			ret = (length/group)+suf+"B";
		}

		return ret;
	}
}
