package com.team293.actions;

import com.slack.api.methods.SlackApiException;
import com.slack.api.model.User;
import com.team293.Main;
import com.team293.actions.dto.UserGroupInfoRes;
import com.team293.actions.util.AttendanceMessenger;
import com.team293.config.AppConfig;
import com.team293.entities.AttendanceEntry;
import com.team293.entities.TimedEvent;
import com.team293.util.action.Action;
import com.team293.util.action.ActionParameter;
import com.team293.util.action.ActionResponse;
import kotlin.Pair;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

public class CreateAttendancePollAction implements Action<Void> {

    @Override
    public String actionId() {
        return "create_attendance_poll";
    }

    @Override
    public ActionResponse<Void> execute(List<ActionParameter<?>> parameters) throws SlackApiException, IOException {
        String channelId = getParameterValue(parameters, "channelId", String.class);
        TimedEvent timedEvent = getParameterValue(parameters, "timedEvent", TimedEvent.class);

        AttendanceMessenger.sendCheckInMessage(
                Main.slack.methods(Main.token),
                channelId,
                timedEvent
        );

        return ActionResponse.empty();
    }

    @Override
    public List<ActionParameter<?>> getParameters() {
        return List.of(
                createParameter("channelId", String.class),
                createParameter("timedEvent", TimedEvent.class)
        );
    }

    @Override
    public void initialize() {
        Main.app.blockAction("check_in_yes", (req, ctx) -> {
            String userId = req.getPayload().getUser().getId();
            String rawValue = req.getPayload().getActions().getFirst().getValue();

            String[] parts = rawValue.split(":", 2);
            String eventId = parts.length > 1 ? parts[1] : rawValue;

            TimedEvent event = TimedEvent.find.byId(Long.valueOf(eventId));

            if (event == null) {
                ctx.respond("Error: Event not found.");
                return ctx.ack();
            }

            if (event.getCheckedInUsers().contains(userId)) {
                ctx.respond("You have already checked in for this event.");
                return ctx.ack();
            }

            if (event.getCapacity() != null) {
                int currentCount = event.getCheckedInUsers().size();
                if (currentCount >= event.getCapacity()) {
                    ctx.respond("Sorry, this event has reached its capacity.");
                    return ctx.ack();
                }
            }

//            if (event.getEndTime() != null && event.getEndTime().isBefore(LocalDateTime.now())) {
//                ctx.respond("Sorry, this event has already ended.");
//                return ctx.ack();
//            }

            AttendanceEntry entry = new AttendanceEntry();
            entry.setUserId(userId);
            entry.setTimedEvent(event);
            entry.save();

            StringBuilder responseMessage = new StringBuilder("You have successfully checked in for *" + event.getName() + "*.");
            if (event.getCapacity() != null) {
                int currentCount = event.getCheckedInUsers().size();
                responseMessage.append(" Current attendance: ").append(currentCount).append("/").append(event.getCapacity()).append(".");
            }

            ctx.respond(r -> r.text(responseMessage.toString()).responseType("ephemeral"));

            return ctx.ack();
        });

        Main.app.blockAction("more_actions", (req, ctx) -> {
            String selectedOption = req.getPayload().getActions().getFirst().getSelectedOption().getValue();

            String[] parts = selectedOption.split(":", 2);
            String eventId = parts.length > 1 ? parts[1] : selectedOption;

            Action<String> constructRosterAction = Main.getAction("construct_event_roster");

            ActionResponse<String> rosterResponse = constructRosterAction.executeSafe(List.of(
                    Action.createParameter("eventId", Long.class, Long.valueOf(eventId))
            ));

            if (!rosterResponse.isSuccess() || rosterResponse.getData() == null) {
                ctx.respond("Error constructing roster: " + (rosterResponse.getMessage() != null ? rosterResponse.getMessage() : "Unknown error"));
                return ctx.ack();
            }

            String roster = rosterResponse.getData();

            ctx.respond(r -> r.text(roster).responseType("ephemeral"));

            return ctx.ack();
        });
    }
}
