package io.meterian.bitbucket.pipelines;

import io.meterian.HttpClientFactory;

import java.util.ArrayList;
import java.util.List;

public class BitbucketConfiguration implements HttpClientFactory.Config {

    private static final String DEFAULT_BASE_URL = "https://www.meterian.io";
    private static final int ONE_MINUTE = 60 * 1000;

    private final String baseUrl;
    private final String meterianAPIToken;

    private final String jvmArgs;
    private final String meterianBitbucketUser;
    private final String meterianBitbucketEmail;
    private final String meterianBitbucketAppPassword;

    private List<String> unsetEnvVariablesList = new ArrayList<>();

    private String repoWorkspace;

    public BitbucketConfiguration(String baseUrl,
                                  String meterianAPIToken,
                                  String jvmArgs,
                                  String repoWorkspace,
                                  String meterianBitbucketUser,
                                  String meterianBitbucketEmail,
                                  String meterianBitbucketAppPassword) {

        this.baseUrl = baseUrl;
        this.meterianAPIToken = meterianAPIToken;
        this.jvmArgs = jvmArgs;
        this.repoWorkspace = repoWorkspace;
        this.meterianBitbucketUser = meterianBitbucketUser;
        this.meterianBitbucketEmail = meterianBitbucketEmail;
        this.meterianBitbucketAppPassword = meterianBitbucketAppPassword;
    }

    public String getJvmArgs() {
        return jvmArgs;
    }

    public String getUrl() {
        return baseUrl;
    }

    public String getMeterianAPIToken() {
        return meterianAPIToken;
    }

    private String parseEmpty(String text, String defval) {
        return (text == null || text.trim().isEmpty()) ? defval : text;
    }

    public String getMeterianBaseUrl() {
        return parseEmpty(baseUrl, DEFAULT_BASE_URL);
    }

    public String getMeterianBitbucketUser() {
        return meterianBitbucketUser;
    }

    public String getMeterianBitbucketAppPassword() {
        return meterianBitbucketAppPassword;
    }

    public String getMeterianBitbucketEmail() {
        return meterianBitbucketEmail;
    }

    public String getRepoWorkspace() {
        return repoWorkspace;
    }

    public List<String> checkIfEnvironmentVariableHaveBeenSet() {
        addEnvironmentVariableToListIfAbsent(meterianAPIToken, "METERIAN_API_TOKEN");
        addEnvironmentVariableToListIfAbsent(meterianBitbucketUser,"METERIAN_BITBUCKET_USER");
        addEnvironmentVariableToListIfAbsent(meterianBitbucketAppPassword, "METERIAN_BITBUCKET_APP_PASSWORD");
        addEnvironmentVariableToListIfAbsent(meterianBitbucketEmail,"METERIAN_BITBUCKET_EMAIL");

        return unsetEnvVariablesList;
    }

    private void addEnvironmentVariableToListIfAbsent(String value, String environmentVariableName) {
        if ((value == null) || (value.trim().isEmpty())) {
            unsetEnvVariablesList.add(environmentVariableName);
        }
    }

    @Override
    public int getHttpConnectTimeout() {
        return ONE_MINUTE;
    }

    @Override
    public int getHttpSocketTimeout() {
        return ONE_MINUTE;
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
        return "meterian-scanner-client_1.0";
    }
}