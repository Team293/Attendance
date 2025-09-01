package com.team293.util.step;

import com.slack.api.app_backend.events.payload.EventsApiPayload;
import com.slack.api.bolt.context.builtin.EventContext;
import com.slack.api.bolt.response.Response;
import com.slack.api.model.event.FunctionExecutedEvent;
import com.team293.Main;
import com.team293.util.Registerable;
import com.team293.util.reflection.ReflectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public interface Step extends Registerable {

    Logger log = LoggerFactory.getLogger(Step.class);

    String functionId();

    List<StepOutput<?>> execute(EventsApiPayload<FunctionExecutedEvent> req, EventContext ctx);

    default void register() {
        Main.app.function(functionId(), (req, ctx) -> {
            log.info("Executing step: {}", functionId());
            List<StepOutput<?>> outputs = execute(req, ctx);
            log.info("Step {} produced outputs: {}", functionId(), outputs);

            Map<String, Object> outputsMap = outputs.stream()
                    .filter(Objects::nonNull)
                    .collect(
                            Collectors.toMap(
                                    StepOutput::key,
                                    StepOutput::value
                            )
                    );

            log.info("Step {} outputs map: {}", functionId(), outputsMap);

            Main.app.client().functionsCompleteSuccess(r -> r
                    .token(Main.token)
                    .outputs(outputsMap)
                    .functionExecutionId(req.getEvent().getFunctionExecutionId())
            );

            return Response.ok(outputsMap);
        });
    }

    @Override
    default void registerAll() {
        List<Class<? extends Step>> stepClasses = ReflectionUtils.getAllClassesImplementingInterface(
                Step.class,
                "com.team293.steps"
        );

        for (Class<? extends Step> stepClass : stepClasses) {
            try {
                Step step = stepClass.getDeclaredConstructor().newInstance();
                log.info("Registering step: {}", step.functionId());
                step.register();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    default <T> T getInput(EventsApiPayload<FunctionExecutedEvent> req, String id, Class<T> type) {
        var raw = req.getEvent().getInputs().get(id);
        if (raw == null) {
            throw new IllegalArgumentException("Input not found: " + id);
        }

        Object value;

        if (type == String.class) {
            value = raw.getStringValue();
        } else if (type == List.class) {
            value = raw.getStringValues();
        } else if (type == Integer.class) {
            value = raw.asInteger();
        } else if (type == Double.class) {
            value = raw.asDouble();
        } else {
            throw new IllegalArgumentException("Unsupported input type: " + type.getName());
        }

        return type.cast(value);
    }

    default <T> T getInput(EventsApiPayload<FunctionExecutedEvent> req, String id, Class<T> type, boolean optional) {
        try {
            return getInput(req, id, type);
        } catch (IllegalArgumentException e) {
            if (optional) {
                return null;
            } else {
                throw e;
            }
        }
    }

    default void reportError(EventsApiPayload<FunctionExecutedEvent> req, String errorMessage) {
        try {
            Main.app.client().functionsCompleteError(r -> r
                    .token(Main.token)
                    .functionExecutionId(req.getEvent().getFunctionExecutionId())
                    .error(errorMessage)
            );
        } catch (Exception e) {
            e.printStackTrace();
            log.error("Failed to report error for step {}: {}", functionId(), e.getMessage());
        }
    }

    static Step blank() {
        return new Step() {
            @Override
            public String functionId() {
                return "";
            }

            @Override
            public List<StepOutput<?>> execute(EventsApiPayload<FunctionExecutedEvent> req, EventContext ctx) {
                return List.of();
            }
        };
    }

}
