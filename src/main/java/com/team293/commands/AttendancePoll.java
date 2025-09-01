package com.team293.commands;

import com.slack.api.bolt.request.builtin.SlashCommandRequest;
import com.slack.api.bolt.response.Response;
import com.slack.api.model.view.View;
import com.team293.Main;
import com.team293.util.command.Command;
import com.team293.util.modal.Modal;

import java.util.Map;

public class AttendancePoll implements Command {
    @Override
    public String getCommand() {
        return "attendance-poll";
    }

    @Override
    public void execute(SlashCommandRequest req, Response res) {
//        assertWorkspaceAdmin(req); dev mode for some reason doesnt give me admin on my own slack workspace

        Modal attendancePollModal = Main.getModal("create_attendance_poll");

        View view = attendancePollModal.build(
                Map.of("channel_id", req.getPayload().getChannelId())
        );

        try {
            Main.app.client().viewsOpen(r -> r
                    .triggerId(req.getPayload().getTriggerId())
                    .view(view)
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
