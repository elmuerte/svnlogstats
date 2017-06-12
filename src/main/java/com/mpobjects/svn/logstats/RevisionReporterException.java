package com.mpobjects.svn.logstats;

public class RevisionReporterException extends Exception {

	private static final long serialVersionUID = 1L;

	public RevisionReporterException(String message) {
		super(message);
	}

	public RevisionReporterException(String message, Throwable cause) {
		super(message, cause);
	}

	public RevisionReporterException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

}
