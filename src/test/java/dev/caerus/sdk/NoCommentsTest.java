package dev.caerus.sdk;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class NoCommentsTest {

    @Test
    void theCodeCarriesNoCommentsAndNoJavadoc() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (String root : List.of("src/main/java", "src/test/java")) {
            List<Path> files;
            try (Stream<Path> walk = Files.walk(Path.of(root))) {
                files = walk.filter(path -> path.toString().endsWith(".java")).collect(Collectors.toList());
            }
            for (Path file : files) {
                List<String> lines = Files.readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    if (isComment(lines.get(i))) {
                        offenders.add(file + ":" + (i + 1));
                    }
                }
            }
        }

        assertThat(offenders).isEmpty();
    }

    private static final String SLASH = "/";
    private static final String STAR = "*";
    private static final String LINE = SLASH + SLASH;
    private static final String OPEN = SLASH + STAR;
    private static final String CLOSE = STAR + SLASH;

    private static boolean isComment(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith(LINE) || trimmed.startsWith(OPEN) || trimmed.startsWith(CLOSE)) {
            return true;
        }
        return trimmed.matches(".*[;{})]\\s*" + LINE + ".*") || trimmed.contains(OPEN + STAR);
    }
}
