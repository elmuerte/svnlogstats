package com.mpobjects.svn.logstats;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mpobjects.svn.logstats.model.ChangeType;
import com.mpobjects.svn.logstats.model.FileChange;
import com.mpobjects.svn.logstats.model.MergeStatus;
import com.mpobjects.svn.logstats.model.Revision;

public class SvnLogParser {
	private static class DiffState {
		int add;
		int del;
		int totalAdd;
		int totalDel;
		int totalMod;
	}

	private enum ParserState {
		COMMENT, DIFF, DIFF_PROPS, ENTRY, NEW, PATHS;
	}

	private static final String DIFF_BIN = "Cannot display: file marked as a binary type.";

	private static final String DIFF_INDEX = "Index: ";

	private static final Pattern DIFF_INDEX_PATTERN = Pattern.compile("^" + DIFF_INDEX + "(.*)");

	private static final String DIFF_PROPS_INDEX = "Property changes on: ";

	private static final Pattern DIFF_PROPS_INDEX_PATTERN = Pattern.compile("^" + DIFF_PROPS_INDEX + "(.*)");

	private static final Pattern FILE_COPY = Pattern.compile("(.*)( \\(from (.*):([0-9]+)\\))");

	private static final Pattern FILE_ENTRY = Pattern.compile("^   ([ADMR]) /(.*)");

	private static final Logger LOG = LoggerFactory.getLogger(SvnLogParser.class);

	private static final Pattern LOG_ENTRY = Pattern
			.compile("^r([0-9]+) \\| (.*) \\| ([0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2} [-+][0-9]{4}) .*\\| ([0-9]+) lines?");

	private static final String LOG_ENTRY_DIV = "------------------------------------------------------------------------";

	private static final String PATHS_HEADER = "Changed paths:";

	private static final DateTimeFormatter SVN_DATE_FORMAT = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss Z");

	private StringBuilder commentBuffer;

	/**
	 * Number of lines of comment
	 */
	private int commentLines;

	private FileChange currentFileChange;

	private Revision currentRevision;

	private DiffState diffState;

	private RevisionReporter reporter;

	private ParserState state;

	public SvnLogParser(RevisionReporter aReporter) {
		reporter = aReporter;
		state = ParserState.NEW;
	}

	public void flush() {
		reportCurrentRevision();
		try {
			reporter.flush();
		} catch (RevisionReporterException e) {
			LOG.error("Error reporting revision.", e);
		}
	}

	public void parse(String aLine) {
		if (LOG_ENTRY_DIV.equals(aLine)) {
			// always process this
			reportCurrentRevision();
			state = ParserState.ENTRY;
			return;
		}

		if (!ParserState.ENTRY.equals(state) && currentRevision == null) {
			LOG.error("Illegal state: {}", state);
			return;
		}

		switch (state) {
			case NEW:
				// just capture this too, but we should never get here
			case ENTRY:
				parseEntry(aLine);
				return;
			case PATHS:
				parsePaths(aLine);
				return;
			case COMMENT:
				parseComment(aLine);
				return;
			case DIFF:
				parseDiff(aLine);
				return;
			case DIFF_PROPS:
				parseDiffProps(aLine);
				return;
		}
	}

	protected boolean parseDiffContent(String aLine) {
		if (aLine.startsWith("---") || aLine.startsWith("+++") || aLine.startsWith("@@")
				|| "===================================================================".equals(aLine)) {
			// ignore unified diff header
			return true;
		}

		if (aLine.startsWith("-")) {
			diffState.del++;
			diffState.totalDel++;
			return true;
		} else if (aLine.startsWith("+")) {
			diffState.add++;
			diffState.totalAdd++;
			return true;
		} else if (aLine.startsWith(" ")) {
			if (diffState.add > 0 || diffState.del > 0) {
				diffState.totalMod += Math.max(diffState.add, diffState.del);
				diffState.add = 0;
				diffState.del = 0;
			}
			return true;
		} else if (aLine.equals(DIFF_BIN)) {
			currentFileChange.setBinary(true);
		}

		return false;
	}

	private void appyDiffState() {
		if (currentFileChange != null && diffState != null) {
			if (diffState.add > 0 || diffState.del > 0) {
				diffState.totalMod += Math.max(diffState.add, diffState.del);
			}
			currentFileChange.setLinesAdded(diffState.totalAdd);
			currentFileChange.setLinesRemoved(diffState.totalDel);
			currentFileChange.setLinesChanged(diffState.totalMod);
		}
		currentFileChange = null;
		diffState = null;
	}

	private void parseComment(String aLine) {
		if (commentLines-- <= 0) {
			// note: also eat next blank line
			if (!"".equals(aLine)) {
				LOG.error("Comment was not followed with blank line.");
			}
			currentRevision.setComment(commentBuffer.toString());
			commentBuffer = null;
			state = ParserState.DIFF;
			return;
		}
		if (commentBuffer == null) {
			commentBuffer = new StringBuilder();
		} else {
			commentBuffer.append('\n');
		}
		commentBuffer.append(aLine);
	}

	private void parseDiff(String aLine) {
		if ("".equals(aLine)) {
			// end of diff processing
			appyDiffState();
			state = ParserState.DIFF_PROPS;
			return;
		}

		if (currentFileChange != null) {
			if (parseDiffContent(aLine)) {
				return;
			}
		}

		Matcher matcher = DIFF_INDEX_PATTERN.matcher(aLine);
		if (matcher.matches()) {
			appyDiffState();

			String filename = matcher.group(1);
			if (StringUtils.isBlank(filename)) {
				return;
			}
			ChangeType changeType = ChangeType.ADDED;
			if (filename.endsWith(" (deleted)")) {
				filename = StringUtils.substringBefore(filename, " (deleted)");
				changeType = ChangeType.DELETED;
			}
			currentFileChange = currentRevision.getFileChanges().get(filename);
			if (currentFileChange == null) {
				LOG.info("Unreported file in diff (type {}): {}", changeType, filename);
				// If not found it was part of a big add/delete
				currentFileChange = new FileChange(filename, changeType);
				currentFileChange.setInManifest(false);
				currentRevision.addFileChange(currentFileChange);
			}
			diffState = new DiffState();
			LOG.debug("Processing diff in rev {} for: {}", currentRevision.getId(), currentFileChange.getFilename());
			return;
		}
	}

	private void parseDiffProps(String aLine) {
		if (aLine.startsWith(DIFF_INDEX)) {
			if (DIFF_INDEX_PATTERN.matcher(aLine).matches()) {
				currentFileChange = null;
				state = ParserState.DIFF;
				parseDiff(aLine);
				return;
			}
		}

		if (aLine.startsWith(DIFF_PROPS_INDEX)) {
			Matcher matcher = DIFF_PROPS_INDEX_PATTERN.matcher(aLine);
			if (matcher.matches()) {
				currentFileChange = currentRevision.getFileChanges().get(matcher.group(1));
				return;
			}
		}

		if (currentFileChange == null) {
			return;
		}

		if ("___________________________________________________________________".equals(aLine)) {
			// ignore divider
			return;
		}

		if (currentFileChange.isInManifest() && ("Modified: svn:mergeinfo".equals(aLine) || "Added: svn:mergeinfo".equals(aLine))) {
			// if these are added/updated then the file is merged
			currentRevision.setMergeStatus(MergeStatus.MERGED);
		}
	}

	private void parseEntry(String aLine) {
		Matcher matcher = LOG_ENTRY.matcher(aLine);
		if (!matcher.matches()) {
			return;
		}

		if (currentRevision != null) {
			LOG.error("Found new revision while still processing a revision");
		}
		currentRevision = new Revision(NumberUtils.toInt(matcher.group(1)), matcher.group(2), SVN_DATE_FORMAT.parseDateTime(matcher.group(3)));
		commentLines = NumberUtils.toInt(matcher.group(4));
		state = ParserState.PATHS;
	}

	private void parsePaths(String aLine) {
		if (PATHS_HEADER.equals(aLine)) {
			// Paths header, just ignore
			return;
		}
		if ("".equals(aLine)) {
			// blank line = end of record
			state = ParserState.COMMENT;
			return;
		}
		Matcher matcher = FILE_ENTRY.matcher(aLine);
		if (matcher.matches()) {
			String filename = matcher.group(2);
			Matcher m2 = FILE_COPY.matcher(filename);
			if (m2.matches()) {
				filename = m2.group(1);
			}
			FileChange chng = new FileChange(filename, ChangeType.get(matcher.group(1)));
			if (m2.matches()) {
				chng.setFromPath(m2.group(3));
				chng.setFromRevision(NumberUtils.toInt(m2.group(4)));
			}
			currentRevision.addFileChange(chng);
		} else {
			LOG.error("Garbage path entry: {}", aLine);
		}
	}

	private void reportCurrentRevision() {
		try {
			if (currentRevision == null || reporter == null) {
				return;
			}
			try {
				reporter.report(currentRevision);
			} catch (RevisionReporterException e) {
				LOG.error("Error reporting revision.", e);
			}
		} finally {
			currentRevision = null;
			state = ParserState.NEW;
		}
	}
}
