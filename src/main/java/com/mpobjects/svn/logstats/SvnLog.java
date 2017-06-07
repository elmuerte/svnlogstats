package com.mpobjects.svn.logstats;

import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.LogOutputStream;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.io.FilenameUtils;
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

public class SvnLog {
	private static class DiffState {
		int add;
		int del;
		int totalAdd;
		int totalDel;
		int totalMod;
	}

	private enum ParseState {
		COMMENT, DIFF, DIFF_PROPS, ENTRY, NEW, PATHS;
	}

	public static final Set<String> CODE_FILE_EXT;

	public static final String DIFF_INDEX = "Index: ";

	public static final Pattern DIFF_INDEX_PATTERN = Pattern.compile("^" + DIFF_INDEX + "(.*)");

	public static final String DIFF_PROPS_INDEX = "Property changes on: ";

	public static final Pattern DIFF_PROPS_INDEX_PATTERN = Pattern.compile("^" + DIFF_PROPS_INDEX + "(.*)");

	public static final Pattern FILE_COPY = Pattern.compile("(.*)( \\(from .*:[0-9]+\\))");

	public static final Pattern FILE_ENTRY = Pattern.compile("^   ([ADMR]) /(.*)");

	public static final Pattern ISSUE_PATTERN = Pattern.compile("([a-zA-Z][a-zA-Z]+-[1-9][0-9]*)([^.]|\\.[^0-9]|\\.$|$)");

	public static final Pattern LOG_ENTRY = Pattern
			.compile("^r([0-9]+) \\| (.*) \\| ([0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2} [-+][0-9]{4}) .*\\| ([0-9]+) lines?");

	public static final String LOG_ENTRY_DIV = "------------------------------------------------------------------------";

	public static final String PATHS_HEADER = "Changed paths:";

	public static final DateTimeFormatter SVN_DATE_FORMAT = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss Z");

	private static final Logger LOG = LoggerFactory.getLogger(SvnLog.class);

	protected StringBuilder commentBuffer;

	protected int commentLines;

	protected FileChange currentFileChange;

	protected Revision currentRevision;

	protected DiffState diffState;

	protected boolean explodeIssues = true;

	protected CSVPrinter output;

	protected boolean outputHeader = true;

	protected ParseState state = ParseState.NEW;

	static {
		CODE_FILE_EXT = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		CODE_FILE_EXT.add("java");
		CODE_FILE_EXT.add("js");
		CODE_FILE_EXT.add("xml");
		CODE_FILE_EXT.add("jsp");
		CODE_FILE_EXT.add("html");
		CODE_FILE_EXT.add("sql");
	}

	public SvnLog() {
	}

	public static void main(String[] args) throws Exception {
		SvnLog svnlog = new SvnLog();
		svnlog.exec(args);
	}

	public void exec(String[] aArgs) throws ExecuteException, IOException {
		output = new CSVPrinter(System.out, CSVFormat.RFC4180);
		if (outputHeader) {
			printHeader();
		}

		CommandLine cmd = new CommandLine("svn");
		cmd.addArgument("log");
		cmd.addArguments("-v");
		cmd.addArguments("--diff");
		cmd.addArguments("--extensions");
		cmd.addArguments("-w"); // ignore all whitespace
		cmd.addArguments(aArgs);

		LOG.debug("Cmd: {}", cmd);

		DefaultExecutor exec = new DefaultExecutor();
		exec.setStreamHandler(new PumpStreamHandler(new LogOutputStream() {
			@Override
			protected void processLine(String aLine, int aLogLevel) {
				try {
					SvnLog.this.processLine(aLine);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}, System.err));
		exec.execute(cmd);

		report(currentRevision);

		output.flush();
	}

	/**
	 * @param aLine
	 */
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
		}

		return false;
	}

	protected void printHeader() throws IOException {
		output.print("Revision");
		output.print("Timestamp");
		output.print("Author");
		output.print("Date");
		output.print("Time");

		output.print("Merge Status");
		output.print("Branch Act");

		output.print("Files Added");
		output.print("Files Removed");
		output.print("Files Modified");
		output.print("Files Replaced");

		output.print("Files Affected");
		output.print("Code Files Affected");

		output.print("Lines Added");
		output.print("Lines Removed");
		output.print("Lines Modified");

		output.print("Code Lines Added");
		output.print("Code Lines Removed");
		output.print("Code Lines Modified");

		output.print("Projects");
		output.print("Project Count");
		output.print("Issues");
		output.print("Issue Count");

		output.println();
	}

	protected void processLine(String aLine) throws IOException {
		if (LOG_ENTRY_DIV.equals(aLine)) {
			// always process this
			report(currentRevision);
			currentRevision = null;
			state = ParseState.ENTRY;
			return;
		}

		if (!ParseState.ENTRY.equals(state) && currentRevision == null) {
			LOG.error("Illegal state: {}", state);
			return;
		}

		switch (state) {
			case NEW:
				// just capture this too, but whe should never get here
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

	protected void report(Revision aRevision) throws IOException {
		if (aRevision == null) {
			return;
		}

		postProcess(aRevision);

		LOG.info("Processed revision {}", aRevision);

		output.print(aRevision.getId());
		output.print(aRevision.getTimestamp());
		output.print(aRevision.getAuthor());
		output.print(aRevision.getTimestamp().toLocalDate());
		output.print(aRevision.getTimestamp().toLocalTime());

		output.print(aRevision.getMergeStatus());
		// Not completely reliable
		if (aRevision.getFileChanges().values().stream().filter(c -> !c.isInManifest()).count() > 0) {
			output.print("TRUE");
		} else {
			output.print("FALSE");
		}

		output.print(aRevision.getFileChanges(ChangeType.ADDED).count());
		output.print(aRevision.getFileChanges(ChangeType.DELETED).count());
		output.print(aRevision.getFileChanges(ChangeType.MODIFIED).count());
		output.print(aRevision.getFileChanges(ChangeType.REPLACED).count());

		output.print(aRevision.getFileChanges().size());
		output.print(aRevision.getFileChanges().values().stream().filter(c -> CODE_FILE_EXT.contains(FilenameUtils.getExtension(c.getFilename()))).count());

		output.print(aRevision.getLinesAdded());
		output.print(aRevision.getLinesRemoved());
		output.print(aRevision.getLinesChanged());

		output.print(aRevision.getLinesAdded(c -> CODE_FILE_EXT.contains(FilenameUtils.getExtension(c.getFilename()))));
		output.print(aRevision.getLinesRemoved(c -> CODE_FILE_EXT.contains(FilenameUtils.getExtension(c.getFilename()))));
		output.print(aRevision.getLinesChanged(c -> CODE_FILE_EXT.contains(FilenameUtils.getExtension(c.getFilename()))));

		output.print(StringUtils.join(aRevision.getProjects(), ','));
		output.print(aRevision.getProjects().size());
		output.print(StringUtils.join(aRevision.getIssues(), ','));
		output.print(aRevision.getIssues().size());

		output.println();
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
			state = ParseState.DIFF;
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
			state = ParseState.DIFF_PROPS;
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
				state = ParseState.DIFF;
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
			return;
		}

		if (currentFileChange.isInManifest() && ("Modified: svn:mergeinfo".equals(aLine) || "Added: svn:mergeinfo".equals(aLine))) {
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
		state = ParseState.PATHS;
	}

	private void parsePaths(String aLine) {
		if (PATHS_HEADER.equals(aLine)) {
			// Paths header, just ignore
			return;
		}
		if ("".equals(aLine)) {
			// blank line = end of record
			state = ParseState.COMMENT;
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
			currentRevision.addFileChange(chng);
		} else {
			LOG.error("Garbage file entry: {}", aLine);
		}
	}

	private void postProcess(Revision aRevision) {
		Matcher matcher = ISSUE_PATTERN.matcher(aRevision.getComment());
		while (matcher.find()) {
			String issueId = matcher.group(1).toUpperCase();

			if ("UTF-8".equals(issueId)) {
				// just too common an error
				continue;
			}

			String projectCode = StringUtils.substringBefore(issueId, "-");
			aRevision.getIssues().add(issueId);
			if (!StringUtils.isBlank(projectCode)) {
				aRevision.getProjects().add(projectCode);
			}
		}
		if (MergeStatus.NORMAL.equals(aRevision.getMergeStatus())) {
			if (StringUtils.containsIgnoreCase(aRevision.getComment(), "Merged revision(s)")) {
				aRevision.setMergeStatus(MergeStatus.UNSURE);
			}
		}
	}
}
