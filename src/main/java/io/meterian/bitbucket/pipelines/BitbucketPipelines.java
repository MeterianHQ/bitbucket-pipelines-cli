package io.meterian.bitbucket.pipelines;

import com.meterian.common.system.OS;
import io.meterian.AutoFixFeature;
import io.meterian.ClientRunner;
import io.meterian.MeterianConsole;
import io.meterian.core.Meterian;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

public class BitbucketPipelines {
    static final Logger log = LoggerFactory.getLogger(BitbucketPipelines.class);

    private static final String BASE_URL = "https://www.meterian.com";
    private static final String NO_JVM_ARGS = "";

    private Map<String, String> environment = new HashMap<>();

    public static void main(String[] args) throws Exception {
        log.info("Bitbucket Pipelines app started");
        BitbucketPipelines main = new BitbucketPipelines();
        int exitCode;
        if ((args == null) || (args.length == 0)) {
            exitCode = main.runMeterianScanner("");
        } else {
            exitCode = main.runMeterianScanner(args[0]);
        }

        log.info("Bitbucket Pipelines app finished");
        System.exit(exitCode);
    }

    private int runMeterianScanner(String cliArg) throws Exception {
        String currentDirectory = System.getProperty("user.dir");
        environment = getOSEnvSettings();
        environment.put("WORKSPACE", currentDirectory == null ? "." : currentDirectory);

        BitbucketConfiguration configuration = getConfiguration();

        File logFile = File.createTempFile("jenkins-logger-", Long.toString(System.nanoTime()));
        MeterianConsole console = new MeterianConsole(new PrintStream(logFile));
        Meterian client = Meterian.build(
                configuration,
                environment,
                console,
                NO_JVM_ARGS);

        if (! client.requiredEnvironmentVariableHasBeenSet()) {
            console.println("Required environment variable has not been set");
            return 0;
        }

        client.prepare("--interactive=false", cliArg);

        ClientRunner clientRunner = new ClientRunner(client, console);
        int exitCode = -1;
        if (clientRunner.userHasUsedTheAutofixFlag()) {
           exitCode = new AutoFixFeature(
                    configuration,
                    environment,
                    clientRunner,
                    console
            ).execute();
        } else {
            exitCode = clientRunner.execute();
        }

        return exitCode;
    }

    private BitbucketConfiguration getConfiguration() {
        String meterianAPIToken =
                reportErrorIfEnvironmentVariableIsAbsent("METERIAN_API_TOKEN");

        String meterianBitbucketAppPassword =
                reportErrorIfEnvironmentVariableIsAbsent("METERIAN_BITBUCKET_APP_PASSWORD");

        String meterianBitbucketUser =
                reportWarningIfEnvironmentVariableIsAbsent("METERIAN_BITBUCKET_USER");

        String meterianBitbucketEmail =
                reportWarningIfEnvironmentVariableIsAbsent("METERIAN_BITBUCKET_EMAIL");

        String repoWorkspace = environment.getOrDefault("WORKSPACE", ".");
        return new BitbucketConfiguration(
                BASE_URL,
                meterianAPIToken,
                NO_JVM_ARGS,
                repoWorkspace,
                meterianBitbucketUser,
                meterianBitbucketEmail,
                meterianBitbucketAppPassword);
    }

    private String reportWarningIfEnvironmentVariableIsAbsent(String environmentVariableName) {
        String value = environment.get(environmentVariableName);
        if ((value == null) || value.trim().isEmpty()) {
            log.warn(String.format("%s has not been set, scan will be Meterian Scanner using the default value assumed for this environment variable", environmentVariableName));
        }
        return value;
    }

    private String reportErrorIfEnvironmentVariableIsAbsent(String environmentVariableName) {
        String value = environment.get(environmentVariableName);
        if ((value == null) || (value.trim().isEmpty())) {
            log.error(String.format("%s has not been set, cannot run Meterian Scanner without a valid value", environmentVariableName));
        }
        return value;
    }

    private Map<String, String> getOSEnvSettings() {
        Map<String, String> localEnvironment = new OS().getenv();
        for (String envKey: localEnvironment.keySet()) {
            environment.put(envKey, localEnvironment.get(envKey));

        }
        return environment;
    }
}
