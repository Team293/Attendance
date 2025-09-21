package com.team293.commands;

import com.slack.api.bolt.request.builtin.SlashCommandRequest;
import com.slack.api.bolt.response.Response;
import com.team293.Main;
import com.team293.util.action.Action;
import com.team293.util.action.ActionResponse;
import com.team293.util.command.Command;
import com.team293.util.modal.Modal;

import java.util.List;
import java.util.Map;

public class DeleteAttendanceEntry implements Command {
    @Override
    public String getCommand() {
        return "delete-attendance-entry";
    }

    @Override
    public void execute(SlashCommandRequest req, Response res) {
        String userHandle = getOptionValue(req, 0, String.class).replace("@", "");
        Action<String> getUserIdByHandleAction = Main.getAction("get_user_id_from_handle");
        ActionResponse<String> userIdResponse = getUserIdByHandleAction.executeSafe(
                List.of(Action.createParameter("userHandle", String.class, userHandle))
        );

        if (!userIdResponse.isSuccess()) {
            String errorMessage = userIdResponse.getMessage() != null
                    ? userIdResponse.getMessage()
                    : "An error occurred while retrieving the user ID.";
            try {
                Main.app.client().chatPostMessage(r -> r
                        .channel(req.getPayload().getChannelId())
                        .text(errorMessage)
                );
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return;
        }

        Modal deleteAttendanceEntryModal = Main.getModal("delete_attendance_entry");

        try {
            Main.app.client().viewsOpen(r -> r
                    .triggerId(req.getPayload().getTriggerId())
                    .view(deleteAttendanceEntryModal.build(Map.of("user_id", userIdResponse.getData())))
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
