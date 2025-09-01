package com.team293.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.api.bolt.App;
import com.slack.api.bolt.response.Response;
import com.slack.api.model.block.LayoutBlock;

import java.util.*;
import java.util.function.Predicate;

public class BlockActionRouter {
    private final List<Route<?>> routes = new ArrayList<>();
    private final ObjectMapper om;

    public BlockActionRouter(ObjectMapper om) { this.om = om; }

    public <T> BlockActionRouter on(String actionId, Class<T> valueType,
                                    Handler<T> handler) {
        routes.add(new Route<>(actionId, v -> true, valueType, handler));
        return this;
    }

    public <T> BlockActionRouter on(String actionId, Predicate<String> valueMatches,
                                    Class<T> valueType, Handler<T> handler) {
        routes.add(new Route<>(actionId, valueMatches, valueType, handler));
        return this;
    }

    public BlockActionRouter on(String actionId, Handler<String> handler) {
        return on(actionId, String.class, handler);
    }

    /** Registers a single Bolt blockAction handler that delegates to routes. */
    public void install(App app) {
        app.blockAction(".*", (req, ctx) -> {
            var payload = req.getPayload();
            var action = payload.getActions().isEmpty() ? null : payload.getActions().getFirst();
            if (action == null) return ctx.ack();

            var actionId = action.getActionId();
            var value = action.getValue(); // may be null for some elements

            for (var r : routes) {
                if (r.matches(actionId, value)) {
                    var ac = new ActionContext(req, ctx, om);
                    try {
                        Object decoded = r.decode(value, om);
                        r.invoke(decoded, ac);
                    } catch (Exception e) {
                        ac.fail(e);
                    }
                    // Always ack once per event
                    return Response.ok();
                }
            }
            // No route matched: ack to stop retries, optionally log
            return ctx.ack();
        });
    }

    // --- Types ---
    public interface Handler<T> {
        void handle(T value, ActionContext ctx) throws Exception;
    }

    private record Route<T>(String actionId,
                            Predicate<String> valueMatches,
                            Class<T> valueType,
                            Handler<T> handler) {

        boolean matches(String id, String value) {
            return this.actionId.equals(id) && valueMatches.test(value);
        }

        Object decode(String raw, ObjectMapper om) throws Exception {
            if (valueType.equals(String.class)) return raw;
            if (raw == null || raw.isBlank()) return null;
            return om.readValue(raw, valueType);
        }

        @SuppressWarnings("unchecked")
        void invoke(Object decoded, ActionContext ctx) throws Exception {
            handler.handle((T) decoded, ctx);
        }
    }

    // A friendly facade around Bolt request/ctx
    public static class ActionContext {
        private final com.slack.api.bolt.request.builtin.BlockActionRequest req;
        private final com.slack.api.bolt.context.builtin.ActionContext ctx;
        private final ObjectMapper om;

        public ActionContext(
                com.slack.api.bolt.request.builtin.BlockActionRequest req,
                com.slack.api.bolt.context.builtin.ActionContext ctx,
                ObjectMapper om) {
            this.req = req; this.ctx = ctx; this.om = om;
        }

        public String userId() { return req.getPayload().getUser().getId(); }
        public String teamId() { return req.getPayload().getTeam().getId(); }
        public String channelId() {
            var c = req.getPayload().getContainer();
            return c != null ? c.getChannelId() : null;
        }
        public String triggerId() { return req.getPayload().getTriggerId(); }
        public Optional<String> responseUrl() {
            return Optional.ofNullable(req.getPayload().getResponseUrl());
        }

        // Reply in-thread or ephemeral using response_url if available
        public void respond(String text) throws Exception {
            if (responseUrl().isPresent()) ctx.respond(text);
            else ctx.say(r -> r.text(text)); // fallback to channel (e.g., from modals)
        }

        // Update the original message blocks
        public void updateBlocks(List<LayoutBlock> blocks) throws Exception {
            ctx.respond(r -> r.replaceOriginal(true).blocks(blocks));
        }

        // Open a modal
        public void modal(com.slack.api.model.view.View view) throws Exception {
            ctx.client().viewsOpen(r -> r
                    .triggerId(triggerId())
                    .view(view));
        }

        // Serialize helper
        public String toJson(Object o) {
            try { return om.writeValueAsString(o); }
            catch (Exception e) { throw new RuntimeException(e); }
        }

        // Common failure handling
        public void fail(Exception e) {
            System.err.println("[block_action_error] " + e);
            // Ack already returned by outer router; optionally send a friendly notice
            try { respond(":warning: Something went wrong. Try again."); }
            catch (Exception ex) { /* swallow */ }
        }
    }
}
