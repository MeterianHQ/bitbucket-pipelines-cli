package io.meterian;

import org.slf4j.Logger;

import java.io.PrintStream;

public class MeterianConsole {

    private Logger logger;
    private PrintStream console;

    public MeterianConsole(PrintStream console) {
        this.console = console;
    }

    public void println(String msg) {
        console.print(msg);
    }

    public void warn(String msg) {
        logger.warn(msg);
    }

    public void print(String msg) {
        console.println((msg));
    }

    public void flush() {
        console.flush();
    }

    public void close() {
        console.close();
    }

    public void printStackTrace(Exception ex) {
        ex.printStackTrace(console);
    }
}
