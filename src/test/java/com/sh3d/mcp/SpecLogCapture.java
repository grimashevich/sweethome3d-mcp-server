package com.sh3d.mcp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Collects every record of the plugin's log (the {@code com.sh3d.mcp} logger hierarchy, which the plugin writes to
 * {@code sh3d-mcp.log}) at every level, so tests can check what reaches the log.
 */
public final class SpecLogCapture implements AutoCloseable {

    private final Logger logger = Logger.getLogger("com.sh3d.mcp");
    private final Level previousLevel;
    private final List<LogRecord> records = Collections.synchronizedList(new ArrayList<>());
    private final Handler handler = new Handler() {
        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };

    public SpecLogCapture() {
        previousLevel = logger.getLevel();
        handler.setLevel(Level.ALL);
        logger.addHandler(handler);
        logger.setLevel(Level.ALL);
    }

    /** Every record as the log file would show it: formatted message, then each exception of the cause chain. */
    public List<String> texts() {
        SimpleFormatter formatter = new SimpleFormatter();
        List<String> texts = new ArrayList<>();
        synchronized (records) {
            for (LogRecord r : records) {
                StringBuilder sb = new StringBuilder();
                sb.append(r.getLevel()).append(' ').append(r.getLoggerName()).append(": ");
                sb.append(formatter.formatMessage(r));
                if (r.getParameters() != null) {
                    for (Object p : r.getParameters()) {
                        sb.append(" | ").append(p);
                    }
                }
                for (Throwable t = r.getThrown(); t != null; t = t.getCause()) {
                    sb.append(" | ").append(t);
                }
                texts.add(sb.toString());
            }
        }
        return texts;
    }

    /** Records at WARNING or above. */
    public List<String> warnings() {
        List<String> result = new ArrayList<>();
        synchronized (records) {
            for (LogRecord r : records) {
                if (r.getLevel().intValue() >= Level.WARNING.intValue()) {
                    result.add(r.getLoggerName() + ": " + new SimpleFormatter().formatMessage(r));
                }
            }
        }
        return result;
    }

    @Override
    public void close() {
        logger.removeHandler(handler);
        logger.setLevel(previousLevel);
    }
}
