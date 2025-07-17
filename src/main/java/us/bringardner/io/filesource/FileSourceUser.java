package us.bringardner.io.filesource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.UserPrincipal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class FileSourceUser extends FileSourcePrinciple implements UserPrincipal {
	
	
	Map<Integer,FileSourceGroup> groups = new TreeMap<>();
	FileSourceGroup group;
	
	/*
	 User name                    tony
Full Name
Comment
User's comment
Country/region code          000 (System Default)
Account active               Yes
Account expires              Never

Password last set            6/25/2025 8:03:14 AM
Password expires             Never
Password changeable          6/25/2025 8:03:14 AM
Password required            No
User may change password     Yes

Workstations allowed         All
Logon script
User profile
Home directory
Last logon                   6/29/2025 8:55:40 PM

Logon hours allowed          All

Local Group Memberships      *Administrators       *Remote Desktop Users
                             *Users
	 */
	public static FileSourceUser findUser(String userName) {
		FileSourceUser ret = null;
			//*nix, including macOS,  system use id
			String [] command = {"id ???"};

			if(FileSourceFactory. isWindows() ) {
				int idx = userName.indexOf('\\');
				if( idx > 0 ) {
					userName = userName.substring(idx+1);
				}
				
				String tmp []  = {"net","user",userName};
				command = tmp;
			} 

			ProcessBuilder builder = new ProcessBuilder(command);
			Process process;
			try {
				process = builder.start();
				int status = -1;
				try {
					status = process.waitFor();
				} catch (InterruptedException e) {
				}

				StringBuilder out = new StringBuilder();
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
					String line = null;
					while ((line = reader.readLine()) != null) {
						out.append(line);
						out.append("\n");
					}					
				}

				try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
					String line = null;
					while ((line = reader.readLine()) != null) {
						out.append(line);
						out.append("\n");
					}					
				}

				if( status == 0 ) {
					ret = new FileSourceUser(0, userName);
					String text = out.toString();
					int idx = text.indexOf("Local Group Memberships");
					if( idx > 0 ) {
						text = text.substring(idx+23);
						idx = text.indexOf("Global Group memberships");
						if( idx > 0 ) {
							text = text.substring(0,idx).trim();
						}
						String group=null;
						idx = text.indexOf('*');
						while( idx >= 0 ) {
							int idx2 = text.indexOf('*',idx+1);
							if( idx2 >=0) {
								group = text.substring(idx+1,idx2).trim();							
							} else {
								group = text.substring(idx+1).trim();
							}
							FileSourceGroup g=null;
							int id = windowsGroups.indexOf(group);
							if( id < 0 ) {
								id = windowsGroups.size();
								windowsGroups.add(group);							
							} 
							
							g = new FileSourceGroup(id, group);
							ret.addGroup(g);
							
							idx = idx2;
						}
					}
					
				} 
			} catch (IOException e) {
				e.printStackTrace();
			}
	
			return ret;
	}
	
	
	public FileSourceUser() {}
	
	public FileSourceUser(int uid,String name) {
		this(uid,name,0,"UnKnown");
	}
	
	public FileSourceUser(int uid,String name,int gid,String groupName) {
		super(uid, name);
		group = new FileSourceGroup(gid,groupName);	
		groups.put(gid, group);
	}
	
	public boolean hasGroup(int id) {
		return groups.containsKey(id);
	}
	
	public boolean hasGroup(String groupName) {
		if( FileSourceFactory.isWindows() ) {
			int idx = groupName.indexOf('\\');
			if( idx >= 0 ) {
				groupName = groupName.substring(idx+1);
			}
		}
		
		boolean ret = false;
		for(GroupPrincipal g : groups.values()) {
			if( (ret=g.getName().equalsIgnoreCase(groupName))) {
				break;
			}
		}
		
		return ret;
	}
	
	
	public FileSourceGroup getGroup() {
		return group;
	}

	public Map<Integer, FileSourceGroup> getGroups() {
		Map<Integer,FileSourceGroup> ret = new TreeMap<>();
		ret.putAll(groups);
		return ret;
	}

	public void setGroups(Map<Integer, FileSourceGroup> groups) {
		this.groups.clear();
		this.groups.putAll(groups);			
	}


	public String toString() {
		StringBuilder ret = new StringBuilder("uid="+getId()+"("+getName()+")");
		if( group !=null ) {
			ret.append(" gid="+group.getId()+"("+group.getName()+")");
		}
		
		if( groups.size()>0) {
			ret.append("groups=");
		}
		
		int idx=0;
		for(Integer id: groups.keySet()) {
			if( idx++>0) {
				ret.append(',');
			}
			
			ret.append(""+id+"("+groups.get(id).getName()+")");
		}
		
		return ret.toString();
	}
	
	public static FileSourceUser fromId(String idResponse) {
		if(FileSourceFactory.isWindows()) {
			return fromWindowsId(idResponse);
		} else {
			return fromUnixId(idResponse);
		}
	}
	
	/*
	 
USER INFORMATION
----------------
User Name: windowslaptop\tony
SID:       S-1-5-21-4225293122-3422176466-2310549978-1007

GROUP INFORMATION
-----------------
Group Name: Everyone
Type:       Well-known group
SID:        S-1-1-0
Attributes: Mandatory group, Enabled by default, Enabled group
	 */
	private static List<String> windowsGroups = new ArrayList<>();
	
	private static FileSourceUser fromWindowsId(String idResponse) {
		String lines [] = idResponse.split("\n");
		FileSourceUser ret = null;
		for(String line : lines) {
			if( line.startsWith("User Name:")) {
				int idx = line.lastIndexOf('\\');
				if( idx > 0 ) {
					String name = line.substring(idx+1).trim();
					ret = new FileSourceUser(0, name);
				}
			} else if( line.startsWith("Group Name:")) {
				if( ret !=null) {
					if( line.contains("Label")) {
						continue;
					}
					
					int idx = line.indexOf('\\');
					if( idx > 0 ) {
						String name = line.substring(idx+1).trim();
						idx = name.indexOf('\\');
						if( idx > 0 ) {
							name = name.substring(idx+1);							
						}
						
						FileSourceGroup g=null;
						int id = windowsGroups.indexOf(name);
						if( id < 0 ) {
							id = windowsGroups.size();
							windowsGroups.add(name);							
						} 
						
						g = new FileSourceGroup(id, name);
						ret.addGroup(g);
						
					}

				}
			}
		}
		
		return ret;
	}

	/**
	 * 
	 * @param idResponse:  response from the id command on *nix systems
	 * macos uid=503(Jimmie) gid=20(staff) groups=20(staff),12(everyone),61(localaccounts),701(com.apple.sharepoint.group.1),702(com.apple.sharepoint.group.2),100(_lpoperator),703(com.apple.sharepoint.group.3)
	 * linux uid=1000(ec2-user) gid=1000(ec2-user) groups=1000(ec2-user),4(adm),10(wheel),190(systemd-journal)
	 * @return A FileSourcePrinciple representing the id response
	 */
	
	private static FileSourceUser fromUnixId(String idResponse) {
		FileSourceUser ret = null;
		if( idResponse !=null && !idResponse.isEmpty()) {
			for(String part : idResponse.split(" ")) {
				part = part.trim();
				if( !part.isEmpty()) {
					String [] parts = part.split("=");
					if( parts.length==2) {
						if( parts[0].equals("uid")) {
							FileSourcePrinciple e = parseEntry(parts[1]);
							if( e!=null) {
								ret = new FileSourceUser(e.getId(),e.getName());
							}
						} else if( parts[0].equals("gid")) {
							FileSourcePrinciple e = parseEntry(parts[1]);
							if( e!=null) {
								ret.setGroup(new FileSourceGroup(e.getId(), e.getName()));
							}
						} else if( parts[0].equals("groups")) {
							for(String g : parts[1].split(",")) {
								FileSourcePrinciple e = parseEntry(g);
								if( e != null ) {
									ret.addGroup(new FileSourceGroup(e.getId(), e.getName()));
								}
							}
						} 
					}
				}
			}			
		}
		
		return ret;
	}
	
	public void addGroup(FileSourceGroup g) {
			groups.put(g.getId(), g);		
	}

	public void setGroup(FileSourceGroup group) {
		this.group = group;		
	}

	/**
	 * 
	 * @param id part: looks like this: 0-9+(name)
	 * @return
	 */
	private static FileSourcePrinciple parseEntry(String str) {
		
		String [] parts = str.replace('(', ' ').replace(')', ' ').trim().split(" ");
		if( parts.length>=2) {
			try {
				
				int id = Integer.parseInt(parts[0]);
				String name = parts[1].trim();
				return new FileSourcePrinciple(id, name);
			} catch (Exception e) {
			}
		}
		
		return null;
		
	}


	public boolean hasGroup(UserPrincipal principal) {
		return hasGroup(principal.getName());
	}
}
