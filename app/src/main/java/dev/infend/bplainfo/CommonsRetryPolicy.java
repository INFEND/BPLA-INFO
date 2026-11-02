package dev.infend.bplainfo;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/** Interprets Retry-After without ever waiting on the Android UI or photo queue. */
final class CommonsRetryPolicy {
    private CommonsRetryPolicy() {}

    static long nextAllowedMillis(int status, String retryAfter, long nowMillis) {
        long defaultSeconds = status == 429 ? 60 : 15;
        long seconds = defaultSeconds;
        if (retryAfter != null && !retryAfter.trim().isEmpty()) {
            String value = retryAfter.trim();
            try {
                seconds = Long.parseLong(value);
            } catch (NumberFormatException numeric) {
                if (value.matches("[0-9]+")) return nowMillis + 900_000L;
                try {
                    long deadline = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                            .toInstant().toEpochMilli();
                    seconds = (deadline - nowMillis + 999L) / 1000L;
                } catch (Exception ignored) {
                    seconds = defaultSeconds;
                }
            }
        }
        // Cap malformed, negative and unreasonably long server responses.
        seconds = Math.max(1L, Math.min(900L, seconds));
        return nowMillis + seconds * 1000L;
    }
}
