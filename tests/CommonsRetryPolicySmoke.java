package dev.infend.bplainfo;

import java.time.Instant;

public final class CommonsRetryPolicySmoke {
    private static void check(long expected, long actual) {
        if (expected != actual) throw new AssertionError(expected + " != " + actual);
    }
    public static void main(String[] args) {
        long now = 1_760_000_000_000L;
        check(now + 600_000L, CommonsRetryPolicy.nextAllowedMillis(429, "600", now));
        check(now + 900_000L, CommonsRetryPolicy.nextAllowedMillis(429, "9999999999999999999999", now));
        check(now + 60_000L, CommonsRetryPolicy.nextAllowedMillis(429, null, now));
        check(now + 15_000L, CommonsRetryPolicy.nextAllowedMillis(503, "invalid", now));
        check(now + 1_000L, CommonsRetryPolicy.nextAllowedMillis(429, "-10", now));
        long dateNow = Instant.parse("2026-11-02T19:00:00Z").toEpochMilli();
        check(dateNow + 120_000L, CommonsRetryPolicy.nextAllowedMillis(429, "Mon, 02 Nov 2026 19:02:00 GMT", dateNow));
        System.out.println("Commons retry policy: 6 cases passed");
    }
}
