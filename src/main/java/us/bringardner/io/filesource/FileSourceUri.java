package us.bringardner.io.filesource;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * This class is required because the Java URI and URL both SUCK!!!
 */
public class FileSourceUri  {
	//scheme      = ALPHA *( ALPHA / DIGIT / "+" / "-" / "." )
	String scheme;
	String host;
	Integer port;
	String authority;
	String path;
	String query;
	String fragment;
	String user;
	String password;
	
	List<String[]> queryParts = new ArrayList<String[]>();
	
	
	
	
	
	public String toString() {
		StringBuilder ret = new StringBuilder(scheme+":");
		if( authority !=null ) {
			ret.append(authority);
		} else if( host != null) {
			ret.append(host);
			if( port != null ) {
				ret.append(":"+port);
			}
		}
		
		
		if( path !=null ) {
			ret.append(path);
		}
		if( query != null ) {
			ret.append("?"+query);
		}
		if( fragment!=null ) {
			ret.append("#"+fragment);
		}


		return ret.toString();
	}


	public String getUser() {
		return user;
	}


	public void setUser(String user) {
		this.user = user;
	}


	public String getPassword() {
		return password;
	}


	public void setPassword(String password) {
		this.password = password;
	}


	public String getQueryByName(String name) {
		for(String [] s: queryParts) {
			if( s.length>1 && s[0].equalsIgnoreCase(name)) {
				return s[1];
			}
		}
		return null;
	}

	public FileSourceUri(URI uri) throws IOException {
		user = uri.getUserInfo();
		if( user != null ) {
			int idx = user.indexOf(':');
			if( idx > 0) {
				password = user.substring(idx+1);
				user = user.substring(0,idx);
			}
		}
		
		scheme = uri.getScheme();
		authority = uri.getAuthority();
		if( uri.getPort()>0) {
			port = uri.getPort();
		}
		host = uri.getHost();
		path = uri.getPath();
		String qu = uri.getQuery();
		fragment = uri.getFragment();

		if( path == null ) {			
			String ssp = uri.getSchemeSpecificPart();
			if( ssp != null ) {
				int idx = ssp.indexOf('?');
				if( idx>=0) {
					path = ssp.substring(0, idx);
					ssp = ssp.substring(idx+1);
					idx = ssp.indexOf('#');
					if( idx >=0) {
						fragment = ssp.substring(idx+1);
						ssp =  ssp.substring(idx);
					}
					qu = ssp;
				} else {
					// no query string
					idx = ssp.indexOf('#');
					if( idx >=0) {
						fragment = ssp.substring(idx+1);
						ssp =  ssp.substring(idx);
					}
					path = ssp;
				}
			}			
		}
		
		setQuery(qu);
	}

	public String getScheme() {
		return scheme;
	}


	public void setScheme(String scheme) {
		this.scheme = scheme;
	}


	public String getHost() {
		return host;
	}


	public void setHost(String host) {
		this.host = host;
	}


	public Integer getPort() {
		return port;
	}


	public void setPort(Integer port) {
		this.port = port;
	}


	public String getAuthority() {
		return authority;
	}


	public void setAuthority(String authority) {
		this.authority = authority;
	}


	public String getPath() {
		return path;
	}


	public void setPath(String path) {
		this.path = path;
	}


	public String getQuery() {
		return query;
	}


	public void setQuery(String query) {
		this.query = query;
		queryParts.clear();
		if( query != null ) {
			List<String[]> list = new ArrayList<String[]>();
			String p1 [] =  query.split("[,]");
			for(String s : p1) {
				String [] p2 = s.split("[=]");
				if( p2.length>1) {
					list.add(p2);
				}
			}
			queryParts.addAll(list);
		} 
	}


	public String getFragment() {
		return fragment;
	}


	public void setFragment(String fragment) {
		this.fragment = fragment;
	}


	public static void main(String[] args) throws URISyntaxException, IOException {


		URI uri = new URI("filesource:target/TestDir?sourcetype=fileproxy");


		FileSourceUri fsuri = new FileSourceUri(uri);
		System.out.println(fsuri);
		uri = new URI("foo://example.com:8042/over/there?name=ferret#nose");
		fsuri = new FileSourceUri(uri);
		System.out.println(fsuri);
		uri = new URI("ftps://tony:0000@bringardner.us:10021/home/tony");
		fsuri = new FileSourceUri(uri);
		System.out.println(fsuri);
		
	}


	public String getFactoryId() {
		String ret = getQueryByName(FileSourceFactory.QUERY_STRING_SOURCE_TYPE);
		if( ret == null &&  !FileSourceFactory.FILE_SOURCE_PROTOCOL.equals(scheme)) {
			ret = scheme;
		}
		return ret;
	}

	public String getSessionId() {		
		return getQueryByName(FileSourceFactory.QUERY_STRING_SESSION_ID);
	}
	
}
