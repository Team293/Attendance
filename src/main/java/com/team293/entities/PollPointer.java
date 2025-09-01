package com.team293.entities;

import io.ebean.Finder;
import io.ebean.Model;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "poll_pointers")
@Data
public class PollPointer extends Model {
    public static final Finder<Long, PollPointer> find = new Finder<>(PollPointer.class);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "slack_channel_id", nullable = false, length = 32)
    private String slackChannelId;

    @Column(name = "event_group", nullable = false, length = 64)
    private String eventGroup; // e.g. "shop_session"

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "timed_event_id", nullable = false)
    private TimedEvent timedEvent;

    @Column(name = "last_message_ts", length = 32)
    private String lastMessageTs;
}
