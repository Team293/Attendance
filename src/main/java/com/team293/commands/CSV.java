package com.team293.commands;

import com.slack.api.bolt.request.builtin.SlashCommandRequest;
import com.slack.api.bolt.response.Response;
import com.slack.api.methods.SlackApiException;
import com.slack.api.model.User;
import com.team293.Main;
import com.team293.util.action.Action;
import com.team293.util.action.ActionResponse;
import com.team293.util.command.Command;
import kotlin.Pair;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class CSV implements Command {
    @Override
    public String getCommand() {
        return "csv";
    }

    @Override
    public void execute(SlashCommandRequest req, Response res) {
        Action<List<Pair<String, Integer>>> attendanceHourListAction = Main.getAction("create_attendance_list");
        String fileName = "attendance.csv";
        File csvFile = new File(fileName);

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

        try (FileWriter writer = new FileWriter(csvFile)) {
            writer.write("User ID,User Name,Hours\n");
            for (Pair<String, Integer> entry : actionResponse.getData()) {
                User user;
                try {
                    user = Main.slack.methods().usersInfo(r -> r.token(Main.token).user(entry.getFirst())).getUser();
                } catch (IOException | SlackApiException e) {
                    throw new RuntimeException(e);
                }

                writer.write(String.format("%s,%s,%d\n", entry.getFirst(), user.getRealName(), entry.getSecond()));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to write CSV file", e);
        }

        try {
            Main.app.client().filesUploadV2(r -> r
                    .channels(Collections.singletonList(req.getPayload().getChannelId()))
                    .file(csvFile)
                    .filename(fileName)
                    .title("Attendance Hours")
            );
        } catch (IOException | SlackApiException e) {
            throw new RuntimeException("Failed to upload CSV file", e);
        }
    }
}
