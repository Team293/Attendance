package com.team293.util.step;

public record StepOutput<T>(
        String key,
        T value
) {
}
