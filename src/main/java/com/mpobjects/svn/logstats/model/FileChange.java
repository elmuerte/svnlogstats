package com.mpobjects.svn.logstats.model;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class FileChange {

	protected boolean binary;

	protected ChangeType changeType;

	protected String filename;

	protected String fromPath;

	protected int fromRevision;

	/**
	 * True if this file change was in the manifest. False if it came from the diff.
	 */
	protected boolean inManifest;

	protected int linesAdded;

	protected int linesChanged;

	protected int linesRemoved;

	public FileChange(String aFilename, ChangeType aChangeType) {
		filename = aFilename;
		changeType = aChangeType;
		inManifest = true;
	}

	public ChangeType getChangeType() {
		return changeType;
	}

	public String getFilename() {
		return filename;
	}

	public String getFromPath() {
		return fromPath;
	}

	public int getFromRevision() {
		return fromRevision;
	}

	public int getLinesAdded() {
		return linesAdded;
	}

	public int getLinesChanged() {
		return linesChanged;
	}

	public int getLinesRemoved() {
		return linesRemoved;
	}

	public boolean isBinary() {
		return binary;
	}

	public boolean isInManifest() {
		return inManifest;
	}

	public void setBinary(boolean aBinary) {
		binary = aBinary;
	}

	public void setFromPath(String aFromPath) {
		fromPath = aFromPath;
	}

	public void setFromRevision(int aFromRevision) {
		fromRevision = aFromRevision;
	}

	public void setInManifest(boolean aInManifest) {
		inManifest = aInManifest;
	}

	public void setLinesAdded(int aLinesAdded) {
		linesAdded = aLinesAdded;
	}

	public void setLinesChanged(int aLinesChanged) {
		linesChanged = aLinesChanged;
	}

	public void setLinesRemoved(int aLinesRemoved) {
		linesRemoved = aLinesRemoved;
	}

	@Override
	public String toString() {
		ToStringBuilder sb = new ToStringBuilder(this, ToStringStyle.NO_CLASS_NAME_STYLE);
		sb.append("changeType", changeType);
		sb.append("filename", filename);
		sb.append("linesAdded", linesAdded);
		sb.append("linesRemoved", linesRemoved);
		sb.append("linesChanged", linesChanged);
		return sb.toString();
	}
}
