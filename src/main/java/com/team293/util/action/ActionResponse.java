package com.team293.util.action;

public class ActionResponse<T> {
    private final boolean success;
    private final String message;
    private final T data;

    public ActionResponse(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    public static <T> ActionResponse<T> success(T data) {
        return new ActionResponse<>(true, null, data);
    }

    public static <T> ActionResponse<T> failure(String message) {
        return new ActionResponse<>(false, message, null);
    }

    public static <T> ActionResponse<T> empty() {
        return new ActionResponse<>(true, null, null);
    }
}
