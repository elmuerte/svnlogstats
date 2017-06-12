package com.mpobjects.svn.logstats;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.StringUtils;

import com.mpobjects.svn.logstats.model.ChangeType;
import com.mpobjects.svn.logstats.model.FileChange;
import com.mpobjects.svn.logstats.model.Revision;

public class CsvRevisionReporter extends AbstractRevisionReporter {

	private static final int IDX_ISSUES = 0;
	private static final int IDX_PROJECTS = 0;

	protected boolean normalizeIssues;

	protected CSVPrinter output;

	public CsvRevisionReporter(Appendable aOutput, Configuration aConfig) throws RevisionReporterException {
		super(aConfig);
		output = createOutput(aOutput);
	}

	@Override
	public void report(Revision aRevision) throws RevisionReporterException {
		Object[] entry = createEntry(aRevision);

		try {
			if (!normalizeIssues) {
				output.printRecord(entry);
			} else {
				reportNormalized(aRevision, entry);
			}
		} catch (IOException e) {
			throw new RevisionReporterException("Failure writing CSV record.", e);
		}
	}

	protected Object[] createEntry(Revision aRevision) {
		List<Object> entry = new ArrayList<>();

		entry.add(aRevision.getId());
		entry.add(aRevision.getAuthor());
		entry.add(aRevision.getTimestamp());
		entry.add(aRevision.getTimestamp().toLocalDate());
		entry.add(aRevision.getTimestamp().toLocalTime());

		entry.add(aRevision.getMergeStatus());
		// Not completely reliable, large than 0 is not a valid criteria
		if (aRevision.getFileChanges().values().stream().filter(c -> !c.isInManifest()).count() > 0) {
			entry.add("TRUE");
		} else {
			entry.add("FALSE");
		}

		entry.add(StringUtils.join(aRevision.getProjects(), ','));
		entry.add(StringUtils.join(aRevision.getIssues(), ','));

		entry.add(aRevision.getFileChanges(ChangeType.ADDED).count());
		entry.add(aRevision.getFileChanges(ChangeType.DELETED).count());
		entry.add(aRevision.getFileChanges(ChangeType.MODIFIED).count());
		entry.add(aRevision.getFileChanges(ChangeType.REPLACED).count());

		entry.add(aRevision.getFileChanges().size());

		entry.add(aRevision.getLinesAdded());
		entry.add(aRevision.getLinesRemoved());
		entry.add(aRevision.getLinesChanged());

		for (FileGroup fileGroup : fileGroups) {
			Predicate<? super FileChange> predicate = c -> fileGroup.matches(c.getFilename());

			entry.add(aRevision.getFileChanges().values().stream().filter(predicate).count());
			entry.add(aRevision.getLinesAdded(predicate));
			entry.add(aRevision.getLinesRemoved(predicate));
			entry.add(aRevision.getLinesChanged(predicate));
		}

		return entry.toArray();
	}

	protected CSVPrinter createOutput(Appendable aOutput) throws RevisionReporterException {
		try {
			return new CSVPrinter(aOutput, getCsvFormat());
		} catch (IOException e) {
			throw new RevisionReporterException("Failure to create CSVPrinter.", e);
		}
	}

	protected CSVFormat getCsvFormat() {
		CSVFormat format = CSVFormat.valueOf(config.getString("csv.format", CSVFormat.Predefined.RFC4180.name()));
		if (config.getBoolean("csv.withheader", true)) {
			format.withHeader(getHeader());
		}
		return format;
	}

	protected String[] getHeader() {
		List<String> header = new ArrayList<>();

		if (normalizeIssues) {
			header.add("RecordType");
		}

		header.add("Revision");
		header.add("Author");
		header.add("Timestamp");
		header.add("Date");
		header.add("Time");

		header.add("Merge Status");
		header.add("Branch Action");

		header.add("Issues");
		header.add("Projects");

		header.add("Files Added");
		header.add("Files Removed");
		header.add("Files Modified");
		header.add("Files Replaced");
		header.add("Files Affected");

		header.add("Lines Added");
		header.add("Lines Removed");
		header.add("Lines Modified");

		for (FileGroup fileGroup : fileGroups) {
			header.add(fileGroup.getName() + " Files Affected");
			header.add(fileGroup.getName() + " Lines Added");
			header.add(fileGroup.getName() + " Lines Removed");
			header.add(fileGroup.getName() + " Lines Modified");
		}

		return header.toArray(new String[0]);
	}

	@Override
	protected void initConfig() {
		super.initConfig();
		normalizeIssues = config.getBoolean("csv.normalize.issues", false);
	}

	protected void reportNormalized(Revision aRevision, Object[] entry) throws IOException {
		output.print("Main");
		output.printRecord(entry);
		for (String issue : aRevision.getIssues()) {
			entry[IDX_ISSUES] = issue;
			if (projectPattern != null) {
				entry[IDX_PROJECTS] = projectPattern.matcher(issue).replaceFirst("$1");
			}
			output.print("Main");
			output.printRecord(entry);
		}
	}

}
