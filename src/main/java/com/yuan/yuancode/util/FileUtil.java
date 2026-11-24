package com.yuan.yuancode.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Atomic file helpers and CSV escaping. */
public final class FileUtil {

    private FileUtil() {
    }

    public static void writeAtomic(Path target, String content) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = Files.createTempFile(parent, target.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public static void writeLines(Path target, List<String> lines) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(target, lines, StandardCharsets.UTF_8);
    }

    public static String safeFileName(String input, String fallback) {
        if (input == null || input.isBlank()) {
            return fallback;
        }
        String clean = input.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        if (clean.isBlank() || clean.equals(".") || clean.equals("..")) {
            return fallback;
        }
        return clean;
    }

    public static String timestampFile(String prefix, String extension, ZoneId zone) {
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT)
                .withZone(zone)
                .format(Instant.now());
        return prefix + "-" + stamp + (extension.startsWith(".") ? extension : "." + extension);
    }

    /** RFC 4180-ish CSV field escaping. */
    public static String csv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.indexOf(',') >= 0 || escaped.indexOf('"') >= 0
                || escaped.indexOf('\n') >= 0 || escaped.indexOf('\r') >= 0) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }
}
