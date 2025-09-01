package com.team293.commands;

import com.slack.api.bolt.request.builtin.SlashCommandRequest;
import com.slack.api.bolt.response.Response;
import com.slack.api.methods.SlackApiException;
import com.team293.Main;
import com.team293.entities.TimedEvent;
import com.team293.util.command.Command;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

public class TotalHours implements Command {
    @Override
    public String getCommand() {
        return "total-hours";
    }

    @Override
    public void execute(SlashCommandRequest req, Response res) {
        String eventType = getOptionValue(req, 0, String.class);

        if (eventType == null || eventType.isEmpty()) {
            throw new IllegalArgumentException("Event type is required.");
        }

        List<TimedEvent> events = TimedEvent.find.query()
                .where()
                .eq("event_group", eventType)
                .findList();

        int totalHours = 0;

        for (TimedEvent event : events) {
            LocalDateTime start = event.getStartTime();
            LocalDateTime end = event.getEndTime();

            if (start != null && end != null) {
                totalHours += (int) Duration.between(start, end).toHours();
            }
        }

        String responseText = String.format("Total hours for event type *%s*: %d hours", eventType, totalHours);

        try {
            Main.app.client().chatPostMessage(r -> r
                    .channel(req.getPayload().getChannelId())
                    .text(responseText)
            );
        } catch (IOException | SlackApiException e) {
            throw new RuntimeException(e);
        }
    }
}
