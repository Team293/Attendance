package com.team293.modals;

import com.slack.api.bolt.request.builtin.ViewSubmissionRequest;
import com.slack.api.bolt.response.Response;
import com.slack.api.model.block.composition.OptionObject;
import com.slack.api.model.view.View;
import com.team293.Main;
import com.team293.entities.AttendanceEntry;
import com.team293.entities.PollPointer;
import com.team293.entities.TimedEvent;
import com.team293.util.modal.Modal;

import static com.slack.api.model.block.Blocks.*;
import static com.slack.api.model.block.composition.BlockCompositions.*;
import static com.slack.api.model.block.element.BlockElements.staticSelect;
import static com.slack.api.model.view.Views.*;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DeleteTimedEventModal implements Modal {
    @Override
    public String callbackId() {
        return "delete_timed_event";
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
                .title(viewTitle(t -> t.type("plain_text").text("Delete Event").emoji(true)))
                .submit(viewSubmit(s -> s.type("plain_text").text("Delete").emoji(true)))
                .close(viewClose(c -> c.type("plain_text").text("Cancel").emoji(true)))
                .privateMetadata(stringifyMetadata(privateMetadata))
                .blocks(asBlocks(
                        section(s -> s
                                .blockId("select-event-block")
                                .text(markdownText("*Select Event to Delete*"))
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
        String eventIdStr = req.getPayload().getView().getState().getValues()
                .get("select-event-block")
                .get("select-event-action")
                .getSelectedOption()
                .getValue();

        long eventId = Long.parseLong(eventIdStr);

        TimedEvent event = TimedEvent.find.byId(eventId);

        List<AttendanceEntry> entries = AttendanceEntry.find.query()
                .where()
                .eq("timed_event_id", eventId)
                .findList();

        for (AttendanceEntry entry : entries) {
            entry.delete();
        }

        PollPointer pointer = PollPointer.find.query()
                .where()
                .eq("timed_event_id", eventId)
                .findOne();

        if (pointer != null && pointer.getLastMessageTs() != null) {
            try {
                Main.app.client().chatDelete(r -> r
                        .channel(pointer.getSlackChannelId())
                        .ts(pointer.getLastMessageTs())
                );
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        if (pointer != null) {
            pointer.delete();
        }

        if (event != null) {
            event.delete();
        }
    }
}
