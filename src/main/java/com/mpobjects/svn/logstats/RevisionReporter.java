package com.mpobjects.svn.logstats;

import com.mpobjects.svn.logstats.model.Revision;

public interface RevisionReporter {
	void report(Revision aRevision) throws RevisionReporterException;
}
