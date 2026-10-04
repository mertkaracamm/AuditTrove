package com.audittrove.ops;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import jakarta.annotation.PostConstruct;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Uygulama loglarini bellekte tutan halka tampon (son MAX_ENTRIES kayit).
 * Ops servisi /internal/ops/logs uzerinden okur; disk/DB yok, restart'ta sifirlanir.
 * Mevcut loglamaya dokunmaz — root logger'a EK bir appender takar.
 */
@Component
public class OpsLogBuffer {

    public record Entry(long ts, String level, String logger, String message) {}

    private static final int MAX_ENTRIES = 600;
    private static final int MAX_MESSAGE_LEN = 500;
    private static final ArrayDeque<Entry> BUFFER = new ArrayDeque<>(MAX_ENTRIES);

    @PostConstruct
    void attach() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);

        AppenderBase<ILoggingEvent> appender = new AppenderBase<>() {
            @Override
            protected void append(ILoggingEvent event) {
                if (!event.getLevel().isGreaterOrEqual(Level.INFO)) return;
                String msg = event.getFormattedMessage();
                if (msg == null) msg = "";
                if (msg.length() > MAX_MESSAGE_LEN) msg = msg.substring(0, MAX_MESSAGE_LEN) + "…";
                Entry e = new Entry(
                        event.getTimeStamp(),
                        event.getLevel().toString(),
                        shortLogger(event.getLoggerName()),
                        msg);
                synchronized (BUFFER) {
                    if (BUFFER.size() >= MAX_ENTRIES) BUFFER.pollFirst();
                    BUFFER.addLast(e);
                }
            }
        };
        appender.setContext(context);
        appender.setName("opsRingBuffer");
        appender.start();
        root.addAppender(appender);
    }

    private static String shortLogger(String name) {
        if (name == null) return "";
        int i = name.lastIndexOf('.');
        return i >= 0 ? name.substring(i + 1) : name;
    }

    /** minutes geriye git, minLevel ve ustunu dondur (WARN -> WARN+ERROR). */
    public static List<Entry> snapshot(int minutes, String minLevel) {
        long since = System.currentTimeMillis() - (long) minutes * 60_000L;
        int threshold = levelRank(minLevel);
        List<Entry> out = new ArrayList<>();
        synchronized (BUFFER) {
            for (Entry e : BUFFER) {
                if (e.ts() >= since && levelRank(e.level()) >= threshold) out.add(e);
            }
        }
        return out;
    }

    private static int levelRank(String level) {
        if (level == null) return 1;
        return switch (level.toUpperCase()) {
            case "ERROR" -> 3;
            case "WARN" -> 2;
            default -> 1; // INFO ve belirsizler
        };
    }
}