package io.meterian.bitbucket.pipelines;

import com.meterian.common.system.OS;
import io.meterian.AutoFixFeature;
import io.meterian.ClientRunner;
import io.meterian.MeterianConsole;
import io.meterian.core.Meterian;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

public class BitbucketPipelines {
    static final Logger log = LoggerFactory.getLogger(BitbucketPipelines.class);

    private static final String BASE_URL = "https://www.meterian.com";
    private static final String NO_JVM_ARGS = "";
    private static final String NO_CLI_ARGS = "";

    private Map<String, String> environment = new HashMap<>();
    private MeterianConsole console;

    public static void main(String[] args) throws Exception {
        log.info("Bitbucket Pipelines CLI app started");
        File logFile = File.createTempFile("bitbucket-pipelines-cli-logger-", Long.toString(System.nanoTime()));
        MeterianConsole console = new MeterianConsole(new PrintStream(logFile));

        BitbucketPipelines main = new BitbucketPipelines(console);
        int exitCode;
        if ((args == null) || (args.length == 0)) {
            exitCode = main.runMeterianScanner(NO_CLI_ARGS);
        } else {
            exitCode = main.runMeterianScanner(args);
        }

        log.info("Bitbucket Pipelines CLI app finished");
        System.exit(exitCode);
    }

    public BitbucketPipelines(MeterianConsole console) {
        this.console = console;
    }

    private int runMeterianScanner(String... cliArgs) throws Exception {
        BitbucketConfiguration configuration = getConfiguration();

        return runClient(configuration, cliArgs);
    }

    public int runMeterianScanner(BitbucketConfiguration configuration,
                                  Map<String, String> environment,
                                  String... cliArgs) throws Exception {
        this.environment = environment;
        return runClient(configuration, cliArgs);
    }

    private int runClient(BitbucketConfiguration configuration,
                          String[] cliArgs) throws IOException {
        log.info(String.format("WORKSPACE: %s", environment.get("WORKSPACE")));

        Meterian client = Meterian.build(
                configuration,
                environment,
                console,
                NO_JVM_ARGS);

        if (!client.requiredEnvironmentVariableHasBeenSet()) {
            console.println("[warning] Exiting as required environment variable(s) have not been set");
            log.warn("Exiting as required environment variable(s) have not been set");
            return -1;
        }

        String[] composedCliArgs = ArrayUtils.addAll(new String[]{"--interactive=false"}, cliArgs);
        client.prepare(composedCliArgs);

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
        environment = getOSEnvSettings();

        String meterianAPIToken = environment.get("METERIAN_API_TOKEN");
        String meterianBitbucketAppPassword = environment.get("METERIAN_BITBUCKET_APP_PASSWORD");
        String meterianBitbucketUser = environment.get("METERIAN_BITBUCKET_USER");
        String meterianBitbucketEmail = environment.get("METERIAN_BITBUCKET_EMAIL");

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

    private Map<String, String> getOSEnvSettings() {
        Map<String, String> localEnvironment = new OS().getenv();
        for (String envKey: localEnvironment.keySet()) {
            environment.put(envKey, localEnvironment.get(envKey));

        }
        return environment;
    }
}
