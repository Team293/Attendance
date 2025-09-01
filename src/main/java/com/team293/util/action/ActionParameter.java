package com.team293.util.action;

import lombok.Data;

@Data
public class ActionParameter<T> {
    private final String name;
    private final Class<T> type;
    private final T value;

    public ActionParameter(String name, Class<T> type) {
        this.name = name;
        this.type = type;
        this.value = null;
    }

    public ActionParameter(String name, Class<T> type, T value) {
        this.name = name;
        this.type = type;
        this.value = value;
    }
}
