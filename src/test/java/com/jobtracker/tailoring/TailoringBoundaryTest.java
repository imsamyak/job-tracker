package com.jobtracker.tailoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Keeps the tailoring logic a module: the app talks to {@link Tailoring} only, and the module depends on nothing of the app. */
class TailoringBoundaryTest {

    private static final Path SOURCES = Path.of("src/main/java/com/jobtracker");

    private static List<Path> javaFiles() throws IOException {
        try (Stream<Path> s = Files.walk(SOURCES)) {
            return s.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    private static boolean insideModule(Path p) {
        return p.toString().replace('\\', '/').contains("/com/jobtracker/tailoring/");
    }

    @Test
    void theAppUsesTheModuleOnlyThroughItsFrontDoor() throws IOException {
        List<String> breaches = new ArrayList<>();
        for (Path p : javaFiles()) {
            if (insideModule(p)) continue;
            for (String line : Files.readAllLines(p)) {
                String l = line.strip();
                if (l.startsWith("import com.jobtracker.tailoring.") && !l.startsWith("import com.jobtracker.tailoring.Tailoring")) {
                    breaches.add(p.getFileName() + ": " + l);
                }
            }
        }
        assertThat(breaches).as("imports that reach behind the Tailoring class").isEmpty();
    }

    @Test
    void theModuleDependsOnNothingOfTheApp() throws IOException {
        List<String> breaches = new ArrayList<>();
        for (Path p : javaFiles()) {
            if (!insideModule(p)) continue;
            for (String line : Files.readAllLines(p)) {
                String l = line.strip();
                if (l.startsWith("import com.jobtracker.") && !l.startsWith("import com.jobtracker.tailoring.")) {
                    breaches.add(p.getFileName() + ": " + l);
                }
            }
        }
        assertThat(breaches).as("module files importing the app").isEmpty();
    }

    @Test
    void onlyTheFrontDoorIsPublic() throws IOException {
        List<String> exposed = new ArrayList<>();
        for (Path p : javaFiles()) {
            if (!insideModule(p) || p.getFileName().toString().equals("Tailoring.java")) continue;
            for (String line : Files.readAllLines(p)) {
                if (line.startsWith("public ")) exposed.add(p.getFileName() + ": " + line.strip());
            }
        }
        assertThat(exposed).as("public classes in the module other than Tailoring").isEmpty();
    }
}
