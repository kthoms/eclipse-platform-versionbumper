package org.eclipse.platform.tools;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Console;
import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.jar.Manifest;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.osgi.framework.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.api.changes.RebaseInput;
import com.google.gerrit.extensions.api.changes.RevisionApi;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.urswolfer.gerrit.client.rest.GerritAuthData;
import com.urswolfer.gerrit.client.rest.GerritRestApiFactory;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.PicocliException;

@Command(name = Info.COMMANDNAME, footer = "\n" + Info.COPYRIGHT,
description = "\nCreate version bump changes for Eclipse Platform changes provided on the Eclipse Gerrit server.\n")
public class EclipsePlatformVersionBumper {
	private static final Logger LOG = LoggerFactory.getLogger("main");
	private GerritApi gerritApi;

	@Option(names = { "-c", "--changes" }, description = "List of Gerrit change ids to process")
	private List<String> changeIds = new ArrayList<>();
	private String email;
	@Option(names = { "-u", "--user" }, description = "Eclipse Gerrit User Id", required = true)
	private String user;
	@Option(names = { "-p", "--password" }, description = "Eclipse Gerrit Password", required = true)
	private String password;
	@Option(names = { "-y",
			"--assume-yes" }, description = "Assume 'yes' when asked for confirmation, e.g. to push changes. Use this option for batch mode.")
	private boolean assumeYes;
	@Option(names = { "--help", "-h" }, usageHelp = true, description = "Display this help and exit")
	boolean help;
	private String serverUri = "https://git.eclipse.org/r";
	private String gitRoot = System.getProperty("user.home") + "/git";
	private CredentialsProvider credentialsProvider;
	private Console console;
	@Option(names = "--dryrun", defaultValue = "true", description="Use this option to commit changes locally only without publishing it to Gerrit.")
	boolean dryrun;

	static class BundleInfo {
		public String name;
		public File projectDir;
		public Path manifestPath;
		public Path pomPath;
		public Version currentVersion;
		public Version baselineVersion;
		public Version bumpedVersion;

		public BundleInfo(String name, File projectDir, Path manifestPath, Path pomPath) {
			this.name = name;
			this.projectDir = projectDir;
			this.manifestPath = manifestPath;
			this.pomPath = pomPath;
		}
	}

	public static void main(String[] args) throws Exception {
		EclipsePlatformVersionBumper instance = new EclipsePlatformVersionBumper();
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

		if (changeIds.isEmpty()) {
			if (console == null) {
				LOG.error("No system console available. Please add change ids as program argument.");
				return;
			}
			System.out.print("Please enter the change ids to process (space separated): ");
			String input = console.readLine();
			changeIds.addAll(Arrays.asList(input.split("\\s+")));
		}
		try {
			connect();

			for (var id : changeIds) {

				var change = getChange(id);

				var eclipseVersion = getCurrentEclipseVersion();

				var git = getGit(change);

				var bundleInfos = affectedBundles(change, git);

				fetchBundleVersions(change.info().project, bundleInfos, "master");
				fetchBundleVersions(change.info().project, bundleInfos, getBaselineBranch(eclipseVersion));

				boolean anyBumped = fetchVersionBumps(bundleInfos);

				if (bundleInfos.isEmpty() || !anyBumped) {
					LOG.info("Versions are up-to-date, no change is created");
				} else {
					createChange(git, change, bundleInfos, eclipseVersion);
					var changeId = pushChange(git, change);

					changeId.ifPresent(rebaseOnId -> rebaseChange(id, rebaseOnId));
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	String getGitRoot() {
		return gitRoot;
	}

	private Git getGit(ChangeApi change) throws Exception {
		File repoDir = getRepoDir(change.info().project);
		try {
			LOG.info("Repository exists at " + repoDir.getPath());
			Git git = Git.open(repoDir);
			Status status = git.status().call();
			if (!status.isClean()) {
				// TODO Add option for auto-reset, or ask user
				// LOG.error("Repository is not clean. Exiting.");
				// System.exit(-1);
				LOG.warn("Repository is not clean. Performing hard reset.");
				git.reset().setMode(ResetType.HARD).call();
			}
			var branch = git.getRepository().getBranch();
			LOG.info("Repository {} is on branch {}", repoDir.getPath(), branch);

			if (!"master".equals(branch)) {
				LOG.info("Switching to master");
				git.checkout().setName("master").call();
			}

			LOG.info("Pulling changes...");
			git.pull().setCredentialsProvider(credentialsProvider).call();

			return git;
		} catch (RepositoryNotFoundException e) {
			var cloneUri = getGitCloneUri(change);
			LOG.info("Cloning " + cloneUri + " to " + repoDir.getPath());
			Git git = Git.cloneRepository().setCredentialsProvider(credentialsProvider).setURI(cloneUri)
					.setDirectory(repoDir).call();
			// install hook for Gerrit Change Id
			Runtime.getRuntime().exec(String.format(
					"sh gitdir=$(git rev-parse --git-dir); scp -p -P 29418 %s@git.eclipse.org:hooks/commit-msg ${gitdir}/hooks/",
					user), new String[0], git.getRepository().getDirectory().getAbsoluteFile());
			return git;
		}
	}

	private File getRepoDir(String projectName) {
		return new File(getGitRoot(), projectName);
	}

	private String getGitCloneUri(ChangeApi change) throws Exception {
		return serverUri.replace("https://", "https://" + user + "@") + "/a/" + change.info().project;
	}

	void connect() {
		LOG.info("Connecting to Eclipse Gerrit");
		
		GerritRestApiFactory gerritRestApiFactory = new GerritRestApiFactory();
		GerritAuthData.Basic authData = new GerritAuthData.Basic(serverUri, user, password);
		gerritApi = gerritRestApiFactory.create(authData);

		credentialsProvider = new UsernamePasswordCredentialsProvider(user, password);
	}

	ChangeApi getChange(String id) throws RestApiException {
		LOG.info("Get change " + id + "...");
		var change = gerritApi.changes().id(id);
		LOG.info("  This change is for project " + change.info().project);
		return change;
	}

	Set<BundleInfo> affectedBundles(ChangeApi change, Git git) throws RestApiException {
		var bundles = new LinkedHashSet<BundleInfo>();
		RevisionApi revision = change.current();

		LOG.info("This change affects the following bundles:");

		var workTree = git.getRepository().getWorkTree();
		for (var path : revision.files().keySet()) {
			File f = new File(workTree, path).getParentFile();
			if (bundles.stream().noneMatch(s -> f.getAbsolutePath().contains("/" + s.name + "/"))) {
				getBundleInfo(git, f).ifPresent(bundles::add);
			}
		}

		bundles.stream().forEach(info -> LOG.info("- " + info.name));
		LOG.info("");

		return bundles;
	}

	/**
	 * Find the directory that contains the .project file
	 */
	Optional<BundleInfo> getBundleInfo(Git git, File f) {
		var workTree = git.getRepository().getWorkTree();
		while (!f.equals(workTree) && f.getParentFile() != null) {
			File projectFile = new File(f, ".project");
			if (projectFile.exists()) {
				File manifestFile = new File(f, "META-INF/MANIFEST.MF");
				Path manifestPath = manifestFile.exists() ? workTree.toPath().relativize(manifestFile.toPath()) : null;
				File pomFile = new File(f, "pom.xml");
				Path pomPath = pomFile.exists() ? workTree.toPath().relativize(pomFile.toPath()) : null;

				BundleInfo info = new BundleInfo(f.getName(), projectFile, manifestPath, pomPath);
				return Optional.of(info);
			} else {
				f = f.getParentFile();
			}
		}

		return Optional.empty();
	}

	Optional<String> getFileContent(String projectName, Path path, String branch) throws Exception {
		var project = gerritApi.projects().name(projectName);
		try {
			BinaryResult file = project.branch(branch).file(path.toString());
			return Optional
					.of(file.isBase64() ? new String(Base64.getDecoder().decode(file.asString())) : file.asString());
		} catch (Exception e) {
			return Optional.empty();
		}
	}

	Version getCurrentEclipseVersion() throws Exception {
		var model = getModel("platform/eclipse.platform.releng.aggregator", "eclipse-platform-parent/pom.xml",
				"master");

		var version = Version.valueOf(model.getVersion().replace("-SNAPSHOT", ""));
		LOG.info("Eclipse baseline version on branch master is {}.{}", version.getMajor(), version.getMinor());
		return version;
	}

	Model getModel(String projectName, String pomPath, String branch) throws Exception {
		var project = gerritApi.projects().name(projectName);
		var pom = project.branch(branch).file(pomPath);

		var content = new String(Base64.getDecoder().decode(pom.asString()));

		var reader = new MavenXpp3Reader();
		return reader.read(new StringReader(content));
	}

	Optional<Manifest> getBundleManifest(String projectName, BundleInfo info, String branch) {
		try {
			var content = getFileContent(projectName, info.manifestPath, branch);
			if (content.isPresent()) {
				return Optional.of(new Manifest(new ByteArrayInputStream(content.get().getBytes())));
			}
		} catch (Exception e) {
		}
		return Optional.empty();
	}

	Version getVersion(Manifest mf) {
		return Version.valueOf(mf.getMainAttributes().getValue("Bundle-Version"));
	}

	String unqualifiedVersion(Version v) {
		return String.format("%d.%d.%d", v.getMajor(), v.getMinor(), v.getMicro());
	}

	void fetchBundleVersions(String projectName, Set<BundleInfo> bundleInfos, String branch) throws RestApiException {

		LOG.info("Bundle versions on branch " + branch + ":");
		bundleInfos.forEach(info -> {
			getBundleManifest(projectName, info, branch).ifPresent(mf -> {
				var version = getVersion(mf);
				if ("master".equals(branch)) {
					info.currentVersion = version;
				} else {
					info.baselineVersion = version;
				}
				LOG.info(String.format("- %s: %s", info.name, unqualifiedVersion(version)));
			});
		});

		LOG.info("");
	}

	String getBaselineBranch(Version version) {
		return String.format("R%d_%d_maintenance", version.getMajor(), version.getMinor() - 1);
	}

	boolean fetchVersionBumps(Set<BundleInfo> infos) {

		LOG.info("The following versions are bumped:");
		boolean anyBump = false;
		for (var info : infos) {
			var version = info.currentVersion;
			if (info.baselineVersion != null && version != null && Objects.equals(version, info.baselineVersion)) {
				String newVersion = String.format("%d.%d.%d.%s", version.getMajor(), version.getMinor(),
						version.getMicro() + 100, version.getQualifier());
				info.bumpedVersion = Version.valueOf(newVersion);
				anyBump = true;
				LOG.info(String.format("- %s: %s -> %s %s", info.name, unqualifiedVersion(version),
						unqualifiedVersion(info.bumpedVersion), info.pomPath == null ? "(POM-less)" : ""));
			}
		}
		if (!anyBump) {
			LOG.info("NONE");
		}

		LOG.info("");
		return anyBump;
	}

	void createChange(Git git, ChangeApi change, Set<BundleInfo> infos, Version eclipseVersion) throws Exception {
		LOG.info("Creating change");

		var branchName = String.format("change/%s/%d.%d-versionbump", change.id(), eclipseVersion.getMajor(),
				eclipseVersion.getMinor());
		LOG.info("Creating target branch " + branchName);
		git.branchCreate().setForce(true).setName(branchName).call();
		git.checkout().setName(branchName).call();

		var branch = git.getRepository().getBranch();
		LOG.info("On branch " + branch);

		infos.stream().filter(info -> info.bumpedVersion != null).forEach(info -> {
			try {
				bumpManifest(git, info);
				bumpPOM(git, info);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
		git.add().addFilepattern("**/pom.xml").addFilepattern("**/MANIFEST.MF").call();

		var message = getCommitMessage(git, change, infos, eclipseVersion);
		git.commit().setMessage(message.toString()).call();
	}

	String getCommitMessage(Git git, ChangeApi change, Set<BundleInfo> infos, Version eclipseVersion)
			throws RestApiException {
		StringBuilder message = new StringBuilder();
		message.append(String.format("%d.%d version bump", eclipseVersion.getMajor(), eclipseVersion.getMinor()));
		if (infos.size() == 1) {
			message.append(" for ").append(infos.iterator().next().name);
		} else {
			message.append("\n\n").append("Bumped bundles:");
			infos.stream().filter(info -> info.bumpedVersion != null)
					.forEach(info -> message.append("\n- ").append(info.name));
		}
		message.append(
				String.format("\n\nRequired for change %s/c/%s/+/%s\n", serverUri, change.info().project, change.id()));
		var config = git.getRepository().getConfig();
		message.append(String.format("\nSigned-off-by: %s <%s>", config.getString("user", null, "name"),
				config.getString("user", null, "email")));
		return message.toString();
	}

	private void bumpManifest(Git git, BundleInfo info) throws Exception {
		File workTree = git.getRepository().getWorkTree();
		File manifestFile = new File(workTree, info.manifestPath.toString());
		String content = Files.readString(manifestFile.toPath());
		content = content.replaceFirst("Bundle-Version: .*", "Bundle-Version: " + info.bumpedVersion);
		Files.writeString(manifestFile.toPath(), content, StandardCharsets.UTF_8);
		git.add().addFilepattern(info.manifestPath.toString()).call();
		LOG.info("Bumped version in " + manifestFile.toPath());
	}

	private void bumpPOM(Git git, BundleInfo info) throws Exception {
		File workTree = git.getRepository().getWorkTree();
		if (info.pomPath == null) {
			return;
		}
		File pomFile = new File(workTree, info.pomPath.toString());

		if (!pomFile.exists()) {
			return;
		}

		var mavenVersion = info.bumpedVersion.toString().replace(".qualifier", "-SNAPSHOT");

		// need to change the pom version without reformatting or loss of comments
		// (MavenXpp3Reader)
		String content = Files.readString(pomFile.toPath());

		int parentCloseOffset = content.indexOf("</parent>");
		String modified = content.substring(0, parentCloseOffset) + content.substring(parentCloseOffset)
				.replaceFirst("<version>.*</version>", "<version>" + mavenVersion + "</version>");

		Files.writeString(pomFile.toPath(), modified, StandardCharsets.UTF_8);
		git.add().addFilepattern(info.pomPath.toString()).call();
		LOG.info("Bumped version in " + pomFile.toPath());
	}

	Optional<String> pushChange(Git git, ChangeApi change) throws Exception {
		var repository = git.getRepository();
		RevWalk rw = new RevWalk(repository);
		ObjectId head = repository.resolve(Constants.HEAD);
		RevCommit commit = rw.parseCommit(head);

		LOG.info("The following commit is about to be pushed:");
		LOG.info("------------------------------------------------------------------------------");
		LOG.info(String.format("Author: %s <%s>", commit.getAuthorIdent().getName(),
				commit.getAuthorIdent().getEmailAddress()));
		LOG.info("------------------------------------------------------------------------------");
		LOG.info(commit.getFullMessage());
		LOG.info("------------------------------------------------------------------------------");
		// commit.getId()

		RevCommit parent = rw.parseCommit(commit.getParent(0).getId());
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		DiffFormatter df = new DiffFormatter(out);
		df.setRepository(repository);
		df.setDiffComparator(RawTextComparator.DEFAULT);
		df.setDetectRenames(true);

		List<DiffEntry> diffs = df.scan(parent.getTree(), commit.getTree());

		for (DiffEntry diff : diffs) {
			LOG.info(String.format("(%s %s %s", diff.getChangeType().name(), diff.getNewMode().getBits(),
					diff.getNewPath()));
		}
		LOG.info("------------------------------------------------------------------------------");

		boolean confirm = assumeYes;
		if (!confirm) {
			if (console == null) {
				LOG.error(
						"No system console available. Changes are not pushed. Use -y/--assume-yes=true to perform the push in non-interactive mode.");
			} else {
				LOG.info("Do you want to push the change (y|N)?");
				confirm = "y".equalsIgnoreCase(console.readLine());
			}
		}
		if (confirm) {
			var pushResult = git.push().setCredentialsProvider(credentialsProvider).setRemote("origin")
					.add("HEAD:refs/for/master").call();

			for (var result : pushResult) {
				var path = result.getURI().getPath();
				String changeId = path.substring(path.lastIndexOf('/') + 1);
				LOG.info("Created change#" + changeId);
				return Optional.of(changeId);
			}
		}
		return Optional.empty();
	}

	void rebaseChange(String changeId, String rebaseOnId) {
		if (true) {
			return; // not implemented on Gerrit API yet
		}
		try {
			LOG.info("Rebasing change#{} on #{}", changeId, rebaseOnId);
			var change = getChange(changeId);
			var input = new RebaseInput();
			input.base = rebaseOnId;

			change.rebase(input);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
