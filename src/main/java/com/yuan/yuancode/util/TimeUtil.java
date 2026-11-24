package com.yuan.yuancode.util;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.WeekFields;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsing and formatting helpers for all human-readable time values in YuanCode.
 *
 * <p>Supported durations are combinations such as {@code 1d12h}, {@code 30s},
 * {@code 1mo} (30 days) or {@code 7d}. Supported dates are ISO-like values and
 * {@code yyyy-MM-dd HH:mm:ss}, all interpreted in the configured time zone.</p>
 */
public final class TimeUtil {

    private static final Pattern DURATION_PART =
            Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(mo|ms|s|m|h|d|w)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PERIOD_ALIAS =
            Pattern.compile("^(daily|day|weekly|week|monthly|month)$", Pattern.CASE_INSENSITIVE);

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
    private static final DateTimeFormatter DATE_TIME_MINUTES =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);
    private static final DateTimeFormatter DATE_ONLY =
            DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT);

    private TimeUtil() {
    }

    /**
     * Parses a user supplied duration. Returns {@code fallback} for empty values.
     *
     * @throws IllegalArgumentException when the value is not a valid duration
     */
    public static Duration parseDuration(String input, Duration fallback) {
        if (input == null || input.isBlank()) {
            return fallback;
        }
        String value = input.trim();
        if (value.equals("-") || value.equalsIgnoreCase("none")
                || value.equalsIgnoreCase("permanent") || value.equalsIgnoreCase("forever")) {
            return Duration.ZERO;
        }
        if (value.equals("0")) {
            return Duration.ZERO;
        }

        Matcher matcher = DURATION_PART.matcher(value);
        int cursor = 0;
        long nanos = 0L;
        boolean matched = false;
        while (matcher.find()) {
            if (!value.substring(cursor, matcher.start()).isBlank()) {
                throw new IllegalArgumentException("Invalid duration: " + input);
            }
            matched = true;
            double amount = Double.parseDouble(matcher.group(1));
            String unit = matcher.group(2).toLowerCase(Locale.ROOT);
            long multiplierNanos = switch (unit) {
                case "ms" -> 1_000_000L;
                case "s" -> 1_000_000_000L;
                case "m" -> 60_000_000_000L;
                case "h" -> 3_600_000_000_000L;
                case "d" -> 86_400_000_000_000L;
                case "w" -> 604_800_000_000_000L;
                case "mo" -> 2_592_000_000_000_000L;
                default -> throw new IllegalArgumentException("Unknown duration unit: " + unit);
            };
            nanos = Math.addExact(nanos, (long) (amount * multiplierNanos));
            cursor = matcher.end();
        }
        if (!matched || !value.substring(cursor).isBlank()) {
            throw new IllegalArgumentException("Invalid duration: " + input);
        }
        return Duration.ofNanos(nanos);
    }

    /**
     * Parses a period. Named periods are convenient aliases:
     * daily = 1 day, weekly = 7 days, monthly = 30 days.
     */
    public static Duration parsePeriod(String input) {
        if (input == null || input.isBlank()) {
            return Duration.ZERO;
        }
        String value = input.trim();
        if (PERIOD_ALIAS.matcher(value).matches()) {
            return switch (value.toLowerCase(Locale.ROOT)) {
                case "daily", "day" -> Duration.ofDays(1);
                case "weekly", "week" -> Duration.ofDays(7);
                case "monthly", "month" -> Duration.ofDays(30);
                default -> Duration.ZERO;
            };
        }
        return parseDuration(value, Duration.ZERO);
    }

    /** Parses an absolute instant, returning {@code null} for empty / "-". */
    public static Instant parseInstant(String input, ZoneId zone, String configuredPattern) {
        if (input == null || input.isBlank() || input.trim().equals("-")) {
            return null;
        }
        String value = input.trim();
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            // Try the remaining formats below.
        }
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME).atZone(zone).toInstant();
        } catch (DateTimeParseException ignored) {
            // Continue.
        }
        try {
            return OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant();
        } catch (DateTimeParseException ignored) {
            // Continue.
        }
        if (configuredPattern != null && !configuredPattern.isBlank()) {
            try {
                return LocalDateTime.parse(value, DateTimeFormatter.ofPattern(configuredPattern, Locale.ROOT))
                        .atZone(zone).toInstant();
            } catch (DateTimeParseException | IllegalArgumentException ignored) {
                // Continue.
            }
        }
        for (DateTimeFormatter formatter : new DateTimeFormatter[]{DATE_TIME, DATE_TIME_MINUTES, DATE_ONLY}) {
            try {
                if (formatter == DATE_ONLY) {
                    return LocalDate.parse(value, formatter).atStartOfDay(zone).toInstant();
                }
                return LocalDateTime.parse(value, formatter).atZone(zone).toInstant();
            } catch (DateTimeParseException ignored) {
                // Try the next format.
            }
        }
        throw new IllegalArgumentException("Invalid date/time: " + input);
    }

    public static String formatInstant(Instant instant, ZoneId zone, String pattern) {
        if (instant == null) {
            return "";
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern, Locale.ROOT).withZone(zone);
        return formatter.format(instant);
    }

    /**
     * Returns a stable period bucket for the current instant.
     *
     * <p>1 day buckets by calendar date, 7 days by ISO week and 30 days by
     * calendar month. Other periods are fixed-width buckets from the epoch.</p>
     */
    public static String periodKey(Duration period, Instant now, ZoneId zone) {
        if (period == null || period.isZero() || period.isNegative()) {
            return "GLOBAL";
        }
        long seconds = period.getSeconds();
        if (seconds == 86_400L) {
            return "D" + LocalDate.ofInstant(now, zone).format(DateTimeFormatter.BASIC_ISO_DATE);
        }
        if (seconds == 604_800L) {
            LocalDate date = LocalDate.ofInstant(now, zone);
            WeekFields iso = WeekFields.ISO;
            int weekYear = date.get(iso.weekBasedYear());
            int week = date.get(iso.weekOfWeekBasedYear());
            return "W" + weekYear + "-W" + String.format(Locale.ROOT, "%02d", week);
        }
        if (seconds == 2_592_000L) {
            LocalDate date = LocalDate.ofInstant(now, zone);
            return "M" + date.getYear() + "-" + String.format(Locale.ROOT, "%02d", date.getMonthValue());
        }
        long bucket = Math.floorDiv(now.getEpochSecond(), seconds);
        return "B" + bucket;
    }

    /** A short human-readable duration such as {@code 1d 2h 3m}. */
    public static String compact(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            return "0s";
        }
        long millis = duration.toMillis();
        long totalSeconds = millis / 1_000L;
        long remainderMillis = millis % 1_000L;
        long days = totalSeconds / 86_400;
        totalSeconds %= 86_400;
        long hours = totalSeconds / 3_600;
        totalSeconds %= 3_600;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        StringBuilder out = new StringBuilder();
        if (days > 0) {
            out.append(days).append("d ");
        }
        if (hours > 0) {
            out.append(hours).append("h ");
        }
        if (minutes > 0) {
            out.append(minutes).append("m ");
        }
        if (seconds > 0) {
            out.append(seconds).append("s ");
        }
        if (remainderMillis > 0) {
            out.append(remainderMillis).append("ms ");
        }
        if (out.isEmpty()) {
            out.append("0s ");
        }
        return out.toString().trim();
    }

    /** LuckPerms friendly duration without spaces, e.g. {@code 1d12h}. */
    public static String lpDuration(Duration duration) {
        return compact(duration).replace(" ", "");
    }

    public static long secondsUntil(Instant target, Instant now) {
        if (target == null) {
            return 0L;
        }
        return Math.max(0L, Duration.between(now, target).getSeconds());
    }

    public static ZoneId zoneOrServer(String configured) {
        if (configured == null || configured.isBlank() || configured.equalsIgnoreCase("server")) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(configured.trim());
        } catch (Exception ignored) {
            return ZoneId.systemDefault();
        }
    }

    /** Utility for tests and for displaying a UTC instant. */
    public static String iso(Instant instant) {
        return instant == null ? "" : instant.atOffset(ZoneOffset.UTC).toString();
    }
}
