package work.lclpnet.ap2.impl.util.debug;

import java.io.PrintStream;

public interface StopWatch {

    void start(String section);

    void stop();

    void printResults(PrintStream out);
}
