package com.winthier.ticket;

public class UsageException extends RuntimeException {
    private final String key;

    UsageException(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
