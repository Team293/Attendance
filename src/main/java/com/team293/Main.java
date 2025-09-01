package com.team293;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.api.Slack;
import com.slack.api.bolt.App;
import com.slack.api.bolt.response.Response;
import com.slack.api.bolt.socket_mode.SocketModeApp;
import com.slack.api.bolt.util.JsonOps;
import com.team293.config.AppConfig;
import com.team293.util.BlockActionRouter;
import com.team293.util.action.Action;
import com.team293.util.command.Command;
import com.team293.util.modal.Modal;
import com.team293.util.step.Step;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static java.util.stream.Collectors.joining;

public class Main {
    public static App app;
    public static BlockActionRouter router;
    public static SmallRyeConfig smallRyeConfig;
    public static AppConfig config;
    public static Slack slack;
    public static final String token = System.getenv("SLACK_BOT_TOKEN");

    public static final Map<String, Modal> modals = Collections.synchronizedMap(new HashMap<>());
    public static final Map<String, Action> actions = Collections.synchronizedMap(new HashMap<>());

    public static void main(String[] args) throws Exception {
        app = new App();
        router = new BlockActionRouter(new ObjectMapper());
        slack = Slack.getInstance();
        smallRyeConfig = new SmallRyeConfigBuilder()
                .addDefaultSources()       // system props, env vars
                .addDiscoveredSources()    // META-INF/microprofile-config.properties if present
                .addDiscoveredConverters()
                .withMapping(AppConfig.class, "app")
                .build();

        config = smallRyeConfig.getConfigMapping(AppConfig.class);

        initDebugMode();

        Modal.blank().registerAll();
        Command.blank().registerAll();
        Action.blank().registerAll();
        Step.blank().registerAll();

        new SocketModeApp(app).start();
    }

    static class DebugResponseBody {
        public String responseType;
        public String text;
    }

    public static Modal getModal(String callbackId) {
        return modals.get(callbackId);
    }

    public static <R> Action<R> getAction(String id) {
        @SuppressWarnings("unchecked")
        Action<R> a = (Action<R>) actions.get(id);
        return a;
    }

    private static void initDebugMode() {
        String debugMode = System.getenv("SLACK_APP_DEBUG_MODE");

        if (debugMode != null && debugMode.equals("1")) { // enable only when SLACK_APP_DEBUG_MODE=1
            app.use((req, _resp, chain) -> {
                Response resp = chain.next(req);
                if (resp.getStatusCode() != 200) {
                    resp.getHeaders().put("content-type", Collections.singletonList(resp.getContentType()));
                    // dump all the headers as a single string
                    String headers = resp.getHeaders().entrySet().stream()
                            .map(e -> e.getKey() + ": " + e.getValue() + "\n").collect(joining());

                    // set an ephemeral message with useful information
                    DebugResponseBody body = new DebugResponseBody();
                    body.responseType = "ephemeral";
                    body.text =
                            ":warning: *[DEBUG MODE] Something is technically wrong* :warning:\n" +
                                    "Below is a response the Slack app was going to send...\n" +
                                    "*Status Code*: " + resp.getStatusCode() + "\n" +
                                    "*Headers*: ```" + headers + "```" + "\n" +
                                    "*Body*: ```" + resp.getBody() + "```";
                    resp.setBody(JsonOps.toJsonString(body));

                    resp.setStatusCode(200);
                }
                return resp;
            });
        }
    }
}