package com.team293.util.action;

import com.team293.Main;
import com.team293.util.Registerable;
import com.team293.util.reflection.ReflectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public interface Action<R> extends Registerable {

    Logger log = LoggerFactory.getLogger(Action.class);

    ActionResponse<R> execute(List<ActionParameter<?>> parameters) throws Exception;

    default boolean isThreadSafe() {
        return true;
    }

    default ActionResponse<R> executeSafe(List<ActionParameter<?>> parameters) {
        // make sure parameters are valid
        for (ActionParameter<?> param : getParameters()) {
            boolean found = false;
            for (ActionParameter<?> inputParam : parameters) {
                if (param.getName().equals(inputParam.getName()) &&
                        param.getType().equals(inputParam.getType())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new IllegalArgumentException("Missing or invalid parameter: " + param.getName());
            }
        }

        try {
            return execute(parameters);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    default ActionResponse<R> executeAsync(List<ActionParameter<?>> parameters) {
        if (!isThreadSafe()) {
            throw new IllegalStateException("Action is not thread-safe");
        }
        final AtomicReference<ActionResponse<R>> responseRef = new AtomicReference<>();
        Thread actionThread = new Thread(() -> {
            responseRef.set(executeSafe(parameters));
        });
        actionThread.start();

        try {
            actionThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ActionResponse.failure("Action execution interrupted");
        }

        return responseRef.get();
    }

    List<ActionParameter<?>> getParameters();

    default <T> ActionParameter<T> createParameter(String name, Class<T> type) {
        return new ActionParameter<>(name, type);
    }

    @SuppressWarnings("unchecked")
    default <T> T getParameterValue(List<ActionParameter<?>> parameters, String name, Class<T> type) {
        for (ActionParameter<?> param : parameters) {
            if (param.getName().equals(name) && param.getType().equals(type)) {
                return ((T) param.getValue());
            }
        }
        throw new IllegalArgumentException("Parameter not found: " + name);
    }

    default void initialize() {
        // Optional initialization logic
    }

    default void register() {
        Main.actions.put(actionId(), this);
    }

    String actionId();

    static <T> ActionParameter<T> createParameter(String name, Class<T> type, T value) {
        return new ActionParameter<>(name, type, value);
    }

    @Override
    default void registerAll() {
        List<Class<? extends Action>> actionClasses = ReflectionUtils.getAllClassesImplementingInterface(
                Action.class,
                "com.team293.actions"
        );

        for (Class<? extends Action> actionClass : actionClasses) {
            try {
                Action<?> action = actionClass.getDeclaredConstructor().newInstance();
                log.info("Registering action: {}", action.actionId());
                action.register();
                action.initialize();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    static Action blank() {
        return new Action() {
            @Override
            public ActionResponse execute(List list) throws Exception {
                return null;
            }

            @Override
            public List<ActionParameter<?>> getParameters() {
                return List.of();
            }

            @Override
            public String actionId() {
                return "";
            }
        };
    }
}

