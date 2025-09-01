package com.team293.entities;

import io.ebean.Finder;
import io.ebean.Model;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "attendance_entries")
@Data
public class AttendanceEntry extends Model {

    public static Finder<Long, AttendanceEntry> find = new Finder<>(AttendanceEntry.class);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "timed_event_id", nullable = false)
    private TimedEvent timedEvent; // Foreign key to TimedEvent
}
