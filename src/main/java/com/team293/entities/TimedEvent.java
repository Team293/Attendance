package com.team293.entities;

import io.ebean.Finder;
import io.ebean.Model;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "timed_events")
@Data
// stores stuff like shop sessions, outreach events, etc.
public class TimedEvent extends Model {

    public static Finder<Long, TimedEvent> find = new Finder<>(TimedEvent.class);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_group", nullable = false)
    private String eventGroup; // e.g., "shop_session", "outreach_event" (for grouping hours)

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "name")
    private String name; // e.g., "Saturday Morning Session"

    @Column(name = "description")
    private String description; // e.g., "Outreach event at local school"

    @Column(name = "capacity")
    private Integer capacity; // max number of attendees, null if unlimited

    public List<String> getCheckedInUsers() {
        return AttendanceEntry.find.query()
                .select("userId")
                .where().eq("timedEvent", this)
                .findList()
                .stream()
                .map(AttendanceEntry::getUserId)
                .toList();
    }

}
