/**
 * <PRE>
 * 
 * Copyright Tony Bringarder 1998, 2025 <A href="http://bringardner.com/tony">Tony Bringardner</A>
 * 
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       <A href="http://www.apache.org/licenses/LICENSE-2.0">http://www.apache.org/licenses/LICENSE-2.0</A>
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *  </PRE>
 */
package us.bringardner.io.filesource.fileproxy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.UserPrincipal;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Owner and group names by numeric id (BJL-31).
 * <p>
 * Files.getOwner() and PosixFileAttributes.group() turn the file's uid/gid into a name
 * (getpwuid / getgrgid) on every call, and the JDK doesn't cache it. With a directory
 * service (LDAP, SSSD, NIS) each lookup can take milliseconds, so listing a large directory
 * (FTP LIST, MLSD) could take seconds. Here the ids are read from the file (a plain stat)
 * and each id is looked up once, then reused for a short time so renamed users and groups
 * still show up.
 * <p>
 * Only where the "unix" attribute view exists (Linux, macOS); elsewhere the lookups are
 * done as before.
 */
final class PrincipalCache {

	/** How long a looked up name is reused */
	static final long TTL_MS = 60_000;
	/** At most this many ids per kind; the cache is emptied when it grows past it */
	static final int MAX_ENTRIES = 4096;

	private static final class Entry<P> {
		final P principal;
		final long expires;

		Entry(P principal, long expires) {
			this.principal = principal;
			this.expires = expires;
		}
	}

	private static final ConcurrentHashMap<Integer, Entry<UserPrincipal>> USERS = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<Integer, Entry<GroupPrincipal>> GROUPS = new ConcurrentHashMap<>();
	private static volatile boolean unixView = true;

	private PrincipalCache() {
	}

	/**
	 * @return the owner of path (links followed, like Files.getOwner)
	 */
	static UserPrincipal owner(Path path) throws IOException {
		Integer uid = id(path, "unix:uid");
		if( uid == null ) {
			return Files.getOwner(path);
		}
		return lookup(USERS, uid, () -> {
			try {
				return Files.getOwner(path);
			} catch (IOException e) {
				throw new java.io.UncheckedIOException(e);
			}
		});
	}

	/**
	 * @return the group of path (links not followed), or null if it has none or the
	 * file system has no POSIX attributes
	 */
	static GroupPrincipal group(Path path) throws IOException {
		Integer gid = id(path, "unix:gid", LinkOption.NOFOLLOW_LINKS);
		if( gid == null ) {
			return readGroup(path);
		}
		return lookup(GROUPS, gid, () -> {
			try {
				return readGroup(path);
			} catch (IOException e) {
				throw new java.io.UncheckedIOException(e);
			}
		});
	}

	private static GroupPrincipal readGroup(Path path) throws IOException {
		PosixFileAttributeView view = Files.getFileAttributeView(path, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
		if( view == null ) {
			return null;
		}
		PosixFileAttributes at = view.readAttributes();
		return at == null ? null : at.group();
	}

	/** The numeric id, or null where the "unix" view isn't available. */
	private static Integer id(Path path, String attribute, LinkOption... options) throws IOException {
		if( !unixView ) {
			return null;
		}
		try {
			Object ret = Files.getAttribute(path, attribute, options);
			return ret instanceof Integer ? (Integer) ret : null;
		} catch (UnsupportedOperationException | IllegalArgumentException e) {
			unixView = false;
			return null;
		}
	}

	private static <P> P lookup(ConcurrentHashMap<Integer, Entry<P>> cache, Integer id, Supplier<P> load) throws IOException {
		long now = System.currentTimeMillis();
		Entry<P> e = cache.get(id);
		if( e != null && e.expires > now ) {
			return e.principal;
		}
		P p;
		try {
			p = load.get();
		} catch (java.io.UncheckedIOException ex) {
			throw ex.getCause();
		}
		if( p != null ) {
			if( cache.size() >= MAX_ENTRIES ) {
				cache.clear();
			}
			cache.put(id, new Entry<>(p, now + TTL_MS));
		}
		return p;
	}

	/** Forget every cached name (for tests). */
	static void clear() {
		USERS.clear();
		GROUPS.clear();
	}
}
