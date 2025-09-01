package com.team293.steps;

import com.slack.api.app_backend.events.payload.EventsApiPayload;
import com.slack.api.bolt.context.builtin.EventContext;
import com.slack.api.model.event.FunctionExecutedEvent;
import com.team293.entities.TimedEvent;
import com.team293.util.step.Step;
import com.team293.util.step.StepOutput;

import java.util.List;

public class GetLatestEventByTypeStep implements Step {
    @Override
    public String functionId() {
        return "get_latest_event_by_type";
    }

    @Override
    public List<StepOutput<?>> execute(EventsApiPayload<FunctionExecutedEvent> req, EventContext ctx) {
        String pollType = getInput(req, "poll_type", String.class);

        TimedEvent latestEvent = TimedEvent.find.query()
                .where()
                .eq("event_group", pollType)
                .orderBy("start_time DESC")
                .setMaxRows(1)
                .findOne();

        if (latestEvent == null) {
            reportError(req, "No event found for poll type: " + pollType);
            return List.of();
        }

        return List.of(
                new StepOutput<>(
                        "event_id",
                        latestEvent.getId()
                )
        );
    }
}
