package io.meterian;

import io.meterian.core.Meterian;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;

public class ClientRunner {
    private static final Logger log = LoggerFactory.getLogger(ClientRunner.class);

    private Meterian client;
    private MeterianConsole console;

    private Callable setBuildToBreak;

    public ClientRunner(Meterian client,
                        MeterianConsole console) {
        this.client = client;
        this.console = console;

        setBuildToBreak = () -> {
            String clientFailedMsg = String.format("[meterian] Breaking build");
            log.error(clientFailedMsg);
            console.println(clientFailedMsg);
            return null;
        };
    }

    public int execute() {
        int executionResult = -1;
        try {
            Meterian.Result buildResult = client.run();
            if (failedAnalysis(buildResult)) {
                breakBuild();

                String clientFailedMsg = String.format("Meterian client analysis failed with exit code %d", buildResult.exitCode);
                log.error(clientFailedMsg);
                console.println(clientFailedMsg);
            }
            executionResult = buildResult.exitCode;
        } catch (Exception ex) {
            log.warn("Unexpected", ex);
            console.println("Unexpected exception!");
            console.printStackTrace(ex);
        }
        return executionResult;
    }

    private boolean failedAnalysis(Meterian.Result buildResult) {
        return buildResult.exitCode != 0;
    }

    public boolean userHasUsedTheAutofixFlag() {
        return client.getFinalClientArgs().contains("--autofix");
    }

    public void breakBuild() throws Exception {
        setBuildToBreak.call();
    }
}
