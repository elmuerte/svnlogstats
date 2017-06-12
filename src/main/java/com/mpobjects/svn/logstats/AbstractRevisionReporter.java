package com.mpobjects.svn.logstats;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.configuration2.Configuration;

public abstract class AbstractRevisionReporter implements RevisionReporter {

	protected static class FileGroup {
		protected String name;
		protected Set<String> suffixes;

		public FileGroup(String aName, Set<String> aSuffixes) {
			name = aName;
			suffixes = aSuffixes;
		}

		public String getName() {
			return name;
		}

		public Set<String> getSuffixes() {
			return suffixes;
		}

		public boolean matches(String aFilename) {
			for (String suffix : suffixes) {
				if (aFilename.endsWith(suffix)) {
					return true;
				}
			}
			return false;
		}
	}

	protected final Configuration config;

	protected List<FileGroup> fileGroups;

	protected Pattern projectPattern;

	public AbstractRevisionReporter(Configuration aConfig) {
		config = aConfig;
		initConfig();
	}

	protected void initConfig() {
		if (config.containsKey("pattern.project")) {
			projectPattern = Pattern.compile(config.getString("pattern.project"));
		}

		fileGroups = new ArrayList<>();
	}

}