package com.mpobjects.svn.logstats.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.joda.time.DateTime;

public class Revision {
	protected String author;

	protected String comment;

	protected Map<String, FileChange> fileChanges;

	protected int id;

	protected Set<String> issues;

	protected MergeStatus mergeStatus;

	protected Set<String> projects;

	protected DateTime timestamp;

	public Revision(int aId, String aAuthor, DateTime aTimestamp) {
		id = aId;
		author = aAuthor;
		timestamp = aTimestamp;
		fileChanges = new HashMap<>();
		issues = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		projects = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		mergeStatus = MergeStatus.NORMAL;
	}

	public void addFileChange(FileChange aFileChange) {
		if (fileChanges.containsKey(aFileChange.getFilename())) {
			// TODO: error because duplicate
		}
		fileChanges.put(aFileChange.getFilename(), aFileChange);
	}

	public String getAuthor() {
		return author;
	}

	public String getComment() {
		return comment;
	}

	public Map<String, FileChange> getFileChanges() {
		return fileChanges;
	}

	public Stream<FileChange> getFileChanges(ChangeType aChnageType) {
		return fileChanges.values().stream().filter(c -> aChnageType.equals(c.getChangeType()));
	}

	public int getId() {
		return id;
	}

	public Set<String> getIssues() {
		return issues;
	}

	public int getLinesAdded() {
		int cnt = 0;
		for (FileChange chng : fileChanges.values()) {
			cnt += chng.getLinesAdded();
		}
		return cnt;
	}

	public int getLinesAdded(Predicate<? super FileChange> predicate) {
		AtomicInteger res = new AtomicInteger();
		fileChanges.values().stream().filter(predicate).forEach(c -> res.getAndAdd(c.getLinesAdded()));
		return res.get();
	}

	public int getLinesChanged() {
		int cnt = 0;
		for (FileChange chng : fileChanges.values()) {
			cnt += chng.getLinesChanged();
		}
		return cnt;
	}

	public int getLinesChanged(Predicate<? super FileChange> predicate) {
		AtomicInteger res = new AtomicInteger();
		fileChanges.values().stream().filter(predicate).forEach(c -> res.getAndAdd(c.getLinesChanged()));
		return res.get();
	}

	public int getLinesRemoved() {
		int cnt = 0;
		for (FileChange chng : fileChanges.values()) {
			cnt += chng.getLinesRemoved();
		}
		return cnt;
	}

	public int getLinesRemoved(Predicate<? super FileChange> predicate) {
		AtomicInteger res = new AtomicInteger();
		fileChanges.values().stream().filter(predicate).forEach(c -> res.getAndAdd(c.getLinesRemoved()));
		return res.get();
	}

	public MergeStatus getMergeStatus() {
		return mergeStatus;
	}

	public Set<String> getProjects() {
		return projects;
	}

	public DateTime getTimestamp() {
		return timestamp;
	}

	public void setComment(String aComment) {
		comment = aComment;
	}

	public void setMergeStatus(MergeStatus mergeStatus) {
		this.mergeStatus = mergeStatus;
	}

	@Override
	public String toString() {
		ToStringBuilder sb = new ToStringBuilder(this, ToStringStyle.NO_CLASS_NAME_STYLE);
		sb.append("id", id);
		sb.append("author", author);
		sb.append("timestamp", timestamp);
		sb.append("mergeStatus", mergeStatus);
		sb.append("projects", projects);
		sb.append("issues", issues);
		sb.append("no. files", fileChanges.size());
		return sb.toString();
	}
}
