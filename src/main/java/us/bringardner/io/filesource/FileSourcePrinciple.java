package us.bringardner.io.filesource;

public class FileSourcePrinciple {
	private int id;
	private String name="UnKnown";
	
	public FileSourcePrinciple() {
		
	}
	
	public FileSourcePrinciple(FileSourcePrinciple principle) {
		this(principle.getId(),principle.getName());
	}
	
	public FileSourcePrinciple(int id, String name) {
		this.id = id;
		this.name = name;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
	
	public String toString() {
		return  ""+id+"("+name+")";
	}
	
	
}
