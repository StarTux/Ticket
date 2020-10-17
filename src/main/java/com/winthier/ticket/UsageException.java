package com.winthier.ticket;

public final class UsageException extends RuntimeException {
    private final String key;

    UsageException(final String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
