package com.team293.commands;

import com.slack.api.bolt.request.builtin.SlashCommandRequest;
import com.slack.api.bolt.response.Response;
import com.slack.api.methods.response.users.UsersListResponse;
import com.slack.api.model.User;
import com.team293.Main;
import com.team293.util.action.Action;
import com.team293.util.action.ActionResponse;
import com.team293.util.command.Command;

import java.time.LocalDateTime;
import java.util.List;

public class Hours implements Command {
    @Override
    public String getCommand() {
        return "hours";
    }

    @Override
    public void execute(SlashCommandRequest req, Response res) {
        String userHandle = getOptionValue(req, 0, String.class).replace("@", "");
        String userId = null;

        try {
            UsersListResponse usersListResponse = Main.slack.methods().usersList(r -> r.token(Main.token));
            if (usersListResponse.isOk() && usersListResponse.getMembers() != null) {
                for (User user : usersListResponse.getMembers()) {
                    if (userHandle.equals(user.getName())) {
                        userId = user.getId();
                        break;
                    }
                }
            }
            if (userId == null) {
                throw new IllegalArgumentException("User handle not found: @" + userHandle);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to resolve user handle to ID", e);
        }

        String range = getOptionValue(req, 1, String.class);

        Action<Integer> calculateHoursForUserAction = Main.getAction("calculate_hours_for_user");

        LocalDateTime startTime = null;
        LocalDateTime endTime = null;

        if (range != null && !range.equalsIgnoreCase("all")) {
            char unit = range.charAt(range.length() - 1);
            int value;
            try {
                value = Integer.parseInt(range.substring(0, range.length() - 1));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid range format. Use formats like '30d', '1w', '1h', '1m', etc.");
            }

            endTime = switch (unit) {
                case 'd' -> {
                    startTime = LocalDateTime.now().minusDays(value);
                    yield LocalDateTime.now();
                }
                case 'w' -> {
                    startTime = LocalDateTime.now().minusWeeks(value);
                    yield LocalDateTime.now();
                }
                case 'h' -> {
                    startTime = LocalDateTime.now().minusHours(value);
                    yield LocalDateTime.now();
                }
                case 'm' -> {
                    startTime = LocalDateTime.now().minusMonths(value);
                    yield LocalDateTime.now();
                }
                default ->
                        throw new IllegalArgumentException("Invalid range unit. Use 'd' for days, 'w' for weeks, 'h' for hours, or 'm' for months.");
            };
        }

        ActionResponse<Integer> actionResponse = calculateHoursForUserAction.executeSafe(
                List.of(
                        Action.createParameter("userId", String.class, userId),
                        Action.createParameter("allTime", Boolean.class, range == null || range.equalsIgnoreCase("all")),
                        Action.createParameter("startDate", LocalDateTime.class, startTime),
                        Action.createParameter("endDate", LocalDateTime.class, endTime)
                )
        );

        if (actionResponse.isSuccess()) {
            Integer hours = actionResponse.getData();
            String message = (hours != null)
                    ? String.format("User <@%s> has logged %d hours.", userId, hours)
                    : "Could not retrieve hours.";
            try {
                Main.app.client().chatPostMessage(r -> r
                        .channel(req.getPayload().getChannelId())
                        .text(message)
                );
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            String errorMessage = actionResponse.getMessage() != null
                    ? actionResponse.getMessage()
                    : "An error occurred while calculating hours.";
            try {
                Main.app.client().chatPostMessage(r -> r
                        .channel(req.getPayload().getChannelId())
                        .text(errorMessage)
                );
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

    }
}
