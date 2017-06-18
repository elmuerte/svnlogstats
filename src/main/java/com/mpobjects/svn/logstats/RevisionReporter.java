package com.mpobjects.svn.logstats;

import javax.annotation.Nonnull;

import com.mpobjects.svn.logstats.model.Revision;

public interface RevisionReporter {
	void report(@Nonnull Revision aRevision) throws RevisionReporterException;
}
