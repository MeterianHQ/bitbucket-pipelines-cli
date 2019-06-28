package io.meterian.bitbucket;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import io.meterian.MeterianConsole;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class LocalBitBucketClient {

    static final Logger log = LoggerFactory.getLogger(LocalBitBucketClient.class);

    private static final String METERIAN_BITBUCKET_APP_PASSWORD_ABSENT_WARNING =
            "[meterian] Warning: METERIAN_BITBUCKET_APP_PASSWORD has not been set in the config (please check for settings in " +
                    "Bitbucket Settings > Account Variables of your Bitbucket account interface), cannot create pull request without this setting.";
    private static final String PULL_REQUEST_ALREADY_EXISTS_WARNING =
            "[meterian] Warning: Pull request already exists for this branch, no new pull request will be created. " +
                    "Fixed already generated for current branch (commit point).";
    private static final String FOUND_PULL_REQUEST_WARNING =
            "[meterian] Warning: Found %d pull request(s) for org: %s, repo: %s, branch: %s";
    private static final String FINISHED_CREATING_PULL_REQUEST_MESSAGE = "[meterian] Finished creating pull request for org: %s, repo: %s, branch: %s.";

    private static final String METERIAN_FIX_PULL_REQUEST_TITLE = "[meterian] Fix for vulnerable dependencies";
    private static final String METERIAN_FIX_PULL_REQUEST_BODY = "Dependencies in project configuration file has been fixed";

    private static final String PULL_REQUEST_CREATION_ERROR = "Error occurred while creating pull request due to: %s";
    private static final String PULL_REQUEST_FETCHING_ACTION = "Fetching pull request(s) for org: %s, repo: %s, branch: %s";
    private static final String PULL_REQUEST_CREATION_ACTION = "Creating pull request for org: %s, repo: %s, branch: %s";
    private static final String PULL_REQUEST_FETCHING_ERROR = "Error occurred while fetching pull requests due to: %s";

    private static final boolean PULL_REQUEST_FOR_BRANCH_FOUND = true;
    private static final boolean PULL_REQUEST_FOR_BRANCH_NOT_FOUND = false;

    private String bitbucketMachineUser;
    private String bitbucketAppPassword;
    private final String orgOrUserName;
    private final String repoName;
    private final MeterianConsole console;

    public LocalBitBucketClient(String bitbucketMachineUser,
                                String bitbucketAppPassword,
                                String orgOrUserName,
                                String repoName,
                                MeterianConsole console) {
        this.bitbucketMachineUser = bitbucketMachineUser;
        this.bitbucketAppPassword = bitbucketAppPassword;
        this.orgOrUserName = orgOrUserName;
        this.repoName = repoName;
        this.console = console;

        if (bitbucketAppPassword == null || bitbucketAppPassword.isEmpty()) {
            log.warn(METERIAN_BITBUCKET_APP_PASSWORD_ABSENT_WARNING);
            console.println(METERIAN_BITBUCKET_APP_PASSWORD_ABSENT_WARNING);
            return;
        }
    }

    public void createPullRequest(String branchName) {
        if (pullRequestExists(branchName)) {
            log.warn(PULL_REQUEST_ALREADY_EXISTS_WARNING);
            console.println(PULL_REQUEST_ALREADY_EXISTS_WARNING);
        } else {
            log.info(String.format(
                    PULL_REQUEST_CREATION_ACTION, orgOrUserName, repoName, branchName
            ));
            try {
                performPullRequest(repoName, branchName, METERIAN_FIX_PULL_REQUEST_TITLE, METERIAN_FIX_PULL_REQUEST_BODY);
                String finishedCreatingPullRequestMessage =
                        String.format(FINISHED_CREATING_PULL_REQUEST_MESSAGE, orgOrUserName, repoName, branchName);
                log.info(finishedCreatingPullRequestMessage);
                console.println(finishedCreatingPullRequestMessage);
            } catch (Exception ex) {
                log.error(String.format(PULL_REQUEST_CREATION_ERROR, ex.getMessage()), ex);
                throw new RuntimeException(ex);
            }
        }
    }

    private String performPullRequest(String repository,
                                      String branchName,
                                      String title,
                                      String body) throws UnirestException {
        // See docs at https://developer.atlassian.com/bitbucket/api/2/reference/resource/repositories/%7Busername%7D/%7Brepo_slug%7D/pullrequests#post
        HttpResponse<String> response =
                Unirest.post(String.format(
                        "https://api.bitbucket.org/2.0/repositories/%s/pullrequests",
                        repository))
                        .basicAuth(bitbucketMachineUser, bitbucketAppPassword)
                        .header("content-type", "application/json")
                        .header("accept", "application/json")
                        .body(String.format("{\n" +
                                        "        \"title\": \"%s\",\n" +
                                        "        \"summary\": {\n" +
                                        "            \"raw\": \"%s\"\n" +
                                        "        },\n" +
                                        "        \"source\": {\n" +
                                        "            \"branch\": {\n" +
                                        "                \"name\": \"%s\"\n" +
                                        "            }\n" +
                                        "        }\n" +
                                        "    }", title,
                                body,
                                branchName)
                        )
                        .asString();

        return response.getBody();
    }

    private boolean pullRequestExists(String branchName) {
        log.info(String.format(
                PULL_REQUEST_FETCHING_ACTION, orgOrUserName, repoName, branchName
        ));
        try {
            // See docs at https://developer.atlassian.com/bitbucket/api/2/reference/resource/repositories/%7Busername%7D/%7Brepo_slug%7D/pullrequests#get
            List pullRequestsFound = getPullRequests(repoName, "OPEN");

            if (findTargetBranchInPullRequest(branchName, pullRequestsFound)) {
                String foundPullRequestWarning = String.format(
                        FOUND_PULL_REQUEST_WARNING, pullRequestsFound.size(), orgOrUserName, repoName, branchName
                );
                log.warn(foundPullRequestWarning);
                console.println(foundPullRequestWarning);

                return PULL_REQUEST_FOR_BRANCH_FOUND;
            }

            return PULL_REQUEST_FOR_BRANCH_NOT_FOUND;
        } catch (Exception ex) {
            log.error(String.format(PULL_REQUEST_FETCHING_ERROR, ex.getMessage()), ex);
            throw new RuntimeException(ex);
        }
    }

    private boolean findTargetBranchInPullRequest(String branchName, List pullRequests) {
        Optional foundPullrequestForBranch = pullRequests
                .stream()
                .filter(eachPullRequest ->
                        ((JSONObject) eachPullRequest)
                                .getJSONObject("source")
                                .getJSONObject("branch")
                                .get("name")
                                .equals(branchName)
                ).findAny();

        return foundPullrequestForBranch.isPresent();
    }

    private List getPullRequests(String repository, String state) throws UnirestException {
        HttpResponse<JsonNode> response =
                Unirest.get(String.format(
                        "https://api.bitbucket.org/2.0/repositories/%s/pullrequests",
                        repository))
                        .header("accept", "application/json")
                        .queryString("state", state)
                        .asJson();

        JSONArray pullRequests = response
                                    .getBody()
                                    .getObject()
                                    .getJSONArray("values");
        List results = new ArrayList();
        for (int index = 0; index < pullRequests.length(); index++) {
            results.add(pullRequests.get(index));
        }
        return results;
    }
}
