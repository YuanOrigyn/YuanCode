package com.yuan.yuancode.storage;

import java.nio.file.Path;

public record ExportResult(Path file, long rows) {
}
