package com.team293.commands;

import com.slack.api.bolt.request.builtin.SlashCommandRequest;
import com.slack.api.bolt.response.Response;
import com.slack.api.methods.SlackApiException;
import com.slack.api.model.User;
import com.team293.Main;
import com.team293.entities.AttendanceEntry;
import com.team293.util.action.Action;
import com.team293.util.action.ActionResponse;
import com.team293.util.command.Command;
import kotlin.Pair;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AttendanceHourList implements Command {
    @Override
    public String getCommand() {
        return "attendance-hour-list";
    }

    @Override
    public void execute(SlashCommandRequest req, Response res) {
        Action<List<Pair<String, Integer>>> attendanceHourListAction = Main.getAction("create_attendance_list");

        ActionResponse<List<Pair<String, Integer>>> actionResponse = attendanceHourListAction.executeSafe(List.of());

        if (!actionResponse.isSuccess()) {
            try {
                Main.app.client().chatPostMessage(r -> r
                        .channel(req.getPayload().getChannelId())
                        .text("Failed to retrieve attendance hour list: " + actionResponse.getMessage())
                );
            } catch (IOException | SlackApiException e) {
                throw new RuntimeException(e);
            }
            return;
        }

        StringBuilder responseText = new StringBuilder("*Attendance Hour List:*\n");

        for (Pair<String, Integer> entry : actionResponse.getData()) {
            User user;
            try {
                user = Main.slack.methods().usersInfo(r -> r.token(Main.token).user(entry.getFirst())).getUser();
            } catch (IOException | SlackApiException e) {
                throw new RuntimeException(e);
            }
            responseText.append(String.format("- %s: %d hours\n",
                    user != null ? user.getRealName() : entry.getFirst(),
                    entry.getSecond()));
        }

        try {
            Main.app.client().chatPostMessage(r -> r
                    .channel(req.getPayload().getChannelId())
                    .text(responseText.toString())
            );
        } catch (IOException | SlackApiException e) {
            throw new RuntimeException(e);
        }
    }
}
