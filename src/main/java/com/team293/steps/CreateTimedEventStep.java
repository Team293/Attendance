package com.team293.steps;

import com.slack.api.app_backend.events.payload.EventsApiPayload;
import com.slack.api.bolt.context.builtin.EventContext;
import com.slack.api.model.event.FunctionExecutedEvent;
import com.team293.Main;
import com.team293.actions.util.AttendanceMessenger;
import com.team293.entities.TimedEvent;
import com.team293.util.step.Step;
import com.team293.util.step.StepOutput;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CreateTimedEventStep implements Step {
    private static final Pattern CURRENT_TIME_EXPR = Pattern.compile(
            "^\\{\\s*currentTime\\s*\\}(?:\\s*([+-])\\s*(\\d+))?\\s*$",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public String functionId() {
        return "create_timed_event";
    }

    @Override
    public List<StepOutput<?>> execute(EventsApiPayload<FunctionExecutedEvent> req, EventContext ctx) {
        String eventGroup = getInput(req, "event_group", String.class);
        String startTime = getInput(req, "start_time", String.class);
        String endTime = getInput(req, "end_time", String.class);
        String name = getInput(req, "name", String.class);
        String description = getInput(req, "description", String.class);
        Integer maxAttendees = getInput(req, "max_attendees", Integer.class, true);
        String channel = getInput(req, "channel", String.class);

        // parse local date times from unix timestamps or expressions
        LocalDateTime startDateTime;
        LocalDateTime endDateTime;
        ZoneId eastern = ZoneId.of("America/New_York");

        try {
            Instant startInstant = parseToInstant(startTime);
            Instant endInstant = parseToInstant(endTime);

            startDateTime = LocalDateTime.ofInstant(startInstant, eastern);
            endDateTime = LocalDateTime.ofInstant(endInstant, eastern);
        } catch (Exception e) {
            e.printStackTrace();
            reportError(req, "Invalid timestamp or expression. Use Unix epoch seconds (e.g., 1756394880) or expressions like \"{currentTime}\" or \"{currentTime} + 120\" (minutes).");
            return List.of();
        }

        TimedEvent timedEvent = new TimedEvent();
        timedEvent.setEventGroup(eventGroup);
        timedEvent.setStartTime(startDateTime);
        timedEvent.setEndTime(endDateTime);
        timedEvent.setName(replacePlaceholders(name, timedEvent));
        timedEvent.setDescription(replacePlaceholders(description, timedEvent));
        timedEvent.setCapacity(maxAttendees != null && maxAttendees > 0 ? maxAttendees : null);
        timedEvent.save();

        try {
            AttendanceMessenger.sendCheckInMessage(
                    Main.slack.methods(Main.token),
                    channel,
                    timedEvent
            );
        } catch (Exception e) {
            e.printStackTrace();
            reportError(req, "Failed to send check-in message to channel: " + channel);
            return List.of();
        }

        return List.of(
                new StepOutput<>(
                        "event_id",
                        timedEvent.getId()
                )
        );
    }

    private String replacePlaceholders(String template, TimedEvent event) {
        if (template == null) return null;
        return template
                .replace("{name}", event.getName() != null ? event.getName() : "Unnamed Event")
                .replace("{start_time}", event.getStartTime().toString())
                .replace("{end_time}", event.getEndTime().toString())
                .replace("{description}", event.getDescription() != null ? event.getDescription() : "No description");
    }

    // Parses input into an Instant.
    // Supported:
    // - Unix epoch seconds, e.g. "1756394880"
    // - "{currentTime}"
    // - "{currentTime} + 120" (minutes) or "{currentTime}-30"
    private Instant parseToInstant(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Time input cannot be null");
        }
        String s = input.trim();

        // Check for {currentTime} [+|- minutes]
        Matcher m = CURRENT_TIME_EXPR.matcher(s);
        if (m.matches()) {
            Instant now = Instant.now();
            String sign = m.group(1);
            String minutesStr = m.group(2);

            if (minutesStr == null || minutesStr.isEmpty()) {
                return now;
            }

            long minutes = Long.parseLong(minutesStr);
            Duration delta = Duration.ofMinutes(minutes);

            if ("-".equals(sign)) {
                return now.minus(delta);
            } else {
                // default to plus for "+" or missing sign (though regex requires if minutes present)
                return now.plus(delta);
            }
        }

        try {
            long epochSeconds = Long.parseLong(s);
            return Instant.ofEpochSecond(epochSeconds);
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException("Unsupported time format: " + s, nfe);
        }
    }
}