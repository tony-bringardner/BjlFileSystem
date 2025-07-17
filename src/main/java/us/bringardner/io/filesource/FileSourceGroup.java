package us.bringardner.io.filesource;

import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.UserPrincipal;

public class FileSourceGroup extends FileSourcePrinciple implements GroupPrincipal {
	
	public FileSourceGroup() {}
	public FileSourceGroup(int id,String name) {
		super(id, name);
	}
	
	@Override
	public boolean equals(Object obj) {
		if (	obj instanceof UserPrincipal) {
			return ((UserPrincipal)obj).getName().equals(getName());
		}
		return false;
	}
	
}
