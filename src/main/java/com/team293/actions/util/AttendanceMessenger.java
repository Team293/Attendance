package com.team293.actions.util;

import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.model.Conversation;
import com.slack.api.model.block.Blocks;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.composition.BlockCompositions;
import com.slack.api.model.block.composition.PlainTextObject;
import com.slack.api.model.block.composition.OptionObject;
import com.slack.api.model.block.composition.ConfirmationDialogObject;
import com.slack.api.model.block.element.BlockElements;
import com.slack.api.model.block.element.ButtonElement;
import com.slack.api.model.block.element.OverflowMenuElement;
import com.team293.Main;
import com.team293.entities.PollPointer;
import com.team293.entities.TimedEvent;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class AttendanceMessenger {

    public static void sendCheckInMessage(
            MethodsClient client,
            String channel,
            TimedEvent e
    ) throws IOException, SlackApiException {
        Conversation slackChannel = client.conversationsInfo(r -> r.channel(channel)).getChannel();

        if (slackChannel == null) {
            throw new IllegalArgumentException("Channel not found: " + channel);
        }

        List<LayoutBlock> blocks = new ArrayList<>();

        // Header
        blocks.add(Blocks.header(h -> h.text(plain("🔥 Check-In: " + e.getName()))));

        // Attendance meter + overflow accessory
        OverflowMenuElement overflow = BlockElements.overflowMenu(o -> o
                .actionId("more_actions")
                .options(List.of(
                        OptionObject.builder().text(plain("View roster")).value("view_roster:" + e.getId()).build()
                ))
        );

        // Summary with banner accessory
        blocks.add(Blocks.section(s -> s
                .blockId("event_summary")
                .text(BlockCompositions.markdownText(
                        "*📅 Date*\n" + e.getStartTime().format(DateTimeFormatter.ofPattern("EEE, MMM d")) + "\n\n*📝 Description*\n" + e.getDescription()
                ))
                .accessory(overflow)
        ));

        blocks.add(Blocks.divider());

        // Actions: Here / Not Here with confirms
        ButtonElement yesBtn = BlockElements.button(b -> b
                .actionId("check_in_yes")
                .text(plain("✅ I’m Here"))
                .style("primary")
                .value("check_in_yes:" + e.getId())
                .confirm(ConfirmationDialogObject.builder()
                        .title(plain("Confirm status"))
                        .text(BlockCompositions.markdownText("Mark as *Here* for *" + e.getName() + "*?"))
                        .confirm(plain("Confirm"))
                        .deny(plain("Cancel"))
                        .build()
                )
        );

        blocks.add(Blocks.actions(a -> a
                .blockId("checkin_actions")
                .elements(List.of(yesBtn))
        ));

        ChatPostMessageRequest req = ChatPostMessageRequest.builder()
                .channel(channel)
                .blocks(blocks)
                .text("Event Check-In: " + e.getName()) // Fallback for notifications
                .build();

        var response = client.chatPostMessage(req);

        List<String> singleMessageGroups = Main.config.singleMessageGroups();

        if (singleMessageGroups.contains(e.getEventGroup())) {
            PollPointer existingPoll = PollPointer.find.query()
                    .where()
                    .eq("event_group", e.getEventGroup())
                    .setMaxRows(1)
                    .findOne();
            if (existingPoll != null && existingPoll.getLastMessageTs() != null) {
                // delete message
                client.chatDelete(r -> r
                        .channel(existingPoll.getSlackChannelId())
                        .ts(existingPoll.getLastMessageTs())
                );
                existingPoll.delete();
            } else {
                if (existingPoll != null) {
                    existingPoll.delete();
                }
            }
        }

        PollPointer pollPointer = new PollPointer();
        pollPointer.setEventGroup(e.getEventGroup());
        pollPointer.setSlackChannelId(channel);
        pollPointer.setTimedEvent(e);
        pollPointer.setLastMessageTs(response.getTs());
        pollPointer.save();
    }

    // Helpers
    private static PlainTextObject plain(String text) {
        return BlockCompositions.plainText(pt -> pt.text(text).emoji(true));
    }
}