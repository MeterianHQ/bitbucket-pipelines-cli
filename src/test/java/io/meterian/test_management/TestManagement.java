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
import io.meterian.bitbucket.pipelines.BitbucketPipelines;
import io.meterian.git.LocalGitClient;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;

public class TestManagement {

    private static final String BASE_URL = "https://www.meterian.com";
    private static final String NO_JVM_ARGS = "";

    private static final int MAX_WAIT_POLL_TIME = 10_000; // seconds
    private static final int POLL_RETRY_COUNT = 10; // number of times to poll before giving up

    private String meterianBitbucketUser;
    private String meterianBitbucketAppPassword;
    private String meterianBitbucketEmail;

    private final BitbucketConfiguration configuration;

    private String repoWorkspace;
    private Logger log;
    private MeterianConsole console;
    private Map<String, String> environment;
    private UsernamePasswordCredentialsProvider credentialsProvider;

    public TestManagement(String repoWorkspace,
                          String meterianBitbucketUser,
                          String meterianBitbucketAppPassword,
                          String meterianBitbucketEmail,
                          Logger log,
                          MeterianConsole console) {
        configuration = getConfiguration(
                repoWorkspace,
                meterianBitbucketUser,
                meterianBitbucketAppPassword,
                meterianBitbucketEmail);
        this.log = log;
        this.console = console;
    }

    public void runPipelineCLIClientAndReportAnalysis(MeterianConsole console) {
        try {
            BitbucketPipelines bitbucketPipelines = new BitbucketPipelines(console);
            int exitCode = bitbucketPipelines.runMeterianScanner(
                    configuration,
                    environment,
                    "--autofix");
            assertThat(
                    "Meterian scanner client should NOT have terminated with a non-zero exit code", exitCode, is(0));
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

    public String getFixedByMeterianBranchName(String repoWorkspace, String currentBranch) throws Exception {
        try {
            LocalGitClient gitClient = new LocalGitClient(
                    repoWorkspace,
                    meterianBitbucketUser,
                    meterianBitbucketAppPassword,
                    meterianBitbucketUser,
                    console
            );

            gitClient.checkoutBranch(currentBranch);
            return String.format("fixed-by-meterian-%s", gitClient.getCurrentBranchSHA());
        } catch (Exception ex) {
            console.println(String.format(
                    "Could not fetch the name of the fixed-by-meterian-xxxx branch, due to error: %s" , ex.getMessage())
            );
            throw new Exception(ex);
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
            console.println(
                    String.format("We were unable to remove a remote branch %s from the repo, " +
                            "maybe the branch does not exist or the name has changed", branchName));
        }
    }

    public void closePullRequestForBranch(String bitbucketRepoName, String branchName) throws InterruptedException {
        log.info(String.format("Attempting to close pull request for linked branch %s", branchName));
        try {
            String pullRequestId = getOpenPullRequestIdForBranch(bitbucketRepoName, branchName);
            if (pullRequestId.isEmpty()) {
                log.info(String.format("No pull request for linked branch %s, proceeding forward", branchName));
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

            pollToCheckIfPullRequestIsClosed(bitbucketRepoName, branchName);
        } catch (UnirestException ex) {
            log.error(
                    String.format(
                            "Error trying to close pull request, due to %s (cause: %s)",
                            ex.getMessage(), ex.getCause()), ex);
        }
    }

    private void pollToCheckIfPullRequestIsClosed(
            String bitbucketRepoName, String branchName) throws UnirestException, InterruptedException {
        int retryCount = 0;
        while (getOpenPullRequestIdForBranch(bitbucketRepoName, branchName).isEmpty()) {
            // Wait for a bit for the changes to reflect across the system
            // before querying for anything via REST API calls.
            // It came to light during running tests on CI/CD and local machine.
            Thread.sleep(MAX_WAIT_POLL_TIME);
            retryCount++;
            if (retryCount == POLL_RETRY_COUNT) {
                String retryFailedMessage = String.format("Giving up, pull request linked to branch still NOT closed %s, cannot proceed forward.", branchName);
                log.error(retryFailedMessage);
                Assert.fail(retryFailedMessage);
            }
        }
        log.info(String.format("Pull request for linked branch %s is closed, proceeding forward", branchName));
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
        assertValidityOf("METERIAN_API_TOKEN", meterianAPIToken);

        this.meterianBitbucketUser = meterianBitbucketUser;
        assertValidityOf("METERIAN_BITBUCKET_USER", meterianBitbucketUser);

        this.meterianBitbucketAppPassword = meterianBitbucketAppPassword;
        assertValidityOf("METERIAN_BITBUCKET_APP_PASSWORD", meterianBitbucketAppPassword);

        credentialsProvider = new UsernamePasswordCredentialsProvider(
                meterianBitbucketUser,
                meterianBitbucketAppPassword);

        this.meterianBitbucketEmail = meterianBitbucketEmail;
        assertValidityOf("METERIAN_BITBUCKET_EMAIL", meterianBitbucketEmail);

        return new BitbucketConfiguration(
                BASE_URL,
                meterianAPIToken,
                NO_JVM_ARGS,
                repoWorkspace,
                meterianBitbucketUser,
                meterianBitbucketEmail,
                meterianBitbucketAppPassword);
    }

    private void assertValidityOf(String variableName, String variableValue) {
        String reason = String.format("%s has not been set, cannot run test without a valid value", variableName);
        assertThat(reason, variableValue, notNullValue());
        assertFalse(reason, variableValue.isEmpty());
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
}
