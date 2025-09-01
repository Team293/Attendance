package com.team293.actions;

import com.team293.entities.AttendanceEntry;
import com.team293.util.action.Action;
import com.team293.util.action.ActionParameter;
import com.team293.util.action.ActionResponse;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

public class CalculateHoursForUserAction implements Action<Integer> {
    @Override
    public ActionResponse<Integer> execute(List<ActionParameter<?>> parameters) throws Exception {
        String userId = getParameterValue(parameters, "userId", String.class);
        LocalDateTime startDate = getParameterValue(parameters, "startDate", LocalDateTime.class);
        LocalDateTime endDate = getParameterValue(parameters, "endDate", LocalDateTime .class);
        Boolean allTime = getParameterValue(parameters, "allTime", Boolean.class);

        List<AttendanceEntry> entries;

        if (allTime != null && allTime) {
            entries = AttendanceEntry.find.query()
                    .where()
                    .eq("user_id", userId)
                    .findList();
        } else {
            entries = AttendanceEntry.find.query()
                    .where()
                    .eq("user_id", userId)
                    .ge("timed_event.start_time", startDate)
                    .le("timed_event.end_time", endDate)
                    .findList();
        }

        int totalHours = 0;

        for (AttendanceEntry entry : entries) {
            if (entry.getTimedEvent() != null && entry.getTimedEvent().getStartTime() != null && entry.getTimedEvent().getEndTime() != null) {
                long hours = Duration.between(entry.getTimedEvent().getStartTime(), entry.getTimedEvent().getEndTime()).toHours();
                totalHours += (int) hours;
            }
        }

        return ActionResponse.success(totalHours);
    }

    @Override
    public List<ActionParameter<?>> getParameters() {
        return List.of(
                createParameter("userId", String.class),
                createParameter("startDate", LocalDateTime.class),
                createParameter("endDate", LocalDateTime.class),
                createParameter("allTime", Boolean.class)
        );
    }

    @Override
    public String actionId() {
        return "calculate_hours_for_user";
    }
}
