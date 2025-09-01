package com.team293.steps;

import com.slack.api.app_backend.events.payload.EventsApiPayload;
import com.slack.api.bolt.context.builtin.EventContext;
import com.slack.api.model.event.FunctionExecutedEvent;
import com.team293.Main;
import com.team293.util.action.Action;
import com.team293.util.action.ActionResponse;
import com.team293.util.step.Step;
import com.team293.util.step.StepOutput;

import java.util.List;

public class SendEventRosterStep implements Step {
    @Override
    public String functionId() {
        return "send_event_roster";
    }

    @Override
    public List<StepOutput<?>> execute(EventsApiPayload<FunctionExecutedEvent> req, EventContext ctx) {
        String eventId = getInput(req, "event_id", String.class);
        String channel = getInput(req, "channel", String.class);

        Action<String> constructRosterAction = Main.getAction("construct_event_roster");

        ActionResponse<String> rosterResponse = constructRosterAction.executeSafe(List.of(
                Action.createParameter("eventId", Long.class, Long.parseLong(eventId))
        ));

        if (rosterResponse.isSuccess() && rosterResponse.getData() != null) {
            String rosterMessage = rosterResponse.getData();
            try {
                Main.app.client().chatPostMessage(r -> r
                        .channel(channel)
                        .text(rosterMessage)
                );
            } catch (Exception e) {
                e.printStackTrace();
                reportError(req, "Failed to send message to channel: " + channel);
                return List.of();
            }
            return List.of(new StepOutput<>("status", "roster sent successfully"));
        } else {
            reportError(req, "Failed to construct roster: " + (rosterResponse.getMessage() != null ? rosterResponse.getMessage() : "Unknown error"));
            return List.of();
        }
    }
}
