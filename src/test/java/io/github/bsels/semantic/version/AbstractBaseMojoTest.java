package io.github.bsels.semantic.version;

import io.github.bsels.semantic.version.test.utils.TestLog;

import java.net.URISyntaxException;
import java.nio.file.CopyOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractBaseMojoTest {

    protected Path getResourcesPath(String... relativePaths) {
        return Stream.of(relativePaths)
                .reduce(getResourcesPath(), Path::resolve, (a, b) -> {
                    throw new UnsupportedOperationException();
                });
    }

    protected Path getResourcesPath() {
        try {
            return Path.of(
                    Objects.requireNonNull(UpdatePomMojoTest.class.getResource("/itests/"))
                            .toURI()
            );
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    protected Consumer<TestLog.LogRecord> validateLogRecordDebug(String message) {
        return validateLogRecord(TestLog.LogLevel.DEBUG, message);
    }

    protected Consumer<TestLog.LogRecord> validateLogRecordInfo(String message) {
        return validateLogRecord(TestLog.LogLevel.INFO, message);
    }

    protected Consumer<TestLog.LogRecord> validateLogRecordWarn(String message) {
        return validateLogRecord(TestLog.LogLevel.WARN, message);
    }

    protected Consumer<TestLog.LogRecord> validateLogRecord(TestLog.LogLevel level, String message) {
        return record -> assertThat(record)
                .hasFieldOrPropertyWithValue("level", level)
                .hasFieldOrPropertyWithValue("throwable", Optional.empty())
                .hasFieldOrPropertyWithValue("message", Optional.of(message));
    }

    protected record CopyPath(Path original, Path copy, List<CopyOption> options) {
    }
}
