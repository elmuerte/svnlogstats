package com.mpobjects.svn.logstats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

public abstract class AbstractRevisionReporter implements RevisionReporter {

	protected static class FileGroup {
		protected String name;
		protected Set<String> suffixes;

		public FileGroup(String aName, Set<String> aSuffixes) {
			name = StringUtils.defaultIfBlank(aName, "unnamed");
			suffixes = ObjectUtils.defaultIfNull(aSuffixes, Collections.emptySet());
		}

		@Nonnull
		public String getName() {
			return name;
		}

		@Nonnull
		public Set<String> getSuffixes() {
			return suffixes;
		}

		public boolean matches(String aFilename) {
			for (String suffix : suffixes) {
				if (StringUtils.endsWithIgnoreCase(aFilename, suffix)) {
					return true;
				}
			}
			return false;
		}
	}

	protected final Configuration config;

	protected List<FileGroup> fileGroups;

	protected Pattern projectPattern;

	public AbstractRevisionReporter(@Nonnull Configuration aConfig) {
		config = aConfig;
		initConfig();
	}

	protected void initConfig() {
		if (config.containsKey("pattern.project")) {
			projectPattern = Pattern.compile(config.getString("pattern.project"));
		}

		fileGroups = new ArrayList<>();
		for (String groupId : config.getList(String.class, "filegroups", Collections.emptyList())) {
			if (StringUtils.isBlank(groupId)) {
				continue;
			}
			fileGroups.add(loadFileGroup(groupId.trim()));
		}
	}

	@Nonnull
	protected FileGroup loadFileGroup(String aGroupId) {
		Set<String> suffixes = new HashSet<>(config.getList(String.class, "filegroup." + aGroupId, Collections.emptyList()));
		return new FileGroup(aGroupId, suffixes);
	}

}