package com.mpobjects.svn.logstats.model;

import javax.annotation.CheckForNull;

import org.apache.commons.lang3.StringUtils;

public enum ChangeType {
	ADDED('A'), DELETED('D'), MODIFIED('M'), REPLACED('R');

	@CheckForNull
	public static final ChangeType get(char aCode) {
		switch (aCode) {
			case 'A':
				return ChangeType.ADDED;
			case 'D':
				return ChangeType.DELETED;
			case 'M':
				return ChangeType.MODIFIED;
			case 'R':
				return ChangeType.REPLACED;
		}
		return null;
	}

	@CheckForNull
	public static final ChangeType get(String aCode) {
		if (StringUtils.length(aCode) != 1) {
			return null;
		}
		return get(aCode.charAt(0));
	}

	private final char code;

	private ChangeType(char aCode) {
		code = aCode;
	}

	public char getCode() {
		return code;
	}
}
