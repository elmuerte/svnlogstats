package com.mpobjects.svn.logstats;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import com.mpobjects.svn.logstats.model.ChangeType;
import com.mpobjects.svn.logstats.model.FileChange;
import com.mpobjects.svn.logstats.model.Revision;

public class CsvRevisionReporter extends AbstractRevisionReporter {

	private static final int IDX_ISSUES = 7;
	private static final int IDX_PROJECTS = 8;

	protected boolean normalizeIssues;

	protected CSVPrinter output;

	public CsvRevisionReporter(@Nonnull Appendable aOutput, @Nonnull Configuration aConfig) throws RevisionReporterException {
		super(aConfig);
		output = createOutput(aOutput);
	}

	@Override
	public void flush() throws RevisionReporterException {
		try {
			output.flush();
		} catch (IOException e) {
			throw new RevisionReporterException("Failure writing CSV record.", e);
		}
	}

	@Override
	public void report(@Nonnull Revision aRevision) throws RevisionReporterException {
		super.report(aRevision);
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

	@Nonnull
	protected Object[] createEntry(@Nonnull Revision aRevision) {
		List<Object> entry = new ArrayList<>();

		entry.add(aRevision.getId());
		entry.add(aRevision.getAuthor());
		entry.add(aRevision.getTimestamp());
		entry.add(aRevision.getTimestamp().toLocalDate());
		entry.add(aRevision.getTimestamp().toLocalTime());

		entry.add(aRevision.getMergeStatus());
		// Not completely reliable, large than 0 is not a valid criteria
		if (isBranchActions(aRevision)) {
			entry.add("TRUE");
		} else {
			entry.add("FALSE");
		}

		entry.add(StringUtils.join(aRevision.getIssues(), ','));
		entry.add(StringUtils.join(aRevision.getProjects(), ','));

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

	protected CSVPrinter createOutput(@Nonnull Appendable aOutput) throws RevisionReporterException {
		try {
			return new CSVPrinter(aOutput, getCsvFormat());
		} catch (IOException e) {
			throw new RevisionReporterException("Failure to create CSVPrinter.", e);
		}
	}

	@Nonnull
	protected CSVFormat getCsvFormat() {
		CSVFormat format = CSVFormat.valueOf(config.getString("csv.format", CSVFormat.Predefined.RFC4180.name()));
		if (config.getBoolean("csv.withheader", true)) {
			format = format.withHeader(getHeader());
		}
		return format;
	}

	@Nonnull
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

	protected void reportNormalized(@Nonnull Revision aRevision, @Nonnull Object[] entry) throws IOException {
		output.printRecord(ArrayUtils.add(entry, 0, "Combined"));
		// Report per project
		for (String project : aRevision.getProjects()) {
			entry[IDX_ISSUES] = StringUtils
					.join(aRevision.getIssues().stream().filter(i -> i.replaceFirst(projectPattern.pattern(), "$1").equals(project)).iterator(), ',');
			entry[IDX_PROJECTS] = project;
			output.printRecord(ArrayUtils.add(entry, 0, "Project"));
		}
		// Report per issue
		for (String issue : aRevision.getIssues()) {
			entry[IDX_ISSUES] = issue;
			if (projectPattern != null) {
				entry[IDX_PROJECTS] = projectPattern.matcher(issue).replaceFirst("$1");
			}
			output.printRecord(ArrayUtils.add(entry, 0, "Issue"));
		}
	}

	/**
	 * Try to determine if it was a branch action (create, delete, move)
	 *
	 * @param aRevision
	 * @return
	 */
	private boolean isBranchActions(Revision aRevision) {
		final Set<FileChange> manifest = aRevision.getFileChanges().values().stream().filter(c -> c.isInManifest()).collect(Collectors.toSet());
		if (manifest.size() == aRevision.getFileChanges().size()) {
			// everything was known, can't be a branch action
			return false;
		}
		if (manifest.size() != manifest.stream()
				.filter(c -> c.getLinesChanged() == 0 && !c.isBinary() && (!c.getChangeType().equals(ChangeType.ADDED) || c.getFromRevision() > 0)).count()) {
			// not all are directories
			// or not copied from
			return false;
		}

		Iterator<FileChange> it = aRevision.getFileChanges().values().stream().filter(c -> !c.isInManifest()).iterator();
		while (it.hasNext()) {
			final FileChange change = it.next();
			Optional<FileChange> manEntry = manifest.stream()
					.filter(e -> change.getFilename().startsWith(e.getFilename()) && change.getChangeType().equals(e.getChangeType())).findFirst();
			if (!manEntry.isPresent()) {
				// non-manifest entry was not in the manifest with the same change type
				return false;
			}
			// Keep count?
		}

		// at this point, it might be a branch action
		// TODO: branch creation looks ok, deleting gives false positives (e.g. 28040)
		return true;
	}
}
