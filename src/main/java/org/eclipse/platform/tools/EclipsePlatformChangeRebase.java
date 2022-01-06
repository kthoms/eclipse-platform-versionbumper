package org.eclipse.platform.tools;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

import java.io.Console;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.urswolfer.gerrit.client.rest.GerritAuthData;
import com.urswolfer.gerrit.client.rest.GerritRestApiFactory;

import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.PicocliException;

public class EclipsePlatformChangeRebase {
	private static final String UID_PLATFORM_BOT = "platform-bot@eclipse.org";
	private static final Logger LOG = LoggerFactory.getLogger("main");

	@Option(names = { "-u", "--user" }, description = "Eclipse Gerrit User Id", required = true)
	private String user;
	@Option(names = { "-p", "--password" }, description = "Eclipse Gerrit Password", required = true)
	private String password;

	private String serverUri = "https://git.eclipse.org/r";

	private GerritApi gerritApi;
	private CredentialsProvider credentialsProvider;
	private Console console;

	void connect() {
		LOG.info("Connecting to Eclipse Gerrit");

		GerritRestApiFactory gerritRestApiFactory = new GerritRestApiFactory();
		GerritAuthData.Basic authData = new GerritAuthData.Basic(serverUri, user, password);
		gerritApi = gerritRestApiFactory.create(authData);

		credentialsProvider = new UsernamePasswordCredentialsProvider(user, password);
	}

	public static void main(String[] args) throws Exception {
		EclipsePlatformChangeRebase instance = new EclipsePlatformChangeRebase();
		try {
			var commandLine = new CommandLine(instance);
			var parseResult = commandLine.parseArgs(args);
			boolean helpPrinted = CommandLine.printHelpIfRequested(parseResult);
			if (!helpPrinted) {
				instance.run();
			}
		} catch (PicocliException ex) {
			LOG.error(ex.getMessage());
		}
	}

	void run() {
		console = System.console();

		try {
			connect();
			
			var candidates = findChangeCandidatesForRebase();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	List<ChangeInfo> findChangeCandidatesForRebase() throws RestApiException {
		var changes = gerritApi.changes().query("projects:platform+status:open+label:Verified=-1,user=platform-bot@eclipse.org").get();
		if (changes.isEmpty()) {
			LOG.info("No open changes found. Strange...");
			return emptyList();
		}
		LOG.info("The following changes are OK, but have Verified-1 due to code freeze:");
		List<ChangeInfo> candidates = changes.stream()
				.filter(this::isLastVerifyFailureDueToCodeFreeze)
				.peek(change -> LOG.info("- {}/c/{}/+/{}", serverUri, change.project, change._number))
				.collect(toList());
		return candidates;
	}
	
	boolean isLastVerifyFailureDueToCodeFreeze (ChangeInfo changeInfo) {
		//var reviewersMap = changeInfo.reviewerUpdates();
		ChangeApi change;
		try {
			change = gerritApi.changes().id(changeInfo.id);
			//var rc = change.robotComments();
			// var com = change.commentsAsList();
			var revs = change.listReviewers();
			var botMessages = change.messages().stream()
				.filter(m -> UID_PLATFORM_BOT.equals(m.author.email))
				.collect(Collectors.toList());
			
			var lastMessage = botMessages.get(botMessages.size()-1);

			return lastMessage.message.contains("Build and test are OK");
		} catch (RestApiException e) {
			throw new RuntimeException(e);
		}
	}

}
