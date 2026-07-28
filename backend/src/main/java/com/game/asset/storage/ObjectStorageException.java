package com.game.asset.storage;

public final class ObjectStorageException extends RuntimeException {

    private final boolean notFound;

    private ObjectStorageException(String operation, Integer statusCode, boolean notFound) {
        super(message(operation, statusCode));
        this.notFound = notFound;
    }

    public static ObjectStorageException failed(String operation, Integer statusCode) {
        return new ObjectStorageException(operation, statusCode, false);
    }

    public static ObjectStorageException notFound(String operation) {
        return new ObjectStorageException(operation, 404, true);
    }

    public boolean isNotFound() {
        return notFound;
    }

    private static String message(String operation, Integer statusCode) {
        String status = statusCode == null ? "unknown" : statusCode.toString();
        return "Object storage " + operation + " failed with status " + status;
    }
}
