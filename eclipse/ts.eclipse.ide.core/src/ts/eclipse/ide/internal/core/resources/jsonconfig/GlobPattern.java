package ts.eclipse.ide.internal.core.resources.jsonconfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

final class GlobPattern {

	private final List<String> leadingFixedSteps;
	private final List<String> otherRegexSteps;
	private final ImplicitExtensions implicitExts;
	private Pattern containersPattern = null;
	private Pattern filesPattern = null;

	private GlobPattern(List<String> leadingFixedSteps, List<String> otherRegexSteps, ImplicitExtensions implicitExts) {
		this.leadingFixedSteps = leadingFixedSteps;
		this.otherRegexSteps = otherRegexSteps;
		this.implicitExts = implicitExts;
	}

	IPath getLeadingFixedPath() {
		if (!leadingFixedSteps.isEmpty()) {
			return new Path(leadingFixedSteps.stream().collect(Collectors.joining("/")));
		}
		return null;
	}

	GlobPattern withoutLeadingFixedPath() {
		if (leadingFixedSteps.isEmpty()) {
			return this;
		}
		if (!otherRegexSteps.isEmpty()) {
			return new GlobPattern(Collections.emptyList(), otherRegexSteps, implicitExts);
		}
		return null;
	}

	GlobPattern concat(GlobPattern otherPattern) {
		List<String> leadingFixedSteps = new ArrayList<>(this.leadingFixedSteps);
		List<String> otherRegexSteps = new ArrayList<>();
		for (String regexStep : this.otherRegexSteps) {
			otherRegexSteps.add(regexStep);
		}
		for (String fixedStep : otherPattern.leadingFixedSteps) {
			if (otherRegexSteps.isEmpty()) {
				leadingFixedSteps.add(fixedStep);
			} else {
				otherRegexSteps.add(Pattern.quote(fixedStep));
			}
		}
		for (String regexStep : otherPattern.otherRegexSteps) {
			otherRegexSteps.add(regexStep);
		}
		return new GlobPattern(Collections.unmodifiableList(leadingFixedSteps),
				Collections.unmodifiableList(otherRegexSteps), implicitExts);
	}

	Pattern getContainersPattern() {
		if (containersPattern == null) {
			StringBuffer s = new StringBuffer();
			for (String fixedStep : leadingFixedSteps) {
				s.append(Pattern.quote(fixedStep));
			}
			for (String regexSteps : otherRegexSteps) {
				s.append(regexSteps);
			}
			containersPattern = Pattern.compile(s.toString());
		}
		return containersPattern;
	}

	Pattern getFilesPattern() {
		if (filesPattern == null) {
			Pattern containersPattern = getContainersPattern();
			if (implicitExts == null) {
				filesPattern = containersPattern;
			} else {
				filesPattern = Pattern.compile(containersPattern.pattern() + "(?:" + implicitExts.pattern + ")");
			}
		}
		return filesPattern;
	}

	@Override
	public String toString() {
		String s = leadingFixedSteps.toString() + otherRegexSteps.toString();
		if (implicitExts != null) {
			s += " (implicit extesions " + implicitExts + ")";
		}
		return s;
	}

	enum ImplicitExtensions {
		TS_ONLY("\\.(?:d\\.ts|tsx?)"), TS_AND_JS("(\\*)|(\\?)|(.+)");

		private final Pattern pattern;

		ImplicitExtensions(String regex) {
			this.pattern = Pattern.compile(regex);
		}
	}

	static GlobPattern parse(String patternString) {
		return parse(patternString, null);
	}

	static GlobPattern parse(String patternString, ImplicitExtensions implicitExts) {
		List<String> leadingFixedSteps = new ArrayList<>();
		List<String> otherRegexSteps = new ArrayList<>();

		String[] segments = patternString.split("/");
		for (int i = 0; i < segments.length; i++) {
			String segment = segments[i];
			boolean last = i >= segments.length - 1;
			if ("**".equals(segment)) { // recursive subdirs
				otherRegexSteps.add("(?:.+/)?");
			} else {
				String suffix = (!last ? "/" : "");
				if (isFixedSegment(segment)) {
					if (otherRegexSteps.isEmpty()) {
						leadingFixedSteps.add(segment + suffix);
					} else {
						otherRegexSteps.add(Pattern.quote(segment) + suffix);
					}
				} else {
					otherRegexSteps.add(translateSegmentToRegex(segment) + suffix);
				}
			}
		}

		if (implicitExts != null) {
			String lastSegment = segments[segments.length - 1];
			if (!(lastSegment.equals("*") || lastSegment.endsWith(".*"))) {
				implicitExts = null;
			}
		}


		return new GlobPattern(Collections.unmodifiableList(leadingFixedSteps),
				Collections.unmodifiableList(otherRegexSteps), implicitExts);
	}

	private static final Pattern SPECIAL_SEQUENCE_PATTERN = Pattern.compile("(\\*)|(\\?)|(.+)");

	private static boolean isFixedSegment(String segment) {
		Matcher m = SPECIAL_SEQUENCE_PATTERN.matcher(segment);
		if (m.find()) {
			return m.group(3) != null && m.end() == segment.length();
		}
		return true;
	}

	private static String translateSegmentToRegex(String segment) {
		StringBuilder s = new StringBuilder();
		Matcher m = SPECIAL_SEQUENCE_PATTERN.matcher(segment);
		while (m.find()) {
			if (m.group(3) != null) { // literal
				s.append(Pattern.quote(m.group(3)));
			} else if (m.group(2) != null) { // one character
				s.append("[^/]");
			} else if (m.group(1) != null) { // many characters
				s.append("[^/]*");
			}
		}
		return s.toString();
	}

}
