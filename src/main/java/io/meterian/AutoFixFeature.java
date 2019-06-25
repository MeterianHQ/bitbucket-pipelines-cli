package io.meterian;

import io.meterian.bitbucket.LocalBitBucketClient;
import io.meterian.git.LocalGitClient;
import io.meterian.bitbucket.pipelines.BitbucketConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class AutoFixFeature {

    private static final String ABORTING_BRANCH_AND_PR_CREATION_PROCESS = "[meterian] Aborting, not continuing with rest of the local/remote branch or pull request creation process.";

    private static final String LOCAL_BRANCH_ALREADY_EXISTS_WARNING =
            "[meterian] Warning: %s already exists in the local repo, skipping the local branch creation process";

    private static final String BRANCH_ALREADY_FIXED_WARNING =
            "[meterian] Warning: %s is already fixed, no need to do anything";

    private static final int SUCCESSFUL = 0;
    private static final int FAILED = -1;

    static final Logger log = LoggerFactory.getLogger(AutoFixFeature.class);

    private final LocalGitClient localGitClient;
    private ClientRunner clientRunner;
    private MeterianConsole console;
    private BitbucketConfiguration configuration;

    public AutoFixFeature(BitbucketConfiguration configuration,
                          Map<String, String> environment,
                          ClientRunner clientRunner,
                          MeterianConsole console) {
        this.configuration = configuration;
        this.clientRunner = clientRunner;
        this.console = console;

        localGitClient = new LocalGitClient(
                configuration.getRepoWorkspace(),
                configuration.getMeterianBitbucketUser(),
                configuration.getMeterianBitbucketAppPassword(),
                configuration.getMeterianBitbucketEmail(),
                console);
    }

    public int execute() {
        int exitCode;
        try {
            if (localGitClient.currentBranchWasCreatedByMeterianClient()) {
                String thisBranchIsFixedMessage = String.format(
                        BRANCH_ALREADY_FIXED_WARNING, localGitClient.getCurrentBranch()
                );
                log.warn(thisBranchIsFixedMessage);
                console.println(thisBranchIsFixedMessage);

                return SUCCESSFUL;
            } else if (localGitClient.currentBranchHasNotBeenFixedYet()) {
                if (failedClientExecution()) {
                    localGitClient.resetChanges();

                    log.error(ABORTING_BRANCH_AND_PR_CREATION_PROCESS);
                    console.println(ABORTING_BRANCH_AND_PR_CREATION_PROCESS);

                    return FAILED;
                }
                exitCode = SUCCESSFUL;
            } else {
                String fixedBranchExistsMessage = String.format(
                        LOCAL_BRANCH_ALREADY_EXISTS_WARNING, localGitClient.getFixedBranchNameForCurrentBranch()
                );
                log.warn(fixedBranchExistsMessage);
                console.println(fixedBranchExistsMessage);

                exitCode = FAILED;
            }
        } catch (Exception ex) {
            log.error(String.format("Checking for branch or running the Meterian client was not successful due to: %s", ex.getMessage()), ex);
            throw new RuntimeException(ex);
        }

        try {
            if (localGitClient.hasChanges()) {
                localGitClient.applyCommitsToLocalRepo();
            } else {
                log.warn(LocalGitClient.NO_CHANGES_FOUND_WARNING);
                console.println(LocalGitClient.NO_CHANGES_FOUND_WARNING);
            }
        } catch (Exception ex) {
            log.error(String.format("Commits have not been applied due to the error: %s", ex.getMessage()), ex);
            throw new RuntimeException(ex);
        }

        localGitClient.pushBranchToRemoteRepo();

        try {
            LocalBitBucketClient localBitBucketClient = new LocalBitBucketClient(
                    configuration.getMeterianBitbucketUser(),
                    configuration.getMeterianBitbucketAppPassword(),
                    localGitClient.getOrgOrUsername(),
                    localGitClient.getRepositoryName(),
                    console);
            localBitBucketClient.createPullRequest(localGitClient.getCurrentBranch());
        } catch (Exception ex) {
            log.error(String.format("Pull Request was not created, due to the error: %s", ex.getMessage()), ex);
            throw new RuntimeException(ex);
        }

        return exitCode;
    }

    private boolean failedClientExecution() {
        return clientRunner.execute() != 0;
    }
}
