package com.mpobjects.svn.logstats.model;

import org.apache.commons.lang3.StringUtils;

public enum ChangeType {
	ADDED('A'), MODIFIED('M'), DELETED('D'), REPLACED('R');

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
