package com.yuan.yuancode;

import com.yuan.yuancode.util.TimeUtil;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeUtilTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Test
    void parsesCompositeDurations() {
        assertEquals(Duration.ofDays(7), TimeUtil.parseDuration("7d", Duration.ZERO));
        assertEquals(Duration.ofSeconds(90), TimeUtil.parseDuration("1m30s", Duration.ZERO));
        assertEquals(Duration.ofHours(36), TimeUtil.parseDuration("1d12h", Duration.ZERO));
        assertEquals(Duration.ofDays(30), TimeUtil.parseDuration("1mo", Duration.ZERO));
        assertEquals(Duration.ZERO, TimeUtil.parseDuration("0", Duration.ofDays(1)));
    }

    @Test
    void parsesNamedPeriods() {
        assertEquals(Duration.ofDays(1), TimeUtil.parsePeriod("daily"));
        assertEquals(Duration.ofDays(7), TimeUtil.parsePeriod("weekly"));
        assertEquals(Duration.ofDays(30), TimeUtil.parsePeriod("monthly"));
    }

    @Test
    void periodBucketsAreStable() {
        Instant first = TimeUtil.parseInstant("2026-11-24 10:00:00", ZONE, "yyyy-MM-dd HH:mm:ss");
        Instant second = TimeUtil.parseInstant("2026-11-24 23:59:59", ZONE, "yyyy-MM-dd HH:mm:ss");
        Instant nextDay = TimeUtil.parseInstant("2026-11-25 00:00:00", ZONE, "yyyy-MM-dd HH:mm:ss");
        String key = TimeUtil.periodKey(Duration.ofDays(1), first, ZONE);
        assertEquals(key, TimeUtil.periodKey(Duration.ofDays(1), second, ZONE));
        assertTrue(!key.equals(TimeUtil.periodKey(Duration.ofDays(1), nextDay, ZONE)));
    }

    @Test
    void parsesAndFormatsDates() {
        Instant instant = TimeUtil.parseInstant("2026-01-02 03:04:05", ZONE, "yyyy-MM-dd HH:mm:ss");
        assertEquals("2026-01-02 03:04:05", TimeUtil.formatInstant(instant, ZONE, "yyyy-MM-dd HH:mm:ss"));
    }

    @Test
    void keepsSubSecondDurations() {
        assertEquals("500ms", TimeUtil.compact(Duration.ofMillis(500)));
        assertEquals("1s 500ms", TimeUtil.compact(Duration.ofMillis(1_500)));
    }
}
