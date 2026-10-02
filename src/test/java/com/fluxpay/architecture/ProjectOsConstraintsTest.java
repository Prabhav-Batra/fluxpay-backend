package com.fluxpay.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Enforces .engineering/config/constraints.yaml line limits. */
class ProjectOsConstraintsTest {

    private static final Path MAIN = Path.of("src/main/java");

    private static List<String> filesOver(String nameFragment, int maxLines) throws IOException {
        try (Stream<Path> files = Files.walk(MAIN)) {
            return files.filter(path -> path.getFileName().toString().contains(nameFragment))
                    .filter(path -> lineCount(path) > maxLines)
                    .map(Path::toString)
                    .toList();
        }
    }

    private static long lineCount(Path path) {
        try (Stream<String> lines = Files.lines(path)) {
            return lines.count();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void should_keep_controllers_within_300_lines() throws IOException {
        assertThat(filesOver("Controller", 300)).isEmpty();
    }

    @Test
    void should_keep_services_within_500_lines() throws IOException {
        assertThat(filesOver("Service", 500)).isEmpty();
    }
}
