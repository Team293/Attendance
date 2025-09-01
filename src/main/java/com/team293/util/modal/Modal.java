package com.team293.util.modal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.api.bolt.request.builtin.ViewSubmissionRequest;
import com.slack.api.bolt.response.Response;
import com.slack.api.model.view.View;
import com.team293.Main;
import com.team293.util.Registerable;
import com.team293.util.reflection.ReflectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public interface Modal extends Registerable {

    Logger log = LoggerFactory.getLogger(Modal.class);

    String callbackId();

    View build(Map<String, Object> privateMetadata);

    default View build() {
        return build(Map.of());
    }

    void callback(ViewSubmissionRequest req, Response res);

    default void register() {
        Main.app.viewSubmission(callbackId(), (req, ctx) -> {
            Response res = Response.builder().build();
            callback(req, res);
            if (res.getStatusCode() == 200) {
                return ctx.ack(); // This closes the modal
            } else {
                // Optionally, show an error
                return ctx.ack(r -> r.responseAction("errors").errors(Map.of("_", res.getBody())));
            }
        });

        Main.app.viewClosed(callbackId(), (req, ctx) -> {
            // No action needed on close for now
            return ctx.ack();
        });

        Main.modals.put(callbackId(), this);
    }

    default Map parseMetadata(String metadata) {
        ObjectMapper om = new ObjectMapper();
        try {
            return om.readValue(metadata, Map.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    default String stringifyMetadata(Map<String, Object> metadata) {
        ObjectMapper om = new ObjectMapper();
        try {
            return om.writeValueAsString(metadata);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    default void registerAll() {
        List<Class<? extends Modal>> modalClasses = ReflectionUtils.getAllClassesImplementingInterface(
                Modal.class,
                "com.team293.modals"
        );

        for (Class<? extends Modal> modalClass : modalClasses) {
            try {
                Modal modal = modalClass.getDeclaredConstructor().newInstance();
                log.info("Registering modal: {}", modal.callbackId());
                modal.register();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    static Modal blank() {
        return new Modal() {
            @Override
            public String callbackId() {
                return "";
            }

            @Override
            public View build(Map<String, Object> privateMetadata) {
                return null;
            }

            @Override
            public void callback(ViewSubmissionRequest req, Response res) { }
        };
    }

}