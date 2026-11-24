package com.yuan.yuancode.storage;

/** Wraps checked SQL failures so callers can log them uniformly. */
public final class StorageException extends RuntimeException {
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }

    public StorageException(String message) {
        super(message);
    }
}
