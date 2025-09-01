package com.team293.modals;

import com.slack.api.bolt.request.builtin.ViewSubmissionRequest;
import com.slack.api.bolt.response.Response;
import com.slack.api.model.view.View;
import com.team293.Main;
import com.team293.entities.TimedEvent;
import com.team293.util.action.Action;
import com.team293.util.action.ActionResponse;
import com.team293.util.modal.Modal;
import lombok.SneakyThrows;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.slack.api.model.block.Blocks.*;
import static com.slack.api.model.block.composition.BlockCompositions.*;
import static com.slack.api.model.block.element.BlockElements.*;
import static com.slack.api.model.view.Views.*;

public class CreateAttendancePollModal implements Modal {
    @Override
    public String callbackId() {
        return "create_attendance_poll";
    }

    @Override
    public void register() {
        Modal.super.register();
        Main.app.blockAction("category-selection-action", (req, ctx) -> {
            // Acknowledge the action to avoid timeout
            return ctx.ack();
        });
    }

    @Override
    public View build(Map<String, Object> privateMetadata) {
        return view(v -> v
                .callbackId(callbackId())
                .type("modal")
                .notifyOnClose(true)
                .title(viewTitle(t -> t.type("plain_text").text("Attendance Poll").emoji(true)))
                .submit(viewSubmit(s -> s.type("plain_text").text("Create").emoji(true)))
                .close(viewClose(c -> c.type("plain_text").text("Cancel").emoji(true)))
                .privateMetadata(stringifyMetadata(privateMetadata))
                .blocks(asBlocks(
                        // Category
                        section(s -> s
                                .blockId("category-block")
                                .text(markdownText("*Category*"))
                                .accessory(staticSelect(ss -> ss
                                        .actionId("category-selection-action")
                                        .placeholder(plainText("Select a category"))
                                        .options(asOptions(
                                                option(plainText("Shop Session"), "shop_session"),
                                                option(plainText("Outreach Event"), "outreach_event"),
                                                option(plainText("Internal"), "internal")
                                        ))
                                ))
                        ),
                        divider(),
                        // Create event inputs
                        input(i -> i
                                .blockId("name-block")
                                .element(plainTextInput(pti -> pti.actionId("name-action")))
                                .label(plainText("Event Name"))
                                .optional(false)
                        ),
                        input(i -> i
                                .blockId("start-date-block")
                                .element(datePicker(dp -> dp.actionId("start-date-action")))
                                .label(plainText("Start Date"))
                                .optional(false)
                        ),
                        input(i -> i
                                .blockId("start-time-block")
                                .element(timePicker(tp -> tp.actionId("start-time-action")))
                                .label(plainText("Start Time"))
                                .optional(false)
                        ),
                        input(i -> i
                                .blockId("end-date-block")
                                .element(datePicker(dp -> dp.actionId("end-date-action")))
                                .label(plainText("End Date"))
                                .optional(false)
                        ),
                        input(i -> i
                                .blockId("end-time-block")
                                .element(timePicker(tp -> tp.actionId("end-time-action")))
                                .label(plainText("End Time"))
                                .optional(false)
                        ),
                        input(i -> i
                                .blockId("description-block")
                                .element(plainTextInput(pti -> pti.actionId("description-action").multiline(true)))
                                .label(plainText("Description"))
                                .optional(false)
                        ),
                        input(i -> i
                                .blockId("capacity-block")
                                .element(plainTextInput(pti -> pti.actionId("capacity-action").placeholder(plainText("e.g., 20"))))
                                .label(plainText("Capacity (optional)"))
                                .optional(true)
                        )
                ))
        );
    }

    @Override
    @SneakyThrows
    public void callback(ViewSubmissionRequest req, Response res) {
        // Only creating a new event now
        String category = readCategoryFromState(req);

        String name = readPlainText(req, "name-block", "name-action");
        String startDate = readDate(req, "start-date-block", "start-date-action");
        String startTime = readTime(req, "start-time-block", "start-time-action");
        String endDate = readDate(req, "end-date-block", "end-date-action");
        String endTime = readTime(req, "end-time-block", "end-time-action");
        String description = readPlainText(req, "description-block", "description-action");
        String capacityStr = readPlainText(req, "capacity-block", "capacity-action");
        Integer capacity = null;
        if (capacityStr != null && !capacityStr.isBlank()) {
            try {
                capacity = Integer.parseInt(capacityStr);
                if (capacity <= 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                res.setStatusCode(400);
                res.setBody("Capacity must be a positive integer");
                return;
            }
        }

        LocalDateTime start = mergeDateTime(startDate, startTime);
        LocalDateTime end = mergeDateTime(endDate, endTime);

        TimedEvent ev = new TimedEvent();
        ev.setEventGroup(category);
        ev.setStartTime(start);
        ev.setEndTime(end);
        ev.setName(name);
        ev.setDescription(description);
        ev.setCapacity(capacityStr == null || capacityStr.isBlank() ? null : capacity);
        ev.save();

        sendEvent(req, res, ev);
    }

    private void sendEvent(ViewSubmissionRequest req, Response res, TimedEvent ev) {
        String metadata = req.getPayload().getView().getPrivateMetadata();
        Map<String, Object> metaMap = parseMetadata(metadata);

        Action<Void> createAttendancePoll = Main.getAction("create_attendance_poll");
        ActionResponse<Void> response = createAttendancePoll.executeSafe(
                List.of(
                        Action.createParameter("channelId", String.class, (String) metaMap.get("channel_id")),
                        Action.createParameter("timedEvent", TimedEvent.class, ev)
                )
        );

        if (!response.isSuccess()) {
            res.setStatusCode(500);
            res.setBody("Failed to create attendance poll: " + response.getMessage());
            return;
        }

        res.setStatusCode(200);
        res.setBody("");
    }

    private String readCategoryFromState(ViewSubmissionRequest req) {
        return readValue(req, "category-block", "category-selection-action", "internal");
    }

    private String readValue(ViewSubmissionRequest req, String blockId, String actionId) {
        try {
            var state = req.getPayload().getView().getState().getValues();
            var v = state.get(blockId).get(actionId);
            if (v.getType().equals("external_select") || v.getType().equals("static_select") || v.getType().equals("radio_buttons")) {
                return v.getSelectedOption().getValue();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String readValue(ViewSubmissionRequest req, String blockId, String actionId, String def) {
        String v = readValue(req, blockId, actionId);
        return v == null ? def : v;
    }

    private String readPlainText(ViewSubmissionRequest req, String blockId, String actionId) {
        try {
            var v = req.getPayload().getView().getState().getValues().get(blockId).get(actionId);
            return v.getValue();
        } catch (Exception ignored) {}
        return null;
    }

    private String readDate(ViewSubmissionRequest req, String blockId, String actionId) {
        try {
            var v = req.getPayload().getView().getState().getValues().get(blockId).get(actionId);
            return v.getSelectedDate();
        } catch (Exception ignored) {}
        return null;
    }

    private String readTime(ViewSubmissionRequest req, String blockId, String actionId) {
        try {
            var v = req.getPayload().getView().getState().getValues().get(blockId).get(actionId);
            return v.getSelectedTime();
        } catch (Exception ignored) {}
        return null;
    }

    private LocalDateTime mergeDateTime(String date, String time) {
        if (date == null) return null;
        LocalTime t = Optional.ofNullable(time).map(LocalTime::parse).orElse(LocalTime.of(0, 0));
        return LocalDateTime.parse(date + "T" + t.toString());
    }
}