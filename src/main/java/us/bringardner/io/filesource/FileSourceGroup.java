package us.bringardner.io.filesource;

import java.nio.file.attribute.GroupPrincipal;

public class FileSourceGroup extends FileSourcePrinciple implements GroupPrincipal {
	public FileSourceGroup() {}
	public FileSourceGroup(int id,String name) {
		super(id, name);
	}
}
