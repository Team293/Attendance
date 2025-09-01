package com.team293.commands;

import com.slack.api.bolt.request.builtin.SlashCommandRequest;
import com.slack.api.bolt.response.Response;
import com.slack.api.model.view.View;
import com.team293.Main;
import com.team293.util.command.Command;
import com.team293.util.modal.Modal;

public class DeleteEvent implements Command {
    @Override
    public String getCommand() {
        return "delete-event";
    }

    @Override
    public void execute(SlashCommandRequest req, Response res) {
//        assertWorkspaceAdmin(req);

        Modal deleteTimedEventModal = Main.getModal("delete_timed_event");

        View view = deleteTimedEventModal.build();

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
