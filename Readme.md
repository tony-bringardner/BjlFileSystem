# BjlFileSystem
 
This project defines an Interface called **FileSource** that looks very much like **java.io.File** but enables multiple implementations to coexists in a runtime environment (FTP, SFTP, ...).  This will allow the same application code to use many different types (implementations) without the knowledge at compile time.

The objective here was to provide something with syntax close enough java.io.File that it would not require significant effort to convert back and forth from java.io.File.  

 
All **FileSource** objects are created by a **FileSourceFactory**.
The package provides two concrete implementations:
+ FileProxy:  This implementation using java.io.File as it's underlying implementation.  This is the default factory.
+ MemoryFileSource:  This is a virtual, memory only implementation.


The base **FileSourceFactory** uses [ServiceLoader](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/util/ServiceLoader.html) to identify and instantiate factories you can see an example of the [ServiceLoader](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/util/ServiceLoader.html) configuration in the **resource** directory of this project.





I was very excited when **java.nio.file.FileSystem** was introduced in Java SE 7 (1.7) July 2011.  However, IMHO, The syntax is overly complex and not even close to **java.io.File**. So it falls short of meeting what I would consider *minimal* requirements. Even so, this package does provides support for the **java.nio.file.FileSystem** package.


The best way to get started with **FileSource** is the look at the unit test FileSourceAbstractTestClass.

 
**Dependencies:**  
- Java 9  
- BjlCore  
- BjlIo  

Testing:
- Junit 4.12
 			
