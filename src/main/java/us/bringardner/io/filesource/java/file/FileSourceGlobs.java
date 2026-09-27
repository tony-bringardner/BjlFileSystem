package us.bringardner.io.filesource.java.file;

import java.util.regex.PatternSyntaxException;

/**
 * Converts a glob (as documented for java.nio.file.FileSystem.getPathMatcher)
 * to a regular expression, for a file system whose name separator is 'sep'.
 * Follows the JDK's rules:
 * <ul>
 * <li>{@code *} matches zero or more characters within one name (it doesn't cross a separator)</li>
 * <li>{@code **} matches zero or more characters across names</li>
 * <li>{@code ?} matches exactly one character other than the separator</li>
 * <li>{@code [abc]}, {@code [a-z]}, {@code [!a-c]} match one character (never the separator)</li>
 * <li>{@code {a,b,c}} matches any of the comma-separated sub-patterns (groups can't nest)</li>
 * <li>{@code \} escapes the next character; {@code /} in the glob means the separator</li>
 * </ul>
 */
final class FileSourceGlobs {

	private static final String REGEX_META = ".^$+{[]|()";
	private static final String GLOB_META = "\\*?[{";

	private FileSourceGlobs() {
	}

	private static boolean isRegexMeta(char c) {
		return REGEX_META.indexOf(c) >= 0;
	}

	private static boolean isGlobMeta(char c) {
		return GLOB_META.indexOf(c) >= 0;
	}

	private static char next(String glob, int i) {
		return i < glob.length() ? glob.charAt(i) : 0;
	}

	static String toRegex(String glob, char sep) {
		String sepRegex = sep == '\\' ? "\\\\" : (isRegexMeta(sep) ? "\\"+sep : ""+sep);
		String notSep = "[^"+(sep == '\\' || sep == '^' || sep == ']' || sep == '-' ? "\\"+sep : ""+sep)+"]";

		boolean inGroup = false;
		StringBuilder regex = new StringBuilder("^");

		int i = 0;
		while( i < glob.length() ) {
			char c = glob.charAt(i++);
			switch (c) {
			case '\\':
				if( i == glob.length() ) {
					throw new PatternSyntaxException("No character to escape", glob, i - 1);
				}
				char escaped = glob.charAt(i++);
				if( isGlobMeta(escaped) || isRegexMeta(escaped) ) {
					regex.append('\\');
				}
				regex.append(escaped);
				break;
			case '/':
				regex.append(sepRegex);
				break;
			case '[':
				// one character, never the separator
				regex.append("[").append(notSep).append("&&[");
				if( next(glob, i) == '^' ) {
					regex.append("\\^");
					i++;
				} else {
					if( next(glob, i) == '!' ) {
						regex.append('^');
						i++;
					}
					if( next(glob, i) == '-' ) {
						regex.append('-');
						i++;
					}
				}
				boolean hasRangeStart = false;
				char last = 0;
				while( i < glob.length() ) {
					c = glob.charAt(i++);
					if( c == ']' ) {
						break;
					}
					if( c == '/' || c == sep ) {
						throw new PatternSyntaxException("Explicit 'name separator' in class", glob, i - 1);
					}
					if( c == '\\' || c == '[' || c == '&' && next(glob, i) == '&' ) {
						regex.append('\\');
					}
					regex.append(c);
					if( c == '-' ) {
						if( !hasRangeStart ) {
							throw new PatternSyntaxException("Invalid range", glob, i - 1);
						}
						if( (c = next(glob, i++)) == 0 || c == ']' ) {
							break;
						}
						if( c < last ) {
							throw new PatternSyntaxException("Invalid range", glob, i - 3);
						}
						regex.append(c);
						hasRangeStart = false;
					} else {
						hasRangeStart = true;
						last = c;
					}
				}
				if( c != ']' ) {
					throw new PatternSyntaxException("Missing ']'", glob, i - 1);
				}
				regex.append("]]");
				break;
			case '{':
				if( inGroup ) {
					throw new PatternSyntaxException("Cannot nest groups", glob, i - 1);
				}
				regex.append("(?:(?:");
				inGroup = true;
				break;
			case '}':
				if( inGroup ) {
					regex.append("))");
					inGroup = false;
				} else {
					regex.append('}');
				}
				break;
			case ',':
				if( inGroup ) {
					regex.append(")|(?:");
				} else {
					regex.append(',');
				}
				break;
			case '*':
				if( next(glob, i) == '*' ) {
					regex.append(".*");   // crosses name boundaries
					i++;
				} else {
					regex.append(notSep).append('*');
				}
				break;
			case '?':
				regex.append(notSep);
				break;
			default:
				if( isRegexMeta(c) ) {
					regex.append('\\');
				}
				regex.append(c);
			}
		}

		if( inGroup ) {
			throw new PatternSyntaxException("Missing '}'", glob, i - 1);
		}
		return regex.append('$').toString();
	}
}
