package us.bringardner.io.filesource;

import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.UserPrincipal;
import java.util.Map;
import java.util.TreeMap;

public class FileSourceUser extends FileSourcePrinciple implements UserPrincipal {
	
	
	Map<Integer,FileSourceGroup> groups = new TreeMap<>();
	FileSourceGroup group;
	
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
	
	/**
	 * 
	 * @param idResponse:  response from the id command on *nix systems
	 * macos uid=503(Jimmie) gid=20(staff) groups=20(staff),12(everyone),61(localaccounts),701(com.apple.sharepoint.group.1),702(com.apple.sharepoint.group.2),100(_lpoperator),703(com.apple.sharepoint.group.3)
	 * linux uid=1000(ec2-user) gid=1000(ec2-user) groups=1000(ec2-user),4(adm),10(wheel),190(systemd-journal)
	 * @return A FileSourcePrinciple representing the id response
	 */
	
	public static FileSourceUser fromId(String idResponse) {
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
}
