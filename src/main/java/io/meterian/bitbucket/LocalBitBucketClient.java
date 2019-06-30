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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class LocalBitBucketClient {

    static final Logger log = LoggerFactory.getLogger(LocalBitBucketClient.class);

    private static final String METERIAN_BITBUCKET_USER_ABSENT_WARNING =
            "[meterian] Warning: METERIAN_BITBUCKET_USER has not been set in the config (please check for settings in " +
                    "Bitbucket Settings > Account Variables of your Bitbucket account interface), cannot create pull request without this setting.";
    private static final String METERIAN_BITBUCKET_APP_PASSWORD_ABSENT_WARNING =
            "[meterian] Warning: METERIAN_BITBUCKET_APP_PASSWORD has not been set in the config (please check for settings in " +
                    "Bitbucket Settings > Account Variables of your Bitbucket account interface), cannot create pull request without this setting.";
    private static final String PULL_REQUEST_ALREADY_EXISTS_WARNING =
            "[meterian] Warning: Pull request already exists for this branch, no new pull request will be created. " +
                    "Fixed already generated for current branch (commit point).";
    private static final String FOUND_PULL_REQUEST_WARNING =
            "[meterian] Warning: Found a pull request (id: %s) for org: %s, repo: %s, branch: %s";
    private static final String FINISHED_CREATING_PULL_REQUEST_MESSAGE = "[meterian] Finished creating pull request for org: %s, repo: %s, branch: %s.";

    private static final String METERIAN_FIX_PULL_REQUEST_TITLE = "[meterian] Fix for vulnerable dependencies";
    private static final String METERIAN_FIX_PULL_REQUEST_BODY = "Dependencies in project configuration file has been fixed";

    private static final String PULL_REQUEST_CREATION_ERROR = "Error occurred while creating pull request due to: %s";
    private static final String PULL_REQUEST_FETCHING_ACTION = "Fetching pull request(s) for org: %s, repo: %s, branch: %s";
    private static final String PULL_REQUEST_CREATION_ACTION = "Creating pull request for org: %s, repo: %s, branch: %s";
    private static final String PULL_REQUEST_FETCHING_ERROR = "Error occurred while fetching pull requests due to: %s";

    private static final boolean PULL_REQUEST_FOR_BRANCH_FOUND = true;
    private static final boolean PULL_REQUEST_FOR_BRANCH_NOT_FOUND = false;

    private String bitbucketUser;
    private String bitbucketAppPassword;
    private final String repoName;
    private final MeterianConsole console;

    public LocalBitBucketClient(String bitbucketUser,
                                String bitbucketAppPassword,
                                String repoName,
                                MeterianConsole console) {
        this.bitbucketUser = bitbucketUser;
        this.bitbucketAppPassword = bitbucketAppPassword;
        this.repoName = repoName;
        this.console = console;

        if (bitbucketUser == null || bitbucketUser.isEmpty()) {
            log.warn(METERIAN_BITBUCKET_USER_ABSENT_WARNING);
            console.println(METERIAN_BITBUCKET_USER_ABSENT_WARNING);
            return;
        }

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
                    PULL_REQUEST_CREATION_ACTION, bitbucketUser, repoName, branchName
            ));
            try {
                performPullRequest(repoName, branchName, METERIAN_FIX_PULL_REQUEST_TITLE, METERIAN_FIX_PULL_REQUEST_BODY);
                String finishedCreatingPullRequestMessage =
                        String.format(FINISHED_CREATING_PULL_REQUEST_MESSAGE, bitbucketUser, repoName, branchName);
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
                        .basicAuth(bitbucketUser, bitbucketAppPassword)
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
                PULL_REQUEST_FETCHING_ACTION, bitbucketUser, repoName, branchName
        ));
        try {
            // See docs at https://developer.atlassian.com/bitbucket/api/2/reference/resource/repositories/%7Busername%7D/%7Brepo_slug%7D/pullrequests#get
            String pullRequestId = getOpenPullRequestIdForBranch(repoName, branchName);

            if (pullRequestId.isEmpty()) {
                return PULL_REQUEST_FOR_BRANCH_NOT_FOUND;
            }

            String foundPullRequestWarning = String.format(
                    FOUND_PULL_REQUEST_WARNING, pullRequestId, bitbucketUser, repoName, branchName
            );
            log.warn(foundPullRequestWarning);
            console.println(foundPullRequestWarning);
            return PULL_REQUEST_FOR_BRANCH_FOUND;
        } catch (Exception ex) {
            log.error(String.format(PULL_REQUEST_FETCHING_ERROR, ex.getMessage()), ex);
            throw new RuntimeException(ex);
        }
    }

    public String getOpenPullRequestIdForBranch(String bitbucketRepoName, String branchName) throws UnirestException {
        LocalDateTime branchCreationDateTime = getBranchCreationDateTime(branchName);
        List pullRequests = getAllOpenPullRequests(bitbucketRepoName);
        Optional foundPullRequest = pullRequests
                .stream()
                .filter(eachPullRequest ->
                    pullRequestForBranchWasCreatedJustNow(
                            branchName,
                            (JSONObject) eachPullRequest,
                            branchCreationDateTime)
                ).findFirst();

        if (foundPullRequest.isPresent()) {
            JSONObject pullRequestAsJsonObject = (JSONObject) foundPullRequest.get();
            return pullRequestAsJsonObject.get("id").toString();
        }
        return "";
    }

    private LocalDateTime getBranchCreationDateTime(String branchName) throws UnirestException {
        HttpResponse<JsonNode> response =
                Unirest.get(String.format(
                        "https://api.bitbucket.org/2.0/repositories/%s/%s/refs/branches",
                        bitbucketUser,
                        repoName))
                        .asJson();

        if (response.getStatus() == 200) {
            JsonNode body = response.getBody();

            JSONArray fetchedBranches = body.getObject().getJSONArray("values");
            for (int index = 0; index < fetchedBranches.length(); index++) {
                String fetchedBranchName = fetchedBranches.getJSONObject(index).get("name").toString();

                if (fetchedBranchName.equals(branchName)) {
                    String fetchedBranchNameCreationDate =
                            fetchedBranches.getJSONObject(index)
                                    .getJSONObject("target")
                                    .get("date").toString();
                    return LocalDateTime.parse(dropTimeZoneStringIn(fetchedBranchNameCreationDate));
                }
            }
        }

        log.warn(String.format("Error occurred while fetching information for branch %s, due to %s",
                branchName, response.getBody()));

        return LocalDateTime.now();
    }

    private boolean pullRequestForBranchWasCreatedJustNow(String branchName,
                                                          JSONObject eachPullRequest,
                                                          LocalDateTime referenceDateTime) {
        boolean branchNameMatches = eachPullRequest
                .getJSONObject("source")
                .getJSONObject("branch")
                .get("name")
                .equals(branchName);
        String referenceDateTimeAsString =
                dropTimeZoneStringIn(eachPullRequest.get("created_on").toString());
        LocalDateTime localDateTime = LocalDateTime.parse(referenceDateTimeAsString);
        boolean createdAfterReferenceDateTime = localDateTime.isAfter(referenceDateTime);

        if (branchNameMatches && (! createdAfterReferenceDateTime) ) {
            log.warn(String.format("REST API call has returned incorrect OPEN pull requests for branch %s", branchName));
        }

        return branchNameMatches && createdAfterReferenceDateTime;
    }

    private String dropTimeZoneStringIn(String referenceDateTimeAsString) {
        int foundTimeZone = referenceDateTimeAsString.indexOf("+");
        if (foundTimeZone > 0) {
            referenceDateTimeAsString = referenceDateTimeAsString.substring(0, foundTimeZone);
        }
        return referenceDateTimeAsString;
    }

    private List getAllOpenPullRequests(String bitbucketRepoName) throws UnirestException {
        HttpResponse<JsonNode> response =
                Unirest.get(String.format(
                        "https://api.bitbucket.org/2.0/repositories/%s/%s/pullrequests?state=%s",
                        bitbucketUser,
                        bitbucketRepoName,
                        "OPEN"))
                        .asJson();

        return parsePullRequestsList(response);
    }

    private List parsePullRequestsList(HttpResponse<JsonNode> response) {
        JsonNode body = response.getBody();
        if (response.getStatus() == 200) {
            JSONArray pullRequests = body.getObject().getJSONArray("values");
            List results = new ArrayList();
            for (int index = 0; index < pullRequests.length(); index++) {
                results.add(pullRequests.get(index));
            }
            return results;
        } else {
            log.warn(String.format("Pull requests fetching call resulted in unexpected error: %s", response.getBody()));
            return Collections.emptyList();
        }
    }
}
