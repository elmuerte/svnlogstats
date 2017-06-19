package com.mpobjects.svn.logstats;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.LogOutputStream;
import org.apache.commons.exec.PumpStreamHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SvnLog {

	private static final Logger LOG = LoggerFactory.getLogger(SvnLog.class);

	public static void main(String[] args) throws Exception {
		SvnLog svnlog = new SvnLog();
		svnlog.exec(args);
	}

	public SvnLog() {
	}

	public void exec(String[] aArgs) throws Exception {
		CommandLine cmd = new CommandLine("svn");
		cmd.addArgument("log");
		cmd.addArguments("-v");
		cmd.addArguments("--diff");
		cmd.addArguments("--extensions");
		cmd.addArguments("-w"); // ignore all whitespace
		cmd.addArguments(aArgs);

		LOG.debug("Cmd: {}", cmd);

		final Configuration config = new Configurations().properties("settings.properties");

		final RevisionReporter reporter = createReporter(config);
		final SvnLogParser parser = new SvnLogParser(reporter);

		DefaultExecutor exec = new DefaultExecutor();
		exec.setStreamHandler(new PumpStreamHandler(new LogOutputStream() {
			@Override
			protected void processLine(String aLine, int aLogLevel) {
				parser.parse(aLine);
			}
		}, System.err));
		exec.execute(cmd);
		parser.flush();
	}

	private RevisionReporter createReporter(Configuration aConfig) throws FileNotFoundException, RevisionReporterException {
		// this clearly needs to become better
		final String fmt = aConfig.getString("output.format", "csv");
		if ("csv".equals(fmt)) {
			return new CsvRevisionReporter(new PrintWriter(new File(aConfig.getString("output", "output.csv"))), aConfig);
		}
		return null;
	}

}
