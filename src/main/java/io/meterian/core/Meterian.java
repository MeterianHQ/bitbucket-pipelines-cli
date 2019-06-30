package io.meterian.core;

import com.meterian.common.system.LineGobbler;
import com.meterian.common.system.OS;
import com.meterian.common.system.Shell;
import com.meterian.common.system.Shell.Options;
import com.meterian.common.system.Shell.Task;
import io.meterian.HttpClientFactory;
import io.meterian.MeterianConsole;
import io.meterian.ClientDownloader;
import io.meterian.bitbucket.pipelines.BitbucketConfiguration;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.utils.URLEncodedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import java.util.*;

public class Meterian {

    private static final String ENVIRONMENT_VARIABLE_ABSENT_WARNING =
            "[meterian] Warning: %s has not been set in the config (please check for settings in " +
                    "Bitbucket Settings > Account Variables of your Bitbucket account interface), cannot complete process without this setting.";

    public static class Result {

        public int exitCode;
        public UUID projectUUID;
        public String projectBranch;
        public URI reportUrl;
        @Override
        public String toString() {
            return "[exitCode=" + exitCode + ", projectUUID=" + projectUUID + ", projectBranch=" + projectBranch + ", reportUrl=" + reportUrl + "]";
        }

    }

    private static final Logger log = LoggerFactory.getLogger(Meterian.class);

    private BitbucketConfiguration config;
    private final Map<String, String> environment;
    private final MeterianConsole console;
    private final String args;
    private final Shell shell;

    private File clientJar;
    private List<String> finalClientArgs;
    private List<String> finalJvmArgs;

    public static Meterian build(BitbucketConfiguration config, Map<String, String> environment, MeterianConsole console, String args)
            throws IOException {
        Meterian meterian = new Meterian(config, environment, console, args);
        meterian.init();
        return meterian;
    }

    private Meterian(BitbucketConfiguration config, Map<String, String> environment, MeterianConsole console, String args) {
        this.config = config;
        this.args = args;
        this.environment = environment;
        this.console = console;
        this.shell = new Shell();
    }

    private void init() throws IOException {
        HttpClient httpClient = new HttpClientFactory().newHttpClient(config);
        clientJar = new ClientDownloader(httpClient, config.getMeterianBaseUrl(), console).load();
    }

    private void init(File clientJar) {
        this.clientJar = clientJar;
    }

    public void prepare(String... extraClientArgs) {
        finalJvmArgs = compose(config.getJvmArgs(), mandatoryJvmArgs());
        finalClientArgs = compose(args, extraClientArgs);
    }

    public boolean requiredEnvironmentVariableHasBeenSet() {
        List<String> unsetEnvVariablesList = config.checkIfEnvironmentVariableHaveBeenSet();
        if (! unsetEnvVariablesList.isEmpty()) {
            log.warn(String.format(ENVIRONMENT_VARIABLE_ABSENT_WARNING, unsetEnvVariablesList));
            return false;
        }

        return true;
    }

    public List<String> getFinalClientArgs() {
        return finalClientArgs;
    }

    public Meterian.Result run() throws IOException {
        log.info("url:  {}", config.getMeterianBaseUrl());
        log.info("jvm:  {}", finalJvmArgs);
        log.info("args: {}", finalClientArgs);

        Meterian.Result result = new Meterian.Result();
        Task task = shell.exec(commands(finalJvmArgs, finalClientArgs), options(result));
        task.waitFor();
        result.exitCode = task.exitValue();

        return result;
    }

    private String[] mandatoryJvmArgs() {
        return new String[]{
                "-Dcli.param.folder=" + environment.get("WORKSPACE")
        };
    }

    private List<String> compose(String standardArgs, String[] extraArgs) {
        List<String> args = new ArrayList<>();

        if (standardArgs != null) {
            for (String s : standardArgs.split(" ")) {
                if (!s.trim().isEmpty())
                    args.add(s);
            }
        }

        if (extraArgs != null) args.addAll(Arrays.asList(extraArgs));

        return args;
    }

    private String[] commands(List<String> finalJvmArgs, List<String> finalClientArgs) {
        List<String> commands = new ArrayList<>();
        commands.add("java");
        for (String arg : finalJvmArgs)
            commands.add(arg);
        commands.add("-jar");
        commands.add(clientJar.getAbsolutePath());
        for (String arg : finalClientArgs)
            commands.add(arg);

        log.info("Commands: {}", commands);
        return commands.toArray(new String[commands.size()]);
    }

    private Options options(Meterian.Result result) {
        LineGobbler gobbler = new LineGobbler() {
            int count = 0;

            @Override
            public void process(String type, String line) {
                log.info(line);

                console.print("[meterian] ");
                console.println(line);
                if (++count % 10 == 0)
                    console.flush();

                try {
                    parseMeterianReportURLIfPresent(line);
                } catch (URISyntaxException e) {
                    log.warn("Unexpected", e);
                }
            }

            private void parseMeterianReportURLIfPresent(String line) throws URISyntaxException {
                if (line.indexOf("http") == -1 || line.indexOf("meterian.") == -1)
                    return;

                log.debug("Possible URL found in line {}", line);
                String[] tokens = line.split(" ");
                for (String token : tokens) {
                    if (token.startsWith("http")) {
                        UUID pid = null;
                        String branch = null;
                        URI url = new URI(token);
                        List<NameValuePair> params = URLEncodedUtils.parse(url, Charset.forName("UTF-8"));
                        for (NameValuePair param : params) {
                            if ("branch".equalsIgnoreCase(param.getName())) {
                                branch = param.getValue();
                                log.debug("Meterian project branch?: {}", branch);
                            } else if ("pid".equalsIgnoreCase(param.getName())) {
                                pid = UUID.fromString(param.getValue());
                                log.debug("Meterian project UUID?: {}", pid);
                            }
                        }

                        if (branch != null && pid != null) {
                            result.projectBranch = branch;
                            result.projectUUID = pid;
                            result.reportUrl = url;
                            log.info("Meterian project info: {}", result);
                        }
                    }
                }
            }
        };

        log.info("Using config token: {}", config.getMeterianAPIToken() != null ? "yes" : "no");

        return new Options()
                .withOutputGobbler(gobbler)
                .withErrorGobbler(gobbler)
                .withEnvironmentVariables(this.environment)
                .withEnvironmentVariable("METERIAN_API_TOKEN", config.getMeterianAPIToken())
                .withEnvironmentVariables(new OS().getenv());
    }
}