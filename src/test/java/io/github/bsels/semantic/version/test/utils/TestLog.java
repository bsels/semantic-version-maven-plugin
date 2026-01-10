package io.github.bsels.semantic.version.test.utils;

import org.apache.maven.plugin.logging.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class TestLog implements Log {

    private final List<LogRecord> records;
    private final LogLevel minimalLogLevel;
    private final List<LogRecord> logRecords;

    public TestLog(LogLevel minimalLogLevel) {
        this.records = new ArrayList<>();
        this.logRecords = Collections.unmodifiableList(this.records);
        this.minimalLogLevel = Objects.requireNonNull(minimalLogLevel, "`minimalLogLevel` must not be null");
    }

    @Override
    public boolean isDebugEnabled() {
        return LogLevel.DEBUG.compareTo(minimalLogLevel) == 0;
    }

    @Override
    public void debug(CharSequence charSequence) {
        records.add(new LogRecord(LogLevel.DEBUG, charSequence));
    }

    @Override
    public void debug(CharSequence charSequence, Throwable throwable) {
        records.add(new LogRecord(LogLevel.DEBUG, charSequence, throwable));
    }

    @Override
    public void debug(Throwable throwable) {
        records.add(new LogRecord(LogLevel.DEBUG, throwable));
    }

    @Override
    public boolean isInfoEnabled() {
        return LogLevel.INFO.compareTo(minimalLogLevel) >= 0;
    }

    @Override
    public void info(CharSequence charSequence) {
        records.add(new LogRecord(LogLevel.INFO, charSequence));
    }

    @Override
    public void info(CharSequence charSequence, Throwable throwable) {
        records.add(new LogRecord(LogLevel.INFO, charSequence, throwable));
    }

    @Override
    public void info(Throwable throwable) {
        records.add(new LogRecord(LogLevel.INFO, throwable));
    }

    @Override
    public boolean isWarnEnabled() {
        return LogLevel.WARN.compareTo(minimalLogLevel) >= 0;
    }

    @Override
    public void warn(CharSequence charSequence) {
        records.add(new LogRecord(LogLevel.WARN, charSequence));
    }

    @Override
    public void warn(CharSequence charSequence, Throwable throwable) {
        records.add(new LogRecord(LogLevel.WARN, charSequence, throwable));
    }

    @Override
    public void warn(Throwable throwable) {
        records.add(new LogRecord(LogLevel.WARN, throwable));
    }

    @Override
    public boolean isErrorEnabled() {
        return LogLevel.ERROR.compareTo(minimalLogLevel) >= 0;
    }

    @Override
    public void error(CharSequence charSequence) {
        records.add(new LogRecord(LogLevel.ERROR, charSequence));
    }

    @Override
    public void error(CharSequence charSequence, Throwable throwable) {
        records.add(new LogRecord(LogLevel.ERROR, charSequence, throwable));
    }

    @Override
    public void error(Throwable throwable) {
        records.add(new LogRecord(LogLevel.ERROR, throwable));
    }

    public List<LogRecord> getLogRecords() {
        return logRecords;
    }

    public void clear() {
        records.clear();
    }

    public enum LogLevel {
        DEBUG, INFO, WARN, ERROR, NONE
    }

    public record LogRecord(LogLevel level, Optional<String> message, Optional<Throwable> throwable) {
        public LogRecord {
            Objects.requireNonNull(level, "`level` must not be null");
            Objects.requireNonNull(message, "`message` must not be null");
            Objects.requireNonNull(throwable, "`throwable` must not be null");
        }

        public LogRecord(LogLevel level, CharSequence message) {
            this(
                    level,
                    Optional.of(Objects.requireNonNull(message, "`message` must not be null").toString()),
                    Optional.empty()
            );
        }

        public LogRecord(LogLevel level, Throwable throwable) {
            this(
                    level,
                    Optional.empty(),
                    Optional.of(Objects.requireNonNull(throwable, "`throwable` must not be null"))
            );
        }

        public LogRecord(LogLevel level, CharSequence message, Throwable throwable) {
            this(
                    level,
                    Optional.of(Objects.requireNonNull(message, "`message` must not be null").toString()),
                    Optional.of(Objects.requireNonNull(throwable, "`throwable` must not be null"))
            );
        }
    }
}
