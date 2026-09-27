package us.bringardner.io.filesource;

import java.io.IOException;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.UserPrincipal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class FileSourceUser extends FileSourcePrinciple implements UserPrincipal {

	private static final long serialVersionUID = 1L;
	private static final us.bringardner.core.util.LogHelper logger = new us.bringardner.core.util.LogHelper(FileSourceUser.class);
	
	
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
	/**
	 * Look up a user and their groups: `id name` on macOS/Linux (this used to
	 * run the non-existent command "id ???", so it always failed there) and
	 * `net user name` on Windows.
	 * Note: the `net user` output is parsed by its English labels, so group
	 * membership isn't found on localized Windows.
	 * @return the user, or null if the lookup failed
	 */
	public static FileSourceUser findUser(String userName) {
		FileSourceUser ret = null;
		try {
			if( !FileSourceFactory.isWindows() ) {
				ProcessRunner.Result result = ProcessRunner.run("id", userName);
				return result.exitCode == 0 ? fromUnixId(result.stdout.trim()) : null;
			}

			int idx = userName.indexOf('\\');
			if( idx > 0 ) {
				userName = userName.substring(idx+1);
			}

			// Reads output while the command runs (waiting first could deadlock)
			ProcessRunner.Result result = ProcessRunner.run("net","user",userName);
			StringBuilder out = new StringBuilder(result.stdout);
			out.append(result.stderr);
			int status = result.exitCode;

			if( status == 0 ) {
				ret = new FileSourceUser(0, userName);
				String text = out.toString();
				idx = text.indexOf("Local Group Memberships");
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
						ret.addGroup(new FileSourceGroup(windowsGroupId(group), group));
						idx = idx2;
					}
				}
			} 
		} catch (IOException e) {
			logger.logError("Can't look up user "+userName, e);
			return null;
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
			if( idResponse != null && idResponse.trim().startsWith("\"") ) {
				return fromWindowsCsv(idResponse);
			}
			return fromWindowsId(idResponse);
		} else {
			return fromUnixId(idResponse);
		}
	}

	/**
	 * Parse `whoami /user /groups /fo csv /nh`. Unlike the /fo list output
	 * (parsed by fromWindowsId), this doesn't depend on the English labels
	 * "User Name:" / "Group Name:", so it works on localized Windows:
	 * user rows have 2 columns ("DOMAIN\\user","SID"), group rows have 4
	 * ("DOMAIN\\group","type","SID","attributes"). Integrity-level
	 * entries (SID S-1-16-*) are skipped.
	 */
	public static FileSourceUser fromWindowsCsv(String csv) {
		FileSourceUser ret = null;
		if( csv == null ) {
			return null;
		}
		for(String line : csv.split("\\r?\\n")) {
			List<String> cols = parseCsvLine(line.trim());
			if( cols.size() == 2 && cols.get(1).startsWith("S-1-") && ret == null ) {
				ret = new FileSourceUser(0, stripDomain(cols.get(0)));
			} else if( cols.size() >= 4 && ret != null && cols.get(2).startsWith("S-1-") && !cols.get(2).startsWith("S-1-16-") ) {
				String name = stripDomain(cols.get(0));
				ret.addGroup(new FileSourceGroup(windowsGroupId(name), name));
			}
		}
		return ret;
	}

	private static String stripDomain(String name) {
		int idx = name.lastIndexOf('\\');
		return idx >= 0 ? name.substring(idx+1).trim() : name.trim();
	}

	/** Split one CSV line with double-quoted fields ("" is an escaped quote). */
	private static List<String> parseCsvLine(String line) {
		List<String> ret = new ArrayList<>();
		if( line.isEmpty() ) {
			return ret;
		}
		StringBuilder cur = new StringBuilder();
		boolean quoted = false;
		for(int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if( quoted ) {
				if( c == '"' ) {
					if( i+1 < line.length() && line.charAt(i+1) == '"' ) {
						cur.append('"');
						i++;
					} else {
						quoted = false;
					}
				} else {
					cur.append(c);
				}
			} else if( c == '"' ) {
				quoted = true;
			} else if( c == ',' ) {
				ret.add(cur.toString());
				cur.setLength(0);
			} else {
				cur.append(c);
			}
		}
		ret.add(cur.toString());
		return ret;
	}

	/** Stable small ids for Windows group names (Windows groups have SIDs, not numeric ids). */
	private static int windowsGroupId(String name) {
		synchronized (windowsGroups) {
			int id = windowsGroups.indexOf(name);
			if( id < 0 ) {
				id = windowsGroups.size();
				windowsGroups.add(name);
			}
			return id;
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
	private static final List<String> windowsGroups = new ArrayList<>();
	
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
						
						ret.addGroup(new FileSourceGroup(windowsGroupId(name), name));
						
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
			} catch (NumberFormatException e) {
				// not an "id(name)" entry
			}
		}
		
		return null;
		
	}


	public boolean hasGroup(UserPrincipal principal) {
		return hasGroup(principal.getName());
	}
}
