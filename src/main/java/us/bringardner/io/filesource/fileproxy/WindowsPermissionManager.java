package us.bringardner.io.filesource.fileproxy;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.UserPrincipal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import us.bringardner.io.filesource.fileproxy.FileProxy.PermissionManager;

public class WindowsPermissionManager implements PermissionManager{

	public static final String GROUP_SID_PROPERTY = "GroupSid";
	public static final String OTHER_SID_PROPERTY = "OtherSid";
	
	private static volatile String groupSid;
	private static volatile String otherSid;
	
	public static String getGroupSid() {
		if( groupSid == null ) {
			synchronized (WindowsPermissionManager.class) {
				if( groupSid == null ) {
					groupSid= System.getProperty(GROUP_SID_PROPERTY, "Users");
				}
			}
		}
		
		return groupSid;
	}
	
	public static String getOtherSid() {
		if( otherSid == null ) {
			synchronized (WindowsPermissionManager.class) {
				if( otherSid == null ) {
					otherSid= System.getProperty(OTHER_SID_PROPERTY, "Other");
				}
			}
		}
		
		return otherSid;
	}
	
	
	private File target ;

	public WindowsPermissionManager(File file) {
		target = file;
		
	}
	
	public UserPrincipal getGroupPrincipal() throws IOException {
		return target.toPath().getFileSystem().getUserPrincipalLookupService().lookupPrincipalByGroupName(getGroupSid());			
	}

	public UserPrincipal getOtherPrincipal() throws IOException {
		return target.toPath().getFileSystem().getUserPrincipalLookupService().lookupPrincipalByGroupName(getOtherSid());			
	}

	private boolean isAllowed(UserPrincipal user,AclEntryPermission permission) throws IOException {
		boolean ret = false;
		
		AclFileAttributeView view = Files.getFileAttributeView(target.toPath(), AclFileAttributeView.class);
		List<AclEntry> acl = view.getAcl();

		for(AclEntry e : acl) {
			if(e.principal().equals(user)) {
				for(AclEntryPermission p : e.permissions()) { 
					if( p.equals(permission)) {
						if( e.type() == AclEntryType.DENY) {
							return false;
						}
						if( e.type() == AclEntryType.ALLOW) {
							ret = true;
						}
					}
				} 
			} 
		}

		return ret;
	}
	
	private boolean isOtherAllowed(AclEntryPermission permission) throws IOException {
		boolean ret = false;
		GroupPrincipal other = target.toPath().getFileSystem().getUserPrincipalLookupService().lookupPrincipalByGroupName(getOtherSid());		
		ret = isAllowed(other, permission);
		return ret;
	}

	private boolean isGroupAllowed( AclEntryPermission permission) throws IOException {
		boolean ret = false;
		UserPrincipal group = target.toPath().getFileSystem().getUserPrincipalLookupService().lookupPrincipalByGroupName(getGroupSid());		
		ret = isAllowed(group, permission);

		return ret;
	}

	public static void main(String[] args) throws IOException {
		File file = new File("TestFiles\\Hotel California.txt").getCanonicalFile();
		GroupPrincipal o = file.toPath().getFileSystem().getUserPrincipalLookupService().lookupPrincipalByGroupName("Creator Owner");

		System.out.println(o);
	}

	private boolean setPermission(UserPrincipal user,AclEntryPermission perm,boolean value) throws IOException {
		boolean ret = false;

		if( value) {
			ret = addPermission(user,perm);
		} else {
			ret = removePermission(user,perm);
		}

		return ret;

	}

	public boolean setOwnerReadable(boolean value) throws IOException {
		boolean ret = setPermission(Files.getOwner(target.toPath()),AclEntryPermission.READ_DATA, value);

		return ret;
	}

	public boolean setOwnerWritable(boolean value) throws IOException {
		boolean ret = setPermission(Files.getOwner(target.toPath()),AclEntryPermission.WRITE_DATA, value);
		return ret;
	}

	

	public boolean setGroupReadable(boolean value) throws IOException {
		boolean ret =setPermission(getGroupPrincipal(),AclEntryPermission.READ_DATA, value);
		return ret;
	}

	public boolean setGroupWritable(boolean value) throws IOException {
		boolean ret =setPermission(getGroupPrincipal(),AclEntryPermission.WRITE_DATA, value);
		return ret;
	}

	
	public boolean setOtherReadable(boolean value) throws IOException {
		boolean ret =setPermission(getOtherPrincipal(),AclEntryPermission.READ_DATA, value);
		return ret;
	}

	public boolean setOtherWritable(boolean value) throws IOException {
		boolean ret =setPermission(getOtherPrincipal(),AclEntryPermission.WRITE_DATA, value);
		return ret;
	}


	@Override
	public boolean canRead() throws IOException {
		return Files.isReadable(target.toPath());
	}

	@Override
	public boolean canWrite() throws IOException {
		return Files.isWritable(target.toPath());
	}

	@Override
	public boolean canExecute() throws IOException {
		return Files.isExecutable(target.toPath());
	}

	@Override
	public boolean canOwnerExecute() throws IOException {
		return isOwnerAllowed( AclEntryPermission.EXECUTE);
	}

	private boolean isOwnerAllowed(AclEntryPermission permission) throws IOException {
		Boolean ret = null;
		UserPrincipal owner = Files.getOwner(target.toPath());
		ret = isAllowed(owner, permission);
		return ret;
	}

	@Override
	public boolean canOwnerRead() throws IOException {
		return isOwnerAllowed( AclEntryPermission.READ_DATA);
	}

	@Override
	public boolean canOwnerWrite() throws IOException {
		return isOwnerAllowed( AclEntryPermission.WRITE_DATA);
	}

	@Override
	public boolean canGroupRead() throws IOException {
		return isGroupAllowed( AclEntryPermission.READ_DATA);
	}

	@Override
	public boolean canGroupWrite() throws IOException {
		return isGroupAllowed( AclEntryPermission.WRITE_DATA);
	}

	@Override
	public boolean canGroupExecute() throws IOException {
		return isGroupAllowed( AclEntryPermission.EXECUTE);
	}

	@Override
	public boolean canOtherRead() throws IOException {
		return isOtherAllowed( AclEntryPermission.READ_DATA);
	}

	@Override
	public boolean canOtherWrite() throws IOException {
		return isOtherAllowed( AclEntryPermission.WRITE_DATA);
	}



	@Override
	public boolean canOtherExecute() throws IOException {
		return isOtherAllowed( AclEntryPermission.EXECUTE);
	}

	@Override
	public boolean setExecutable(boolean b, boolean ownerOnly) throws IOException {
		boolean ret = setOwnerExecutable(b);

		if(ret &&  !ownerOnly ) {
			if(setGroupExecutable(b)) {
				setOtherExecutable(b);
			}
		}
		return ret;
	}

	@Override
	public boolean setReadable(boolean b, boolean ownerOnly) throws IOException {
		boolean ret = setOwnerReadable(b);

		if(ret &&  !ownerOnly ) {
			if(setGroupReadable(b)) {
				setOtherReadable(b);
			}
		}
		return ret;
	}

	@Override
	public boolean setWritable(boolean b, boolean ownerOnly) throws IOException {
		boolean ret = setOwnerWritable(b);

		if(ret &&  !ownerOnly ) {
			if(setGroupWritable(b)) {
				setOtherWritable(b);
			}
		}
		return ret;
	}

	@Override
	public boolean setExecutable(boolean b) throws IOException {
		return setOwnerExecutable(b);
	}

	@Override
	public boolean setReadable(boolean b) throws IOException {
		return setOwnerReadable(b);
	}

	@Override
	public boolean setWritable(boolean b) throws IOException {
		return setOwnerWritable(b);
	}

	@Override
	public boolean setGroupExecutable(boolean b) throws IOException {
		return setPermission(getGroupPrincipal(),AclEntryPermission.EXECUTE, b);
	}

	@Override
	public boolean setOwnerExecutable(boolean b) throws IOException {
		return setPermission(Files.getOwner(target.toPath()),AclEntryPermission.EXECUTE, b);
	}

	@Override
	public boolean setOtherExecutable(boolean b) throws IOException {
		return setPermission(getOtherPrincipal(),AclEntryPermission.EXECUTE, b);
	}

	@Override
	public boolean setLastAccessTime(long time) throws IOException {
		BasicFileAttributeView view = Files.getFileAttributeView(target.toPath(), BasicFileAttributeView.class);
		view.setTimes(null, FileTime.fromMillis(time),null);
		return true;

	}

	@Override
	public boolean setCreateTime(long time) throws IOException {
		BasicFileAttributeView view = Files.getFileAttributeView(target.toPath(), BasicFileAttributeView.class);
		view.setTimes(null, null, FileTime.fromMillis(time));
		return true;
	}

	@Override
	public boolean setGroup(GroupPrincipal group) throws IOException {
		AclFileAttributeView view = Files.getFileAttributeView(target.toPath(), AclFileAttributeView.class);
		
		List<AclEntry> acl = view.getAcl();
		String name = group.getName();
		AclEntry ge=null;

		for(AclEntry e : acl) {
			if( e.principal().getName().equals(name)){
				ge = e;
				break;
			}
		} 

		if( ge == null) {
			ge = AclEntry.newBuilder().setPrincipal(group).setType(AclEntryType.ALLOW).setPermissions(AclEntryPermission.READ_DATA,AclEntryPermission.WRITE_DATA,AclEntryPermission.EXECUTE).build();
			acl.add(0,ge);
			view.setAcl(acl);
		} 


		return true;
	}

	private boolean addPermission(UserPrincipal user,AclEntryPermission perm) throws IOException {
		boolean ret = false;


		AclFileAttributeView view = Files.getFileAttributeView(target.toPath(), AclFileAttributeView.class);
		List<AclEntry> acl = view.getAcl();
		List<AclEntry> acl2 = new ArrayList<>();

		AclEntry oe=null;		
		for(AclEntry e : acl) {
			if( e.principal().equals(user))	{
				oe = e;				
			} else {
				acl2.add(e);
			}
		}

		if( oe == null) {
			oe = AclEntry.newBuilder().setPrincipal(user).setType(AclEntryType.ALLOW).build();
		} 

		Set<AclEntryPermission> perms = oe.permissions();
		if( !perms.contains(perm)) {
			perms.add(perm);
		}

		oe = AclEntry.newBuilder(oe).setPermissions(perms).build();
		acl2.add(0,oe);
		view.setAcl(acl2);
		ret = true;


		return ret;

	}

	private boolean removePermission(UserPrincipal user,AclEntryPermission perm) throws IOException {
		boolean ret = false;

		AclFileAttributeView view = Files.getFileAttributeView(target.toPath(), AclFileAttributeView.class);
		List<AclEntry> acl = view.getAcl();
		List<AclEntry> acl2 = new ArrayList<>();

		AclEntry oe=null;		
		for(AclEntry e : acl) {
			if( e.principal().equals(user))	{
				oe = e;				
			} else {
				acl2.add(e);
			}
		}

		if( oe == null) {
			oe = AclEntry.newBuilder().setPrincipal(user).setType(AclEntryType.ALLOW).build();
		} 

		Set<AclEntryPermission> perms = oe.permissions();
		if( perms.contains(perm)) {
			perms.remove(perm);
		}

		oe = AclEntry.newBuilder(oe).setPermissions(perms).build();
		acl2.add(0,oe);
		view.setAcl(acl2);
		ret = true;

		return ret;

	}

}
