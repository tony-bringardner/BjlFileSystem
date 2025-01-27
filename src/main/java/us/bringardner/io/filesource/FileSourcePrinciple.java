package us.bringardner.io.filesource;

import java.nio.file.attribute.UserPrincipal;
import java.util.Map;
import java.util.TreeMap;

public class FileSourcePrinciple implements UserPrincipal {
	String name;
	int uid;
	int gid;
	
	Map<Integer,String> groups = new TreeMap<>();
	
	
	public FileSourcePrinciple() {}
	
	public FileSourcePrinciple(String name) {
		this(name,0,0);
	}
	
	public FileSourcePrinciple(String name,int uid) {
		this(name,uid,0);
	}
	public FileSourcePrinciple(String name,int uid,int gid) {
		this.name = name;
		this.uid = uid;
		this.gid = gid;
	}
	
	public boolean hasGroup(int id) {
		return groups.containsKey(id);
	}
	
	public boolean hasGroup(String groupName) {
		boolean ret = false;
		for(String name: groups.values()) {
			if( (ret=name.equalsIgnoreCase(groupName))) {
				break;
			}
		}
		
		return ret;
	}
	
	@Override
	public String getName() {
		return name;
	}

	public int getUid() {
		return uid;
	}

	public void setUid(int uid) {
		this.uid = uid;
	}

	public int getGid() {
		return gid;
	}

	public void setGid(int gid) {
		this.gid = gid;
	}

	public Map<Integer, String> getGroups() {
		Map<Integer,String> ret = new TreeMap<>();
		ret.putAll(groups);
		return ret;
	}

	public void setGroups(Map<Integer, String> groups) {
		this.groups.clear();
		this.groups.putAll(groups);			
	}

	public  void setName(String name) {
		this.name = name;
	}

	public String toString() {
		StringBuilder ret = new StringBuilder("uid="+uid+"("+name+")");
		if( gid > 0 ) {
			String gn = groups.get(gid);
			ret.append(" gid"+gid+"("+gn+") ");
		}
		if( groups.size()>0) {
			ret.append("groups=");
		}
		int idx=0;
		for(Integer id: groups.keySet()) {
			if( idx++>0) {
				ret.append(',');
			}
			
			ret.append(""+id+"("+groups.get(id)+")");
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
	private static class Entry {
		int id;
		String name;
	}
	
	public static FileSourcePrinciple fromId(String idResponse) {
		FileSourcePrinciple ret = null;
		if( idResponse !=null && !idResponse.isEmpty()) {
			FileSourcePrinciple tmp = new FileSourcePrinciple();
			
			for(String part : idResponse.split(" ")) {
				part = part.trim();
				if( !part.isEmpty()) {
					String [] parts = part.split("=");
					if( parts.length==2) {
						if( parts[0].equals("uid")) {
							Entry e = parseEntry(parts[1]);
							if( e!=null) {
								tmp.uid = e.id;
								tmp.name = e.name;
							}
						} else if( parts[0].equals("gid")) {
							Entry e = parseEntry(parts[1]);
							if( e!=null) {
								tmp.gid = e.id;
							}
						} else if( parts[0].equals("groups")) {
							for(String g : parts[1].split(",")) {
								Entry e = parseEntry(g);
								if( e != null ) {
									tmp.groups.put(e.id, e.name);
								}
							}
						} 
					}
				}
			}
			if( tmp.uid> 0 && tmp.name!=null ) {
				ret = tmp;
			}
		}
		
		return ret;
	}
	
	/**
	 * 
	 * @param id part: looks like this: 0-9+(name)
	 * @return
	 */
	private static Entry parseEntry(String str) {
		
		String [] parts = str.replace('(', ' ').replace(')', ' ').trim().split(" ");
		if( parts.length>=2) {
			try {
				Entry ret = new Entry();
				ret.id = Integer.parseInt(parts[0]);
				ret.name = parts[1].trim();
				return ret;
			} catch (Exception e) {
			}
		}
		
		return null;
		
	}
}
