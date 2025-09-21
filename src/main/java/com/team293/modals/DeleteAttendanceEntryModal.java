package com.team293.modals;

import com.slack.api.bolt.request.builtin.ViewSubmissionRequest;
import com.slack.api.bolt.response.Response;
import com.slack.api.model.block.composition.OptionObject;
import com.slack.api.model.view.View;
import com.team293.Main;
import com.team293.entities.AttendanceEntry;
import com.team293.entities.TimedEvent;
import com.team293.util.modal.Modal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.slack.api.model.block.Blocks.asBlocks;
import static com.slack.api.model.block.Blocks.section;
import static com.slack.api.model.block.composition.BlockCompositions.*;
import static com.slack.api.model.block.element.BlockElements.staticSelect;
import static com.slack.api.model.view.Views.*;
import static com.slack.api.model.view.Views.viewClose;

public class DeleteAttendanceEntryModal implements Modal {
    @Override
    public String callbackId() {
        return "delete_attendance_entry";
    }

    @Override
    public void register() {
        Modal.super.register();
        Main.app.blockAction("select-event-action", (req, ctx) -> {
            // Acknowledge the action to avoid timeout
            return ctx.ack();
        });
    }

    @Override
    public View build(Map<String, Object> privateMetadata) {
        List<OptionObject> eventOptions = new ArrayList<>();

        for (TimedEvent e : TimedEvent.find.all()) {
            eventOptions.add(
                    option(plainText(e.getName() + " (" + e.getStartTime().toLocalDate() + ")"), String.valueOf(e.getId()))
            );
        }

        return view(v -> v
                .callbackId(callbackId())
                .type("modal")
                .notifyOnClose(true)
                .title(viewTitle(t -> t.type("plain_text").text("Delete Attendance Entry").emoji(true)))
                .submit(viewSubmit(s -> s.type("plain_text").text("Delete").emoji(true)))
                .close(viewClose(c -> c.type("plain_text").text("Cancel").emoji(true)))
                .privateMetadata(stringifyMetadata(privateMetadata))
                .blocks(asBlocks(
                        section(s -> s
                                .blockId("select-event-block")
                                .text(markdownText("*Select Entry to Delete for User*"))
                                .accessory(staticSelect(ss -> ss
                                                .actionId("select-event-action")
                                                .placeholder(plainText("Select an event"))
                                                .options(eventOptions)
                                        )
                                ))
                )));
    }

    @Override
    public void callback(ViewSubmissionRequest req, Response res) {
        Map<String, Object> metadata = parseMetadata(req.getPayload().getView().getPrivateMetadata());
        String eventIdStr = req.getPayload().getView().getState().getValues()
                .get("select-event-block")
                .get("select-event-action")
                .getSelectedOption()
                .getValue();

        long eventId = Long.parseLong(eventIdStr);

        AttendanceEntry entry = AttendanceEntry.find.query()
                .where()
                .eq("timed_event_id", eventId)
                .eq("user_id", metadata.get("user_id"))
                .findOne();

        if (entry != null) {
            entry.delete();
        }
    }
}
