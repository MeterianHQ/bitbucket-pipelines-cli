package io.meterian.integration_tests;

import io.meterian.MeterianConsole;
import io.meterian.test_management.TestManagement;

import org.apache.commons.io.FileUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Paths;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class BitbucketPipelinesClientAutofixFeatureTest {

    private static final Logger log = LoggerFactory.getLogger(BitbucketPipelinesClientAutofixFeatureTest.class);

    private TestManagement testManagement;
    private static final String CURRENT_WORKING_DIR = System.getProperty("user.dir");

    private String bitbucketRepoName = "ClientOfMutabilityDetector";
    private String repoWorkspaceRootFolder = Paths.get(CURRENT_WORKING_DIR, "target/bitbucket-repo/").toString();
    private String repoWorkspace = Paths.get(repoWorkspaceRootFolder, bitbucketRepoName).toString();

    private File logFile;
    private MeterianConsole console;

    private String fixedByMeterianBranchName;
    private String meterianBitbucketUser = "meterian-bot";

    @Before
    public void setup() throws IOException {
        logFile = File.createTempFile("bitbucket-pipelines-cli-logger-", Long.toString(System.nanoTime()));
        console = new MeterianConsole(new PrintStream(logFile));
        log.info("Bitbucket Pipelines CLI log file: " + logFile.toPath().toString());

        String meterianBitbucketAppPassword = System.getenv("METERIAN_BITBUCKET_APP_PASSWORD");
        String meterianBitbucketEmail = "bot.bitbucket@meterian.io";

        testManagement = new TestManagement(
                repoWorkspace,
                meterianBitbucketUser,
                meterianBitbucketAppPassword,
                meterianBitbucketEmail,
                log,
                console);
    }

    @After
    public void tearDown() throws InterruptedException {
        testManagement.closePullRequestForBranch(bitbucketRepoName, fixedByMeterianBranchName);
        testManagement.deleteRemoteBranch(repoWorkspace, fixedByMeterianBranchName);
    }

    @Test
    public void scenario1_givenConfiguration_whenMeterianClientIsRunWithAutofixOptionForTheFirstTime_thenItShouldReturnAnalysisReportAndFixThem() throws Exception {
        // Given: we are setup to run the meterian client against a repo that has vulnerabilities
        FileUtils.deleteDirectory(new File(repoWorkspaceRootFolder));

        new File(repoWorkspaceRootFolder).mkdir();
        testManagement.performGitRepoClone(
                meterianBitbucketUser,
                bitbucketRepoName,
                repoWorkspace,
                "master");

        // Deleting remote branch automatically closes any Pull Request attached to it
        testManagement.configureGitUserNameAndEmail();
        fixedByMeterianBranchName = testManagement.getFixedByMeterianBranchName(repoWorkspace,"master");
        testManagement.closePullRequestForBranch(bitbucketRepoName, fixedByMeterianBranchName);
        testManagement.deleteRemoteBranch(repoWorkspace, fixedByMeterianBranchName);

        // When: the meterian client is run against the locally cloned gitF repo w2ith the autofix feature (--autofix) passed as a CLI arg
        testManagement.runPipelineCLIClientAndReportAnalysis(console);

        // Then: we should be able to see the expected output in the execution analysis output logs and the
        // reported vulnerabilities should be fixed, the changes committed to a branch and a pull request
        // created onto the respective remote Bitbucket repository of the project
        testManagement.verifyRunAnalysisLogs(logFile,
            new String[]{
                "Client successfully authorized",
                "Meterian Client v",
                "- autofix mode:      on",
                "Running autofix,",
                "Autofix applied, will run the build again.",
                "Project information:",
                "JAVA scan -",
                String.format("%s/%s.git", meterianBitbucketUser, bitbucketRepoName),
                "Build successful!",
                String.format("Finished creating pull request for org: %s, repo: %s/%s, branch: %s.",
                        meterianBitbucketUser, meterianBitbucketUser, bitbucketRepoName, fixedByMeterianBranchName)
            },
            new String[]{
                "Meterian client analysis failed with exit code ",
                "Breaking build",
                "Aborting, not continuing with rest of the local/remote branch or pull request creation process."
            }
        );
    }
}