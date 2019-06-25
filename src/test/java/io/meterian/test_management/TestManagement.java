package io.meterian.test_management;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.meterian.common.system.LineGobbler;
import com.meterian.common.system.OS;
import com.meterian.common.system.Shell;
import io.meterian.*;
import io.meterian.bitbucket.pipelines.BitbucketConfiguration;
import io.meterian.core.Meterian;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.http.client.HttpClient;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

import static junit.framework.TestCase.fail;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

public class TestManagement {

    private static final String BASE_URL = "https://www.meterian.com";
    private static final String NO_JVM_ARGS = "";

    private String meterianBitbucketUser;
    private String meterianBitbucketAppPassword;
    private String meterianBitbucketEmail;

    private final BitbucketConfiguration configuration;

    private String repoWorkspace;
    private Logger log;
    private MeterianConsole jenkinsLogger;
    private Map<String, String> environment;
    private UsernamePasswordCredentialsProvider credentialsProvider;

    public TestManagement(String repoWorkspace,
                          String meterianBitbucketUser,
                          String meterianBitbucketAppPassword,
                          String meterianBitbucketEmail,
                          Logger log,
                          MeterianConsole jenkinsLogger) {
        configuration = getConfiguration(
                repoWorkspace,
                meterianBitbucketUser,
                meterianBitbucketAppPassword,
                meterianBitbucketEmail);
        this.log = log;
        this.jenkinsLogger = jenkinsLogger;
    }

    public void runMeterianClientAndReportAnalysis(MeterianConsole jenkinsLogger) {
        try {
            File clientJar = getClientJar();
            Meterian client = getMeterianClient(configuration, clientJar);
            client.prepare(
                    "--interactive=false", "--autofix"
            );

            ClientRunner clientRunner =
                    new ClientRunner(client, jenkinsLogger);

            AutoFixFeature autoFixFeature = new AutoFixFeature(
                    configuration,
                    environment,
                    clientRunner,
                    jenkinsLogger
            );

            if (clientRunner.userHasUsedTheAutofixFlag()) {
                autoFixFeature.execute();
            } else {
                clientRunner.execute();
            }

            jenkinsLogger.close();
        } catch (Exception ex) {
            fail(String.format(
                    "Run meterian scan analysis: should not have failed with the exception: %s (cause: %s)", ex.getMessage(), ex.getCause()));
            log.error("Error while running Meterian Scanner client", ex);
        }
    }

    public void verifyRunAnalysisLogs(File logFile,
                                      String[] containsLogLines,
                                      String[] doesNotContainLogLines) throws IOException {
        String runAnalysisLogs = FileUtils.readFileToString(new File(logFile.getPath()));

        for (String eachLogLine: containsLogLines) {
            assertThat(runAnalysisLogs, containsString(eachLogLine));
        }

        for (String eachLogLine: doesNotContainLogLines) {
            assertThat(runAnalysisLogs, not(containsString(eachLogLine)));
        }
    }

    public void configureGitUserNameAndEmail() throws IOException {
        // git config --global user.name "Your Name"
        String[] gitConfigUserNameCommand = new String[] {
                "git",
                "config",
                "--local",
                "user.name",
                meterianBitbucketUser
        };

        int exitCode = runCommand(gitConfigUserNameCommand, repoWorkspace, log);

        assertThat("Cannot run the test, as we were unable configure a user due to error code: " +
                exitCode, exitCode, is(equalTo(0)));

        // git config --global user.email "you@example.com"
        String[] gitConfigUserEmailCommand = new String[] {
                "git",
                "config",
                "--local",
                "user.email",
                meterianBitbucketEmail
        };

        exitCode = runCommand(gitConfigUserEmailCommand, repoWorkspace, log);

        assertThat("Cannot run the test, as we were unable configure a user userEmail due to error code: " +
                exitCode, exitCode, is(equalTo(0)));
    }

    public void performGitRepoClone(String bitbucketOrgOrUserName,
                                    String bitbucketRepoName,
                                    String workingFolder,
                                    String branch) {
        String repoURI = String.format(
                "https://bitbucket.org/%s/%s.git", bitbucketOrgOrUserName, bitbucketRepoName);
        try {
            Git.cloneRepository()
                    .setCredentialsProvider(credentialsProvider)
                    .setURI(repoURI)
                    .setBranch(branch)
                    .setDirectory(new File(workingFolder))
                    .call();
        } catch (Exception ex) {
            fail(String.format("Cannot run the test, as we were unable to clone the target git repo due to an error: %s (cause: %s)",
                    ex.getMessage(), ex.getCause()));
        }
    }

    public void deleteRemoteBranch(String workingFolder, String branchName) {
        try {
            Git git = Git.open(new File(workingFolder));
            RefSpec refSpec = new RefSpec()
                    .setSource(null)
                    .setDestination("refs/heads/" + branchName);
            git.push()
                    .setCredentialsProvider(credentialsProvider)
                    .setRefSpecs(refSpec)
                    .setRemote("origin")
                    .call();
        } catch (IOException | GitAPIException ex) {
            jenkinsLogger.println(
                    String.format("We were unable to remove a remote branch %s from the repo, " +
                            "maybe the branch does not exist or the name has changed", branchName));
        }
    }

    public void closePullRequestForBranch(String bitbucketRepoName, String branchName) {
        try {
            String pullRequestId = getOpenPullRequestIdForBranch(bitbucketRepoName, branchName);
            if (pullRequestId.isEmpty()) {
                log.info(String.format("No pull request for linked to branch %s proceeding forward", branchName));
                return;
            }

            Unirest.post(
                    String.format(
                            "https://api.bitbucket.org/2.0/repositories/%s/%s/pullrequests/%s/decline",
                            meterianBitbucketUser,
                            bitbucketRepoName,
                            pullRequestId
                    )
            ).basicAuth(meterianBitbucketUser, meterianBitbucketAppPassword)
                    .asString();
        } catch (UnirestException ex) {
            log.error(
                    String.format(
                            "Error trying to close pull request, due to %s (cause: %s)",
                            ex.getMessage(), ex.getCause()), ex);
        }
    }

    private String getOpenPullRequestIdForBranch(String bitbucketRepoName, String branchName) throws UnirestException {
        List pullRequests = getAllOpenPullRequests(bitbucketRepoName);
        Optional foundPullRequest = pullRequests
                .stream()
                .filter(eachPullRequest ->
                        ((JSONObject) eachPullRequest)
                                .getJSONObject("source")
                                .getJSONObject("branch")
                                .get("name")
                                .equals(branchName)
                ).findFirst();

        if (foundPullRequest.isPresent()) {
            JSONObject pullRequestAsJsonObject = (JSONObject) foundPullRequest.get();
            return pullRequestAsJsonObject.get("id").toString();
        }
        return "";
    }

    private List getAllOpenPullRequests(String bitbucketRepoName) throws UnirestException {
        HttpResponse<JsonNode> response =
                Unirest.get(String.format(
                        "https://api.bitbucket.org/2.0/repositories/%s/%s/pullrequests",
                        meterianBitbucketUser,
                        bitbucketRepoName))
                        .header("accept", "application/json")
                        .queryString("state", "OPEN")
                        .asJson();

        return parsePullRequestsList(response);
    }

    private List parsePullRequestsList(HttpResponse<JsonNode> response) {
        JsonNode body = response.getBody();
        JSONArray pullRequests = body.getObject().getJSONArray("values");
        List results = new ArrayList();
        for (int i=0; i < pullRequests.length(); i++) {
            results.add(pullRequests.get(i));
        }
        return results;
    }


    private Map<String, String> getEnvironment() {
        Map<String, String> environment = new HashMap<>();

        Map<String, String> localEnvironment = new OS().getenv();
        for (String envKey: localEnvironment.keySet()) {
            environment.put(envKey, localEnvironment.get(envKey));
        }
        return environment;
    }

    private BitbucketConfiguration getConfiguration(String repoWorkspace,
                                                    String meterianBitbucketUser,
                                                    String meterianBitbucketAppPassword,
                                                    String meterianBitbucketEmail) {


        environment = getEnvironment();

        this.repoWorkspace = repoWorkspace;
        environment.put("WORKSPACE", repoWorkspace);

        String meterianAPIToken = environment.get("METERIAN_API_TOKEN");
        assertThat(
                "METERIAN_API_TOKEN has not been set, cannot run test without a valid value", meterianAPIToken, notNullValue());

        this.meterianBitbucketUser = meterianBitbucketUser;
        if ((meterianBitbucketUser == null) || meterianBitbucketUser.trim().isEmpty()) {
            jenkinsLogger.println(
                    "METERIAN_BITBUCKET_USER has not been set, tests will be run using the default value assumed for this environment variable");
        }

        this.meterianBitbucketAppPassword = meterianBitbucketAppPassword;
        assertThat(
                "METERIAN_BITBUCKET_APP_PASSWORD has not been set, cannot run test without a valid value", meterianBitbucketAppPassword, notNullValue());

        credentialsProvider = new UsernamePasswordCredentialsProvider(
                meterianBitbucketUser,
                meterianBitbucketAppPassword);

        this.meterianBitbucketEmail = meterianBitbucketEmail;
        if ((meterianBitbucketEmail == null) || meterianBitbucketEmail.trim().isEmpty()) {
            jenkinsLogger.println("METERIAN_BITBUCKET_EMAIL has not been set, tests will be run using the default value assumed for this environment variable");
        }

        return new BitbucketConfiguration(
                BASE_URL,
                meterianAPIToken,
                NO_JVM_ARGS,
                repoWorkspace,
                meterianBitbucketUser,
                meterianBitbucketEmail,
                meterianBitbucketAppPassword);
    }

    private File getClientJar() throws IOException {
        return new ClientDownloader(newHttpClient(), BASE_URL, nullPrintStream()).load();
    }

    private Meterian getMeterianClient(BitbucketConfiguration configuration, File clientJar) {
        return Meterian.build(configuration, environment, jenkinsLogger, NO_JVM_ARGS, clientJar);
    }

    private int runCommand(String[] command, String workingFolder, Logger log) throws IOException {
        LineGobbler errorLineGobbler = (type, text) ->
                log.error("{}> {}", type, text);

        Shell.Options options = new Shell.Options()
                .onDirectory(new File(workingFolder))
                .withErrorGobbler(errorLineGobbler)
                .withEnvironmentVariables(environment);
        Shell.Task task = new Shell().exec(
                command,
                options
        );

        return task.waitFor();
    }

    private MeterianConsole nullPrintStream() {
        return new MeterianConsole(
                new PrintStream(new NullOutputStream())
        );
    }

    private static HttpClient newHttpClient() {
        return new HttpClientFactory().newHttpClient(new HttpClientFactory.Config() {
            @Override
            public int getHttpConnectTimeout() {
                return Integer.MAX_VALUE;
            }

            @Override
            public int getHttpSocketTimeout() {
                return Integer.MAX_VALUE;
            }

            @Override
            public int getHttpMaxTotalConnections() {
                return 100;
            }

            @Override
            public int getHttpMaxDefaultConnectionsPerRoute() {
                return 100;
            }

            @Override
            public String getHttpUserAgent() {
                // TODO Auto-generated method stub
                return null;
            }});
    }
}
