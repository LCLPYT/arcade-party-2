package work.lclpnet.ap2.impl.util.debug;

import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class StopWatchImpl implements StopWatch {

    private @Nullable List<Section> sections = null;
    private @Nullable String section = null;
    private long startNanos = 0L;

    public void enable() {
        sections = new ArrayList<>();
    }

    @Override
    public void start(String section) {
        if (sections == null) return;

        stop();

        this.section = section;
        startNanos = System.nanoTime();
    }

    @Override
    public void stop() {
        if (sections == null || section == null) return;

        long elapsed = System.nanoTime() - startNanos;

        sections.add(new Section(section, elapsed));

        startNanos = 0L;
        section = null;
    }

    public List<Section> getSections() {
        return sections != null ? sections : List.of();
    }

    @Override
    public void printResults(PrintStream out) {
        if (sections == null) return;

        stop();

        sections.add(new Section("total", sections.stream().mapToLong(Section::nanoSeconds).sum()));

        final int n = sections.size() + 1;

        // columns
        String[] names = new String[n];
        String[] nanos = new String[n];
        String[] millis = new String[n];
        String[] seconds = new String[n];

        // header
        names[0] = "section";
        nanos[0] = "nanoseconds";
        millis[0] = "milliseconds";
        seconds[0] = "seconds";

        // rows
        for (int i = 1; i <= sections.size(); i++) {
            Section section = sections.get(i - 1);
            names[i] = section.name();
            nanos[i] = "%.3E".formatted((double) section.nanoSeconds());
            millis[i] = "%.0f".formatted(section.milliSeconds());
            seconds[i] = "%.3f".formatted(section.seconds());
        }

        // find column width
        int namesWidth = Arrays.stream(names).mapToInt(String::length).max().orElse(0);
        int nanosWidth = Arrays.stream(nanos).mapToInt(String::length).max().orElse(0);
        int millisWidth = Arrays.stream(millis).mapToInt(String::length).max().orElse(0);
        int secondsWidth = Arrays.stream(seconds).mapToInt(String::length).max().orElse(0);

        String hl = "─".repeat(namesWidth + nanosWidth + millisWidth + secondsWidth + 3) + "%n";

        out.printf(hl);

        for (int i = 0; i < n; i++) {
            String name = ("%-" + namesWidth + "s").formatted(names[i]);
            String nano = ("%" + nanosWidth + "s").formatted(nanos[i]);
            String milli = ("%" + millisWidth + "s").formatted(millis[i]);
            String second = ("%" + secondsWidth + "s").formatted(seconds[i]);
            out.printf("%s %s %s %s%n", name, second, milli, nano);

            if (i == 0 || i >= n - 2) {
                out.printf(hl);
            }
        }
    }

    public record Section(String name, long nanoSeconds) {

        public double milliSeconds() {
            return nanoSeconds * 1e-6d;
        }

        public double seconds() {
            return nanoSeconds * 1e-9d;
        }

        @Override
        public String toString() {
            return "%s:\t%dns\t%.3fms\t%.3fs".formatted(name, nanoSeconds, milliSeconds(), seconds());
        }
    }
}
